package dev.autoreplant;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
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

        if (plugin.isCheckSeedsEnabled()) {
            // ── check-seeds: true ──
            // 從掉落物中消耗一顆種子作為回種植的代價。
            // 若掉落物中沒有種子（例如甜菜根隨機未掉種子），則放棄回種植，
            // 讓所有掉落物正常生成。
            Material seedMaterial = CROP_TO_SEED.get(blockType);
            if (!consumeOneSeed(event.getItems(), seedMaterial)) return;
        }
        // ── check-seeds: false ──
        // 不消耗種子，直接回種植；所有掉落物均正常生成。

        // 排程回種植（延遲一 tick 確保方塊已變為空氣）
        final Location blockLoc = block.getLocation().clone();
        final Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Block target = blockLoc.getBlock();

            // 安全確認：位置必須是空氣，且下方必須仍是耕地
            if (target.getType() != Material.AIR) return;
            if (target.getRelative(BlockFace.DOWN).getType() != Material.FARMLAND) return;

            // 種回農作物（預設 BlockData → age = 0，即幼苗狀態）
            target.setType(blockType, false);

            // 在該農作物位置對觸發玩家顯示 happy_villager 粒子
            if (player.isOnline()) {
                Location center = blockLoc.clone().add(0.5, 0.5, 0.5);
                player.spawnParticle(Particle.HAPPY_VILLAGER, center, 8, 0.25, 0.25, 0.25, 0.02);
            }
        });
    }

    // ─── 工具方法 ──────────────────────────────────────────────────────────────

    /**
     * 從 {@link BlockDropItemEvent} 的掉落物清單中找到指定材質的種子並消耗一顆。
     *
     * <ul>
     *   <li>若該堆數量 &gt; 1：數量 -1，{@link Item} 保留在清單中。
     *   <li>若該堆數量 == 1：直接從清單移除，物品不會生成。
     * </ul>
     *
     * @param items        BlockDropItemEvent 提供的可變掉落物清單
     * @param seedMaterial 要消耗的種子材質
     * @return 成功消耗回傳 {@code true}，清單中找不到種子回傳 {@code false}
     */
    private boolean consumeOneSeed(List<Item> items, Material seedMaterial) {
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i).getItemStack();
            if (stack.getType() != seedMaterial || stack.getAmount() <= 0) continue;

            if (stack.getAmount() > 1) {
                stack.setAmount(stack.getAmount() - 1);
                items.get(i).setItemStack(stack);
            } else {
                items.remove(i);
            }
            return true;
        }
        return false;
    }
}
