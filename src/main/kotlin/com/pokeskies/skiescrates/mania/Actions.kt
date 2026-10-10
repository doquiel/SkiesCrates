/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Arquivo da Pokémania (fork pokemaniabrasil/SkiesCrates).
 */
package com.pokeskies.skiescrates.mania

import com.pokeskies.skiescrates.SkiesCrates.Companion.asyncScope
import com.pokeskies.skiescrates.data.Crate
import com.pokeskies.skiescrates.data.CrateOpenData
import com.pokeskies.skiescrates.data.DimensionalBlockPos
import com.pokeskies.skiescrates.mania.chest.PreviewChest
import com.pokeskies.skiescrates.mania.ui.UiHook
import com.pokeskies.skiescrates.managers.CratesManager
import com.pokeskies.skiescrates.managers.OpeningManager
import kotlinx.coroutines.launch
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.HoverEvent
import net.minecraft.server.level.ServerPlayer

/** O que os botões dos menus fazem (baú e ManiaUI chamam daqui). */
object Actions {

    /** A caixa mais perto do jogador, se estiver dentro de `preview.distanciaAbrir`. */
    fun nearby(player: ServerPlayer, crate: Crate): DimensionalBlockPos? {
        val max = Mania.config.preview.distanciaAbrir
        val dim = player.level().dimension().location().toString()
        return CratesManager.getAllInstances()
            .filter { it.crate.id == crate.id && it.dimPos.dimension == dim }
            .map { it to it.pos.center.distanceToSqr(player.position()) }
            .filter { it.second <= max * max }
            .minByOrNull { it.second }
            ?.first?.dimPos
    }

    /** Abre [count] caixas (o servidor limita pelas chaves). Longe do bloco: avisa onde fica. */
    fun open(player: ServerPlayer, crate: Crate, count: Int, skip: Boolean = false): Boolean {
        val location = nearby(player, crate)
        if (location == null) {
            Mania.send(player, Mania.config.preview.ondeFica)
            return false
        }
        if (OpeningManager.getInstance(player.uuid) != null) return false
        asyncScope.launch {
            CratesManager.openCrate(player, crate, CrateOpenData(location, null), false, count.coerceAtLeast(1), skip, true)
        }
        return true
    }

    /** Preview novo (ManiaUI para quem tem o cliente; senão o baú). */
    fun preview(player: ServerPlayer, crate: Crate) {
        if (UiHook.openPreview(player, crate)) return
        PreviewChest.open(player, crate)
    }

    /** Lista de caixas (/caixas). */
    fun list(player: ServerPlayer) {
        if (UiHook.openList(player)) return
        com.pokeskies.skiescrates.mania.chest.ListChest.open(player)
    }

    /** "Onde conseguir chave": texto no chat (e o link, se houver). */
    fun keysInfo(player: ServerPlayer, crate: Crate) {
        val lines = crate.keysInfo ?: Mania.config.preview.ondeConseguir
        lines.forEach { Mania.send(player, it, mapOf("%crate_name%" to Mania.crateName(crate))) }
        val link = crate.keysLink ?: Mania.config.preview.link
        if (link.isNotBlank()) {
            val msg = Mania.text(player, "<aqua><u>$link</u>").copy().withStyle {
                it.withClickEvent(ClickEvent(ClickEvent.Action.OPEN_URL, link))
                    .withHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, Mania.text(player, "<gray>Abrir o link")))
            }
            player.sendSystemMessage(msg)
        }
    }

    /** Chaves do jogador para abrir a caixa uma vez: (tem, precisa) da chave mais apertada. */
    fun keyStatus(crate: Crate, keys: Map<String, Int>): Pair<Int, Int> {
        if (crate.keys.isEmpty()) return Int.MAX_VALUE to 0
        var bestOpens = Int.MAX_VALUE
        var shown = 0 to 1
        for ((keyId, amount) in crate.keys) {
            val have = keys[keyId] ?: 0
            val opens = if (amount <= 0) Int.MAX_VALUE else have / amount
            if (opens < bestOpens) {
                bestOpens = opens
                shown = have to amount
            }
        }
        return shown
    }

    /** Quantas vezes dá para abrir com estas chaves. */
    fun opensWith(crate: Crate, keys: Map<String, Int>): Int {
        if (crate.keys.isEmpty()) return Int.MAX_VALUE
        return crate.keys.minOf { (keyId, amount) -> if (amount <= 0) Int.MAX_VALUE else (keys[keyId] ?: 0) / amount }
    }
}
