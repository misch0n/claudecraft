package org.vmstudio.visor.loader.neoforge.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
//? if <1.21 {
/*import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.vmstudio.visor.core.client.render.VRRenderState;
import net.minecraft.client.Camera;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.spongepowered.asm.mixin.injection.At;
*///?}


@Mixin(GameRenderer.class)
public class NeoForgeGameRendererMixin {

    //? if <1.21 {
    /*@WrapOperation(at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/Camera;setAnglesInternal(FF)V", remap = false),
            method = "renderLevel", require = 1)
    public void visor$keepVRAnglesInEyes(Camera camera, float yaw, float pitch, Operation<Void> original) {
        if (VRRenderState.getPhase().isVanilla()
                || !VRRenderState.getRenderPass().isEye()) {
            original.call(camera, yaw, pitch);
        }
    }

    @WrapOperation(at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/client/event/ViewportEvent$ComputeCameraAngles;getRoll()F",
            remap = false),
            method = "renderLevel", require = 1)
    public float visor$dropEventRollInEyes(ViewportEvent.ComputeCameraAngles event, Operation<Float> original) {
        if (VRRenderState.getPhase().isVanilla()
                || !VRRenderState.getRenderPass().isEye()) {
            return original.call(event);
        }
        return 0F;
    }
    *///?}
}
