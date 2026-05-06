package com.antiaddiction;

import com.antiaddiction.data.PlayerDataManager;
import com.antiaddiction.network.ApiClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * FML 生命周期事件：客户端初始化
 * 在这里完成本地数据预加载和后端配置读取。
 */
@EventBusSubscriber(modid = AntiAddictionMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetupHandler {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        AntiAddictionMod.LOGGER.info("[防沉迷] 客户端初始化...");
        // 预加载本地玩家数据
        PlayerDataManager.getInstance();
        // 读取 antiaddiction.properties 并拉取节假日
        ApiClient.loadConfigAndFetchHolidays();
        // 每次启动游戏时上报一条登录记录
        ApiClient.reportGameStart();
    }
}
