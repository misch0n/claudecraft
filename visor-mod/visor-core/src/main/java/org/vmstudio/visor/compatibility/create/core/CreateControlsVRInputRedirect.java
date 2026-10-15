package org.vmstudio.visor.compatibility.create.core;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2fc;
import org.vmstudio.visor.api.client.input.redirect.VRInputRedirect;
import org.vmstudio.visor.api.common.addon.VisorAddon;
import org.vmstudio.visor.api.common.addon.component.ComponentPriority;
import org.vmstudio.visor.core.client.VisorState;

public class CreateControlsVRInputRedirect extends VRInputRedirect {
    public static final String ID = "create_controls";

    private static CreateControlsVRInputRedirect instance;

    private boolean forward;
    private boolean backward;
    private boolean left;
    private boolean right;

    public CreateControlsVRInputRedirect(@NotNull VisorAddon owner) {
        super(owner);
        instance = this;
    }

    public static boolean isInputRedirectActive() {
        return instance != null && instance.isRedirecting();
    }

    private boolean isRedirecting() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && canRedirect(player);
    }

    @Nullable
    public static Boolean isControlPressed(@NotNull KeyMapping kb) {
        if (instance == null || !instance.isRedirecting()) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        if (kb == mc.options.keyUp) {
            return instance.forward;
        } else if (kb == mc.options.keyDown) {
            return instance.backward;
        } else if (kb == mc.options.keyLeft) {
            return instance.left;
        } else if (kb == mc.options.keyRight) {
            return instance.right;
        }
        return null;
    }

    @Override
    public boolean canRedirect(@NotNull LocalPlayer player) {
        if (VisorState.get().isNotActive()) {
            return false;
        }
        return CreateControlsState.isAnyControlsActive();
    }

    @Override
    public boolean onAxis(@NotNull LocalPlayer player, @NotNull Vector2fc axis) {
        forward = axis.y() > 0.4f;
        backward = axis.y() < -0.4f;
        left = axis.x() < -0.4f;
        right = axis.x() > 0.4f;
        return true;
    }

    @Override
    public void onStop(@Nullable LocalPlayer player) {
        forward = false;
        backward = false;
        left = false;
        right = false;
    }

    @Override
    public @NotNull ComponentPriority getPriority() {
        return ComponentPriority.HIGH;
    }

    @Override
    public @NotNull String getId() {
        return ID;
    }
}
