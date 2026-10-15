package org.vmstudio.visor.compatibility.create.addons.aeroworks.internal;

import org.vmstudio.visor.api.common.utils.LoggerUtils;
import org.vmstudio.visor.compatibility.OneShotSetup;

import java.lang.reflect.Method;

public final class AeroworksInputInternal {
    private static final String CONSOLE_CONTROL_CLIENT_CLASS =
            "com.mred231.aeroworks.content.controls.console.ConsoleControlClient";

    private static final OneShotSetup SETUP = new OneShotSetup(AeroworksInputInternal::resolve);

    private static Method isActiveMethod;
    private static Method isMouseCapturedMethod;
    private static Method feedMouseDeltaMethod;
    private static Method feedScrollMethod;
    private static Method requestExitMethod;

    public static boolean isConsoleActive() {
        if (!SETUP.ok()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(isActiveMethod.invoke(null));
        } catch (Throwable t) {
            fail("check if Aeroworks console is active", t);
            return false;
        }
    }

    public static boolean isMouseCaptured() {
        if (!SETUP.ok()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(isMouseCapturedMethod.invoke(null));
        } catch (Throwable t) {
            fail("check if Aeroworks mouse is captured", t);
            return false;
        }
    }

    public static void feedMouseDelta(double dx, double dy) {
        if (!SETUP.ok()) {
            return;
        }
        try {
            feedMouseDeltaMethod.invoke(null, dx, dy);
        } catch (Throwable t) {
            fail("feed mouse delta to Aeroworks", t);
        }
    }

    public static boolean feedScroll(double delta) {
        if (!SETUP.ok()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(feedScrollMethod.invoke(null, delta));
        } catch (Throwable t) {
            fail("feed scroll to Aeroworks", t);
            return false;
        }
    }

    public static void requestExit() {
        if (!SETUP.ok()) {
            return;
        }
        try {
            requestExitMethod.invoke(null);
        } catch (Throwable t) {
            fail("request exit from Aeroworks console", t);
        }
    }

    private static void fail(String what, Throwable t) {
        SETUP.disable();
        LoggerUtils.getLogger().warn("Visor: failed to {}, Aeroworks compat disabled", what, t);
    }

    private static boolean resolve() throws ReflectiveOperationException {
        Class<?> consoleClient = Class.forName(CONSOLE_CONTROL_CLIENT_CLASS);
        isActiveMethod = consoleClient.getMethod("isActive");
        isMouseCapturedMethod = consoleClient.getMethod("isMouseCaptured");
        feedMouseDeltaMethod = consoleClient.getMethod("feedMouseDelta", double.class, double.class);
        feedScrollMethod = consoleClient.getMethod("feedScroll", double.class);
        requestExitMethod = consoleClient.getMethod("requestExit");
        return true;
    }

    private AeroworksInputInternal() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
