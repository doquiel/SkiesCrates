/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Arquivo da Pokémania (fork pokemaniabrasil/SkiesCrates).
 */
package com.pokeskies.skiescrates.mania

import com.pokeskies.skiescrates.data.Crate
import com.pokeskies.skiescrates.mania.net.AnnounceNetwork
import com.pokeskies.skiescrates.utils.TextUtils
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource

/**
 * Anúncio dos prêmios para a rede inteira (todas as raridades, com mais destaque nas raras). O item tem hover com o
 * item de verdade. Neste servidor vai direto; nos outros, pelo canal `skiescrates/anuncio` do Synx Connect.
 */
object Announcer {

    fun announce(player: ServerPlayer, crate: Crate, draws: List<Draw>) {
        if (draws.isEmpty() || !Mania.announces(crate)) return
        val cfg = Mania.config.anuncio
        val min = Rarity.parse(cfg.minimo) ?: Rarity.COMUM
        val shown = draws.filter { it.rarity.atLeast(min) }
        if (shown.isEmpty()) return
        val lines = mutableListOf<Component>()
        var sound = ""
        if (shown.size == 1) {
            lines += single(player, crate, shown.first())
            sound = shown.first().rarity.style.somAnuncio
        } else {
            // Várias: uma linha de resumo (hover com tudo) e linhas próprias para lendário e mítico
            lines += summary(player, crate, shown)
            for (d in shown.filter { it.rarity >= Rarity.LENDARIO }.sortedByDescending { it.rarity }) lines += single(player, crate, d)
            sound = shown.maxBy { it.rarity }.rarity.style.somAnuncio
        }
        val server = player.server
        lines.forEach { server.playerList.broadcastSystemMessage(it, false) }
        if (sound.isNotBlank()) {
            ResourceLocation.tryParse(sound)?.let { rl ->
                val event = SoundEvent.createVariableRangeEvent(rl)
                server.playerList.players.forEach { it.playNotifySound(event, SoundSource.MASTER, 0.6f, 1.0f) }
            }
        }
        if (cfg.rede) AnnounceNetwork.publish(lines, sound)
    }

    private fun single(player: ServerPlayer, crate: Crate, draw: Draw): List<Component> {
        val templates = Mania.config.textos.anuncio[draw.rarity.id] ?: Mania.config.textos.anuncio["comum"] ?: return emptyList()
        val reward = rewardComponent(player, crate, draw)
        return templates.map { line ->
            if (line.isBlank()) Component.literal(" ") else build(player, crate, line, draw.rarity, reward, null, 1)
        }
    }

    private fun summary(player: ServerPlayer, crate: Crate, draws: List<Draw>): List<Component> {
        val cfg = Mania.config
        val best = draws.maxBy { it.rarity }
        val counts = draws.groupBy { it.rarity }.toSortedMap(compareByDescending { it })
            .entries.joinToString("<dark_gray>, ") { (r, l) -> "<${r.style.cor}>${l.size} ${r.style.nome}</${r.style.cor}>" }
        val hoverLines = draws.sortedByDescending { it.rarity }.take(cfg.anuncio.resumoMaximo).map { d ->
            "${d.rarity.colored} <gray>${Effects.rewardName(d.reward)}"
        } + if (draws.size > cfg.anuncio.resumoMaximo) listOf("<dark_gray>+${draws.size - cfg.anuncio.resumoMaximo}") else emptyList()
        val hover = TextUtils.toNative(hoverLines.joinToString("<newline>"))
        return cfg.textos.anuncioVarios.map { line ->
            if (line.isBlank()) Component.literal(" ")
            else build(player, crate, line, best.rarity, rewardComponent(player, crate, best), counts, draws.size)
                .withStyle { it.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)) }
        }
    }

    /** O nome do prêmio na cor da raridade, com o item de verdade no hover. */
    private fun rewardComponent(player: ServerPlayer, crate: Crate, draw: Draw): Component {
        val name = TextUtils.toNative("<${draw.rarity.style.cor}>${Effects.rewardName(draw.reward)}</${draw.rarity.style.cor}>")
        val stack = draw.reward.getDisplayItem(player)
        val out = Component.empty().append(name)
        if (!stack.isEmpty) out.withStyle { it.withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_ITEM, HoverEvent.ItemStackInfo(stack))) }
        return out
    }

    /** Monta a linha trocando %reward% pelo componente com hover (o MiniMessage não sabe fazer hover de item). */
    private fun build(
        player: ServerPlayer, crate: Crate, template: String, rarity: Rarity, reward: Component, summary: String?, amount: Int
    ): MutableComponent {
        val replaced = template
            .replace("%player%", player.gameProfile.name)
            .replace("%crate_name%", Mania.crateName(crate))
            .replace("%rarity_colored%", rarity.colored)
            .replace("%rarity%", rarity.style.nome)
            .replace("%amount%", amount.toString())
            .replace("%summary%", summary ?: "")
        val parts = replaced.split("%reward%")
        val out = Component.empty()
        parts.forEachIndexed { i, part ->
            if (part.isNotEmpty()) out.append(TextUtils.toNative(part))
            if (i < parts.size - 1) out.append(reward)
        }
        return out
    }
}
