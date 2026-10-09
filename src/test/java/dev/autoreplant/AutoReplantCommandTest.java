package dev.autoreplant;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Characterization tests：記錄 /autoreplant 指令目前的行為。 */
class AutoReplantCommandTest {

    private AutoReplantPlugin plugin;
    private AutoReplantCommand command;
    private Player player;
    private final Command cmd = mock(Command.class);

    @BeforeEach
    void setUp() {
        plugin = mock(AutoReplantPlugin.class);
        // 以 "key|label" 形式回傳，方便斷言送出的是哪一則訊息
        when(plugin.getMessage(anyString())).thenAnswer(inv -> Component.text(inv.getArgument(0) + "|"));
        when(plugin.getMessage(anyString(), anyString()))
                .thenAnswer(inv -> Component.text(inv.getArgument(0) + "|" + inv.getArgument(1)));
        command = new AutoReplantCommand(plugin);
        player = mock(Player.class);
        when(player.hasPermission("autoreplant.use")).thenReturn(true);
    }

    private boolean run(CommandSender sender, String label, String... args) {
        return command.onCommand(sender, cmd, label, args);
    }

    @Test
    void consoleIsDenied() {
        CommandSender console = mock(CommandSender.class);
        assertTrue(run(console, "autoreplant"));
        verify(console).sendMessage(Component.text("console-denied|"));
        verify(plugin, never()).setAutoReplant(any(), anyBoolean());
    }

    @Test
    void noUsePermission() {
        when(player.hasPermission("autoreplant.use")).thenReturn(false);
        assertTrue(run(player, "arp", "on"));
        verify(player).sendMessage(Component.text("no-permission|"));
        verify(plugin, never()).setAutoReplant(any(), anyBoolean());
    }

    @Test
    void noArgsTogglesFromEnabledToDisabled() {
        when(plugin.isAutoReplantEnabled(player)).thenReturn(true);
        assertTrue(run(player, "arp"));
        verify(plugin).setAutoReplant(player, false);
        verify(player).sendMessage(Component.text("disabled|arp"));
    }

    @Test
    void noArgsTogglesFromDisabledToEnabled() {
        when(plugin.isAutoReplantEnabled(player)).thenReturn(false);
        assertTrue(run(player, "autoreplant"));
        verify(plugin).setAutoReplant(player, true);
        verify(player).sendMessage(Component.text("enabled|autoreplant"));
    }

    @Test
    void onAndOffAreCaseInsensitive() {
        run(player, "arp", "ON");
        verify(plugin).setAutoReplant(player, true);
        verify(player).sendMessage(Component.text("enabled|arp"));

        run(player, "arp", "oFf");
        verify(plugin).setAutoReplant(player, false);
        verify(player).sendMessage(Component.text("disabled|arp"));
    }

    @Test
    void tooManyArgsShowsUsage() {
        assertTrue(run(player, "arp", "on", "extra"));
        verify(player).sendMessage(Component.text("usage|arp"));
        verify(plugin, never()).setAutoReplant(any(), anyBoolean());
    }

    @Test
    void unknownArgShowsUsage() {
        assertTrue(run(player, "arp", "toggle"));
        verify(player).sendMessage(Component.text("usage|arp"));
        verify(plugin, never()).setAutoReplant(any(), anyBoolean());
    }

    @Test
    void reloadRequiresReloadPermission() {
        assertTrue(run(player, "arp", "reload"));
        verify(player).sendMessage(Component.text("no-permission|"));
        verify(plugin, never()).reload();
    }

    @Test
    void reloadWithPermission() {
        when(player.hasPermission("autoreplant.reload")).thenReturn(true);
        assertTrue(run(player, "arp", "reload"));
        verify(plugin).reload();
        verify(player).sendMessage(Component.text("reload-success|arp"));
    }

    @Test
    void tabCompletion() {
        assertEquals(List.of("on", "off", "reload"), command.onTabComplete(player, cmd, "arp", new String[]{""}));
        assertEquals(List.of("on", "off"), command.onTabComplete(player, cmd, "arp", new String[]{"O"}));
        assertEquals(List.of("reload"), command.onTabComplete(player, cmd, "arp", new String[]{"re"}));
        assertEquals(List.of(), command.onTabComplete(player, cmd, "arp", new String[]{"x"}));
        assertEquals(List.of(), command.onTabComplete(player, cmd, "arp", new String[]{"on", ""}));
        assertEquals(List.of(), command.onTabComplete(player, cmd, "arp", new String[]{}));
    }

    @Test
    void tabCompletionIgnoresPermissions() {
        Player noPerm = mock(Player.class);
        assertEquals(List.of("on", "off", "reload"), command.onTabComplete(noPerm, cmd, "arp", new String[]{""}));
    }
}
