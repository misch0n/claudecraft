package org.vmstudio.visor.mixin.client.renderer;

import org.vmstudio.visor.api.client.input.HapticFeedback;
import org.vmstudio.visor.api.common.HandType;
import org.vmstudio.visor.core.client.ClientContext;
import org.vmstudio.visor.core.client.VisorState;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
//? if >=1.21.2 {
import net.minecraft.client.renderer.LevelEventHandler;
//?} else {
/*import net.minecraft.client.renderer.LevelRenderer;
*///?}
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


// 1.21.2 moved levelEvent out of LevelRenderer
//? if >=1.21.2 {
@Mixin(LevelEventHandler.class)
//?} else {
/*@Mixin(value = LevelRenderer.class, priority = 999)
*///?}
public abstract class LevelRendererHapticsMixin {

    // ---- Shadow fields ----
    @Final @Shadow
    private Minecraft minecraft;


    /* ***************** *\
  //--------MIXINS--------\\
    \* ***************** */

    @Inject(at = @At("HEAD"), method = "levelEvent")
    public void visor$hapticOnSound(int i, BlockPos blockPos, int j, CallbackInfo ci) {
        if(VisorState.get().isNotActive()) return;

        if (this.minecraft.player != null
                && this.minecraft.player.isAlive()
                && this.minecraft.player.blockPosition().distSqr(blockPos) < 25.0D) {
            switch (i) {
                case 1019,      // ZOMBIE_ATTACK_WOODEN_DOOR
                     1020,   // ZOMBIE_ATTACK_IRON_DOOR
                     1021    // ZOMBIE_BREAK_WOODEN_DOOR
                        -> {
                    ClientContext.inputManager
                            .triggerHapticPulseMicroSec(HandType.MAIN, HapticFeedback.WORLD_DOOR_HIT);
                    ClientContext.inputManager
                            .triggerHapticPulseMicroSec(HandType.OFFHAND, HapticFeedback.WORLD_DOOR_HIT);
                }
                case 1030 ->    // ANVIL_USE
                        ClientContext.inputManager
                                .triggerHapticPulseMicroSec(HandType.MAIN, HapticFeedback.WORLD_ANVIL_USE);
                case 1031 -> {  // ANVIL_LAND
                    ClientContext.inputManager
                            .triggerHapticPulseMicroSec(HandType.MAIN, HapticFeedback.WORLD_ANVIL_LAND);
                    ClientContext.inputManager
                            .triggerHapticPulseMicroSec(HandType.OFFHAND, HapticFeedback.WORLD_ANVIL_LAND);
                }
            }
        }
    }
}
