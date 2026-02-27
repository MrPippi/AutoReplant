package dev.autoreplant;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class AutoReplantListener implements Listener {

    /**
     * 以「世界 UUID + 方塊整數座標」組成字串 key，確保 BlockBreakEvent 與 BlockDropItemEvent
     * 使用完全一致的 key（不依賴 {@link Location#equals} 或 Block 參考），連續採集時每格都能正確匹配。
     */
    private static String blockKey(Block block) {
        Location loc = block.getLocation();
        return loc.getWorld().getUID() + " " + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ();
    }

    /**
     * 支援的農作物 → 對應需消耗的種子材質。
     *
     *   WHEAT     → WHEAT_SEEDS    (小麥種子，獨立物品)
     *   CARROTS   → CARROT         (胡蘿蔔本身即為種子)
     *   POTATOES  → POTATO         (馬鈴薯本身即為種子)
     *   BEETROOTS → BEETROOT_SEEDS (甜菜根種子，獨立物品)
     */
    private static final Map<Material, Material> CROP_TO_SEED = Map.of(
            Material.WHEAT,     Material.WHEAT_SEEDS,
            Material.CARROTS,   Material.CARROT,
            Material.POTATOES,  Material.POTATO,
            Material.BEETROOTS, Material.BEETROOT_SEEDS
    );

    /**
     * 記錄「已確認需要回種植」的方塊位置與作物材質。
     * 由 onBlockBreak (HIGHEST) 寫入，由 onBlockDropItem (NORMAL) 讀出並清除。
     * Key 為 {@link #blockKey(Block)} 字串，確保連續採集時每格都能正確匹配。
     *
     * 生命週期極短（同一 tick 內的兩個事件之間），無並發問題。
     */
    private final Map<String, Material> pendingReplants = new HashMap<>();

    private final AutoReplantPlugin plugin;

    public AutoReplantListener(AutoReplantPlugin plugin) {
        this.plugin = plugin;
    }

    // ─── 第一階段：條件確認 ────────────────────────────────────────────────────

    /**
     * 在所有插件（含保護插件）處理完 BlockBreakEvent 之後才執行，
     * 確保只有最終未被取消的破壞動作才進入待回種植佇列。
     *
     * <p>此處不修改任何掉落物，避免在事件仍可能被取消時就動手腳。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // 創意模式不觸發（不掉落物品，種子邏輯無意義）
        if (player.getGameMode() == GameMode.CREATIVE) return;

        Block block = event.getBlock();
        Material blockType = block.getType();

        // 只處理支援的農作物
        if (!CROP_TO_SEED.containsKey(blockType)) return;

        if (!(block.getBlockData() instanceof Ageable ageable)) return;

        // 開啟自動回種時：禁止破壞未成熟農作物（避免長按左鍵誤破幼苗）
        if (plugin.isAutoReplantEnabled(player) && ageable.getAge() < ageable.getMaximumAge()) {
            event.setCancelled(true);
            return;
        }

        // 僅完全成熟（age == maxAge）時才進入待回種佇列
        if (ageable.getAge() < ageable.getMaximumAge()) return;

        String key = blockKey(block);

        if (plugin.isAutoReplantEnabled(player)) {
            // 標記此位置等待 BlockDropItemEvent 進行後續處理
            pendingReplants.put(key, blockType);
        } else {
            // 玩家已關閉自動回種植 — 清除可能由先前取消破壞所殘留的舊紀錄，
            // 防止下一位破壞同一位置的玩家誤觸舊紀錄。
            pendingReplants.remove(key);
        }
    }

    // ─── 第二階段：掉落物處理 + 回種植排程 ────────────────────────────────────

    /**
     * 在方塊確實破壞完成、物品即將生成前觸發。
     * 此時可安全修改掉落物清單，且保證對應的破壞動作未被取消。
     *
     * <p>掉落物為伺服器實際計算值（含其他插件的修改與 Fortune 附魔效果），
     * 比在 BlockBreakEvent 中模擬更準確。
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockDropItem(BlockDropItemEvent event) {
        Block block = event.getBlock();
        String key = blockKey(block);
        Material blockType = pendingReplants.remove(key);

        // 若此位置不在待處理佇列中則略過
        if (blockType == null) return;

        final Player player = event.getPlayer();

        if (plugin.isCheckSeedsEnabled()) {
            // ── check-seeds: true ──
            // 優先從掉落物扣一顆種子；若掉落物無種子則從背包扣。
            Material seedMaterial = CROP_TO_SEED.get(blockType);
            boolean fromDrops = consumeOneSeedFromDrops(event.getItems(), seedMaterial);
            if (!fromDrops && !consumeSeedFromInventory(player, seedMaterial)) return;
        }
        // ── check-seeds: false ──
        // 不消耗種子，直接回種植。

        // 排程回種植（延遲一 tick 確保方塊已變為空氣）
        final Location blockLoc = block.getLocation().clone();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Block target = blockLoc.getBlock();

            // 安全確認：位置必須是空氣，且下方必須仍是耕地
            if (target.getType() != Material.AIR) return;
            if (target.getRelative(BlockFace.DOWN).getType() != Material.FARMLAND) return;

            // 種回農作物（預設 BlockData → age = 0，即幼苗狀態）
            target.setType(blockType, false);
            spawnReplantParticle(player, blockLoc);
        });
    }

    // ─── 骨粉催化至成熟後自動收成 + 回種 ────────────────────────────────────────

    /**
     * 當玩家以骨粉將作物催化至完全成熟時，不取消事件；延遲 1 tick 後對該格執行
     * 「生成掉落物 + 有條件回種」（復用 check-seeds 與玩家開關）。
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockFertilize(BlockFertilizeEvent event) {
        if (!plugin.isBoneMealAutoReplantEnabled()) return;

        Player player = event.getPlayer();
        if (player == null) return;
        if (player.getGameMode() == GameMode.CREATIVE) return;

        for (BlockState state : event.getBlocks()) {
            if (!(state.getBlockData() instanceof Ageable ageable)) continue;
            if (ageable.getAge() != ageable.getMaximumAge()) continue;
            if (!CROP_TO_SEED.containsKey(state.getType())) continue;

            final Location blockLoc = state.getBlock().getLocation().clone();
            final Material blockType = state.getType();
            final Player playerRef = player;

            plugin.getServer().getScheduler().runTask(plugin, () ->
                    harvestAndReplantBoneMeal(blockLoc, blockType, playerRef));
        }
    }

    /**
     * 延遲任務：對骨粉催熟的那一格執行收成（生成掉落物）並在條件滿足時回種。
     * 若需消耗種子但背包無種子，則只收成（掉落物照常）、不回種，並將方塊設為空氣。
     */
    private void harvestAndReplantBoneMeal(Location blockLoc, Material blockType, Player player) {
        Block target = blockLoc.getBlock();
        if (target.getType() != blockType) return;
        if (!(target.getBlockData() instanceof Ageable ageable) || ageable.getAge() != ageable.getMaximumAge()) return;
        if (target.getRelative(BlockFace.DOWN).getType() != Material.FARMLAND) return;

        Location dropCenter = blockLoc.clone().add(0.5, 0.5, 0.5);
        Material seedMaterial = CROP_TO_SEED.get(blockType);
        ItemStack hand = player.getInventory().getItemInMainHand();
        Collection<ItemStack> rawDrops = target.getDrops(hand, player);
        DropResult dropResult = computeDropsAfterSeedConsume(rawDrops, seedMaterial,
                plugin.isCheckSeedsEnabled());

        var compat = plugin.getAutoPickupCompat();
        if (compat != null && compat.isAvailable() && compat.isEnabledFor(player)) {
            compat.giveDropsToPlayer(player, dropResult.toDrop, dropCenter);
        } else {
            for (ItemStack drop : dropResult.toDrop) {
                if (drop.getAmount() > 0) {
                    target.getWorld().dropItemNaturally(dropCenter, drop);
                }
            }
        }

        if (!plugin.isAutoReplantEnabled(player)) {
            target.setType(Material.AIR, false);
            return;
        }

        if (plugin.isCheckSeedsEnabled()) {
            if (!dropResult.consumedFromDrops && !consumeSeedFromInventory(player, seedMaterial)) {
                target.setType(Material.AIR, false);
                return;
            }
        }

        target.setType(blockType, false);
        spawnReplantParticle(player, blockLoc);
    }

    // ─── 工具方法 ──────────────────────────────────────────────────────────────

    /** 在方塊中心對玩家顯示回種成功粒子（僅在玩家在線時）。 */
    private void spawnReplantParticle(Player player, Location blockLoc) {
        if (player.isOnline()) {
            Location center = blockLoc.clone().add(0.5, 0.5, 0.5);
            player.spawnParticle(Particle.HAPPY_VILLAGER, center, 8, 0.25, 0.25, 0.25, 0.02);
        }
    }

    /** 掉落物處理結果：實際要生成的掉落清單，以及是否已從掉落物中扣過一顆種子。 */
    private record DropResult(List<ItemStack> toDrop, boolean consumedFromDrops) {}

    /**
     * 當 check-seeds 時，從掉落物中扣一顆種子（優先），回傳要實際生成的掉落清單與是否已從掉落物扣除。
     */
    private DropResult computeDropsAfterSeedConsume(Collection<ItemStack> rawDrops,
                                                     Material seedMaterial, boolean checkSeeds) {
        ArrayList<ItemStack> toDrop = new ArrayList<>();
        if (!checkSeeds) {
            rawDrops.forEach(stack -> toDrop.add(stack.clone()));
            return new DropResult(toDrop, false);
        }
        boolean consumed = false;
        for (ItemStack stack : rawDrops) {
            if (!consumed && stack.getType() == seedMaterial && stack.getAmount() >= 1) {
                consumed = true;
                if (stack.getAmount() > 1) {
                    ItemStack reduced = stack.clone();
                    reduced.setAmount(stack.getAmount() - 1);
                    toDrop.add(reduced);
                }
            } else {
                toDrop.add(stack.clone());
            }
        }
        return new DropResult(toDrop, consumed);
    }

    /**
     * 從 BlockDropItemEvent 的掉落實體清單中扣一顆種子（減少對應 Item 的數量或移除該 Item）。
     * @return 是否成功從掉落物扣除一顆種子
     */
    private boolean consumeOneSeedFromDrops(List<Item> items, Material seedMaterial) {
        Iterator<Item> it = items.iterator();
        while (it.hasNext()) {
            Item item = it.next();
            ItemStack stack = item.getItemStack();
            if (stack.getType() != seedMaterial) continue;
            if (stack.getAmount() > 1) {
                stack.setAmount(stack.getAmount() - 1);
                item.setItemStack(stack);
            } else {
                it.remove();
            }
            return true;
        }
        return false;
    }

    /**
     * 從玩家背包中找到指定材質的種子並消耗一顆。
     * 僅比對材質（{@link Material}），忽略自訂 ItemMeta，
     * 確保有特殊名稱或附魔的種子也能被正確識別。
     *
     * <ul>
     *   <li>若該堆數量 &gt; 1：數量 -1，格子保留。
     *   <li>若該堆數量 == 1：格子設為 {@code null}（清空）。
     * </ul>
     *
     * @param player       要消耗種子的玩家
     * @param seedMaterial 要消耗的種子材質
     * @return 成功消耗回傳 {@code true}，背包中找不到種子回傳 {@code false}
     */
    private boolean consumeSeedFromInventory(Player player, Material seedMaterial) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack == null || stack.getType() != seedMaterial) continue;

            if (stack.getAmount() > 1) {
                stack.setAmount(stack.getAmount() - 1);
                inv.setItem(i, stack);
            } else {
                inv.setItem(i, null);
            }
            return true;
        }
        return false;
    }
}
