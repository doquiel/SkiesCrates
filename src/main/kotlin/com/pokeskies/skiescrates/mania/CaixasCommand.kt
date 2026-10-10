/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Arquivo da Pokémania (fork pokemaniabrasil/SkiesCrates).
 */
package com.pokeskies.skiescrates.mania

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.pokeskies.skiescrates.SkiesCrates
import com.pokeskies.skiescrates.SkiesCrates.Companion.asyncScope
import com.pokeskies.skiescrates.config.ConfigManager
import com.pokeskies.skiescrates.data.Crate
import com.pokeskies.skiescrates.data.CrateOpenData
import com.pokeskies.skiescrates.managers.CratesManager
import kotlinx.coroutines.launch
import me.lucko.fabric.api.permissions.v0.Permissions
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.SharedSuggestionProvider
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import java.util.*

/**
 * `/caixas` (alias `/caixa`): lista, preview e a preferência de animação. Admin (`skiescrates.command.caixas.admin`):
 * pendentes, simular (confere as chances) e abrir para alguém.
 */
object CaixasCommand {
    private const val ADMIN = "skiescrates.command.caixas.admin"

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        for (alias in listOf("caixas", "caixa")) {
            dispatcher.register(
                Commands.literal(alias)
                    .requires(Permissions.require("skiescrates.command.caixas", 0))
                    .executes { ctx -> ctx.source.playerOrException.let { Actions.list(it) }; 1 }
                    .then(Commands.literal("preview")
                        .then(Commands.argument("caixa", StringArgumentType.word())
                            .suggests { _, b -> SharedSuggestionProvider.suggest(ConfigManager.CRATES.keys, b) }
                            .executes { ctx ->
                                val crate = crateArg(ctx) ?: return@executes 0
                                val player = ctx.source.playerOrException
                                if (Mania.usesNewPreview(crate)) Actions.preview(player, crate) else CratesManager.previewCrate(player, crate)
                                1
                            }))
                    .then(Commands.literal("animacao")
                        .then(Commands.literal("rapida").executes { ctx -> setSkip(ctx, true) })
                        .then(Commands.literal("completa").executes { ctx -> setSkip(ctx, false) }))
                    .then(Commands.literal("admin")
                        .requires(Permissions.require(ADMIN, 2))
                        .then(Commands.literal("pendentes")
                            .then(Commands.argument("jogador", EntityArgument.player()).executes { ctx -> pending(ctx) }))
                        .then(Commands.literal("simular")
                            .then(Commands.argument("caixa", StringArgumentType.word())
                                .suggests { _, b -> SharedSuggestionProvider.suggest(ConfigManager.CRATES.keys, b) }
                                .then(Commands.argument("vezes", IntegerArgumentType.integer(1, 1_000_000))
                                    .executes { ctx -> simulate(ctx) })))
                        .then(Commands.literal("abrir")
                            .then(Commands.argument("jogador", EntityArgument.player())
                                .then(Commands.argument("caixa", StringArgumentType.word())
                                    .suggests { _, b -> SharedSuggestionProvider.suggest(ConfigManager.CRATES.keys, b) }
                                    .executes { ctx -> forceOpen(ctx, 1) }
                                    .then(Commands.argument("vezes", IntegerArgumentType.integer(1, 21))
                                        .executes { ctx -> forceOpen(ctx, IntegerArgumentType.getInteger(ctx, "vezes")) })))))
            )
        }
    }

    private fun crateArg(ctx: CommandContext<CommandSourceStack>): Crate? {
        val id = StringArgumentType.getString(ctx, "caixa")
        val crate = ConfigManager.CRATES[id]
        if (crate == null) ctx.source.sendFailure(Mania.text(ctx.source.player, Mania.config.textos.caixaNaoExiste))
        return crate
    }

    private fun setSkip(ctx: CommandContext<CommandSourceStack>, skip: Boolean): Int {
        val player = ctx.source.playerOrException
        asyncScope.launch {
            Journal.withUserLock(player.uuid) {
                val storage = SkiesCrates.INSTANCE.storage
                val data = storage.getUser(player.uuid)
                data.skipAnimation = if (skip) true else null
                storage.saveUser(data)
            }
            player.server.execute {
                Mania.send(player, if (skip) Mania.config.textos.animacaoRapida else Mania.config.textos.animacaoCompleta)
            }
        }
        return 1
    }

    private fun pending(ctx: CommandContext<CommandSourceStack>): Int {
        val target = EntityArgument.getPlayer(ctx, "jogador")
        val source = ctx.source
        asyncScope.launch {
            val data = SkiesCrates.INSTANCE.storage.getUser(target.uuid)
            val list = data.pending ?: emptyList()
            target.server.execute {
                source.sendSystemMessage(Component.literal("Pendentes de ${target.name.string}: ${list.size}"))
                list.forEach { source.sendSystemMessage(Component.literal(" - ${it.crate}/${it.reward} (${it.id.take(8)})")) }
            }
        }
        return 1
    }

    /** Sorteia N vezes (sem entregar nada) e compara com a chance calculada. */
    private fun simulate(ctx: CommandContext<CommandSourceStack>): Int {
        val crate = crateArg(ctx) ?: return 0
        val times = IntegerArgumentType.getInteger(ctx, "vezes")
        val source = ctx.source
        asyncScope.launch {
            val bag = crate.generateRewardBag(com.pokeskies.skiescrates.data.userdata.UserData(UUID.randomUUID()))
            val counts = HashMap<Rarity, Int>()
            repeat(times) {
                val r = bag.next() ?: return@repeat
                val rarity = Rarities.of(crate, r)
                counts[rarity] = (counts[rarity] ?: 0) + 1
            }
            val chances = RewardInfo.chances(crate, null)
            val expected = HashMap<Rarity, Double>()
            crate.rewards.values.forEach { r -> expected.merge(Rarities.of(crate, r), chances[r.id] ?: 0.0, Double::plus) }
            source.server.execute {
                source.sendSystemMessage(Component.literal("Simulação de ${crate.id}: $times aberturas"))
                for (rarity in Rarity.entries.reversed()) {
                    val e = expected[rarity] ?: continue
                    val got = (counts[rarity] ?: 0).toDouble() / times
                    source.sendSystemMessage(Mania.text(null, " ${rarity.colored} <gray>esperado <white>${RewardInfo.formatChance(e)}" +
                        "</white> · sorteado <white>${RewardInfo.formatChance(got)}"))
                }
            }
        }
        return 1
    }

    /** Abre de graça para o jogador (teste e suporte). Usa a caixa mais perto dele, se houver. */
    private fun forceOpen(ctx: CommandContext<CommandSourceStack>, times: Int): Int {
        val target = EntityArgument.getPlayer(ctx, "jogador")
        val crate = crateArg(ctx) ?: return 0
        val location = Actions.nearby(target, crate)
        asyncScope.launch {
            CratesManager.openCrate(target, crate, CrateOpenData(location, null), true, times, false, true)
        }
        return 1
    }
}
