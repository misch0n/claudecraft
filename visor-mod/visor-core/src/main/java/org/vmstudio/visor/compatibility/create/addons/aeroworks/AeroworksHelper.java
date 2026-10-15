package org.vmstudio.visor.compatibility.create.addons.aeroworks;

import org.jetbrains.annotations.NotNull;
import org.vmstudio.visor.api.ModLoader;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.visor.api.common.addon.VisorAddon;

public class AeroworksHelper {
    public static void registerComponents(@NotNull VisorAddon owner) {
        if (isLoaded()) {
            var registries = VisorAPI.addonManager().getRegistries();
            registries.inputRedirects().registerComponent(new AeroworksVRInputRedirect(owner));
        }
    }

    public static boolean isLoaded() {
        return ModLoader.get().isModLoaded("aeroworks");
    }
}
