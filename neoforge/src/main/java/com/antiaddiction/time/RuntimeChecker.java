package com.antiaddiction.time;

import com.antiaddiction.data.PlayerDataManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = "antiaddiction", value = Dist.CLIENT)
public class RuntimeChecker {

    public static int countdownSeconds = -1;
    private static long lastCountdownTick = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        if (!PlayerDataManager.getInstance().isMinor()) return;
        if (client.screen instanceof com.antiaddiction.screen.VerificationScreen) return;
        if (client.screen instanceof com.antiaddiction.screen.TimeRestrictionScreen) return;

        long remaining = PlayTimeChecker.getRemainingSecondsForCountdown();

        if (remaining <= 0) {
            countdownSeconds = -1;
            if (client.getSingleplayerServer() != null) {
                client.getSingleplayerServer().saveEverything(true, true, true);
            }
            client.execute(() -> client.setScreen(
                new com.antiaddiction.screen.TimeRestrictionScreen()));
        } else if (remaining <= 60) {
            countdownSeconds = (int) remaining;
            lastCountdownTick = System.currentTimeMillis();
        }
    }

    public static void renderCountdownHud(GuiGraphics gfx) {
        if (countdownSeconds <= 0) return;

        long now = System.currentTimeMillis();
        if (now - lastCountdownTick >= 1000) {
            countdownSeconds--;
            lastCountdownTick = now;
        }

        if (countdownSeconds <= 0) {
            Minecraft client = Minecraft.getInstance();
            if (client.getSingleplayerServer() != null) {
                client.getSingleplayerServer().saveEverything(true, true, true);
            }
            client.execute(() -> client.setScreen(
                new com.antiaddiction.screen.TimeRestrictionScreen()));
            return;
        }

        Minecraft client = Minecraft.getInstance();
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();

        String text = "游玩时间剩余 " + (countdownSeconds / 60) + ":" +
                String.format("%02d", countdownSeconds % 60);
        int textWidth = client.font.width(text);
        int x = (width - textWidth) / 2;
        int y = height - 68;

        gfx.fill(x - 8, y - 2, x + textWidth + 8, y + client.font.lineHeight + 2, 0xCC000000);
        gfx.drawCenteredString(client.font, text, width / 2, y, 0xFFFFAA00);
    }
}
