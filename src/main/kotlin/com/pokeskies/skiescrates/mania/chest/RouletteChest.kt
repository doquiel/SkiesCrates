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
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Roleta em baú (9×4):
 * ```
 *  ▒ ▒ ▒ ▒ ▼ ▒ ▒ ▒ ▒      moldura (vira a cor da raridade no fim) e o marcador
 *  ■ ■ ■ ■ ■ ■ ■ ■ ■      a faixa andando para a esquerda; o centro (13) é o prêmio
 *  ▒ ▒ ▒ ▒ ▲ ▒ ▒ ▒ ▒
 *  · ↻ · ⇉ ★ ◎ · ✕ ·      botões (durante: pular)
 * ```
 * A faixa usa prêmios sorteados com os mesmos weights reais da caixa; só a casa onde ela para é o prêmio já sorteado.
 * Cada passo atualiza só as 9 casas da faixa.
 */
class RouletteChest(private val opening: ManiaOpening) : ManiaChest(MenuType.GENERIC_9x4, opening.player), OpeningView {
    private val cfg = Mania.config.abertura.roleta
    private val t = Mania.config.textos
    private val draw = opening.draws.first()
    private val crate = opening.crate
    private val steps = cfg.passos.coerceIn(8, 200)
    private val strip: List<Reward> = List(steps + 9) { i -> if (i == steps + 4) draw.reward else opening.filler() }
    private val delays: IntArray = IntArray(steps) { i -> 1 + (cfg.pausaMaxima * (i.toDouble() / steps).pow(cfg.curva)).roundToInt() }
    private val stackCache = HashMap<String, ItemStack>()

    private var position = 0
    private var wait = 0
    private var done = false

    init {
        title = ChestKit.text(player, t.tituloRoleta.replace("%crate_name%", Mania.crateName(crate)))
    }

    override fun show() {
        val glass = ChestKit.filler(cfg.vidro)
        for (i in 0 until 36) put(i, glass)
        put(4, ChestKit.named(player, ChestKit.item(cfg.marcador), "<white>▼"))
        put(22, ChestKit.named(player, ChestKit.item(cfg.marcador), "<white>▲"))
        put(31, ChestKit.named(player, ChestKit.item("minecraft:clock"), t.pular, t.pularLore))
        renderStrip()
        wait = 6
        open()
    }

    private fun stackOf(reward: Reward): ItemStack = stackCache.getOrPut(reward.id) {
        ChestKit.reelItem(player, crate, reward, Rarities.of(crate, reward))
    }

    private fun renderStrip() {
        for (i in 0 until 9) put(9 + i, stackOf(strip[position + i]))
    }

    override fun tick() {
        if (done || !isOpen) return
        if (--wait > 0) return
        position++
        renderStrip()
        val progress = position.toDouble() / steps
        val pitch = (0.6 + 1.3 * progress * progress).toFloat()
        if (steps - position <= 3) sound(cfg.somTickFim, 0.7f, pitch) else sound(cfg.somTick, 0.6f, pitch)
        if (position >= steps) {
            finish()
            return
        }
        wait = delays[position]
    }

    override fun skip() {
        if (done) return
        position = steps
        renderStrip()
        finish()
    }

    private fun finish() {
        if (done) return
        done = true
        val rarity = draw.rarity
        val frame = ChestKit.rarityGlass(player, rarity)
        for (i in 0 until 9) {
            if (i != 4) put(i, frame)
            if (i != 4) put(18 + i, frame)
        }
        put(4, ChestKit.named(player, ChestKit.item(cfg.marcador), "<${rarity.style.cor}>▼ ${rarity.style.nome} ▼"))
        put(22, ChestKit.named(player, ChestKit.item(cfg.marcador), "<${rarity.style.cor}>▲ ${rarity.style.nome} ▲"))
        val chance = RewardInfo.chances(crate, null)[draw.reward.id]
        put(13, ChestKit.infoItem(player, crate, draw.reward, rarity, chance, glint = true))
        renderButtons()
        opening.complete()
    }

    private fun renderButtons() {
        val glass = ChestKit.filler(cfg.vidro)
        for (i in 27 until 36) put(i, glass)
        put(28, ChestKit.named(player, ChestKit.item("minecraft:lime_dye"), t.abrirDeNovo))
        val multi = Mania.multiOf(crate)
        if (multi > 1) put(30, ChestKit.named(player, ChestKit.item("minecraft:gold_ingot"), t.abrirVarias, placeholders = mapOf("%amount%" to multi.toString())))
        val chance = RewardInfo.chances(crate, null)[draw.reward.id]
        put(31, ChestKit.infoItem(player, crate, draw.reward, draw.rarity, chance))
        put(32, ChestKit.named(player, ChestKit.item("minecraft:spyglass"), t.verPremios))
        put(34, ChestKit.named(player, ChestKit.item("minecraft:barrier"), t.fechar))
    }

    override fun onSlotClick(index: Int, type: ClickType) {
        if (!done) {
            if (type.shift || index == 31) skip()
            return
        }
        when (index) {
            28 -> reopen(1)
            30 -> reopen(Mania.multiOf(crate))
            32 -> {
                click()
                closeSilently()
                Actions.preview(player, crate)
            }
            34 -> closeSilently()
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
