package dev.autoreplant;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

public class AutoReplantPlugin extends JavaPlugin {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    // Stores per-player overrides. If UUID is absent, the default applies.
    private final Map<UUID, Boolean> playerStates = new HashMap<>();
    private boolean defaultEnabled;

    private File dataFile;
    private YamlConfiguration dataConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        defaultEnabled = getConfig().getBoolean("default-enabled", true);

        dataFile = new File(getDataFolder(), "players.yml");
        loadPlayerData();

        getServer().getPluginManager().registerEvents(new AutoReplantListener(this), this);

        AutoReplantCommand commandHandler = new AutoReplantCommand(this);
        Objects.requireNonNull(getCommand("autoreplant")).setExecutor(commandHandler);
        Objects.requireNonNull(getCommand("autoreplant")).setTabCompleter(commandHandler);

        getLogger().info("AutoReplant v" + getDescription().getVersion() + " 已啟動！");
    }

    @Override
    public void onDisable() {
        savePlayerData();
        getLogger().info("AutoReplant 已停用。");
    }

    // ─── Player state ────────────────────────────────────────────────────────

    public boolean isAutoReplantEnabled(Player player) {
        return playerStates.getOrDefault(player.getUniqueId(), defaultEnabled);
    }

    public void setAutoReplant(Player player, boolean enabled) {
        UUID uuid = player.getUniqueId();
        if (enabled == defaultEnabled) {
            // Matches the default — no need to store an override
            playerStates.remove(uuid);
        } else {
            playerStates.put(uuid, enabled);
        }
    }

    // ─── Persistence ─────────────────────────────────────────────────────────

    private void loadPlayerData() {
        dataConfig = dataFile.exists()
                ? YamlConfiguration.loadConfiguration(dataFile)
                : new YamlConfiguration();

        var section = dataConfig.getConfigurationSection("players");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                playerStates.put(uuid, section.getBoolean(key));
            } catch (IllegalArgumentException e) {
                getLogger().warning("players.yml 中有無效的 UUID：" + key);
            }
        }
    }

    private void savePlayerData() {
        if (dataConfig == null) dataConfig = new YamlConfiguration();

        // Clear old data and rewrite
        dataConfig.set("players", null);
        playerStates.forEach((uuid, state) ->
                dataConfig.set("players." + uuid, state));

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "無法儲存 players.yml", e);
        }
    }

    // ─── Messaging ───────────────────────────────────────────────────────────

    /**
     * Returns a coloured Adventure Component using & codes from config.
     *
     * @param key     message key under messages.* in config.yml
     * @param command the command label used (replaces {@code <command>} placeholder)
     */
    public Component getMessage(String key, String command) {
        String prefix = getConfig().getString("messages.prefix", "&8[&aAutoReplant&8] ");
        String msg = getConfig().getString("messages." + key, "");
        String full = (prefix + msg).replace("<command>", command);
        return LEGACY.deserialize(full);
    }

    /** Convenience overload without a command label. */
    public Component getMessage(String key) {
        return getMessage(key, "autoreplant");
    }
}
