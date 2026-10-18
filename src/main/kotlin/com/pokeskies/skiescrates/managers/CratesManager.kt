/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Modificado pela Pokémania (fork pokemaniabrasil/SkiesCrates): abrir várias, pular animação, trava por jogador,
 * diário dos prêmios e a abertura/preview novos (pacote mania).
 */
package com.pokeskies.skiescrates.managers

import com.pokeskies.skiescrates.SkiesCrates
import com.pokeskies.skiescrates.SkiesCrates.Companion.asyncScope
import com.pokeskies.skiescrates.config.ConfigManager
import com.pokeskies.skiescrates.config.Lang
import com.pokeskies.skiescrates.config.block.CrateBlockLocation
import com.pokeskies.skiescrates.data.Crate
import com.pokeskies.skiescrates.data.CrateInstance
import com.pokeskies.skiescrates.data.CrateOpenData
import com.pokeskies.skiescrates.data.DimensionalBlockPos
import com.pokeskies.skiescrates.data.opening.inventory.InventoryOpeningAnimation
import com.pokeskies.skiescrates.data.opening.inventory.InventoryOpeningInstance
import com.pokeskies.skiescrates.data.opening.world.WorldOpeningAnimation
import com.pokeskies.skiescrates.data.opening.world.WorldOpeningInstance
import com.pokeskies.skiescrates.economy.EconomyManager
import com.pokeskies.skiescrates.events.ItemSwingEvent
import com.pokeskies.skiescrates.gui.PreviewInventory
import com.pokeskies.skiescrates.integrations.bil.BILCrateData
import com.pokeskies.skiescrates.mania.Actions
import com.pokeskies.skiescrates.mania.Journal
import com.pokeskies.skiescrates.mania.Mania
import com.pokeskies.skiescrates.mania.ManiaOpening
import com.pokeskies.skiescrates.mania.OpeningStyle
import com.pokeskies.skiescrates.utils.ChunkKey
import com.pokeskies.skiescrates.utils.MinecraftDispatcher
import com.pokeskies.skiescrates.utils.TextUtils
import com.pokeskies.skiescrates.utils.Utils
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.lucko.fabric.api.permissions.v0.Permissions
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.core.component.DataComponentPatch
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.phys.Vec3
import java.util.*

object CratesManager {
    const val CRATE_IDENTIFIER: String = "${SkiesCrates.MOD_ID}:crate"

    // Chunk -> List of Crate Instances in that chunk
    private val instancesByChunk: MutableMap<ChunkKey, MutableMap<DimensionalBlockPos, CrateInstance>> = mutableMapOf()
    private val interactionLimiter = mutableMapOf<UUID, Long>()

    fun init() {
        // destroy existing instances and load from config
        instancesByChunk.values.forEach { chunkInstances ->
            chunkInstances.values.forEach { it.destroy() }
        }
        instancesByChunk.clear()

        ConfigManager.CRATES.forEach { (_, crate) ->
            loadCrate(crate)
        }

        registerEvents()
    }

    fun registerEvents() {
        ServerPlayConnectionEvents.JOIN.register(ServerPlayConnectionEvents.Join { handler, _, _ ->
            OpeningManager.getInstance(handler.player.uuid)?.stop()
        })

        // Preventing block breaking
        PlayerBlockBreakEvents.BEFORE.register(PlayerBlockBreakEvents.Before { level, _, blockPos, _, _ ->
            val dimensionalPos = DimensionalBlockPos(
                level.dimension().location().toString(),
                blockPos.x,
                blockPos.y,
                blockPos.z
            )
            getCrateFromPos(dimensionalPos)?.let { _ ->
                return@Before false
            }
            return@Before true
        })
        // Initially attempting to break a block
        AttackBlockCallback.EVENT.register(AttackBlockCallback { player, level, _, blockPos, _ ->
            if (player !is ServerPlayer) return@AttackBlockCallback InteractionResult.PASS

            val dimensionalPos = DimensionalBlockPos(
                level.dimension().location().toString(),
                blockPos.x,
                blockPos.y,
                blockPos.z
            )
            getCrateFromPos(dimensionalPos)?.let { instance ->
                previewCrate(player, instance.crate)
                return@AttackBlockCallback InteractionResult.FAIL
            }
            return@AttackBlockCallback InteractionResult.PASS
        })
        // Called when right clicking a block, whether you use an item or not
        UseBlockCallback.EVENT.register(UseBlockCallback { player, level, hand, blockHitResult ->
            if (player !is ServerPlayer) return@UseBlockCallback InteractionResult.PASS
            if (hand != InteractionHand.MAIN_HAND) return@UseBlockCallback InteractionResult.PASS

            // Detect for a crate block
            val blockPos = DimensionalBlockPos(
                level.dimension().location().toString(),
                blockHitResult.blockPos.x,
                blockHitResult.blockPos.y,
                blockHitResult.blockPos.z
            )
            getCrateFromPos(blockPos)?.let { instance ->
                // Pokémania: shift + clique direito abre sem animação
                val skip = player.isShiftKeyDown && Mania.config.abertura.pularComShift
                asyncScope.launch {
                    openCrate(player, instance.crate, CrateOpenData(blockPos, null), false, 1, skip)
                }
                return@UseBlockCallback InteractionResult.FAIL
            }

            // Detect for a crate in hand to prevent the placement and attempt to open the crate
            val item = player.getItemInHand(hand)
            if (!item.isEmpty) {
                val crate = getCrateOrNull(item)
                if (crate != null) {
                    asyncScope.launch {
                        openCrate(player, crate, CrateOpenData(null, item), false)
                    }
                    return@UseBlockCallback InteractionResult.FAIL
                }

                // Prevent placing keys
                val keyId = KeyManager.getKeyOrNull(item)
                if (keyId != null) {
                    return@UseBlockCallback InteractionResult.FAIL
                }
            }

            return@UseBlockCallback InteractionResult.PASS
        })
        // Called when Right Clicking with an item/block in hand.
        // We need to detect for a crate in hand here, not key as that is handled by UseBlockCallback
        UseItemCallback.EVENT.register(UseItemCallback { player, _, hand ->
            if (player !is ServerPlayer) return@UseItemCallback InteractionResultHolder.pass(player.getItemInHand(hand))

            val item = player.getItemInHand(hand)
            if (hand != InteractionHand.MAIN_HAND) return@UseItemCallback InteractionResultHolder.pass(item)

            getCrateOrNull(item)?.let { crate ->
                asyncScope.launch {
                    openCrate(player, crate, CrateOpenData(null, item), false)
                }
                return@UseItemCallback InteractionResultHolder.fail(item)
            }

            KeyManager.getKeyOrNull(item)?.let { _ ->
                return@UseItemCallback InteractionResultHolder.fail(item)
            }

            return@UseItemCallback InteractionResultHolder.pass(item)
        })
        ItemSwingEvent.EVENT.register { player, itemStack, _ ->
            SkiesCrates.INSTANCE.server.execute { // Ensure we are on the main thread
                val crate = getCrateOrNull(itemStack) ?: return@execute
                previewCrate(player, crate)
            }

            return@register InteractionResult.PASS
        }

        ServerChunkEvents.CHUNK_LOAD.register { level, chunk ->
            // Filters instances that have bilData but are not attached
            val unattached = getInstancesAtChunk(level, chunk).filter {
                it.bilData?.let { bilData -> !bilData.isAttached() } ?: false
            }

            for (instance in unattached) {
                instance.model?.let { modelOptions ->
                    instance.bilData = BILCrateData.create(instance, chunk, modelOptions)
                }
            }
        }
    }

    fun tick() {
        instancesByChunk.forEach { (_, chunkInstances) -> chunkInstances.values.forEach { it.tick() } }
    }

    fun loadCrate(crate: Crate) {
        if (!crate.enabled) return
        // Servidor só de chaves (realms): as coordenadas das caixas são do mapa do hub.
        if (!Mania.config.blocos) return
        for (blockLocation in crate.block.locations) {
            loadCrateLocation(crate, blockLocation)
        }
    }

    fun loadCrateLocation(crate: Crate, blockLocation: CrateBlockLocation): CrateInstance? {
        val location = blockLocation.getDimensionalBlockPos()

        val level = Utils.getLevel(location.dimension)
        if (level == null) {
            Utils.printError("Crate ${crate.name} has an invalid dimension location: $location")
            return null
        }

        val key = ChunkKey.of(location)
        val chunkInstances = getChunkInstances(key)
        if (chunkInstances.containsKey(location)) {
            Utils.printError("Crate ${crate.name} has a duplicate location: $location")
            return null
        }

        val instance = CrateInstance(
            crate,
            level,
            location.getBlockPos(),
            location,
            blockLocation.model ?: crate.block.model,
            blockLocation.hologram ?: crate.block.hologram,
            ConfigManager.PARTICLES[blockLocation.particles ?: crate.block.particle]
        )
        chunkInstances[location] = instance
        return instance
    }

    fun unloadCrateLocation(instance: CrateInstance): Boolean {
        instance.destroy()
        val key = ChunkKey.of(instance.dimPos)
        val chunkInstances = instancesByChunk[key] ?: return false
        val removed = chunkInstances.remove(instance.dimPos) != null
        if (chunkInstances.isEmpty()) instancesByChunk.remove(key) // Remove chunk from map if the instances is empty now
        return removed
    }

    fun giveCrates(crate: Crate, player: ServerPlayer, amount: Int, silent: Boolean = false): Boolean {
        val item = crate.display.createItemStack(player)

        // TODO: Update amount to checking unique
        item.count = amount

        // Apply custom data to identify as a crate
        val tag = CompoundTag()
        tag.putString(CRATE_IDENTIFIER, crate.id)
        item.applyComponents(
            DataComponentPatch.builder()
            .set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
            .build())

        // Add to player's inventory
        player.inventory.placeItemBackInInventory(item)

        if (!silent) {
            Lang.CRATE_GIVE.forEach {
                player.sendMessage(TextUtils.parseAllNative(player, crate.parsePlaceholders(it)))
            }
        }

        return true
    }

    // This method is massive, but it handles a lot of things!
    suspend fun openCrate(player: ServerPlayer, crate: Crate, openData: CrateOpenData, isForced: Boolean): Boolean =
        openCrate(player, crate, openData, isForced, 1, false, false)

    /**
     * Pokémania: [count] = quantas abrir de uma vez (limitado pelas chaves e pelo saldo; só na abertura nova),
     * [skip] = sem animação, [fromMenu] = clique num menu (o menu já tem o próprio anti-spam).
     */
    suspend fun openCrate(
        player: ServerPlayer, crate: Crate, openData: CrateOpenData, isForced: Boolean,
        count: Int, skip: Boolean = false, fromMenu: Boolean = false
    ): Boolean {
        // TODO: Check crate validity
        if (!fromMenu) {
            interactionLimiter[player.uuid]?.let {
                if ((it + ConfigManager.CONFIG.interactionLimiter) > System.currentTimeMillis())
                    return false
            }
        }

        interactionLimiter[player.uuid] = System.currentTimeMillis()

        // Check if the player is already opening a crate
        if (OpeningManager.getInstance(player.uuid) != null) {
            handleCrateFail(player, crate, openData)
            Lang.ERROR_ALREADY_OPENING.forEach {
                player.sendMessage(TextUtils.parseAllNative(player, crate.parsePlaceholders(
                    it
                )))
            }
            return false
        }

        // Permission check
        if (!isForced && crate.permission.isNotEmpty() && !Permissions.check(player, crate.permission)) {
            handleCrateFail(player, crate, openData)
            Lang.ERROR_NO_PERMISSION.forEach {
                player.sendMessage(TextUtils.parseAllNative(player, crate.parsePlaceholders(
                    it
                )))
            }
            return false
        }

        // Inventory space check
        if (!isForced && crate.inventorySpace > 0 && player.inventory.items.count { it.isEmpty } < crate.inventorySpace) {
            handleCrateFail(player, crate, openData)
            Lang.ERROR_INVENTORY_SPACE.forEach {
                player.sendMessage(TextUtils.parseAllNative(player, crate.parsePlaceholders(
                    it
                )))
            }
            return false
        }

        // Balance check
        if (crate.cost != null && crate.cost.amount > 0) {
            val service = EconomyManager.getService(crate.cost.provider) ?: run {
                handleCrateFail(player, crate, openData)
                Utils.printError("Crate ${crate.id} has an invalid economy provider '${crate.cost.provider}'. Valid providers are: ${EconomyManager.getServices().keys.joinToString(", ")}")
                Lang.ERROR_ECONOMY_PROVIDER.forEach {
                    player.sendMessage(TextUtils.parseAllNative(player, crate.parsePlaceholders(
                        it
                    )))
                }
                return false
            }
            if (service.balance(player, crate.cost.currency) < crate.cost.amount) {
                handleCrateFail(player, crate, openData)
                Lang.ERROR_COST.forEach {
                    player.sendMessage(TextUtils.parseAllNative(player, crate.parsePlaceholders(
                        it
                    )))
                }
                return false
            }
        }

        // Pokémania: abertura nova (null = a animação antiga do SkiesCrates)
        val maniaStyle: OpeningStyle? = Mania.openingStyle(crate)
        val requested = if (maniaStyle == null || openData.itemStack != null) 1 else count.coerceIn(1, 64)

        // Pokémania: da leitura dos dados até a gravação, ninguém mais mexe nos dados deste jogador (a mesma fila dos
        // comandos de chave). Antes, uma chave dada no meio podia ser apagada por esta gravação.
        return Journal.withUserLock(player.uuid) {
            openCrateLocked(player, crate, openData, isForced, requested, skip, maniaStyle)
        }
    }

    private suspend fun openCrateLocked(
        player: ServerPlayer, crate: Crate, openData: CrateOpenData, isForced: Boolean,
        requested: Int, skip: Boolean, maniaStyle: OpeningStyle?
    ): Boolean {
        val storage = SkiesCrates.INSTANCE.storage

        val playerData = storage.getUser(player.uuid)

        // Pokémania: quantas dá para abrir agora (chaves e saldo, com os dados frescos)
        var amount = requested
        if (amount > 1 && !isForced) {
            amount = minOf(amount, withContext(MinecraftDispatcher(player.server)) {
                var max = amount
                for ((keyId, need) in crate.keys) {
                    val key = ConfigManager.KEYS[keyId] ?: continue
                    if (need <= 0) continue
                    val have = if (key.virtual) playerData.keys[keyId] ?: 0
                        else player.inventory.items.filter { !it.isEmpty && KeyManager.getKeyOrNull(it)?.id == keyId }.sumOf { it.count }
                    max = minOf(max, have / need)
                }
                max
            })
            if (crate.cost != null && crate.cost.amount > 0) {
                EconomyManager.getService(crate.cost.provider)?.let { service ->
                    val canPay = (service.balance(player, crate.cost.currency) / crate.cost.amount).toInt()
                    amount = minOf(amount, canPay)
                }
            }
            amount = amount.coerceAtLeast(1) // com 0, as checagens abaixo dão a mensagem de sempre
        }

        // Check for a cooldown, if one is present
        if (crate.cooldown > 0) {
            val lastOpened = playerData.getCrateCooldown(crate)
            if (lastOpened != null) {
                val cooldownTime = lastOpened + (crate.cooldown * 1000)
                if (System.currentTimeMillis() < cooldownTime) {
                    withContext(MinecraftDispatcher(player.server)) {
                        handleCrateFail(player, crate, openData)
                        Lang.ERROR_COOLDOWN.forEach {
                            player.sendMessage(TextUtils.parseAllNative(player, crate.parsePlaceholders(
                                it.replace("%cooldown%", Utils.getFormattedTime((cooldownTime - System.currentTimeMillis()) / 1000))
                            )))
                        }
                    }
                    return false
                }
            }
        }

        // Ensure there are rewards to be given
        if (crate.rewards.isEmpty()) {
            withContext(MinecraftDispatcher(player.server)) {
                handleCrateFail(player, crate, openData)
                Lang.ERROR_NO_REWARDS.forEach {
                    player.sendMessage(TextUtils.parseAllNative(player, crate.parsePlaceholders(it)))
                }
            }
            return false
        }

        // Check for any keys needed
        if (!isForced && crate.keys.isNotEmpty()) {
            if(!withContext(MinecraftDispatcher(player.server)) {
                if (!crate.keys.all { (keyId, needed) ->
                    val key = ConfigManager.KEYS[keyId] ?: run {
                        Utils.printError("Key $keyId does not exist while opening crate ${crate.id} for ${player.name.string}!")
                        Lang.ERROR_KEY_NOT_FOUND.forEach {
                            player.sendMessage(
                                TextUtils.parseAllNative(
                                    player, crate.parsePlaceholders(
                                        it.replace("%key_id%", keyId)
                                    )
                                )
                            )
                        }
                        return@all false
                    }

                    KeyManager.checkPlayerForKeys(player, playerData, key, needed * amount)
                }) {
                    handleCrateFail(player, crate, openData)
                    Lang.ERROR_MISSING_KEYS.forEach {
                        player.sendMessage(TextUtils.parseAllNative(player, crate.parsePlaceholders(
                            it
                        )))
                    }
                    return@withContext false
                }
                true
            }) return false
        }

        // Check for crate item
        if (!isForced && openData.itemStack != null) {
            var contains = true
            withContext(MinecraftDispatcher(player.server)) {
                if (!player.inventory.contains { getCrateOrNull(it)?.id == crate.id }) {
                    contains = false
                    handleCrateFail(player, crate, openData)
                    Lang.ERROR_NO_CRATE.forEach {
                        player.sendMessage(TextUtils.parseAllNative(player, crate.parsePlaceholders(
                            it
                        )))
                    }
                }
            }
            if (!contains) return false
        }

        // Pokémania: sorteia antes de cobrar; o sorteio entra no diário junto com a retirada das chaves
        val fillerBag = crate.generateRewardBag(playerData)
        val draws = if (maniaStyle != null) Journal.draw(crate, playerData, amount) else null
        if (draws != null && draws.isEmpty()) {
            withContext(MinecraftDispatcher(player.server)) {
                handleCrateFail(player, crate, openData)
                Lang.ERROR_NO_REWARDS.forEach {
                    player.sendMessage(TextUtils.parseAllNative(player, crate.parsePlaceholders(it)))
                }
            }
            return false
        }

        // Take cost of opening the crate
        if (!isForced) {
            // Remove balance if needed
            if (crate.cost != null && crate.cost.amount > 0) {
                val service = EconomyManager.getService(crate.cost.provider) ?: run {
                    withContext(MinecraftDispatcher(player.server)) {
                        handleCrateFail(player, crate, openData)
                        Utils.printError("Crate ${crate.id} has an invalid economy provider '${crate.cost.provider}'. Valid providers are: ${EconomyManager.getServices().keys.joinToString(", ")}")
                        Lang.ERROR_ECONOMY_PROVIDER.forEach {
                            player.sendMessage(TextUtils.parseAllNative(player, crate.parsePlaceholders(
                                it
                            )))
                        }
                    }
                    return false
                }
                if (!service.withdraw(player, crate.cost.amount * amount, crate.cost.currency)) {
                    withContext(MinecraftDispatcher(player.server)) {
                        handleCrateFail(player, crate, openData)
                        Lang.ERROR_BALANCE_CHANGED.forEach {
                            player.sendMessage(
                                TextUtils.parseAllNative(
                                    player, crate.parsePlaceholders(
                                        it
                                    )
                                )
                            )
                        }
                    }
                    return false
                }
            }

            // Apply a cooldown
            if (crate.cooldown > 0) {
                playerData.addCrateCooldown(crate, System.currentTimeMillis())
            }

            // Take keys if needed
            if (crate.keys.isNotEmpty()) {
                // TODO: I dont like this code very much, but need to figure out a better way
                if (!withContext(MinecraftDispatcher(player.server)) {
                    for ((keyId, perOpen) in crate.keys) {
                        val amount = perOpen * amount // Pokémania: abrir várias
                        var removed = 0
                        val key = ConfigManager.KEYS[keyId] ?: run {
                            Utils.printError("Key $keyId does not exist while opening crate ${crate.id} for ${player.name.string}!")
                            Lang.ERROR_KEY_NOT_FOUND.forEach {
                                player.sendMessage(TextUtils.parseAllNative(player, crate.parsePlaceholders(
                                    it.replace("%key_id%", keyId)
                                )))
                            }
                            return@withContext false
                        }

                        if (key.virtual) {
                            if (!playerData.removeKeys(key, amount)) {
                                Utils.printError("Failed to remove $amount keys from ${player.name.string} for crate ${crate.id}, but they were present in the check!")
                                Lang.ERROR_KEYS_CHANGED.forEach {
                                    player.sendMessage(TextUtils.parseAllNative(player, crate.parsePlaceholders(
                                        it.replace("%key_id%", keyId)
                                    )))
                                }
                                return@withContext false
                            }
                            removed += amount
                        } else {
                            for ((i, stack) in player.inventory.items.withIndex()) {
                                if (!stack.isEmpty) {
                                    if (KeyManager.getKeyOrNull(stack)?.id == keyId) {
                                        val stackSize = stack.count
                                        if (removed + stackSize >= amount) {
                                            KeyManager.markStackUsed(stack, key, keyId, player)
                                            player.inventory.items[i].shrink(amount - removed)
                                            removed += (amount - removed)
                                            break
                                        } else {
                                            KeyManager.markStackUsed(stack, key, keyId, player)
                                            player.inventory.items[i].shrink(stackSize)
                                            removed += stackSize
                                        }
                                    }
                                }
                            }
                        }

                        if (removed != amount) {
                            // This should never happen, but just in case
                            Utils.printError("Somehow the ${player.name.string} had $amount keys on check, but we removed $removed instead!")
                            Lang.ERROR_KEYS_CHANGED.forEach {
                                player.sendMessage(TextUtils.parseAllNative(player, crate.parsePlaceholders(
                                    it.replace("%key_id%", keyId)
                                )))
                            }
                            return@withContext false
                        }
                    }
                    return@withContext true
                }) return false
            }

            // Take crate item, if it was an inventory open
            if (openData.itemStack != null) {
                withContext(MinecraftDispatcher(player.server)) {
                    openData.itemStack.count -= 1
                }
            }
        }

        repeat(amount) { playerData.addCrateUse(crate) }

        return withContext(MinecraftDispatcher(player.server)) {
            if (!storage.saveUserAsync(playerData).get()) {
                Utils.printError("Failed to save user data after opening a crate for ${player.name.string}! Check elsewhere for errors.")
                Lang.ERROR_STORAGE.forEach {
                    player.sendMessage(TextUtils.toNative(it))
                }
                return@withContext false
            }

            if (draws != null && maniaStyle != null) {
                // Pokémania: abertura nova (os prêmios já estão no diário)
                val skipNow = skip || playerData.prefersSkip()
                val opening = ManiaOpening(player, crate, draws, maniaStyle, skipNow, openData.location, fillerBag)
                OpeningManager.addInstance(player.uuid, opening)
                opening.setup()
                return@withContext true
            }

            Lang.CRATE_OPENING.forEach {
                player.sendMessage(TextUtils.parseAllNative(player, crate.parsePlaceholders(
                    it
                )))
            }

            val rewardBag = crate.generateRewardBag(playerData)
            if (rewardBag.size() <= 0) {
                handleCrateFail(player, crate, openData)
                Lang.ERROR_NO_REWARDS.forEach {
                    player.sendMessage(TextUtils.parseAllNative(player, crate.parsePlaceholders(
                        it
                    )))
                }
                return@withContext false
            }

            if (crate.animation.isEmpty()) {
                // TODO: Update this probably. No option for selecting how many
                val reward = rewardBag.next() ?: run {
                    handleCrateFail(player, crate, openData)
                    Lang.ERROR_NO_REWARDS.forEach {
                        player.sendMessage(TextUtils.parseAllNative(player, crate.parsePlaceholders(
                            it
                        )))
                    }
                    return@withContext false
                }
                reward.giveReward(player, crate)

                if (reward.getPlayerLimit() > 0) {
                    playerData.addRewardUse(crate, reward)
                    storage.saveUserAsync(playerData)
                }

                return@withContext true
            }

            val animation = OpeningManager.getAnimation(crate.animation) ?: run {
                handleCrateFail(player, crate, openData)
                Lang.ERROR_INVALID_ANIMATION.forEach {
                    player.sendMessage(TextUtils.parseAllNative(player, crate.parsePlaceholders(
                        it
                    )))
                }
                return@withContext false
            }

            val opening = when (animation) {
                is InventoryOpeningAnimation -> {
                    InventoryOpeningInstance(player, crate, animation, rewardBag)
                }
                is WorldOpeningAnimation -> {
                    val positionData = openData.location ?: run {
                        handleCrateFail(player, crate, openData)
                        Utils.printError("No position data found for world opening animation ${crate.animation} for crate ${crate.id} for player ${player.name.string}!")
                        Lang.ERROR_INVALID_ANIMATION.forEach {
                            player.sendMessage(TextUtils.parseAllNative(player, crate.parsePlaceholders(
                                it
                            )))
                        }
                        return@withContext false
                    }
                    val crateInstance = getCrateFromPos(positionData) ?: run {
                        handleCrateFail(player, crate, openData)
                        Utils.printError("No crate instance found at $positionData for world opening animation ${crate.animation} for crate ${crate.id} for player ${player.name.string}!")
                        Lang.ERROR_INVALID_ANIMATION.forEach {
                            player.sendMessage(TextUtils.parseAllNative(player, crate.parsePlaceholders(
                                it
                            )))
                        }
                        return@withContext false
                    }
                    WorldOpeningInstance(player, crate, crateInstance, animation, rewardBag)
                }
                else -> {
                    handleCrateFail(player, crate, openData)
                    Lang.ERROR_INVALID_ANIMATION.forEach {
                        player.sendMessage(TextUtils.parseAllNative(player, crate.parsePlaceholders(
                            it
                        )))
                    }
                    return@withContext false
                }
            }

            OpeningManager.addInstance(player.uuid, opening)
            opening.setup()

            return@withContext true
        }
    }

    fun previewCrate(player: ServerPlayer, crate: Crate) {
        interactionLimiter[player.uuid]?.let {
            if ((it + ConfigManager.CONFIG.interactionLimiter) > System.currentTimeMillis()) {
                return
            }
        }

        interactionLimiter[player.uuid] = System.currentTimeMillis()

        // Pokémania: preview novo (chance real, raridades, abrir/abrir várias)
        if (Mania.usesNewPreview(crate)) {
            Actions.preview(player, crate)
            return
        }

        val preview = ConfigManager.PREVIEW[crate.preview] ?: run {
            Lang.ERROR_INVALID_PREVIEW.forEach {
                player.sendMessage(TextUtils.parseAllNative(player, crate.parsePlaceholders(
                    it
                )))
            }
            return
        }
        Utils.printDebug("previewCrate - Preview found, opening")

        PreviewInventory(player, crate, preview).open()
    }

    private fun handleCrateFail(player: ServerPlayer, crate: Crate, openData: CrateOpenData) {
        crate.failure?.sound?.playSound(player)
        val force = crate.failure?.pushback ?: return
        if (openData.location != null) {
            val blockPos = openData.location.getBlockPos()
            val sourcePos = blockPos.center
            val playerPos = player.position()

            val direction = Vec3(
                playerPos.x - sourcePos.x,
                playerPos.y - sourcePos.y,
                playerPos.z - sourcePos.z
            ).normalize()

            val velocity = direction.scale(force)
            player.addDeltaMovement(velocity)
            player.hurtMarked = true
        }
    }

    fun getCrateOrNull(itemStack: ItemStack): Crate? {
        val tag = itemStack.get(DataComponents.CUSTOM_DATA) ?: return null
        if (tag.contains(CRATE_IDENTIFIER)) {
            return ConfigManager.CRATES[tag.copyTag().getString(CRATE_IDENTIFIER)]
        }
        return null
    }

    private fun getChunkInstances(key: ChunkKey): MutableMap<DimensionalBlockPos, CrateInstance> =
        instancesByChunk.computeIfAbsent(key) { mutableMapOf() }

    fun getCrateFromPos(pos: DimensionalBlockPos): CrateInstance? {
        val key = ChunkKey.of(pos)
        val chunkInstances = instancesByChunk[key] ?: return null
        return chunkInstances[pos]
    }

    fun getInstancesAtChunk(level: ServerLevel, chunk: LevelChunk): List<CrateInstance> {
        val key = ChunkKey.of(level, chunk)
        val chunkInstances = instancesByChunk[key] ?: return emptyList()

        return chunkInstances.values.toList()
    }

    fun getAllInstances(): List<CrateInstance> {
        return instancesByChunk.values.flatMap { instances ->
            instances.values.toList()
        }
    }
}
