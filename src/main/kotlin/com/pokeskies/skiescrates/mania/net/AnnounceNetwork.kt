/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Arquivo da Pokémania (fork pokemaniabrasil/SkiesCrates).
 */
package com.pokeskies.skiescrates.mania.net

import com.pokeskies.skiescrates.SkiesCrates
import com.pokeskies.skiescrates.mania.Mania
import com.pokeskies.skiescrates.utils.Utils
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource

/**
 * Anúncio para os outros servidores pelo Synx Connect (o mesmo do Gladiador e da Ranqueada). Esta classe não importa
 * nada do Connect: só [ConnectAnnounce] importa, e ela só carrega com o mod `connect` instalado.
 */
object AnnounceNetwork {
    const val CHANNEL = "skiescrates/anuncio"

    private var enabled = false

    fun start() {
        if (enabled || !Mania.config.ativo || !Mania.config.anuncio.rede) return
        if (!FabricLoader.getInstance().isModLoaded("connect")) {
            Utils.printInfo("Anúncio das caixas só neste servidor (sem o Synx Connect).")
            return
        }
        try {
            ConnectAnnounce.open { message -> SkiesCrates.INSTANCE.server.execute { receive(message) } }
            enabled = true
            Utils.printInfo("Anúncio das caixas em rede pelo canal $CHANNEL (servidor '${ConnectAnnounce.serverId()}').")
        } catch (e: Throwable) {
            Utils.printError("Não consegui abrir o canal $CHANNEL do Connect: ${e.message}")
        }
    }

    fun stop() {
        enabled = false
    }

    fun publish(lines: List<Component>, sound: String) {
        if (!enabled || lines.isEmpty()) return
        try {
            val access = SkiesCrates.INSTANCE.server.registryAccess()
            val json = lines.map { Component.Serializer.toJson(it, access) }
            ConnectAnnounce.send(NetworkAnnounce(ConnectAnnounce.serverId(), json, sound))
        } catch (e: Throwable) {
            Utils.printError("Falha ao mandar o anúncio da caixa para a rede: ${e.message}")
        }
    }

    private fun receive(message: NetworkAnnounce) {
        if (!enabled || message.origin == ConnectAnnounce.serverId()) return
        val server = SkiesCrates.INSTANCE.server
        val access = server.registryAccess()
        for (json in message.lines) {
            val line = try {
                Component.Serializer.fromJson(json, access)
            } catch (e: Exception) {
                Utils.printError("Anúncio de caixa ilegível vindo de '${message.origin}': ${e.message}")
                null
            } ?: continue
            server.playerList.broadcastSystemMessage(line, false)
        }
        if (message.sound.isNotBlank()) {
            ResourceLocation.tryParse(message.sound)?.let { rl ->
                val event = SoundEvent.createVariableRangeEvent(rl)
                server.playerList.players.forEach { it.playNotifySound(event, SoundSource.MASTER, 0.6f, 1.0f) }
            }
        }
    }
}

/**
 * Mensagem do canal `skiescrates/anuncio`. [lines] = componentes do Minecraft em JSON (cores, hover do item).
 * Qualquer mod da rede pode ouvir este canal com uma classe do mesmo formato (ex: num servidor sem o SkiesCrates).
 */
class NetworkAnnounce(
    val origin: String = "",
    val lines: List<String> = emptyList(),
    val sound: String = "",
)
