/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Modificado pela Pokémania (fork pokemaniabrasil/SkiesCrates): liga a parte da Pokémania (pacote mania): config, eventos, tick e /caixas.
 */
package com.pokeskies.skiescrates

import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.pokeskies.skiescrates.commands.BaseCommand
import com.pokeskies.skiescrates.commands.KeysCommand
import com.pokeskies.skiescrates.config.ConfigManager
import com.pokeskies.skiescrates.config.Lang
import com.pokeskies.skiescrates.config.SoundOption
import com.pokeskies.skiescrates.data.actions.Action
import com.pokeskies.skiescrates.data.opening.world.WorldOpeningAnimation
import com.pokeskies.skiescrates.data.particles.effects.ParticleEffect
import com.pokeskies.skiescrates.data.rewards.Reward
import com.pokeskies.skiescrates.data.rewards.options.boolean.BooleanOption
import com.pokeskies.skiescrates.data.rewards.options.int.IntOption
import com.pokeskies.skiescrates.economy.EconomyManager
import com.pokeskies.skiescrates.gui.InventoryType
import com.pokeskies.skiescrates.integrations.ModIntegration
import com.pokeskies.skiescrates.mania.CaixasCommand
import com.pokeskies.skiescrates.mania.Mania
import com.pokeskies.skiescrates.mania.WorldFx
import com.pokeskies.skiescrates.managers.CratesManager
import com.pokeskies.skiescrates.managers.CratesManager.tick
import com.pokeskies.skiescrates.managers.HologramsManager
import com.pokeskies.skiescrates.managers.KeyManager
import com.pokeskies.skiescrates.managers.OpeningManager
import com.pokeskies.skiescrates.placeholders.PlaceholderManager
import com.pokeskies.skiescrates.storage.IStorage
import com.pokeskies.skiescrates.storage.StorageType
import com.pokeskies.skiescrates.utils.CompoundTagAdaptor
import com.pokeskies.skiescrates.utils.Utils
import kotlinx.coroutines.*
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.loader.api.FabricLoader
import net.kyori.adventure.platform.fabric.FabricServerAudiences
import net.kyori.adventure.text.minimessage.MiniMessage
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.NbtOps
import net.minecraft.nbt.Tag
import net.minecraft.resources.RegistryOps
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.MinecraftServer
import net.minecraft.sounds.SoundEvent
import net.minecraft.world.item.Item
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SkiesCrates : ModInitializer {
    companion object {
        lateinit var INSTANCE: SkiesCrates

        const val MOD_ID = "skiescrates"
        const val MOD_NAME = "SkiesCrates"

        val LOGGER: Logger = LogManager.getLogger(MOD_ID)
        val MINI_MESSAGE: MiniMessage = MiniMessage.miniMessage()

        val asyncScope = CoroutineScope(Dispatchers.IO)

        @JvmStatic
        fun asResource(path: String): ResourceLocation {
            return ResourceLocation.fromNamespaceAndPath(MOD_ID, path)
        }
    }

    lateinit var configDir: File
    lateinit var storage: IStorage

    lateinit var adventure: FabricServerAudiences
    lateinit var server: MinecraftServer
    lateinit var nbtOpts: RegistryOps<Tag>

    val asyncExecutor: ExecutorService = Executors.newFixedThreadPool(8, ThreadFactoryBuilder()
        .setNameFormat("SkiesCrates-Async-%d")
        .setDaemon(true)
        .build())

    @OptIn(DelicateCoroutinesApi::class)
    private val particleThreadPool = newFixedThreadPoolContext(1, "SkiesCratesParticleThread")
    private val particleScope = CoroutineScope(particleThreadPool + SupervisorJob())
    fun runOnParticleThread(block: suspend () -> Unit) {
        particleScope.launch {
            block()
            delay(1)
        }
    }

    var gson: Gson = GsonBuilder().disableHtmlEscaping()
        .registerTypeAdapter(Reward::class.java, Reward.Adapter())
        .registerTypeAdapter(Action::class.java, Action.Adapter())
        .registerTypeAdapter(StorageType::class.java, StorageType.Adapter())
        .registerTypeAdapter(ParticleEffect::class.java, ParticleEffect.Adapter())
        .registerTypeAdapter(WorldOpeningAnimation::class.java, WorldOpeningAnimation.Adapter())
        .registerTypeAdapter(IntOption::class.java, IntOption.Adapter())
        .registerTypeAdapter(BooleanOption::class.java, BooleanOption.Adapter())
        .registerTypeHierarchyAdapter(Item::class.java, Utils.RegistrySerializer(BuiltInRegistries.ITEM))
        .registerTypeHierarchyAdapter(SoundEvent::class.java, Utils.RegistrySerializer(BuiltInRegistries.SOUND_EVENT))
        .registerTypeHierarchyAdapter(ParticleOptions::class.java, Utils.CodecSerializer(ParticleTypes.CODEC))
        .registerTypeAdapter(InventoryType::class.java, InventoryType.Deserializer())
        .registerTypeAdapter(SoundOption::class.java, SoundOption.Adaptor())
        .registerTypeAdapter(CompoundTag::class.java, CompoundTagAdaptor())
        .create()

    var gsonPretty: Gson = gson.newBuilder().setPrettyPrinting().create()

    override fun onInitialize() {
        INSTANCE = this

        this.configDir = File(FabricLoader.getInstance().configDirectory, MOD_ID)
        ConfigManager.load()
        Mania.load()
        this.storage = IStorage.load(ConfigManager.CONFIG.storage)
        Lang.init()

        EconomyManager.init()

        ModIntegration.onInit()

        registerEvents()
    }

    private fun registerEvents() {
        ServerLifecycleEvents.SERVER_STARTING.register(ServerLifecycleEvents.ServerStarting { server: MinecraftServer ->
            this.adventure = FabricServerAudiences.of(server)
            this.server = server
            this.nbtOpts = server.registryAccess().createSerializationContext(NbtOps.INSTANCE)
            ModIntegration.onServerStarting()
        })
        ServerLifecycleEvents.SERVER_STARTED.register(ServerLifecycleEvents.ServerStarted { _: MinecraftServer ->
            OpeningManager.load()
            CratesManager.init()
            PlaceholderManager.init()
            ModIntegration.onServerStarted()
            Mania.onServerStarted()
        })
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            BaseCommand().register(dispatcher)
            KeysCommand().register(dispatcher)
            CaixasCommand.register(dispatcher)
        }
        ServerLifecycleEvents.SERVER_STOPPING.register(ServerLifecycleEvents.ServerStopping { _: MinecraftServer ->
            Mania.onStopping() // entrega as aberturas em andamento antes de fechar o banco
            this.storage.close()
            ModIntegration.onServerShutdown()
        })
        ServerTickEvents.END_SERVER_TICK.register(ServerTickEvents.EndTick { server ->
            tick()
            OpeningManager.tick()
            Mania.tick()
            WorldFx.tick()

            if (server.tickCount % 6000 == 0) {
                KeyManager.cleanCache()
            }
        })
    }

    fun reload() {
        this.storage.close()

        ConfigManager.load()
        Mania.load()
        this.storage = IStorage.load(ConfigManager.CONFIG.storage)
        Lang.init()

        OpeningManager.load()
        CratesManager.init()

        if (FabricLoader.getInstance().isModLoaded("holodisplays")) HologramsManager.load()
    }
}
