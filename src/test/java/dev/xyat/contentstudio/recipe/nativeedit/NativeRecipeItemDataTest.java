package dev.xyat.contentstudio.recipe.nativeedit;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NativeRecipeItemDataTest {
    private static NativeRecipeDocument draft(String json) {
        return new NativeRecipeDocument(JsonParser.parseString(json).getAsJsonObject());
    }
    private static JsonElement preview(NativeRecipeDocument doc, List<String> path) throws Exception {
        return (JsonElement) Class.forName("dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeItemData")
                .getMethod("preview", NativeRecipeDocument.class, List.class).invoke(null, doc, path);
    }
    private static void select(NativeRecipeDocument doc, List<String> path, String stack, boolean output) throws Exception {
        Class.forName("dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeItemData")
                .getMethod("select", NativeRecipeDocument.class, List.class, JsonObject.class, boolean.class)
                .invoke(null, doc, path, JsonParser.parseString(stack).getAsJsonObject(), output);
    }
    @Test void itemFieldIconIncludesTheAmmoIdInsteadOfRenderingTheBareRegistryItem() throws Exception {
        var doc=draft("{\"type\":\"create:cutting\",\"results\":[{\"item\":\"tacz:ammo\",\"count\":64,\"nbt\":\"{AmmoId:'tacz:9mm'}\"}]}");
        assertEquals(doc.at(List.of("results","0")), preview(doc,List.of("results","0","item")));
    }
    @Test void nativeTaczIdAndComponentFieldsUseTheirCompleteOutputForPreviews() throws Exception {
        for(String output:List.of("{\"type\":\"ammo\",\"id\":\"tacz:9mm\",\"count\":30}",
                "{\"id\":\"minecraft:potion\",\"components\":{\"minecraft:potion_contents\":{\"potion\":\"minecraft:healing\"}}}")) {
            var doc=draft("{\"type\":\"test:recipe\",\"result\":"+output+"}");
            assertEquals(doc.at(List.of("result")), preview(doc,List.of("result","id")));
        }
    }
    @Test void selectingAmmoPreservesItsNbtAndTheRecipeQuantityAndChance() throws Exception {
        var doc=draft("{\"type\":\"create:cutting\",\"results\":[{\"item\":\"minecraft:stone\",\"count\":64,\"chance\":0.5}]}");
        select(doc,List.of("results","0"),"{\"item\":\"tacz:ammo\",\"count\":1,\"nbt\":\"{AmmoId:'tacz:9mm'}\"}",true);
        assertEquals("tacz:ammo", doc.at(List.of("results","0","item")).getAsString());
        assertEquals("{AmmoId:'tacz:9mm'}", doc.at(List.of("results","0","nbt")).getAsString());
        assertEquals(64, doc.at(List.of("results","0","count")).getAsInt());
        assertEquals(0.5, doc.at(List.of("results","0","chance")).getAsDouble());
    }
    @Test void replacingAnItemClearsThePreviousItemsNbt() throws Exception {
        var doc=draft("{\"type\":\"create:cutting\",\"results\":[{\"item\":\"tacz:ammo\",\"nbt\":\"{AmmoId:'tacz:9mm'}\",\"count\":64}]}");
        select(doc,List.of("results","0"),"{\"item\":\"minecraft:diamond\"}",true);
        assertFalse(doc.at(List.of("results","0")).getAsJsonObject().has("nbt"));
        assertEquals(64,doc.at(List.of("results","0","count")).getAsInt());
    }
    @Test void selectingAComponentVariantRetainsItsNativeDataInsideACountedWrapper() throws Exception {
        var doc=draft("{\"type\":\"test:machine\",\"result\":{\"item\":{\"id\":\"minecraft:stone\"},\"count\":4}}");
        select(doc,List.of("result"),"{\"id\":\"minecraft:potion\",\"components\":{\"minecraft:potion_contents\":{\"potion\":\"minecraft:healing\"}}}",true);
        assertEquals("minecraft:healing",doc.at(List.of("result","item","components","minecraft:potion_contents","potion")).getAsString());
        assertEquals(4,doc.at(List.of("result","count")).getAsInt());
    }
    @Test void changingANativeTaczResultKeepsItsRawResultFormat() throws Exception {
        var doc=draft("{\"type\":\"tacz:gun_smith_table_crafting\",\"result\":{\"type\":\"ammo\",\"id\":\"tacz:9mm\",\"count\":64}}");
        select(doc,List.of("result"),"{\"item\":\"tacz:ammo\",\"nbt\":\"{AmmoId:'tacz:12g'}\"}",true);
        assertEquals("ammo",doc.at(List.of("result","type")).getAsString());
        assertEquals("tacz:12g",doc.at(List.of("result","id")).getAsString());
        assertFalse(doc.at(List.of("result")).getAsJsonObject().has("item"));
        assertEquals(64,doc.at(List.of("result","count")).getAsInt());
    }
    @Test void selectingTaggedInputsEncodesAnNbtAwareIngredient() throws Exception {
        var doc=draft("{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"minecraft:stone\"}],\"result\":{\"item\":\"minecraft:diamond\"}}");
        select(doc,List.of("ingredients","0"),"{\"item\":\"tacz:ammo\",\"nbt\":\"{AmmoId:'tacz:9mm'}\"}",false);
        assertEquals("forge:nbt",doc.at(List.of("ingredients","0","type")).getAsString());
        assertEquals("{AmmoId:'tacz:9mm'}",doc.at(List.of("ingredients","0","nbt")).getAsString());
    }
    @Test void sequenceStepAndConditionalBranchOutputsAreNotEncodedAsIngredients() throws Exception {
        var doc=draft("{\"type\":\"create:sequenced_assembly\",\"ingredient\":{\"item\":\"minecraft:iron_ingot\"},\"sequence\":[{\"type\":\"create:deploying\",\"ingredients\":[{\"item\":\"minecraft:iron_ingot\"}],\"results\":[{\"item\":\"minecraft:iron_ingot\"}]}],\"results\":[{\"item\":\"minecraft:diamond\"}]}");
        var method=Class.forName("dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeItemData").getMethod("outputField",NativeRecipeDocument.class,List.class);
        assertEquals(true,method.invoke(null,doc,List.of("sequence","0","results","0","item")));
        assertEquals(false,method.invoke(null,doc,List.of("sequence","0","ingredients","0","item")));
        var conditional=draft("{\"type\":\"forge:conditional\",\"recipes\":[{\"conditions\":[],\"recipe\":{\"type\":\"create:cutting\",\"results\":[{\"item\":\"minecraft:stone\"}]}},{\"conditions\":[],\"recipe\":{\"type\":\"create:cutting\",\"results\":[{\"item\":\"minecraft:diamond\"}]}}]}");
        assertEquals(true,method.invoke(null,conditional,List.of("recipes","1","recipe","results","0","item")));
    }
    @Test void plainComponentEraStackUsesTheIngredientItemKey() throws Exception {
        var doc=draft("{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"minecraft:stone\"}],\"result\":{\"id\":\"minecraft:diamond\"}}");
        select(doc,List.of("ingredients","0"),"{\"id\":\"minecraft:emerald\"}",false);
        assertEquals(JsonParser.parseString("{\"item\":\"minecraft:emerald\"}"),doc.at(List.of("ingredients","0")));
    }
    @Test void nativeTaczResultCanBeReplacedWithAnOrdinaryItemWithoutLosingItsQuantity() throws Exception {
        var doc=draft("{\"type\":\"tacz:gun_smith_table_crafting\",\"result\":{\"type\":\"ammo\",\"id\":\"tacz:9mm\",\"count\":64,\"group\":\"tacz:ammo\"}}");
        select(doc,List.of("result"),"{\"item\":\"minecraft:diamond\",\"nbt\":\"{RepairMarker:1b}\"}",true);
        assertEquals("custom",doc.at(List.of("result","type")).getAsString());
        assertEquals("minecraft:diamond",doc.at(List.of("result","item","item")).getAsString());
        assertEquals(64,doc.at(List.of("result","item","count")).getAsInt());
        assertEquals("{RepairMarker:1b}",doc.at(List.of("result","item","nbt")).getAsString());
        assertEquals("tacz:ammo",doc.at(List.of("result","group")).getAsString());
    }
    @Test void nativeTaczReplacementUsesTheEffectiveNestedCustomQuantity() throws Exception {
        var doc=draft("{\"type\":\"tacz:gun_smith_table_crafting\",\"result\":{\"type\":\"custom\",\"item\":{\"item\":\"minecraft:diamond\",\"count\":32},\"count\":64}}");
        select(doc,List.of("result"),"{\"item\":\"minecraft:emerald\"}",true);
        assertEquals(32,doc.at(List.of("result","item","count")).getAsInt());
        assertFalse(doc.at(List.of("result")).getAsJsonObject().has("count"));
        select(doc,List.of("result"),"{\"item\":\"tacz:ammo\",\"nbt\":\"{AmmoId:'tacz:9mm'}\"}",true);
        assertEquals(32,doc.at(List.of("result","count")).getAsInt());
    }
    @Test void changingAnNbtIngredientToATagClearsItsItemConstraint() throws Exception {
        var doc=draft("{\"type\":\"test:machine\",\"ingredients\":[{\"type\":\"forge:nbt\",\"item\":\"tacz:ammo\",\"nbt\":\"{AmmoId:'tacz:9mm'}\",\"count\":4}]}");
        select(doc,List.of("ingredients","0"),"{\"tag\":\"forge:ingots/iron\"}",false);
        assertEquals(JsonParser.parseString("{\"tag\":\"forge:ingots/iron\",\"count\":4}"),doc.at(List.of("ingredients","0")));
    }
    @Test void primitiveIngredientsRetainTheNativeTagShorthand() throws Exception {
        var doc=draft("{\"type\":\"test:machine\",\"ingredients\":[\"minecraft:stone\"]}");
        select(doc,List.of("ingredients","0"),"{\"tag\":\"forge:ingots/iron\"}",false);
        assertEquals("#forge:ingots/iron",doc.at(List.of("ingredients","0")).getAsString());
    }
    //? if <1.21 {
    @Test void unsupportedStonecuttingNbtSelectionLeavesTheDraftUnchanged() throws Exception {
        var doc=draft("{\"type\":\"minecraft:stonecutting\",\"ingredient\":{\"item\":\"minecraft:stone\"},\"result\":\"minecraft:stone_slab\",\"count\":2}");
        var original=doc.json();
        var error=assertThrows(java.lang.reflect.InvocationTargetException.class,()->select(doc,List.of("result"),"{\"item\":\"tacz:ammo\",\"nbt\":\"{AmmoId:'tacz:9mm'}\"}",true));
        assertInstanceOf(IllegalArgumentException.class,error.getCause());
        assertEquals(original,doc.json());
    }
    //?}
}
