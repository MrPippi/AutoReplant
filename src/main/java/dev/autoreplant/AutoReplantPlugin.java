package dev.autoreplant;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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

    private static final MiniMessage MM = MiniMessage.miniMessage();

    // Stores per-player overrides. If UUID is absent, the default applies.
    private final Map<UUID, Boolean> playerStates = new HashMap<>();
    private boolean defaultEnabled;
    private boolean checkSeeds;

    private File dataFile;
    private YamlConfiguration dataConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        defaultEnabled = getConfig().getBoolean("default-enabled", true);
        checkSeeds     = getConfig().getBoolean("check-seeds", true);

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

    public boolean isCheckSeedsEnabled() {
        return checkSeeds;
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
     * 取得格式化後的訊息 Component。
     *
     * <p>支援兩種語法（可混用）：
     * <ul>
     *   <li>MiniMessage 標籤：{@code <green>}, {@code <bold>}, {@code <#ff0000>},
     *       {@code <gradient:#ff0000:#0000ff>} 等
     *   <li>傳統 &amp; 代碼：{@code &a}, {@code &l}, {@code &#00ff00} 等（大小寫均可）
     * </ul>
     *
     * <p>{@code <command>} 佔位符會被實際使用的指令名稱取代，
     * 以純文字方式插入（不會再次被 MiniMessage 解析）。
     *
     * @param key     config.yml 中 messages.* 底下的鍵名
     * @param command 玩家實際輸入的指令名稱（label）
     */
    public Component getMessage(String key, String command) {
        String prefix = getConfig().getString("messages.prefix", "");
        String msg    = getConfig().getString("messages." + key, "");
        String raw    = translateLegacy(prefix + msg);
        return MM.deserialize(raw, Placeholder.component("command", Component.text(command)));
    }

    /** 不帶指令名稱的便捷多載（使用 "autoreplant" 作為預設值）。 */
    public Component getMessage(String key) {
        return getMessage(key, "autoreplant");
    }

    /**
     * 將傳統 &amp; 顏色代碼預處理為等效的 MiniMessage 標籤，
     * 使兩種語法得以在同一字串中混用。
     *
     * <p>轉換範例：
     * <pre>
     *   "&aHello &lWorld"    → "&lt;green&gt;Hello &lt;bold&gt;World"
     *   "&#00ff00RGB!"       → "&lt;#00ff00&gt;RGB!"
     *   "&lt;yellow&gt;Hi"   → 原樣保留（已是 MiniMessage）
     * </pre>
     *
     * @param text 原始字串
     * @return 已將 &amp; 代碼轉換為 MiniMessage 標籤的字串
     */
    private static String translateLegacy(String text) {
        // &#RRGGBB → <#RRGGBB>（舊式十六進位顏色代碼）
        text = text.replaceAll("&#([0-9a-fA-F]{6})", "<#$1>");

        // 標準顏色與格式代碼（大小寫均支援）
        String[][] map = {
            {"&0", "<black>"},        {"&1", "<dark_blue>"},    {"&2", "<dark_green>"},
            {"&3", "<dark_aqua>"},    {"&4", "<dark_red>"},     {"&5", "<dark_purple>"},
            {"&6", "<gold>"},         {"&7", "<gray>"},         {"&8", "<dark_gray>"},
            {"&9", "<blue>"},         {"&a", "<green>"},        {"&b", "<aqua>"},
            {"&c", "<red>"},          {"&d", "<light_purple>"}, {"&e", "<yellow>"},
            {"&f", "<white>"},        {"&k", "<obfuscated>"},   {"&l", "<bold>"},
            {"&m", "<strikethrough>"}, {"&n", "<underlined>"},  {"&o", "<italic>"},
            {"&r", "<reset>"}
        };

        for (String[] entry : map) {
            // 先取代小寫，再取代大寫（如 &a 與 &A）
            text = text.replace(entry[0], entry[1])
                       .replace(entry[0].toUpperCase(), entry[1]);
        }
        return text;
    }
}
