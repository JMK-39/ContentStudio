//? if >=1.21 {
/*package dev.xyat.contentstudiovalidation;
import dev.xyat.contentstudio.loot.*;


import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RuntimeLootJsonChecks {
    @BeforeAll static void bootstrap() { if (net.neoforged.fml.loading.LoadingModList.get() == null) net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(),java.util.List.of(),java.util.List.of(),java.util.List.of(),java.util.Map.of()); SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    private static JsonObject entry() {
        JsonObject entry = new JsonObject();
        entry.addProperty("type", "minecraft:item");
        entry.addProperty("name", "minecraft:diamond_sword");
        return entry;
    }
    @Test void componentFunctionRoundTripPreservesRemovedAndAddedComponents() {
        JsonObject entry = entry();
        ItemStack configured = new ItemStack(Items.DIAMOND_SWORD);
        configured.remove(DataComponents.ATTRIBUTE_MODIFIERS);
        configured.set(DataComponents.DAMAGE, 7);
        LootJsonEditUtil.setItemNbt(entry, configured);
        JsonObject function = entry.getAsJsonArray("functions").get(0).getAsJsonObject();
        assertEquals("minecraft:set_components", function.get("function").getAsString());
        assertFalse(function.has("tag"));
        assertTrue(function.getAsJsonObject("components").has("!minecraft:attribute_modifiers"));
        ItemStack actual = new ItemStack(Items.DIAMOND_SWORD);
        LootJsonEditUtil.applyItemNbt(entry, actual);
        assertEquals(configured.getComponentsPatch(), actual.getComponentsPatch());
        assertEquals(0, LootJsonEditUtil.unknownFunctionCount(entry));
    }
    @Test void editingComponentsPreservesUnknownFunctionsAndRejectsLegacyNbt() {
        JsonObject entry = entry();
        JsonObject unknown = new JsonObject();
        unknown.addProperty("function", "example:custom");
        unknown.addProperty("opaque", "keep");
        JsonArray functions = new JsonArray(); functions.add(unknown); entry.add("functions", functions);
        LootJsonEditUtil.setItemNbt(entry, "[minecraft:damage=5]");
        assertTrue(entry.getAsJsonArray("functions").contains(unknown));
        String before = entry.toString();
        assertThrows(IllegalArgumentException.class, () -> LootJsonEditUtil.setItemNbt(entry, "{Damage:6}"));
        assertEquals(before, entry.toString());
        LootJsonEditUtil.setItemNbt(entry, "[]");
        assertEquals(1, entry.getAsJsonArray("functions").size());
        assertEquals(unknown, entry.getAsJsonArray("functions").get(0));
    }
    @Test void customDataNumericTypesSurviveLootJson() {
        var entry = new com.google.gson.JsonObject();entry.addProperty("type","minecraft:item");entry.addProperty("name","minecraft:diamond");
        var original = dev.xyat.contentstudio.item.ItemData.compile("minecraft:diamond","[custom_data={Key:1,Large:2L}]");
        LootJsonEditUtil.setItemNbt(entry,original);
        var restored = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND);LootJsonEditUtil.applyItemNbt(entry,restored);
        assertEquals(original.getComponentsPatch(),restored.getComponentsPatch());
    }

    @Test void changedItemUsesItsOwnComponentPrototype() {
        var entry = new JsonObject();entry.addProperty("name","minecraft:stone");
        var selected = LootJsonEditUtil.setSelectedItemComponents(entry,"minecraft:diamond_sword","[max_damage=2000]");
        assertEquals("minecraft:diamond_sword",entry.get("name").getAsString());
        assertEquals(2000,selected.getMaxDamage());
        var reloaded = new ItemStack(Items.DIAMOND_SWORD);LootJsonEditUtil.applyItemNbt(entry,reloaded);
        assertEquals(selected.getComponentsPatch(),reloaded.getComponentsPatch());
    }

}

*///?} else {

//?}
