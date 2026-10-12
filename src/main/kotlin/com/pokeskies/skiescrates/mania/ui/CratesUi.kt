/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Arquivo da Pokémania (fork pokemaniabrasil/SkiesCrates).
 */
package com.pokeskies.skiescrates.mania.ui

import com.pokeskies.skiescrates.SkiesCrates
import com.pokeskies.skiescrates.SkiesCrates.Companion.asyncScope
import com.pokeskies.skiescrates.config.ConfigManager
import com.pokeskies.skiescrates.data.Crate
import com.pokeskies.skiescrates.data.rewards.Reward
import com.pokeskies.skiescrates.mania.*
import com.pokeskies.skiescrates.mania.chest.PreviewData
import kotlinx.coroutines.launch
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Items
import team.mania.ui.ManiaUI
import team.mania.ui.api.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Telas do ManiaUI das caixas (só carrega com o ManiaUI; ver [UiHook]): preview com os Pokémon em 3D, roleta,
 * cartas, abrir várias e a lista. O estado de cada jogador fica aqui; os menus só leem e desenham.
 */
internal object CratesUi {
    const val PREVIEW_ID = "skiescrates_preview"
    const val OPENING_ID = "skiescrates_abertura"
    const val LIST_ID = "skiescrates_caixas"

    class PreviewState(val data: PreviewData, var filter: Rarity? = null, var page: Int = 0)

    private val previews = ConcurrentHashMap<UUID, PreviewState>()
    private val openings = ConcurrentHashMap<UUID, UiOpeningView>()
    private val lists = ConcurrentHashMap<UUID, Map<String, Int>>()

    fun register() {
        ManiaUI.register(ListMenu)
    }

    fun hasClient(player: ServerPlayer): Boolean = ManiaUI.hasClient(player)

    fun accent(): Int = Rarity.parseRgb(Mania.config.maniaui.cor)

    fun t(player: ServerPlayer?, mini: String, ph: Map<String, String> = emptyMap()): Component = Mania.text(player, mini, ph)

    fun openPreview(player: ServerPlayer, crate: Crate, back: UiMenu? = null) {
        PreviewData.load(player, crate) { data ->
            previews[player.uuid] = PreviewState(data)
            if (back != null) ManiaUI.open(player, PreviewMenu, null, back) else ManiaUI.open(player, PreviewMenu)
        }
    }

    fun openList(player: ServerPlayer, back: UiMenu? = null) {
        asyncScope.launch {
            val keys = try { SkiesCrates.INSTANCE.storage.getUser(player.uuid).keys ?: emptyMap() } catch (e: Exception) { emptyMap() }
            player.server.execute {
                if (player.hasDisconnected()) return@execute
                lists[player.uuid] = HashMap(keys)
                if (back != null) ManiaUI.open(player, ListMenu, null, back) else ManiaUI.open(player, ListMenu)
            }
        }
    }

    fun openingView(opening: ManiaOpening): OpeningView = UiOpeningView(opening).also { openings[opening.player.uuid] = it }

    fun forget(uuid: UUID) {
        previews.remove(uuid)
        openings.remove(uuid)
        lists.remove(uuid)
    }

    fun opening(uuid: UUID): UiOpeningView? = openings[uuid]

    // ---- peças ----

    /** Linhas do tooltip de um prêmio: raridade, chance e o que vem de fato. */
    fun rewardTooltip(player: ServerPlayer, reward: Reward, rarity: Rarity, chance: Double?, available: Boolean = true): List<Component> {
        val tx = Mania.config.textos
        val out = mutableListOf<Component>()
        out += t(player, "<${rarity.style.cor}>${Effects.rewardName(reward)}")
        out += t(player, "<${rarity.style.cor}>■ ${rarity.style.nome}" + if (chance != null) " <dark_gray>· <white>${RewardInfo.formatChance(chance)}" else "")
        RewardInfo.highlight(reward)?.let { out += t(player, it) }
        if (!available) out += t(player, tx.esgotado)
        return out
    }

    /** Um prêmio: Pokémon em 3D (ou card com o item) + etiqueta da raridade. */
    /** Servidor e cliente com o ManiaUI 0.4.2+ (o `itemModel`, item girando no mesmo palco do Pokémon 3D). */
    private fun supportsItemModel(player: ServerPlayer): Boolean {
        fun atLeast(v: String?): Boolean {
            val n = v?.split('.', '-', '+')?.take(3)?.map { it.toIntOrNull() ?: 0 } ?: return false
            val (a, b, c) = (n + listOf(0, 0, 0)).take(3)
            return a > 0 || b > 4 || (b == 4 && c >= 2)
        }
        return try {
            atLeast(ManiaUI.version) && atLeast(ManiaUI.clientVersion(player.uuid))
        } catch (e: LinkageError) {
            false
        }
    }

    /** O item no palco 3D (mesma altura do Pokémon). false = não deu (ManiaUI antigo no servidor ou no cliente). */
    private fun NodesBuilder.itemModelCell(
        player: ServerPlayer, stack: net.minecraft.world.item.ItemStack, height: Int, name: Component,
        tooltip: List<Component>, id: String?, onClick: UiHandler?
    ): Boolean {
        if (!supportsItemModel(player)) return false
        return try {
            ItemModelCompat.add(this, stack, height, name, tooltip, id, onClick)
            true
        } catch (e: LinkageError) {
            false
        }
    }

    fun NodesBuilder.rewardCell(
        player: ServerPlayer, reward: Reward, rarity: Rarity, chance: Double?, height: Int,
        available: Boolean = true, chip: Boolean = true, id: String? = null, onClick: UiHandler? = null
    ) {
        val stack = RewardInfo.icon(player, reward)
        val name = t(player, "<${rarity.style.cor}>${Effects.rewardName(reward)}")
        val tooltip = rewardTooltip(player, reward, rarity, chance, available)
        val ref = RewardInfo.pokemon(reward)
        stack {
            if (ref != null) {
                pokemonModel(ref.species, ref.aspects, UiIcon.item(stack), height, name, 35f, tooltip, id, onClick)
            } else if (!itemModelCell(player, stack, height, name, tooltip, id, onClick)) {
                // Cliente sem o modelo 3D de itens: "tile" de grade, sempre com 2 linhas para todos terem a mesma altura
                val line = RewardInfo.highlight(reward)?.let { t(player, it) } ?: Component.literal(" ")
                grid(1) { item(name, UiIcon.item(stack), listOf(line), null, true, tooltip, null, id, onClick) }
            }
            // A "barra" da raridade embaixo do item (como nas caixas de loot), com a chance real
            if (chip) {
                val label = if (chance != null) RewardInfo.formatChance(chance) else rarity.style.nome
                chips(listOf(UiChip(Component.literal(label), rarity.rgb)))
            }
        }
    }

    // ---- preview ----

    object PreviewMenu : UiMenu {
        override val id = PREVIEW_ID

        /**
         * A vitrine da caixa (ManiaUI 0.5.0, `collection`): prêmios em 3D (Pokémon ou item girando) nos cartões, com a
         * chance real no selo e a cor da raridade; filtro por raridade com a chance somada; o prêmio escolhido grande
         * à esquerda; Abrir, Abrir xN e Onde conseguir chave embaixo da prévia. Sem o cliente 0.5.0, o ManiaUI entrega
         * uma grade comum (ou o baú), com os mesmos botões.
         */
        override fun build(ctx: UiContext): UiScreen {
            val player = ctx.player
            val st = previews[player.uuid] ?: return ctx.screen(t(player, "Caixas")) { text(t(player, "<gray>Carregando...")) }
            val view = st.data
            val crate = ConfigManager.CRATES[view.crate.id] ?: view.crate
            val tx = Mania.config.textos
            val (have, need) = Actions.keyStatus(crate, view.keys)
            val opens = Actions.opensWith(crate, view.keys)
            val subtitle = if (crate.keys.isEmpty()) t(player, tx.chancesTitulo)
                else t(player, "<white>$have" + (if (need > 1) " <gray>/ $need" else "") + " <gray>" + (if (have == 1) "chave" else "chaves"))
            return ctx.screen(t(player, "<white>${Mania.crateName(crate)}"), subtitle, UiIcon.item(crate.display.createItemStack(player)),
                accent(), UiSize.LARGE) {
                collection(ManiaUI.head(player), null, t(player, "<gray>Nenhum prêmio por aqui"), t(player, "<gray>Buscar prêmio")) {
                    filter("", t(player, tx.filtroTodas))
                    for (r in view.rarities) filter(r.id, t(player, r.colored + " <gray>" + RewardInfo.formatChance(view.chanceOf(r))))
                    view.entries.forEachIndexed { i, e ->
                        val ref = RewardInfo.pokemon(e.reward)
                        val stack = RewardInfo.icon(player, e.reward)
                        prize(
                            "r:$i:${e.reward.id}".take(60), t(player, "<${e.rarity.style.cor}>${Effects.rewardName(e.reward)}"),
                            t(player, e.rarity.colored), e.rarity.rgb,
                            if (e.available) UiCollectionState.OWNED else UiCollectionState.LOCKED,
                            ref?.species, ref?.aspects ?: emptyList(), if (ref == null) stack else null, UiIcon.item(stack),
                            e.chance?.let { Component.literal(RewardInfo.formatChance(it)) },
                            listOfNotNull(RewardInfo.highlight(e.reward)?.let { t(player, it) }),
                            if (!e.available) t(player, tx.esgotado) else null, listOf(e.rarity.id)
                        )
                    }
                    val near = Actions.nearby(player, crate) != null
                    val multi = Mania.multiOf(crate)
                    val openLabel = when {
                        !near -> tx.vaAteACaixa
                        opens <= 0 -> tx.semChaves
                        else -> tx.abrir
                    }
                    action(t(player, openLabel), UiStyle.SUCCESS, near && opens > 0,
                        if (!near) listOf(t(player, Mania.config.preview.ondeFica)) else emptyList(), "abrir") { action ->
                        Actions.open(action.player, crate, 1)
                    }
                    if (multi > 1) {
                        // Com 2+ aberturas possíveis mostra quantas abre; senão o máximo da caixa (botão desligado)
                        val n = if (opens > 1) minOf(multi, opens) else multi
                        action(t(player, tx.abrirVarias, mapOf("%amount%" to "$n")), UiStyle.PRIMARY, near && opens > 1, emptyList(), "abrir_varias") { action ->
                            Actions.open(action.player, crate, multi)
                        }
                    }
                    action(t(player, tx.ondeConseguir), UiStyle.SECONDARY, true,
                        (crate.keysInfo ?: Mania.config.preview.ondeConseguir).map { t(player, it) }, "onde") { action ->
                        action.close()
                        Actions.keysInfo(action.player, crate)
                    }
                }
            }
        }
    }

    // ---- lista ----

    object ListMenu : UiMenu {
        override val id = LIST_ID

        /** Todas as caixas na vitrine: a caixa em 3D, as chaves no selo, "Ver prêmios" embaixo da prévia. */
        override fun build(ctx: UiContext): UiScreen {
            val player = ctx.player
            val keys = lists[player.uuid] ?: emptyMap()
            val crates = ConfigManager.CRATES.values.filter { it.enabled }.sortedBy { Mania.crateName(it) }
            return ctx.screen(t(player, "<white>${Mania.config.textos.tituloLista}"), t(player, "<gray>Prêmios e chances de cada caixa"),
                UiIcon.item(Items.CHEST), accent(), UiSize.LARGE) {
                collection(ManiaUI.head(player), null, t(player, "<gray>Nenhuma caixa"), t(player, "<gray>Buscar caixa")) {
                    filter("", t(player, "Todas"))
                    filter("chave", t(player, "Com chave"))
                    for (crate in crates) {
                        val (have, need) = Actions.keyStatus(crate, keys)
                        val opens = Actions.opensWith(crate, keys)
                        val rarity = if (crate.keys.isEmpty()) "<gray>sem chave" else "<gray>Chaves: <white>$have" + if (need > 1) "<dark_gray>/$need" else ""
                        prize(
                            "c:${crate.id}".take(60), t(player, "<white>${Mania.crateName(crate)}"), t(player, rarity), accent(),
                            UiCollectionState.OWNED, stack = crate.display.createItemStack(player),
                            badge = if (opens in 1 until Int.MAX_VALUE) t(player, "<white>x$opens") else null,
                            tags = if (opens > 0) listOf("chave") else emptyList()
                        ) {
                            button(t(player, "<white>Ver prêmios"), UiStyle.PRIMARY, id = "c:${crate.id}".take(60)) { action ->
                                openPreview(action.player, crate, ListMenu)
                            }
                        }
                    }
                }
            }
        }
    }

    // ---- abertura ----

    object OpeningMenu : UiMenu {
        override val id = OPENING_ID
        override val refreshTicks = 2

        override fun build(ctx: UiContext): UiScreen {
            val view = openings[ctx.player.uuid] ?: return ctx.screen(t(ctx.player, "Caixas")) { text(t(ctx.player, "<gray>...")) }
            return view.build(ctx)
        }
    }
}

/**
 * A abertura numa tela do ManiaUI. Mesmas regras do baú: os prêmios já foram sorteados; a faixa e as cartas usam os
 * weights reais; fechar a tela entrega na hora.
 */
internal class UiOpeningView(private val opening: ManiaOpening) : OpeningView {
    private enum class Mode { ROLETA, MULTI, CARTAS }

    private val player = opening.player
    private val crate = opening.crate
    private val cfg = Mania.config.abertura
    private val tx = Mania.config.textos
    private val mode = when {
        opening.style == OpeningStyle.CARTAS -> Mode.CARTAS
        opening.draws.size > 1 -> Mode.MULTI
        else -> Mode.ROLETA
    }
    private var age = 0
    private var done = false
    private var shown = false

    // Roleta: 5 casas visíveis, o centro (2) é onde para
    private val steps = cfg.roleta.passos.coerceIn(8, 200)
    private val strip: List<Reward> = if (mode == Mode.ROLETA) List(steps + 5) { i -> if (i == steps + 2) opening.draws.first().reward else opening.filler() } else emptyList()
    private val delays = IntArray(steps) { i -> 1 + (cfg.roleta.pausaMaxima * (i.toDouble() / steps).pow(cfg.roleta.curva)).roundToInt() }
    private var position = 0
    private var wait = 6

    // Várias e cartas: da raridade menor para a maior
    private val order: List<Draw> = opening.draws.sortedBy { it.rarity }.take(21)
    private val stopAt = IntArray(order.size) { cfg.multipla.inicio + it * cfg.multipla.intervalo }
    private val stopped = BooleanArray(order.size)
    private val spinning = Array(order.size) { opening.filler() }

    // Cartas
    private val revealedAt = arrayOfNulls<Draw>(order.size)
    private var next = 0
    private var flipping: Triple<Int, Draw, Int>? = null
    private val queue = kotlin.collections.ArrayDeque<Int>()

    override fun show() {
        ManiaUI.open(player, CratesUi.OpeningMenu)
        shown = true
    }

    override fun tick() {
        age++
        if (shown && age > 3 && ManiaUI.openMenu(player) != CratesUi.OPENING_ID) {
            // Fechou a tela (ESC) ou outro menu abriu por cima: entrega na hora
            if (!opening.ended) opening.finishNow(ManiaOpening.EndReason.CLOSED)
            return
        }
        if (done) return
        when (mode) {
            Mode.ROLETA -> tickRoulette()
            Mode.MULTI -> tickMulti()
            Mode.CARTAS -> tickCards()
        }
    }

    private fun tickRoulette() {
        if (--wait > 0) return
        position++
        val progress = position.toDouble() / steps
        val pitch = (0.6 + 1.3 * progress * progress).toFloat()
        Effects.sound(player, if (steps - position <= 3) cfg.roleta.somTickFim else cfg.roleta.somTick, 0.6f, pitch)
        if (position >= steps) {
            finish()
            return
        }
        wait = delays[position]
    }

    private fun tickMulti() {
        for (i in order.indices) {
            if (stopped[i]) continue
            if (age >= stopAt[i]) {
                stopped[i] = true
                val d = order[i]
                if (d.rarity >= Rarity.EPICO) Effects.raritySound(player, d.rarity)
                else Effects.sound(player, cfg.roleta.somTickFim, 0.7f, 0.8f + d.rarity.ordinal * 0.25f)
            } else if (age % 2 == 0) spinning[i] = opening.filler()
        }
        if (age % 4 == 0) Effects.sound(player, cfg.roleta.somTick, 0.4f, 0.8f + age / 80f)
        if (stopped.all { it }) finish()
    }

    private fun tickCards() {
        val f = flipping ?: run {
            queue.removeFirstOrNull()?.let { startFlip(it) }
            return
        }
        val (pos, draw, a) = f
        val total = cfg.cartas.subida.coerceAtLeast(draw.rarity.ordinal + 1)
        val perTier = (total / (draw.rarity.ordinal + 1)).coerceAtLeast(1)
        if (a % perTier == 0) Effects.sound(player, "minecraft:block.note_block.bell", 0.5f, 0.7f + (a / perTier).coerceAtMost(draw.rarity.ordinal) * 0.2f)
        if (a >= total) {
            flipping = null
            revealCard(pos, draw)
        } else {
            flipping = Triple(pos, draw, a + 1)
        }
    }

    private fun flipTier(): Rarity? {
        val (_, draw, a) = flipping ?: return null
        val total = cfg.cartas.subida.coerceAtLeast(draw.rarity.ordinal + 1)
        val perTier = (total / (draw.rarity.ordinal + 1)).coerceAtLeast(1)
        return Rarity.entries[(a / perTier).coerceAtMost(draw.rarity.ordinal)]
    }

    private fun startFlip(pos: Int) {
        if (revealedAt[pos] != null || next >= order.size) return
        val draw = order[next++]
        revealedAt[pos] = draw
        Effects.sound(player, cfg.cartas.somVirar, 0.8f, 1f)
        flipping = Triple(pos, draw, 0)
    }

    private fun revealCard(pos: Int, draw: Draw, quiet: Boolean = false) {
        opening.deliver(listOf(draw))
        if (!quiet) Effects.raritySound(player, draw.rarity)
        if (next >= order.size && flipping == null) finish()
    }

    override fun skip() {
        if (done) return
        when (mode) {
            Mode.ROLETA -> position = steps
            Mode.MULTI -> stopped.fill(true)
            Mode.CARTAS -> {
                val current = flipping
                flipping = null
                queue.clear()
                current?.let { (pos, draw, _) -> revealCard(pos, draw, quiet = true) }
                for (pos in order.indices) {
                    if (revealedAt[pos] != null || next >= order.size) continue
                    val draw = order[next++]
                    revealedAt[pos] = draw
                    revealCard(pos, draw, quiet = true)
                }
            }
        }
        finish()
    }

    private fun finish() {
        if (done) return
        done = true
        opening.complete()
    }

    override fun dispose() {
        if (ManiaUI.openMenu(player) == CratesUi.OPENING_ID) ManiaUI.close(player)
    }

    // ---- desenho ----

    fun build(ctx: UiContext): UiScreen {
        val best = opening.best()
        val accent = if (done) best.rgb else CratesUi.accent()
        val title = when (mode) {
            Mode.ROLETA -> tx.tituloRoleta
            Mode.MULTI -> tx.tituloMultipla
            Mode.CARTAS -> tx.tituloCartas
        }.replace("%crate_name%", Mania.crateName(crate)).replace("%amount%", "${opening.draws.size}")
        val subtitle = if (done) CratesUi.t(player, if (opening.draws.size == 1) best.colored else "<white>${opening.draws.size} prêmios")
            else CratesUi.t(player, "<gray>O prêmio já foi sorteado")
        return ctx.screen(CratesUi.t(player, "<white>$title"), subtitle, UiIcon.item(crate.display.createItemStack(player)), accent, UiSize.LARGE) {
            when (mode) {
                Mode.ROLETA -> roulette(this)
                Mode.MULTI -> multi(this)
                Mode.CARTAS -> cards(this)
            }
            footer { buttons(this) }
        }
    }

    private fun roulette(b: NodesBuilder) = with(CratesUi) {
        val draw = opening.draws.first()
        b.text(t(player, if (done) "<${draw.rarity.style.cor}>▼ ${draw.rarity.style.nome} ▼" else "<white>▼"), UiAlign.CENTER)
        val visible = (0 until 5).map { strip[(position + it).coerceAtMost(strip.size - 1)] }
        b.row(listOf(1f, 1f, 1.5f, 1f, 1f)) {
            visible.forEachIndexed { i, reward ->
                val rarity = Rarities.of(crate, reward)
                val center = i == 2
                val chance = if (center && done) RewardInfo.chances(crate, null)[reward.id] else null
                rewardCell(player, reward, rarity, chance, if (center) 92 else 58, chip = center && done)
            }
        }
        b.text(t(player, if (done) "<${draw.rarity.style.cor}>▲" else "<white>▲"), UiAlign.CENTER)
        if (done) RewardInfo.highlight(draw.reward)?.let { b.text(t(player, it), UiAlign.CENTER) }
    }

    private fun multi(b: NodesBuilder) = with(CratesUi) {
        order.indices.chunked(5).forEach { chunk ->
            b.row {
                chunk.forEach { i ->
                    if (stopped[i]) rewardCell(player, order[i].reward, order[i].rarity, null, 54)
                    else rewardCell(player, spinning[i], Rarities.of(crate, spinning[i]), null, 54, chip = false)
                }
                repeat(5 - chunk.size) { spacer(0) }
            }
        }
        if (done) summary(b)
    }

    private fun summary(b: NodesBuilder) = with(CratesUi) {
        b.divider(t(player, "<gray>Resumo"))
        b.row {
            for ((rarity, list) in opening.draws.groupBy { it.rarity }.toSortedMap(compareByDescending { it })) {
                val lines = list.groupBy { it.reward.id }.values.take(4).map { same ->
                    t(player, "<gray>${Effects.rewardName(same.first().reward)}" + if (same.size > 1) " <dark_gray>×${same.size}" else "")
                }
                card(t(player, rarity.colored), lines, null, rarity.rgb, Component.literal("×${list.size}"))
            }
        }
    }

    private fun cards(b: NodesBuilder) = with(CratesUi) {
        val positions = order.indices.toList()
        val single = positions.size == 1
        positions.chunked(5).forEach { chunk ->
            b.row(if (single) listOf(1f, 1.3f, 1f) else List(5) { 1f }) {
                if (single) spacer(0)
                chunk.forEach { pos ->
                    val revealed = revealedAt[pos]
                    val isFlipping = flipping?.first == pos
                    when {
                        revealed != null && !isFlipping -> rewardCell(player, revealed.reward, revealed.rarity, null, if (single) 92 else 56)
                        isFlipping -> {
                            val tier = flipTier() ?: Rarity.COMUM
                            stack {
                                grid(1) { item(t(player, "<${tier.style.cor}><b>✦ ✦ ✦</b>"), UiIcon.item(cfg.cartas.verso)) }
                                chips(listOf(UiChip(Component.literal(tier.style.nome), tier.rgb)))
                            }
                        }
                        else -> grid(1) {
                            item(t(player, "<light_purple><b>?</b>"), UiIcon.item(cfg.cartas.verso), emptyList(), null, true,
                                tx.cartaLore.map { t(player, it) }, null, "carta:$pos") { if (flipping == null) startFlip(pos) else if (pos !in queue) queue.addLast(pos) }
                        }
                    }
                }
                if (single) spacer(0) else repeat(5 - chunk.size) { spacer(0) }
            }
        }
        if (done && order.size > 1) summary(b)
    }

    private fun buttons(b: NodesBuilder) = with(CratesUi) {
        if (!done) {
            val label = if (mode == Mode.CARTAS && order.size > 1) tx.revelarTodas else tx.pular
            b.button(t(player, label), UiStyle.SECONDARY, null, true, tx.pularLore.map { t(player, it) }, null, "pular") { skip() }
            return
        }
        val multi = Mania.multiOf(crate)
        b.button(t(player, tx.abrirDeNovo), UiStyle.SUCCESS, null, true, emptyList(), null, "de_novo") { action ->
            Actions.open(action.player, crate, 1)
        }
        if (multi > 1) b.button(t(player, tx.abrirVarias, mapOf("%amount%" to "$multi")), UiStyle.PRIMARY, null, true, emptyList(), null, "varias") { action ->
            Actions.open(action.player, crate, multi)
        }
        b.button(t(player, tx.verPremios), UiStyle.SECONDARY, null, true, emptyList(), null, "ver") { action ->
            CratesUi.openPreview(action.player, crate)
        }
        b.button(t(player, tx.fechar), UiStyle.DANGER, null, true, emptyList(), null, "fechar") { action -> action.close() }
    }
}
