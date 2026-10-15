package org.vivecraft.mod_compat_vr.sodium.mixin.caffeinemc;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vivecraft.client_vr.perf.SharedEyeCulling;
import org.vivecraft.client_xr.render_pass.RenderPassType;

/**
 * claudecraft: sharedEyeCulling (PLAN Phase 3), written against Sodium 0.6.13. The second eye pass of a shared
 * frame skips setupTerrain (graph update, chunk updates and uploads, GFNI, sprite ticking) and draws the first
 * eye pass's render lists. If these injections stop matching a newer Sodium, the hook never runs, the
 * {@code [VCPERF] cull} line says {@code sodiumHook=false} and both eyes keep culling on their own.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer")
public class SodiumWorldRendererSharedCullingMixin {

    @Inject(method = "setupTerrain", at = @At("HEAD"), cancellable = true, remap = false, require = 0, expect = 1)
    private void vivecraft$sharedEyeCullingSkip(CallbackInfo ci) {
        if (!RenderPassType.isVanilla() && SharedEyeCulling.onTerrainStart()) {
            ci.cancel();
        }
    }

    @Inject(method = "setupTerrain", at = @At("RETURN"), remap = false, require = 0, expect = 1)
    private void vivecraft$sharedEyeCullingTime(CallbackInfo ci) {
        if (!RenderPassType.isVanilla()) {
            SharedEyeCulling.onTerrainEnd();
        }
    }
}
