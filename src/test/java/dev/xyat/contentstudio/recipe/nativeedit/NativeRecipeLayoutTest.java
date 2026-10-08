package dev.xyat.contentstudio.recipe.nativeedit;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NativeRecipeLayoutTest {
    @Test void cauldronKeepsItsBaseFluidSeparateFromItsReagent() {
        var json=JsonParser.parseString("{\"type\":\"irons_spellbooks:alchemist_cauldron_brew\",\"base_fluid\":{\"FluidName\":\"minecraft:water\",\"Amount\":1000},\"input\":\"minecraft:gold_ingot\",\"results\":[{\"FluidName\":\"minecraft:lava\",\"Amount\":250}]}").getAsJsonObject();
        assertTrue(NativeRecipeLayout.of(json).slots().stream().anyMatch(slot->slot.path().equals(List.of("base_fluid"))));
    }
    @Test void incompleteAdvancedDraftsKeepSafeEditablePreviews() {
        for(String text:List.of(
                "{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\"A\",{}],\"key\":{\"A\":\"minecraft:stone\"}}",
                "{\"type\":\"create:sequenced_assembly\",\"sequence\":{}}",
                "{\"type\":\"eidolon:crucible\",\"steps\":false}")) {
            var json=JsonParser.parseString(text).getAsJsonObject();String before=json.toString();
            assertDoesNotThrow(()->NativeRecipeLayout.of(json));assertEquals(before,json.toString());
        }
    }
    @Test void crucibleStepsAndEntropyDropsHaveIndividualEditableSlots() {
        var crucible=JsonParser.parseString("{\"type\":\"eidolon:crucible\",\"steps\":[{\"items\":[{\"item\":\"minecraft:iron_ingot\"}]},{\"items\":[{\"item\":\"minecraft:gold_ingot\"}]}],\"result\":{\"item\":\"minecraft:diamond\"}}").getAsJsonObject();
        var slots=NativeRecipeLayout.of(crucible).slots();
        assertTrue(slots.stream().anyMatch(s->s.path().equals(List.of("steps","0","items","0"))));
        assertTrue(slots.stream().anyMatch(s->s.path().equals(List.of("steps","1","items","0"))));
        var entropy=JsonParser.parseString("{\"type\":\"ae2:entropy\",\"output\":{\"drops\":[{\"item\":\"minecraft:snowball\"},{\"item\":\"minecraft:ice\"}]}}").getAsJsonObject();
        assertEquals(2,NativeRecipeLayout.of(entropy).slots().stream().filter(NativeRecipeLayout.Slot::output).count());
    }
    @Test void legacyAndModernMachineSchemasUseTheSameInputOutputPositions() {
        var legacy=JsonParser.parseString("{\"type\":\"mekanism:reaction\",\"itemInput\":{\"ingredient\":{\"item\":\"minecraft:stone\"}},\"gasInput\":{\"gas\":\"mekanism:plutonium\"},\"fluidInput\":{\"tag\":\"minecraft:water\"},\"itemOutput\":{\"item\":\"minecraft:diamond\"},\"gasOutput\":{\"gas\":\"mekanism:spent_nuclear_waste\"}}").getAsJsonObject();
        var modern=JsonParser.parseString("{\"type\":\"mekanism:reaction\",\"item_input\":{\"item\":\"minecraft:stone\"},\"chemical_input\":{\"chemical\":\"mekanism:plutonium\"},\"fluid_input\":{\"tag\":\"minecraft:water\"},\"item_output\":{\"id\":\"minecraft:diamond\"},\"chemical_output\":{\"id\":\"mekanism:spent_nuclear_waste\"}}").getAsJsonObject();
        var oldSlots=NativeRecipeLayout.of(legacy).slots();var newSlots=NativeRecipeLayout.of(modern).slots();
        assertEquals(newSlots.size(),oldSlots.size());
        for(int i=0;i<newSlots.size();i++){assertEquals(newSlots.get(i).x(),oldSlots.get(i).x());assertEquals(newSlots.get(i).y(),oldSlots.get(i).y());assertEquals(newSlots.get(i).output(),oldSlots.get(i).output());}
    }
    @Test void inscriberNamedIngredientsHaveIndividualClickableSlots() {
        var json=JsonParser.parseString("{\"type\":\"ae2:inscriber\",\"ingredients\":{\"top\":{\"item\":\"minecraft:diamond\"},\"middle\":{\"item\":\"minecraft:iron_ingot\"}},\"result\":{\"id\":\"minecraft:stone\"}}").getAsJsonObject();
        var layout=NativeRecipeLayout.of(json);
        assertTrue(layout.slots().stream().anyMatch(s->s.path().equals(List.of("ingredients","top"))));
        assertTrue(layout.slots().stream().anyMatch(s->s.path().equals(List.of("ingredients","middle"))));
    }
    @Test void thirdPartyEntriesUseTheirMachinesInsteadOfARecipeSerializerAsAnItemId() {
        assertEquals("mekanism:metallurgic_infuser",NativeRecipeLayout.stationCandidates("mekanism:metallurgic_infusing").get(0));
        assertEquals("cataclysm:mechanical_fusion_anvil",NativeRecipeLayout.stationCandidates("cataclysm:weapon_fusion").get(0));
        assertEquals("goety:dark_altar",NativeRecipeLayout.stationCandidates("goety:ritual").get(0));
        assertEquals("industrialforegoing:ore_laser_base",NativeRecipeLayout.stationCandidates("industrialforegoing:laser_drill_ore").get(0));
        assertEquals("industrialforegoing:fluid_laser_base",NativeRecipeLayout.stationCandidates("industrialforegoing:laser_drill_fluid").get(0));
        assertEquals("alloy_smelter:forge_controller_tier1",NativeRecipeLayout.stationCandidates("alloy_smelter:smelting").get(0));
        assertEquals("tacz:gun_smith_table",NativeRecipeLayout.stationCandidates("tacz:gun_smith_table_crafting").get(0));
        assertEquals("iceandfire:dragonforge_fire_core",NativeRecipeLayout.stationCandidates("iceandfire:dragonforge").get(0));
        assertEquals("ars_nouveau:scribes_table",NativeRecipeLayout.stationCandidates("ars_nouveau:glyph").get(0));
    }
    @Test void modernSequenceUsesNativeTransitionalItemName() {
        var json=JsonParser.parseString("{\"type\":\"create:sequenced_assembly\",\"ingredient\":\"minecraft:iron_ingot\",\"transitional_item\":{\"id\":\"create:incomplete_precision_mechanism\"},\"sequence\":[],\"results\":[{\"id\":\"minecraft:diamond\"}]}").getAsJsonObject();
        assertTrue(NativeRecipeLayout.of(json).slots().stream().anyMatch(s->s.path().equals(List.of("transitional_item"))));
    }
    @Test void moddedShapedCraftingKeepsNativeGridAndSeparateOutput() {
        var json=JsonParser.parseString("{\"type\":\"extendedcrafting:shaped_table\",\"pattern\":[\"AAAAA\",\"A   A\",\"AAAAA\"],\"key\":{\"A\":{\"item\":\"minecraft:diamond\"}},\"result\":{\"id\":\"minecraft:diamond_block\"}}").getAsJsonObject();
        var layout=NativeRecipeLayout.of(json);
        assertEquals("crafting",layout.kind());assertEquals(12,layout.slots().stream().filter(s->!s.output()).count());
        assertTrue(layout.slots().stream().filter(NativeRecipeLayout.Slot::output).allMatch(s->s.x()>=140));
    }
    @Test void apparatusAndMachineRolesRemainSeparateWithoutTraversingNbt() {
        var ars=JsonParser.parseString("{\"type\":\"ars_nouveau:enchanting_apparatus\",\"pedestalItems\":[{\"item\":\"minecraft:gold_ingot\"}],\"reagent\":{\"item\":\"minecraft:stone\"},\"result\":{\"id\":\"minecraft:diamond\"}}").getAsJsonObject();
        assertEquals(3,NativeRecipeLayout.of(ars).slots().size());
        var mekanism=JsonParser.parseString("{\"type\":\"mekanism:reaction\",\"item_input\":{\"item\":\"minecraft:stone\"},\"chemical_input\":{\"chemical\":\"mekanism:plutonium\",\"amount\":1000},\"fluid_input\":{\"fluid\":\"minecraft:water\"},\"chemical_output\":{\"id\":\"mekanism:spent_nuclear_waste\"},\"item_output\":{\"id\":\"minecraft:diamond\",\"components\":{\"example:container\":{\"item\":\"minecraft:emerald\"}}}}").getAsJsonObject();
        var layout=NativeRecipeLayout.of(mekanism);assertEquals(3,layout.slots().stream().filter(s->!s.output()).count());assertEquals(2,layout.slots().stream().filter(NativeRecipeLayout.Slot::output).count());
    }
    @Test void cuttingKeepsToolInputAndChanceOutputsInTheirOwnPositions() {
        var json=JsonParser.parseString("{\"type\":\"farmersdelight:cutting\",\"ingredients\":[{\"item\":\"minecraft:oak_log\"}],\"tool\":[{\"tag\":\"minecraft:axes\"}],\"result\":[{\"item\":\"minecraft:oak_planks\",\"count\":4},{\"item\":{\"id\":\"minecraft:stick\",\"count\":2},\"chance\":0.5}]}").getAsJsonObject();
        var layout=NativeRecipeLayout.of(json);
        assertEquals("farmersdelight:cutting_board",NativeRecipeLayout.stationCandidates("farmersdelight:cutting").get(0));
        assertEquals("cutting",layout.kind());
        assertEquals(List.of("tool"),layout.slots().get(0).path());
        assertEquals(List.of("ingredients","0"),layout.slots().get(1).path());
        assertEquals(2,layout.slots().stream().filter(NativeRecipeLayout.Slot::output).count());
        assertNotEquals(layout.slots().get(0).y(),layout.slots().get(1).y());
    }
    @Test void shapedGridKeepsSpacesAndRepeatedKeysWithoutTreatingComponentsAsInputs() {
        var json=JsonParser.parseString("{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\" A \",\" B \"],\"key\":{\"A\":{\"item\":\"minecraft:diamond\"},\"B\":{\"item\":\"minecraft:stick\"}},\"result\":{\"id\":\"minecraft:diamond_sword\",\"components\":{\"mod:container\":{\"item\":\"minecraft:emerald\"}}}}").getAsJsonObject();
        var layout=NativeRecipeLayout.of(json);
        assertEquals(3,layout.slots().size());
        assertEquals(List.of("key","A"),layout.slots().get(0).path());
        assertEquals(layout.slots().get(0).x(),layout.slots().get(1).x());
        assertTrue(layout.slots().get(1).y()>layout.slots().get(0).y());
    }
    @Test void cookingShowsSixIngredientsAndSeparateContainerWithoutJei() {
        var json=JsonParser.parseString("{\"type\":\"farmersdelight:cooking\",\"ingredients\":[\"minecraft:carrot\",\"minecraft:potato\"],\"container\":{\"item\":\"minecraft:bowl\"},\"result\":{\"id\":\"minecraft:mushroom_stew\"},\"cookingtime\":200,\"experience\":0.5}").getAsJsonObject();
        var layout=NativeRecipeLayout.of(json);
        assertEquals("cooking_pot",layout.kind());
        assertTrue(layout.slots().stream().anyMatch(s->s.path().equals(List.of("container"))));
        assertEquals("farmersdelight:cooking_pot",NativeRecipeLayout.stationCandidates("farmersdelight:cooking").get(0));
    }
    @Test void sequencedAssemblyKeepsStepsAndTransitionalItemEditableAsSeparateNodes() {
        var json=JsonParser.parseString("{\"type\":\"create:sequenced_assembly\",\"ingredient\":{\"tag\":\"forge:plates/gold\"},\"transitionalItem\":{\"item\":\"create:incomplete_precision_mechanism\"},\"sequence\":[{\"type\":\"create:deploying\",\"ingredients\":[{\"item\":\"create:incomplete_precision_mechanism\"},{\"item\":\"create:cogwheel\"}],\"results\":[{\"item\":\"create:incomplete_precision_mechanism\"}]}],\"results\":[{\"item\":\"create:precision_mechanism\",\"chance\":120}],\"loops\":5}").getAsJsonObject();
        var layout=NativeRecipeLayout.of(json);
        assertEquals("create_sequence",layout.kind());
        for(var path:List.of(List.of("ingredient"),List.of("transitionalItem"),List.of("sequence","0"),List.of("sequence","0","ingredients","1"),List.of("results","0")))
            assertTrue(layout.slots().stream().anyMatch(s->s.path().equals(path)),path.toString());
    }
    @Test void serializersWithSeveralOutputFieldsNeverOverlapTheirSlots() {
        var json=JsonParser.parseString("{\"type\":\"example:machine\",\"input\":\"minecraft:stone\",\"result\":\"minecraft:dirt\",\"outputs\":[\"minecraft:gravel\"]}").getAsJsonObject();
        var outputs=NativeRecipeLayout.of(json).slots().stream().filter(NativeRecipeLayout.Slot::output).toList();
        assertEquals(2,outputs.size());assertTrue(outputs.get(0).x()!=outputs.get(1).x() || outputs.get(0).y()!=outputs.get(1).y());
    }
}
