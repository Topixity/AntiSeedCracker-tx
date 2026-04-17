package me.gadse.antiseedcracker.commands;

import me.gadse.antiseedcracker.AntiSeedCracker;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AntiSeedCrackerCommand implements CommandExecutor, TabCompleter {

    private static final Set<String> SUBCOMMANDS = Set.of("reload");

    private final AntiSeedCracker plugin;

    public AntiSeedCrackerCommand(AntiSeedCracker plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("AntiSeedCracker v" + plugin.getDescription().getVersion());
            sender.sendMessage("Usage: /" + label + " reload");
            return true;
        }
        if (!args[0].equalsIgnoreCase("reload")) {
            return false;
        }
        if (!sender.hasPermission("antiseedcracker.admin")) {
            sender.sendMessage("You do not have permission to do that.");
            return true;
        }

        try {
            plugin.reloadConfig();
            plugin.reload(false);
            sender.sendMessage("AntiSeedCracker config reloaded.");
        } catch (Throwable t) {
            plugin.getLogger().severe("Reload failed: " + t);
            sender.sendMessage("Reload failed: " + t.getMessage());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && sender.hasPermission("antiseedcracker.admin")) {
            return StringUtil.copyPartialMatches(args[0], SUBCOMMANDS, new ArrayList<>());
        }
        return List.of();
    }
}
