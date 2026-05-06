package com.antiaddiction;

import com.antiaddiction.data.PlayerDataManager;
import com.antiaddiction.network.ApiClient;
import com.antiaddiction.time.RuntimeChecker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class AntiAddictionClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        AntiAddictionMod.LOGGER.info("[防沉迷] 客户端模块已初始化");
        PlayerDataManager.getInstance();
        ApiClient.loadConfigAndFetchHolidays();
        ApiClient.reportGameStart();
        RuntimeChecker.init();
    }
}
