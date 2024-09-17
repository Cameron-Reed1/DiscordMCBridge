package dev.cam123.mods.discord_bridge;

import net.dv8tion.jda.api.entities.Activity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;

public class ActivityUpdater implements ServerLifecycleEvents.ServerStarted, ServerPlayConnectionEvents.Join, ServerPlayConnectionEvents.Disconnect {
    @Override
    public void onServerStarted(MinecraftServer server) {
        updateActivity(server, 0);
    }

    @Override
    public void onPlayReady(ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) {
        updateActivity(server, server.getCurrentPlayerCount() + 1);
    }

    @Override
    public void onPlayDisconnect(ServerPlayNetworkHandler handler, MinecraftServer server) {
        updateActivity(server, server.getCurrentPlayerCount() - 1);
    }

    private void updateActivity(MinecraftServer server, int playerCount) {
        DiscordMCBridgeServer.discord_api.getPresence().setActivity(Activity.playing(
            DiscordMCBridgeServer.config.serverName +
                    " (" + playerCount + "/" + server.getMaxPlayerCount() + ")"));
    }
}
