package pojlib.util;

import android.content.Context;
import android.content.res.AssetManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

import pojlib.util.json.MinecraftInstances;
import pojlib.util.json.ProjectInfo;

/**
 * claudecraft (immersivemc.install): the patched APK can carry our ImmersiveMC build in
 * {@code assets/claudecraft/ImmersiveMC.jar}, described by {@code immersivemc_jar}, {@code immersivemc_version} and
 * {@code immersivemc_mc_version} in {@code assets/claudecraft/build.properties} (apk/build-apk.sh --immersivemc).
 * Called right after {@link BundledVivecraft#install} at every launch.
 *
 * <p>QuestCraft's mods.json has no ImmersiveMC, so the jar goes in under a fixed name of its own,
 * {@code <gameDir>/mods/ImmersiveMC-claudecraft.jar}. Pojlib's updater only touches the files of the instance's
 * mod entries, so it never deletes or replaces this one. When the key is off, the APK has no ImmersiveMC, the
 * Minecraft version does not match, or the instance already has an ImmersiveMC entry of its own (a second copy
 * would stop Fabric from starting), the file is deleted instead, so "off" really means not loaded.
 */
public final class BundledImmersiveMC {
    private BundledImmersiveMC() {}

    private static final String ASSET_DIR = "claudecraft/";
    static final String FILE_NAME = "ImmersiveMC-claudecraft.jar";

    public static void install(Context ctx, MinecraftInstances.Instance instance) {
        try {
            if (instance.gameDir == null) return;
            File target = new File(instance.gameDir + "/mods", FILE_NAME);
            if (!ClaudecraftConfig.getBoolean(ClaudecraftConfig.IMMERSIVEMC_INSTALL)) {
                log("bundled ImmersiveMC not installed (immersivemc.install=false)" + remove(target));
                return;
            }
            AssetManager assets = ctx.getAssets();
            Properties build = new Properties();
            try (InputStream in = assets.open(ASSET_DIR + "build.properties")) {
                build.load(in);
            } catch (IOException e) {
                log("no bundled ImmersiveMC in this APK (" + e.getMessage() + ")" + remove(target));
                return;
            }
            String version = build.getProperty("immersivemc_version", "").trim();
            if (version.isEmpty()) {
                log("no bundled ImmersiveMC in this APK" + remove(target));
                return;
            }
            String jarName = build.getProperty("immersivemc_jar", "?").trim();
            // Built against our Vivecraft for the same Minecraft: fall back to its mc_version.
            String mc = build.getProperty("immersivemc_mc_version", "").trim();
            if (mc.isEmpty()) mc = build.getProperty("mc_version", "").trim();
            if (!BundledVivecraft.mcVersionMatches(mc, instance.versionName)) {
                log("bundled ImmersiveMC " + version + " is for Minecraft " + mc + " but instance '" + instance.instanceName
                        + "' is " + instance.versionName + ": not installed" + remove(target));
                return;
            }
            String own = ownEntry(instance);
            if (own != null) {
                log("instance '" + instance.instanceName + "' has its own ImmersiveMC entry (" + own
                        + "): bundled ImmersiveMC " + version + " not installed" + remove(target));
                return;
            }
            File dir = target.getParentFile();
            if (dir != null) dir.mkdirs();
            // Not *.jar, so Fabric never loads a half-written file.
            File tmp = new File(dir, FILE_NAME + ".claudecraft-tmp");
            long bytes = 0;
            try (InputStream in = assets.open(ASSET_DIR + "ImmersiveMC.jar"); OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    bytes += n;
                }
            }
            if (bytes == 0) {
                tmp.delete();
                log("bundled ImmersiveMC.jar is empty: not installed" + remove(target));
                return;
            }
            if (!tmp.renameTo(target)) {
                target.delete();
                if (!tmp.renameTo(target)) {
                    tmp.delete();
                    log("could not write " + target + ": bundled ImmersiveMC not installed");
                    return;
                }
            }
            log("installed bundled ImmersiveMC " + version + " (" + jarName + ", " + bytes + " bytes) as " + target);
        } catch (Throwable t) {
            log("bundled ImmersiveMC install failed: " + t);
        }
    }

    /** Deletes our file if present; returns a note for the log line. */
    private static String remove(File target) {
        if (!target.exists()) return "";
        return target.delete() ? "; removed " + target : "; could not remove " + target;
    }

    /** The slug of an ImmersiveMC mod entry the user added to the instance, or null. */
    private static String ownEntry(MinecraftInstances.Instance instance) {
        if (instance.extProjects == null) return null;
        for (ProjectInfo p : instance.extProjects) {
            if (p == null || p.slug == null) continue;
            if (p.type != null && !p.type.equals("mod")) continue;
            String s = p.slug.replaceAll("[^A-Za-z]", "").toLowerCase(java.util.Locale.ROOT);
            if (s.equals("immersivemc")) return p.slug;
        }
        return null;
    }

    private static void log(String msg) {
        Logger.getInstance().appendToLog("[claudecraft] " + msg);
    }
}
