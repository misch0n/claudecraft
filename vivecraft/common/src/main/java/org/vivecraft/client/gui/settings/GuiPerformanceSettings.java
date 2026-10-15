package org.vivecraft.client.gui.settings;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.vivecraft.client.gui.framework.screens.GuiListScreen;
import org.vivecraft.client.gui.framework.widgets.SettingsList;
import org.vivecraft.client_vr.settings.VRSettings;

import java.util.LinkedList;
import java.util.List;

/**
 * claudecraft: one page with every performance and stability toggle, so a single build can A/B them
 * in one headset session (see PLAN.md)
 */
public class GuiPerformanceSettings extends GuiListScreen {
    public GuiPerformanceSettings(Screen lastScreen) {
        super(Component.translatable("vivecraft.options.screen.performance"), lastScreen);
    }

    @Override
    protected List<SettingsList.BaseEntry> getEntries() {
        List<SettingsList.BaseEntry> entries = new LinkedList<>();
        entries.add(new SettingsList.CategoryEntry(Component.translatable("vivecraft.options.performance.memory")));
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.FREE_MENU_WORLD_IN_GAME));
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.SHARE_OUTLINE_TARGETS));
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.LAZY_AUX_BUFFERS));
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.FIX_SUPPORTER_LEAK));
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.CLEAR_TRACKER_REFS));

        entries.add(new SettingsList.CategoryEntry(Component.translatable("vivecraft.options.performance.openxr")));
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.XR_REFRESH_RATE));
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.XR_LATE_FRAME_WAIT));
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.CACHE_INPUT_ORIGINS));
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.XR_STARTUP_RETRY));
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.XR_SESSION_STATE_HANDLING));
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.XR_PERF_HINTS));

        entries.add(new SettingsList.CategoryEntry(Component.translatable("vivecraft.options.performance.rendering")));
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.GL_ERROR_CHECKS));
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.LTW_LINEAR_FILTER_FIX));
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.SHARED_EYE_CULLING));

        entries.add(new SettingsList.CategoryEntry(Component.translatable("vivecraft.options.performance.startup")));
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.API_REGISTER_ONCE));

        entries.add(new SettingsList.CategoryEntry(Component.translatable("vivecraft.options.performance.diagnostics")));
        entries.add(SettingsList.vrOptionToEntry(VRSettings.VrOptions.PERF_MEMORY_LOG));
        return entries;
    }
}
