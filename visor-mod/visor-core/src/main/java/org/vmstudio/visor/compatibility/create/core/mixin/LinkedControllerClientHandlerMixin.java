package org.vmstudio.visor.compatibility.create.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vmstudio.visor.compatibility.MixinGate;
import org.vmstudio.visor.compatibility.create.core.CreateControlsState;

@Mixin(targets = "com.simibubi.create.content.redstone.link.controller.LinkedControllerClientHandler", remap = false)
@MixinGate(classes = "com.simibubi.create.content.redstone.link.controller.LinkedControllerClientHandler")
@Pseudo
public class LinkedControllerClientHandlerMixin {

    @Inject(
            method = "tick",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/foundation/utility/ControlsUtil;getControls()Ljava/util/List;"
            )
    )
    private static void visor$onControlsTick(CallbackInfo ci) {
        CreateControlsState.setLinkedControllerActive(true);
    }

    @Inject(
            method = "tick",
            at = @At(
                    value = "RETURN",
                    ordinal = 0
            )
    )
    private static void visor$onIdleTick(CallbackInfo ci) {
        CreateControlsState.setLinkedControllerActive(false);
    }

    @Inject(method = "onReset", at = @At("TAIL"))
    private static void visor$onReset(CallbackInfo ci) {
        CreateControlsState.setLinkedControllerActive(false);
    }

    @Inject(method = "deactivateInLectern", at = @At("TAIL"))
    private static void visor$onDeactivateLectern(CallbackInfo ci) {
        CreateControlsState.setLinkedControllerActive(false);
    }
}
