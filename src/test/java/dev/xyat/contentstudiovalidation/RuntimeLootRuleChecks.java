//? if >=1.21 {
/*package dev.xyat.contentstudiovalidation;
import dev.xyat.contentstudio.loot.*;


import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeLootRuleChecks {
    @BeforeAll
    static void bootstrap() {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(),java.util.List.of(),java.util.List.of(),java.util.List.of(),java.util.Map.of());
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void exactRuleIncludesRemovedComponentsAndIgnoresCount() {
        ItemStack expected = new ItemStack(Items.DIAMOND_SWORD);
        expected.remove(DataComponents.ATTRIBUTE_MODIFIERS);
        expected.set(DataComponents.DAMAGE, 3);
        GlobalRemoveRule rule = GlobalRemoveRule.fromStack(expected);
        ItemStack same = expected.copy();
        same.setCount(2);
        assertTrue(rule.matches(same));
        ItemStack extra = expected.copy();
        extra.set(DataComponents.REPAIR_COST, 1);
        assertFalse(rule.matches(extra));
        ItemStack withoutRemoval = new ItemStack(Items.DIAMOND_SWORD);
        withoutRemoval.set(DataComponents.DAMAGE, 3);
        assertFalse(rule.matches(withoutRemoval));
    }

    @Test
    void fuzzyRuleRequiresTypedValueAndRemovalSubset() {
        ItemStack expected = new ItemStack(Items.DIAMOND_SWORD);
        expected.remove(DataComponents.ATTRIBUTE_MODIFIERS);
        expected.set(DataComponents.DAMAGE, 3);
        GlobalRemoveRule rule = GlobalRemoveRule.fromStack(expected).withMode(GlobalRemoveRule.MatchMode.NBT_FUZZY);
        ItemStack extra = expected.copy();
        extra.set(DataComponents.REPAIR_COST, 1);
        assertTrue(rule.matches(extra));
        ItemStack missingRemoval = new ItemStack(Items.DIAMOND_SWORD);
        missingRemoval.set(DataComponents.DAMAGE, 3);
        assertFalse(rule.matches(missingRemoval));
        extra.set(DataComponents.DAMAGE, 4);
        assertFalse(rule.matches(extra));
    }

    @Test
    void presentRuleDetectsOverridesRatherThanItemDefaults() {
        GlobalRemoveRule rule = GlobalRemoveRule.item("minecraft:diamond_sword").withMode(GlobalRemoveRule.MatchMode.NBT_PRESENT);
        ItemStack plain = new ItemStack(Items.DIAMOND_SWORD);
        assertFalse(rule.matches(plain));
        plain.remove(DataComponents.ATTRIBUTE_MODIFIERS);
        assertTrue(rule.matches(plain));
    }

    @Test
    void legacyNbtTextIsRejectedInsteadOfMatchingAnEmptyPatch() {
        GlobalRemoveRule rule = new GlobalRemoveRule("minecraft:diamond_sword", GlobalRemoveRule.MatchMode.NBT_EXACT, "{Damage:3}");
        assertFalse(rule.isValid());
        assertFalse(rule.matches(new ItemStack(Items.DIAMOND_SWORD)));
    }
}

*///?} else {

//?}
