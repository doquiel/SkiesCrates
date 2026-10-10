/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Arquivo da Pokémania (fork pokemaniabrasil/SkiesCrates).
 */
package com.pokeskies.skiescrates.mania.chest

import com.pokeskies.skiescrates.SkiesCrates
import com.pokeskies.skiescrates.SkiesCrates.Companion.asyncScope
import com.pokeskies.skiescrates.config.ConfigManager
import com.pokeskies.skiescrates.data.Crate
import com.pokeskies.skiescrates.data.rewards.Reward
import com.pokeskies.skiescrates.data.userdata.UserData
import com.pokeskies.skiescrates.mania.*
import com.pokeskies.skiescrates.utils.Utils
import eu.pb4.sgui.api.ClickType
import kotlinx.coroutines.launch
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.MenuType

/** Dados do preview: prêmios em ordem (o mais raro primeiro), raridade, chance real do jogador. */
class PreviewData(val crate: Crate, val data: UserData?) {
    class Entry(val reward: Reward, val rarity: Rarity, val chance: Double?, val available: Boolean)

    val chances: Map<String, Double> = RewardInfo.chances(crate, data)
    val entries: List<Entry> = crate.rewards.values
        .map { r -> Entry(r, Rarities.of(crate, r), chances[r.id], data == null || r.canReceive(data, crate)) }
        .sortedWith(compareByDescending<Entry> { it.rarity }.thenBy { it.chance ?: 0.0 }.thenBy { Effects.rewardName(it.reward) })
    val rarities: List<Rarity> = entries.map { it.rarity }.distinct().sortedDescending()
    val keys: Map<String, Int> = data?.keys ?: emptyMap()

    /** Chance de tirar esta raridade ou melhor numa abertura. */
    fun chanceAtLeast(r: Rarity): Double = entries.filter { it.rarity >= r }.sumOf { it.chance ?: 0.0 }

    fun chanceOf(r: Rarity): Double = entries.filter { it.rarity == r }.sumOf { it.chance ?: 0.0 }

    companion object {
        /** Lê os dados do jogador em segundo plano e chama [then] na thread do servidor. */
        fun load(player: ServerPlayer, crate: Crate, then: (PreviewData) -> Unit) {
            asyncScope.launch {
                val data = try {
                    SkiesCrates.INSTANCE.storage.getUser(player.uuid)
                } catch (e: Exception) {
                    Utils.printError("Erro ao ler os dados de ${player.name.string} para o preview: ${e.message}")
                    null
                }
                player.server.execute { if (!player.hasDisconnected()) then(PreviewData(crate, data)) }
            }
        }
    }
}

/**
 * Preview novo em baú (9×6): uma linha por raridade (a moldura das pontas tem a cor dela), do mais raro para o mais
 * comum, com a chance real em % de cada prêmio e o que vem de fato. Cabeçalho com as chaves; rodapé com filtro,
 * abrir, abrir várias, onde conseguir chave e páginas.
 */
class PreviewChest(player: ServerPlayer, private val view: PreviewData) : ManiaChest(MenuType.GENERIC_9x6, player) {
    private val crate = view.crate
    private val t = Mania.config.textos
    private var filter: Rarity? = null
    private var page = 0

    /** Linhas de prêmios: (raridade, até 7 prêmios). */
    private fun rows(): List<Pair<Rarity, List<PreviewData.Entry>>> {
        val out = mutableListOf<Pair<Rarity, List<PreviewData.Entry>>>()
        for (r in view.rarities) {
            if (filter != null && r != filter) continue
            view.entries.filter { it.rarity == r }.chunked(7).forEach { out += r to it }
        }
        return out
    }

    private fun pages(): Int = ((rows().size + 3) / 4).coerceAtLeast(1)

    init {
        title = ChestKit.text(player, t.tituloPreview.replace("%crate_name%", Mania.crateName(crate)))
        render()
    }

    private fun render() {
        val black = ChestKit.filler("minecraft:black_stained_glass_pane")
        for (i in 0 until 54) put(i, black)
        renderHeader()
        val rows = rows()
        val pageRows = rows.drop(page * 4).take(4)
        pageRows.forEachIndexed { i, (rarity, list) ->
            val base = 9 * (i + 1)
            val frame = ChestKit.rarityGlass(player, rarity, listOf(
                t.chance.replace("%chance%", RewardInfo.formatChance(view.chanceOf(rarity))) + " <dark_gray>(" + rarity.style.nome + ")"
            ))
            put(base, frame)
            put(base + 8, frame)
            list.forEachIndexed { j, e ->
                put(base + 1 + j, ChestKit.infoItem(player, crate, e.reward, e.rarity, e.chance, e.available))
            }
        }
        renderFooter()
    }

    private fun renderHeader() {
        val (have, need) = Actions.keyStatus(crate, view.keys)
        val opens = Actions.opensWith(crate, view.keys)
        val lore = mutableListOf<String>()
        if (crate.keys.isNotEmpty()) {
            lore += t.suasChaves.replace("%keys%", "$have") + if (need > 1) " <dark_gray>($need por abertura)" else ""
        }
        lore += "<gray>Prêmios: <white>${view.entries.size}"
        lore += ""
        for (r in view.rarities) {
            lore += " ${r.colored} <dark_gray>· <white>${RewardInfo.formatChance(view.chanceOf(r))}"
        }
        lore += ""
        lore += t.chancesTitulo
        val display = crate.display.createItemStack(player)
        put(4, ChestKit.named(player, display, "<gold><b>${Mania.crateName(crate)}</b>", lore, glint = opens > 0 && opens != Int.MAX_VALUE))
        val glass = ChestKit.filler("minecraft:gray_stained_glass_pane")
        for (i in listOf(0, 1, 2, 3, 5, 6, 7, 8)) put(i, glass)
    }

    private fun renderFooter() {
        val pages = pages()
        if (page > 0) put(45, ChestKit.named(player, ChestKit.item("minecraft:arrow"), t.paginaAnterior, listOf(t.pagina),
            placeholders = mapOf("%page%" to "${page + 1}", "%pages%" to "$pages")))
        if (page < pages - 1) put(53, ChestKit.named(player, ChestKit.item("minecraft:arrow"), t.proximaPagina, listOf(t.pagina),
            placeholders = mapOf("%page%" to "${page + 1}", "%pages%" to "$pages")))

        val filterName = filter?.colored ?: t.filtroTodas
        val filterLore = listOf("<gray>Clique: próxima raridade", "<gray>Direito: anterior", "") +
            (listOf<Rarity?>(null) + view.rarities).map { r -> (if (r == filter) "<white>▶ " else "<dark_gray>  ") + (r?.colored ?: t.filtroTodas) }
        put(47, ChestKit.named(player, ChestKit.item("minecraft:hopper"), t.filtro, filterLore, mapOf("%filter%" to filterName)))

        val near = Actions.nearby(player, crate) != null
        val opens = Actions.opensWith(crate, view.keys)
        val (have, need) = Actions.keyStatus(crate, view.keys)
        val keyLine = if (crate.keys.isEmpty()) emptyList() else listOf(t.suasChaves.replace("%keys%", "$have") + if (need > 1) " <dark_gray>($need por abertura)" else "")
        when {
            !near -> put(48, ChestKit.named(player, ChestKit.item("minecraft:compass"), t.vaAteACaixa, listOf(Mania.config.preview.ondeFica) + keyLine))
            opens <= 0 -> put(48, ChestKit.named(player, ChestKit.item("minecraft:gray_dye"), t.semChaves, keyLine))
            else -> put(48, ChestKit.named(player, ChestKit.item("minecraft:lime_dye"), t.abrir, keyLine + listOf("<dark_gray>Shift: sem animação")))
        }
        val multi = Mania.multiOf(crate)
        if (multi > 1 && near && opens > 1) {
            val n = minOf(multi, opens)
            put(50, ChestKit.named(player, ChestKit.item("minecraft:gold_ingot"), t.abrirVarias, keyLine + listOf("<dark_gray>Shift: sem animação"),
                mapOf("%amount%" to "$n")))
        }
        put(49, ChestKit.named(player, ChestKit.item("minecraft:barrier"), t.fechar))
        put(51, ChestKit.named(player, ChestKit.item("minecraft:writable_book"), t.ondeConseguir,
            (crate.keysInfo ?: Mania.config.preview.ondeConseguir)))
        put(46, ChestKit.named(player, ChestKit.item("minecraft:tripwire_hook"), t.suasChaves.replace("%keys%", "$have"),
            if (opens == Int.MAX_VALUE) emptyList() else listOf("<gray>Dá para abrir <white>$opens</white> vez(es).")))
        put(52, ChestKit.named(player, ChestKit.item("minecraft:chest"), "<gold>Todas as caixas", listOf("<gray>/caixas")))
    }

    override fun onSlotClick(index: Int, type: ClickType) {
        when (index) {
            45 -> if (page > 0) { page--; click(); render() }
            53 -> if (page < pages() - 1) { page++; click(); render() }
            47 -> {
                val options = listOf<Rarity?>(null) + view.rarities
                val i = options.indexOf(filter)
                filter = if (type.isRight) options[(i - 1 + options.size) % options.size] else options[(i + 1) % options.size]
                page = 0
                click()
                render()
            }
            48, 50 -> {
                click()
                if (Actions.nearby(player, crate) == null) {
                    Mania.send(player, Mania.config.preview.ondeFica)
                    return
                }
                if (Actions.opensWith(crate, view.keys) <= 0) return
                val count = if (index == 50) Mania.multiOf(crate) else 1
                closeSilently()
                Actions.open(player, crate, count, skip = type.shift)
            }
            49 -> closeSilently()
            51 -> {
                click()
                closeSilently()
                Actions.keysInfo(player, crate)
            }
            52 -> {
                click()
                closeSilently()
                Actions.list(player)
            }
        }
    }

    companion object {
        fun open(player: ServerPlayer, crate: Crate) {
            PreviewData.load(player, crate) { data -> PreviewChest(player, data).open() }
        }
    }
}

/** /caixas: todas as caixas com as chaves do jogador; clique abre o preview. */
class ListChest(player: ServerPlayer, private val data: UserData?) : ManiaChest(MenuType.GENERIC_9x6, player) {
    private val crates: List<Crate> = ConfigManager.CRATES.values.filter { it.enabled }.sortedBy { Mania.crateName(it) }
    private val slots = (0 until 54).filter { it / 9 in 1..4 && it % 9 in 1..7 }
    private var page = 0

    init {
        title = ChestKit.text(player, Mania.config.textos.tituloLista)
        render()
    }

    private fun render() {
        val black = ChestKit.filler("minecraft:black_stained_glass_pane")
        for (i in 0 until 54) put(i, black)
        val keys = data?.keys ?: emptyMap()
        crates.drop(page * slots.size).take(slots.size).forEachIndexed { i, crate ->
            val (have, need) = Actions.keyStatus(crate, keys)
            val opens = Actions.opensWith(crate, keys)
            val lore = mutableListOf<String>()
            if (crate.keys.isNotEmpty()) lore += Mania.config.textos.suasChaves.replace("%keys%", "$have") + if (need > 1) " <dark_gray>($need por abertura)" else ""
            lore += "<gray>Prêmios: <white>${crate.rewards.size}"
            lore += ""
            lore += "<yellow>Clique para ver os prêmios e as chances"
            put(slots[i], ChestKit.named(player, crate.display.createItemStack(player), "<gold>${Mania.crateName(crate)}", lore,
                glint = opens in 1 until Int.MAX_VALUE).copyWithCount(1))
        }
        val pages = ((crates.size + slots.size - 1) / slots.size).coerceAtLeast(1)
        if (page > 0) put(45, ChestKit.named(player, ChestKit.item("minecraft:arrow"), Mania.config.textos.paginaAnterior))
        if (page < pages - 1) put(53, ChestKit.named(player, ChestKit.item("minecraft:arrow"), Mania.config.textos.proximaPagina))
        put(49, ChestKit.named(player, ChestKit.item("minecraft:barrier"), Mania.config.textos.fechar))
    }

    override fun onSlotClick(index: Int, type: ClickType) {
        when (index) {
            45 -> if (page > 0) { page--; render() }
            53 -> if ((page + 1) * slots.size < crates.size) { page++; render() }
            49 -> closeSilently()
            else -> {
                val i = slots.indexOf(index)
                if (i < 0) return
                val crate = crates.getOrNull(page * slots.size + i) ?: return
                click()
                closeSilently()
                Actions.preview(player, crate)
            }
        }
    }

    companion object {
        fun open(player: ServerPlayer) {
            asyncScope.launch {
                val data = try { SkiesCrates.INSTANCE.storage.getUser(player.uuid) } catch (e: Exception) { null }
                player.server.execute { if (!player.hasDisconnected()) ListChest(player, data).open() }
            }
        }
    }
}
