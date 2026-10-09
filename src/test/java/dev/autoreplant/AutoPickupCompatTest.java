package dev.autoreplant;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Characterization tests：記錄 AutoPickupCompat（反射呼叫 AutoPickup API）目前的行為。 */
class AutoPickupCompatTest {

    /** 模擬 AutoPickup 的公開 API 形狀：getStateManager() / getFilterManager()。 */
    public abstract static class FakeAutoPickup implements Plugin {
        public abstract Object getStateManager();
        public abstract Object getFilterManager();
    }

    public static class FakeStateManager {
        final Set<UUID> enabled = new java.util.HashSet<>();
        public boolean isEnabled(UUID uuid) { return enabled.contains(uuid); }
    }

    public static class FakeFilterManager {
        final Set<Material> blocked = new java.util.HashSet<>();
        public boolean shouldPickup(UUID uuid, Material material) { return !blocked.contains(material); }
    }

    private Plugin host;
    private PluginManager pluginManager;
    private FakeAutoPickup autoPickup;
    private FakeStateManager states;
    private FakeFilterManager filters;
    private Player player;
    private PlayerInventory inventory;
    private World world;
    private Location dropLoc;

    @BeforeEach
    void setUp() {
        host = mock(Plugin.class);
        Server server = mock(Server.class);
        pluginManager = mock(PluginManager.class);
        when(host.getServer()).thenReturn(server);
        when(host.getLogger()).thenReturn(Logger.getLogger("test"));
        when(server.getPluginManager()).thenReturn(pluginManager);

        states = new FakeStateManager();
        filters = new FakeFilterManager();
        autoPickup = mock(FakeAutoPickup.class);
        when(autoPickup.getStateManager()).thenReturn(states);
        when(autoPickup.getFilterManager()).thenReturn(filters);
        when(autoPickup.isEnabled()).thenReturn(true);

        player = mock(Player.class);
        inventory = mock(PlayerInventory.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.addItem(any(ItemStack[].class))).thenReturn(new HashMap<>());

        world = mock(World.class);
        dropLoc = new Location(world, 0.5, 64.5, 0.5);
    }

    private static ItemStack stack(Material type, int amount) {
        ItemStack s = mock(ItemStack.class);
        when(s.getType()).thenReturn(type);
        when(s.getAmount()).thenReturn(amount);
        return s;
    }

    private AutoPickupCompat create() {
        when(pluginManager.getPlugin("AutoPickup")).thenReturn(autoPickup);
        return AutoPickupCompat.create(host);
    }

    @Test
    void createReturnsNullForNullHost() {
        assertNull(AutoPickupCompat.create(null));
    }

    @Test
    void createReturnsNullWhenAutoPickupMissing() {
        assertNull(AutoPickupCompat.create(host));
    }

    @Test
    void createReturnsNullWhenApiShapeDoesNotMatch() {
        when(pluginManager.getPlugin("AutoPickup")).thenReturn(mock(Plugin.class));
        assertNull(AutoPickupCompat.create(host));
    }

    @Test
    void createReturnsNullWhenManagersAreNull() {
        when(autoPickup.getStateManager()).thenReturn(null);
        assertNull(create());
    }

    @Test
    void availabilityFollowsAutoPickupEnabledState() {
        AutoPickupCompat compat = create();
        assertNotNull(compat);
        assertTrue(compat.isAvailable());
        when(autoPickup.isEnabled()).thenReturn(false);
        assertFalse(compat.isAvailable());
    }

    @Test
    void isEnabledForDelegatesToStateManager() {
        AutoPickupCompat compat = create();
        assertFalse(compat.isEnabledFor(player));
        states.enabled.add(player.getUniqueId());
        assertTrue(compat.isEnabledFor(player));
        assertFalse(compat.isEnabledFor(null));
    }

    @Test
    void shouldPickupDelegatesToFilterAndDefaultsToTrue() {
        AutoPickupCompat compat = create();
        assertTrue(compat.shouldPickup(player, Material.WHEAT));
        filters.blocked.add(Material.WHEAT);
        assertFalse(compat.shouldPickup(player, Material.WHEAT));
        assertTrue(compat.shouldPickup(null, Material.WHEAT));
        assertTrue(compat.shouldPickup(player, null));
    }

    @Test
    void giveDropsAddsToInventoryWhenEnabledAndAllowed() {
        states.enabled.add(player.getUniqueId());
        AutoPickupCompat compat = create();
        ItemStack wheat = stack(Material.WHEAT, 1);

        compat.giveDropsToPlayer(player, List.of(wheat), dropLoc);

        verify(inventory).addItem(wheat);
        verify(world, never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
    }

    @Test
    void giveDropsDropsOverflowAtLocation() {
        states.enabled.add(player.getUniqueId());
        AutoPickupCompat compat = create();
        ItemStack wheat = stack(Material.WHEAT, 64);
        ItemStack overflow = stack(Material.WHEAT, 10);
        when(inventory.addItem(wheat)).thenReturn(new HashMap<>(Map.of(0, overflow)));

        compat.giveDropsToPlayer(player, List.of(wheat), dropLoc);

        verify(world).dropItemNaturally(dropLoc, overflow);
    }

    @Test
    void giveDropsDropsFilteredItemsOnGround() {
        states.enabled.add(player.getUniqueId());
        filters.blocked.add(Material.WHEAT_SEEDS);
        AutoPickupCompat compat = create();
        ItemStack wheat = stack(Material.WHEAT, 1);
        ItemStack seeds = stack(Material.WHEAT_SEEDS, 2);

        compat.giveDropsToPlayer(player, List.of(wheat, seeds), dropLoc);

        verify(inventory).addItem(wheat);
        verify(inventory, never()).addItem(seeds);
        verify(world).dropItemNaturally(dropLoc, seeds);
    }

    @Test
    void giveDropsDropsEverythingWhenPlayerHasAutoPickupOff() {
        AutoPickupCompat compat = create();
        ItemStack wheat = stack(Material.WHEAT, 1);

        compat.giveDropsToPlayer(player, List.of(wheat), dropLoc);

        verify(inventory, never()).addItem(any(ItemStack[].class));
        verify(world).dropItemNaturally(dropLoc, wheat);
    }

    @Test
    void giveDropsSkipsAirAndEmptyStacks() {
        AutoPickupCompat compat = create();
        ItemStack air = stack(Material.AIR, 1);
        ItemStack empty = stack(Material.WHEAT, 0);
        java.util.ArrayList<ItemStack> stacks = new java.util.ArrayList<>();
        stacks.add(null);
        stacks.add(air);
        stacks.add(empty);

        compat.giveDropsToPlayer(player, stacks, dropLoc);

        verify(world, never()).dropItemNaturally(any(Location.class), any(ItemStack.class));
    }
}
