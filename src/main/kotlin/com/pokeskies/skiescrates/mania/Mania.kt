/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Arquivo da Pokémania (fork pokemaniabrasil/SkiesCrates).
 */
package com.pokeskies.skiescrates.mania

import com.pokeskies.skiescrates.SkiesCrates
import com.pokeskies.skiescrates.config.ConfigManager
import com.pokeskies.skiescrates.data.Crate
import com.pokeskies.skiescrates.data.opening.inventory.InventoryOpeningInstance
import com.pokeskies.skiescrates.mania.net.AnnounceNetwork
import com.pokeskies.skiescrates.mania.ui.UiHook
import com.pokeskies.skiescrates.managers.OpeningManager
import com.pokeskies.skiescrates.utils.TextUtils
import com.pokeskies.skiescrates.utils.Utils
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.*

/** Estilo da abertura nova. */
enum class OpeningStyle { ROLETA, CARTAS }

/**
 * Ponto de entrada da parte da Pokémania: config `mania.json`, eventos (entrar, sair, desligar) e o agendador de
 * tarefas por tick. O resto do SkiesCrates chama daqui; nada aqui roda com `ativo = false`.
 */
object Mania {
    const val CONFIG_FILE = "mania.json"

    var config: ManiaConfig = ManiaConfig()
        private set

    private val scheduled = mutableListOf<Pair<Int, () -> Unit>>()
    private var tick = 0
    private var eventsRegistered = false

    val server: MinecraftServer get() = SkiesCrates.INSTANCE.server

    fun load() {
        config = ConfigManager.loadFile(CONFIG_FILE, ManiaConfig(), "", true)
        Rarities.clear()
        Effects.clearNames()
    }

    /** Chamado no SERVER_STARTED (depois do load do SkiesCrates). */
    fun onServerStarted() {
        if (!eventsRegistered) {
            eventsRegistered = true
            registerEvents()
        }
        AnnounceNetwork.start()
        UiHook.init()
    }

    private val EARLY: ResourceLocation = SkiesCrates.asResource("early")

    private fun registerEvents() {
        // Sair: entrega na hora o que a animação ainda não entregou. Fase "early" para rodar antes de os outros mods
        // (Cobblemon, sincronização do PC) salvarem os dados do jogador que está saindo.
        ServerPlayConnectionEvents.DISCONNECT.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE)
        ServerPlayConnectionEvents.DISCONNECT.register(EARLY) { handler, server ->
            val player = handler.player
            val run = Runnable {
                val opening = OpeningManager.getInstance(player.uuid)
                if (opening is ManiaOpening) opening.onDisconnect()
                else if (opening is InventoryOpeningInstance) opening.forceFinish()
                UiHook.forget(player.uuid)
            }
            if (server.isSameThread) run.run() else server.execute(run)
        }
        // Entrar: prêmios que ficaram no diário (servidor caiu no meio, ou saiu antes de entregar)
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val player = handler.player
            later(100) { if (!player.hasDisconnected()) Journal.recover(player) }
        }
        // Itens subindo que sobraram de um desligamento no meio da animação
        ServerEntityEvents.ENTITY_LOAD.register { entity, _ ->
            if (entity.tags.contains(WorldFx.TAG)) entity.discard()
        }
    }

    fun tick() {
        tick++
        if (scheduled.isEmpty()) return
        val due = scheduled.filter { it.first <= tick }
        if (due.isEmpty()) return
        scheduled.removeAll(due)
        for ((_, action) in due) {
            try {
                action()
            } catch (e: Exception) {
                Utils.printError("Erro numa tarefa agendada: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /** Roda [action] na thread do servidor daqui a [ticks] ticks. */
    fun later(ticks: Int, action: () -> Unit) {
        scheduled += (tick + ticks.coerceAtLeast(1)) to action
    }

    /** SERVER_STOPPING, antes de fechar o banco: entrega tudo e grava o diário. */
    fun onStopping() {
        if (!config.ativo) return
        for (opening in OpeningManager.activeInstances().filterIsInstance<ManiaOpening>()) {
            try {
                opening.finishNow(ManiaOpening.EndReason.STOPPING)
            } catch (e: Exception) {
                Utils.printError("Erro ao entregar a abertura de ${opening.player.name.string} no desligamento: ${e.message}")
            }
        }
        Journal.flush()
        WorldFx.clearAll()
        AnnounceNetwork.stop()
    }

    // ---- regras por caixa ----

    fun openingStyle(crate: Crate): OpeningStyle? {
        if (!config.ativo) return null
        return when ((crate.opening ?: config.abertura.padrao).lowercase(Locale.ROOT)) {
            "roleta", "roulette", "spin" -> OpeningStyle.ROLETA
            "cartas", "cards", "card" -> OpeningStyle.CARTAS
            else -> null
        }
    }

    fun usesNewPreview(crate: Crate): Boolean = config.ativo && (crate.newPreview ?: config.preview.todas)

    fun multiOf(crate: Crate): Int = (crate.multiOpen ?: config.abertura.multiplas).coerceIn(0, 21)

    fun announces(crate: Crate): Boolean = config.ativo && config.anuncio.ativo && (crate.announce ?: true)

    // ---- textos ----

    fun text(player: ServerPlayer?, template: String, placeholders: Map<String, String> = emptyMap()): Component {
        val replaced = placeholders.entries.fold(template) { acc, (k, v) -> acc.replace(k, v) }
        return if (player != null) TextUtils.parseAllNative(player, replaced) else TextUtils.toNative(replaced)
    }

    fun send(player: ServerPlayer, template: String, placeholders: Map<String, String> = emptyMap()) {
        if (template.isBlank()) return
        player.sendSystemMessage(text(player, template, placeholders))
    }

    fun crateName(crate: Crate): String = crate.name.ifBlank { crate.id }
}
