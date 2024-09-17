package dev.cam123.mods.discord_bridge;

import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class Config {
    String BOT_TOKEN = "";
    boolean autoWhitelist = true;
    boolean ephemeralResponses = true;
    DiscordMessageBridge messageBridge = null;
    String serverName = "Server";
    String serverAvatar = "https://archive.org/download/minecraft-vector-icons/Minecraft_2009_icon_accurate.png";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final File file;


    private static final String ENABLE_KEY = "enable";
    private static final String BOT_TOKEN_KEY = "token";
    private static final String AUTO_WHITELIST_KEY = "auto_whitelist";
    private static final String EPHEMERAL_RESPONSES_KEY = "ephemeral_responses";
    private static final String MESSAGE_BRIDGE_KEY = "message_bridge";
    private static final String CHANNEL_ID_KEY = "channel_id";
    private static final String TRANSLATE_USERNAMES_KEY = "translate_usernames";
    private static final String TRANSLATE_DISCORD_KEY = "discord2mc";
    private static final String TRANSLATE_MINECRAFT_KEY = "mc2discord";
    private static final String SERVER_NAME_KEY = "server_name";
    private static final String SERVER_AVATAR_URL_KEY = "server_avatar_url";


    Config(File file) {
        this.file = file;
    }

    public void load() {
        if (!file.exists()) {
            writeDefault();
            return;
        }

        JsonObject json;
        try (BufferedReader reader = Files.newReader(file, StandardCharsets.UTF_8)) {
            json = GSON.fromJson(reader, JsonObject.class);
        } catch (IOException ignored) {
            return;
        }

        if (json.has(BOT_TOKEN_KEY)) {
            BOT_TOKEN = json.get(BOT_TOKEN_KEY).getAsString();
        } else {
            DiscordMCBridge.logger.warn("Bot token was not provided in config.json");
        }

        if (json.has(SERVER_NAME_KEY)) {
            serverName = json.get(SERVER_NAME_KEY).getAsString();
        }

        if (json.has(SERVER_AVATAR_URL_KEY)) {
            serverAvatar = json.get(SERVER_AVATAR_URL_KEY).getAsString();
        }

        if (json.has(AUTO_WHITELIST_KEY)) {
            autoWhitelist = json.get(AUTO_WHITELIST_KEY).getAsBoolean();
        }

        if (json.has(EPHEMERAL_RESPONSES_KEY)) {
            ephemeralResponses = json.get(EPHEMERAL_RESPONSES_KEY).getAsBoolean();
        }

        if (json.has(MESSAGE_BRIDGE_KEY)) {
            JsonObject bridgeJson = json.getAsJsonObject(MESSAGE_BRIDGE_KEY);
            boolean enabled = true;
            if (bridgeJson.has(ENABLE_KEY)) {
                enabled = bridgeJson.get(ENABLE_KEY).getAsBoolean();
            }

            if (bridgeJson.has(CHANNEL_ID_KEY) && enabled) {
                String channelId = bridgeJson.get(CHANNEL_ID_KEY).getAsString();
                boolean discord2mc = false;
                boolean mc2discord = false;
                if (bridgeJson.has(TRANSLATE_USERNAMES_KEY)) {
                    JsonElement elm = bridgeJson.get(TRANSLATE_USERNAMES_KEY);
                    if (elm.isJsonObject()) {
                        JsonObject obj = bridgeJson.getAsJsonObject(TRANSLATE_USERNAMES_KEY);
                        if (obj.has(TRANSLATE_DISCORD_KEY)) {
                            discord2mc = obj.get(TRANSLATE_DISCORD_KEY).getAsBoolean();
                        }
                        if (obj.has(TRANSLATE_MINECRAFT_KEY)) {
                            mc2discord = obj.get(TRANSLATE_MINECRAFT_KEY).getAsBoolean();
                        }
                    } else {
                        mc2discord = discord2mc = bridgeJson.get(TRANSLATE_USERNAMES_KEY).getAsBoolean();
                    }
                }

                messageBridge = new DiscordMessageBridge(channelId, discord2mc, mc2discord);
            }
        }
    }

    private void writeDefault() {
        JsonObject json = new JsonObject();

        json.addProperty(BOT_TOKEN_KEY, BOT_TOKEN);
        json.addProperty(SERVER_NAME_KEY, serverName);
        json.addProperty(SERVER_AVATAR_URL_KEY, serverAvatar);
        json.addProperty(AUTO_WHITELIST_KEY, autoWhitelist);
        json.addProperty(EPHEMERAL_RESPONSES_KEY, ephemeralResponses);

        JsonObject bridgeJson = new JsonObject();
        bridgeJson.addProperty(ENABLE_KEY, false);
        bridgeJson.addProperty(CHANNEL_ID_KEY, "");
        bridgeJson.addProperty(TRANSLATE_USERNAMES_KEY, true);
        json.add(MESSAGE_BRIDGE_KEY, bridgeJson);

        try (BufferedWriter writer = Files.newWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(json, GSON.newJsonWriter(writer));
        } catch (IOException ignored) {
            DiscordMCBridge.logger.warn("Failed to write default config file to disk");
        }
    }

    record DiscordMessageBridge(String channelId, boolean discord2mcUsernames, boolean mc2discordUsernames) {}
}
