package org.vivecraft.client_vr.perf;

import org.vivecraft.Xloader;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.settings.VRSettings;

import java.io.IOException;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * claudecraft: the {@code [VCPERF]} log lines.
 * <ul>
 *     <li>{@code [VCPERF] config}: build tag and every perf toggle, logged on VR init (CLAUDE.md rule 2)</li>
 *     <li>{@code [VCPERF] mem}: Java heap, metaspace, code cache, direct buffers, GC counts and the
 *     process RSS/PSS split, at milestones and every 30 s while {@code perfMemoryLog} is on</li>
 * </ul>
 * dumpsys meminfo's "Java Heap" row only covers Android's ART, so these lines are how the
 * Minecraft JVM's own memory is measured.
 */
public final class PerfProbe {
    private static final long PERIOD_NANOS = 30_000_000_000L;
    private static final long MIB = 1024L * 1024L;
    private static final Map<String, String> EXTRA = new ConcurrentSkipListMap<>();

    private static long lastPeriodic = System.nanoTime();
    private static long joinedAt = -1;
    private static boolean jvmLogged = false;

    private PerfProbe() {}

    /**
     * adds a value to every following config line, e.g. the refresh rate the runtime actually uses
     */
    public static void put(String key, Object value) {
        EXTRA.put(key, String.valueOf(value));
    }

    /**
     * logs the build tag and all perf toggles
     */
    public static void logConfig(String reason) {
        VRSettings s = ClientDataHolderVR.getInstance().vrSettings;
        if (s == null) {
            return;
        }
        VRSettings.LOGGER.info(
            "[VCPERF] config reason={} build={} immersivemc={} perfMemoryLog={} freeMenuWorldInGame={} " +
                "shareOutlineTargets={} lazyAuxBuffers={} fixSupporterLeak={} clearTrackerRefs={} " +
                "xrStartupRetry={} xrSessionStateHandling={} xrLateFrameWait={} xrRefreshRate={} " +
                "cacheInputOrigins={} glErrorChecks={} ltwLinearFilterFix={} xrPerfHints={} menuWorldSelection={} {}",
            reason, Xloader.getModVersion(), Objects.requireNonNullElse(Xloader.getModVersionById("immersivemc"), "absent"),
            s.perfMemoryLog, s.freeMenuWorldInGame,
            s.shareOutlineTargets, s.lazyAuxBuffers, s.fixSupporterLeak, s.clearTrackerRefs,
            s.xrStartupRetry, s.xrSessionStateHandling, s.xrLateFrameWait, s.xrRefreshRate,
            s.cacheInputOrigins, s.glErrorChecks, s.ltwLinearFilterFix, s.xrPerfHints, s.menuWorldSelection,
            EXTRA);
        if (!jvmLogged) {
            jvmLogged = true;
            try {
                List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
                VRSettings.LOGGER.info("[VCPERF] jvm version={} args={}", System.getProperty("java.vm.version"),
                    args);
            } catch (Throwable t) {
                VRSettings.LOGGER.warn("[VCPERF] jvm args unavailable: {}", t.toString());
            }
        }
    }

    /**
     * logs a memory line now, tagged with {@code label}
     */
    public static void milestone(String label) {
        VRSettings s = ClientDataHolderVR.getInstance().vrSettings;
        if (s != null && s.perfMemoryLog) {
            logMemory(label);
        }
    }

    /**
     * a world was joined: logs now, and once more 60 s later ("settled" in the protocol)
     */
    public static void markJoin() {
        joinedAt = System.nanoTime();
        milestone("join");
    }

    /**
     * call once per client tick; logs a memory line every 30 s
     */
    public static void tick() {
        long now = System.nanoTime();
        if (joinedAt >= 0 && now - joinedAt >= 2 * PERIOD_NANOS) {
            joinedAt = -1;
            milestone("join+60s");
        }
        if (now - lastPeriodic >= PERIOD_NANOS) {
            lastPeriodic = now;
            milestone("periodic");
        }
    }

    private static void logMemory(String label) {
        try {
            MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            long metaspace = -1, classSpace = -1, codeCache = 0;
            for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
                String name = pool.getName();
                long committed = pool.getUsage().getCommitted();
                if (name.equals("Metaspace")) {
                    metaspace = committed;
                } else if (name.equals("Compressed Class Space")) {
                    classSpace = committed;
                } else if (name.startsWith("CodeHeap") || name.equals("Code Cache")) {
                    codeCache += committed;
                }
            }
            long direct = -1;
            for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
                if (pool.getName().equals("direct")) {
                    direct = pool.getMemoryUsed();
                }
            }
            StringBuilder gc = new StringBuilder();
            for (GarbageCollectorMXBean bean : ManagementFactory.getGarbageCollectorMXBeans()) {
                gc.append(bean.getName().replace(' ', '_')).append('=').append(bean.getCollectionCount())
                    .append('/').append(bean.getCollectionTime()).append("ms ");
            }
            VRSettings.LOGGER.info(
                "[VCPERF] mem label={} heapUsed={}M heapCommitted={}M heapMax={}M metaspace={}M classSpace={}M " +
                    "codeCache={}M direct={}M {} {} gc[{}]",
                label, heap.getUsed() / MIB, heap.getCommitted() / MIB, heap.getMax() / MIB, metaspace / MIB,
                classSpace / MIB, codeCache / MIB, direct / MIB,
                procFields("/proc/self/status", "VmRSS", "RssAnon", "RssFile", "RssShmem", "VmSwap", "Threads"),
                procFields("/proc/self/smaps_rollup", "Pss", "Pss_Anon", "Pss_File", "Pss_Shmem"),
                gc.toString().trim());
        } catch (Throwable t) {
            VRSettings.LOGGER.warn("[VCPERF] mem label={} failed: {}", label, t.toString());
        }
    }

    /**
     * picks "Key: value kB" lines out of a /proc file, returns them as {@code Key=valueM}
     */
    private static String procFields(String file, String... keys) {
        Path path = Path.of(file);
        if (!Files.isReadable(path)) {
            return "";
        }
        try {
            StringBuilder out = new StringBuilder();
            for (String line : Files.readAllLines(path, StandardCharsets.US_ASCII)) {
                int colon = line.indexOf(':');
                if (colon <= 0) {
                    continue;
                }
                String key = line.substring(0, colon);
                for (String wanted : keys) {
                    if (wanted.equals(key)) {
                        String value = line.substring(colon + 1).trim();
                        if (value.endsWith(" kB")) {
                            value = (Long.parseLong(value.substring(0, value.length() - 3).trim()) / 1024L) + "M";
                        }
                        out.append(key).append('=').append(value).append(' ');
                    }
                }
            }
            return out.toString().trim();
        } catch (IOException | RuntimeException e) {
            return file + "=unreadable";
        }
    }
}
