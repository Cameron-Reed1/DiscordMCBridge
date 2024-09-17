package dev.cam123.mods.discord_bridge;

import com.google.common.io.Files;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import com.soundicly.jnanoidenhanced.jnanoid.NanoIdUtils;
import net.minecraft.util.JsonHelper;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.UUID;

public class DiscordLinkManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final ArrayList<ActiveLinkCode> activeLinkCodes = new ArrayList<>();
    private final ArrayList<LinkedAccount> linkedAccounts = new ArrayList<>();
    private final File file;

    public DiscordLinkManager(File file) {
        this.file = file;
    }

    public String generateLinkCode(GameProfile profile) {
        String activeCode = getActiveCodeForProfile(profile);
        if (activeCode != null) {
            return activeCode;
        }

        String code = NanoIdUtils.randomNanoId(9);
        activeLinkCodes.add(new ActiveLinkCode(code, profile, System.currentTimeMillis() + 5 * 60 * 1000));

        return code;
    }

    public String getActiveCodeForProfile(GameProfile profile) {
        for (ActiveLinkCode linkCode: activeLinkCodes) {
            if (linkCode.isExpired()) {
                activeLinkCodes.remove(linkCode);
                continue;
            }

            if (linkCode.profile.getId().equals(profile.getId())) {
                return linkCode.code;
            }
        }

        return null;
    }

    public @Nullable GameProfile linkAccount(String code, DiscordAccount account) {
        for (ActiveLinkCode linkCode: activeLinkCodes) {
            if (linkCode.isExpired()) {
                activeLinkCodes.remove(linkCode);
                continue;
            }

            if (linkCode.code.equals(code)) {
                linkedAccounts.add(new LinkedAccount(account, linkCode.profile));
                activeLinkCodes.remove(linkCode);
                save();
                return linkCode.profile;
            }
        }

        return null;
    }

    public @Nullable GameProfile getMCProfile(long discord_id) {
        for (LinkedAccount link: linkedAccounts) {
            if (link.discord.id == discord_id) {
                return link.profile;
            }
        }

        return null;
    }

    public @Nullable DiscordAccount getDiscordAccount(UUID uuid) {
        for (LinkedAccount link: linkedAccounts) {
            if (link.profile.getId().equals(uuid)) {
                return link.discord;
            }
        }

        return null;
    }

    public boolean updateDiscordAccount(DiscordAccount new_details) {
        for (LinkedAccount link: linkedAccounts) {
            if (link.discord.id == new_details.id) {
                LinkedAccount new_link = new LinkedAccount(new_details, link.profile);
                linkedAccounts.remove(link);
                linkedAccounts.add(new_link);
                return true;
            }
        }

        return false;
    }

    public void save() {
        JsonArray array = new JsonArray();
        for (LinkedAccount link: linkedAccounts) {
            array.add(link.toJson());
        }

        try (BufferedWriter writer = Files.newWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(array, GSON.newJsonWriter(writer));
        } catch (IOException ignored) {
            DiscordMCBridge.logger.warn("Failed to write linked accounts to disk");
        }
    }

    public void load() {
        if (!file.exists()) {
            return;
        }

        try (BufferedReader reader = Files.newReader(file, StandardCharsets.UTF_8)) {
            JsonArray jsonArray = GSON.fromJson(reader, JsonArray.class);

            if (jsonArray != null) {
                for (JsonElement elem : jsonArray) {
                    JsonObject json = JsonHelper.asObject(elem, "");
                    LinkedAccount link = LinkedAccount.fromJson(json);
                    if (link != null) {
                        linkedAccounts.add(link);
                    }
                }
            }
        } catch (IOException ignored) { }
    }


    public record DiscordAccount(long id, String user_name, String avatar_url) { }
    record ActiveLinkCode(String code, GameProfile profile, long expires) {
        boolean isExpired() {
            return System.currentTimeMillis() > expires;
        }
    }
    record LinkedAccount(DiscordAccount discord, GameProfile profile) {
        static LinkedAccount fromJson(JsonObject json) {
            if (!json.has("discord") || !json.has("minecraft")) {
                return null;
            }

            JsonObject discord_json = json.getAsJsonObject("discord");
            JsonObject mc_json = json.getAsJsonObject("minecraft");

            if (!discord_json.has("id") || !discord_json.has("avatar") || !mc_json.has("uuid") || !mc_json.has("name")) {
                return null;
            }

            String name = mc_json.get("name").getAsString();
            String uuid_str = mc_json.get("uuid").getAsString();
            UUID uuid = UUID.fromString(uuid_str);

            GameProfile profile = new GameProfile(uuid, name);

            long discord_id = discord_json.get("id").getAsLong();
            String discord_avatar = discord_json.get("avatar").getAsString();
            String discord_name = discord_json.get("name").getAsString();

            DiscordAccount discord = new DiscordAccount(discord_id, discord_name, discord_avatar);

            return new LinkedAccount(discord, profile);
        }

        JsonObject toJson() {
            JsonObject obj = new JsonObject();

            JsonObject discord_obj = new JsonObject();
            JsonObject mc_obj = new JsonObject();

            discord_obj.addProperty("id", discord.id);
            discord_obj.addProperty("name", discord.user_name);
            discord_obj.addProperty("avatar", discord.avatar_url);

            mc_obj.addProperty("name", profile.getName());
            mc_obj.addProperty("uuid", profile.getId().toString());

            obj.add("discord", discord_obj);
            obj.add("minecraft", mc_obj);

            return obj;
        }
    }
}
