package org.vivecraft.client_vr.perf;

/**
 * claudecraft: static hook points, so a setting change can reach the running VR provider without the
 * settings class depending on provider classes.
 */
public final class PerfHooks {
    private PerfHooks() {}

    /**
     * set by the OpenXR provider while a session exists; applies {@code xrRefreshRate} to it
     */
    public static volatile Runnable refreshRateApplier = null;

    public static void onRefreshRateChanged() {
        Runnable applier = refreshRateApplier;
        if (applier != null) {
            applier.run();
        }
    }
}
