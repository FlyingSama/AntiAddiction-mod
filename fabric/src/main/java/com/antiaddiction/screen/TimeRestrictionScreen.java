package com.antiaddiction.screen;

import com.antiaddiction.AntiAddictionMod;
import com.antiaddiction.data.PlayerDataManager;
import com.antiaddiction.network.ApiClient;
import com.antiaddiction.time.PlayTimeChecker;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class TimeRestrictionScreen extends Screen {

    private static final ZoneId CHINA_TZ = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.CHINESE);

    private long lastCheckMs = 0L;
    private double savedBgmVolume = 1.0;
    private static final Identifier BGM_ON  = Identifier.of("antiaddiction", "textures/gui/sound_on");
    private static final Identifier BGM_OFF = Identifier.of("antiaddiction", "textures/gui/sound_off");

    private static final int COLOR_BG_TOP    = 0xFF120A00;
    private static final int COLOR_BG_BOTTOM = 0xFF2A1000;
    private static final int COLOR_ACCENT    = 0xFFE25A00;

    private String line1, line2, line3, line4;
    private int line1cx, line2cx, line3cx, line4cx;
    private int btnY, btnH;
    private int boxX, boxY, boxW, boxH;
    private int startX, lineH, accentY;

    public TimeRestrictionScreen() {
        super(Text.literal("防沉迷 - 时间限制"));
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

        line1cx = startX + this.textRenderer.getWidth(line1) / 2;
        line2cx = startX + this.textRenderer.getWidth(line2) / 2;
        line3cx = startX + this.textRenderer.getWidth(line3) / 2;
        line4cx = startX + this.textRenderer.getWidth(line4) / 2;

        btnY = cy + boxH / 2 + 10;
        btnH = Math.max(16, (int)(20 * s));

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("退出"),
                btn -> { assert this.client != null; this.client.stop(); }
        ).dimensions(cx - 110, btnY, 60, btnH).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("日历"),
                btn -> openCalendar()
        ).dimensions(cx - 30, btnY, 60, btnH).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("刷新"),
                btn -> checkAndProceed()
        ).dimensions(cx + 50, btnY, 60, btnH).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, this.width, this.height, COLOR_BG_TOP, COLOR_BG_BOTTOM);

        boolean bgmOn = this.client == null ||
                this.client.options.getSoundVolumeOption(SoundCategory.MUSIC).getValue() > 0;
        int bgmX = this.width - 28;
        ctx.drawTexturedQuad(bgmOn ? BGM_ON : BGM_OFF, bgmX, 9, bgmX + 20, 28, 0f, 0f, 1f, 1f);
        ctx.fill(boxX - 2, boxY - 2, boxX + boxW + 2, boxY + boxH + 2, COLOR_ACCENT);
        ctx.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xCC1E0A00);
        ctx.fill(boxX + 10, accentY, boxX + boxW - 10, accentY + 1, COLOR_ACCENT);

        ctx.drawCenteredTextWithShadow(this.textRenderer, line1, line1cx, boxY + lineH, 0xFFFF5555);
        ctx.drawCenteredTextWithShadow(this.textRenderer, line2, line2cx, boxY + lineH * 2, 0xFFFFAA00);
        ctx.drawCenteredTextWithShadow(this.textRenderer, line3, line3cx, boxY + lineH * 3 + 2, 0xFFAAAAAA);
        ctx.drawCenteredTextWithShadow(this.textRenderer, line4, line4cx, boxY + lineH * 4 + 2, 0xFF55FF55);

        super.render(ctx, mouseX, mouseY, delta);
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

    private void openCalendar() {
        try {
            Class<?> calClass = Class.forName("com.antiaddiction.screen.CalendarScreen");
            Screen cal = (Screen) calClass.getDeclaredConstructor().newInstance();
            assert this.client != null;
            this.client.setScreen(cal);
        } catch (Exception e) {
            AntiAddictionMod.LOGGER.warn("[防沉迷] 无法打开日历: {}", e.getMessage());
        }
    }

    private void checkAndProceed() {
        ApiClient.refreshRules();
        if (PlayTimeChecker.isPlayAllowed()) {
            PlayTimeChecker.markSessionStart();
            assert this.client != null;
            this.client.setScreen(new TitleScreen(false));
        } else {
            this.clearAndInit();
        }
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public void close() { }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (doubled || click.button() != 0) return super.mouseClicked(click, doubled);
        int bgmX = this.width - 28;
        if (click.x() >= bgmX && click.x() < bgmX + 20 && click.y() >= 8 && click.y() < 28) {
            if (this.client == null) return true;
            var opt = this.client.options.getSoundVolumeOption(SoundCategory.MUSIC);
            if (opt.getValue() > 0) { savedBgmVolume = opt.getValue(); opt.setValue(0.0); }
            else { opt.setValue(savedBgmVolume > 0 ? savedBgmVolume : 1.0); }
            return true;
        }
        return super.mouseClicked(click, doubled);
    }
}
