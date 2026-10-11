/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Arquivo da Pokémania (fork pokemaniabrasil/SkiesCrates).
 */
package com.pokeskies.skiescrates.mania

import com.pokeskies.skiescrates.config.ConfigManager
import com.pokeskies.skiescrates.data.Crate
import com.pokeskies.skiescrates.data.rewards.Reward
import com.pokeskies.skiescrates.data.rewards.types.CommandConsoleReward
import com.pokeskies.skiescrates.data.rewards.types.CommandPlayerReward
import com.pokeskies.skiescrates.data.rewards.types.ItemReward
import com.pokeskies.skiescrates.data.rewards.types.PokemonReward
import com.pokeskies.skiescrates.data.userdata.UserData
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.resources.ResourceLocation
import java.util.*

/** Pokémon mostrado em 3D (ManiaUI) e no lore. */
class PokemonRef(val species: String, val aspects: List<String>)

/** O que se sabe de um prêmio para mostrar ao jogador: Pokémon, detalhes e chance. Sem I/O. */
object RewardInfo {
    private val SPECIES_FLAGS = setOf("shiny")

    fun commandsOf(reward: Reward): List<String> = when (reward) {
        is CommandConsoleReward -> reward.commands
        is CommandPlayerReward -> reward.commands
        else -> emptyList()
    }

    /** Espécie e aspects: do item de exibição (`cobblemon:pokemon_item`), do prêmio POKEMON ou do comando pokegive. */
    fun pokemon(reward: Reward): PokemonRef? {
        if (reward is PokemonReward && reward.pokemon.species.isNotBlank() && !reward.pokemon.species.equals("random", true)) {
            val aspects = reward.pokemon.aspects.toMutableList()
            return PokemonRef(withNamespace(reward.pokemon.species), aspects)
        }
        val components: CompoundTag? = (reward.preview ?: reward.display)?.components
        if (components != null && components.contains("cobblemon:pokemon_item", Tag.TAG_COMPOUND.toInt())) {
            val tag = components.getCompound("cobblemon:pokemon_item")
            val species = tag.getString("species")
            if (species.isNotBlank()) {
                val list = tag.getList("aspects", Tag.TAG_STRING.toInt())
                return PokemonRef(withNamespace(species), (0 until list.size).map { list.getString(it) })
            }
        }
        val give = pokegive(reward) ?: return null
        return PokemonRef(withNamespace(give.species), give.aspects)
    }

    private class Give(val species: String, val props: Map<String, String>, val aspects: List<String>)

    private fun pokegive(reward: Reward): Give? {
        val cmd = commandsOf(reward).firstOrNull { it.trimStart().removePrefix("/").startsWith("pokegive", true) } ?: return null
        val tokens = cmd.trim().removePrefix("/").split(Regex("\\s+"))
        if (tokens.isEmpty()) return null
        // pokegive <props...> | pokegiveother <jogador> <props...>
        val args = if (tokens[0].equals("pokegiveother", true)) tokens.drop(2) else tokens.drop(1)
        var species: String? = null
        val props = linkedMapOf<String, String>()
        for (t in args) {
            if (t.contains('=')) {
                val (k, v) = t.split('=', limit = 2)
                props[k.lowercase(Locale.ROOT)] = v
            } else if (t.lowercase(Locale.ROOT) in SPECIES_FLAGS) {
                props[t.lowercase(Locale.ROOT)] = "true"
            } else if (species == null) {
                species = t.lowercase(Locale.ROOT)
            } else {
                props[t.lowercase(Locale.ROOT)] = "true" // aspect solto (ex: "alolan")
            }
        }
        val s = species ?: return null
        val aspects = mutableListOf<String>()
        if (props["shiny"].isTrue()) aspects += "shiny"
        props["skin"]?.let { aspects += "skin-$it" }
        return Give(s, props, aspects)
    }

    private fun String?.isTrue() = this != null && (this == "true" || this == "yes" || this == "1")

    private fun withNamespace(species: String): String =
        if (species.contains(':')) species.lowercase(Locale.ROOT) else "cobblemon:${species.lowercase(Locale.ROOT)}"

    /** Nome da espécie traduzido no idioma do cliente (MiniMessage). */
    fun speciesName(species: String): String {
        val id = species.substringAfter(':')
        return "<lang:cobblemon.species.$id.name>"
    }

    /**
     * Linhas "o que vem de fato" (MiniMessage): a "description" do prêmio, se houver; senão o que dá para ler dos
     * comandos (pokegive, give, chave de caixa).
     */
    fun details(reward: Reward): List<String> {
        reward.description?.takeIf { it.isNotEmpty() }?.let { return it }
        val lines = mutableListOf<String>()
        if (reward is PokemonReward) {
            val p = reward.pokemon
            if (p.species.isNotBlank()) lines += "<gray>Pokémon: <white>${speciesName(p.species)}"
            if (p.form.isNotBlank()) lines += "<gray>Forma: <white>${p.form}"
            if (p.aspects.any { it.equals("shiny", true) }) lines += "<yellow>✦ Shiny"
            return lines
        }
        if (reward is ItemReward) {
            val display = reward.getGenericDisplay()
            lines += "<gray>Item: <white>${itemName(display.item)}" + if (display.amount > 1) " <gray>×${display.amount}" else ""
            return lines
        }
        for (raw in commandsOf(reward)) {
            val cmd = raw.trim().removePrefix("/")
            val tokens = cmd.split(Regex("\\s+"))
            val head = tokens.firstOrNull()?.lowercase(Locale.ROOT) ?: continue
            when {
                head.startsWith("pokegive") -> {
                    val give = pokegive(reward) ?: continue
                    lines += "<gray>Pokémon: <white>${speciesName(give.species)}"
                    if (give.props["shiny"].isTrue()) lines += "<yellow>✦ Shiny"
                    give.props["min_perfect_ivs"]?.toIntOrNull()?.let { lines += "<gray>IVs perfeitos: <white>$it de 6 <dark_gray>(31)" }
                    (give.props["level"] ?: give.props["lvl"])?.let { lines += "<gray>Nível: <white>$it" }
                    give.props["skin"]?.let { lines += "<gray>Skin: <white>$it" }
                    give.props["form"]?.let { lines += "<gray>Forma: <white>$it" }
                    listOf("alolan", "galarian", "hisuian", "paldean").filter { give.props[it].isTrue() }
                        .forEach { lines += "<gray>Forma: <white>${it.replaceFirstChar { c -> c.uppercase() }}" }
                    if (give.props["unbreedable"].isTrue()) lines += "<gray>Não pode cruzar"
                }
                head == "give" && tokens.size >= 3 -> {
                    val item = tokens[2].substringBefore('[').substringBefore('{')
                    val amount = tokens.getOrNull(3)?.toIntOrNull() ?: 1
                    lines += "<gray>Item: <white>${itemName(item)}" + if (amount > 1) " <gray>×$amount" else ""
                }
                head == "skiescrates" || head == "crates" || head == "crate" -> {
                    // skiescrates key give <jogador> <chave> <quantidade>
                    if (tokens.getOrNull(1).equals("key", true) && tokens.getOrNull(2).equals("give", true)) {
                        val keyId = tokens.getOrNull(4) ?: continue
                        val amount = tokens.getOrNull(5)?.toIntOrNull() ?: 1
                        val keyName = ConfigManager.KEYS[keyId]?.name ?: keyId
                        lines += "<gray>+ <white>$amount</white> chave <white>$keyName"
                    }
                }
            }
        }
        return lines
    }

    fun itemName(id: String): String {
        val rl = ResourceLocation.tryParse(id) ?: return id
        val item = BuiltInRegistries.ITEM.getOptional(rl).orElse(null) ?: return id
        return "<lang:${item.descriptionId}>"
    }

    /** Chance real de cada prêmio para este jogador (só os que ele pode receber), de 0 a 1. */
    fun chances(crate: Crate, userData: UserData?): Map<String, Double> {
        val eligible = crate.rewards.values.filter { it.weight > 0 && (userData == null || it.canReceive(userData, crate)) }
        val total = eligible.sumOf { it.weight.toDouble() }
        if (total <= 0) return emptyMap()
        return eligible.associate { it.id to it.weight / total }
    }

    /**
     * Uma linha curta com o que importa (estilo loot box): "✦ Shiny · 6 IVs 31", "×32", "+1 chave". Sem skin, sem
     * "não pode cruzar". A "description" do config, se houver, manda.
     */
    fun highlight(reward: Reward): String? {
        reward.description?.takeIf { it.isNotEmpty() }?.let { return it.first() }
        val parts = mutableListOf<String>()
        val lines = details(reward)
        if (lines.any { it.contains("Shiny") }) parts += "<yellow>✦ Shiny"
        lines.firstOrNull { it.contains("IVs perfeitos") }?.let { l ->
            Regex("(\\d+) de 6").find(l)?.groupValues?.get(1)?.let { parts += "<aqua>$it IVs 31" }
        }
        lines.firstOrNull { it.startsWith("<gray>Item:") }?.let { l -> Regex("×(\\d+)").find(l)?.let { parts += "<white>×${it.groupValues[1]}" } }
        lines.firstOrNull { it.contains("chave") }?.let { l -> Regex("<white>(\\d+)</white> chave").find(l)?.let { parts += "<gold>+${it.groupValues[1]} chave" } }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" <dark_gray>· ")
    }

    /**
     * Item que representa o prêmio nas telas. Se o item do config não existe neste servidor (mod faltando, id errado),
     * o SkiesCrates devolve ar e a casa fica vazia: aqui vira um papel, e o erro vai para o log uma vez por prêmio.
     */
    fun icon(player: net.minecraft.server.level.ServerPlayer, reward: Reward): net.minecraft.world.item.ItemStack {
        val stack = try {
            reward.getDisplayItem(player, emptyMap())
        } catch (e: Exception) {
            net.minecraft.world.item.ItemStack.EMPTY
        }
        if (!stack.isEmpty) return stack
        if (missing.add(reward)) {
            com.pokeskies.skiescrates.utils.Utils.printError("Prêmio '${reward.id}': o item de exibição não existe neste servidor (mod faltando?). Mostrando papel.")
        }
        return net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER)
    }

    private val missing = java.util.Collections.newSetFromMap(java.util.WeakHashMap<Reward, Boolean>())

    /** "0,45%" com casas suficientes para não virar zero. */
    fun formatChance(p: Double): String {
        val pct = p * 100
        val text = when {
            pct >= 10 -> String.format(Locale.ROOT, "%.1f", pct)
            pct >= 0.1 -> String.format(Locale.ROOT, "%.2f", pct)
            pct >= 0.01 -> String.format(Locale.ROOT, "%.3f", pct)
            else -> String.format(Locale.ROOT, "%.4f", pct)
        }
        return text.replace('.', ',') + "%"
    }

    /** "1 em 222". */
    fun oneIn(p: Double): String {
        if (p <= 0) return "∞"
        val n = Math.round(1 / p)
        return String.format(Locale.GERMANY, "%,d", n)
    }
}
