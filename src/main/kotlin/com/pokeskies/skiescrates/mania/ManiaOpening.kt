/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Arquivo da Pokémania (fork pokemaniabrasil/SkiesCrates).
 */
package com.pokeskies.skiescrates.mania

import com.pokeskies.skiescrates.data.Crate
import com.pokeskies.skiescrates.data.DimensionalBlockPos
import com.pokeskies.skiescrates.data.opening.OpeningInstance
import com.pokeskies.skiescrates.data.rewards.Reward
import com.pokeskies.skiescrates.mania.chest.CardsChest
import com.pokeskies.skiescrates.mania.chest.MultiChest
import com.pokeskies.skiescrates.mania.chest.RouletteChest
import com.pokeskies.skiescrates.mania.ui.UiHook
import com.pokeskies.skiescrates.managers.OpeningManager
import com.pokeskies.skiescrates.utils.RandomCollection
import com.pokeskies.skiescrates.utils.Utils
import net.minecraft.server.level.ServerPlayer

/** Uma tela de abertura (baú ou ManiaUI). Roda na thread do servidor. */
interface OpeningView {
    fun show()
    fun tick()
    /** Pula para o fim (shift+clique, preferência do jogador). */
    fun skip()
    /** Fecha a tela sem avisar a abertura (a abertura já terminou ou vai abrir outra tela). */
    fun dispose()
}

/** Entrega de prêmios da abertura nova: sem as mensagens do lang e sem os comandos de anúncio antigos. */
object Delivery {
    fun give(player: ServerPlayer, crate: Crate, draws: List<Draw>, announce: Boolean, journal: Boolean = true) {
        if (draws.isEmpty()) return
        Reward.deliveryQuiet = true
        Reward.skipCommandPrefixes = if (Mania.announces(crate)) Mania.config.anuncio.pularComandos else emptyList()
        try {
            for (draw in draws) {
                try {
                    draw.reward.giveReward(player, crate)
                } catch (e: Exception) {
                    Utils.printError("Erro ao entregar ${draw.reward.id} da caixa ${crate.id} para ${player.name.string}: ${e.message}")
                    e.printStackTrace()
                }
            }
        } finally {
            Reward.deliveryQuiet = false
            Reward.skipCommandPrefixes = emptyList()
        }
        val ids = draws.map { it.id }
        Journal.markDelivered(ids)
        if (journal) Journal.removePending(player.uuid, ids)
        Utils.printInfo("Caixa ${crate.id}: ${player.name.string} recebeu ${draws.joinToString { "${it.reward.id} (${it.rarity.id})" }}")
        if (announce) Announcer.announce(player, crate, draws)
    }
}

/**
 * Uma abertura da Pokémania. Os prêmios ([draws]) já foram sorteados e gravados no diário antes de a tela abrir:
 * a tela só mostra o resultado. Entrega: no fim da animação, ao fechar o menu, ao sair do servidor ou ao desligar,
 * o que acontecer primeiro, e nunca duas vezes.
 */
class ManiaOpening(
    player: ServerPlayer,
    crate: Crate,
    val draws: List<Draw>,
    val style: OpeningStyle,
    private val skipAtStart: Boolean,
    val location: DimensionalBlockPos?,
    /** Os prêmios possíveis com os weights reais, para encher a roleta e as cartas. */
    val fillers: RandomCollection<Reward>,
) : OpeningInstance(player, crate) {

    enum class EndReason { FINISHED, CLOSED, DISCONNECT, STOPPING }

    private val delivered = mutableSetOf<String>()
    private var announced = false
    var ended = false
        private set
    lateinit var view: OpeningView
        private set

    fun drawIds(): Set<String> = draws.map { it.id }.toSet()

    override fun setup() {
        view = UiHook.openingView(this) ?: when {
            style == OpeningStyle.CARTAS -> CardsChest(this)
            draws.size > 1 -> MultiChest(this)
            else -> RouletteChest(this)
        }
        view.show()
        if (skipAtStart) view.skip()
    }

    override fun tick() {
        if (ended) return
        try {
            view.tick()
        } catch (e: Exception) {
            Utils.printError("Erro na animação da caixa ${crate.id} de ${player.name.string}: ${e.message}")
            e.printStackTrace()
            finishNow(EndReason.CLOSED)
        }
    }

    /** Entrega estes prêmios (os já entregues são ignorados). */
    fun deliver(list: List<Draw>) {
        val todo = list.filter { delivered.add(it.id) }
        if (todo.isEmpty()) return
        Delivery.give(player, crate, todo, announce = false)
    }

    fun isDelivered(draw: Draw) = draw.id in delivered

    /** A animação terminou: entrega o resto, efeitos e mensagens. A tela continua aberta mostrando o resultado. */
    fun complete() {
        if (ended) return
        ended = true
        deliver(draws)
        announceOnce()
        Effects.reveal(this)
        Effects.chatSummary(this)
        // Libera o jogador para abrir outra (o "Abrir de novo" da tela de resultado)
        OpeningManager.removeInstance(player.uuid)
    }

    /** Fechou o menu, saiu ou o servidor está desligando: entrega na hora. */
    fun finishNow(reason: EndReason) {
        if (ended) {
            if (reason != EndReason.FINISHED) view.dispose()
            return
        }
        ended = true
        val wasMissing = draws.any { it.id !in delivered }
        deliver(draws)
        announceOnce()
        if (reason == EndReason.CLOSED && wasMissing) {
            Effects.chatSummary(this)
            Mania.send(player, Mania.config.textos.entregueAoFechar)
        }
        if (reason != EndReason.CLOSED) view.dispose()
        OpeningManager.removeInstance(player.uuid)
    }

    fun onDisconnect() = finishNow(EndReason.DISCONNECT)

    private fun announceOnce() {
        if (announced) return
        announced = true
        Announcer.announce(player, crate, draws)
    }

    override fun stop() {
        finishNow(EndReason.CLOSED)
    }

    override fun destroy() {
        finishNow(EndReason.CLOSED)
    }

    /** Melhor raridade desta abertura. */
    fun best(): Rarity = draws.maxOf { it.rarity }

    /** Um prêmio qualquer pelos weights reais (para encher a roleta e as cartas). */
    fun filler(): Reward = fillers.next() ?: draws.first().reward
}
