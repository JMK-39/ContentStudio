//? if >=26.1 {
/*
package dev.xyat.contentstudiovalidation;
import dev.xyat.contentstudio.recipe.*;


import com.google.gson.JsonObject;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// 26.1: recipes are built against the server registries, ingredients are never empty and show slot displays.
class RuntimeRecipeChecks {
    private static net.minecraft.core.HolderLookup.Provider registries() {
        return dev.xyat.contentstudio.item.ItemData.registries();
    }

    private static RecipeHolder<?> build(RecipeRecord record) {
        return RecipeMemoryManager.buildRecipe(record, registries());
    }

    private static Ingredient ingredient(RecipeRecord record, int index) {
        return RecipeMemoryManager.ingredient(record, index, registries());
    }

    private static ItemStack shown(Ingredient ingredient) {
        return ((SlotDisplay.ItemStackSlotDisplay) ingredient.display()).stack().create();
    }

    private static RecipeRecord record() {
        RecipeRecord record = new RecipeRecord();
        record.editorType = "FURNACE";
        record.outputUseNbt = true;
        record.output = new ItemStack(Items.DIAMOND);
        record.output.set(DataComponents.CUSTOM_NAME, Component.literal("Component output"));
        ItemStack input = new ItemStack(Items.DIAMOND_SWORD);
        input.set(DataComponents.CUSTOM_NAME, Component.literal("Required name"));
        record.inputs.add(input);
        record.inputModes.add(1);
        return record;
    }

    @Test
    void partialAndStrictComponentsHaveDifferentExtraComponentSemantics() {
        RecipeRecord record = record();
        ItemStack extra = record.inputs.getFirst().copy();
        extra.set(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        assertTrue(ingredient(record, 0).test(extra));
        record.inputModes.set(0, 2);
        assertFalse(ingredient(record, 0).test(extra));
        assertTrue(ingredient(record, 0).test(record.inputs.getFirst()));
        extra.set(DataComponents.CUSTOM_NAME, Component.literal("Different"));
        record.inputModes.set(0, 1);
        assertFalse(ingredient(record, 0).test(extra));
    }

    @Test
    void holderKeepsConfiguredIdAndOutputComponents() {
        RecipeRecord record = record();
        RecipeHolder<?> holder = build(record);
        assertEquals(RecipeConfigStore.memoryRecipeId(record), holder.id().identifier());
        assertEquals(record.output.get(DataComponents.CUSTOM_NAME),
                RecipeView.output(holder.value(), registries()).get(DataComponents.CUSTOM_NAME));
        record.outputUseNbt = false;
        assertNull(RecipeView.output(build(record).value(), registries()).get(DataComponents.CUSTOM_NAME));
    }

    @Test
    void configUsesNativeComponentsAndKeepsCountAndMode() throws Exception {
        RecipeRecord record = record();
        ItemStack stack = record.inputs.getFirst();
        stack.setCount(3);
        stack.set(DataComponents.DAMAGE, 12);
        var write = RecipeConfigStore.class.getDeclaredMethod("writeStack", ItemStack.class, Integer.class);
        write.setAccessible(true);
        JsonObject encoded = (JsonObject) write.invoke(null, stack, 2);
        assertTrue(encoded.has("components"));
        assertFalse(encoded.has("nbt"));
        assertEquals(2, encoded.get("mode").getAsInt());
        assertEquals(3, encoded.get("count").getAsInt());
        var read = RecipeConfigStore.class.getDeclaredMethod("readStack", JsonObject.class, boolean.class, boolean.class);
        read.setAccessible(true);
        ItemStack decoded = (ItemStack) read.invoke(null, encoded, false, false);
        assertTrue(ItemStack.isSameItemSameComponents(stack, decoded));
        assertEquals(stack.getCount(), decoded.getCount());
    }

    @Test
    void recordPersistenceKeepsNativeComponentsAndEmptySlots() {
        RecipeRecord record = record();
        record.inputs.getFirst().set(DataComponents.DAMAGE, 12);
        record.inputs.add(ItemStack.EMPTY);
        record.inputModes.add(0);
        var saved = record.saveToNBT();
        assertTrue(saved.contains("outputUseComponents"));
        assertFalse(saved.contains("outputUse" + "Nbt"));
        RecipeRecord loaded = RecipeRecord.loadFromNBT(saved);
        assertEquals(record.uuid, loaded.uuid);
        assertEquals(record.outputUseNbt, loaded.outputUseNbt);
        assertEquals(record.inputModes, loaded.inputModes);
        assertTrue(ItemStack.isSameItemSameComponents(record.output, loaded.output));
        assertTrue(ItemStack.isSameItemSameComponents(record.inputs.getFirst(), loaded.inputs.getFirst()));
        assertTrue(loaded.inputs.get(1).isEmpty());
    }

    @Test
    void resetRemovesAddedComponentsAndRestoresRemovedDefaults() {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD, 7);
        stack.remove(DataComponents.DAMAGE);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Remove me"));
        dev.xyat.contentstudio.item.ItemData.reset(stack);
        assertTrue(ItemStack.isSameItemSameComponents(new ItemStack(Items.DIAMOND_SWORD), stack));
        assertEquals(7, stack.getCount());
    }

    @Test
    void shapedRecipeRetainsTrimmedPatternAndInteriorEmptySlot() {
        RecipeRecord record = record();
        record.editorType = "CRAFTING";
        ItemStack input = record.inputs.getFirst();
        record.inputs.clear();
        record.inputModes.clear();
        for (int i = 0; i < 9; i++) {
            record.inputs.add(i == 1 || i == 7 ? input.copy() : ItemStack.EMPTY);
            record.inputModes.add(1);
        }
        var recipe = (net.minecraft.world.item.crafting.ShapedRecipe) build(record).value();
        assertEquals(1, recipe.getWidth());
        assertEquals(3, recipe.getHeight());
        assertEquals(3, recipe.getIngredients().size());
        assertTrue(recipe.getIngredients().get(1).isEmpty());
        assertTrue(recipe.getIngredients().getFirst().orElseThrow().test(input));
        assertEquals(RecipeConfigStore.memoryRecipeId(record), build(record).id().identifier());
    }

    @Test
    void outputTagRemovalUsesTheRegistryTagsOfTheOutput() {
        var tag = net.minecraft.resources.ResourceLocation.parse("minecraft:beacon_payment_items");
        var candidate = RecipeMemoryManager.candidateOf(build(record()), registries(), null);
        assertTrue(candidate.outputTagIds().contains(tag));
        assertTrue(dev.xyat.contentstudio.recipe.removal.RemovalRuleEvaluator.blockingRules(candidate,
                java.util.List.of(new dev.xyat.contentstudio.recipe.removal.RemovalEntry(
                        dev.xyat.contentstudio.recipe.removal.RemovalMode.TAG, tag.toString(), ""))).size() == 1);
    }

    @Test
    void strictInputPreservesRemovedDefaultComponents() {
        RecipeRecord record = record();
        ItemStack configured = record.inputs.getFirst();
        configured.remove(DataComponents.DAMAGE);
        record.inputModes.set(0, 2);
        var ingredient = ingredient(record, 0);
        ItemStack restoredDefault = configured.copy();
        restoredDefault.set(DataComponents.DAMAGE, 0);
        assertFalse(ingredient.test(restoredDefault));
        assertTrue(ingredient.test(configured));
        assertNull(shown(ingredient).get(DataComponents.DAMAGE));
        record.inputModes.set(0, 1);
        assertFalse(ingredient(record, 0).test(restoredDefault));
        assertTrue(ingredient(record, 0).test(configured));
    }

    @Test
    void ingredientJsonAndNetworkCodecsKeepComponentRemovalAndMatching() {
        ItemStack input = record().inputs.getFirst();
        input.remove(DataComponents.DAMAGE);
        input.set(DataComponents.CUSTOM_DATA, dev.xyat.contentstudio.item.ItemData.compile("minecraft:diamond_sword","[custom_data={Key:1,LongKey:2L}]").get(DataComponents.CUSTOM_DATA));
        var ingredient = new ComponentPatchIngredient(input.typeHolder(), input.getComponentsPatch(), true);
        var ops = registries().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        var encoded = ComponentPatchIngredient.CODEC.codec().encodeStart(ops, ingredient).getOrThrow();
        assertTrue(encoded.getAsJsonObject().getAsJsonObject("components").has("!minecraft:damage"));
        var jsonDecoded = ComponentPatchIngredient.CODEC.codec().parse(ops, encoded).getOrThrow();
        assertEquals(ingredient, jsonDecoded);
        assertTrue(jsonDecoded.test(input));
        ItemStack restoredDefault = input.copy();
        restoredDefault.set(DataComponents.DAMAGE, 0);
        assertFalse(jsonDecoded.test(restoredDefault));
    }

    // Registry ID codecs require a running NeoForge mod loader; exercised by the runtime fixture.
    public static void networkRoundTrip(net.minecraft.core.RegistryAccess registries) {
        ItemStack input = new ItemStack(Items.DIAMOND_SWORD);
        input.remove(DataComponents.DAMAGE);
        var ingredient = new ComponentPatchIngredient(input.typeHolder(), input.getComponentsPatch(), true);
        ItemStack restoredDefault=input.copy();
        restoredDefault.set(DataComponents.DAMAGE,0);
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
                registries);
        try {
            ComponentPatchIngredient.STREAM_CODEC.encode(buffer, ingredient);
            var networkDecoded = ComponentPatchIngredient.STREAM_CODEC.decode(buffer);
            assertEquals(ingredient, networkDecoded);
            assertTrue(networkDecoded.test(input));
            assertFalse(networkDecoded.test(restoredDefault));
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }

    @Test
    void partialMatchingAllowsUnspecifiedDefaultChangesButChecksRequestedValues() {
        RecipeRecord record = record();
        var partial = ingredient(record, 0);
        ItemStack damaged = record.inputs.getFirst().copy();
        damaged.set(DataComponents.DAMAGE, 12);
        assertTrue(partial.test(damaged));
        damaged.set(DataComponents.CUSTOM_NAME, Component.literal("Wrong name"));
        assertFalse(partial.test(damaged));
        var custom = (ComponentPatchIngredient) partial.getCustomIngredient();
        var display = shown(partial);
        display.set(DataComponents.CUSTOM_NAME, Component.literal("Changed display"));
        assertTrue(custom.test(record.inputs.getFirst()));
        assertEquals(record.inputs.getFirst().get(DataComponents.CUSTOM_NAME), shown(partial).get(DataComponents.CUSTOM_NAME));
    }

    @Test
    void legacyItemNbtIsRejectedRatherThanIgnored() throws Exception {
        JsonObject object = new JsonObject();
        object.addProperty("item", "minecraft:diamond");
        object.addProperty("nbt", "{display:{Name:'old'}}");
        var method = RecipeConfigStore.class.getDeclaredMethod("readStack", JsonObject.class, boolean.class, boolean.class);
        method.setAccessible(true);
        var error = assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> method.invoke(null, object, false, false));
        assertInstanceOf(IllegalArgumentException.class, error.getCause());
        assertTrue(error.getCause().getMessage().contains("nbt"));
    }
}
*///?} else if >=1.21 {
/*
package dev.xyat.contentstudiovalidation;
import dev.xyat.contentstudio.recipe.*;


import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RuntimeRecipeChecks {
    @BeforeAll
    static void bootstrap() {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(),java.util.List.of(),java.util.List.of(),java.util.List.of(),java.util.Map.of());
        try { net.neoforged.fml.loading.FMLPaths.loadAbsolutePaths(java.nio.file.Files.createTempDirectory("contentstudio-tests-")); }
        catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static RecipeRecord record() {
        RecipeRecord record = new RecipeRecord();
        record.editorType = "FURNACE";
        record.outputUseNbt = true;
        record.output = new ItemStack(Items.DIAMOND);
        record.output.set(DataComponents.CUSTOM_NAME, Component.literal("Component output"));
        ItemStack input = new ItemStack(Items.DIAMOND_SWORD);
        input.set(DataComponents.CUSTOM_NAME, Component.literal("Required name"));
        record.inputs.add(input);
        record.inputModes.add(1);
        return record;
    }

    @Test
    void partialAndStrictComponentsHaveDifferentExtraComponentSemantics() {
        RecipeRecord record = record();
        ItemStack extra = record.inputs.getFirst().copy();
        extra.set(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        assertTrue(RecipeMemoryManager.ingredient(record, 0).test(extra));
        record.inputModes.set(0, 2);
        assertFalse(RecipeMemoryManager.ingredient(record, 0).test(extra));
        assertTrue(RecipeMemoryManager.ingredient(record, 0).test(record.inputs.getFirst()));
        extra.set(DataComponents.CUSTOM_NAME, Component.literal("Different"));
        record.inputModes.set(0, 1);
        assertFalse(RecipeMemoryManager.ingredient(record, 0).test(extra));
    }

    @Test
    void holderKeepsConfiguredIdAndOutputComponents() {
        RecipeRecord record = record();
        RecipeHolder<?> holder = RecipeMemoryManager.buildRecipe(record);
        assertEquals(RecipeConfigStore.memoryRecipeId(record), holder.id());
        assertEquals(record.output.get(DataComponents.CUSTOM_NAME),
                holder.value().getResultItem(dev.xyat.contentstudio.item.ItemData.registries()).get(DataComponents.CUSTOM_NAME));
        record.outputUseNbt = false;
        assertNull(RecipeMemoryManager.buildRecipe(record).value()
                .getResultItem(dev.xyat.contentstudio.item.ItemData.registries()).get(DataComponents.CUSTOM_NAME));
    }

    @Test
    void configUsesNativeComponentsAndKeepsCountAndMode() throws Exception {
        RecipeRecord record = record();
        ItemStack stack = record.inputs.getFirst();
        stack.setCount(3);
        stack.set(DataComponents.DAMAGE, 12);
        var write = RecipeConfigStore.class.getDeclaredMethod("writeStack", ItemStack.class, Integer.class);
        write.setAccessible(true);
        JsonObject encoded = (JsonObject) write.invoke(null, stack, 2);
        assertTrue(encoded.has("components"));
        assertFalse(encoded.has("nbt"));
        assertEquals(2, encoded.get("mode").getAsInt());
        assertEquals(3, encoded.get("count").getAsInt());
        var read = RecipeConfigStore.class.getDeclaredMethod("readStack", JsonObject.class, boolean.class, boolean.class);
        read.setAccessible(true);
        ItemStack decoded = (ItemStack) read.invoke(null, encoded, false, false);
        assertTrue(ItemStack.isSameItemSameComponents(stack, decoded));
        assertEquals(stack.getCount(), decoded.getCount());
    }

    @Test
    void recordPersistenceKeepsNativeComponentsAndEmptySlots() {
        RecipeRecord record = record();
        record.inputs.getFirst().set(DataComponents.DAMAGE, 12);
        record.inputs.add(ItemStack.EMPTY);
        record.inputModes.add(0);
        var saved = record.saveToNBT();
        assertTrue(saved.contains("outputUseComponents"));
        assertFalse(saved.contains("outputUse" + "Nbt"));
        RecipeRecord loaded = RecipeRecord.loadFromNBT(saved);
        assertEquals(record.uuid, loaded.uuid);
        assertEquals(record.outputUseNbt, loaded.outputUseNbt);
        assertEquals(record.inputModes, loaded.inputModes);
        assertTrue(ItemStack.isSameItemSameComponents(record.output, loaded.output));
        assertTrue(ItemStack.isSameItemSameComponents(record.inputs.getFirst(), loaded.inputs.getFirst()));
        assertTrue(loaded.inputs.get(1).isEmpty());
    }

    @Test
    void resetRemovesAddedComponentsAndRestoresRemovedDefaults() {
        ItemStack stack = new ItemStack(Items.DIAMOND_SWORD, 7);
        stack.remove(DataComponents.DAMAGE);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Remove me"));
        dev.xyat.contentstudio.item.ItemData.reset(stack);
        assertTrue(ItemStack.isSameItemSameComponents(new ItemStack(Items.DIAMOND_SWORD), stack));
        assertEquals(7, stack.getCount());
    }

    @Test
    void shapedRecipeRetainsTrimmedPatternAndInteriorEmptySlot() {
        RecipeRecord record = record();
        record.editorType = "CRAFTING";
        ItemStack input = record.inputs.getFirst();
        record.inputs.clear();
        record.inputModes.clear();
        for (int i = 0; i < 9; i++) {
            record.inputs.add(i == 1 || i == 7 ? input.copy() : ItemStack.EMPTY);
            record.inputModes.add(1);
        }
        var recipe = (net.minecraft.world.item.crafting.ShapedRecipe) RecipeMemoryManager.buildRecipe(record).value();
        assertEquals(1, recipe.getWidth());
        assertEquals(3, recipe.getHeight());
        assertEquals(3, recipe.getIngredients().size());
        assertTrue(recipe.getIngredients().get(1).isEmpty());
        assertTrue(recipe.getIngredients().getFirst().test(input));
        assertEquals(RecipeConfigStore.memoryRecipeId(record), RecipeMemoryManager.buildRecipe(record).id());
    }

    @Test
    void outputTagRemovalUsesStagedReloadTagsBeforeWorldBinding() {
        var stagedTag = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("example", "new_output_tag");
        var conditions = new net.neoforged.neoforge.common.conditions.ICondition.IContext() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> java.util.Map<net.minecraft.resources.ResourceLocation, java.util.Collection<net.minecraft.core.Holder<T>>>
                    getAllTags(net.minecraft.resources.ResourceKey<? extends net.minecraft.core.Registry<T>> registry) {
                if (!registry.equals(net.minecraft.core.registries.Registries.ITEM)) return java.util.Map.of();
                return java.util.Map.of(stagedTag, java.util.List.of(
                        (net.minecraft.core.Holder<T>) Items.DIAMOND.builtInRegistryHolder()));
            }
        };
        var candidate = RecipeMemoryManager.candidateOf(RecipeMemoryManager.buildRecipe(record()),
                dev.xyat.contentstudio.item.ItemData.registries(), conditions);
        assertEquals(java.util.Set.of(stagedTag), candidate.outputTagIds());
        assertTrue(dev.xyat.contentstudio.recipe.removal.RemovalRuleEvaluator.blockingRules(candidate,
                java.util.List.of(new dev.xyat.contentstudio.recipe.removal.RemovalEntry(
                        dev.xyat.contentstudio.recipe.removal.RemovalMode.TAG, stagedTag.toString(), ""))).size() == 1);
    }

    @Test
    void strictInputPreservesRemovedDefaultComponents() {
        RecipeRecord record = record();
        ItemStack configured = record.inputs.getFirst();
        configured.remove(DataComponents.DAMAGE);
        record.inputModes.set(0, 2);
        var ingredient = RecipeMemoryManager.ingredient(record, 0);
        ItemStack restoredDefault = configured.copy();
        restoredDefault.set(DataComponents.DAMAGE, 0);
        assertFalse(ingredient.test(restoredDefault));
        assertTrue(ingredient.test(configured));
        assertNull(ingredient.getItems()[0].get(DataComponents.DAMAGE));
        record.inputModes.set(0, 1);
        assertFalse(RecipeMemoryManager.ingredient(record, 0).test(restoredDefault));
        assertTrue(RecipeMemoryManager.ingredient(record, 0).test(configured));
    }

    @Test
    void ingredientJsonAndNetworkCodecsKeepComponentRemovalAndMatching() {
        ItemStack input = record().inputs.getFirst();
        input.remove(DataComponents.DAMAGE);
        input.set(DataComponents.CUSTOM_DATA, dev.xyat.contentstudio.item.ItemData.compile("minecraft:diamond_sword","[custom_data={Key:1,LongKey:2L}]").get(DataComponents.CUSTOM_DATA));
        var ingredient = new ComponentPatchIngredient(input.getItemHolder(), input.getComponentsPatch(), true);
        var ops = dev.xyat.contentstudio.item.ItemData.registries().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        var encoded = ComponentPatchIngredient.CODEC.codec().encodeStart(ops, ingredient).getOrThrow();
        assertTrue(encoded.getAsJsonObject().getAsJsonObject("components").has("!minecraft:damage"));
        var jsonDecoded = ComponentPatchIngredient.CODEC.codec().parse(ops, encoded).getOrThrow();
        assertEquals(ingredient, jsonDecoded);
        assertTrue(jsonDecoded.test(input));
        ItemStack restoredDefault = input.copy();
        restoredDefault.set(DataComponents.DAMAGE, 0);
        assertFalse(jsonDecoded.test(restoredDefault));
    }

    // Registry ID codecs require a running NeoForge mod loader; exercised by the runtime fixture.
    public static void networkRoundTrip(net.minecraft.core.RegistryAccess registries) {
        ItemStack input = new ItemStack(Items.DIAMOND_SWORD);
        input.remove(DataComponents.DAMAGE);
        var ingredient = new ComponentPatchIngredient(input.getItemHolder(), input.getComponentsPatch(), true);
        ItemStack restoredDefault=input.copy();
        restoredDefault.set(DataComponents.DAMAGE,0);
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),
                registries);
        try {
            ComponentPatchIngredient.STREAM_CODEC.encode(buffer, ingredient);
            var networkDecoded = ComponentPatchIngredient.STREAM_CODEC.decode(buffer);
            assertEquals(ingredient, networkDecoded);
            assertTrue(networkDecoded.test(input));
            assertFalse(networkDecoded.test(restoredDefault));
            assertFalse(buffer.isReadable());
        } finally {
            buffer.release();
        }
    }

    @Test
    void partialMatchingAllowsUnspecifiedDefaultChangesButChecksRequestedValues() {
        RecipeRecord record = record();
        var partial = RecipeMemoryManager.ingredient(record, 0);
        ItemStack damaged = record.inputs.getFirst().copy();
        damaged.set(DataComponents.DAMAGE, 12);
        assertTrue(partial.test(damaged));
        damaged.set(DataComponents.CUSTOM_NAME, Component.literal("Wrong name"));
        assertFalse(partial.test(damaged));
        var custom = (ComponentPatchIngredient) partial.getCustomIngredient();
        var display = custom.getItems().findFirst().orElseThrow();
        display.set(DataComponents.CUSTOM_NAME, Component.literal("Changed display"));
        assertTrue(custom.test(record.inputs.getFirst()));
        assertEquals(record.inputs.getFirst().get(DataComponents.CUSTOM_NAME),
                custom.getItems().findFirst().orElseThrow().get(DataComponents.CUSTOM_NAME));
    }

    @Test
    void legacyItemNbtIsRejectedRatherThanIgnored() throws Exception {
        JsonObject object = new JsonObject();
        object.addProperty("item", "minecraft:diamond");
        object.addProperty("nbt", "{display:{Name:'old'}}");
        var method = RecipeConfigStore.class.getDeclaredMethod("readStack", JsonObject.class, boolean.class, boolean.class);
        method.setAccessible(true);
        var error = assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> method.invoke(null, object, false, false));
        assertInstanceOf(IllegalArgumentException.class, error.getCause());
        assertTrue(error.getCause().getMessage().contains("nbt"));
    }
}
*///?}