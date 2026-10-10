/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Arquivo da Pokémania (fork pokemaniabrasil/SkiesCrates).
 */
package com.pokeskies.skiescrates.mania.chest

import com.pokeskies.skiescrates.data.rewards.Reward
import com.pokeskies.skiescrates.mania.*
import eu.pb4.sgui.api.ClickType
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack

/** Onde ficam as casas de N prêmios num baú 9×6 (até 21) e a moldura de cada uma. */
object Slots {
    fun positions(n: Int): List<Int> = when {
        n <= 1 -> listOf(22)
        n <= 5 -> row(n, 5).map { it + 9 }
        n <= 10 -> row(minOf(n, 5), 5).map { it + 9 } + row(n - 5, 5).map { it + 18 }
        else -> (0 until minOf(n, 21)).map { i -> 9 * (1 + i / 7) + 1 + i % 7 }
    }

    /** [count] casas centralizadas numa linha de [width] colunas (devolve a coluna, 0..8). */
    private fun row(count: Int, width: Int): List<Int> {
        val first = (9 - width) / 2 + (width - count) / 2
        return (0 until count).map { first + it }
    }

    /** Casas da moldura de uma posição (em cima ou embaixo; para uma só casa, em volta). */
    fun frame(n: Int, slot: Int): List<Int> = when {
        n <= 1 -> listOf(12, 13, 14, 21, 23, 30, 31, 32)
        n <= 10 -> if (slot < 18) listOf(slot - 9) else listOf(slot + 9)
        else -> emptyList()
    }
}

/**
 * Abrir várias (roleta, 9×6): cada casa gira com prêmios dos weights reais e para uma depois da outra (a mais rara
 * por último), depois um resumo agrupado por raridade.
 */
class MultiChest(private val opening: ManiaOpening) : ManiaChest(MenuType.GENERIC_9x6, opening.player), OpeningView {
    private val cfg = Mania.config.abertura
    private val t = Mania.config.textos
    private val crate = opening.crate
    /** Ordem de parada: da raridade menor para a maior. */
    private val order: List<Draw> = opening.draws.sortedBy { it.rarity }.take(21)
    private val slots = Slots.positions(order.size)
    private val stopAt = IntArray(order.size) { cfg.multipla.inicio + it * cfg.multipla.intervalo }
    private val stackCache = HashMap<String, ItemStack>()
    private val stopped = BooleanArray(order.size)
    private var ticks = 0
    private var done = false

    init {
        title = ChestKit.text(player, t.tituloMultipla.replace("%crate_name%", Mania.crateName(crate)).replace("%amount%", opening.draws.size.toString()))
    }

    override fun show() {
        val glass = ChestKit.filler(cfg.roleta.vidro)
        for (i in 0 until 54) put(i, glass)
        put(49, ChestKit.named(player, ChestKit.item("minecraft:clock"), t.pular, t.pularLore))
        for (i in slots.indices) put(slots[i], spinning())
        open()
    }

    private fun stackOf(reward: Reward): ItemStack = stackCache.getOrPut(reward.id) {
        ChestKit.reelItem(player, crate, reward, Rarities.of(crate, reward))
    }

    private fun spinning(): ItemStack = stackOf(opening.filler())

    override fun tick() {
        if (done || !isOpen) return
        ticks++
        for (i in order.indices) {
            if (stopped[i]) continue
            if (ticks >= stopAt[i]) stop(i) else if (ticks % 2 == 0) put(slots[i], spinning())
        }
        if (ticks % 4 == 0 && stopped.any { !it }) sound(cfg.roleta.somTick, 0.4f, 0.8f + ticks / 80f)
        if (stopped.all { it }) finish()
    }

    private fun stop(i: Int, quiet: Boolean = false) {
        if (stopped[i]) return
        stopped[i] = true
        val d = order[i]
        put(slots[i], ChestKit.reelItem(player, crate, d.reward, d.rarity, glint = d.rarity >= Rarity.EPICO))
        val frame = ChestKit.rarityGlass(player, d.rarity)
        Slots.frame(order.size, slots[i]).forEach { put(it, frame) }
        if (!quiet) {
            if (d.rarity >= Rarity.EPICO) Effects.raritySound(player, d.rarity)
            else sound(cfg.roleta.somTickFim, 0.7f, 0.8f + d.rarity.ordinal * 0.25f)
        }
    }

    override fun skip() {
        if (done) return
        for (i in order.indices) stop(i, quiet = true)
        finish()
    }

    private fun finish() {
        if (done) return
        done = true
        renderSummary()
        opening.complete()
    }

    private fun renderSummary() {
        val glass = ChestKit.filler(cfg.roleta.vidro)
        for (i in 36 until 54) put(i, glass)
        val groups = opening.draws.groupBy { it.rarity }.toSortedMap(compareByDescending { it })
        val start = 36 + (9 - groups.size) / 2
        var slot = start
        for ((rarity, list) in groups) {
            val lore = list.groupBy { it.reward.id }.values.map { same ->
                "<gray>• <${rarity.style.cor}>${Effects.rewardName(same.first().reward)}" + if (same.size > 1) " <dark_gray>×${same.size}" else ""
            }
            put(slot++, ChestKit.named(player, Effects.glass(rarity), "${rarity.colored} <dark_gray>×${list.size}", lore,
                glint = rarity >= Rarity.EPICO).copyWithCount(list.size.coerceIn(1, 64)))
        }
        put(47, ChestKit.named(player, ChestKit.item("minecraft:gold_ingot"), t.abrirVarias, placeholders = mapOf("%amount%" to Mania.multiOf(crate).toString())))
        put(48, ChestKit.named(player, ChestKit.item("minecraft:lime_dye"), t.abrirDeNovo))
        put(50, ChestKit.named(player, ChestKit.item("minecraft:spyglass"), t.verPremios))
        put(51, ChestKit.named(player, ChestKit.item("minecraft:barrier"), t.fechar))
    }

    override fun onSlotClick(index: Int, type: ClickType) {
        if (!done) {
            if (type.shift || index == 49) skip()
            return
        }
        when (index) {
            47 -> reopen(Mania.multiOf(crate))
            48 -> reopen(1)
            50 -> {
                click()
                closeSilently()
                Actions.preview(player, crate)
            }
            51 -> closeSilently()
        }
    }

    private fun reopen(count: Int) {
        click()
        if (Actions.nearby(player, crate) == null) {
            Mania.send(player, Mania.config.preview.ondeFica)
            return
        }
        closeSilently()
        Actions.open(player, crate, count)
    }

    override fun onClosed() {
        if (!opening.ended) opening.finishNow(ManiaOpening.EndReason.CLOSED)
    }

    override fun dispose() = closeSilently()
}
