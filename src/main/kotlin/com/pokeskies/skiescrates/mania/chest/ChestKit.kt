/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Arquivo da Pokémania (fork pokemaniabrasil/SkiesCrates).
 */
package com.pokeskies.skiescrates.mania.chest

import com.pokeskies.skiescrates.data.Crate
import com.pokeskies.skiescrates.data.rewards.Reward
import com.pokeskies.skiescrates.mania.Effects
import com.pokeskies.skiescrates.mania.Mania
import com.pokeskies.skiescrates.mania.Rarity
import com.pokeskies.skiescrates.mania.RewardInfo
import com.pokeskies.skiescrates.utils.TextUtils
import eu.pb4.sgui.api.ClickType
import eu.pb4.sgui.api.elements.GuiElementInterface
import eu.pb4.sgui.api.gui.SimpleGui
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.ItemLore
import net.minecraft.world.inventory.ClickType as McClickType

/** Itens de menu: nome e lore em MiniMessage, sem itálico. */
object ChestKit {

    fun item(id: String): ItemStack {
        val rl = ResourceLocation.tryParse(id) ?: return ItemStack(Items.PAPER)
        val item = BuiltInRegistries.ITEM.getOptional(rl).orElse(Items.PAPER)
        return ItemStack(item)
    }

    fun text(player: ServerPlayer, mini: String, placeholders: Map<String, String> = emptyMap()): Component =
        Component.empty().setStyle(Style.EMPTY.withItalic(false)).append(Mania.text(player, mini, placeholders))

    fun named(
        player: ServerPlayer, stack: ItemStack, name: String?, lore: List<String> = emptyList(),
        placeholders: Map<String, String> = emptyMap(), glint: Boolean = false, hideExtra: Boolean = true
    ): ItemStack {
        val out = stack.copy()
        if (name != null) out.set(DataComponents.CUSTOM_NAME, text(player, name, placeholders))
        if (lore.isNotEmpty()) {
            out.set(DataComponents.LORE, ItemLore(lore.flatMap { it.split("\n") }.map { text(player, it, placeholders) }))
        }
        if (glint) out.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
        if (hideExtra) out.set(DataComponents.HIDE_ADDITIONAL_TOOLTIP, net.minecraft.util.Unit.INSTANCE)
        return out
    }

    /** Vidro de fundo sem tooltip. */
    fun filler(id: String): ItemStack {
        val out = item(id)
        out.set(DataComponents.HIDE_TOOLTIP, net.minecraft.util.Unit.INSTANCE)
        return out
    }

    /** Vidro da raridade (moldura), com o nome da raridade. */
    fun rarityGlass(player: ServerPlayer, rarity: Rarity, extra: List<String> = emptyList()): ItemStack =
        named(player, Effects.glass(rarity), rarity.colored, extra)

    /** Item do prêmio na roleta/cartas: nome na cor da raridade + raridade. */
    fun reelItem(player: ServerPlayer, crate: Crate, reward: Reward, rarity: Rarity, glint: Boolean = false): ItemStack {
        val base = RewardInfo.icon(player, reward)
        val name = "<${rarity.style.cor}>${Effects.rewardName(reward)}"
        return named(player, base.copyWithCount(1), name, listOf("<${rarity.style.cor}>■ ${rarity.style.nome}"), glint = glint)
    }

    /** Item do prêmio com chance real e o que vem de fato (preview e resultado). */
    fun infoItem(
        player: ServerPlayer, crate: Crate, reward: Reward, rarity: Rarity, chance: Double?, available: Boolean = true,
        extra: List<String> = emptyList(), glint: Boolean = false
    ): ItemStack {
        val t = Mania.config.textos
        val base = RewardInfo.icon(player, reward)
        val lore = mutableListOf<String>()
        // Estilo loot box: raridade + chance numa linha, e no máximo uma linha do que importa
        lore += "<${rarity.style.cor}>■ ${rarity.style.nome}" + if (chance != null) " <dark_gray>· <white>${RewardInfo.formatChance(chance)}" else ""
        RewardInfo.highlight(reward)?.let { lore += it }
        if (!available) lore += t.esgotado
        if (extra.isNotEmpty()) {
            lore += ""
            lore += extra
        }
        val name = "<${rarity.style.cor}>${Effects.rewardName(reward)}"
        return named(player, base.copyWithCount(1), name, lore, glint = glint)
    }
}

/**
 * Baú da Pokémania: um ponto de clique ([onSlotClick]) e o fechar ([onClosed]). Os cliques nunca mexem em itens.
 */
abstract class ManiaChest(type: MenuType<*>, player: ServerPlayer) : SimpleGui(type, player, false) {
    /** true = fechar não chama [onClosed] (a tela foi trocada ou fechada de propósito). */
    protected var silentClose = false

    override fun onClick(index: Int, type: ClickType, action: McClickType, element: GuiElementInterface?): Boolean {
        try {
            onSlotClick(index, type)
        } catch (e: Exception) {
            com.pokeskies.skiescrates.utils.Utils.printError("Erro no clique do menu: ${e.message}")
            e.printStackTrace()
        }
        return true
    }

    abstract fun onSlotClick(index: Int, type: ClickType)

    open fun onClosed() {}

    override fun onClose() {
        if (!silentClose) onClosed()
    }

    /** Fecha sem chamar [onClosed]. */
    fun closeSilently() {
        silentClose = true
        if (isOpen) close()
    }

    fun put(slot: Int, stack: ItemStack) {
        if (slot < 0 || slot >= size) return
        setSlot(slot, stack)
    }

    fun sound(id: String, volume: Float = 0.6f, pitch: Float = 1f) = Effects.sound(player, id, volume, pitch)

    fun click() = sound("minecraft:ui.button.click", 0.4f, 1f)
}
