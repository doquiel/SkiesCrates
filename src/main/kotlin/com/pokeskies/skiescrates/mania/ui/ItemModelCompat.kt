/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Arquivo da Pokémania (fork pokemaniabrasil/SkiesCrates).
 */
package com.pokeskies.skiescrates.mania.ui

import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import org.joml.Matrix4f
import team.mania.ui.api.NodesBuilder
import team.mania.ui.api.UiHandler
import team.mania.ui.api.UiIcon
import team.mania.ui.api.UiModelPart
import team.mania.ui.api.UiItemModel
import net.minecraft.world.item.ItemDisplayContext

/**
 * Só esta classe usa o `itemModel` do ManiaUI 0.4.2: com um ManiaUI mais antigo no servidor ela falha ao carregar
 * (LinkageError) e quem chamou cai no tile de grade.
 */
internal object ItemModelCompat {
    fun add(b: NodesBuilder, stack: ItemStack, height: Int, name: Component, tooltip: List<Component>, id: String?, onClick: UiHandler?) {
        // Um item só, parado e de frente no centro do palco (girando, os itens planos viram uma lasca de lado). FIXED (o desenho da moldura de item) centraliza qualquer item
        // no mesmo tamanho; o HEAD padrão deixava a Master Ball enorme e as chaves minúsculas lá em cima.
        val action = if (onClick != null && id != null) id.also { b.on(it, true, onClick) } else null
        b.node(UiItemModel(listOf(UiModelPart(stack, Matrix4f())), 1.07f, UiIcon.item(stack), height.coerceIn(30, 220), name,
            20f, 0f, tooltip, action, ItemDisplayContext.FIXED))
    }
}
