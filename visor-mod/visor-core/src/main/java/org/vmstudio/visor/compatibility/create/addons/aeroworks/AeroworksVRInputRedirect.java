package org.vmstudio.visor.compatibility.create.addons.aeroworks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector2fc;
import org.vmstudio.visor.api.client.input.redirect.VRInputRedirect;
import org.vmstudio.visor.api.common.addon.VisorAddon;
import org.vmstudio.visor.api.common.addon.component.ComponentPriority;
import org.vmstudio.visor.compatibility.create.addons.aeroworks.internal.AeroworksInputInternal;

public class AeroworksVRInputRedirect extends VRInputRedirect {
    private static final String ID = "aeroworks_console";
    private static final double MOUSE_DELTA_SCALE = 30.0;

    public AeroworksVRInputRedirect(@NotNull VisorAddon owner) {
        super(owner);
    }

    @Override
    public boolean canRedirect(@NotNull LocalPlayer player) {
        return AeroworksInputInternal.isConsoleActive();
    }

    @Override
    public boolean onAxis(@NotNull LocalPlayer player, @NotNull Vector2fc axis) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.keyShift.isDown()) {
            AeroworksInputInternal.requestExit();
            return true;
        }

        if (AeroworksInputInternal.isMouseCaptured()) {
            AeroworksInputInternal.feedMouseDelta(
                    axis.x() * MOUSE_DELTA_SCALE,
                    -axis.y() * MOUSE_DELTA_SCALE
            );
        }

        return true;
    }

    @Override
    public void onScroll(@NotNull LocalPlayer player, double deltaX, double deltaY) {
        AeroworksInputInternal.feedScroll(deltaY);
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
