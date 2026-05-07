package com.antiaddiction.time;

import com.antiaddiction.data.PlayerDataManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.server.integrated.IntegratedServer;

public class RuntimeChecker {

    private static boolean registered;
    public static int countdownSeconds = -1;
    private static long lastCountdownTick = 0;

    public static void init() {
        if (registered) return;
        registered = true;

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            if (!PlayerDataManager.getInstance().isMinor()) return;
            if (client.currentScreen instanceof com.antiaddiction.screen.VerificationScreen) return;
            if (client.currentScreen instanceof com.antiaddiction.screen.TimeRestrictionScreen) return;

            long remaining = PlayTimeChecker.getRemainingSecondsForCountdown();

            if (remaining <= 0) {
                countdownSeconds = -1;
                IntegratedServer server = client.getServer();
                if (server != null) {
                    server.saveAll(true, true, true);
                }
                client.execute(() -> client.setScreen(
                    new com.antiaddiction.screen.TimeRestrictionScreen()));
            } else if (remaining <= 60) {
                countdownSeconds = (int) remaining;
                lastCountdownTick = System.currentTimeMillis();
            }
        });
    }

    public static void renderCountdownHud(DrawContext ctx) {
        if (countdownSeconds <= 0) return;

        long now = System.currentTimeMillis();
        if (now - lastCountdownTick >= 1000) {
            countdownSeconds--;
            lastCountdownTick = now;
        }

        if (countdownSeconds <= 0) {
            MinecraftClient client = MinecraftClient.getInstance();
            IntegratedServer server = client.getServer();
            if (server != null) {
                server.saveAll(true, true, true);
            }
            client.execute(() -> client.setScreen(
                new com.antiaddiction.screen.TimeRestrictionScreen()));
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();

        String text = "游玩时间剩余 " + (countdownSeconds / 60) + ":" +
                String.format("%02d", countdownSeconds % 60);
        int textWidth = client.textRenderer.getWidth(text);
        int x = (width - textWidth) / 2;
        int y = height - 68;

        ctx.fill(x - 8, y - 2, x + textWidth + 8, y + client.textRenderer.fontHeight + 2, 0xCC000000);
        ctx.drawCenteredTextWithShadow(client.textRenderer, text, width / 2, y, 0xFFFFAA00);
    }
}
