package com.antiaddiction.time;

import com.antiaddiction.data.PlayerDataManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = "antiaddiction", value = Dist.CLIENT)
public class RuntimeChecker {

    private static boolean kickPending;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        if (client.screen instanceof KickScreen) return;
        if (!PlayerDataManager.getInstance().isMinor()) return;

        if (!PlayTimeChecker.isPlayAllowed()) {
            kickPending = true;
            client.execute(() -> client.setScreen(new KickScreen()));
        }
    }

    public static class KickScreen extends Screen {
        private int countdown = 30;
        private boolean saved;

        protected KickScreen() {
            super(Component.literal("防沉迷 - 时间到"));
        }

        @Override
        protected void init() {
            int cx = this.width / 2;
            int cy = this.height / 2;

            EditBox label = new EditBox(this.font, 0, 0, 1, 1, Component.literal(""));
            label.setVisible(false);
            this.addRenderableWidget(label);

            this.addRenderableWidget(Button.builder(
                    Component.literal("立即退出"),
                    btn -> doKick()
            ).bounds(cx - 50, cy + 20, 100, 20).build());
        }

        @Override
        public void tick() {
            super.tick();
            if (countdown <= 0) return;

            if (!saved) {
                saved = true;
                Minecraft client = Minecraft.getInstance();
                if (client.getSingleplayerServer() != null) {
                    client.getSingleplayerServer().saveEverything(true, true, true);
                }
            }

            countdown--;
            if (countdown <= 0) {
                doKick();
            }
        }

        private void doKick() {
            Minecraft.getInstance().disconnect();
        }

        @Override
        public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
            gfx.fillGradient(0, 0, this.width, this.height, 0xFF0A0000, 0xFF1A0000);

            int cx = this.width / 2;
            int cy = this.height / 2;
            int boxW = 420, boxH = 80;
            int boxX = cx - boxW / 2, boxY = cy - boxH / 2 - 20;

            gfx.fill(boxX - 2, boxY - 2, boxX + boxW + 2, boxY + boxH + 2, 0xFFE25A00);
            gfx.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xCC1E0A00);

            gfx.drawCenteredString(this.font,
                    Component.literal("⚠  游玩时间已结束  ⚠"), cx, boxY + 10, 0xFFFF5555);
            gfx.drawCenteredString(this.font,
                    Component.literal("根据防沉迷规定，当前时间不允许继续游玩"), cx, boxY + 28, 0xFFFFFFFF);
            gfx.drawCenteredString(this.font,
                    Component.literal("已自动保存，游戏将在 " + countdown + " 秒后退出"), cx, boxY + 48, 0xFFFFAA00);

            super.render(gfx, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean shouldCloseOnEsc() { return false; }
    }
}
