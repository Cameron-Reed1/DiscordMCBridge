package dev.cam123.mods.discord_bridge;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;

public class DiscordMCBridge implements ModInitializer {
    public static Logger logger;

    @Override
    public void onInitialize() {
        DiscordMCBridge.logger = LogUtils.getLogger();
    }
}
