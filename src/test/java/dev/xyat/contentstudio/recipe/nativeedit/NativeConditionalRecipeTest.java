package dev.xyat.contentstudio.recipe.nativeedit;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeConditionalRecipeTest {
    @Test void selectsActiveBranchAndPreservesInactiveBranchesWhenEditing() {
        var source=JsonParser.parseString("{\"type\":\"forge:conditional\",\"recipes\":[{\"enabled\":false,\"recipe\":{\"type\":\"minecraft:stonecutting\",\"ingredient\":\"minecraft:dirt\",\"result\":\"minecraft:stone\"}},{\"enabled\":true,\"recipe\":{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\"AA\"],\"key\":{\"A\":{\"tag\":\"minecraft:planks\"}},\"result\":{\"item\":\"minecraft:stick\"}}}]}").getAsJsonObject();
        var inactive=source.getAsJsonArray("recipes").get(0).deepCopy();
        var view=NativeRecipeView.resolve(source, branch->branch.get("enabled").getAsBoolean());
        assertEquals(java.util.List.of("recipes","1","recipe"),view.path());
        var document=new NativeRecipeDocument(source);var layout=view.layout(0);
        NativeRecipeSlotEdits.remove(document,layout,0);
        assertEquals(" A",document.at(java.util.List.of("recipes","1","recipe","pattern","0")).getAsString());
        assertEquals(inactive,document.json().getAsJsonArray("recipes").get(0));
        assertEquals("forge:conditional",NativeRecipeDocument.serializerId(document.json()));
        assertEquals(2,document.json().getAsJsonArray("recipes").size());
    }
    @Test void inactiveConditionalRecipeIsRejected() {
        var source=JsonParser.parseString("{\"type\":\"forge:conditional\",\"recipes\":[{\"recipe\":{\"type\":\"minecraft:stonecutting\"}}]}").getAsJsonObject();
        assertThrows(IllegalArgumentException.class,()->NativeRecipeView.resolve(source,branch->false));
    }
    @Test void conditionalStonecutterShowsItsIngredientsAndOutput() {
        var source=JsonParser.parseString("{\"type\":\"forge:conditional\",\"recipes\":[{\"conditions\":[],\"recipe\":{\"type\":\"minecraft:stonecutting\",\"ingredient\":{\"item\":\"minecraft:stone\"},\"result\":\"minecraft:stone_slab\",\"count\":2}}]}").getAsJsonObject();
        var layout=NativeRecipeLayout.of(source);
        assertEquals(2,layout.slots().size(),"conditional wrapper must expose the native recipe slots");
        assertEquals(java.util.List.of("recipes","0","recipe","ingredient"),layout.slots().get(0).path());
    }
}
