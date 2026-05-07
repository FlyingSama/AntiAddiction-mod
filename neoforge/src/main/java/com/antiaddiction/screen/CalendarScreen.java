package com.antiaddiction.screen;

import com.antiaddiction.network.ApiClient;
import com.antiaddiction.time.PlayTimeChecker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class CalendarScreen extends Screen {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy年M月");

    private YearMonth currentMonth;
    private int panelX, panelY, panelW, panelH;
    private int cellW, cellH;
    private int btnRowY, hdrRowY, gridTopY, titleY;
    private static final int COLS = 7;
    private static final int MAX_ROWS = 6;
    private static final int MARGIN = 8;

    private LocalDate selectedDate = null;
    private boolean selectedPlayable, selectedIsOverride;
    private int selectedStartH, selectedEndH, selectedMaxMin;
    private boolean infoExpanded = false;

    private long animStartMs = 0;
    private int panelXBefore, panelXAfter;
    private boolean animating = false;
    private boolean animInitDone = false;
    private double savedBgmVolume = 1.0;
    private static final ResourceLocation BGM_ON  = ResourceLocation.fromNamespaceAndPath("antiaddiction", "textures/gui/sound_on");
    private static final ResourceLocation BGM_OFF = ResourceLocation.fromNamespaceAndPath("antiaddiction", "textures/gui/sound_off");
    private static final long SLIDE_MS = 200;
    private static final long INFO_ANIM_MS = 300;

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
    private static final int C_INFO_BG   = 0xCC1E293B;
    private static final int C_DOT       = 0xFF3B82F6;

    public CalendarScreen() {
        super(Component.literal("游戏日日历"));
        this.currentMonth = YearMonth.now();
    }

    @Override
    protected void init() {
        int btnH = 20;
        int bottomBarH = 40;

        int availW = this.width - MARGIN * 2;
        int availH = this.height - MARGIN * 2 - bottomBarH;

        int legendW = 130;
        int maxPanelW = Math.max(60, availW - legendW);
        cellH = Math.max(14, Math.min(availH / (MAX_ROWS + 2), maxPanelW / (COLS + 2)));
        cellW = cellH;
        panelW = cellW * COLS + 6;
        panelY = MARGIN;

        if (!animating) {
            if (infoExpanded) {
                panelX = MARGIN;
            } else {
                panelX = Math.max(MARGIN, (this.width - panelW) / 2);
            }
        }

        btnRowY = panelY + 4;
        titleY = btnRowY + btnH + 8;
        hdrRowY = titleY + this.font.lineHeight + 6;
        gridTopY = hdrRowY + cellH;
        panelH = gridTopY - panelY + cellH * MAX_ROWS;

        int btnY = btnRowY;

        this.addRenderableWidget(Button.builder(
                Component.literal("◀"), btn -> navMonth(-1)
        ).bounds(panelX + 4, btnY, 28, btnH).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("▶"), btn -> navMonth(1)
        ).bounds(panelX + panelW - 32, btnY, 28, btnH).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("今天"), btn -> { currentMonth = YearMonth.now(); rebuildWidgets(); }
        ).bounds(panelX + panelW / 2 - 21, btnY, 42, btnH).build());

        int backY = Math.min(this.height - 26, panelY + panelH + 12);
        int retW = 100, retH = 24;
        int retX = (this.width - retW) / 2;

        this.addRenderableWidget(Button.builder(
                Component.literal("返回"), btn -> {
                    if (this.minecraft != null) this.minecraft.setScreen(new TitleScreen(false));
                }
        ).bounds(retX, backY, retW, retH).build());

        this.addRenderableWidget(Button.builder(
                Component.literal("⟳"), btn -> {
                    ApiClient.refreshRules();
                    try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                    rebuildWidgets();
                }
        ).bounds(retX + retW + 4, backY + 1, 22, 22).build());

        animInitDone = true;
    }

    private void navMonth(int delta) {
        currentMonth = currentMonth.plusMonths(delta);
        animating = false;
        animStartMs = 0;
        rebuildWidgets();
    }

    private float animProgress(long elapsed, long duration) {
        if (elapsed <= 0) return 0f;
        if (elapsed >= duration) return 1f;
        float t = (float) elapsed / (float) duration;
        return t * t * (3f - 2f * t);
    }

    @Override
    public void renderBackground(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        gfx.fillGradient(0, 0, this.width, this.height, C_BG_TOP, C_BG_BOT);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);

        boolean bgmOn = this.minecraft == null ||
                this.minecraft.options.getSoundSourceOptionInstance(SoundSource.MUSIC).get() > 0;
        ResourceLocation bgmIcon = bgmOn ? BGM_ON : BGM_OFF;
        gfx.blit(bgmIcon, MARGIN, btnRowY + 1, 0, 0, 20, 20, 20, 20);

        int drawPanelX = panelX;

        if (animating) {
            long elapsed = System.currentTimeMillis() - animStartMs;
            float slideT = animProgress(elapsed, SLIDE_MS);
            drawPanelX = panelXBefore + (int)((panelXAfter - panelXBefore) * slideT);
            if (slideT >= 1f && animInitDone) {
                animating = false;
                rebuildWidgets();
                return;
            }
        }

        if (infoExpanded && animating) {
            long elapsed = System.currentTimeMillis() - animStartMs;

            int ipX = drawPanelX + panelW + MARGIN;
            int maxTw = this.font.width(
                    selectedDate.format(DateTimeFormatter.ofPattern("M月d日 EEEE", java.util.Locale.CHINESE)));
            maxTw = Math.max(maxTw, this.font.width("调休非玩日"));
            if (selectedPlayable)
                maxTw = Math.max(maxTw, this.font.width(
                        String.format("%02d:00 - %02d:00", selectedStartH, selectedEndH)));
            int ipW = Math.min(maxTw + 24, this.width - ipX - MARGIN);
            if (ipW < 90) ipW = 90;

            int lineCount = 2;
            if (selectedPlayable) lineCount++;
            if (selectedMaxMin > 0) lineCount++;
            int ipH = 8 + lineCount * 13 + 4;
            int ipY = panelY + (panelH - ipH) / 2;
            if (ipY < MARGIN) ipY = MARGIN;
            if (ipY + ipH > this.height - MARGIN) ipY = this.height - ipH - MARGIN;

            int rightEdgeX = drawPanelX + panelW;
            int midY = panelY + panelH / 2;

            if (elapsed < 60) {
                float t = animProgress(elapsed, 60);
                int r = Math.max(1, (int)(2 * t));
                gfx.fill(rightEdgeX + 4 - r, midY - r, rightEdgeX + 4 + r, midY + r, C_DOT);
            } else if (elapsed < 150) {
                float t = animProgress(elapsed - 60, 90);
                int halfH = Math.max(4, (int)((ipH / 2f) * t));
                gfx.fill(rightEdgeX + 4, midY - halfH, rightEdgeX + 4, midY + halfH, C_DOT);
            } else if (elapsed < 220) {
                int topY = midY - ipH / 2;
                int botY = midY + ipH / 2;
                float t = animProgress(elapsed - 150, 70);
                int rw = Math.max(4, (int)((ipX + ipW - rightEdgeX) * t));
                gfx.fill(rightEdgeX + 4, topY, rightEdgeX + 4 + rw, topY + 2, C_DOT);
                gfx.fill(rightEdgeX + 4, botY - 2, rightEdgeX + 4 + rw, botY, C_DOT);
                gfx.fill(rightEdgeX + 4, topY, rightEdgeX + 4, botY, C_DOT);
            } else {
                int topY = ipY;
                int botY = ipY + ipH;
                float t = animProgress(elapsed - 220, 80);
                float closeT = Math.min(1f, t * 1.5f);
                int curBot = topY + (int)(ipH * closeT);
                gfx.fill(rightEdgeX + 4, topY, ipX + ipW, curBot, C_INFO_BG);
                gfx.fill(rightEdgeX + 4, topY, ipX + ipW, topY + 2, C_DOT);
                gfx.fill(rightEdgeX + 4, topY, rightEdgeX + 4, curBot, C_DOT);
                gfx.fill(ipX + ipW - 2, topY, ipX + ipW, curBot, C_DOT);
                if (closeT >= 1f) {
                    gfx.fill(rightEdgeX + 4, botY - 2, ipX + ipW, botY, C_DOT);
                }

                if (elapsed >= 260) {
                    float textAlpha = animProgress(elapsed - 260, 60);
                    int alpha = (int)(textAlpha * 255);
                    int tx = ipX + 6;
                    int ty = ipY + 4;

                    String dateStr = selectedDate.format(DateTimeFormatter.ofPattern("M月d日 EEEE", java.util.Locale.CHINESE));
                    int dw = this.font.width(dateStr);
                    gfx.drawCenteredString(this.font, Component.literal(dateStr), tx + dw / 2, ty,
                            blendAlpha(C_WHITE, alpha));

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
                    gfx.drawCenteredString(this.font, Component.literal(statusLine), tx + sw / 2, ty,
                            blendAlpha(statusColor, alpha));

                    if (selectedPlayable) {
                        ty += 13;
                        String timeStr = String.format("%02d:00 - %02d:00", selectedStartH, selectedEndH);
                        int tw = this.font.width(timeStr);
                        gfx.drawCenteredString(this.font, Component.literal(timeStr), tx + tw / 2, ty,
                                blendAlpha(C_WHITE, alpha));
                    }
                    if (selectedMaxMin > 0) {
                        ty += 13;
                        String limitStr = "限时 " + selectedMaxMin + " 分钟";
                        int lw2 = this.font.width(limitStr);
                        gfx.drawCenteredString(this.font, Component.literal(limitStr), tx + lw2 / 2, ty,
                                blendAlpha(C_ORANGE, alpha));
                    }
                }
            }

            panelH = gridTopY - panelY + cellH * MAX_ROWS;
            gfx.fill(drawPanelX - 2, panelY - 2, drawPanelX + panelW + 2, panelY + panelH + 2, C_ACCENT);
            gfx.fill(drawPanelX, panelY, drawPanelX + panelW, panelY + panelH, C_SURFACE);

            String title = currentMonth.format(MONTH_FMT);
            int titleW = this.font.width(title);
            int titleX = drawPanelX + panelW - titleW / 2 - 8;
            gfx.drawCenteredString(this.font, Component.literal(title), titleX, titleY, C_WHITE);

            String[] hdr = {"日","一","二","三","四","五","六"};
            for (int c = 0; c < COLS; c++) {
                int x = drawPanelX + 3 + c * cellW + cellW / 2;
                int color = (c == 0 || c == 6) ? 0xFF60A5FA : C_MUTED;
                gfx.drawCenteredString(this.font, Component.literal(hdr[c]), x, hdrRowY + 2, color);
            }

            drawCalendarCells(gfx, mouseX, mouseY, drawPanelX);

            int legX = Math.min(drawPanelX + panelW + 6, this.width - MARGIN - 130);
            drawLegend(gfx, legX, btnRowY);
            return;
        }

        if (infoExpanded && selectedDate != null && !animating) {
            int ipX = drawPanelX + panelW + MARGIN;
            int maxTw = this.font.width(
                    selectedDate.format(DateTimeFormatter.ofPattern("M月d日 EEEE", java.util.Locale.CHINESE)));
            maxTw = Math.max(maxTw, this.font.width("调休非玩日"));
            if (selectedPlayable)
                maxTw = Math.max(maxTw, this.font.width(
                        String.format("%02d:00 - %02d:00", selectedStartH, selectedEndH)));
            int ipW = Math.min(maxTw + 24, this.width - ipX - MARGIN);
            if (ipW < 90) ipW = 90;

            int lineCount = 2;
            if (selectedPlayable) lineCount++;
            if (selectedMaxMin > 0) lineCount++;
            int ipH = 8 + lineCount * 13 + 4;
            int ipY = panelY + (panelH - ipH) / 2;
            if (ipY < MARGIN) ipY = MARGIN;
            if (ipY + ipH > this.height - MARGIN) ipY = this.height - ipH - MARGIN;

            gfx.fill(drawPanelX + panelW + 2, ipY, ipX + ipW, ipY + ipH, C_INFO_BG);
            gfx.fill(drawPanelX + panelW + 2, ipY, ipX + ipW, ipY + 2, C_DOT);
            gfx.fill(drawPanelX + panelW + 2, ipY, drawPanelX + panelW + 4, ipY + ipH, C_DOT);
            gfx.fill(ipX + ipW - 2, ipY, ipX + ipW, ipY + ipH, C_DOT);
            gfx.fill(drawPanelX + panelW + 2, ipY + ipH - 2, ipX + ipW, ipY + ipH, C_DOT);

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
                int lw2 = this.font.width(limitStr);
                gfx.drawCenteredString(this.font, Component.literal(limitStr), tx + lw2 / 2, ty, C_ORANGE);
            }
        }

        gfx.fill(drawPanelX - 2, panelY - 2, drawPanelX + panelW + 2, panelY + panelH + 2, C_ACCENT);
        gfx.fill(drawPanelX, panelY, drawPanelX + panelW, panelY + panelH, C_SURFACE);

        String title = currentMonth.format(MONTH_FMT);
        int titleW = this.font.width(title);
        int titleX = drawPanelX + panelW - titleW / 2 - 8;
        gfx.drawCenteredString(this.font, Component.literal(title), titleX, titleY, C_WHITE);

        String[] hdr = {"日","一","二","三","四","五","六"};
        for (int c = 0; c < COLS; c++) {
            int x = drawPanelX + 3 + c * cellW + cellW / 2;
            int color = (c == 0 || c == 6) ? 0xFF60A5FA : C_MUTED;
            gfx.drawCenteredString(this.font, Component.literal(hdr[c]), x, hdrRowY + 2, color);
        }

        drawCalendarCells(gfx, mouseX, mouseY, drawPanelX);

        int legX = Math.min(drawPanelX + panelW + 6, this.width - MARGIN - 130);
        drawLegend(gfx, legX, btnRowY);
    }

    private void drawCalendarCells(GuiGraphics gfx, int mouseX, int mouseY, int drawPanelX) {
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
                int x = drawPanelX + 3 + col * cellW;
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
    }

    private void drawLegend(GuiGraphics gfx, int legX, int legY) {
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
    }

    private static int blendAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
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

        if (mouseX >= MARGIN && mouseX < MARGIN + 20 && mouseY >= btnRowY && mouseY < btnRowY + 20) {
            if (this.minecraft == null) return true;
            var opt = this.minecraft.options.getSoundSourceOptionInstance(SoundSource.MUSIC);
            if (opt.get() > 0) {
                savedBgmVolume = opt.get();
                opt.set(0.0);
            } else {
                opt.set(savedBgmVolume > 0 ? savedBgmVolume : 1.0);
            }
            return true;
        }

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
                    if (!infoExpanded) {
                        infoExpanded = true;
                        panelXBefore = panelX;
                        panelXAfter = MARGIN;
                        animStartMs = System.currentTimeMillis();
                        animating = true;
                        animInitDone = false;
                    }
                    return true;
                }
            }
        }
        if (infoExpanded) {
            selectedDate = null;
            infoExpanded = false;
            panelXBefore = panelX;
            animStartMs = System.currentTimeMillis();
            animating = true;
            animInitDone = false;
            rebuildWidgets();
            panelXAfter = panelX;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
