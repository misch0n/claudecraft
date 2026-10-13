package pojlib;

import android.app.Activity;
import android.os.SystemClock;

import pojlib.util.ClaudecraftConfig;
import pojlib.util.Logger;

/**
 * claudecraft (unity.pauseInGame, PLAN 1o): once Minecraft is in VR, pause the Unity player so it stops running
 * and rendering its launcher scene beside the game, and keep it paused while the game runs
 * ({@link UnityPlayerActivity} skips {@code resume()} and focus forwarding while {@link #isPausedInGame()}).
 *
 * <p>Timeline: Vivecraft sets {@link API#gameReady} when it starts VR. Unity's InstanceButton.Update() sees it,
 * fades for 1 s, waits 200 ms and deinitialises its XR loader; Vivecraft waits 2.5 s and creates its own OpenXR
 * session. We pause only {@code unity.pauseDelayMs} (default 10 s) after gameReady, so Unity's XR shutdown has
 * finished first: a paused Unity cannot run that shutdown.
 *
 * <p>The game normally ends by killing the whole process (System.exit or the launcher restart). If the JVM
 * returns instead, {@link #onGameEnded(Activity)} resumes Unity, as it was before.
 */
public final class ClaudecraftUnityPause {
    private ClaudecraftUnityPause() {}

    private static volatile boolean sPausedInGame;
    private static volatile boolean sGameEnded;

    /** True while we keep Unity paused: UnityPlayerActivity must not resume it or forward focus changes. */
    public static boolean isPausedInGame() {
        return sPausedInGame;
    }

    /** Called right before the (blocking) JVM launch. Starts a daemon thread that waits for API.gameReady. */
    public static void startWatcher(final Activity activity) {
        sGameEnded = false;
        try {
            if (!ClaudecraftConfig.getBoolean(ClaudecraftConfig.UNITY_PAUSE_IN_GAME)) {
                log("Unity stays running in game (unity.pauseInGame=false)");
                return;
            }
            if (!(activity instanceof UnityPlayerActivity)) {
                log("Unity pause: launch activity is not a UnityPlayerActivity (" + activity + "); not pausing");
                return;
            }
            final long delayMs = ClaudecraftConfig.getInt(ClaudecraftConfig.UNITY_PAUSE_DELAY_MS);
            final UnityPlayerActivity unity = (UnityPlayerActivity) activity;
            Thread watcher = new Thread(() -> {
                try {
                    long start = SystemClock.elapsedRealtime();
                    // API.gameReady is set from native code (VLoader.setupAndroid); polled, the field is not volatile.
                    while (!API.gameReady) {
                        if (sGameEnded) return;
                        Thread.sleep(250);
                    }
                    log("Unity pause: game ready " + (SystemClock.elapsedRealtime() - start) + " ms after JVM start; pausing Unity in " + delayMs + " ms");
                    Thread.sleep(delayMs);
                    if (sGameEnded) return;
                    unity.runOnUiThread(() -> pauseNow(unity));
                } catch (Throwable t) {
                    log("Unity pause watcher stopped: " + t);
                }
            }, "claudecraft-unity-pause");
            watcher.setDaemon(true);
            watcher.start();
        } catch (Throwable t) {
            log("Unity pause watcher not started: " + t);
        }
    }

    /** UI thread. */
    private static void pauseNow(UnityPlayerActivity unity) {
        if (sGameEnded || sPausedInGame) return;
        try {
            long t0 = SystemClock.elapsedRealtime();
            unity.mUnityPlayer.pause();
            sPausedInGame = true;
            log("Unity player paused (" + (SystemClock.elapsedRealtime() - t0) + " ms); it stays paused while the game runs");
        } catch (Throwable t) {
            log("Unity pause failed: " + t);
        }
    }

    /** Called when the JVM launch call returned (the game ended without killing the process). */
    public static void onGameEnded(final Activity activity) {
        sGameEnded = true;
        if (!sPausedInGame || !(activity instanceof UnityPlayerActivity)) return;
        final UnityPlayerActivity unity = (UnityPlayerActivity) activity;
        unity.runOnUiThread(() -> {
            if (!sPausedInGame) return;
            sPausedInGame = false;
            try {
                unity.mUnityPlayer.resume();
                unity.mUnityPlayer.windowFocusChanged(unity.hasWindowFocus());
                log("Game ended; Unity player resumed");
            } catch (Throwable t) {
                log("Unity resume failed: " + t);
            }
        });
    }

    private static void log(String msg) {
        try {
            Logger.getInstance().appendToLog("[claudecraft] " + msg);
        } catch (Throwable ignored) {
        }
        android.util.Log.i("claudecraft", msg);
    }
}
