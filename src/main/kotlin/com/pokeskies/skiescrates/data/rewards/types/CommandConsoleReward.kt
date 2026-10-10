/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Modificado pela Pokémania (fork pokemaniabrasil/SkiesCrates): pula os comandos de anúncio na abertura nova.
 */
package com.pokeskies.skiescrates.data.rewards.types

import com.google.gson.annotations.JsonAdapter
import com.pokeskies.skiescrates.SkiesCrates
import com.pokeskies.skiescrates.config.item.GenericItem
import com.pokeskies.skiescrates.data.Crate
import com.pokeskies.skiescrates.data.rewards.Reward
import com.pokeskies.skiescrates.data.rewards.RewardLimits
import com.pokeskies.skiescrates.data.rewards.RewardType
import com.pokeskies.skiescrates.placeholders.PlaceholderManager
import com.pokeskies.skiescrates.utils.FlexibleListAdaptorFactory
import com.pokeskies.skiescrates.utils.Utils
import net.minecraft.server.level.ServerPlayer

class CommandConsoleReward(
    name: String = "",
    weight: Int = 1,
    display: GenericItem? = null,
    limits: RewardLimits? = null,
    broadcast: Boolean = false,
    @JsonAdapter(FlexibleListAdaptorFactory::class)
    val commands: List<String> = emptyList()
) : Reward(RewardType.COMMAND_CONSOLE, name, display, weight, limits, broadcast) {
    companion object {
        val DEFAULT_DISPLAY = GenericItem("minecraft:paper", name = "Command")
    }

    override fun giveReward(player: ServerPlayer, crate: Crate) {
        // Super to call the message
        super.giveReward(player, crate)

        if (SkiesCrates.INSTANCE.server.commands == null) {
            Utils.printError("There was an error while giving a reward for player ${player.name}: Server was somehow null on command execution?")
            return
        }

        for (command in commands) {
            if (shouldSkipCommand(command)) continue // Pokémania
            SkiesCrates.INSTANCE.server.commands.performPrefixedCommand(
                SkiesCrates.INSTANCE.server.createCommandSourceStack(),
                PlaceholderManager.parse(player, command)
            )
        }
    }

    override fun getGenericDisplay(): GenericItem {
        return display ?: DEFAULT_DISPLAY
    }

    override fun toString(): String {
        return "CommandConsole(type=$type, name='$name', display=$display, weight=$weight, limits=$limits, broadcast=$broadcast, commands=$commands)"
    }
}
