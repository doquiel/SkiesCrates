/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Modificado pela Pokémania (fork pokemaniabrasil/SkiesCrates): opções do Pokémon visíveis (preview em 3D).
 */
package com.pokeskies.skiescrates.data.rewards.types

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.pokemon.PokemonProperties
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.item.PokemonItem
import com.cobblemon.mod.common.pokemon.Pokemon
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import com.pokeskies.skiescrates.config.item.GenericItem
import com.pokeskies.skiescrates.data.Crate
import com.pokeskies.skiescrates.data.rewards.Reward
import com.pokeskies.skiescrates.data.rewards.RewardLimits
import com.pokeskies.skiescrates.data.rewards.RewardType
import com.pokeskies.skiescrates.data.rewards.options.boolean.BooleanChance
import com.pokeskies.skiescrates.data.rewards.options.boolean.BooleanOption
import com.pokeskies.skiescrates.data.rewards.options.boolean.BooleanValue
import com.pokeskies.skiescrates.data.rewards.options.int.IntOption
import com.pokeskies.skiescrates.placeholders.PlaceholderManager
import com.pokeskies.skiescrates.utils.FlexibleListAdaptorFactory
import com.pokeskies.skiescrates.utils.TextUtils
import com.pokeskies.skiescrates.utils.Utils
import net.kyori.adventure.text.format.NamedTextColor
import net.minecraft.core.component.DataComponentPatch
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemLore

class PokemonReward(
    name: String = "",
    display: GenericItem? = null,
    weight: Int = 1,
    limits: RewardLimits? = null,
    broadcast: Boolean = false,
    val pokemon: PokemonRewardOptions = PokemonRewardOptions(),
) : Reward(RewardType.POKEMON, name, display, weight, limits, broadcast) {
    companion object {
        private val DEFAULT_DISPLAY = GenericItem("cobblemon:poke_ball", name = "Pokemon Reward")
    }

    override fun giveReward(player: ServerPlayer, crate: Crate) {
        // Super to call the message
        super.giveReward(player, crate)

        val pokemonInstance = pokemon.createPokemon(false) ?: run {
            Utils.printError("Failed to create Pokemon for reward '$name' when giving to player ${player.name}.")
            player.sendMessage(net.kyori.adventure.text.Component.text("Your Pokemon Reward could not be created! Please contact an administrator.",
                NamedTextColor.RED))
            return
        }

        if (!Cobblemon.storage.getParty(player).add(pokemonInstance)) {
            Utils.printInfo("There was an error while giving a player their Pokemon Reward (${pokemon}).")
            player.sendMessage(net.kyori.adventure.text.Component.text("Your Pokemon Reward could not be added to your party! Please contact an administrator.",
                NamedTextColor.RED))
            return
        }
    }

    override fun getGenericDisplay(): GenericItem {
        return display ?: DEFAULT_DISPLAY
    }

    override fun getDisplayItem(player: ServerPlayer, placeholders: Map<String, String>): ItemStack {
        // If a display item is set that is not a Pokemon Model item, use that instead
        if (display != null && display.item.isNotEmpty() && !display.item.equals("cobblemon:pokemon_model", ignoreCase = true)) {
            return display.also {
                if (it.name == null) it.name = name
            }.createItemStack(player, placeholders)
        }

        val pokemonInstance = pokemon.createPokemon(true)
        val itemStack = if (pokemonInstance != null && !pokemon.isRandom()) {
            PokemonItem.from(pokemonInstance)
        } else {
            DEFAULT_DISPLAY.createItemStack(player, placeholders)
        }

        // Set the name onto the ItemStack, which may get overridden by the display
        DataComponentPatch.builder().let {
            it.set(DataComponents.ITEM_NAME, Component.empty().setStyle(Style.EMPTY.withItalic(false))
                    .append(TextUtils.parseAllNative(player, name, placeholders)))
            itemStack.applyComponents(it.build())
        }

        display?.let {
            val dataComponents = DataComponentPatch.builder()

            if (display.name != null) {
                dataComponents.set(
                    DataComponents.ITEM_NAME, Component.empty().setStyle(Style.EMPTY.withItalic(false))
                        .append(TextUtils.parseAllNative(player, name, placeholders)))
            }

            if (!display.lore.isNullOrEmpty()) {
                val parsedLore: MutableList<String> = mutableListOf()
                for (line in display.lore.stream().map { it }.toList()) {
                    val parsedLine = PlaceholderManager.parse(player, line)
                    if (parsedLine.contains("\n")) {
                        parsedLine.split("\n").forEach { parsedLore.add(it) }
                    } else {
                        parsedLore.add(parsedLine)
                    }
                }
                dataComponents.set(DataComponents.LORE, ItemLore(parsedLore.stream().map {
                    Component.empty().setStyle(Style.EMPTY.withItalic(false)).append(
                        TextUtils.toNative(
                            it
                        )
                    ) as Component
                }.toList()))
            }

            itemStack.applyComponents(dataComponents.build())

            itemStack
        }

        return itemStack
    }

    class PokemonRewardOptions(
        val species: String = "",
        val form: String = "",
        @JsonAdapter(FlexibleListAdaptorFactory::class)
        @SerializedName("aspects", alternate = ["aspect"])
        val aspects: List<String> = emptyList(),
        val level: IntOption? = null,
        val shiny: BooleanOption? = null,
    ) {
        fun isRandom(): Boolean {
            return species.equals("random", ignoreCase = true)
        }

        // Create a Pokemon instance based on the options. isDisplay may affect how it's created to not promise the player something they may not get.
        fun createPokemon(isDisplay: Boolean): Pokemon? {
            if (species.isEmpty()) {
                Utils.printError("Species was empty when creating Pokemon reward.")
                return null
            }
            val species = if (isRandom()) {
                PokemonSpecies.random()
            } else {
                if (species.contains(":")) {
                    ResourceLocation.tryParse(species)?.let {
                        PokemonSpecies.getByIdentifier(it)
                    }
                } else {
                    PokemonSpecies.getByName(species)
                }
            } ?: run {
                Utils.printError("Could not find Pokemon species '$species' when creating Pokemon reward.")
                return null
            }

            val pokemon = species.create()
            pokemon.form = species.getFormByName(form)

            aspects.forEach {
                PokemonProperties.parse(it).apply(pokemon)
            }

            level?.let { option -> pokemon.level = option.getValue() }
            shiny?.let { option ->
                if (option is BooleanValue) {
                    pokemon.shiny = option.bool
                } else if (option is BooleanChance && !isDisplay) {
                    pokemon.shiny = option.getValue()
                }
            }

            pokemon.initialize()

            return pokemon
        }
    }

    override fun toString(): String {
        return "PokemonReward(name='$name', display=$display, weight=$weight, limits=$limits, broadcast=$broadcast, pokemon=$pokemon)"
    }
}
