/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Modificado pela Pokémania (fork pokemaniabrasil/SkiesCrates): entrega ao sair do servidor.
 */
package com.pokeskies.skiescrates.data.opening.inventory

import com.pokeskies.skiescrates.data.Crate
import com.pokeskies.skiescrates.data.opening.OpeningInstance
import com.pokeskies.skiescrates.data.rewards.Reward
import com.pokeskies.skiescrates.gui.CrateInventory
import com.pokeskies.skiescrates.utils.RandomCollection
import net.minecraft.server.level.ServerPlayer

class InventoryOpeningInstance(
    player: ServerPlayer,
    crate: Crate,
    val animation: InventoryOpeningAnimation,
    val randomBag: RandomCollection<Reward>,
): OpeningInstance(player, crate) {
    private val gui = CrateInventory(player, this)

    override fun setup() {
        gui.open()
    }

    override fun tick() {
        gui.tick()
    }

    /** Pokémania: o jogador saiu no meio da animação antiga: entrega na hora. */
    fun forceFinish() {
        gui.forceFinish()
    }
}