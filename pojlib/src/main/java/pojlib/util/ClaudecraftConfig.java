package pojlib.util;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * claudecraft: launcher settings, read from {@code claudecraft.properties} in {@link Constants#USER_HOME}
 * (the app's external files dir, e.g. /sdcard/Android/data/com.qcxr.qcxr/files).
 *
 * <ul>
 *   <li>A missing file is written with every key, its default and a short comment; keys missing from an
 *       existing file are appended with their defaults.</li>
 *   <li>The file is re-read whenever it changes, so edits made while the launcher is open still apply to
 *       the next game launch.</li>
 *   <li>Invalid values fall back to the default, with a warning in the launcher log.</li>
 *   <li>The effective values are written to the launcher log (prefix {@code [claudecraft]}) the first time a
 *       setting is used and again whenever they change.</li>
 * </ul>
 *
 * Loading never touches {@link Logger}: the Logger itself reads {@code log.keepPrevious} through
 * {@link #peekBoolean(String)} while it is being created, so the config must not log from that path, and
 * the config lock is never held while calling into the Logger.
 */
public final class ClaudecraftConfig {
    private ClaudecraftConfig() {}

    private static final String TAG = "claudecraft";
    public static final String FILE_NAME = "claudecraft.properties";

    public static final String JVM_POLICY = "jvm.policy";
    public static final String JVM_GC = "jvm.gc";
    public static final String JVM_GC_LOG = "jvm.gcLog";
    public static final String JVM_EXTRA_ARGS = "jvm.extraArgs";
    public static final String LOG_KEEP_PREVIOUS = "log.keepPrevious";
    public static final String LOG_KEEP_LOGCAT = "log.keepLogcat";
    public static final String LOG_REDACT_TOKEN = "log.redactToken";
    public static final String UNITY_PAUSE_IN_GAME = "unity.pauseInGame";
    public static final String UNITY_PAUSE_DELAY_MS = "unity.pauseDelayMs";
    public static final String VIVECRAFT_INSTALL_BUNDLED = "vivecraft.installBundled";
    public static final String IMMERSIVEMC_INSTALL = "immersivemc.install";

    public static final String POLICY_CLAUDECRAFT = "claudecraft";
    public static final String POLICY_ORIGINAL = "original";
    public static final String GC_ZGC = "zgc";
    public static final String GC_G1 = "g1";

    /** Key, default value, comment; in file order. */
    private static final String[][] KEYS = {
            {JVM_POLICY, POLICY_CLAUDECRAFT, "JVM heap and GC flags. claudecraft: -Xmx = QuestCraft's custom RAM value if enabled, else 2048 MB; -Xms512M; ZGC with SoftMaxHeapSize = 75% of -Xmx and ZUncommitDelay=30. original: QuestCraft's own flags, unchanged."},
            {JVM_GC, GC_ZGC, "Garbage collector when jvm.policy=claudecraft: zgc or g1 (ignored when jvm.policy=original)."},
            {JVM_GC_LOG, "true", "Write a JVM GC log to gc.log in this folder (3 rotated files of 10 MB)."},
            {JVM_EXTRA_ARGS, "", "Extra JVM options, separated by spaces, added after all others (they win). A bad option stops the game from starting."},
            {LOG_KEEP_PREVIOUS, "true", "Keep the previous launcher log as latestlog.prev.txt instead of deleting it at app start."},
            {LOG_KEEP_LOGCAT, "true", "Do not clear the system log (logcat) when the game starts."},
            {LOG_REDACT_TOKEN, "true", "Print the game launch arguments to logcat with the access token replaced by <redacted>."},
            {UNITY_PAUSE_IN_GAME, "true", "Pause the Unity launcher once Minecraft is in VR and keep it paused while the game runs."},
            {UNITY_PAUSE_DELAY_MS, "10000", "Milliseconds to wait after Minecraft reports VR ready before pausing Unity (Unity fades out and shuts its XR down first)."},
            {VIVECRAFT_INSTALL_BUNDLED, "true", "Copy the Vivecraft jar bundled in this APK over the instance's Vivecraft mod at every launch. Turn off to test a jar pushed by hand."},
            {IMMERSIVEMC_INSTALL, "true", "Install the ImmersiveMC mod bundled in this APK as mods/ImmersiveMC-claudecraft.jar at every launch. false deletes that file, so ImmersiveMC is not loaded. Its in-game config can also turn off single immersives."},
    };

    private static final String HEADER =
            "# claudecraft launcher settings (personal QuestCraft build).\n"
            + "# Read at every game launch. Delete a line to get its default back.\n";

    private static Properties sValues;            // effective values; null until USER_HOME is known
    private static long sStamp = Long.MIN_VALUE;  // identity of the file contents sValues came from
    private static List<String> sNotes = new ArrayList<>(); // warnings not yet written to the Logger
    private static String sLoggedSummary;         // summary last written to the Logger

    // ---- typed getters (these log the effective config once, and again when it changes) ----

    public static String getString(String key) {
        return value(key, true);
    }

    public static boolean getBoolean(String key) {
        return "true".equals(value(key, true));
    }

    public static int getInt(String key) {
        try {
            return Integer.parseInt(value(key, true));
        } catch (NumberFormatException e) {
            return Integer.parseInt(defaultOf(key));
        }
    }

    /** Like {@link #getBoolean(String)} but never writes to the Logger (safe while the Logger is created). */
    public static boolean peekBoolean(String key) {
        return "true".equals(value(key, false));
    }

    public static boolean isJvmPolicyClaudecraft() {
        return POLICY_CLAUDECRAFT.equals(getString(JVM_POLICY));
    }

    public static boolean isJvmGcG1() {
        return GC_G1.equals(getString(JVM_GC));
    }

    /** {@code jvm.extraArgs} split on whitespace; empty list when unset. */
    public static List<String> getJvmExtraArgs() {
        String v = getString(JVM_EXTRA_ARGS).trim();
        List<String> out = new ArrayList<>();
        if (v.isEmpty()) return out;
        Collections.addAll(out, v.split("\\s+"));
        return out;
    }

    public static String getFilePath() {
        return new File(Constants.USER_HOME, FILE_NAME).getAbsolutePath();
    }

    // ---- implementation ----

    private static String value(String key, boolean log) {
        Properties p;
        try {
            p = load();
        } catch (Throwable t) {
            Log.w(TAG, "config load failed, using defaults", t);
            p = defaults();
        }
        if (log) logIfChanged();
        String v = p.getProperty(key);
        return v != null ? v : defaultOf(key);
    }

    private static String defaultOf(String key) {
        for (String[] k : KEYS) {
            if (k[0].equals(key)) return k[1];
        }
        throw new IllegalArgumentException("unknown claudecraft key " + key);
    }

    private static Properties defaults() {
        Properties p = new Properties();
        for (String[] k : KEYS) p.setProperty(k[0], k[1]);
        return p;
    }

    private static long stampOf(File file) {
        return file.isFile() ? file.lastModified() * 31 + file.length() : -1;
    }

    private static synchronized Properties load() {
        String home = Constants.USER_HOME;
        if (home == null) return defaults(); // too early (before UnityPlayerActivity.onCreate); retry later
        File file = new File(home, FILE_NAME);
        long stamp = stampOf(file);
        if (sValues != null && stamp == sStamp) return sValues;

        List<String> notes = new ArrayList<>();
        Properties raw = new Properties();
        boolean readOk = true;
        if (file.isFile()) {
            try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                raw.load(r);
            } catch (Throwable t) {
                readOk = false;
                raw.clear();
                notes.add("could not read " + file + " (" + t + "); using defaults");
            }
        }

        // Add any missing keys (with defaults) so the file always documents every setting.
        if (readOk) {
            StringBuilder missing = new StringBuilder();
            for (String[] k : KEYS) {
                if (!raw.containsKey(k[0])) missing.append("\n# ").append(k[2]).append('\n').append(k[0]).append('=').append(k[1]).append('\n');
            }
            if (missing.length() > 0) {
                boolean created = !file.exists();
                try (Writer w = new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8)) {
                    if (created) w.write(HEADER);
                    w.write(missing.toString());
                    notes.add(created ? "created " + file + " with defaults" : "added missing keys with defaults to " + file);
                } catch (Throwable t) {
                    notes.add("could not write " + file + " (" + t + ")");
                }
                stamp = stampOf(file);
            }
        }

        Properties values = new Properties();
        for (String[] k : KEYS) {
            String v = raw.getProperty(k[0]);
            v = v == null ? k[1] : v.trim();
            String n = normalise(k[0], v);
            if (n == null) {
                notes.add(k[0] + "=" + v + " is not valid; using " + k[1]);
                n = k[1];
            }
            values.setProperty(k[0], n);
        }
        sValues = values;
        sStamp = stamp;
        sNotes.addAll(notes);
        return values;
    }

    /** Canonical form of a value, or null when it is not valid for the key. */
    private static String normalise(String key, String v) {
        String l = v.toLowerCase(Locale.ROOT);
        switch (key) {
            case JVM_POLICY:
                return POLICY_CLAUDECRAFT.equals(l) || POLICY_ORIGINAL.equals(l) ? l : null;
            case JVM_GC:
                return GC_ZGC.equals(l) || GC_G1.equals(l) ? l : null;
            case JVM_EXTRA_ARGS:
                return v;
            case UNITY_PAUSE_DELAY_MS:
                try {
                    int ms = Integer.parseInt(l);
                    return ms >= 0 ? Integer.toString(ms) : null;
                } catch (NumberFormatException e) {
                    return null;
                }
            default: // booleans
                if (l.equals("true") || l.equals("on") || l.equals("yes") || l.equals("1")) return "true";
                if (l.equals("false") || l.equals("off") || l.equals("no") || l.equals("0")) return "false";
                return null;
        }
    }

    private static void logIfChanged() {
        String summary;
        List<String> notes;
        synchronized (ClaudecraftConfig.class) {
            if (sValues == null) return; // not loaded from a file yet
            StringBuilder sb = new StringBuilder();
            for (String[] k : KEYS) sb.append(' ').append(k[0]).append('=').append(sValues.getProperty(k[0]));
            summary = sb.toString();
            if (summary.equals(sLoggedSummary) && sNotes.isEmpty()) return;
            sLoggedSummary = summary;
            notes = sNotes;
            sNotes = new ArrayList<>();
        }
        // Outside the lock: the Logger may be created here, and it calls peekBoolean() (which takes the lock).
        try {
            Logger logger = Logger.getInstance();
            for (String n : notes) logger.appendToLog("[claudecraft] config: " + n);
            logger.appendToLog("[claudecraft] config (" + FILE_NAME + "):" + summary);
        } catch (Throwable t) {
            Log.w(TAG, "could not write config to the launcher log", t);
        }
        Log.i(TAG, "config:" + summary);
    }
}
