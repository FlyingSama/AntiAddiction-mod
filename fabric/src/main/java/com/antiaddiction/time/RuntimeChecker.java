package com.antiaddiction.time;

import com.antiaddiction.data.PlayerDataManager;
import com.antiaddiction.network.ApiClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.text.Text;

public class RuntimeChecker {

    private static boolean registered;

    public static void init() {
        if (registered) return;
        registered = true;

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            if (client.currentScreen instanceof KickScreen) return;
            if (!PlayerDataManager.getInstance().isMinor()) return;

            if (!PlayTimeChecker.isPlayAllowed()) {
                client.execute(() -> client.setScreen(new KickScreen()));
            }
        });
    }

    public static class KickScreen extends Screen {
        private int countdown = 30;
        private boolean saved;

        protected KickScreen() {
            super(Text.literal("防沉迷 - 时间到"));
        }

        @Override
        protected void init() {
            int cx = this.width / 2;
            this.addDrawableChild(ButtonWidget.builder(
                    Text.literal("立即退出"),
                    btn -> doKick()
            ).dimensions(cx - 50, this.height / 2 + 20, 100, 22).build());
        }

        @Override
        public void tick() {
            super.tick();
            if (countdown <= 0) return;

            if (!saved) {
                saved = true;
                MinecraftClient client = MinecraftClient.getInstance();
                IntegratedServer server = client.getServer();
                if (server != null) {
                    server.saveAll(true, true, true);
                }
            }

            countdown--;
            if (countdown <= 0) {
                doKick();
            }
        }

        private void doKick() {
            MinecraftClient client = MinecraftClient.getInstance();
            client.disconnect(Text.literal("防沉迷游玩时间已结束"));
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            ctx.fillGradient(0, 0, this.width, this.height, 0xFF0A0000, 0xFF1A0000);

            int cx = this.width / 2;
            int cy = this.height / 2;
            int boxW = Math.min(420, this.width - 20);
            int boxX = cx - boxW / 2;

            ctx.fill(boxX - 2, cy - 42, boxX + boxW + 2, cy + 42, 0xFFE25A00);
            ctx.fill(boxX, cy - 40, boxX + boxW, cy + 40, 0xCC1E0A00);

            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("⚠  游玩时间已结束  ⚠"), cx, cy - 22, 0xFFFF5555);
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("根据防沉迷规定，当前时间不允许继续游玩"), cx, cy - 4, 0xFFFFFFFF);
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("已自动保存，" + countdown + " 秒后退出"), cx, cy + 14, 0xFFFFAA00);

            super.render(ctx, mouseX, mouseY, delta);
        }

        @Override
        public boolean shouldCloseOnEsc() { return false; }
    }
}
