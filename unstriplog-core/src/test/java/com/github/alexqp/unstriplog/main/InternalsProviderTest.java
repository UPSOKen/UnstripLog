package com.github.alexqp.unstriplog.main;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InternalsProviderTest {
    @Test
    void olderApiWithoutOptionalMaterialsKeepsExistingMaterialsAndHasNoNulls() {
        InternalsProvider internals = new InternalsProvider();
        assertNull(Material.matchMaterial("POPLAR_LOG"), "This regression runs against the declared 1.20.6 API");
        assertNull(Material.matchMaterial("COPPER_AXE"));
        assertEquals(Set.of(Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE,
                Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE), internals.getLogToolTypeSet());
        assertEquals(Set.of(Material.WOODEN_SHOVEL, Material.STONE_SHOVEL, Material.IRON_SHOVEL,
                Material.GOLDEN_SHOVEL, Material.DIAMOND_SHOVEL, Material.NETHERITE_SHOVEL), internals.getGrassToolTypeSet());
        assertEquals(21, internals.getLogOriginBlockTypeSet().size());
        assertEquals(21, internals.getLogStrippedBlockTypeSet().size());
        for (Material origin : internals.getLogOriginBlockTypeSet()) {
            assertTrue(internals.getLogStrippedBlockTypeSet().contains(Material.matchMaterial("STRIPPED_" + origin.name())));
        }
        assertFalse(internals.getLogOriginBlockTypeSet().contains(null));
        assertFalse(internals.getLogStrippedBlockTypeSet().contains(null));
        assertFalse(internals.getLogToolTypeSet().contains(null));
        assertFalse(internals.getGrassToolTypeSet().contains(null));
    }

    @Test
    void optionalPoplarAndCopperAreIncludedWhenRuntimeLookupsResolve() {
        // Simulate new enum constants on the intentionally older compile/test API.
        Material poplarLog = namedMaterial("POPLAR_LOG");
        Material poplarWood = namedMaterial("POPLAR_WOOD");
        Material strippedLog = namedMaterial("STRIPPED_POPLAR_LOG");
        Material strippedWood = namedMaterial("STRIPPED_POPLAR_WOOD");
        Material copperAxe = namedMaterial("COPPER_AXE");
        Material copperShovel = namedMaterial("COPPER_SHOVEL");
        Map<String, Material> optional = Map.of("POPLAR_LOG", poplarLog, "POPLAR_WOOD", poplarWood,
                "STRIPPED_POPLAR_LOG", strippedLog, "STRIPPED_POPLAR_WOOD", strippedWood,
                "COPPER_AXE", copperAxe, "COPPER_SHOVEL", copperShovel);
        try (MockedStatic<Material> material = mockStatic(Material.class, CALLS_REAL_METHODS)) {
            optional.forEach((name, value) -> material.when(() -> Material.matchMaterial(name)).thenReturn(value));
            material.clearInvocations();
            InternalsProvider internals = new InternalsProvider();
            assertTrue(internals.getLogOriginBlockTypeSet().containsAll(Set.of(poplarLog, poplarWood)));
            assertTrue(internals.getLogStrippedBlockTypeSet().containsAll(Set.of(strippedLog, strippedWood)));
            assertTrue(internals.getLogToolTypeSet().contains(copperAxe));
            assertTrue(internals.getGrassToolTypeSet().contains(copperShovel));
            assertTrue(internals.getLogToolTypeSet().contains(Material.IRON_AXE));
            assertTrue(internals.getGrassToolTypeSet().contains(Material.IRON_SHOVEL));
            optional.keySet().forEach(name -> material.verify(() -> Material.matchMaterial(name)));
        }
    }

    @Test
    void missingStrippedCounterpartIsIgnored() {
        Material poplarLog = namedMaterial("POPLAR_LOG");
        try (MockedStatic<Material> material = mockStatic(Material.class, CALLS_REAL_METHODS)) {
            material.when(() -> Material.matchMaterial("POPLAR_LOG")).thenReturn(poplarLog);
            material.clearInvocations();
            InternalsProvider internals = new InternalsProvider();
            assertTrue(internals.getLogOriginBlockTypeSet().contains(poplarLog));
            assertFalse(internals.getLogStrippedBlockTypeSet().contains(null));
            material.verify(() -> Material.matchMaterial("STRIPPED_POPLAR_LOG"));
        }
    }

    private static Material namedMaterial(String name) {
        Material material = mock(Material.class);
        when(material.name()).thenReturn(name);
        return material;
    }
}
