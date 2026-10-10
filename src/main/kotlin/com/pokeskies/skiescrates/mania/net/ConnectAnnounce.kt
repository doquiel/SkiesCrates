/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Arquivo da Pokémania (fork pokemaniabrasil/SkiesCrates).
 */
package com.pokeskies.skiescrates.mania.net

import games.synx.connect.api.channel.RedisChannel
import games.synx.connect.fabric.ConnectFabricPlugin

/** Único ponto que usa a API do Synx Connect. Só carrega com o mod `connect` instalado (ver [AnnounceNetwork]). */
internal object ConnectAnnounce {
    private lateinit var channel: RedisChannel<NetworkAnnounce>

    fun serverId(): String = ConnectFabricPlugin.getId()

    fun open(onMessage: (NetworkAnnounce) -> Unit) {
        channel = ConnectFabricPlugin.registerChannel(AnnounceNetwork.CHANNEL, NetworkAnnounce::class)
        channel.listen { onMessage(it.message) }
    }

    fun send(message: NetworkAnnounce) {
        channel.send(message)
    }
}
