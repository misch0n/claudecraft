package com.hammy275.immersivemc.common.vr.dev.impl;

import com.hammy275.immersivemc.common.vr.dev.DevVRState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.vivecraft.api.data.VRBodyPart;
import org.vivecraft.api.data.ViveVersion;
import org.vivecraft.api.server.VRServerAPI;

public class DevVRServerAPI implements VRServerAPI {
    @Override
    public void sendHapticPulse(ServerPlayer serverPlayer, VRBodyPart vrBodyPart, float v, float v1, float v2, float v3) {
        serverPlayer.sendSystemMessage(Component.literal("Server haptic pulse for %s and body part %s".formatted(serverPlayer.getGameProfile().getName(), vrBodyPart)));
    }

    // claudecraft: Vivecraft 1.3.13+ API; same answer as DevVRAPI.isVRPlayer, and no known version
    @Override
    public boolean hasVivecraft(ServerPlayer serverPlayer) {
        return DevVRState.inVR;
    }

    @Override
    public ViveVersion getVivecraftVersion(ServerPlayer serverPlayer) {
        return null;
    }
}
