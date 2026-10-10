/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Modificado pela Pokémania (fork pokemaniabrasil/SkiesCrates): diário de prêmios pendentes e preferência de
 * animação.
 */
package com.pokeskies.skiescrates.data.userdata;

import com.pokeskies.skiescrates.data.Crate;
import com.pokeskies.skiescrates.data.key.Key;
import com.pokeskies.skiescrates.data.rewards.Reward;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class UserData {
    @BsonProperty("_id")
    public UUID uuid;
    @BsonProperty
    public HashMap<String, CrateData> crates;
    @BsonProperty
    public HashMap<String, Integer> keys;
    // Pokémania: prêmios sorteados e ainda não entregues (ver PendingRewardData). Documentos antigos vêm sem.
    @BsonProperty
    public List<PendingRewardData> pending;
    // Pokémania: true = sempre pular a animação de abertura (/caixas animacao rapida). null = padrão (com animação).
    @BsonProperty
    public Boolean skipAnimation;

    // Sem prefixo get/is de propósito: o codec POJO do Mongo trataria como propriedade e gravaria no documento.
    public List<PendingRewardData> pendingList() {
        if (pending == null) pending = new ArrayList<>();
        return pending;
    }

    public boolean prefersSkip() {
        return skipAnimation != null && skipAnimation;
    }

    public UserData(UUID uuid) {
        this.uuid = uuid;
        this.crates = new HashMap<>();
        this.keys = new HashMap<>();
    }

    public UserData(UUID uuid, HashMap<String, CrateData> crates, HashMap<String, Integer> keys) {
        this.uuid = uuid;
        this.crates = crates;
        this.keys = keys;
    }

    public UserData() {}

    public @Nullable Long getCrateCooldown(Crate crate) {
        return Optional.ofNullable(crates.get(crate.id)).map(data -> data.lastOpen).orElse(null);
    }

    public void addCrateCooldown(Crate crate, Long lastOpen) {
        crates.computeIfAbsent(crate.id, k -> new CrateData(0L, 0, new HashMap<>())).lastOpen = lastOpen;
    }

    public void addCrateUse(Crate crate) {
        crates.computeIfAbsent(crate.id, k -> new CrateData(0L, 0, new HashMap<>())).openCount++;
    }

    public int getRewardLimits(Crate crate, Reward reward) {
        CrateData crateData = crates.get(crate.id);
        if (crateData == null) return 0;
        return crateData.getRewards().getOrDefault(reward.id, new RewardLimitData()).claimed;
    }

    public void addRewardUse(Crate crate, Reward reward) {
        CrateData crateData = crates.computeIfAbsent(crate.id, id -> new CrateData(0L, 0, new HashMap<>()));
        crateData.getRewards().merge(reward.id, new RewardLimitData(1, System.currentTimeMillis()), (oldVal, newVal) -> {
            oldVal.claimed++;
            oldVal.time = System.currentTimeMillis();
            return oldVal;
        });
    }

    public void addKeys(Key key, int amount) {
        keys.merge(key.id, amount, Integer::sum);
    }

    public boolean removeKeys(Key key, int amount) {
        if (!keys.containsKey(key.id) || keys.get(key.id) < amount) return false;
        keys.compute(key.id, (k, v) -> v > amount ? v - amount : null);
        return true;
    }

    public void setKeys(Key key, int amount) {
        keys.put(key.id, amount);
    }

    @Override
    public String toString() {
        return "UserData{" +
                "uuid=" + uuid +
                ", crates=" + crates +
                ", keys=" + keys +
                '}';
    }
}
