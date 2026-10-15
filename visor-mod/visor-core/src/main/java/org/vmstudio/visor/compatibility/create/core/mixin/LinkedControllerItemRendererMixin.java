package org.vmstudio.visor.compatibility.create.core.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.item.ItemDisplayContext;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.vmstudio.visor.compatibility.MixinGate;
import org.vmstudio.visor.core.client.VisorState;

@Mixin(targets = "com.simibubi.create.content.redstone.link.controller.LinkedControllerItemRenderer", remap = false)
@MixinGate(classes = "com.simibubi.create.content.redstone.link.controller.LinkedControllerItemRenderer")
@Pseudo
public class LinkedControllerItemRendererMixin {

    @ModifyExpressionValue(
            method = "render(Lnet/minecraft/world/item/ItemStack;Lcom/simibubi/create/foundation/item/render/CustomRenderedItemModel;Lcom/simibubi/create/foundation/item/render/PartialItemModelRenderer;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;ILcom/simibubi/create/content/redstone/link/controller/LinkedControllerItemRenderer$RenderType;ZZ)V",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/world/item/ItemDisplayContext;GUI:Lnet/minecraft/world/item/ItemDisplayContext;",
                    opcode = Opcodes.GETSTATIC
            )
    )
    private static ItemDisplayContext visor$redirectGuiContext(
            ItemDisplayContext original,
            @Local(argsOnly = true) ItemDisplayContext transformType
    ) {
        if (VisorState.get().isActive()) {
            if (transformType == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND
                    || transformType == ItemDisplayContext.THIRD_PERSON_LEFT_HAND) {
                return transformType;
            }
        }
        return original;
    }
}
