package org.vivecraft.client.network;

import net.minecraft.world.entity.player.Player;
import org.apache.commons.io.IOUtils;
import org.vivecraft.client.ClientVRPlayers;
import org.vivecraft.client_vr.ClientDataHolderVR;
import org.vivecraft.client_vr.settings.VRSettings;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SupporterReceiver {
    private static final Object LOCK = new Object();
    private static final List<Player> QUEUED_PLAYERS = new LinkedList<>();
    // claudecraft: fixSupporterLeak, queues UUID -> lowercase name instead of the Player (which pins its level)
    private static final Map<UUID, String> QUEUED_PLAYER_NAMES = new HashMap<>();
    private static Map<String, Integer> CACHE;
    private static boolean DOWNLOAD_STARTED;
    private static boolean DOWNLOAD_FAILED;

    private static void fileDownloadFinished(String url, String data, boolean addData) {
        synchronized (LOCK) {
            if (data != null) {
                try {
                    Map<String, Integer> userMap = new HashMap<>();
                    if (addData) {
                        userMap = CACHE;
                    }

                    String[] lines = data.split("\\r?\\n");

                    for (String user : lines) {
                        if (user.isEmpty()) {
                            continue;
                        }
                        try {
                            String[] bits = user.split(":");
                            int i = Integer.parseInt(bits[1]);
                            userMap.put(bits[0].toLowerCase(), i);

                            for (Player player : QUEUED_PLAYERS) {
                                if (bits[0].equalsIgnoreCase(player.getGameProfile().getName())) {
                                    ClientVRPlayers.getInstance().setHMD(player.getUUID(), i);
                                }
                            }
                            for (Map.Entry<UUID, String> queued : QUEUED_PLAYER_NAMES.entrySet()) {
                                if (bits[0].equalsIgnoreCase(queued.getValue())) {
                                    ClientVRPlayers.getInstance().setHMD(queued.getKey(), i);
                                }
                            }
                        } catch (Exception e) {
                            VRSettings.LOGGER.error("Vivecraft: error with supporters txt: {}", user, e);
                        }
                    }

                    CACHE = userMap;
                } catch (Exception e) {
                    VRSettings.LOGGER.error("Vivecraft: error parsing supporter data: {}", url, e);
                    DOWNLOAD_FAILED = true;
                }
            } else {
                DOWNLOAD_FAILED = true;
            }
        }
    }

    public static void addPlayerInfo(Player p) {
        if (!DOWNLOAD_FAILED && p.getGameProfile().getName() != null) {
            synchronized (LOCK) {
                if (CACHE == null) {
                    boolean fixLeak = ClientDataHolderVR.getInstance().vrSettings.fixSupporterLeak;
                    if (fixLeak) {
                        QUEUED_PLAYER_NAMES.put(p.getUUID(), p.getGameProfile().getName().toLowerCase());
                    } else {
                        QUEUED_PLAYERS.add(p);
                    }
                    ClientVRPlayers.getInstance().setHMD(p.getUUID(), 0);

                    if (!DOWNLOAD_STARTED) {
                        DOWNLOAD_STARTED = true;
                        String ogSupportersUrl = "https://www.vivecraft.org/patreon/current.txt";
                        String viveModSupportersUrl = "https://raw.githubusercontent.com/Vivecraft/VivecraftSupporters/supporters/supporters.txt";
                        new Thread(() -> {
                            if (fixLeak) {
                                downloadSupporters(ogSupportersUrl, viveModSupportersUrl);
                                return;
                            }
                            try {
                                String ogSupporters = IOUtils.toString(new URL(ogSupportersUrl),
                                    StandardCharsets.UTF_8);
                                String viveModSupporters = IOUtils.toString(new URL(viveModSupportersUrl),
                                    StandardCharsets.UTF_8);
                                fileDownloadFinished(ogSupportersUrl, ogSupporters, false);
                                fileDownloadFinished(viveModSupportersUrl, viveModSupporters, true);
                                synchronized (LOCK) {
                                    QUEUED_PLAYERS.clear();
                                    // claudecraft: only has entries if fixSupporterLeak was turned on meanwhile
                                    QUEUED_PLAYER_NAMES.clear();
                                }
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }).start();
                    }
                } else {
                    ClientVRPlayers.getInstance()
                        .setHMD(p.getUUID(), CACHE.getOrDefault(p.getGameProfile().getName().toLowerCase(), 0));
                }
            }
        }
    }

    /**
     * claudecraft: fixSupporterLeak. downloads both lists with timeouts. on any failure the download is marked as
     * failed, and the queue is emptied either way, so nothing waits for a download that never finishes
     */
    private static void downloadSupporters(String ogSupportersUrl, String viveModSupportersUrl) {
        try {
            String ogSupporters = httpReadString(ogSupportersUrl);
            String viveModSupporters = httpReadString(viveModSupportersUrl);
            fileDownloadFinished(ogSupportersUrl, ogSupporters, false);
            fileDownloadFinished(viveModSupportersUrl, viveModSupporters, true);
        } catch (Throwable e) {
            VRSettings.LOGGER.warn("Vivecraft: couldn't download supporter data: {}", e.toString());
            synchronized (LOCK) {
                DOWNLOAD_FAILED = true;
            }
        } finally {
            synchronized (LOCK) {
                QUEUED_PLAYERS.clear();
                QUEUED_PLAYER_NAMES.clear();
            }
        }
    }

    // claudecraft: fixSupporterLeak, 10 seconds connect and read timeout
    private static String httpReadString(String url) throws IOException {
        URLConnection conn = new URL(url).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        try (InputStream is = conn.getInputStream()) {
            return new String(IOUtils.toByteArray(is), StandardCharsets.UTF_8);
        }
    }
}
