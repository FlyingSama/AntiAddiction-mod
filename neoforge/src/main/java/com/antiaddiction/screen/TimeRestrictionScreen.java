package com.antiaddiction.screen;

import com.antiaddiction.data.PlayerDataManager;
import com.antiaddiction.network.ApiClient;
import com.antiaddiction.time.PlayTimeChecker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 未成年人防沉迷时间限制界面（NeoForge / Mojang 映射版）
 */
public class TimeRestrictionScreen extends Screen {

    private static final ZoneId     CHINA_TZ = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.CHINESE);

    private long lastCheckMs = 0L;

    private static final int COLOR_BG_TOP    = 0xFF120A00;
    private static final int COLOR_BG_BOTTOM = 0xFF2A1000;
    private static final int COLOR_ACCENT    = 0xFFE25A00;

    private String line1, line2, line3, line4;
    private int line1cx, line2cx, line3cx, line4cx;
    private int btnY, btnH;
    private int boxX, boxY, boxW, boxH;
    private int startX, lineH, accentY;

    public TimeRestrictionScreen() {
        super(Component.literal("防沉迷 - 时间限制"));
    }

    @Override
    protected void init() {
        int cx = this.width  / 2;
        int cy = this.height / 2;

        String userName  = PlayerDataManager.getInstance().getUserName();
        String currentTs = LocalDateTime.now(CHINA_TZ).format(TIME_FMT);
        String nextTime  = PlayTimeChecker.getNextAllowedTime();

        float s = Math.min(1.0f, this.height / 280.0f);
        boxH = (int)(100 * s);
        boxW = Math.min(410, this.width - 40);
        boxY = cy - boxH / 2 - 15;
        boxX = cx - boxW / 2;
        startX = cx - boxW / 2 + 10;
        lineH  = Math.max(14, boxH / 5);
        accentY = boxY + (int)(lineH * 2.7f);

        line1 = "⚠  防沉迷系统  ⚠";
        line2 = userName + "，当前时间不在允许的游玩时段内";
        line3 = "当前时间 " + currentTs;
        line4 = "下次可游玩 " + nextTime;

        line1cx = startX + this.font.width(line1) / 2;
        line2cx = startX + this.font.width(line2) / 2;
        line3cx = startX + this.font.width(line3) / 2;
        line4cx = startX + this.font.width(line4) / 2;

        btnY = cy + boxH / 2 + 10;
        btnH = Math.max(16, (int)(20 * s));

        this.addRenderableWidget(Button.builder(
                Component.literal("退出"),
                btn -> { if (this.minecraft != null) this.minecraft.stop(); }
        ).bounds(cx - 110, btnY, 60, btnH).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("日历"),
                btn -> { if (this.minecraft != null) this.minecraft.setScreen(new CalendarScreen()); }
        ).bounds(cx - 30, btnY, 60, btnH).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("刷新"),
                btn -> checkAndProceed()
        ).bounds(cx + 50, btnY, 60, btnH).build());
    }

    @Override
    public void renderBackground(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        gfx.fillGradient(0, 0, this.width, this.height, COLOR_BG_TOP, COLOR_BG_BOTTOM);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        gfx.fill(boxX - 2, boxY - 2, boxX + boxW + 2, boxY + boxH + 2, COLOR_ACCENT);
        gfx.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xCC1E0A00);
        gfx.fill(boxX + 10, accentY, boxX + boxW - 10, accentY + 1, COLOR_ACCENT);

        gfx.drawCenteredString(this.font, Component.literal(line1), line1cx, boxY + lineH, 0xFF5555);
        gfx.drawCenteredString(this.font, Component.literal(line2), line2cx, boxY + lineH * 2, 0xFFAA00);
        gfx.drawCenteredString(this.font, Component.literal(line3), line3cx, boxY + lineH * 3 + 2, 0xAAAAAA);
        gfx.drawCenteredString(this.font, Component.literal(line4), line4cx, boxY + lineH * 4 + 2, 0x55FF55);

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    public void tick() {
        super.tick();
        long now = System.currentTimeMillis();
        if (now - lastCheckMs > 30_000L) {
            lastCheckMs = now;
            checkAndProceed();
        }
    }

    private void checkAndProceed() {
        ApiClient.refreshRules();
        if (PlayTimeChecker.isPlayAllowed() && this.minecraft != null) {
            PlayTimeChecker.markSessionStart();
            this.minecraft.setScreen(new TitleScreen(false));
        }
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }
    @Override
    public void onClose() { }
}
