package dev.cam123.mods.discord_bridge;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class DiscordMCBridgeServer implements DedicatedServerModInitializer, ServerLifecycleEvents.ServerStarting, ServerLifecycleEvents.ServerStopped {
    static MinecraftServer server = null;
    static JDA discord_api;
    public static DiscordLinkManager linkManager;
    public static Config config;

    private boolean disabled = false;

    @Override
    public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTING.register(this);
        ServerLifecycleEvents.SERVER_STOPPED.register(this);

        DiscordMCBridge.logger.info("Starting");
        try {
            Files.createDirectories(Paths.get("config/DiscordMCBridge"));
        } catch (IOException e) {
            DiscordMCBridge.logger.error("Failed to create config directory");
        }
        DiscordMCBridgeServer.config = new Config(new File("config/DiscordMCBridge/config.json"));
        DiscordMCBridgeServer.linkManager = new DiscordLinkManager(new File("config/DiscordMCBridge/links.json"));

        config.load();
        linkManager.load();
    }

    @Override
    public void onServerStarting(MinecraftServer server) {
        if (config.BOT_TOKEN.isEmpty()) {
            DiscordMCBridge.logger.warn("Discord bot token is empty, disabling myself to prevent problems");
            disabled = true;
            return;
        }

        DiscordMCBridgeServer.server = server;


        DiscordMCBridgeServer.discord_api = JDABuilder.createDefault(config.BOT_TOKEN)
                .addEventListeners(new DiscordEventListener()).enableIntents(GatewayIntent.MESSAGE_CONTENT).build();
        DiscordMCBridgeServer.discord_api.updateCommands().addCommands(
                Commands.slash("whitelist", "Whitelist a user")
                        .addOption(OptionType.USER, "user", "User to whitelist", false)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS)),
                Commands.slash("mcban", "Ban a user")
                        .addOption(OptionType.USER, "user", "User to ban", true)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS)),
                Commands.slash("link", "Link your minecraft account")
                        .addOption(OptionType.STRING, "code", "Link code", true),
                Commands.slash("sync", "Sync your discord name and avatar"),
                Commands.slash("stop", "Stop the server")
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
        ).queue(e -> System.out.println("Registered commands"), e -> System.out.println("Failed to register commands"));

        try {
            discord_api.awaitReady();
        } catch (InterruptedException e) {
            DiscordMCBridge.logger.error("Exception thrown while waiting for discord api to be ready");
            disabled = true;
            discord_api.shutdown();
            return;
        }

        if (config.messageBridge != null) {
            DiscordMCBridge.logger.info(config.messageBridge.channelId());
            DiscordMessageBridge messageBridge = new DiscordMessageBridge(config.messageBridge.channelId(), "config/Discord-Whitelist/webhook.txt");
            if (messageBridge.channel != null) {
                ServerMessageEvents.CHAT_MESSAGE.register(messageBridge);
                ServerMessageEvents.GAME_MESSAGE.register(messageBridge);
                ServerMessageEvents.COMMAND_MESSAGE.register(messageBridge);
                ServerLifecycleEvents.SERVER_STARTED.register(messageBridge);
                ServerLifecycleEvents.SERVER_STOPPED.register(messageBridge);
                discord_api.addEventListener(messageBridge);
            }
        }


        ActivityUpdater activityUpdater = new ActivityUpdater();
        ServerLifecycleEvents.SERVER_STARTED.register(activityUpdater);
        ServerPlayConnectionEvents.JOIN.register(activityUpdater);
        ServerPlayConnectionEvents.DISCONNECT.register(activityUpdater);
    }

    @Override
    public void onServerStopped(MinecraftServer server) {
        if (disabled) {
            return;
        }

        linkManager.save();
        discord_api.shutdown();
    }
}
