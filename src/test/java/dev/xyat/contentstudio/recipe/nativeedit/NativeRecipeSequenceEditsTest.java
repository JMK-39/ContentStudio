package dev.xyat.contentstudio.recipe.nativeedit;

import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeRecipeSequenceEditsTest {
    private NativeRecipeDocument draft() {
        return new NativeRecipeDocument(JsonParser.parseString("""
                {"type":"create:sequenced_assembly","ingredient":{"tag":"forge:plates/gold"},
                 "transitionalItem":{"item":"create:incomplete_precision_mechanism"},"loops":5,
                 "sequence":[{"type":"create:pressing","ingredients":[{"item":"create:incomplete_precision_mechanism"}],
                 "results":[{"item":"create:incomplete_precision_mechanism"}],"custom":{"preserve":true}},
                 {"type":"create:cutting","processingTime":80,"ingredients":[{"item":"create:incomplete_precision_mechanism"}],
                  "results":[{"item":"create:incomplete_precision_mechanism"}]}],"results":[{"item":"create:precision_mechanism","chance":0.8}]}
                """).getAsJsonObject());
    }
    @Test void addingAndReorderingStepsPreservesRecipeAndStepMetadata() throws Exception {
        var doc=draft();
        Class.forName("dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeSequenceEdits")
                .getMethod("add",NativeRecipeDocument.class,List.class,String.class)
                .invoke(null,doc,List.of(),"create:deploying");
        doc.getClass().getMethod("move",List.class,int.class).invoke(doc,List.of("sequence","2"),0);
        assertEquals("create:deploying",doc.at(List.of("sequence","0","type")).getAsString());
        assertEquals("create:incomplete_precision_mechanism",doc.at(List.of("sequence","0","ingredients","0","item")).getAsString());
        assertTrue(doc.at(List.of("sequence","1","custom","preserve")).getAsBoolean());
        assertEquals(0.8,doc.at(List.of("results","0","chance")).getAsDouble());
        assertEquals(5,doc.at(List.of("loops")).getAsInt());
    }
    @Test void fillingAndDeployingGetAnAdditionalMaterialButPressingDoesNot() throws Exception {
        for(String type:List.of("create:filling","create:deploying","create:pressing","create:cutting")) {
            var doc=draft();Class.forName("dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeSequenceEdits")
                    .getMethod("add",NativeRecipeDocument.class,List.class,String.class).invoke(null,doc,List.of(),type);
            var step=doc.at(List.of("sequence","2")).getAsJsonObject();
            assertEquals(type.equals("create:filling")||type.equals("create:deploying")?2:1,step.getAsJsonArray("ingredients").size());
            assertEquals("create:incomplete_precision_mechanism",step.getAsJsonArray("results").get(0).getAsJsonObject().get("item").getAsString());
        }
    }
}
