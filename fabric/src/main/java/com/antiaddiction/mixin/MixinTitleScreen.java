package com.antiaddiction.mixin;

import com.antiaddiction.data.PlayerDataManager;
import com.antiaddiction.screen.TimeRestrictionScreen;
import com.antiaddiction.screen.VerificationScreen;
import com.antiaddiction.time.PlayTimeChecker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public class MixinTitleScreen {

    @Inject(method = "init", at = @At("HEAD"), cancellable = true)
    private void antiAddictionGate(CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerDataManager mgr  = PlayerDataManager.getInstance();

        if (!mgr.isVerified()) {
            client.execute(() -> client.setScreen(new VerificationScreen()));
            ci.cancel();
        } else if (mgr.isMinor() && !PlayTimeChecker.isPlayAllowed()) {
            client.execute(() -> client.setScreen(new TimeRestrictionScreen()));
            ci.cancel();
        }
    }
}
