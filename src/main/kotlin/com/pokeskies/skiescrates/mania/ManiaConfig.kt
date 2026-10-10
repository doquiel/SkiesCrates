/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Arquivo da Pokémania (fork pokemaniabrasil/SkiesCrates).
 */
package com.pokeskies.skiescrates.mania

import com.google.gson.annotations.SerializedName

/**
 * `config/skiescrates/mania.json`: tudo da abertura nova, do preview novo e do anúncio na rede.
 * Fica fora do `config.json` (que tem os dados do banco). Todo campo tem padrão: arquivo faltando ou campo faltando
 * = o padrão daqui.
 */
class ManiaConfig(
    /** false desliga tudo da Pokémania: as caixas voltam a funcionar exatamente como no SkiesCrates 1.0.4. */
    val ativo: Boolean = true,
    val preview: PreviewOptions = PreviewOptions(),
    val abertura: OpeningOptions = OpeningOptions(),
    /** Raridades, da mais comum para a mais rara. As chaves são fixas; o resto (nome, cor, som...) é livre. */
    val raridades: Map<String, RarityStyle> = RarityStyle.defaults(),
    val deducao: Deduction = Deduction(),
    val anuncio: AnnounceOptions = AnnounceOptions(),
    val mundo: WorldOptions = WorldOptions(),
    val maniaui: ManiaUiOptions = ManiaUiOptions(),
    val textos: Texts = Texts(),
) {
    class PreviewOptions(
        /** true = todas as caixas usam o preview novo (a caixa pode recusar com "new_preview": false). */
        val todas: Boolean = true,
        /** Texto do botão "Onde conseguir chave" (a caixa pode trocar com "keys_info"). */
        @SerializedName("ondeConseguir")
        val ondeConseguir: List<String> = listOf(
            "<gray>Chaves vêm da <white>loja do site</white>, dos <white>kits VIP</white>,",
            "<gray>de eventos e de recompensas do servidor."
        ),
        /** Link aberto no chat pelo botão "Onde conseguir chave" ("" = sem link). A caixa pode trocar com "keys_link". */
        val link: String = "",
        /** Distância máxima (em blocos) da caixa para os botões Abrir funcionarem. */
        val distanciaAbrir: Double = 8.0,
        /** Onde a caixa fica, para quem abriu o preview longe dela. */
        val ondeFica: String = "<gray>Vá até a caixa para abrir: <white>/warp caixas</white>",
    )

    class OpeningOptions(
        /** Abertura das caixas que não dizem "opening": "roleta", "cartas" ou "classica" (a animação antiga). */
        val padrao: String = "roleta",
        /** Abrir várias: quantas no máximo por clique (0 desliga). A caixa pode trocar com "multi_open". */
        val multiplas: Int = 10,
        /** Shift + clique direito no bloco (ou shift+clique no menu) abre sem animação. */
        val pularComShift: Boolean = true,
        val roleta: RouletteOptions = RouletteOptions(),
        val cartas: CardsOptions = CardsOptions(),
        val multipla: MultiOptions = MultiOptions(),
    )

    class RouletteOptions(
        /** Quantos itens passam pelo marcador até parar. */
        val passos: Int = 34,
        /** Pausa máxima (ticks) entre um passo e outro, no fim da desaceleração. */
        val pausaMaxima: Int = 9,
        /** Curva da desaceleração (maior = acelera mais tempo e freia mais no fim). */
        val curva: Double = 3.0,
        val somTick: String = "minecraft:block.note_block.hat",
        val somTickFim: String = "minecraft:block.note_block.pling",
        val marcador: String = "minecraft:hopper",
        val vidro: String = "minecraft:black_stained_glass_pane",
    )

    class CardsOptions(
        /** Item do verso da carta. */
        val verso: String = "minecraft:knowledge_book",
        /** Ticks da "subida de raridade" antes de cada carta virar. */
        val subida: Int = 14,
        val somVirar: String = "minecraft:item.book.page_turn",
    )

    class MultiOptions(
        /** Ticks até a primeira casa parar. */
        val inicio: Int = 16,
        /** Ticks entre uma casa parar e a próxima. */
        val intervalo: Int = 5,
    )

    class Deduction(
        /**
         * Sem "rarity", a raridade sai do weight do prêmio dividido pelo maior weight da caixa.
         * Valores = o mínimo dessa razão para cada raridade (abaixo de todos = mítico).
         */
        val comum: Double = 0.75,
        val incomum: Double = 0.45,
        val raro: Double = 0.20,
        val epico: Double = 0.08,
        val lendario: Double = 0.03,
    )

    class AnnounceOptions(
        val ativo: Boolean = true,
        /** Raridade mínima anunciada (o dono pediu todas, com mais destaque nas raras). */
        val minimo: String = "comum",
        /** Manda o anúncio aos outros servidores pelo Synx Connect (canal skiescrates/anuncio). */
        val rede: Boolean = true,
        /**
         * Comandos dos prêmios que são pulados na abertura nova, porque o anúncio novo já faz o papel
         * (os configs antigos têm "announce chat ..." em todo prêmio).
         */
        val pularComandos: List<String> = listOf("announce ", "broadcast "),
        /** Abrir várias: até quantos prêmios aparecem no hover do resumo. */
        val resumoMaximo: Int = 12,
    )

    class WorldOptions(
        /** Item do prêmio subindo em cima do bloco da caixa (display entity, só visual). */
        val itemSubindo: Boolean = true,
        /** Partículas da raridade em volta do bloco. */
        val particulas: Boolean = true,
        /** Fogos a partir desta raridade ("" desliga). */
        val fogosAPartirDe: String = "epico",
        /** Animação do totem na tela de quem abriu, a partir desta raridade ("" desliga). */
        val totemAPartirDe: String = "lendario",
        /** Título na tela de quem abriu, a partir desta raridade ("" desliga). */
        val tituloAPartirDe: String = "raro",
    )

    class ManiaUiOptions(
        /** Usa as telas do ManiaUI (Pokémon em 3D) para quem tem o cliente. Sem o cliente, sempre o baú. */
        val ativo: Boolean = true,
        /** Registra o app "Caixas" no Pokélógio. */
        val app: Boolean = true,
        /** Cor da tela (RGB em hex). */
        val cor: String = "#E8B23A",
    )

    class Texts(
        val tituloRoleta: String = "%crate_name%",
        val tituloCartas: String = "%crate_name% · cartas",
        val tituloMultipla: String = "%crate_name% · x%amount%",
        val tituloPreview: String = "%crate_name%",
        val tituloLista: String = "Caixas",
        val pular: String = "<yellow>⏩ Pular animação",
        val pularLore: List<String> = listOf("<gray>Shift + clique em qualquer lugar.", "<gray>O prêmio já foi sorteado."),
        val abrirDeNovo: String = "<green><b>Abrir de novo</b>",
        val abrirVarias: String = "<gold><b>Abrir x%amount%</b>",
        val abrir: String = "<green><b>Abrir</b>",
        val fechar: String = "<red>Fechar",
        val verPremios: String = "<aqua>Ver prêmios",
        val semChaves: String = "<red>Sem chaves",
        val vaAteACaixa: String = "<yellow>Vá até a caixa",
        val suasChaves: String = "<gold>Suas chaves: <white>%keys%",
        val ondeConseguir: String = "<aqua>Onde conseguir chave",
        val filtro: String = "<yellow>Filtro: <white>%filter%",
        val filtroTodas: String = "Todas",
        val paginaAnterior: String = "<green>◀ Página anterior",
        val proximaPagina: String = "<green>Próxima página ▶",
        val pagina: String = "<gray>Página %page%/%pages%",
        val carta: String = "<light_purple>✦ Carta ✦",
        val cartaLore: List<String> = listOf("<gray>Clique para revelar."),
        val revelarTodas: String = "<yellow>Revelar todas",
        val premio: String = "<gray>Prêmio:",
        val chance: String = "<gray>Chance: <white>%chance%",
        val umEm: String = "<dark_gray>(1 em %n%)",
        val esgotado: String = "<red>Você já pegou o máximo deste prêmio.",
        val ganhou: String = "<gray>Você ganhou <white>%reward%</white> <gray>(%rarity_colored%<gray>) na <white>%crate_name%</white>.",
        val ganhouVarios: String = "<gray>Você abriu <white>%amount%x</white> a <white>%crate_name%</white>:",
        val linhaResumo: String = " %rarity_colored% <dark_gray>×%count% <gray>%rewards%",
        val pendentesEntregues: String = "<green>Você tinha <white>%amount%</white> prêmio(s) de caixa para receber. Entregues!",
        val entregueAoFechar: String = "<gray>Menu fechado: o prêmio já foi entregue.",
        val tituloPremio: String = "%rarity_colored%",
        val subtituloPremio: String = "<white>%reward%",
        val animacaoRapida: String = "<green>Animação das caixas: <white>rápida</white> (sem animação).",
        val animacaoCompleta: String = "<green>Animação das caixas: <white>completa</white>.",
        val caixaNaoExiste: String = "<red>Essa caixa não existe.",
        val chancesTitulo: String = "<gray>Chance de cada prêmio, calculada dos pesos reais.",
        val semPermissao: String = "<red>Você não pode abrir essa caixa.",
        val raridade: String = "%rarity_colored%",
        // Anúncio na rede (MiniMessage). %player%, %crate_name%, %reward% (com hover do item), %rarity_colored%.
        val anuncio: Map<String, List<String>> = mapOf(
            "comum" to listOf("<dark_gray>✦ <gray>%player% tirou %reward% <dark_gray>na %crate_name%"),
            "incomum" to listOf("<dark_gray>✦ <gray>%player% tirou %reward% <dark_gray>(%rarity_colored%<dark_gray>) na %crate_name%"),
            "raro" to listOf("<blue>✦ <white>%player%</white> <gray>tirou %reward% <gray>(%rarity_colored%<gray>) na <white>%crate_name%"),
            "epico" to listOf("<light_purple>✦✦ <white><b>%player%</b></white> <gray>tirou %reward% <gray>(%rarity_colored%<gray>) na <white>%crate_name%</white> <light_purple>✦✦"),
            "lendario" to listOf(
                "",
                "<gold>  ★ <b>LENDÁRIO!</b> ★",
                "<white>  <b>%player%</b></white> <gray>tirou %reward% <gray>na <white>%crate_name%</white>!",
                ""
            ),
            "mitico" to listOf(
                "",
                "<gradient:#FF4F8B:#FFD84F>  ✦✦✦ <b>MÍTICO!</b> ✦✦✦</gradient>",
                "<white>  <b>%player%</b></white> <gray>tirou %reward% <gray>na <white>%crate_name%</white>!",
                ""
            ),
        ),
        val anuncioVarios: List<String> = listOf("<dark_gray>✦ <gray>%player% abriu <white>%amount%x</white> a %crate_name% <dark_gray>· %summary% <dark_gray>(passe o mouse)"),
    )
}
