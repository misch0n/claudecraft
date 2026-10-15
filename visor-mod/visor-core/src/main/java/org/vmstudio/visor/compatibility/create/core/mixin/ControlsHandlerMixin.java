package org.vmstudio.visor.compatibility.create.core.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vmstudio.visor.compatibility.MixinGate;
import org.vmstudio.visor.compatibility.create.core.CreateControlsState;

@Mixin(targets = "com.simibubi.create.content.contraptions.actors.trainControls.ControlsHandler", remap = false)
@MixinGate(classes = "com.simibubi.create.content.contraptions.actors.trainControls.ControlsHandler")
@Pseudo
public class ControlsHandlerMixin {

    @Inject(method = "startControlling", at = @At("TAIL"))
    private static void visor$onStartControlling(CallbackInfo ci) {
        CreateControlsState.setTrainControlsActive(true);
    }

    @Inject(method = "stopControlling", at = @At("TAIL"))
    private static void visor$onStopControlling(CallbackInfo ci) {
        CreateControlsState.setTrainControlsActive(false);
    }

    @Inject(method = "levelUnloaded", at = @At("TAIL"))
    private static void visor$onLevelUnloaded(CallbackInfo ci) {
        CreateControlsState.setTrainControlsActive(false);
    }
}
