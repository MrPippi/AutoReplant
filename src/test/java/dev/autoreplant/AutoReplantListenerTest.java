package dev.autoreplant;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Characterization tests：記錄 AutoReplantListener 目前的行為。
 * 排程器會立即同步執行 runTask 的 Runnable，以便觀察「下一 tick」的結果。
 */
class AutoReplantListenerTest {

    private AutoReplantPlugin plugin;
    private BukkitScheduler scheduler;
    private World world;
    private Player player;
    private PlayerInventory inventory;
    private AutoReplantListener listener;

    /** 作物所在的方塊（破壞 / 骨粉事件中的方塊）。 */
    private Block crop;
    /** 排程任務中透過 Location#getBlock() 取得的方塊（同一位置）。 */
    private Block target;
    private Block below;

    @BeforeEach
    void setUp() {
        plugin = mock(AutoReplantPlugin.class);
        Server server = mock(Server.class);
        scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTask(any(Plugin.class), any(Runnable.class))).thenAnswer(inv -> {
            ((Runnable) inv.getArgument(1)).run();
            return null;
        });

        world = mock(World.class);
        when(world.getUID()).thenReturn(UUID.fromString("00000000-0000-0000-0000-000000000001"));

        crop = mock(Block.class);
        when(crop.getLocation()).thenAnswer(inv -> new Location(world, 10, 64, -3));
        target = mock(Block.class);
        below = mock(Block.class);
        when(target.getRelative(BlockFace.DOWN)).thenReturn(below);
        when(crop.getRelative(BlockFace.DOWN)).thenReturn(below);
        when(target.getWorld()).thenReturn(world);
        when(world.getBlockAt(any(Location.class))).thenReturn(target);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(target);

        player = mock(Player.class);
        inventory = mock(PlayerInventory.class);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getInventory()).thenReturn(inventory);
        when(player.isOnline()).thenReturn(true);
        when(inventory.getSize()).thenReturn(36);

        when(plugin.isAutoReplantEnabled(player)).thenReturn(true);
        when(plugin.isCheckSeedsEnabled()).thenReturn(false);
        when(plugin.isBoneMealAutoReplantEnabled()).thenReturn(true);

        listener = new AutoReplantListener(plugin);
    }

    // ─── 測試工具 ──────────────────────────────────────────────────────────────

    private static Ageable ageable(int age, int max) {
        Ageable a = mock(Ageable.class);
        when(a.getAge()).thenReturn(age);
        when(a.getMaximumAge()).thenReturn(max);
        return a;
    }

    private void cropIs(Material type, int age) {
        Ageable data = ageable(age, type == Material.NETHER_WART ? 3 : 7);
        when(crop.getType()).thenReturn(type);
        when(crop.getBlockData()).thenReturn(data);
    }

    /** 可追蹤數量的假 ItemStack（真正的 ItemStack 需要伺服器實作）。 */
    private static ItemStack stack(Material type, int amount) {
        ItemStack s = mock(ItemStack.class);
        int[] amt = {amount};
        when(s.getType()).thenReturn(type);
        when(s.getAmount()).thenAnswer(inv -> amt[0]);
        org.mockito.Mockito.doAnswer(inv -> { amt[0] = inv.getArgument(0); return null; })
                .when(s).setAmount(anyInt());
        when(s.clone()).thenAnswer(inv -> stack(type, amt[0]));
        return s;
    }

    private static Item itemEntity(ItemStack s) {
        Item item = mock(Item.class);
        when(item.getItemStack()).thenReturn(s);
        return item;
    }

    private BlockBreakEvent breakCrop() {
        BlockBreakEvent e = new BlockBreakEvent(crop, player);
        listener.onBlockBreak(e);
        return e;
    }

    private BlockDropItemEvent dropFromCrop(List<Item> items) {
        BlockDropItemEvent e = new BlockDropItemEvent(crop, mock(BlockState.class), player, items);
        listener.onBlockDropItem(e);
        return e;
    }

    private void groundReady(Material base) {
        when(target.getType()).thenReturn(Material.AIR);
        when(below.getType()).thenReturn(base);
    }

    // ─── BlockBreakEvent ──────────────────────────────────────────────────────

    @Test
    void immatureCropIsProtectedWhenEnabled() {
        cropIs(Material.WHEAT, 3);
        assertTrue(breakCrop().isCancelled());
    }

    @Test
    void immatureCropCanBeBrokenWhenDisabled() {
        when(plugin.isAutoReplantEnabled(player)).thenReturn(false);
        cropIs(Material.WHEAT, 3);
        assertFalse(breakCrop().isCancelled());
        dropFromCrop(new ArrayList<>());
        verify(scheduler, never()).runTask(any(Plugin.class), any(Runnable.class));
    }

    @Test
    void creativeModeIsIgnoredEvenForImmatureCrops() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        cropIs(Material.WHEAT, 3);
        assertFalse(breakCrop().isCancelled());
    }

    @Test
    void unsupportedBlocksAreIgnored() {
        // 例如甘蔗、可可豆、火炬花等都不在支援清單中
        for (Material m : List.of(Material.SUGAR_CANE, Material.COCOA, Material.TORCHFLOWER_CROP,
                Material.PITCHER_CROP, Material.SWEET_BERRY_BUSH)) {
            Ageable data = ageable(0, 3);
            when(crop.getType()).thenReturn(m);
            when(crop.getBlockData()).thenReturn(data);
            assertFalse(breakCrop().isCancelled(), m.name());
        }
        dropFromCrop(new ArrayList<>());
        verify(scheduler, never()).runTask(any(Plugin.class), any(Runnable.class));
    }

    @Test
    void nonAgeableBlockDataIsIgnored() {
        when(crop.getType()).thenReturn(Material.WHEAT);
        BlockData data = mock(BlockData.class);
        when(crop.getBlockData()).thenReturn(data);
        assertFalse(breakCrop().isCancelled());
    }

    // ─── 成熟作物破壞 → 回種 ────────────────────────────────────────────────────

    @Test
    void matureWheatIsReplantedWithoutSeedCheck() {
        cropIs(Material.WHEAT, 7);
        groundReady(Material.FARMLAND);

        assertFalse(breakCrop().isCancelled());
        dropFromCrop(new ArrayList<>());

        verify(target).setType(Material.WHEAT, false);
        verify(player).spawnParticle(eq(Particle.HAPPY_VILLAGER), any(Location.class),
                eq(8), eq(0.25), eq(0.25), eq(0.25), eq(0.02));
    }

    @Test
    void allSupportedCropsReplantAsThemselves() {
        groundReady(Material.FARMLAND);
        for (Material m : List.of(Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS)) {
            cropIs(m, 7);
            breakCrop();
            dropFromCrop(new ArrayList<>());
            verify(target).setType(m, false);
        }
    }

    @Test
    void beetrootsMaxAgeIsReadFromBlockData() {
        // 實際遊戲中甜菜根最大 age 為 3；程式只比較 getAge() 與 getMaximumAge()
        groundReady(Material.FARMLAND);
        Ageable data = ageable(3, 3);
        when(crop.getType()).thenReturn(Material.BEETROOTS);
        when(crop.getBlockData()).thenReturn(data);
        breakCrop();
        dropFromCrop(new ArrayList<>());
        verify(target).setType(Material.BEETROOTS, false);
    }

    @Test
    void netherWartNeedsSoulSand() {
        cropIs(Material.NETHER_WART, 3);
        groundReady(Material.SOUL_SAND);
        breakCrop();
        dropFromCrop(new ArrayList<>());
        verify(target).setType(Material.NETHER_WART, false);
    }

    @Test
    void netherWartOnFarmlandIsNotReplanted() {
        cropIs(Material.NETHER_WART, 3);
        groundReady(Material.FARMLAND);
        breakCrop();
        dropFromCrop(new ArrayList<>());
        verify(target, never()).setType(any(Material.class), anyBoolean());
    }

    @Test
    void wheatOnNonFarmlandIsNotReplanted() {
        cropIs(Material.WHEAT, 7);
        groundReady(Material.DIRT);
        breakCrop();
        dropFromCrop(new ArrayList<>());
        verify(target, never()).setType(any(Material.class), anyBoolean());
    }

    @Test
    void occupiedTargetIsNotReplanted() {
        cropIs(Material.WHEAT, 7);
        groundReady(Material.FARMLAND);
        when(target.getType()).thenReturn(Material.STONE);
        breakCrop();
        dropFromCrop(new ArrayList<>());
        verify(target, never()).setType(any(Material.class), anyBoolean());
    }

    @Test
    void offlinePlayerGetsReplantButNoParticle() {
        when(player.isOnline()).thenReturn(false);
        cropIs(Material.WHEAT, 7);
        groundReady(Material.FARMLAND);
        breakCrop();
        dropFromCrop(new ArrayList<>());
        verify(target).setType(Material.WHEAT, false);
        verify(player, never()).spawnParticle(any(Particle.class), any(Location.class),
                anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    void matureCropWithAutoReplantDisabledIsNotReplanted() {
        when(plugin.isAutoReplantEnabled(player)).thenReturn(false);
        cropIs(Material.WHEAT, 7);
        groundReady(Material.FARMLAND);
        breakCrop();
        dropFromCrop(new ArrayList<>());
        verify(scheduler, never()).runTask(any(Plugin.class), any(Runnable.class));
    }

    @Test
    void dropEventWithoutPriorBreakIsIgnored() {
        cropIs(Material.WHEAT, 7);
        dropFromCrop(new ArrayList<>());
        verify(scheduler, never()).runTask(any(Plugin.class), any(Runnable.class));
    }

    @Test
    void pendingReplantIsConsumedByFirstDropEventOnly() {
        cropIs(Material.WHEAT, 7);
        groundReady(Material.FARMLAND);
        breakCrop();
        dropFromCrop(new ArrayList<>());
        dropFromCrop(new ArrayList<>());
        verify(scheduler, times(1)).runTask(any(Plugin.class), any(Runnable.class));
    }

    @Test
    void laterBreakByDisabledPlayerClearsPendingReplant() {
        cropIs(Material.WHEAT, 7);
        breakCrop(); // 已啟用 → 進入佇列（假設此次破壞後續被取消，沒有 drop 事件）

        when(plugin.isAutoReplantEnabled(player)).thenReturn(false);
        breakCrop(); // 已關閉 → 清除該位置的佇列
        dropFromCrop(new ArrayList<>());
        verify(scheduler, never()).runTask(any(Plugin.class), any(Runnable.class));
    }

    // ─── check-seeds: true ─────────────────────────────────────────────────────

    @Test
    void seedIsTakenFromDropStackWithMultipleSeeds() {
        when(plugin.isCheckSeedsEnabled()).thenReturn(true);
        cropIs(Material.WHEAT, 7);
        groundReady(Material.FARMLAND);

        ItemStack wheat = stack(Material.WHEAT, 1);
        ItemStack seeds = stack(Material.WHEAT_SEEDS, 3);
        Item wheatItem = itemEntity(wheat);
        Item seedItem = itemEntity(seeds);
        List<Item> items = new ArrayList<>(List.of(wheatItem, seedItem));

        breakCrop();
        dropFromCrop(items);

        assertEquals(List.of(wheatItem, seedItem), items);
        assertEquals(2, seeds.getAmount());
        verify(seedItem).setItemStack(seeds);
        verify(inventory, never()).setItem(anyInt(), any());
        verify(target).setType(Material.WHEAT, false);
    }

    @Test
    void singleSeedDropEntityIsRemoved() {
        when(plugin.isCheckSeedsEnabled()).thenReturn(true);
        cropIs(Material.CARROTS, 7);
        groundReady(Material.FARMLAND);

        Item carrotItem = itemEntity(stack(Material.CARROT, 1));
        List<Item> items = new ArrayList<>(List.of(carrotItem));

        breakCrop();
        dropFromCrop(items);

        assertTrue(items.isEmpty());
        verify(target).setType(Material.CARROTS, false);
    }

    @Test
    void onlyOneSeedIsTakenFromFirstMatchingDrop() {
        when(plugin.isCheckSeedsEnabled()).thenReturn(true);
        cropIs(Material.POTATOES, 7);
        groundReady(Material.FARMLAND);

        ItemStack first = stack(Material.POTATO, 2);
        ItemStack second = stack(Material.POTATO, 2);
        List<Item> items = new ArrayList<>(List.of(itemEntity(first), itemEntity(second)));

        breakCrop();
        dropFromCrop(items);

        assertEquals(1, first.getAmount());
        assertEquals(2, second.getAmount());
    }

    @Test
    void seedIsTakenFromInventoryWhenDropsHaveNone() {
        when(plugin.isCheckSeedsEnabled()).thenReturn(true);
        cropIs(Material.WHEAT, 7);
        groundReady(Material.FARMLAND);

        ItemStack invSeeds = stack(Material.WHEAT_SEEDS, 5);
        ItemStack invSingle = stack(Material.WHEAT_SEEDS, 1);
        when(inventory.getItem(4)).thenReturn(invSeeds);
        when(inventory.getItem(2)).thenReturn(invSingle);

        breakCrop();
        dropFromCrop(new ArrayList<>(List.of(itemEntity(stack(Material.WHEAT, 1)))));

        // 依格子順序找到第一個符合者（slot 2，數量 1 → 清空）
        verify(inventory).setItem(2, null);
        assertEquals(5, invSeeds.getAmount());
        verify(target).setType(Material.WHEAT, false);
    }

    @Test
    void inventoryStackIsDecrementedWhenLargerThanOne() {
        when(plugin.isCheckSeedsEnabled()).thenReturn(true);
        cropIs(Material.BEETROOTS, 7);
        groundReady(Material.FARMLAND);

        ItemStack invSeeds = stack(Material.BEETROOT_SEEDS, 5);
        when(inventory.getItem(0)).thenReturn(invSeeds);

        breakCrop();
        dropFromCrop(new ArrayList<>());

        assertEquals(4, invSeeds.getAmount());
        verify(inventory).setItem(0, invSeeds);
        verify(target).setType(Material.BEETROOTS, false);
    }

    @Test
    void noSeedAnywhereSkipsReplant() {
        when(plugin.isCheckSeedsEnabled()).thenReturn(true);
        cropIs(Material.WHEAT, 7);
        groundReady(Material.FARMLAND);

        breakCrop();
        dropFromCrop(new ArrayList<>(List.of(itemEntity(stack(Material.WHEAT, 1)))));

        verify(scheduler, never()).runTask(any(Plugin.class), any(Runnable.class));
        verify(target, never()).setType(any(Material.class), anyBoolean());
    }

    @Test
    void wrongGroundKeepsSeedInDrops() {
        when(plugin.isCheckSeedsEnabled()).thenReturn(true);
        cropIs(Material.WHEAT, 7);
        groundReady(Material.DIRT);

        ItemStack seeds = stack(Material.WHEAT_SEEDS, 2);
        Item seedItem = itemEntity(seeds);
        List<Item> items = new ArrayList<>(List.of(seedItem));
        breakCrop();
        dropFromCrop(items);

        assertEquals(2, seeds.getAmount());
        assertEquals(List.of(seedItem), items);
        verify(scheduler, never()).runTask(any(Plugin.class), any(Runnable.class));
        verify(target, never()).setType(any(Material.class), anyBoolean());
    }

    @Test
    void wrongGroundKeepsSeedInInventory() {
        when(plugin.isCheckSeedsEnabled()).thenReturn(true);
        cropIs(Material.NETHER_WART, 3);
        groundReady(Material.FARMLAND); // 地獄疙瘩需要靈魂沙

        ItemStack invWart = stack(Material.NETHER_WART, 3);
        when(inventory.getItem(0)).thenReturn(invWart);
        breakCrop();
        dropFromCrop(new ArrayList<>());

        assertEquals(3, invWart.getAmount());
        verify(inventory, never()).setItem(anyInt(), any());
        verify(scheduler, never()).runTask(any(Plugin.class), any(Runnable.class));
    }

    // ─── 骨粉 ─────────────────────────────────────────────────────────────────

    private BlockState fertilizedState(Material type, int age, int max) {
        Ageable data = ageable(age, max);
        BlockState state = mock(BlockState.class);
        when(state.getType()).thenReturn(type);
        when(state.getBlockData()).thenReturn(data);
        when(state.getBlock()).thenReturn(crop);
        return state;
    }

    private void fertilize(BlockState... states) {
        listener.onBlockFertilize(new BlockFertilizeEvent(crop, player, new ArrayList<>(List.of(states))));
    }

    /** 骨粉任務執行時，該格已是成熟作物。 */
    private void targetIsMature(Material type, int max, ItemStack... drops) {
        Ageable data = ageable(max, max);
        when(target.getType()).thenReturn(type);
        when(target.getBlockData()).thenReturn(data);
        when(below.getType()).thenReturn(type == Material.NETHER_WART ? Material.SOUL_SAND : Material.FARMLAND);
        when(target.getDrops(any(), eq(player))).thenReturn(List.of(drops));
    }

    private List<ItemStack> droppedNaturally(int expectedCalls) {
        ArgumentCaptor<ItemStack> captor = ArgumentCaptor.forClass(ItemStack.class);
        verify(world, times(expectedCalls)).dropItemNaturally(any(Location.class), captor.capture());
        return captor.getAllValues();
    }

    @Test
    void boneMealToMaturityHarvestsAndReplants() {
        targetIsMature(Material.WHEAT, 7, stack(Material.WHEAT, 1), stack(Material.WHEAT_SEEDS, 2));
        fertilize(fertilizedState(Material.WHEAT, 7, 7));

        List<ItemStack> dropped = droppedNaturally(2);
        assertEquals(Material.WHEAT, dropped.get(0).getType());
        assertEquals(2, dropped.get(1).getAmount(), "check-seeds false → drops untouched");
        verify(target).setType(Material.WHEAT, false);
        verify(player).spawnParticle(eq(Particle.HAPPY_VILLAGER), any(Location.class),
                eq(8), eq(0.25), eq(0.25), eq(0.25), eq(0.02));
    }

    @Test
    void boneMealNotReachingMaturityDoesNothing() {
        fertilize(fertilizedState(Material.WHEAT, 5, 7));
        verify(scheduler, never()).runTask(any(Plugin.class), any(Runnable.class));
    }

    @Test
    void boneMealFeatureDisabledDoesNothing() {
        when(plugin.isBoneMealAutoReplantEnabled()).thenReturn(false);
        fertilize(fertilizedState(Material.WHEAT, 7, 7));
        verify(scheduler, never()).runTask(any(Plugin.class), any(Runnable.class));
    }

    @Test
    void boneMealWithoutPlayerOrInCreativeDoesNothing() {
        listener.onBlockFertilize(new BlockFertilizeEvent(crop, null,
                new ArrayList<>(List.of(fertilizedState(Material.WHEAT, 7, 7)))));
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        fertilize(fertilizedState(Material.WHEAT, 7, 7));
        verify(scheduler, never()).runTask(any(Plugin.class), any(Runnable.class));
    }

    @Test
    void boneMealOnUnsupportedCropDoesNothing() {
        fertilize(fertilizedState(Material.SWEET_BERRY_BUSH, 3, 3));
        verify(scheduler, never()).runTask(any(Plugin.class), any(Runnable.class));
    }

    @Test
    void boneMealDoesNotHarvestWhenPlayerDisabledAutoReplant() {
        // 玩家個人開關關閉時維持原版行為：作物長熟留在原地，不自動收成
        when(plugin.isAutoReplantEnabled(player)).thenReturn(false);
        targetIsMature(Material.WHEAT, 7, stack(Material.WHEAT, 1));
        fertilize(fertilizedState(Material.WHEAT, 7, 7));

        verify(scheduler, never()).runTask(any(Plugin.class), any(Runnable.class));
        droppedNaturally(0);
        verify(target, never()).setType(any(Material.class), anyBoolean());
    }

    @Test
    void boneMealTaskSkipsIfPlayerDisablesAutoReplantBeforeNextTick() {
        targetIsMature(Material.WHEAT, 7, stack(Material.WHEAT, 1));
        // 事件當下為開啟，排程任務執行時已關閉
        when(plugin.isAutoReplantEnabled(player)).thenReturn(true, false);
        fertilize(fertilizedState(Material.WHEAT, 7, 7));

        verify(scheduler).runTask(any(Plugin.class), any(Runnable.class));
        droppedNaturally(0);
        verify(target, never()).setType(any(Material.class), anyBoolean());
    }

    @Test
    void boneMealWithCheckSeedsTakesSeedFromDrops() {
        when(plugin.isCheckSeedsEnabled()).thenReturn(true);
        targetIsMature(Material.WHEAT, 7, stack(Material.WHEAT, 1), stack(Material.WHEAT_SEEDS, 3));
        fertilize(fertilizedState(Material.WHEAT, 7, 7));

        List<ItemStack> dropped = droppedNaturally(2);
        assertEquals(Material.WHEAT_SEEDS, dropped.get(1).getType());
        assertEquals(2, dropped.get(1).getAmount());
        verify(inventory, never()).setItem(anyInt(), any());
        verify(target).setType(Material.WHEAT, false);
    }

    @Test
    void boneMealWithCheckSeedsDropsNothingForSingleSeedStack() {
        when(plugin.isCheckSeedsEnabled()).thenReturn(true);
        targetIsMature(Material.CARROTS, 7, stack(Material.CARROT, 1));
        fertilize(fertilizedState(Material.CARROTS, 7, 7));

        droppedNaturally(0);
        verify(target).setType(Material.CARROTS, false);
    }

    @Test
    void boneMealWithCheckSeedsFallsBackToInventory() {
        when(plugin.isCheckSeedsEnabled()).thenReturn(true);
        ItemStack invSeeds = stack(Material.WHEAT_SEEDS, 2);
        when(inventory.getItem(7)).thenReturn(invSeeds);
        targetIsMature(Material.WHEAT, 7, stack(Material.WHEAT, 1));
        fertilize(fertilizedState(Material.WHEAT, 7, 7));

        assertEquals(1, invSeeds.getAmount());
        verify(target).setType(Material.WHEAT, false);
    }

    @Test
    void boneMealWithCheckSeedsAndNoSeedHarvestsAndLeavesAir() {
        when(plugin.isCheckSeedsEnabled()).thenReturn(true);
        targetIsMature(Material.WHEAT, 7, stack(Material.WHEAT, 1));
        fertilize(fertilizedState(Material.WHEAT, 7, 7));

        droppedNaturally(1);
        verify(target).setType(Material.AIR, false);
    }

    @Test
    void boneMealTaskSkipsIfBlockChangedBeforeNextTick() {
        targetIsMature(Material.WHEAT, 7, stack(Material.WHEAT, 1));
        when(target.getType()).thenReturn(Material.AIR);
        fertilize(fertilizedState(Material.WHEAT, 7, 7));

        verify(scheduler).runTask(any(Plugin.class), any(Runnable.class));
        droppedNaturally(0);
        verify(target, never()).setType(any(Material.class), anyBoolean());
    }

    @Test
    void boneMealTaskSkipsOnWrongGround() {
        targetIsMature(Material.WHEAT, 7, stack(Material.WHEAT, 1));
        when(below.getType()).thenReturn(Material.DIRT);
        fertilize(fertilizedState(Material.WHEAT, 7, 7));

        droppedNaturally(0);
        verify(target, never()).setType(any(Material.class), anyBoolean());
    }

    @Test
    void boneMealDropsGoToAutoPickupWhenAvailableAndEnabled() {
        AutoPickupCompat compat = mock(AutoPickupCompat.class);
        when(compat.isAvailable()).thenReturn(true);
        when(compat.isEnabledFor(player)).thenReturn(true);
        when(plugin.getAutoPickupCompat()).thenReturn(compat);
        targetIsMature(Material.WHEAT, 7, stack(Material.WHEAT, 1));

        fertilize(fertilizedState(Material.WHEAT, 7, 7));

        verify(compat).giveDropsToPlayer(eq(player), any(), any(Location.class));
        droppedNaturally(0);
        verify(target).setType(Material.WHEAT, false);
    }

    @Test
    void boneMealDropsFallToGroundWhenAutoPickupDisabledForPlayer() {
        AutoPickupCompat compat = mock(AutoPickupCompat.class);
        when(compat.isAvailable()).thenReturn(true);
        when(compat.isEnabledFor(player)).thenReturn(false);
        when(plugin.getAutoPickupCompat()).thenReturn(compat);
        targetIsMature(Material.WHEAT, 7, stack(Material.WHEAT, 1));

        fertilize(fertilizedState(Material.WHEAT, 7, 7));

        verify(compat, never()).giveDropsToPlayer(any(), any(), any());
        droppedNaturally(1);
    }
}
