package org.vmstudio.visor.compatibility.create.addons.aeronautics.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.visor.api.common.player.VRPlayer;
import org.vmstudio.visor.api.common.player.VRPlayerPose;
import org.vmstudio.visor.api.common.player.VRPose;
import org.vmstudio.visor.api.common.player.VisorPlayer;
import org.vmstudio.visor.compatibility.MixinGate;

@Mixin(targets = "dev.simulated_team.simulated.content.blocks.handle.ClientHandleHandler", remap = false)
@MixinGate(classes = "dev.simulated_team.simulated.content.blocks.handle.ClientHandleHandler")
@Pseudo
public class ClientHandleHandlerMixin {

    @Redirect(
            method = "activeTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getLookAngle()Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private Vec3 redirectActiveTickLookAngle(LocalPlayer player) {
        return visor$getHandDirectionOrLookAngle(player);
    }

    @Redirect(
            method = "clientTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;getLookAngle()Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private Vec3 redirectClientTickLookAngle(LocalPlayer player) {
        return visor$getHandDirectionOrLookAngle(player);
    }

    @Unique
    private Vec3 visor$getHandDirectionOrLookAngle(LocalPlayer player) {
        VisorPlayer visorPlayer = VisorAPI.getVisorPlayer(player);
        if (visorPlayer != null && visorPlayer.isVR()) {
            VRPlayer vrPlayer = visorPlayer.asVR();
            VRPlayerPose playerPose = vrPlayer.getPoseData();

            VRPose handPose = playerPose.getHand(vrPlayer.getActiveHand());
            return handPose.getDirectionVec3();
        }

        return player.getLookAngle();
    }
}
