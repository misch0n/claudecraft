package org.vmstudio.visor.compatibility.create;

import org.jetbrains.annotations.NotNull;
import org.vmstudio.visor.api.ModLoader;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.visor.api.common.addon.VisorAddon;
import org.vmstudio.visor.compatibility.create.addons.aeronautics.AeronauticsHelper;
import org.vmstudio.visor.compatibility.create.addons.aeroworks.AeroworksHelper;
import org.vmstudio.visor.compatibility.create.core.CreateControlsVRInputRedirect;
import org.vmstudio.visor.compatibility.create.core.LinkedControllerItemPose;

public class CreateHelper {
    public static void registerComponents(@NotNull VisorAddon owner) {
        if (isLoaded()) {
            var registries = VisorAPI.addonManager().getRegistries();
            registries.inputRedirects().registerComponent(new CreateControlsVRInputRedirect(owner));
            registries.itemPoses().registerComponent(new LinkedControllerItemPose(owner));

            AeronauticsHelper.registerComponents(owner);
            AeroworksHelper.registerComponents(owner);
        }
    }

    public static boolean isLoaded() {
        return ModLoader.get().isModLoaded("create");
    }
}
