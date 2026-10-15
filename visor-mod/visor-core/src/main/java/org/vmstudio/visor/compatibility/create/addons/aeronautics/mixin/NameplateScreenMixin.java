package org.vmstudio.visor.compatibility.create.addons.aeronautics.mixin;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vmstudio.visor.compatibility.MixinGate;
import org.vmstudio.visor.core.client.ClientContext;
import org.vmstudio.visor.core.client.VisorState;

@Mixin(targets = "dev.simulated_team.simulated.content.blocks.nameplate.NameplateScreen", remap = false)
@MixinGate(classes = "dev.simulated_team.simulated.content.blocks.nameplate.NameplateScreen")
@Pseudo
public abstract class NameplateScreenMixin extends Screen {

    protected NameplateScreenMixin(Component component) {
        super(component);
    }

    @Inject(at = @At("HEAD"), method = "init")
    public void visor$onInit(CallbackInfo ci) {
        if (VisorState.get().isNotActive()) {
            return;
        }
        var keyboardAccessor = ClientContext.overlayManager.getKeyboardAccessor();
        if (keyboardAccessor.isVisible()) {
            return;
        }

        keyboardAccessor.showKeyboard(this);
    }
}
