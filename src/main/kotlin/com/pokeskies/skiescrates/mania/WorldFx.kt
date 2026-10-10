/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Arquivo da Pokémania (fork pokemaniabrasil/SkiesCrates).
 */
package com.pokeskies.skiescrates.mania

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Display
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3

/**
 * O prêmio subindo em cima do bloco da caixa (item display, só visual, some sozinho). As entidades levam a tag
 * [TAG] e não são salvas de propósito: se o servidor cair com uma no mundo, ela é apagada ao carregar (ver Mania).
 */
object WorldFx {
    const val TAG = "skiescrates_fx"
    private const val LIFE = 70
    private const val RISE = 1.1

    private class Floating(val entity: Display.ItemDisplay, val base: Vec3, val rarity: Rarity, var age: Int = 0)

    private val active = mutableListOf<Floating>()

    fun risingItem(level: ServerLevel, at: Vec3, stack: ItemStack, rarity: Rarity) {
        if (stack.isEmpty) return
        // Um por caixa por vez: o anterior some
        active.filter { it.base.distanceToSqr(at) < 0.01 }.forEach { it.entity.discard() }
        active.removeIf { it.entity.isRemoved }
        val display = Display.ItemDisplay(EntityType.ITEM_DISPLAY, level)
        display.setPos(at.x, at.y, at.z)
        display.addTag(TAG)
        display.itemStack = stack.copyWithCount(1)
        display.itemTransform = ItemDisplayContext.GROUND
        display.billboardConstraints = Display.BillboardConstraints.CENTER
        display.setGlowingTag(rarity.ordinal >= Rarity.EPICO.ordinal)
        display.glowColorOverride = rarity.rgb
        display.posRotInterpolationDuration = 3
        level.addFreshEntity(display)
        active += Floating(display, at, rarity)
    }

    /** Chamado todo tick (só faz algo com itens no ar). */
    fun tick() {
        if (active.isEmpty()) return
        val it = active.iterator()
        while (it.hasNext()) {
            val f = it.next()
            f.age++
            if (f.entity.isRemoved || f.age > LIFE) {
                f.entity.discard()
                it.remove()
                continue
            }
            val t = (f.age / 30.0).coerceAtMost(1.0)
            val eased = 1 - (1 - t) * (1 - t) * (1 - t)
            val y = f.base.y + RISE * eased + if (t >= 1.0) Math.sin(f.age / 6.0) * 0.05 else 0.0
            f.entity.setPos(f.base.x, y, f.base.z)
            if (f.age % 6 == 0) Effects.particles(f.entity.level() as ServerLevel, Vec3(f.base.x, y, f.base.z), f.rarity)
        }
    }

    fun clearAll() {
        active.forEach { it.entity.discard() }
        active.clear()
    }
}
