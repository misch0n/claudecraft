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
 * claudecraft (vivecraft.installBundled, PLAN 1p): the patched APK carries our Vivecraft build in
 * {@code assets/claudecraft/Vivecraft.jar} and {@code assets/claudecraft/build.properties} (build_tag,
 * vivecraft_jar, mc_version; written by apk/build-apk.sh). At every launch, after the mod manifest update
 * (API.prelaunch) and before the JVM starts (called first thing in JREUtils.launchJavaVM), the jar is copied over
 * the instance's Vivecraft mod file.
 *
 * <p>The target is the file Pojlib's updater manages (MinecraftInstances.Instance.updateModByType):
 * {@code <gameDir>/mods/<fileName or slug>.jar} of the instance's "Vivecraft" mod entry, i.e. mods/Vivecraft.jar
 * for the manifest's core mod (a legacy entry without fileName). The updater downloads only when that file is
 * missing or the manifest's version changes. We keep the name and never touch instances.json, so the next
 * launch does not download over our jar; if a new manifest version does download, this copy runs again right
 * after it.
 */
public final class BundledVivecraft {
    private BundledVivecraft() {}

    private static final String ASSET_DIR = "claudecraft/";
    private static final String SLUG = "Vivecraft";

    public static void install(Context ctx, MinecraftInstances.Instance instance) {
        try {
            if (!ClaudecraftConfig.getBoolean(ClaudecraftConfig.VIVECRAFT_INSTALL_BUNDLED)) {
                log("bundled Vivecraft not installed (vivecraft.installBundled=false); the instance keeps its current jar");
                return;
            }
            AssetManager assets = ctx.getAssets();
            Properties build = new Properties();
            try (InputStream in = assets.open(ASSET_DIR + "build.properties")) {
                build.load(in);
            } catch (IOException e) {
                log("no bundled Vivecraft in this APK (" + e.getMessage() + ")");
                return;
            }
            String tag = build.getProperty("build_tag", "?").trim();
            String jarName = build.getProperty("vivecraft_jar", "?").trim();
            String mc = build.getProperty("mc_version", "").trim();
            if (!mcVersionMatches(mc, instance.versionName)) {
                log("bundled Vivecraft " + tag + " is for Minecraft " + mc + " but instance '" + instance.instanceName
                        + "' is " + instance.versionName + ": not installed");
                return;
            }
            File target = vivecraftModFile(instance);
            if (target == null) {
                log("instance '" + instance.instanceName + "' has no Vivecraft mod entry: bundled Vivecraft not installed");
                return;
            }
            File dir = target.getParentFile();
            if (dir != null) dir.mkdirs();
            // Not *.jar, so Fabric never loads a half-written file.
            File tmp = new File(dir, target.getName() + ".claudecraft-tmp");
            long bytes = 0;
            try (InputStream in = assets.open(ASSET_DIR + "Vivecraft.jar"); OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                    bytes += n;
                }
            }
            if (bytes == 0) {
                tmp.delete();
                log("bundled Vivecraft.jar is empty: not installed");
                return;
            }
            if (!tmp.renameTo(target)) {
                target.delete();
                if (!tmp.renameTo(target)) {
                    tmp.delete();
                    log("could not write " + target + ": bundled Vivecraft not installed");
                    return;
                }
            }
            log("installed bundled Vivecraft " + tag + " (" + jarName + ", " + bytes + " bytes) as " + target);
        } catch (Throwable t) {
            log("bundled Vivecraft install failed: " + t);
        }
    }

    /** The instance's Vivecraft mod file, named exactly as MinecraftInstances.Instance.updateModByType names it. */
    private static File vivecraftModFile(MinecraftInstances.Instance instance) {
        if (instance.extProjects == null || instance.gameDir == null) return null;
        for (ProjectInfo p : instance.extProjects) {
            if (p == null || p.slug == null || !SLUG.equalsIgnoreCase(p.slug)) continue;
            if (p.type != null && !p.type.equals("mod")) continue;
            String base = p.fileName == null ? p.slug : p.fileName;
            return new File(instance.gameDir + "/mods", base + ".jar");
        }
        return null;
    }

    /**
     * True when mc_version is empty, or one of its entries equals the instance's version. Accepts "1.21.5",
     * "1.21.5,1.21.4" and the list form fabric.mod.json uses (e.g. "['1.21.5']" from older build scripts).
     */
    static boolean mcVersionMatches(String mcVersion, String instanceVersion) {
        String v = mcVersion == null ? "" : mcVersion.replaceAll("[\\[\\]'\"]", " ").trim();
        if (v.isEmpty()) return true;
        for (String token : v.split("[,\\s]+")) {
            if (!token.isEmpty() && token.equals(instanceVersion)) return true;
        }
        return false;
    }

    private static void log(String msg) {
        Logger.getInstance().appendToLog("[claudecraft] " + msg);
    }
}
