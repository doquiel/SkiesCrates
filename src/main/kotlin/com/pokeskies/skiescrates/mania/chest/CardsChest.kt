/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Arquivo da Pokémania (fork pokemaniabrasil/SkiesCrates).
 */
package com.pokeskies.skiescrates.mania.chest

import com.pokeskies.skiescrates.mania.*
import eu.pb4.sgui.api.ClickType
import net.minecraft.world.inventory.MenuType

/**
 * Cartas (9×6): uma carta virada para cada prêmio já sorteado. O jogador clica e elas viram uma a uma, da raridade
 * menor para a maior (a mais rara por último). Antes de virar, a moldura "sobe" de raridade até a do prêmio (para no
 * prêmio real; nunca mostra uma raridade maior que a dele).
 */
class CardsChest(private val opening: ManiaOpening) : ManiaChest(MenuType.GENERIC_9x6, opening.player), OpeningView {
    private val cfg = Mania.config.abertura
    private val t = Mania.config.textos
    private val crate = opening.crate
    /** Ordem de revelação: raridade crescente. */
    private val order: List<Draw> = opening.draws.sortedBy { it.rarity }.take(21)
    private val slots = Slots.positions(order.size)
    private val revealedAt = arrayOfNulls<Draw>(slots.size)
    private var next = 0
    private var done = false

    /** Carta virando agora: posição, prêmio, ticks. */
    private var flipping: Triple<Int, Draw, Int>? = null
    private val queue = ArrayDeque<Int>()

    init {
        title = ChestKit.text(player, t.tituloCartas.replace("%crate_name%", Mania.crateName(crate)))
    }

    override fun show() {
        val glass = ChestKit.filler(cfg.roleta.vidro)
        for (i in 0 until 54) put(i, glass)
        val back = ChestKit.named(player, ChestKit.item(cfg.cartas.verso), t.carta, t.cartaLore, glint = true)
        slots.forEach { put(it, back) }
        if (order.size > 1) put(49, ChestKit.named(player, ChestKit.item("minecraft:clock"), t.revelarTodas, t.pularLore))
        else put(49, ChestKit.named(player, ChestKit.item("minecraft:clock"), t.pular, t.pularLore))
        open()
    }

    override fun tick() {
        if (done || !isOpen) return
        val f = flipping ?: run {
            val pos = queue.removeFirstOrNull() ?: return
            startFlip(pos)
            return
        }
        val (pos, draw, age) = f
        val total = cfg.cartas.subida.coerceAtLeast(draw.rarity.ordinal + 1)
        val perTier = (total / (draw.rarity.ordinal + 1)).coerceAtLeast(1)
        if (age % perTier == 0) {
            val tier = (age / perTier).coerceAtMost(draw.rarity.ordinal)
            val r = Rarity.entries[tier]
            val glass = ChestKit.rarityGlass(player, r)
            Slots.frame(order.size, slots[pos]).forEach { put(it, glass) }
            sound("minecraft:block.note_block.bell", 0.5f, 0.7f + tier * 0.2f)
        }
        if (age >= total) {
            flipping = null
            reveal(pos, draw)
        } else {
            flipping = Triple(pos, draw, age + 1)
        }
    }

    private fun startFlip(pos: Int) {
        if (revealedAt[pos] != null || next >= order.size) return
        val draw = order[next++]
        revealedAt[pos] = draw
        sound(cfg.cartas.somVirar, 0.8f, 1f)
        flipping = Triple(pos, draw, 0)
    }

    private fun reveal(pos: Int, draw: Draw, quiet: Boolean = false) {
        put(slots[pos], ChestKit.infoItem(player, crate, draw.reward, draw.rarity, RewardInfo.chances(crate, null)[draw.reward.id],
            glint = draw.rarity >= Rarity.EPICO))
        val frame = ChestKit.rarityGlass(player, draw.rarity)
        Slots.frame(order.size, slots[pos]).forEach { put(it, frame) }
        opening.deliver(listOf(draw))
        if (!quiet) Effects.raritySound(player, draw.rarity)
        maybeFinish()
    }

    private fun maybeFinish() {
        if (done || next < order.size || flipping != null) return
        done = true
        renderButtons()
        opening.complete()
    }

    override fun skip() {
        if (done) return
        val current = flipping
        flipping = null
        current?.let { (pos, draw, _) -> reveal(pos, draw, quiet = true) }
        queue.clear()
        for (pos in slots.indices) {
            if (revealedAt[pos] != null) continue
            if (next >= order.size) break
            val draw = order[next++]
            revealedAt[pos] = draw
            reveal(pos, draw, quiet = true)
        }
        maybeFinish()
    }

    private fun renderButtons() {
        val glass = ChestKit.filler(cfg.roleta.vidro)
        for (i in 45 until 54) put(i, glass)
        put(46, ChestKit.named(player, ChestKit.item("minecraft:lime_dye"), t.abrirDeNovo))
        val multi = Mania.multiOf(crate)
        if (multi > 1) put(48, ChestKit.named(player, ChestKit.item("minecraft:gold_ingot"), t.abrirVarias, placeholders = mapOf("%amount%" to multi.toString())))
        put(50, ChestKit.named(player, ChestKit.item("minecraft:spyglass"), t.verPremios))
        put(52, ChestKit.named(player, ChestKit.item("minecraft:barrier"), t.fechar))
    }

    override fun onSlotClick(index: Int, type: ClickType) {
        if (!done) {
            if (type.shift || index == 49) {
                skip()
                return
            }
            val pos = slots.indexOf(index)
            if (pos >= 0 && revealedAt[pos] == null && pos !in queue) {
                if (flipping == null) startFlip(pos) else queue.addLast(pos)
            }
            return
        }
        when (index) {
            46 -> reopen(1)
            48 -> reopen(Mania.multiOf(crate))
            50 -> {
                click()
                closeSilently()
                Actions.preview(player, crate)
            }
            52 -> closeSilently()
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
