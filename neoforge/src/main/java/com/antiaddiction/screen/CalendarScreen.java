package com.antiaddiction.screen;

import com.antiaddiction.network.ApiClient;
import com.antiaddiction.time.PlayTimeChecker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class CalendarScreen extends Screen {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy年M月");

    private YearMonth currentMonth;
    private int panelX, panelY, panelW;
    private int cellW, cellH;
    private int btnRowY, hdrRowY, gridTopY, titleY;
    private static final int COLS = 7;
    private static final int MAX_ROWS = 6;
    private static final int MARGIN = 8;

    private LocalDate selectedDate = null;
    private boolean selectedPlayable, selectedIsOverride;
    private int selectedStartH, selectedEndH, selectedMaxMin;
    private boolean infoExpanded = false;

    private static final int C_BG_TOP    = 0xFF0F172A;
    private static final int C_BG_BOT    = 0xFF1E293B;
    private static final int C_SURFACE   = 0xCC1E293B;
    private static final int C_ACCENT    = 0xFF3B82F6;
    private static final int C_ACCENT_BG = 0x283B82F6;
    private static final int C_GREEN     = 0xFF22C55E;
    private static final int C_RED       = 0xFFEF4444;
    private static final int C_WHITE     = 0xFFFFFFFF;
    private static final int C_MUTED     = 0xFF94A3B8;
    private static final int C_ORANGE    = 0xFFF59E0B;
    private static final int C_BORDER    = 0xFF60A5FA;

    public CalendarScreen() {
        super(Component.literal("游戏日日历"));
        this.currentMonth = YearMonth.now();
    }

    @Override
    protected void init() {
        int btnH = 20;
        int btnGap = 4;
        int bottomBarH = 28;

        int availW = this.width - MARGIN * 2;
        int availH = this.height - MARGIN * 2 - bottomBarH;

        cellH = Math.max(14, Math.min(availH / (MAX_ROWS + 2), availW / (COLS + 2)));
        cellW = cellH;
        panelW = cellW * COLS + 6;
        panelY = MARGIN;

        if (infoExpanded) {
            int infoW = Math.max(120, Math.min(180, this.width - panelW - MARGIN * 3));
            panelX = MARGIN;
            int totalW = panelW + MARGIN + infoW;
            if (totalW > this.width - MARGIN) {
                infoW = this.width - panelW - MARGIN * 2;
                if (infoW < 100) panelX = (this.width - panelW) / 2;
            }
        } else {
            panelX = Math.max(MARGIN, (this.width - panelW) / 2);
        }

        btnRowY = panelY + 4;
        titleY = btnRowY + btnH + 8;
        hdrRowY = titleY + this.font.lineHeight + 6;
        gridTopY = hdrRowY + cellH;

        int btnY = btnRowY;

        this.addRenderableWidget(Button.builder(
                Component.literal("◀"), btn -> navMonth(-1)
        ).bounds(panelX + 4, btnY, 28, btnH).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("▶"), btn -> navMonth(1)
        ).bounds(panelX + panelW - 32, btnY, 28, btnH).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("今天"), btn -> { currentMonth = YearMonth.now(); rebuildWidgets(); }
        ).bounds(panelX + panelW - 78, btnY, 42, btnH).build());

        int backY = Math.min(this.height - 26, panelY + (gridTopY - panelY) + cellH * MAX_ROWS + 16);
        backY = Math.min(backY, this.height - MARGIN);
        int btnRowBottom = backY;
        int totalBtnW = 130;
        int btnStartX = Math.max(MARGIN, (this.width - totalBtnW) / 2);

        this.addRenderableWidget(Button.builder(
                Component.literal("刷新"), btn -> {
                    ApiClient.refreshRules();
                    try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                    rebuildWidgets();
                }
        ).bounds(btnStartX, btnRowBottom, 40, 20).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("返回"), btn -> {
                    if (this.minecraft != null) this.minecraft.setScreen(new TitleScreen(false));
                }
        ).bounds(btnStartX + 50, btnRowBottom, 80, 20).build());
    }

    private void navMonth(int delta) {
        currentMonth = currentMonth.plusMonths(delta);
        rebuildWidgets();
    }

    @Override
    public void renderBackground(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        gfx.fillGradient(0, 0, this.width, this.height, C_BG_TOP, C_BG_BOT);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);

        int panelH = gridTopY - panelY + cellH * MAX_ROWS;

        gfx.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, C_ACCENT);
        gfx.fill(panelX, panelY, panelX + panelW, panelY + panelH, C_SURFACE);

        String title = currentMonth.format(MONTH_FMT);
        int titleW = this.font.width(title);
        int titleX = panelX + panelW - titleW / 2 - 8;
        gfx.drawCenteredString(this.font, Component.literal(title), titleX, titleY, C_WHITE);

        String[] hdr = {"日","一","二","三","四","五","六"};
        for (int c = 0; c < COLS; c++) {
            int x = panelX + 3 + c * cellW + cellW / 2;
            int color = (c == 0 || c == 6) ? 0xFF60A5FA : C_MUTED;
            gfx.drawCenteredString(this.font, Component.literal(hdr[c]), x, hdrRowY + 2, color);
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
                boolean isSelected = date.equals(selectedDate);

                int[] v = gd.get(ds);
                boolean showGreen = false, showRed = false, showOrange = false;
                if (v != null) {
                    if (v.length > 4 && v[4] == 1) showRed = true;
                    else if (v[0] == 1) {
                        if (v[3] > 0) showOrange = true;
                        else showGreen = true;
                    }
                } else {
                    int dow = date.getDayOfWeek().getValue();
                    if (dow >= 5) showGreen = true;
                }

                boolean hovered = mouseX >= x && mouseX < x + cellW && mouseY >= y && mouseY < y + cellH;

                if (isSelected) {
                    gfx.fill(x, y, x + cellW, y + cellH, 0x303B82F6);
                    gfx.fill(x, y, x + cellW, y + 1, C_BORDER);
                    gfx.fill(x, y + cellH - 1, x + cellW, y + cellH, C_BORDER);
                    gfx.fill(x, y, x + 1, y + cellH, C_BORDER);
                    gfx.fill(x + cellW - 1, y, x + cellW, y + cellH, C_BORDER);
                } else if (isToday) {
                    gfx.fill(x + 1, y + 1, x + cellW - 1, y + cellH - 1, C_ACCENT_BG);
                }
                if (hovered && !isSelected) {
                    gfx.fill(x + 1, y + 1, x + cellW - 1, y + cellH - 1, 0x28FFFFFF);
                }

                int numC = (isToday || isSelected) ? C_ACCENT : C_WHITE;
                int numY = y + Math.max(2, cellH / 2 - this.font.lineHeight - 1);
                gfx.drawCenteredString(this.font,
                        Component.literal(String.valueOf(day)), x + cellW / 2, numY, numC);

                if (showGreen || showRed || showOrange) {
                    int dotS = Math.max(3, Math.min(6, cellW / 9));
                    int dotX = x + cellW / 2 - dotS / 2;
                    int dotY = y + cellH - dotS - 3;
                    int dotC = showRed ? C_RED : (showOrange ? C_ORANGE : C_GREEN);
                    gfx.fill(dotX, dotY, dotX + dotS, dotY + dotS, dotC);
                }
            }
        }

        int legX = panelX + 4;
        int legY = titleY - 4;
        int dotSize = 5;
        int dotTextGap = 3;
        int lineH = this.font.lineHeight;

        int dotY1 = legY + lineH / 2 - dotSize / 2;
        gfx.fill(legX, dotY1, legX + dotSize, dotY1 + dotSize, C_GREEN);
        String t1 = "可玩日";
        int c1 = legX + dotSize + dotTextGap + this.font.width(t1) / 2;
        gfx.drawCenteredString(this.font, Component.literal(t1), c1, legY, C_MUTED);

        int legY2 = legY + lineH + 3;
        int dotY2 = legY2 + lineH / 2 - dotSize / 2;
        gfx.fill(legX, dotY2, legX + dotSize, dotY2 + dotSize, C_ORANGE);
        String t2 = "限时可玩日";
        int c2 = legX + dotSize + dotTextGap + this.font.width(t2) / 2;
        gfx.drawCenteredString(this.font, Component.literal(t2), c2, legY2, C_MUTED);

        int legY3 = legY2 + lineH + 3;
        int dotY3 = legY3 + lineH / 2 - dotSize / 2;
        gfx.fill(legX, dotY3, legX + dotSize, dotY3 + dotSize, C_RED);
        String t3 = "调休非玩日";
        int c3 = legX + dotSize + dotTextGap + this.font.width(t3) / 2;
        gfx.drawCenteredString(this.font, Component.literal(t3), c3, legY3, C_MUTED);

        if (infoExpanded && selectedDate != null) {
            int ipX = panelX + panelW + MARGIN;
            int ipW = Math.max(110, this.width - ipX - MARGIN);
            ipW = Math.min(ipW, 180);
            if (ipX + ipW > this.width - MARGIN) ipW = this.width - ipX - MARGIN;
            if (ipW < 100) { ipX = MARGIN; ipW = Math.min(180, this.width - MARGIN * 2); }

            int lineCount = 2;
            if (selectedPlayable) lineCount++;
            if (selectedMaxMin > 0) lineCount++;
            int ipH = 8 + lineCount * 13 + 4;
            int ipY = panelY + 16;
            if (ipY + ipH > this.height - MARGIN) ipY = this.height - ipH - MARGIN;

            gfx.fill(ipX - 1, ipY - 1, ipX + ipW + 1, ipY + ipH + 1, C_ACCENT);
            gfx.fill(ipX, ipY, ipX + ipW, ipY + ipH, C_SURFACE);
            int tx = ipX + 6;
            int ty = ipY + 4;

            String dateStr = selectedDate.format(DateTimeFormatter.ofPattern("M月d日 EEEE", java.util.Locale.CHINESE));
            int dw = this.font.width(dateStr);
            gfx.drawCenteredString(this.font, Component.literal(dateStr), tx + dw / 2, ty, C_WHITE);

            String statusLine;
            int statusColor;
            if (selectedIsOverride) {
                statusLine = "调休非玩日";
                statusColor = C_RED;
            } else if (selectedPlayable) {
                statusLine = "可玩日";
                statusColor = C_GREEN;
            } else {
                statusLine = "非玩日";
                statusColor = C_MUTED;
            }
            ty += 13;
            int sw = this.font.width(statusLine);
            gfx.drawCenteredString(this.font, Component.literal(statusLine), tx + sw / 2, ty, statusColor);

            if (selectedPlayable) {
                ty += 13;
                String timeStr = String.format("%02d:00 - %02d:00", selectedStartH, selectedEndH);
                int tw = this.font.width(timeStr);
                gfx.drawCenteredString(this.font, Component.literal(timeStr), tx + tw / 2, ty, C_WHITE);
            }
            if (selectedMaxMin > 0) {
                ty += 13;
                String limitStr = "限时 " + selectedMaxMin + " 分钟";
                int lw = this.font.width(limitStr);
                gfx.drawCenteredString(this.font, Component.literal(limitStr), tx + lw / 2, ty, C_ORANGE);
            }
        }
    }

    @Override public boolean shouldCloseOnEsc() { return false; }

    private void updateSelectedInfo(LocalDate date) {
        Map<String, int[]> gd = PlayTimeChecker.getGameDays();
        String ds = date.toString();
        int[] v = gd.get(ds);
        if (v != null) {
            selectedIsOverride = v.length > 4 && v[4] == 1;
            selectedPlayable = v[0] == 1 && !selectedIsOverride;
            selectedStartH = v[1];
            selectedEndH = v[2];
            selectedMaxMin = v[3];
        } else {
            selectedIsOverride = false;
            int dow = date.getDayOfWeek().getValue();
            selectedPlayable = dow >= 5;
            selectedStartH = PlayTimeChecker.getDefaultStartHour();
            selectedEndH = PlayTimeChecker.getDefaultEndHour();
            selectedMaxMin = 0;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        LocalDate first = currentMonth.atDay(1);
        int startDow = first.getDayOfWeek().getValue() % 7;
        int days = currentMonth.lengthOfMonth();

        for (int row = 0; row < MAX_ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                int idx = row * COLS + col - startDow;
                if (idx < 0 || idx >= days) continue;
                int x = panelX + 3 + col * cellW;
                int y = gridTopY + row * cellH;
                if (mouseX >= x && mouseX < x + cellW && mouseY >= y && mouseY < y + cellH) {
                    selectedDate = currentMonth.atDay(idx + 1);
                    updateSelectedInfo(selectedDate);
                    infoExpanded = true;
                    rebuildWidgets();
                    return true;
                }
            }
        }
        selectedDate = null;
        infoExpanded = false;
        rebuildWidgets();
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
