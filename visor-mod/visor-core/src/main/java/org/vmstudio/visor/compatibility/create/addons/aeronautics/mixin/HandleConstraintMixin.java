package org.vmstudio.visor.compatibility.create.addons.aeronautics.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.visor.api.common.player.VRPlayer;
import org.vmstudio.visor.api.common.player.VRPlayerPose;
import org.vmstudio.visor.api.common.player.VRPose;
import org.vmstudio.visor.api.common.player.VisorPlayer;
import org.vmstudio.visor.compatibility.MixinGate;

@Mixin(targets = "dev.simulated_team.simulated.content.blocks.handle.HandleBlockEntity$HandleConstraint", remap = false)
@MixinGate(classes = "dev.simulated_team.simulated.content.blocks.handle.HandleBlockEntity$HandleConstraint")
@Pseudo
public class HandleConstraintMixin {

    @Redirect(
            method = "physicsTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Player;getLookAngle()Lnet/minecraft/world/phys/Vec3;"
            )
    )
    private Vec3 redirectLookAngle(Player player) {
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
