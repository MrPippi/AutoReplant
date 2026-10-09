package dev.autoreplant;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.BlockType;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 測試用 RegistryAccess（以 ServiceLoader 載入）。
 * 真實實作由伺服器提供；此處只讓 {@code Material#isAir()} 能在無伺服器環境下運作。
 */
public class TestRegistryAccess implements RegistryAccess {

    private static final Set<String> AIR = Set.of("air", "cave_air", "void_air");

    @Override
    @SuppressWarnings("removal") // 介面仍要求實作此方法
    public <T extends Keyed> Registry<T> getRegistry(Class<T> type) {
        return registry();
    }

    @Override
    public <T extends Keyed> Registry<T> getRegistry(RegistryKey<T> key) {
        return registry();
    }

    @SuppressWarnings("unchecked")
    private static <T extends Keyed> Registry<T> registry() {
        Registry<T> registry = mock(Registry.class);
        when(registry.get(any(NamespacedKey.class))).thenAnswer(inv -> {
            NamespacedKey key = inv.getArgument(0);
            BlockType blockType = mock(BlockType.class);
            when(blockType.isAir()).thenReturn(AIR.contains(key.getKey()));
            return blockType;
        });
        return registry;
    }
}
