package dev.autoreplant;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;
import java.util.Map;

public class AutoReplantListener implements Listener {

    /**
     * Maps the block material of each supported crop to the seed/item
     * that needs to be consumed from the drop pile in order to replant.
     *
     * Wheat     → WHEAT_SEEDS  (separate seed item)
     * Carrots   → CARROT       (the carrot itself acts as the seed)
     * Potatoes  → POTATO       (the potato itself acts as the seed)
     * Beetroots → BEETROOT_SEEDS (separate seed item)
     */
    private static final Map<Material, Material> CROP_TO_SEED = Map.of(
            Material.WHEAT,     Material.WHEAT_SEEDS,
            Material.CARROTS,   Material.CARROT,
            Material.POTATOES,  Material.POTATO,
            Material.BEETROOTS, Material.BEETROOT_SEEDS
    );

    private final AutoReplantPlugin plugin;

    public AutoReplantListener(AutoReplantPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // 創意模式不觸發（方塊破壞不掉落）
        if (player.getGameMode() == GameMode.CREATIVE) return;

        Block block = event.getBlock();
        Material blockType = block.getType();

        // 只處理支援的農作物
        if (!CROP_TO_SEED.containsKey(blockType)) return;

        // 只有完全成熟（age == maxAge）時才觸發
        if (!(block.getBlockData() instanceof Ageable ageable)) return;
        if (ageable.getAge() < ageable.getMaximumAge()) return;

        // 玩家是否開啟自動回種植
        if (!plugin.isAutoReplantEnabled(player)) return;

        // 模擬此方塊在當前工具下的掉落物（支援 Fortune 附魔）
        ItemStack tool = player.getInventory().getItemInMainHand();
        Collection<ItemStack> drops = block.getDrops(tool, player);

        // 從掉落物中取走一顆種子用於回種植；若沒有種子則不處理
        Material seedMaterial = CROP_TO_SEED.get(blockType);
        if (!consumeOneSeed(drops, seedMaterial)) return;

        // 取消預設掉落，手動丟出修改後的物品
        event.setDropItems(false);
        Location dropLoc = block.getLocation();
        for (ItemStack drop : drops) {
            if (drop != null && !drop.getType().isAir() && drop.getAmount() > 0) {
                block.getWorld().dropItemNaturally(dropLoc, drop);
            }
        }

        // 延遲一 tick 等方塊破壞完成後再回種植
        final Location blockLoc = block.getLocation().clone();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Block target = blockLoc.getBlock();

            // 確認該位置已是空氣，且下方仍是耕地
            if (target.getType() != Material.AIR) return;
            if (target.getRelative(BlockFace.DOWN).getType() != Material.FARMLAND) return;

            // 種回農作物（預設 age = 0，即幼苗狀態）
            target.setType(blockType, false);
        });
    }

    /**
     * 在掉落物清單中找到一顆種子並消耗掉（amount - 1）。
     *
     * @return 成功消耗回傳 true，找不到種子回傳 false
     */
    private boolean consumeOneSeed(Collection<ItemStack> drops, Material seedMaterial) {
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType() != seedMaterial || drop.getAmount() <= 0) continue;

            // 直接減少數量；若變成 0 後面的掉落迴圈會過濾掉
            drop.setAmount(drop.getAmount() - 1);
            return true;
        }
        return false;
    }
}
