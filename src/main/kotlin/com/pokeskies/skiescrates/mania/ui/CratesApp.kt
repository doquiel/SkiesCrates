/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Arquivo da Pokémania (fork pokemaniabrasil/SkiesCrates).
 */
package com.pokeskies.skiescrates.mania.ui

import com.pokeskies.skiescrates.config.ConfigManager
import com.pokeskies.skiescrates.managers.KeyManager
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Items
import team.mania.ui.ManiaUI
import team.mania.ui.api.UiApp
import team.mania.ui.api.UiIcon

/**
 * App "Caixas" no Pokélógio (ManiaUI 0.4.0+): a lista de caixas com as chaves do jogador. Os provedores leem só o
 * cache de chaves do SkiesCrates (nunca o banco). Com um ManiaUI mais antigo esta classe nem carrega (ver UiHook).
 */
internal object CratesApp {
    private const val MUTED = 0x9CA5C4

    private fun totalKeys(uuid: java.util.UUID): Int =
        ConfigManager.KEYS.keys.sumOf { KeyManager.getCachedKeys(uuid, it) }

    fun register() {
        ManiaUI.registerApp(
            UiApp(
                "caixas", Component.literal("Caixas"), UiIcon.item(Items.CHEST),
                { player, back -> CratesUi.openList(player, back) },
                accent = CratesUi.accent(), order = 40, description = Component.literal("Prêmios e chances de cada caixa"),
                badge = { player ->
                    val n = totalKeys(player.uuid)
                    if (n > 0) Component.literal("$n CHAVE" + if (n > 1) "S" else "").withColor(0xE8B23A) else null
                },
                summary = { player ->
                    val n = totalKeys(player.uuid)
                    listOf(Component.literal(if (n > 0) "$n chave" + (if (n > 1) "s" else "") else "Prêmios e chances").withColor(MUTED))
                },
            )
        )
    }
}
