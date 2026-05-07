package com.antiaddiction;

import com.antiaddiction.data.PlayerDataManager;

import com.antiaddiction.screen.TimeRestrictionScreen;
import com.antiaddiction.screen.VerificationScreen;
import com.antiaddiction.time.PlayTimeChecker;
import com.antiaddiction.time.RuntimeChecker;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * NeoForge 客户端事件处理
 *
 * NeoForge 提供了 ScreenEvent.Opening 事件，
 * 可以在屏幕打开前替换目标屏幕，不需要 Mixin。
 */
@EventBusSubscriber(modid = AntiAddictionMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClientEventHandler {

    /**
     * 拦截 TitleScreen 的打开事件，进行防沉迷检查
     */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!(event.getScreen() instanceof TitleScreen)) return;

        PlayerDataManager mgr = PlayerDataManager.getInstance();

        if (!mgr.isVerified()) {
            event.setNewScreen(new VerificationScreen());
        } else if (mgr.isMinor() && !PlayTimeChecker.isPlayAllowed()) {
            event.setNewScreen(new TimeRestrictionScreen());
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        RuntimeChecker.renderCountdownHud(event.getGuiGraphics());
    }
}
