package org.vmstudio.visor.compatibility.create.core.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vmstudio.visor.compatibility.MixinGate;
import org.vmstudio.visor.compatibility.create.core.CreateControlsVRInputRedirect;
import org.vmstudio.visor.core.client.VisorState;

@Mixin(targets = "com.simibubi.create.foundation.utility.ControlsUtil", remap = false)
@MixinGate(classes = "com.simibubi.create.foundation.utility.ControlsUtil")
@Pseudo
public class ControlsUtilMixin {

    @Inject(method = "isActuallyPressed", at = @At("HEAD"), cancellable = true)
    private static void visor$isActuallyPressed(KeyMapping kb, CallbackInfoReturnable<Boolean> cir) {
        if (VisorState.get().isActive()) {
            if (CreateControlsVRInputRedirect.isInputRedirectActive()) {
                Boolean state = CreateControlsVRInputRedirect.isControlPressed(kb);
                if (state != null) {
                    cir.setReturnValue(state);
                }
            }
        }
    }
}
