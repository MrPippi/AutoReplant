package dev.autoreplant;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

/**
 * 可選相容層：當 AutoPickup 插件存在且玩家已開啟自動撿取時，
 * 將骨粉催熟產生的掉落物直接加入玩家背包（並尊重 AutoPickup 的篩選），
 * 多餘的掉落地面，行為與挖掘方塊時一致。
 *
 * <p>透過反射呼叫 AutoPickup API，不加入編譯依賴。
 *
 * @see <a href="https://github.com/MrPippi/AutoPickup">AutoPickup</a>
 */
public final class AutoPickupCompat {

    private final Plugin autoPickupPlugin;
    private final Object stateManager;
    private final Object filterManager;
    private final Method isEnabledMethod;
    private final Method shouldPickupMethod;

    private AutoPickupCompat(Plugin autoPickupPlugin, Object stateManager, Object filterManager,
                             Method isEnabledMethod, Method shouldPickupMethod) {
        this.autoPickupPlugin = autoPickupPlugin;
        this.stateManager = stateManager;
        this.filterManager = filterManager;
        this.isEnabledMethod = isEnabledMethod;
        this.shouldPickupMethod = shouldPickupMethod;
    }

    /**
     * 若伺服器已載入 AutoPickup，則建立並回傳相容實例；否則回傳 null。
     */
    public static AutoPickupCompat create(Plugin host) {
        if (host == null) return null;
        Plugin ap = host.getServer().getPluginManager().getPlugin("AutoPickup");
        if (ap == null) return null;

        try {
            Method getStateManager = ap.getClass().getMethod("getStateManager");
            Method getFilterManager = ap.getClass().getMethod("getFilterManager");
            Object stateManager = getStateManager.invoke(ap);
            Object filterManager = getFilterManager.invoke(ap);
            if (stateManager == null || filterManager == null) return null;

            Method isEnabled = stateManager.getClass().getMethod("isEnabled", UUID.class);
            Method shouldPickup = filterManager.getClass().getMethod("shouldPickup", UUID.class, Material.class);

            return new AutoPickupCompat(ap, stateManager, filterManager, isEnabled, shouldPickup);
        } catch (ReflectiveOperationException e) {
            host.getLogger().fine("AutoPickup 已安裝但 API 無法解析: " + e.getMessage());
            return null;
        }
    }

    public boolean isAvailable() {
        return autoPickupPlugin != null && autoPickupPlugin.isEnabled();
    }

    /** 該玩家是否已開啟 AutoPickup。 */
    public boolean isEnabledFor(Player player) {
        if (player == null || stateManager == null || isEnabledMethod == null) return false;
        try {
            return Boolean.TRUE.equals(isEnabledMethod.invoke(stateManager, player.getUniqueId()));
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    /** 該玩家對該材質是否應自動撿取（依 AutoPickup 篩選設定）。 */
    public boolean shouldPickup(Player player, Material material) {
        if (player == null || material == null || filterManager == null || shouldPickupMethod == null) return true;
        try {
            return Boolean.TRUE.equals(shouldPickupMethod.invoke(filterManager, player.getUniqueId(), material));
        } catch (ReflectiveOperationException e) {
            return true;
        }
    }

    /**
     * 將掉落物發放給玩家：若玩家已開 AutoPickup 且該材質通過篩選則加入背包，滿則掉落地面；
     * 否則直接掉落地面。與 AutoPickup 的 BlockDropItemEvent 行為一致。
     */
    public void giveDropsToPlayer(Player player, List<ItemStack> stacks, Location dropLocation) {
        if (player == null || dropLocation == null || stacks == null) return;

        for (ItemStack stack : stacks) {
            if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) continue;

            if (isEnabledFor(player) && shouldPickup(player, stack.getType())) {
                var leftover = player.getInventory().addItem(stack);
                for (ItemStack overflow : leftover.values()) {
                    if (overflow != null && !overflow.getType().isAir()) {
                        dropLocation.getWorld().dropItemNaturally(dropLocation, overflow);
                    }
                }
            } else {
                dropLocation.getWorld().dropItemNaturally(dropLocation, stack);
            }
        }
    }
}
