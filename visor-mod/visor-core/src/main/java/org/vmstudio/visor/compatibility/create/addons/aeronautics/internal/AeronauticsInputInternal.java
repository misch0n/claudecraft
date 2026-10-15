package org.vmstudio.visor.compatibility.create.addons.aeronautics.internal;

import org.vmstudio.visor.api.common.utils.LoggerUtils;
import org.vmstudio.visor.compatibility.OneShotSetup;

import java.lang.reflect.Method;

public final class AeronauticsInputInternal {
    private static final String HOLD_MANAGER_CLASS =
            "dev.simulated_team.simulated.util.hold_interaction.HoldInteractionManager";
    private static final String CLIENT_EVENTS_CLASS =
            "dev.simulated_team.simulated.events.SimulatedCommonClientEvents";
    private static final String RESULT_CLASS =
            "dev.simulated_team.simulated.util.click_interactions.InteractCallback$Result";
    private static final String CLICK_INTERACTIONS_CLASS =
            "dev.simulated_team.simulated.index.SimClickInteractions";
    private static final String TYPEWRITER_HANDLER_CLASS =
            "dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterInteractionHandler";

    private static final OneShotSetup SETUP = new OneShotSetup(AeronauticsInputInternal::resolve);

    private static Method isHoldActiveMethod;
    private static Method onMouseMoveMethod;
    private static Method onMouseScrollMethod;
    private static Method resultCancelledMethod;

    private static Object handleHandlerInstance;
    private static Method isHandleActiveMethod;
    private static Method getTypewriterModeMethod;


    public static boolean isHoldInteractionActive() {
        if (!SETUP.ok()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(isHoldActiveMethod.invoke(null));
        } catch (Throwable t) {
            fail("read the Simulated hold interaction state", t);
            return false;
        }
    }

    public static boolean isHandleActive() {
        if (!SETUP.ok() || handleHandlerInstance == null || isHandleActiveMethod == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(isHandleActiveMethod.invoke(handleHandlerInstance));
        } catch (Throwable t) {
            fail("read the Simulated handle state", t);
            return false;
        }
    }

    public static boolean isTypewriterActive() {
        if (!SETUP.ok() || getTypewriterModeMethod == null) {
            return false;
        }
        try {
            Object mode = getTypewriterModeMethod.invoke(null);
            return mode != null && "ACTIVE".equals(mode.toString());
        } catch (Throwable t) {
            fail("read the Simulated typewriter state", t);
            return false;
        }
    }

    public static boolean sendMouseMove(double yaw, double pitch) {
        return dispatch(onMouseMoveMethod, yaw, pitch, "mouse movement");
    }
    public static boolean sendMouseScroll(double deltaX, double deltaY) {
        return dispatch(onMouseScrollMethod, deltaX, deltaY, "mouse scroll");
    }

    private static boolean dispatch(Method method, double x, double y, String what) {
        if (!SETUP.ok()) {
            return false;
        }
        try {
            Object result = method.invoke(null, x, y);
            return result != null
                    && Boolean.TRUE.equals(resultCancelledMethod.invoke(result));
        } catch (Throwable t) {
            fail("send " + what + " to Simulated", t);
            return false;
        }
    }

    private static void fail(String what, Throwable t) {
        SETUP.disable();
        LoggerUtils.getLogger().warn("Visor: failed to {}, HUD input compat disabled", what, t);
    }

    private static boolean resolve() throws ReflectiveOperationException {
        isHoldActiveMethod = Class.forName(HOLD_MANAGER_CLASS)
                .getMethod("isActive");

        Class<?> clientEvents = Class.forName(CLIENT_EVENTS_CLASS);
        onMouseMoveMethod = clientEvents.getMethod("onMouseMove", double.class, double.class);
        onMouseScrollMethod = clientEvents.getMethod("onMouseScroll", double.class, double.class);

        resultCancelledMethod = Class.forName(RESULT_CLASS).getMethod("cancelled");

        Class<?> clickInteractions = Class.forName(CLICK_INTERACTIONS_CLASS);
        handleHandlerInstance = clickInteractions.getField("HANDLE_HANDLER").get(null);
        isHandleActiveMethod = handleHandlerInstance.getClass().getMethod("isActive");

        Class<?> typewriterHandler = Class.forName(TYPEWRITER_HANDLER_CLASS);
        getTypewriterModeMethod = typewriterHandler.getMethod("getMode");

        return true;
    }


    private AeronauticsInputInternal() {
        throw new UnsupportedOperationException("This is an utility class and cannot be instantiated");
    }
}
