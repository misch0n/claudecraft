package org.vmstudio.visor.mixin.common.listeners;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import org.vmstudio.visor.api.server.VRServerSettings;
import org.vmstudio.visor.core.server.network.ServerNetworking;
import org.vmstudio.visor.core.server.VisorServerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public class PlayerListenerMixins {
    @Mixin(PlayerList.class)
    public static class PlayerListMixin {

        @Inject(at = @At("HEAD"), method = "placeNewPlayer")
        private void visor$onLogin(CallbackInfo ci, @Local(argsOnly = true) ServerPlayer serverPlayer) {
            if (VRServerSettings.isVrOnly()){
                ServerNetworking.kickDelayedIfNoVR(serverPlayer);
            }
        }
        @WrapOperation(method = "respawn", at = @At(value = "INVOKE",
                target = "Lnet/minecraft/server/level/ServerPlayer;initInventoryMenu()V"))
        private void visor$onPlayerRespawn(ServerPlayer serverPlayer, Operation<Void> original) {
            VisorServerImpl.INSTANCE.updateMcPlayer(serverPlayer);
            original.call(serverPlayer);
        }
    }
}
