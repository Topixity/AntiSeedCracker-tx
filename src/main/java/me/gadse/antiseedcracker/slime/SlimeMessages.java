package me.gadse.antiseedcracker.slime;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

public final class SlimeMessages {

    private final FileConfiguration cfg;
    private final String prefix;

    private SlimeMessages(FileConfiguration cfg) {
        this.cfg = cfg;
        this.prefix = color(cfg.getString("prefix", ""));
    }

    public static SlimeMessages load(FileConfiguration cfg) {
        return new SlimeMessages(cfg);
    }

    public void send(CommandSender to, String key, Object... placeholders) {
        String raw = cfg.getString(key, key);
        if (placeholders != null) {
            for (int i = 0; i + 1 < placeholders.length; i += 2) {
                raw = raw.replace("{" + placeholders[i] + "}", String.valueOf(placeholders[i + 1]));
            }
        }
        to.sendMessage(prefix + color(raw));
    }

    @SuppressWarnings("deprecation")
    private static String color(String s) {
        if (s == null) return "";
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
