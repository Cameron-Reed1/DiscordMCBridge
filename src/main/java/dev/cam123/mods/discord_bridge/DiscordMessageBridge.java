package dev.cam123.mods.discord_bridge;

import com.mojang.authlib.GameProfile;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public class DiscordMessageBridge extends ListenerAdapter implements ServerMessageEvents.ChatMessage, ServerMessageEvents.CommandMessage, ServerMessageEvents.GameMessage, ServerLifecycleEvents.ServerStarted, ServerLifecycleEvents.ServerStopped {
    TextChannel channel;
    Webhook hook = null;

    DiscordMessageBridge(String channelId, String webhook_file) {
        channel = DiscordMCBridgeServer.discord_api.getTextChannelById(Long.parseLong(channelId));
        if (channel == null) {
            DiscordMCBridge.logger.warn("Failed to get bridge channel from provided id");
            return;
        }

        File file = new File(webhook_file);
        if (file.exists()) {
            try {
                String webhook_id = Files.readString(Paths.get(webhook_file));
                DiscordMCBridgeServer.discord_api.retrieveWebhookById(webhook_id).queue(webhook -> hook = webhook);
            } catch (IOException e) {
                DiscordMCBridge.logger.warn("Failed to read webhook id from file. Disabling message bridge");
            }
        } else {
            channel.createWebhook(DiscordMCBridgeServer.config.serverName + " webhook").queue(webhook -> {
                hook = webhook;
                try {
                    Files.writeString(Paths.get(webhook_file), hook.getId(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                } catch (IOException e) {
                    DiscordMCBridge.logger.warn("Failed to write webhook id to file. A new webhook will be created each time the server starts up");
                }
            });
        }
    }

    private MessageSource profileToMsgSource(GameProfile profile) {
        if (DiscordMCBridgeServer.config.messageBridge.mc2discordUsernames()) {
            DiscordLinkManager.DiscordAccount account = DiscordMCBridgeServer.linkManager.getDiscordAccount(profile.getId());
            if (account != null) {
                return new MessageSource(account.user_name(), account.avatar_url());
            }
        }

        String avatar_url = "https://crafthead.net/avatar/" + profile.getId().toString();
        return new MessageSource(profile.getName(), avatar_url);
    }

    @Override
    public void onChatMessage(SignedMessage message, ServerPlayerEntity sender, MessageType.Parameters params) {
        GameProfile profile = sender.getGameProfile();
        MessageSource source = profileToMsgSource(profile);

        sendMessage(message.getContent().getLiteralString(), source);
    }

    @Override
    public void onCommandMessage(SignedMessage message, ServerCommandSource source, MessageType.Parameters params) {
        MessageSource msg_source;
        ServerPlayerEntity player = source.getPlayer();

        if (player == null) {
            msg_source = new MessageSource(DiscordMCBridgeServer.config.serverName, DiscordMCBridgeServer.config.serverAvatar);
        } else {
            GameProfile profile = player.getGameProfile();
            msg_source = profileToMsgSource(profile);
        }

        sendMessage(message.getContent().getLiteralString(), msg_source);
    }

    @Override
    public void onGameMessage(MinecraftServer server, Text message, boolean overlay) {
        String msg = message.getString();
        if (!overlay && !msg.startsWith("[Discord]")) {
            MessageSource msg_source = new MessageSource(DiscordMCBridgeServer.config.serverName, DiscordMCBridgeServer.config.serverAvatar);
            sendMessage(msg, msg_source);
        }
    }

    @Override
    public void onServerStarted(MinecraftServer server) {
        MessageSource msg_source = new MessageSource(DiscordMCBridgeServer.config.serverName, DiscordMCBridgeServer.config.serverAvatar);
        sendMessage(DiscordMCBridgeServer.config.serverName + " started", msg_source);
    }

    @Override
    public void onServerStopped(MinecraftServer server) {
        MessageSource msg_source = new MessageSource(DiscordMCBridgeServer.config.serverName, DiscordMCBridgeServer.config.serverAvatar);
        sendMessage(DiscordMCBridgeServer.config.serverName + " stopped", msg_source);
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.getChannel().getIdLong() != channel.getIdLong()) {
            return;
        }

        User user = event.getAuthor();

        ChatSource source = null;
        if (DiscordMCBridgeServer.config.messageBridge.discord2mcUsernames()) {
            GameProfile profile = DiscordMCBridgeServer.linkManager.getMCProfile(user.getIdLong());
            if (profile != null) {
                source = new ChatSource(profile.getName());
            }
        }

        if (source == null) {
            source = new ChatSource(user.getEffectiveName());
        }

        sendChatMessage(event.getMessage().getContentRaw(), source);
    }

    private void sendMessage(String message, MessageSource source) {
        if (DiscordMCBridgeServer.config.messageBridge == null) {
            return;
        }

        if (hook == null) {
            DiscordMCBridge.logger.warn("Cannot forward message. Webhook is null");
            return;
        }

        hook.sendMessage(message).setUsername(source.userName).setAvatarUrl(source.avatar).queue();
    }

    private void sendChatMessage(String message, ChatSource source) {
        if (DiscordMCBridgeServer.config.messageBridge == null) {
            return;
        }

        MutableText text = Text.literal("[Discord] ").formatted(Formatting.BLUE);
        text.append(Text.translatable("chat.type.text", source.userName, message).formatted(Formatting.WHITE)); // Fine
        DiscordMCBridgeServer.server.getPlayerManager().broadcast(text, false);
    }

    record MessageSource(String userName, String avatar) { }
    record ChatSource(String userName) { }
}
