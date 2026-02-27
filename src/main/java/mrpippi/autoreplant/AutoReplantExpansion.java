package dev.autoreplant;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/**
 * PlaceholderAPI 擴展，提供 %autoreplant_status%（ON/OFF）變數。
 */
public class AutoReplantExpansion extends PlaceholderExpansion {

    private final AutoReplantPlugin plugin;

    public AutoReplantExpansion(AutoReplantPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getIdentifier() {
        return "autoreplant";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if ("status".equalsIgnoreCase(params)) {
            return plugin.isAutoReplantEnabled(player) ? "ON" : "OFF";
        }
        return null;
    }
}
