package dev.cam123.mods.discord_bridge;

import com.mojang.authlib.GameProfile;
import dev.cam123.mods.discord_bridge.DiscordLinkManager.DiscordAccount;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.minecraft.server.BannedPlayerEntry;
import net.minecraft.server.BannedPlayerList;
import net.minecraft.server.Whitelist;
import net.minecraft.server.WhitelistEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class DiscordEventListener extends ListenerAdapter {
    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("link")) {
            event.deferReply(DiscordMCBridgeServer.config.ephemeralResponses).queue();

            OptionMapping codeOption = event.getOption("code");
            if (codeOption == null) {
                event.getHook().editOriginal("Failed to link account. No code provided").queue();
                return;
            }

            String code = codeOption.getAsString();
            User user = event.getUser();
            DiscordAccount account = new DiscordAccount(user.getIdLong(), user.getEffectiveName(    ), user.getAvatarUrl());
            GameProfile profile = DiscordMCBridgeServer.linkManager.linkAccount(code, account);
            if (profile == null) {
                event.getHook().editOriginal("Failed to link account. Invalid link code").queue();
                return;
            }

            if (DiscordMCBridgeServer.config.autoWhitelist) {
                Whitelist whitelist = DiscordMCBridgeServer.server.getPlayerManager().getWhitelist();
                if (whitelist.isAllowed(profile)) {
                    event.getHook().editOriginal("Linked " + user.getEffectiveName() + " to " + profile.getName()).queue();
                    return;
                }

                whitelist.add(new WhitelistEntry(profile));
                event.getHook().editOriginal("Linked " + user.getEffectiveName() + " to " + profile.getName() + " and added to whitelist").queue();
            } else {
                event.getHook().editOriginal("Linked " + user.getEffectiveName() + " to " + profile.getName()).queue();
            }
        } else if (event.getName().equals("whitelist")) {
            event.deferReply(DiscordMCBridgeServer.config.ephemeralResponses).queue();

            User user = event.getUser();
            OptionMapping userOption = event.getOption("user");
            if (userOption != null) {
                user = userOption.getAsUser();
            }

            GameProfile profile = DiscordMCBridgeServer.linkManager.getMCProfile(user.getIdLong());
            if (profile == null) {
                event.getHook().editOriginal("Failed to whitelist " + user.getEffectiveName() + ". No MC account linked").queue();
                return;
            }

            Whitelist whitelist = DiscordMCBridgeServer.server.getPlayerManager().getWhitelist();
            if (whitelist.isAllowed(profile)) {
                event.getHook().editOriginal(user.getEffectiveName() + " (" + profile.getName() + ") is already whitelisted").queue();
                return;
            }

            whitelist.add(new WhitelistEntry(profile));
            event.getHook().editOriginal("Whitelisted " + user.getEffectiveName() + " (" + profile.getName() + ")").queue();
        } else if (event.getName().equals("sync")) {
            event.deferReply(DiscordMCBridgeServer.config.ephemeralResponses).queue();

            User user = event.getUser();
            DiscordAccount new_details = new DiscordAccount(user.getIdLong(), user.getEffectiveName(), user.getAvatarUrl());
            if (DiscordMCBridgeServer.linkManager.updateDiscordAccount(new_details)) {
                event.getHook().editOriginal("Successfully synced account details").queue();
            } else {
                event.getHook().editOriginal("Failed to sync account details").queue();
            }
        } else if (event.getName().equals("stop")) {
            event.reply("Shutting down the server")
                    .setEphemeral(DiscordMCBridgeServer.config.ephemeralResponses).queue();
            DiscordMCBridgeServer.server.stop(false);
        } else if (event.getName().equals("mcban")) {
            event.deferReply(DiscordMCBridgeServer.config.ephemeralResponses).queue();

            User user = event.getUser();
            GameProfile profile = DiscordMCBridgeServer.linkManager.getMCProfile(user.getIdLong());

            if (profile != null) {
                BannedPlayerList bannedList = DiscordMCBridgeServer.server.getPlayerManager().getUserBanList();
                bannedList.add(new BannedPlayerEntry(profile));
                ServerPlayerEntity serverPlayerEntity = DiscordMCBridgeServer.server.getPlayerManager().getPlayer(profile.getId());
                if (serverPlayerEntity != null) {
                    serverPlayerEntity.networkHandler.disconnect(Text.translatable("multiplayer.disconnect.banned"));
                }
                event.getHook().editOriginal("Banned " + user.getEffectiveName()).queue();
            } else {
                event.getHook().editOriginal("Unable to ban " + user.getEffectiveName() + ". Account is not linked").queue();
            }
        } else {
            event.reply("Unhandled command. This is a bug. Please report this")
                    .setEphemeral(DiscordMCBridgeServer.config.ephemeralResponses).queue();
        }
    }
}
