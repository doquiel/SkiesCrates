/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Arquivo da Pokémania (fork pokemaniabrasil/SkiesCrates).
 */
package com.pokeskies.skiescrates.mania.ui

import com.pokeskies.skiescrates.data.Crate
import com.pokeskies.skiescrates.mania.Mania
import com.pokeskies.skiescrates.mania.ManiaOpening
import com.pokeskies.skiescrates.mania.OpeningView
import com.pokeskies.skiescrates.utils.Utils
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.server.level.ServerPlayer
import java.util.*

/**
 * Ponte para o ManiaUI sem importar nada dele: [CratesUi] e [CratesApp] só carregam com o mod `maniaui` instalado.
 * Sem o ManiaUI (ou sem o cliente dele), tudo cai nos baús do pacote `mania.chest`.
 */
object UiHook {
    private val available: Boolean by lazy { FabricLoader.getInstance().isModLoaded("maniaui") }
    private var broken = false

    fun init() {
        if (!available || !Mania.config.ativo) return
        try {
            CratesUi.register()
        } catch (e: LinkageError) {
            broken = true
            Utils.printError("ManiaUI incompatível; as caixas usam só o baú: ${e.message}")
            return
        }
        if (Mania.config.maniaui.app) {
            try {
                CratesApp.register()
            } catch (_: LinkageError) {
                // ManiaUI antes da 0.4.0: sem apps
            }
        }
    }

    /** O jogador vê as telas do ManiaUI (tem o cliente e está ligado no config). */
    fun native(player: ServerPlayer): Boolean {
        if (!available || broken || !Mania.config.ativo || !Mania.config.maniaui.ativo) return false
        return try {
            CratesUi.hasClient(player)
        } catch (e: LinkageError) {
            broken = true
            false
        }
    }

    fun openingView(opening: ManiaOpening): OpeningView? =
        if (native(opening.player)) CratesUi.openingView(opening) else null

    fun openPreview(player: ServerPlayer, crate: Crate): Boolean {
        if (!native(player)) return false
        CratesUi.openPreview(player, crate)
        return true
    }

    fun openList(player: ServerPlayer): Boolean {
        if (!native(player)) return false
        CratesUi.openList(player)
        return true
    }

    fun forget(uuid: UUID) {
        if (!available || broken) return
        try {
            CratesUi.forget(uuid)
        } catch (_: LinkageError) {
        }
    }
}
