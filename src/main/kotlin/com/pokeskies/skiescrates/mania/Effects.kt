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
import com.pokeskies.skiescrates.utils.Utils
import net.minecraft.core.component.DataComponents
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.particles.SimpleParticleType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.projectile.FireworkRocketEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.FireworkExplosion
import net.minecraft.world.item.component.Fireworks
import net.minecraft.world.phys.Vec3
import it.unimi.dsi.fastutil.ints.IntArrayList

/** Sons, títulos, partículas, fogos e totem da abertura. */
object Effects {

    fun sound(player: ServerPlayer, id: String, volume: Float = 1f, pitch: Float = 1f) {
        if (id.isBlank()) return
        val rl = ResourceLocation.tryParse(id) ?: return
        player.playNotifySound(SoundEvent.createVariableRangeEvent(rl), SoundSource.MASTER, volume, pitch)
    }

    fun raritySound(player: ServerPlayer, rarity: Rarity) {
        val s = rarity.style
        sound(player, s.som, s.volume, s.tom)
    }

    private fun threshold(id: String): Rarity? = if (id.isBlank()) null else Rarity.parse(id)

    /** Fim da animação: som, título, partículas, fogos, totem e o item subindo no bloco. */
    fun reveal(opening: ManiaOpening) {
        val player = opening.player
        if (player.hasDisconnected()) return
        val best = opening.draws.maxBy { it.rarity }
        val rarity = best.rarity
        raritySound(player, rarity)
        val world = Mania.config.mundo

        threshold(world.tituloAPartirDe)?.let { min ->
            if (rarity.atLeast(min)) {
                val t = Mania.config.textos
                val ph = placeholders(opening.crate, best.reward, rarity)
                player.connection.send(ClientboundSetTitlesAnimationPacket(5, 40, 15))
                player.connection.send(ClientboundSetTitleTextPacket(Mania.text(player, t.tituloPremio, ph)))
                player.connection.send(ClientboundSetSubtitleTextPacket(Mania.text(player, t.subtituloPremio, ph)))
            }
        }
        threshold(world.totemAPartirDe)?.let { min ->
            // Animação do totem só na tela de quem abriu (evento 35 para o próprio jogador)
            if (rarity.atLeast(min)) player.connection.send(ClientboundEntityEventPacket(player, 35.toByte()))
        }

        val level = player.serverLevel()
        val crateInstance = opening.location?.let { com.pokeskies.skiescrates.managers.CratesManager.getCrateFromPos(it) }
        val origin: Vec3 = if (crateInstance != null && crateInstance.level == level &&
            crateInstance.pos.center.distanceToSqr(player.position()) < 32.0 * 32.0
        ) crateInstance.pos.center.add(0.0, 0.6, 0.0) else player.position().add(0.0, 1.2, 0.0)

        if (world.particulas) particles(level, origin, rarity)
        threshold(world.fogosAPartirDe)?.let { min -> if (rarity.atLeast(min)) firework(level, origin, rarity) }
        if (world.itemSubindo && crateInstance != null && crateInstance.level == level) {
            WorldFx.risingItem(level, crateInstance.pos.center.add(0.0, 0.55, 0.0), best.reward.getDisplayItem(player), rarity)
        }
    }

    fun particles(level: ServerLevel, at: Vec3, rarity: Rarity) {
        val style = rarity.style
        val type = ResourceLocation.tryParse(style.particula)?.let { BuiltInRegistries.PARTICLE_TYPE.get(it) }
        val particle: ParticleOptions = (type as? SimpleParticleType) ?: ParticleTypes.HAPPY_VILLAGER
        level.sendParticles(particle, at.x, at.y, at.z, style.quantidadeParticulas, 0.45, 0.45, 0.45, 0.08)
    }

    fun firework(level: ServerLevel, at: Vec3, rarity: Rarity) {
        try {
            val explosion = FireworkExplosion(
                if (rarity.ordinal >= Rarity.LENDARIO.ordinal) FireworkExplosion.Shape.STAR else FireworkExplosion.Shape.BURST,
                IntArrayList.of(rarity.rgb), IntArrayList.of(0xFFFFFF), rarity.ordinal >= Rarity.LENDARIO.ordinal, true
            )
            val stack = ItemStack(Items.FIREWORK_ROCKET)
            stack.set(DataComponents.FIREWORKS, Fireworks(0, listOf(explosion)))
            val rocket = FireworkRocketEntity(level, at.x, at.y + 0.3, at.z, stack)
            rocket.lifetime = 2 // explode logo (access widener)
            level.addFreshEntity(rocket)
        } catch (e: Exception) {
            Utils.printDebug("Fogos falharam: ${e.message}")
        }
    }

    /** Mensagem no chat de quem abriu. */
    fun chatSummary(opening: ManiaOpening) {
        val player = opening.player
        if (player.hasDisconnected()) return
        val t = Mania.config.textos
        val crate = opening.crate
        if (opening.draws.size == 1) {
            val d = opening.draws.first()
            Mania.send(player, t.ganhou, placeholders(crate, d.reward, d.rarity))
            return
        }
        Mania.send(player, t.ganhouVarios, mapOf("%amount%" to opening.draws.size.toString(), "%crate_name%" to Mania.crateName(crate)))
        for ((rarity, list) in opening.draws.groupBy { it.rarity }.toSortedMap(compareByDescending { it })) {
            val names = list.groupBy { it.reward.id }.values.joinToString("<gray>, ") { same ->
                rewardName(same.first().reward) + if (same.size > 1) " <dark_gray>×${same.size}" else ""
            }
            Mania.send(player, t.linhaResumo, mapOf(
                "%rarity_colored%" to rarity.colored, "%count%" to list.size.toString(), "%rewards%" to names
            ))
        }
    }

    private val names = java.util.WeakHashMap<Reward, String>()

    /**
     * Nome curto do prêmio (estilo loot box): sem as tags de cor do config (a cor vem da raridade; alguns configs têm
     * `</color>` solto) e sem repetir o nome da caixa ("Lendas Animadas Cyndaquil" na Caixa Lendas Animadas →
     * "Cyndaquil").
     */
    fun rewardName(reward: Reward): String = synchronized(names) {
        names.getOrPut(reward) {
            val raw = reward.name.ifBlank { reward.getGenericDisplay().name ?: reward.id }
            var clean = try {
                com.pokeskies.skiescrates.SkiesCrates.MINI_MESSAGE.stripTags(raw).replace(Regex("</?[a-zA-Z_:#0-9-]+>"), "").trim()
            } catch (e: Exception) {
                raw
            }
            val crate = com.pokeskies.skiescrates.config.ConfigManager.CRATES.values.firstOrNull { it.rewards[reward.id] === reward }
            if (crate != null) {
                val theme = com.pokeskies.skiescrates.SkiesCrates.MINI_MESSAGE.stripTags(crate.name)
                    .replace(Regex("(?i)^caixa( de| da| do)?\\s+"), "").trim()
                if (theme.length >= 4) {
                    val shorter = clean.replace(Regex("(?i)\\b" + Regex.escape(theme) + "\\b"), "").replace(Regex("\\s+"), " ").trim()
                    if (shorter.isNotEmpty()) clean = shorter
                }
            }
            clean.ifEmpty { reward.id }
        }
    }

    fun clearNames() = synchronized(names) { names.clear() }

    fun placeholders(crate: Crate, reward: Reward, rarity: Rarity): Map<String, String> = mapOf(
        "%reward%" to rewardName(reward),
        "%rarity%" to rarity.style.nome,
        "%rarity_colored%" to rarity.colored,
        "%crate_name%" to Mania.crateName(crate),
        "%crate_id%" to crate.id,
    )

    /** Raridade no tom do item (moldura do baú). */
    fun glass(rarity: Rarity?): ItemStack {
        val id = rarity?.style?.vidro ?: "minecraft:gray_stained_glass_pane"
        val item = ResourceLocation.tryParse(id)?.let { BuiltInRegistries.ITEM.getOptional(it).orElse(null) } ?: Items.GRAY_STAINED_GLASS_PANE
        return ItemStack(item)
    }
}
