package com.antiaddiction.screen;

import com.antiaddiction.AntiAddictionMod;
import com.antiaddiction.data.DemoDatabase;
import com.antiaddiction.data.PlayerDataManager;
import com.antiaddiction.data.VerificationResult;
import com.antiaddiction.network.ApiClient;
import com.antiaddiction.time.PlayTimeChecker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

public class VerificationScreen extends Screen {

    private EditBox nameField;
    private EditBox idCardField;
    private String errorMessage   = "";
    private String successMessage = "";
    private int    attempts       = 0;
    private int    jumpTicks      = -1;

    private static final int COLOR_BG_TOP      = 0xFF0D1B2A;
    private static final int COLOR_BG_BOTTOM   = 0xFF1B2838;
    private static final int COLOR_ACCENT      = 0xFF4A90E2;
    private static final int COLOR_BOX         = 0xCC1E2D3D;
    private static final int COLOR_OK_BG       = 0xCC1B5E20;
    private static final int COLOR_OK_BORDER   = 0xFF4CAF50;
    private static final int COLOR_ERR_BG      = 0xCC5E1B1B;
    private static final int COLOR_ERR_BORDER  = 0xFFEF4444;

    public VerificationScreen() {
        super(Component.literal("防沉迷实名认证"));
    }

    @Override
    protected void init() {
        int cx = this.width  / 2;
        int cy = this.height / 2;

        this.nameField = new EditBox(this.font, cx - 120, cy - 22, 240, 20,
                Component.literal("姓名"));
        this.nameField.setMaxLength(30);
        this.addRenderableWidget(this.nameField);
        this.setInitialFocus(this.nameField);

        this.idCardField = new EditBox(this.font, cx - 120, cy + 14, 240, 20,
                Component.literal("身份证号"));
        this.idCardField.setMaxLength(18);
        this.addRenderableWidget(this.idCardField);

        this.addRenderableWidget(Button.builder(
                Component.literal("提交实名认证"),
                btn -> doVerify()
        ).bounds(cx - 100, cy + 50, 200, 20).build());
    }

    @Override
    public void tick() {
        if (jumpTicks > 0) {
            jumpTicks--;
            if (jumpTicks == 0) {
                doJump();
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) {
            doVerify();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void doVerify() {
        if (jumpTicks >= 0) return;

        String name   = this.nameField.getValue().trim();
        String idCard = this.idCardField.getValue().trim().toUpperCase();
        this.errorMessage   = "";
        this.successMessage = "";

        if (name.isEmpty()) {
            this.errorMessage = "请输入姓名";
            return;
        }
        if (idCard.length() != 18) {
            this.errorMessage = "身份证号必须为18位";
            return;
        }

        VerificationResult result = DemoDatabase.verify(name, idCard);

        if (!result.isValid()) {
            this.attempts++;
            this.errorMessage = result.getMessage() + "（第 " + this.attempts + " 次尝试）";
            this.nameField.setValue("");
            this.idCardField.setValue("");
            this.nameField.setFocused(true);
            return;
        }

        PlayerDataManager.getInstance().saveUserData(name, idCard, result.getAge(), result.isMinor());
        ApiClient.reportSessionStart(name, result.isMinor());
        this.successMessage = result.getMessage();
        this.jumpTicks = 10;
    }

    private void doJump() {
        if (this.minecraft == null) return;
        if (PlayerDataManager.getInstance().isMinor() && !PlayTimeChecker.isPlayAllowed()) {
            this.minecraft.setScreen(new TimeRestrictionScreen());
        } else {
            PlayTimeChecker.markSessionStart();
            this.minecraft.setScreen(new TitleScreen(false));
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        int cx = this.width  / 2;
        int cy = this.height / 2;

        gfx.fillGradient(0, 0, this.width, this.height, COLOR_BG_TOP, COLOR_BG_BOTTOM);

        int boxW = 280, boxH = 200;
        int boxX = cx - boxW / 2, boxY = cy - boxH / 2 - 30;
        gfx.fill(boxX - 2, boxY - 2, boxX + boxW + 2, boxY + boxH + 2, COLOR_ACCENT);
        gfx.fill(boxX, boxY, boxX + boxW, boxY + boxH, COLOR_BOX);

        gfx.drawCenteredString(this.font,
                Component.literal("游戏实名认证系统"), cx, boxY + 12, 0x4A90E2);
        gfx.drawCenteredString(this.font,
                Component.literal("根据《网络游戏管理办法》，请完成实名认证"), cx, boxY + 26, 0xAAAAAA);

        gfx.fill(boxX + 10, boxY + 38, boxX + boxW - 10, boxY + 39, 0xFF4A90E2);

        String label1 = "姓　　名：";
        int lw1 = this.font.width(label1);
        gfx.drawCenteredString(this.font, Component.literal(label1), cx - 120 + lw1 / 2, cy - 36, 0xFFFFFF);
        String label2 = "身份证号：";
        int lw2 = this.font.width(label2);
        gfx.drawCenteredString(this.font, Component.literal(label2), cx - 120 + lw2 / 2, cy,      0xFFFFFF);

        // 结果弹窗
        if (!this.errorMessage.isEmpty()) {
            drawPopup(gfx, cx, cy + 85, this.errorMessage, COLOR_ERR_BG, COLOR_ERR_BORDER, 0xFFFF5555);
        }
        if (!this.successMessage.isEmpty()) {
            drawPopup(gfx, cx, cy + 85, this.successMessage, COLOR_OK_BG, COLOR_OK_BORDER, 0xFF55FF55);
        }

        gfx.drawCenteredString(this.font,
                Component.literal("[ 演示版 | 数据仅保存于本地 | 测试: 胡墨凡 / 429004201712150350 ]"),
                cx, this.height - 20, 0x555555);

        this.nameField.render(gfx, mouseX, mouseY, partialTick);
        this.idCardField.render(gfx, mouseX, mouseY, partialTick);
        super.render(gfx, mouseX, mouseY, partialTick);
    }

    private void drawPopup(GuiGraphics gfx, int cx, int cy, String msg, int bg, int border, int textColor) {
        int pw = Math.min(this.font.width(msg) + 32, this.width - 20);
        int ph = 24;
        int px = cx - pw / 2;
        int py = cy - ph / 2;
        gfx.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, border);
        gfx.fill(px, py, px + pw, py + ph, bg);
        gfx.drawCenteredString(this.font, Component.literal(msg), cx, py + (ph - this.font.lineHeight) / 2, textColor);
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }
}
