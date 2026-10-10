/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Arquivo da Pokémania (fork pokemaniabrasil/SkiesCrates).
 */
package com.pokeskies.skiescrates.mania

import com.pokeskies.skiescrates.SkiesCrates
import com.pokeskies.skiescrates.SkiesCrates.Companion.asyncScope
import com.pokeskies.skiescrates.config.ConfigManager
import com.pokeskies.skiescrates.data.Crate
import com.pokeskies.skiescrates.data.rewards.Reward
import com.pokeskies.skiescrates.data.userdata.PendingRewardData
import com.pokeskies.skiescrates.data.userdata.UserData
import com.pokeskies.skiescrates.managers.KeyManager
import com.pokeskies.skiescrates.managers.OpeningManager
import com.pokeskies.skiescrates.utils.Utils
import kotlinx.coroutines.Job
import kotlinx.coroutines.future.await
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import net.minecraft.server.level.ServerPlayer
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/** Um prêmio sorteado: o id no diário, o prêmio e a raridade. */
class Draw(val id: String, val reward: Reward, val rarity: Rarity)

/**
 * Diário dos prêmios sorteados e ainda não entregues (`UserData.pending`).
 *
 * Toda leitura-alteração-gravação dos dados do jogador feita aqui passa pela mesma fila por jogador dos comandos de
 * chave do SkiesCrates ([KeyManager.queueOperation]) e relê os dados do banco antes de alterar. Assim uma chave
 * dada no meio de uma animação não é apagada por uma gravação velha.
 */
object Journal {
    /** Ids entregues neste servidor (para a recuperação ao entrar não entregar de novo antes de o banco gravar). */
    private val delivered = ConcurrentHashMap<String, Long>()
    private val jobs = Collections.synchronizedSet(mutableSetOf<Job>())

    /** Roda [block] com a vez do jogador na fila: nada mais lê/grava os dados dele enquanto isso. */
    suspend fun <T> withUserLock(uuid: UUID, block: suspend () -> T): T {
        val turn = CompletableFuture<Unit>()
        val done = CompletableFuture<Unit>()
        KeyManager.queueOperation(uuid) {
            turn.complete(Unit)
            done
        }
        turn.await()
        try {
            return block()
        } finally {
            done.complete(Unit)
        }
    }

    /** Sorteia [amount] prêmios pelos weights reais (respeitando os limites) e põe no diário de [data]. */
    fun draw(crate: Crate, data: UserData, amount: Int): List<Draw> {
        val result = mutableListOf<Draw>()
        val now = System.currentTimeMillis()
        repeat(amount) {
            val bag = crate.generateRewardBag(data)
            val reward = bag.next() ?: return result
            if (reward.getPlayerLimit() > 0) data.addRewardUse(crate, reward)
            val id = UUID.randomUUID().toString()
            data.pendingList().add(PendingRewardData(id, crate.id, reward.id, now))
            result += Draw(id, reward, Rarities.of(crate, reward))
        }
        return result
    }

    fun markDelivered(ids: Collection<String>) {
        val now = System.currentTimeMillis()
        ids.forEach { delivered[it] = now }
        if (delivered.size > 5000) delivered.entries.removeIf { now - it.value > 3_600_000 }
    }

    /** Tira os ids do diário (em segundo plano, na fila do jogador). */
    fun removePending(uuid: UUID, ids: Collection<String>) {
        if (ids.isEmpty()) return
        val set = ids.toSet()
        track(asyncScope.launch {
            withUserLock(uuid) {
                try {
                    val storage = SkiesCrates.INSTANCE.storage
                    val data = storage.getUser(uuid)
                    if (data.pendingList().removeIf { it.id in set }) {
                        if (!storage.saveUser(data)) Utils.printError("Não consegui tirar do diário os prêmios $set de $uuid")
                    }
                } catch (e: Exception) {
                    Utils.printError("Erro ao tirar do diário os prêmios $set de $uuid: ${e.message}")
                }
            }
        })
    }

    private fun track(job: Job) {
        jobs += job
        job.invokeOnCompletion { jobs -= job }
    }

    /** Desligamento: espera as gravações do diário em andamento (até 10 s). */
    fun flush() {
        val pending = synchronized(jobs) { jobs.toList() }
        if (pending.isEmpty()) return
        runBlocking { withTimeoutOrNull(10_000) { pending.joinAll() } }
    }

    /**
     * Ao entrar (5 s depois): entrega o que ficou no diário. Tira do diário antes de entregar; se o jogador sair
     * nesse meio-tempo, devolve ao diário.
     */
    fun recover(player: ServerPlayer) {
        if (!Mania.config.ativo) return
        val uuid = player.uuid
        val active = (OpeningManager.getInstance(uuid) as? ManiaOpening)?.drawIds() ?: emptySet()
        track(asyncScope.launch {
            val taken: List<PendingRewardData> = withUserLock(uuid) {
                val storage = SkiesCrates.INSTANCE.storage
                val data = storage.getUser(uuid)
                val list = data.pending ?: return@withUserLock emptyList()
                val alreadyDone = list.filter { delivered.containsKey(it.id) }
                val toDeliver = list.filter { !delivered.containsKey(it.id) && it.id !in active }
                if (alreadyDone.isEmpty() && toDeliver.isEmpty()) return@withUserLock emptyList()
                list.removeAll(alreadyDone.toSet())
                list.removeAll(toDeliver.toSet())
                if (!storage.saveUser(data)) {
                    Utils.printError("Não consegui gravar o diário de ${player.name.string}; tento de novo no próximo login")
                    return@withUserLock emptyList()
                }
                toDeliver
            }
            if (taken.isEmpty()) return@launch
            player.server.execute {
                if (player.hasDisconnected()) {
                    putBack(uuid, taken)
                    return@execute
                }
                var count = 0
                for (entry in taken) {
                    val crate = ConfigManager.CRATES[entry.crate]
                    val reward = crate?.rewards?.get(entry.reward)
                    if (crate == null || reward == null) {
                        Utils.printError("Prêmio pendente de ${player.name.string} sem caixa/prêmio no config: $entry (descartado; entregar à mão)")
                        continue
                    }
                    Delivery.give(player, crate, listOf(Draw(entry.id, reward, Rarities.of(crate, reward))), announce = true)
                    count++
                }
                markDelivered(taken.map { it.id })
                if (count > 0) {
                    Utils.printInfo("Entregues $count prêmio(s) pendente(s) de caixa para ${player.name.string}")
                    Mania.send(player, Mania.config.textos.pendentesEntregues, mapOf("%amount%" to count.toString()))
                }
            }
        })
    }

    private fun putBack(uuid: UUID, entries: List<PendingRewardData>) {
        track(asyncScope.launch {
            withUserLock(uuid) {
                val storage = SkiesCrates.INSTANCE.storage
                val data = storage.getUser(uuid)
                val ids = data.pendingList().map { it.id }.toSet()
                data.pendingList().addAll(entries.filter { it.id !in ids })
                storage.saveUser(data)
            }
        })
    }
}
