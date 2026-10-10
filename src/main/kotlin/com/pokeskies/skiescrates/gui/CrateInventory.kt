/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Modificado pela Pokémania (fork pokemaniabrasil/SkiesCrates): entrega ao sair do servidor (antes o prêmio se perdia).
 */
package com.pokeskies.skiescrates.gui

import com.pokeskies.skiescrates.SkiesCrates
import com.pokeskies.skiescrates.data.Crate
import com.pokeskies.skiescrates.data.opening.inventory.InventoryOpeningAnimation
import com.pokeskies.skiescrates.data.opening.inventory.InventoryOpeningInstance
import com.pokeskies.skiescrates.data.opening.inventory.spinners.AnimatedSpinnerInstance
import com.pokeskies.skiescrates.data.opening.inventory.spinners.RewardSpinnerInstance
import com.pokeskies.skiescrates.data.rewards.Reward
import com.pokeskies.skiescrates.utils.RandomCollection
import com.pokeskies.skiescrates.utils.TextUtils
import com.pokeskies.skiescrates.utils.Utils
import eu.pb4.sgui.api.gui.SimpleGui
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

class CrateInventory(
    player: ServerPlayer,
    val opening: InventoryOpeningInstance
): SimpleGui(opening.animation.type.type, player, false) {
    private var isFinished = false
    private var closeTicks = 0

    // This is a map of "Animated Preset ID" to a RandomCollection of ItemStacks for that preset
    private val cachedAnimatedPresets: MutableMap<String, RandomCollection<ItemStack>> = mutableMapOf()
    private var animatedSpinners: MutableMap<String, AnimatedSpinnerInstance> = mutableMapOf()

    private var cachedRewardStacks: MutableMap<String, ItemStack> = mutableMapOf()
    private var rewardSpinners: MutableMap<String, RewardSpinnerInstance> = mutableMapOf()

    private val userData = SkiesCrates.INSTANCE.storage.getUser(player)

    private val crate: Crate = opening.crate
    private val animation: InventoryOpeningAnimation = opening.animation
    private var randomBag: RandomCollection<Reward> = opening.randomBag

    init {
        this.title = TextUtils.parseAllNative(player, opening.crate.parsePlaceholders(animation.title))

        animation.items.static.forEach { (id, item) ->
            item.slots.forEach { slot ->
                this.setSlot(slot, item.createItemStack(player))
            }
        }

        // Setup animated spinners
        animation.presets.animations.forEach { (id, presetItems) ->
            val collection = RandomCollection<ItemStack>()
            presetItems.forEach { animatedItem ->
                val itemStack = animatedItem.createItemStack(player)
                collection.add(itemStack, animatedItem.weight.toDouble())
            }
            cachedAnimatedPresets[id] = collection
        }
        animation.items.animated.forEach { (id, spinningItem) ->
            if (spinningItem.preset.isEmpty()) return@forEach
            val bag = cachedAnimatedPresets[spinningItem.preset] ?: run {
                Utils.printError("Animated preset ${spinningItem.preset} not found for spinner $id")
                return@forEach
            }

            animatedSpinners[id] = AnimatedSpinnerInstance(spinningItem, bag).also { it.pregenerate() }
        }

        // Setup rewards spinners
        crate.rewards.forEach { (id, reward) ->
            cachedRewardStacks[id] = reward.getDisplayItem(player, reward.getPlaceholders(userData, crate))
        }
        animation.items.rewards.forEach { (id, item) ->
            val spinner = RewardSpinnerInstance(item, randomBag, animation.winSlots).also {
                it.pregenerate()
            }

            val returnBag = spinner.validateRewards(crate, userData)

            if (returnBag == null) {
                Utils.printError("No rewards were possible for spinner $id in crate ${crate.id} for player ${player.name.string}. Cancelling crate!")
                player.sendMessage(Component.text("An error occurred while opening the crate. Please contact an administrator.", NamedTextColor.RED))
                isFinished = true
                close()
                return@forEach
            }

            randomBag = returnBag
            rewardSpinners[id] = spinner
        }
    }

    fun tick() {
        if (isFinished) {
            if (closeTicks++ >= animation.closeDelay) {
                this.close()
                return
            }
        } else {
            var allCompleted = true
            rewardSpinners.forEach { (_, spinner) ->
                if (spinner.isCompleted()) {
                    return@forEach
                }
                allCompleted = false
                spinner.tick(player, this)
            }

            if (allCompleted) {
                isFinished = true
                rewardSpinners.forEach { (_, data) ->
                    data.giveRewards(player, crate)
                }
                SkiesCrates.INSTANCE.storage.saveUser(userData)
            }
        }

        animatedSpinners.forEach { (_, spinner) ->
            if (spinner.isCompleted()) return@forEach
            spinner.tick(player, this)
        }
    }

    fun updateRewardSlot(slot: Int, reward: Reward) {
        this.setSlot(slot, cachedRewardStacks[reward.id] ?: reward.getDisplayItem(player, reward.getPlaceholders(userData, crate)))
    }

    /** Pokémania: entrega o que falta (o jogador saiu do servidor no meio da animação). */
    fun forceFinish() {
        if (!isFinished) {
            isFinished = true
            rewardSpinners.forEach { (_, data) ->
                data.giveRewards(player, crate)
            }
            SkiesCrates.INSTANCE.storage.saveUser(userData)
        }
        opening.stop()
    }

    override fun onClose() {
        if (!isFinished) {
            if (animation.skippable) {
                isFinished = true
                rewardSpinners.forEach { (_, data) ->
                    data.giveRewards(player, crate)
                }
                SkiesCrates.INSTANCE.storage.saveUser(userData)
            } else {
                this.open()
                return
            }
        }

        opening.stop()
    }
}
