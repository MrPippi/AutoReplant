package dev.autoreplant;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;

public class AutoReplantCommand implements CommandExecutor, TabCompleter {

    private final AutoReplantPlugin plugin;

    public AutoReplantCommand(AutoReplantPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // 僅限玩家使用
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessage("console-denied"));
            return true;
        }

        // 權限檢查
        if (!player.hasPermission("autoreplant.use")) {
            player.sendMessage(plugin.getMessage("no-permission"));
            return true;
        }

        // 需要剛好一個子指令
        if (args.length != 1) {
            player.sendMessage(plugin.getMessage("usage", label));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "on" -> {
                plugin.setAutoReplant(player, true);
                player.sendMessage(plugin.getMessage("enabled", label));
            }
            case "off" -> {
                plugin.setAutoReplant(player, false);
                player.sendMessage(plugin.getMessage("disabled", label));
            }
            default -> player.sendMessage(plugin.getMessage("usage", label));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return List.of("on", "off").stream()
                    .filter(s -> s.startsWith(input))
                    .toList();
        }
        return List.of();
    }
}
