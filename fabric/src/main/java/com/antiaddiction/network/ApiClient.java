package com.antiaddiction.network;

import com.antiaddiction.AntiAddictionMod;
import com.antiaddiction.data.DemoDatabase;
import com.antiaddiction.time.PlayTimeChecker;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ApiClient {

    private static String backendUrl = "";
    private static final int TIMEOUT_MS = 5000;

    public static void loadConfigAndFetchHolidays() {
        loadConfigAndFetchHolidays_impl();
    }

    private static void loadConfigAndFetchHolidays_impl() {
        try {
            Path configPath = FabricLoader.getInstance().getConfigDir().resolve("antiaddiction.properties");
            File configFile = configPath.toFile();
            if (!configFile.exists()) {
                try (PrintWriter w = new PrintWriter(configFile, StandardCharsets.UTF_8)) {
                    w.println("# 防沉迷后台服务器地址（留空则使用内置本地数据）");
                    w.println("# backend_url=http://localhost:3000");
                    w.println("backend_url=");
                }
                return;
            }

            Properties props = new Properties();
            try (Reader r = new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8)) {
                props.load(r);
            }
            String url = props.getProperty("backend_url", "").trim();
            if (!url.isEmpty()) {
                backendUrl = url;
                AntiAddictionMod.LOGGER.info("[防沉迷] 已配置后端地址: {}", url);
                LogCache.flush(backendUrl);
                Thread t = new Thread(() -> {
                    fetchUsers();
                    fetchRules();
                    startPeriodicSync();
                }, "AntiAddiction-Fetch");
                t.setDaemon(true);
                t.start();
            }
        } catch (Exception e) {
            AntiAddictionMod.LOGGER.warn("[防沉迷] 加载配置失败: {}", e.getMessage());
        }
    }

    public static boolean isConfigured() {
        return backendUrl != null && !backendUrl.isEmpty();
    }

    private static void fetchUsers() {
        if (!isConfigured()) return;
        try {
            String json = get(backendUrl + "/api/users");
            JsonArray arr = JsonParser.parseString(json).getAsJsonArray();
            Map<String, String> users = new HashMap<>();
            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                users.put(obj.get("name").getAsString(), obj.get("id_card").getAsString());
            }
            DemoDatabase.updateUsers(users);
            AntiAddictionMod.LOGGER.info("[防沉迷] 已从后端更新 {} 个用户", users.size());
        } catch (Exception e) {
            AntiAddictionMod.LOGGER.warn("[防沉迷] 无法获取用户: {}", e.getMessage());
        }
    }

    private static void fetchRules() {
        if (!isConfigured()) return;
        try {
            String json = get(backendUrl + "/api/rules");
            JsonObject resp = JsonParser.parseString(json).getAsJsonObject();
            int defStart = resp.get("default_start_hour").getAsInt();
            int defEnd   = resp.get("default_end_hour").getAsInt();
            PlayTimeChecker.updateDefaultHours(defStart, defEnd);

            JsonArray arr = resp.getAsJsonArray("days");
            Map<String, int[]> gameDays = new HashMap<>();
            for (JsonElement el : arr) {
                JsonObject gd = el.getAsJsonObject();
                String date = gd.get("date").getAsString();
                int playable = gd.get("playable").getAsInt();
                int sh = gd.get("start_hour").getAsInt();
                int eh = gd.get("end_hour").getAsInt();
                int mm = gd.has("max_minutes") ? gd.get("max_minutes").getAsInt() : 0;
                int wo = gd.has("is_workday_override") ? gd.get("is_workday_override").getAsInt() : 0;
                gameDays.put(date, new int[]{playable, sh, eh, mm, wo});
            }
            PlayTimeChecker.updateGameDays(gameDays);
            AntiAddictionMod.LOGGER.info("[防沉迷] 已更新默认时段 {}:00-{}:00，{} 天例外数据", defStart, defEnd, gameDays.size());
        } catch (Exception e) {
            AntiAddictionMod.LOGGER.warn("[防沉迷] 无法获取规则: {}", e.getMessage());
        }
    }

    public static void refreshRules() {
        if (!isConfigured()) return;
        Thread t = new Thread(() -> {
            fetchRules();
            fetchUsers();
        }, "AntiAddiction-Refresh");
        t.setDaemon(true);
        t.start();
    }

    private static void startPeriodicSync() {
        Thread t = new Thread(() -> {
            while (isConfigured()) {
                try { Thread.sleep(30_000); }
                catch (InterruptedException e) { break; }
                fetchRules();
            }
        }, "AntiAddiction-Periodic");
        t.setDaemon(true);
        t.start();
    }

    /**
     * 向后端上报玩家开始游玩（用于日志统计，可选）
     */
    public static void reportSessionStart(String userName, boolean minor) {
        if (!isConfigured()) { LogCache.save(userName, minor, "session_start"); return; }
        Thread t = new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("userName", userName);
                body.addProperty("minor", minor);
                body.addProperty("action", "session_start");
                post(backendUrl + "/api/report", body.toString());
            } catch (Exception ignored) {}
        }, "AntiAddiction-Report");
        t.setDaemon(true);
        t.start();
    }

    /** 客户端启动时自动上报（如果已有本地玩家数据） */
    public static void reportGameStart() {
        com.antiaddiction.data.PlayerDataManager pdm = com.antiaddiction.data.PlayerDataManager.getInstance();
        if (!isConfigured()) {
            String name = pdm.getUserName();
            if (name != null && !name.isEmpty()) LogCache.save(name, pdm.isMinor(), "game_start");
            return;
        }
        String name = pdm.getUserName();
        if (name == null || name.isEmpty()) return;
        boolean minor = pdm.isMinor();
        Thread t = new Thread(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("userName", name);
                body.addProperty("minor", minor);
                body.addProperty("action", "game_start");
                post(backendUrl + "/api/report", body.toString());
            } catch (Exception ignored) {}
        }, "AntiAddiction-StartupReport");
        t.setDaemon(true);
        t.start();
        AntiAddictionMod.LOGGER.info("[防沉迷] 已上报游戏启动: {} ({})", name, minor ? "未成年" : "成年");
    }

    // ---- HTTP 工具方法 ----

    private static String get(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("Accept", "application/json");
        try (InputStream is = conn.getInputStream();
             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    private static String post(String urlStr, String jsonBody) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
        }
        try (InputStream is = conn.getInputStream();
             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }
}
