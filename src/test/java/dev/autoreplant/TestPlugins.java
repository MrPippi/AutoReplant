package dev.autoreplant;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

/**
 * 建立「執行真實方法」的 AutoReplantPlugin，不需啟動伺服器。
 * JavaPlugin 的建構子需要 PluginClassLoader，因此以 Mockito 建立實例，
 * 再手動補上建構時才會初始化的欄位，並將 getConfig() 指向打包的 config.yml。
 */
final class TestPlugins {

    private TestPlugins() {}

    static AutoReplantPlugin realPlugin(YamlConfiguration config) {
        AutoReplantPlugin plugin = mock(AutoReplantPlugin.class, withSettings().defaultAnswer(CALLS_REAL_METHODS));
        setField(plugin, "playerStates", new HashMap<>());
        doReturn(config).when(plugin).getConfig();
        invoke(plugin, "loadConfigValues");
        return plugin;
    }

    static YamlConfiguration bundledConfig() {
        var in = TestPlugins.class.getClassLoader().getResourceAsStream("config.yml");
        if (in == null) throw new IllegalStateException("config.yml not on classpath");
        return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
    }

    static void setField(Object target, String name, Object value) {
        try {
            Field f = AutoReplantPlugin.class.getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static Object getField(Object target, String name) {
        try {
            Field f = AutoReplantPlugin.class.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static Object invoke(Object target, String name) {
        try {
            Method m = AutoReplantPlugin.class.getDeclaredMethod(name);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static String translateLegacy(String text) {
        try {
            Method m = AutoReplantPlugin.class.getDeclaredMethod("translateLegacy", String.class);
            m.setAccessible(true);
            return (String) m.invoke(null, text);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
