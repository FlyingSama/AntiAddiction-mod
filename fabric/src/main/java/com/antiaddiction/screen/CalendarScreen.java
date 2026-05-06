package com.antiaddiction.screen;

import com.antiaddiction.time.PlayTimeChecker;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class CalendarScreen extends Screen {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy年M月");

    private YearMonth currentMonth;
    private int panelX, panelY, panelW;
    private int cellW, cellH;
    private int btnRowY, hdrRowY, gridTopY;
    private static final int COLS = 7;
    private static final int MAX_ROWS = 6;

    private static final int C_BG_TOP    = 0xFF0F172A;
    private static final int C_BG_BOT    = 0xFF1E293B;
    private static final int C_SURFACE   = 0xCC1E293B;
    private static final int C_ACCENT    = 0xFF3B82F6;
    private static final int C_ACCENT_BG = 0x283B82F6;
    private static final int C_GREEN     = 0xFF22C55E;
    private static final int C_RED       = 0xFFEF4444;
    private static final int C_WHITE     = 0xFFFFFFFF;
    private static final int C_MUTED     = 0xFF94A3B8;

    public CalendarScreen() {
        super(Text.literal("游戏日日历"));
        this.currentMonth = YearMonth.now();
    }

    @Override
    protected void init() {
        int margin = 8;
        int btnH = 20;
        int btnGap = 4;

        int availW = this.width - margin * 2;
        int availH = this.height - margin * 2 - 40;

        cellH = Math.max(14, Math.min(availH / (MAX_ROWS + 2), availW / (COLS + 2)));
        cellW = cellH;
        panelW = cellW * COLS + 6;
        panelX = (this.width - panelW) / 2;
        panelY = margin;

        btnRowY = panelY + 4;
        hdrRowY = btnRowY + btnH + btnGap;
        gridTopY = hdrRowY + cellH;

        int btnY = btnRowY;
        int titleX1 = panelX + 36;
        int titleX2 = panelX + panelW - 36;

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("◀"), btn -> navMonth(-1)
        ).dimensions(panelX + 4, btnY, 28, btnH).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("▶"), btn -> navMonth(1)
        ).dimensions(panelX + panelW - 32, btnY, 28, btnH).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("今天"), btn -> { currentMonth = YearMonth.now(); clearAndInit(); }
        ).dimensions(panelX + panelW - 78, btnY, 42, btnH).build());

        int backY = this.height - 26;
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("返回"), btn -> {
                    if (this.client != null) this.client.setScreen(new TitleScreen(false));
                }
        ).dimensions((this.width - 80) / 2, backY, 80, 20).build());
    }

    private void navMonth(int delta) {
        currentMonth = currentMonth.plusMonths(delta);
        clearAndInit();
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fillGradient(0, 0, this.width, this.height, C_BG_TOP, C_BG_BOT);

        int panelH = gridTopY - panelY + cellH * MAX_ROWS;

        ctx.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, C_ACCENT);
        ctx.fill(panelX, panelY, panelX + panelW, panelY + panelH, C_SURFACE);

        ctx.drawCenteredTextWithShadow(this.textRenderer,
                currentMonth.format(MONTH_FMT), panelX + panelW / 2, btnRowY + 4, C_WHITE);

        String[] hdr = {"日","一","二","三","四","五","六"};
        for (int c = 0; c < COLS; c++) {
            int x = panelX + 3 + c * cellW + cellW / 2;
            int color = (c == 0 || c == 6) ? 0xFF60A5FA : C_MUTED;
            ctx.drawCenteredTextWithShadow(this.textRenderer, hdr[c], x, hdrRowY + 2, color);
        }

        LocalDate today = LocalDate.now();
        LocalDate first = currentMonth.atDay(1);
        int startDow = first.getDayOfWeek().getValue() % 7;
        int days = currentMonth.lengthOfMonth();
        Map<String, int[]> gd = PlayTimeChecker.getGameDays();

        for (int row = 0; row < MAX_ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int idx = row * COLS + col - startDow;
                if (idx < 0 || idx >= days) continue;
                int day = idx + 1;
                int x = panelX + 3 + col * cellW;
                int y = gridTopY + row * cellH;
                LocalDate date = currentMonth.atDay(day);
                String ds = date.toString();
                boolean isToday = date.equals(today);

                int[] v = gd.get(ds);
                boolean showGreen = false, showRed = false;
                if (v != null) {
                    if (v.length > 4 && v[4] == 1) showRed = true;
                    else if (v[0] == 1) showGreen = true;
                } else {
                    int dow = date.getDayOfWeek().getValue();
                    if (dow >= 5) showGreen = true;
                }

                if (isToday) {
                    ctx.fill(x + 1, y + 1, x + cellW - 1, y + cellH - 1, C_ACCENT_BG);
                }
                if (mouseX >= x && mouseX < x + cellW && mouseY >= y && mouseY < y + cellH) {
                    ctx.fill(x + 1, y + 1, x + cellW - 1, y + cellH - 1, 0x28FFFFFF);
                }

                int numC = isToday ? C_ACCENT : C_WHITE;
                int numY = y + Math.max(2, cellH / 2 - this.textRenderer.fontHeight - 1);
                ctx.drawCenteredTextWithShadow(this.textRenderer,
                        String.valueOf(day), x + cellW / 2, numY, numC);

                if (showGreen || showRed) {
                    int dotS = Math.max(3, Math.min(6, cellW / 9));
                    int dotX = x + cellW / 2 - dotS / 2;
                    int dotY = y + cellH - dotS - 3;
                    ctx.fill(dotX, dotY, dotX + dotS, dotY + dotS, showRed ? C_RED : C_GREEN);
                }
            }
        }

        int hintY = panelY + panelH + 8;
        String hint = String.format("绿色点 = 可玩日（默认 %02d:00-%02d:00）",
                PlayTimeChecker.getDefaultStartHour(), PlayTimeChecker.getDefaultEndHour());
        ctx.drawCenteredTextWithShadow(this.textRenderer, hint, this.width / 2, hintY, C_MUTED);

        int legY = this.height - 14;
        String leg = "● 可玩    ● 调休非玩日";
        ctx.drawCenteredTextWithShadow(this.textRenderer, leg, this.width / 2, legY + 2, C_MUTED);
        int gw = this.textRenderer.getWidth(leg);
        int gx = this.width / 2 - gw / 2;
        ctx.fill(gx + 0, legY + 7, gx + 4, legY + 11, C_GREEN);
        int rx = gx + this.textRenderer.getWidth("● 可玩    ");
        ctx.fill(rx, legY + 7, rx + 4, legY + 11, C_RED);

        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
}
