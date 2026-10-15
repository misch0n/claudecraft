package org.vmstudio.visor.compatibility.create.addons.aeronautics.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vmstudio.visor.compatibility.MixinGate;
import org.vmstudio.visor.compatibility.create.addons.aeronautics.internal.AeronauticsInputInternal;
import org.vmstudio.visor.core.client.ClientContext;
import org.vmstudio.visor.core.client.VisorState;

@Mixin(targets = "dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterInteractionHandler", remap = false)
@MixinGate(classes = "dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterInteractionHandler")
@Pseudo
public class LinkedTypewriterInteractionHandlerMixin {

    @Inject(method = "associateTypewriter", at = @At("TAIL"))
    private static void visor$onAssociateTypewriter(CallbackInfo ci) {
        if (VisorState.get().isActive()) {
            var keyboardAccessor = ClientContext.overlayManager.getKeyboardAccessor();
            if (AeronauticsInputInternal.isTypewriterActive()) {
                keyboardAccessor.showKeyboard(null);
            } else {
                if (!keyboardAccessor.isStaticAttachment()) {
                    keyboardAccessor.setVisible(false);
                }
            }
        }
    }
}
