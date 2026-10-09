package dev.autoreplant;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Characterization tests：記錄 AutoReplantPlugin 目前的行為。 */
class AutoReplantPluginTest {

    private static Player player(UUID uuid) {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(uuid);
        return p;
    }

    // ─── 設定值 ────────────────────────────────────────────────────────────────

    @Test
    void bundledConfigDefaults() {
        AutoReplantPlugin plugin = TestPlugins.realPlugin(TestPlugins.bundledConfig());
        assertTrue(plugin.isAutoReplantEnabled(UUID.randomUUID()));
        assertTrue(plugin.isCheckSeedsEnabled());
        assertTrue(plugin.isBoneMealAutoReplantEnabled());
    }

    @Test
    void missingConfigKeysFallBackToTrue() {
        AutoReplantPlugin plugin = TestPlugins.realPlugin(new YamlConfiguration());
        assertTrue(plugin.isAutoReplantEnabled(UUID.randomUUID()));
        assertTrue(plugin.isCheckSeedsEnabled());
        assertTrue(plugin.isBoneMealAutoReplantEnabled());
    }

    @Test
    void configValuesAreRead() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("default-enabled", false);
        cfg.set("check-seeds", false);
        cfg.set("bone-meal-auto-replant", false);
        AutoReplantPlugin plugin = TestPlugins.realPlugin(cfg);
        assertFalse(plugin.isAutoReplantEnabled(UUID.randomUUID()));
        assertFalse(plugin.isCheckSeedsEnabled());
        assertFalse(plugin.isBoneMealAutoReplantEnabled());
    }

    // ─── 玩家狀態 ──────────────────────────────────────────────────────────────

    @Test
    @SuppressWarnings("unchecked")
    void setAutoReplantStoresOnlyOverridesThatDifferFromDefault() {
        AutoReplantPlugin plugin = TestPlugins.realPlugin(new YamlConfiguration()); // default true
        UUID uuid = UUID.randomUUID();
        Player p = player(uuid);
        Map<UUID, Boolean> states = (Map<UUID, Boolean>) TestPlugins.getField(plugin, "playerStates");

        plugin.setAutoReplant(p, false);
        assertFalse(plugin.isAutoReplantEnabled(p));
        assertEquals(Map.of(uuid, false), states);

        plugin.setAutoReplant(p, true);
        assertTrue(plugin.isAutoReplantEnabled(p));
        assertTrue(states.isEmpty(), "matching the default removes the override");
    }

    @Test
    void overridesSurviveChangeOfDefault() {
        YamlConfiguration cfg = new YamlConfiguration();
        AutoReplantPlugin plugin = TestPlugins.realPlugin(cfg);
        Player off = player(UUID.randomUUID());
        Player untouched = player(UUID.randomUUID());
        plugin.setAutoReplant(off, false);

        cfg.set("default-enabled", false);
        TestPlugins.invoke(plugin, "loadConfigValues");

        assertFalse(plugin.isAutoReplantEnabled(off));
        assertFalse(plugin.isAutoReplantEnabled(untouched));
    }

    @Test
    void nullOfflinePlayerIsDisabled() {
        AutoReplantPlugin plugin = TestPlugins.realPlugin(new YamlConfiguration());
        assertFalse(plugin.isAutoReplantEnabled((OfflinePlayer) null));
    }

    @Test
    void offlinePlayerLooksUpByUuid() {
        AutoReplantPlugin plugin = TestPlugins.realPlugin(new YamlConfiguration());
        UUID uuid = UUID.randomUUID();
        plugin.setAutoReplant(player(uuid), false);
        OfflinePlayer offline = mock(OfflinePlayer.class);
        when(offline.getUniqueId()).thenReturn(uuid);
        assertFalse(plugin.isAutoReplantEnabled(offline));
    }

    // ─── 訊息格式 ──────────────────────────────────────────────────────────────

    @Test
    void translateLegacyConvertsColorAndFormatCodes() {
        assertEquals("<green>Hello <bold>World", TestPlugins.translateLegacy("&aHello &lWorld"));
        assertEquals("<green>Hi<bold>!", TestPlugins.translateLegacy("&AHi&L!"));
        assertEquals("<#00ff00>RGB!", TestPlugins.translateLegacy("&#00ff00RGB!"));
        assertEquals("<yellow>Hi", TestPlugins.translateLegacy("<yellow>Hi"));
        assertEquals("<reset><obfuscated><strikethrough><underlined><italic>",
                TestPlugins.translateLegacy("&r&k&m&n&o"));
    }

    @Test
    void translateLegacyLeavesUnknownCodesAlone() {
        assertEquals("&z &g", TestPlugins.translateLegacy("&z &g"));
        // 不足六位的十六進位不轉換
        assertEquals("&#12345", TestPlugins.translateLegacy("&#12345"));
    }

    @Test
    void getMessagePrependsPrefixAndInsertsCommandAsPlainText() {
        AutoReplantPlugin plugin = TestPlugins.realPlugin(TestPlugins.bundledConfig());
        var plain = PlainTextComponentSerializer.plainText();

        assertEquals("[AutoReplant] 用法: /arp [on|off|reload]",
                plain.serialize(plugin.getMessage("usage", "arp")));
        assertEquals("[AutoReplant] 自動回種植 開啟", plain.serialize(plugin.getMessage("enabled")));
        // <command> 以純文字插入，不會被再次解析
        assertEquals("[AutoReplant] 用法: /<red>x [on|off|reload]",
                plain.serialize(plugin.getMessage("usage", "<red>x")));
    }

    @Test
    void getMessageWithUnknownKeyReturnsOnlyPrefix() {
        AutoReplantPlugin plugin = TestPlugins.realPlugin(TestPlugins.bundledConfig());
        assertEquals("[AutoReplant] ",
                PlainTextComponentSerializer.plainText().serialize(plugin.getMessage("does-not-exist")));
    }

    @Test
    void getMessageDefaultCommandLabelIsAutoreplant() {
        AutoReplantPlugin plugin = TestPlugins.realPlugin(TestPlugins.bundledConfig());
        assertEquals("[AutoReplant] 用法: /autoreplant [on|off|reload]",
                PlainTextComponentSerializer.plainText().serialize(plugin.getMessage("usage")));
    }
}
