/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Modificado pela Pokémania (fork pokemaniabrasil/SkiesCrates): campos opcionais da abertura nova.
 */
package com.pokeskies.skiescrates.data

import com.google.gson.annotations.JsonAdapter
import com.pokeskies.skiescrates.utils.FlexibleListAdaptorFactory
import com.google.gson.annotations.SerializedName
import com.pokeskies.skiescrates.config.ConfigManager
import com.pokeskies.skiescrates.config.CostOptions
import com.pokeskies.skiescrates.config.FailureOptions
import com.pokeskies.skiescrates.config.block.BlockOptions
import com.pokeskies.skiescrates.config.item.GenericItem
import com.pokeskies.skiescrates.data.rewards.Reward
import com.pokeskies.skiescrates.data.userdata.UserData
import com.pokeskies.skiescrates.utils.RandomCollection

class Crate(
    val enabled: Boolean = true,
    val name: String = "",
    val display: GenericItem = GenericItem(),
    val unique: Boolean = false,
    val preview: String = "",
    val animation: String = "",
    val permission: String = "",
    @SerializedName("inventory_space")
    val inventorySpace: Int = -1,
    val cost: CostOptions? = null,
    val cooldown: Long = -1,
    val failure: FailureOptions? = null,
    val keys: Map<String, Int> = emptyMap(),
    val block: BlockOptions = BlockOptions(),
    @JsonAdapter(Reward.RewardMapAdapter::class)
    val rewards: MutableMap<String, Reward> = mutableMapOf(),
) {
    // Local variable that is filled in when creating the object
    lateinit var id: String

    // ---- Pokémania: campos opcionais (sem eles, vale o padrão do mania.json) ----

    /** Abertura: "roleta", "cartas" ou "classica" (usa o `animation` antigo). null = `abertura.padrao` do mania.json. */
    @SerializedName("opening", alternate = ["abertura"])
    var opening: String? = null

    /** Raridade das recompensas sem `rarity` (ex: "lendario" numa caixa só de lendários). null = deduzir pelo weight. */
    var rarity: String? = null

    /** Texto do botão "Onde conseguir chave" no preview. null = `preview.ondeConseguir` do mania.json. */
    @SerializedName("keys_info", alternate = ["onde_conseguir"])
    @JsonAdapter(FlexibleListAdaptorFactory::class)
    var keysInfo: List<String>? = null

    /** Link aberto pelo botão "Onde conseguir chave". null = `preview.link` do mania.json. */
    @SerializedName("keys_link")
    var keysLink: String? = null

    /** Abrir várias de uma vez (0 desliga). null = `abertura.multiplas` do mania.json. */
    @SerializedName("multi_open")
    var multiOpen: Int? = null

    /** Anúncio na rede: false desliga nesta caixa. null = `anuncio.ativo` do mania.json. */
    var announce: Boolean? = null

    /** Preview novo: false volta ao preview antigo (`preview`). null = `preview.todas` do mania.json. */
    @SerializedName("new_preview")
    var newPreview: Boolean? = null

    fun parsePlaceholders(string: String): String {
        return string.replace("%crate_name%", name)
            .replace("%crate_id%", id)
            .replace("%crate_keys%", keys.entries.joinToString(", ") { (keyId, amount) ->
                "${ConfigManager.KEYS[keyId]?.name ?: keyId} x$amount"
            })
            .replace("%crate_inventory_space%", inventorySpace.toString())
    }

    fun generateRewardBag(data: UserData): RandomCollection<Reward> {
        val bag = RandomCollection<Reward>()
        for (reward in rewards.values) {
            if (!reward.canReceive(data, this)) continue
            bag.add(reward, reward.weight.toDouble())
        }

        return bag
    }

    override fun toString(): String {
        return "Crate(id='$id', enabled=$enabled, name='$name', display=$display, unique=$unique, preview='$preview', " +
                "animation='$animation', permission='$permission', cost=$cost, cooldown=$cooldown, keys=$keys, " +
                "block=$block, rewards=$rewards)"
    }
}