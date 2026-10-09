package dev.autoreplant;

import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Characterization tests：記錄 PlaceholderAPI 擴展 %autoreplant_status% 目前的行為。 */
class AutoReplantExpansionTest {

    private final AutoReplantPlugin plugin = mock(AutoReplantPlugin.class);
    private final AutoReplantExpansion expansion = new AutoReplantExpansion(plugin);
    private final OfflinePlayer player = mock(OfflinePlayer.class);

    /** 以打包（已 filtering）的 plugin.yml 建立描述檔。 */
    private static PluginDescriptionFile bundledDescription() throws Exception {
        var in = AutoReplantExpansionTest.class.getClassLoader().getResourceAsStream("plugin.yml");
        return new PluginDescriptionFile(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    @Test
    void authorAndVersionComeFromPluginYml() throws Exception {
        PluginDescriptionFile description = bundledDescription();
        // PluginDescriptionFile 同時實作 PluginMeta
        when(plugin.getPluginMeta()).thenReturn(description);

        assertEquals("MrPippi", expansion.getAuthor());
        assertEquals("2.0.0", expansion.getVersion());
    }

    @Test
    void identifierAndPersist() {
        assertEquals("autoreplant", expansion.getIdentifier());
        assertTrue(expansion.persist());
    }

    @Test
    void statusReflectsPluginState() {
        when(plugin.isAutoReplantEnabled(player)).thenReturn(true);
        assertEquals("ON", expansion.onRequest(player, "status"));
        assertEquals("ON", expansion.onRequest(player, "STATUS"));

        when(plugin.isAutoReplantEnabled(player)).thenReturn(false);
        assertEquals("OFF", expansion.onRequest(player, "status"));
    }

    @Test
    void nullPlayerDelegatesToPlugin() {
        // 插件本身對 null 回傳 false（見 AutoReplantPluginTest），此處只記錄委派行為
        assertEquals("OFF", expansion.onRequest(null, "status"));
    }

    @Test
    void unknownParamReturnsNull() {
        assertNull(expansion.onRequest(player, "foo"));
        assertNull(expansion.onRequest(player, ""));
    }
}
