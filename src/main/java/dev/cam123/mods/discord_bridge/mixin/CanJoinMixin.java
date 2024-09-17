package dev.cam123.mods.discord_bridge.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.authlib.GameProfile;
import dev.cam123.mods.discord_bridge.DiscordMCBridgeServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerManager.class)
public class CanJoinMixin {

    @Redirect(method = "checkCanJoin", at = @At(value = "INVOKE", target = "Lnet/minecraft/text/Text;translatable(Ljava/lang/String;)Lnet/minecraft/text/MutableText;"))
    MutableText replaceWhitelistText(String key, @Local(argsOnly = true) GameProfile profile) {
        return Text.literal(Text.translatable("discord_bridge.link_account", DiscordMCBridgeServer.linkManager.generateLinkCode(profile)).getString());
    }
}
