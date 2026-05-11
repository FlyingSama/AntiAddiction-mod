package com.antiaddiction.screen;

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

/**
 * 防沉迷实名认证界面（NeoForge / Mojang 映射版）
 * GuiGraphics  = Fabric DrawContext
 * EditBox      = Fabric TextFieldWidget
 * Button       = Fabric ButtonWidget
 * Component    = Fabric Text
 */
public class VerificationScreen extends Screen {

    private EditBox nameField;
    private EditBox idCardField;
    private String  errorMessage   = "";
    private int     attempts       = 0;

    private static final int COLOR_BG_TOP    = 0xFF0D1B2A;
    private static final int COLOR_BG_BOTTOM = 0xFF1B2838;
    private static final int COLOR_ACCENT    = 0xFF4A90E2;
    private static final int COLOR_BOX       = 0xCC1E2D3D;

    public VerificationScreen() {
        super(Component.literal("防沉迷实名认证"));
    }

    @Override
    protected void init() {
        int cx = this.width  / 2;
        int cy = this.height / 2;

        // 姓名输入框
        this.nameField = new EditBox(this.font, cx - 120, cy - 22, 240, 20,
                Component.literal("姓名"));
        this.nameField.setMaxLength(30);


        this.addRenderableWidget(this.nameField);
        this.setInitialFocus(this.nameField);

        // 身份证输入框
        this.idCardField = new EditBox(this.font, cx - 120, cy + 14, 240, 20,
                Component.literal("身份证号"));
        this.idCardField.setMaxLength(18);


        this.addRenderableWidget(this.idCardField);

        // 确认按钮
        this.addRenderableWidget(Button.builder(
                Component.literal("提交实名认证"),
                btn -> doVerify()
        ).bounds(cx - 100, cy + 50, 200, 20).build());
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // ENTER / NUMPAD_ENTER
            doVerify();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void doVerify() {
        String name   = this.nameField.getValue().trim();
        String idCard = this.idCardField.getValue().trim().toUpperCase();
        this.errorMessage = "";

        if (name.isEmpty()) {
            this.errorMessage = "[X]请输入姓名";
            return;
        }
        if (idCard.length() != 18) {
            this.errorMessage = "[X]身份证号必须为18位";
            return;
        }

        VerificationResult result = DemoDatabase.verify(name, idCard);

        if (!result.isValid()) {
            this.attempts++;
            this.errorMessage = String.format("[X]%s（第 %d 次尝试）", result.getMessage(), this.attempts);
            this.nameField.setValue("");
            this.idCardField.setValue("");
            this.nameField.setFocused(true);
            return;
        }

        PlayerDataManager.getInstance().saveUserData(name, idCard, result.getAge(), result.isMinor());
        ApiClient.reportSessionStart(name, result.isMinor());

        if (this.minecraft == null) return;
        if (result.isMinor() && !PlayTimeChecker.isPlayAllowed()) {
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

        // 渐变背景
        gfx.fillGradient(0, 0, this.width, this.height, COLOR_BG_TOP, COLOR_BG_BOTTOM);

        // 面板
        int boxW = 280, boxH = 200;
        int boxX = cx - boxW / 2, boxY = cy - boxH / 2 - 30;
        gfx.fill(boxX - 2, boxY - 2, boxX + boxW + 2, boxY + boxH + 2, COLOR_ACCENT);
        gfx.fill(boxX, boxY, boxX + boxW, boxY + boxH, COLOR_BOX);

        // 标题
        gfx.drawCenteredString(this.font,
                Component.literal("游戏实名认证系统"), cx, boxY + 12, 0x4A90E2);
        gfx.drawCenteredString(this.font,
                Component.literal("根据《网络游戏管理办法》，请完成实名认证"), cx, boxY + 26, 0xAAAAAA);

        // 分割线
        gfx.fill(boxX + 10, boxY + 38, boxX + boxW - 10, boxY + 39, 0xFF4A90E2);

        // 字段标签
        String label1 = "姓　　名：";
        int lw1 = this.font.width(label1);
        gfx.drawCenteredString(this.font, Component.literal(label1), cx - 120 + lw1 / 2, cy - 36, 0xFFFFFF);
        String label2 = "身份证号：";
        int lw2 = this.font.width(label2);
        gfx.drawCenteredString(this.font, Component.literal(label2), cx - 120 + lw2 / 2, cy,      0xFFFFFF);

        // 错误提示
        if (!this.errorMessage.isEmpty()) {
            gfx.drawCenteredString(this.font,
                    Component.literal("[X] " + this.errorMessage), cx, cy + 80, 0xFF5555);
        }

        // 底部说明
        gfx.drawCenteredString(this.font,
                Component.literal("[ 演示版 | 数据仅保存于本地 | 成年测试账号: 张三 / 110101199001011234 ]"),
                cx, this.height - 20, 0x555555);

        // 渲染输入框（需手动调用）
        this.nameField.render(gfx, mouseX, mouseY, partialTick);
        this.idCardField.render(gfx, mouseX, mouseY, partialTick);

        super.render(gfx, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }
}
