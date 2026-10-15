package org.vmstudio.visor.compatibility.create.addons.aeronautics;

import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.NotNull;
import org.vmstudio.visor.api.client.input.redirect.VRInputRedirect;
import org.vmstudio.visor.api.common.addon.VisorAddon;
import org.vmstudio.visor.api.common.addon.component.ComponentPriority;
import org.vmstudio.visor.compatibility.create.addons.aeronautics.internal.AeronauticsInputInternal;

public class AeroHandleVRInputRedirect extends VRInputRedirect {
    private static final String ID = "aeronautics_handle";

    public AeroHandleVRInputRedirect(@NotNull VisorAddon owner) {
        super(owner);
    }

    @Override
    public boolean canRedirect(@NotNull LocalPlayer player) {
        return AeronauticsInputInternal.isHandleActive();
    }

    @Override
    public void onScroll(@NotNull LocalPlayer player,
                         double deltaX,
                         double deltaY) {
        AeronauticsInputInternal.sendMouseScroll(deltaX, deltaY);
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
