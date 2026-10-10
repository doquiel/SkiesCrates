/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Arquivo da Pokémania (fork pokemaniabrasil/SkiesCrates).
 */
package com.pokeskies.skiescrates.data.userdata;

import org.bson.codecs.pojo.annotations.BsonProperty;

/**
 * Prêmio já sorteado e ainda não entregue (diário da abertura).
 *
 * <p>É gravado junto com a retirada das chaves, antes da animação começar. Sai do diário quando o prêmio é
 * entregue. Se o servidor cair no meio, o prêmio é entregue na próxima vez que o jogador entrar.
 */
public class PendingRewardData {
    @BsonProperty
    public String id;
    @BsonProperty
    public String crate;
    @BsonProperty
    public String reward;
    @BsonProperty
    public Long time;

    public PendingRewardData(String id, String crate, String reward, Long time) {
        this.id = id;
        this.crate = crate;
        this.reward = reward;
        this.time = time;
    }

    public PendingRewardData() {}

    @Override
    public String toString() {
        return "PendingRewardData{id='" + id + "', crate='" + crate + "', reward='" + reward + "', time=" + time + '}';
    }
}
