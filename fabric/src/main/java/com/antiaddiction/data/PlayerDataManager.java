package com.antiaddiction.data;

import com.antiaddiction.AntiAddictionMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * 管理玩家实名认证数据的本地持久化（Fabric 平台版）
 * 数据保存至 .minecraft/antiaddiction_data.json
 */
public class PlayerDataManager {

    private static PlayerDataManager instance;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // --- 持久化字段 ---
    private String  userName  = "";
    private String  idCard    = "";   // 生产环境应改为 SHA-256 哈希
    private int     age       = -1;
    private boolean minor     = false;
    private boolean verified  = false;
    private long    verifiedAt = 0L;

    private PlayerDataManager() {
        load();
    }

    public static PlayerDataManager getInstance() {
        if (instance == null) {
            instance = new PlayerDataManager();
        }
        return instance;
    }

    private Path getDataPath() {
        return FabricLoader.getInstance().getGameDir().resolve("antiaddiction_data.json");
    }

    // ---- 读取 ----
    private void load() {
        File file = getDataPath().toFile();
        if (!file.exists()) return;
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            SaveData saved = GSON.fromJson(reader, SaveData.class);
            if (saved != null && saved.verified) {
                this.userName   = saved.userName;
                this.idCard     = saved.idCard;
                this.age        = saved.age;
                this.minor      = saved.minor;
                this.verified   = true;
                this.verifiedAt = saved.verifiedAt;
                AntiAddictionMod.LOGGER.info("[防沉迷] 已加载本地认证数据，用户: " + userName
                        + (minor ? "（未成年）" : "（成年）"));
            }
        } catch (Exception e) {
            AntiAddictionMod.LOGGER.error("[防沉迷] 读取用户数据失败: " + e.getMessage());
        }
    }

    // ---- 写入 ----
    public void saveUserData(String name, String idCard, int age, boolean minor) {
        this.userName   = name;
        this.idCard     = idCard;
        this.age        = age;
        this.minor      = minor;
        this.verified   = true;
        this.verifiedAt = System.currentTimeMillis();

        SaveData data = new SaveData();
        data.userName   = this.userName;
        data.idCard     = this.idCard;
        data.age        = this.age;
        data.minor      = this.minor;
        data.verified   = this.verified;
        data.verifiedAt = this.verifiedAt;

        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(getDataPath().toFile()), StandardCharsets.UTF_8)) {
            GSON.toJson(data, writer);
            AntiAddictionMod.LOGGER.info("[防沉迷] 认证数据已保存，用户: " + name);
        } catch (Exception e) {
            AntiAddictionMod.LOGGER.error("[防沉迷] 保存用户数据失败: " + e.getMessage());
        }
    }

    /** 清除认证（用于测试/重置） */
    public void clearVerification() {
        this.verified = false;
        this.userName = "";
        this.idCard   = "";
        this.age      = -1;
        this.minor    = false;
        File file = getDataPath().toFile();
        if (file.exists()) file.delete();
    }

    // ---- Getter ----
    public boolean isVerified() { return verified; }
    public boolean isMinor()    { return minor; }
    public String  getUserName(){ return userName; }
    public int     getAge()     { return age; }

    // ---- 序列化用内部类 ----
    private static class SaveData {
        String  userName;
        String  idCard;
        int     age;
        boolean minor;
        boolean verified;
        long    verifiedAt;
    }
}
