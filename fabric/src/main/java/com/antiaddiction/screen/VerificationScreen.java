package com.antiaddiction.screen;

import com.antiaddiction.data.DemoDatabase;
import com.antiaddiction.data.PlayerDataManager;
import com.antiaddiction.data.VerificationResult;
import com.antiaddiction.network.ApiClient;
import com.antiaddiction.time.PlayTimeChecker;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * 防沉迷实名认证界面（Fabric / Yarn 映射版）
 * 在主菜单前强制展示，ESC / 关闭按钮被禁用。
 */
public class VerificationScreen extends Screen {

    private TextFieldWidget nameField;
    private TextFieldWidget idCardField;
    private String errorMessage = "";
    private String successMessage = "";
    private int attempts = 0;
    private double savedBgmVolume = 1.0;
    private static final Identifier BGM_ON  = Identifier.of("antiaddiction", "textures/gui/sound_on");
    private static final Identifier BGM_OFF = Identifier.of("antiaddiction", "textures/gui/sound_off");

    // 渐变背景色
    private static final int COLOR_BG_TOP    = 0xFF0D1B2A;
    private static final int COLOR_BG_BOTTOM = 0xFF1B2838;
    private static final int COLOR_ACCENT    = 0xFF4A90E2;
    private static final int COLOR_BOX       = 0xCC1E2D3D;
    private static final int COLOR_ERROR     = 0xFFFF5C5C;
    private static final int COLOR_SUCCESS   = 0xFF5CFF8A;

    public VerificationScreen() {
        super(Text.literal("防沉迷实名认证"));
    }

    @Override
    protected void init() {
        int cx = this.width  / 2;
        int cy = this.height / 2;

        // 姓名输入框
        this.nameField = new TextFieldWidget(
                this.textRenderer, cx - 120, cy - 22, 240, 22,
                Text.literal("姓名")
        );
        this.nameField.setMaxLength(30);
        this.nameField.setPlaceholder(Text.literal("请输入真实姓名"));

        this.addDrawableChild(this.nameField);

        // 身份证输入框
        this.idCardField = new TextFieldWidget(
                this.textRenderer, cx - 120, cy + 14, 240, 22,
                Text.literal("身份证号")
        );
        this.idCardField.setMaxLength(18);
        this.idCardField.setPlaceholder(Text.literal("请输入18位居民身份证号码"));

        this.addDrawableChild(this.idCardField);

        // 确认按钮
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("提交实名认证"),
                btn -> this.doVerify()
        ).dimensions(cx - 100, cy + 50, 200, 22).build());
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        if (keyInput.key() == 257 || keyInput.key() == 335) {
            doVerify();
            return true;
        }
        return super.keyPressed(keyInput);
    }

    private void doVerify() {
        String name   = this.nameField.getText().trim();
        String idCard = this.idCardField.getText().trim().toUpperCase();

        this.errorMessage   = "";
        this.successMessage = "";

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
            this.nameField.setText("");
            this.idCardField.setText("");
            this.nameField.setFocused(true);
            return;
        }

        // 认证成功
        PlayerDataManager.getInstance().saveUserData(name, idCard, result.getAge(), result.isMinor());
        ApiClient.reportSessionStart(name, result.isMinor());

        if (result.isMinor() && !PlayTimeChecker.isPlayAllowed()) {
            // 未成年 + 当前不在允许时段
            assert this.client != null;
            this.client.setScreen(new TimeRestrictionScreen());
        } else {
            // 成年人，或未成年但在允许时段 → 进入游戏
            PlayTimeChecker.markSessionStart();
            assert this.client != null;
            this.client.setScreen(new TitleScreen(false));
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int cx = this.width  / 2;
        int cy = this.height / 2;

        // ---- 背景渐变 ----
        ctx.fillGradient(0, 0, this.width, this.height, COLOR_BG_TOP, COLOR_BG_BOTTOM);

        boolean bgmOn = this.client == null ||
                this.client.options.getSoundVolumeOption(SoundCategory.MUSIC).getValue() > 0;
        int bgmX = this.width - 28;
        ctx.drawTexturedQuad(bgmOn ? BGM_ON : BGM_OFF, bgmX, 9, bgmX + 20, 28, 0f, 0f, 1f, 1f);

        // ---- 面板背景 ----
        int boxW = 280, boxH = 200;
        int boxX = cx - boxW / 2, boxY = cy - boxH / 2 - 30;
        ctx.fill(boxX - 2, boxY - 2, boxX + boxW + 2, boxY + boxH + 2, COLOR_ACCENT); // 描边
        ctx.fill(boxX, boxY, boxX + boxW, boxY + boxH, COLOR_BOX);                     // 面板

        // ---- 标题 ----
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("游戏实名认证系统"),
                cx, boxY + 12, 0x4A90E2);
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("根据《网络游戏管理办法》，请完成实名认证"),
                cx, boxY + 26, 0xAAAAAA);

        // ---- 分割线 ----
        ctx.fill(boxX + 10, boxY + 38, boxX + boxW - 10, boxY + 39, 0xFF4A90E2);

        // ---- 字段标签 ----
        String label1 = "姓　　名：";
        int lw1 = this.textRenderer.getWidth(label1);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(label1), cx - 120 + lw1 / 2, cy - 36, 0xFFFFFF);
        String label2 = "身份证号：";
        int lw2 = this.textRenderer.getWidth(label2);
        ctx.drawCenteredTextWithShadow(this.textRenderer, Text.literal(label2), cx - 120 + lw2 / 2, cy, 0xFFFFFF);

        // ---- 错误 / 成功提示 ----
        if (!this.errorMessage.isEmpty()) {
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("[X] " + this.errorMessage), cx, cy + 80, 0xFF5555);
        }

        // ---- 底部说明 ----
        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("[ 本系统为演示版 | 数据仅保存于本地 ]"),
                cx, this.height - 20, 0x555555);

        // ---- 渲染子控件 ----
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    @Override
    public void close() { /* 禁止关闭 */ }

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
