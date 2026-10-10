/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Arquivo da Pokémania (fork pokemaniabrasil/SkiesCrates).
 */
package com.pokeskies.skiescrates.mania

import com.pokeskies.skiescrates.data.Crate
import com.pokeskies.skiescrates.data.rewards.Reward
import java.text.Normalizer
import java.util.*

/** As seis raridades, da mais comum para a mais rara. O [id] é a chave no mania.json e no campo "rarity". */
enum class Rarity(val id: String, private val aliases: Set<String>) {
    COMUM("comum", setOf("common", "c")),
    INCOMUM("incomum", setOf("uncommon", "u")),
    RARO("raro", setOf("rare", "r")),
    EPICO("epico", setOf("epic", "e")),
    LENDARIO("lendario", setOf("legendary", "l")),
    MITICO("mitico", setOf("mythic", "mythical", "m"));

    val style: RarityStyle get() = Mania.config.raridades[id] ?: RarityStyle.defaults()[id]!!

    /** Nome com a cor (MiniMessage), ex: `<#B565F5>Épico</#B565F5>`. */
    val colored: String get() = "<${style.cor}>${style.nome}</${style.cor}>"

    val rgb: Int get() = parseRgb(style.cor)

    fun atLeast(other: Rarity?): Boolean = other == null || ordinal >= other.ordinal

    companion object {
        fun parse(text: String?): Rarity? {
            if (text.isNullOrBlank()) return null
            val clean = Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}"), "")
                .lowercase(Locale.ROOT)
            return entries.firstOrNull { it.id == clean || clean in it.aliases }
        }

        fun parseRgb(hex: String): Int = hex.removePrefix("#").toIntOrNull(16) ?: 0xFFFFFF
    }
}

/** Visual e efeitos de uma raridade (mania.json → "raridades"). Cores em hex `#RRGGBB`. */
class RarityStyle(
    val nome: String = "",
    val cor: String = "#FFFFFF",
    /** Vidro da moldura no baú. */
    val vidro: String = "minecraft:white_stained_glass_pane",
    /** Som quando o prêmio desta raridade aparece. */
    val som: String = "minecraft:entity.experience_orb.pickup",
    val volume: Float = 1.0f,
    val tom: Float = 1.0f,
    /** Partícula em volta do bloco e de quem abriu. */
    val particula: String = "minecraft:happy_villager",
    val quantidadeParticulas: Int = 12,
    /** Som tocado para todos os servidores no anúncio ("" = nenhum). */
    val somAnuncio: String = "",
) {
    companion object {
        fun defaults(): Map<String, RarityStyle> = linkedMapOf(
            "comum" to RarityStyle("Comum", "#B8C0CC", "minecraft:light_gray_stained_glass_pane",
                "minecraft:entity.experience_orb.pickup", 0.8f, 1.0f, "minecraft:happy_villager", 8),
            "incomum" to RarityStyle("Incomum", "#5BD75B", "minecraft:lime_stained_glass_pane",
                "minecraft:entity.player.levelup", 0.7f, 1.4f, "minecraft:happy_villager", 14),
            "raro" to RarityStyle("Raro", "#4FA3FF", "minecraft:light_blue_stained_glass_pane",
                "minecraft:block.amethyst_block.resonate", 1.0f, 1.2f, "minecraft:glow", 20),
            "epico" to RarityStyle("Épico", "#B565F5", "minecraft:purple_stained_glass_pane",
                "minecraft:ui.toast.challenge_complete", 0.8f, 1.2f, "minecraft:witch", 30,
                "minecraft:block.note_block.chime"),
            "lendario" to RarityStyle("Lendário", "#FFB627", "minecraft:orange_stained_glass_pane",
                "minecraft:ui.toast.challenge_complete", 1.0f, 1.0f, "minecraft:totem_of_undying", 45,
                "minecraft:entity.player.levelup"),
            "mitico" to RarityStyle("Mítico", "#FF4F8B", "minecraft:magenta_stained_glass_pane",
                "minecraft:entity.ender_dragon.growl", 0.6f, 1.3f, "minecraft:end_rod", 60,
                "minecraft:ui.toast.challenge_complete"),
        )
    }
}

/**
 * Raridade de cada prêmio: o "rarity" do prêmio, senão o "rarity" da caixa, senão deduzida pelo weight do prêmio
 * dividido pelo maior weight da caixa (limites em mania.json → "deducao"). Numa caixa com todos os weights iguais,
 * todos ficam com a mesma raridade: a chance é a mesma, então não há o que destacar.
 */
object Rarities {
    private val cache = WeakHashMap<Crate, Map<String, Rarity>>()

    fun clear() = synchronized(cache) { cache.clear() }

    fun of(crate: Crate, reward: Reward): Rarity = forCrate(crate)[reward.id] ?: Rarity.COMUM

    fun forCrate(crate: Crate): Map<String, Rarity> = synchronized(cache) {
        cache.getOrPut(crate) { compute(crate) }
    }

    private fun compute(crate: Crate): Map<String, Rarity> {
        val max = crate.rewards.values.maxOfOrNull { it.weight }?.takeIf { it > 0 } ?: 1
        val crateDefault = Rarity.parse(crate.rarity)
        val d = Mania.config.deducao
        return crate.rewards.mapValues { (_, reward) ->
            Rarity.parse(reward.rarity) ?: crateDefault ?: run {
                val ratio = reward.weight.toDouble() / max
                when {
                    ratio >= d.comum -> Rarity.COMUM
                    ratio >= d.incomum -> Rarity.INCOMUM
                    ratio >= d.raro -> Rarity.RARO
                    ratio >= d.epico -> Rarity.EPICO
                    ratio >= d.lendario -> Rarity.LENDARIO
                    else -> Rarity.MITICO
                }
            }
        }
    }
}
