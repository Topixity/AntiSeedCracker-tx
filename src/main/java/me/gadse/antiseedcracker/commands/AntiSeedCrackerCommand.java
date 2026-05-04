package me.gadse.antiseedcracker.commands;

import me.gadse.antiseedcracker.AntiSeedCracker;
import me.gadse.antiseedcracker.slime.SlimeAntiCrackModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AntiSeedCrackerCommand implements CommandExecutor, TabCompleter {

    private static final Set<String> ROOT_SUBCOMMANDS = Set.of("reload", "slime");
    private static final Set<String> SLIME_SUBCOMMANDS = Set.of("reload", "check", "find", "shuffle-salt");

    private final AntiSeedCracker plugin;

    public AntiSeedCrackerCommand(AntiSeedCracker plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("AntiSeedCracker v" + plugin.getDescription().getVersion());
            sender.sendMessage("Usage: /" + label + " <reload|slime>");
            sender.sendMessage("       /" + label + " slime <reload|check|find|shuffle-salt>");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload" -> handleRootReload(sender);
            case "slime" -> handleSlime(sender, label, args);
            default -> {
                sender.sendMessage("Usage: /" + label + " <reload|slime>");
                return true;
            }
        }
        return true;
    }

    private void handleRootReload(CommandSender sender) {
        if (!sender.hasPermission("antiseedcracker.admin")) {
            sender.sendMessage("You do not have permission to do that.");
            return;
        }
        try {
            plugin.reloadConfig();
            plugin.reload(false);
            sender.sendMessage("AntiSeedCracker config reloaded (slime module included).");
        } catch (Throwable t) {
            plugin.getLogger().severe("Reload failed: " + t);
            sender.sendMessage("Reload failed: " + t.getMessage());
        }
    }

    private void handleSlime(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("antiseedcracker.admin")) {
            sender.sendMessage("You do not have permission to do that.");
            return;
        }
        SlimeAntiCrackModule slime = plugin.getSlimeModule();
        if (slime == null) {
            sender.sendMessage("Slime module is not initialised.");
            return;
        }

        if (args.length == 1) {
            sender.sendMessage("Usage: /" + label + " slime <reload|check|find|shuffle-salt>");
            return;
        }

        String slimeSub = args[1].toLowerCase();

        if (!"reload".equals(slimeSub) && !slime.isEnabled()) {
            slime.messages().send(sender, "module-disabled");
            return;
        }

        switch (slimeSub) {
            case "reload" -> {
                try {
                    plugin.getServer().getAsyncScheduler().runNow(plugin, task -> {
                        slime.disable();
                        slime.enable();
                        sender.sendMessage("Slime module reloaded.");
                    });
                } catch (Throwable t) {
                    plugin.getLogger().severe("Slime reload failed: " + t);
                    sender.sendMessage("Slime reload failed: " + t.getMessage());
                }
            }
            case "check" -> {
                String[] checkArgs = sliceFromOne(args);
                slime.handleCheck(sender, checkArgs);
            }
            case "find" -> slime.handleFind(sender);
            case "shuffle-salt" -> {
                boolean confirmed = args.length >= 3 && "confirm".equalsIgnoreCase(args[2]);
                slime.handleShuffleSalt(sender, confirmed);
            }
            default -> sender.sendMessage("Usage: /" + label + " slime <reload|check|find|shuffle-salt>");
        }
    }

    /** Returns args without the leading "slime" token, so handleCheck sees [check, x?, z?]. */
    private static String[] sliceFromOne(String[] args) {
        String[] out = new String[args.length - 1];
        System.arraycopy(args, 1, out, 0, out.length);
        return out;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("antiseedcracker.admin")) return List.of();
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], ROOT_SUBCOMMANDS, new ArrayList<>());
        }
        if (args.length == 2 && "slime".equalsIgnoreCase(args[0])) {
            return StringUtil.copyPartialMatches(args[1], SLIME_SUBCOMMANDS, new ArrayList<>());
        }
        if (args.length == 3 && "slime".equalsIgnoreCase(args[0])
                && "shuffle-salt".equalsIgnoreCase(args[1])) {
            return StringUtil.copyPartialMatches(args[2], List.of("confirm"), new ArrayList<>());
        }
        return List.of();
    }
}
