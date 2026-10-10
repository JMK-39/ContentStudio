package dev.xyat.contentstudio.recipe.nativeedit;

import com.google.gson.*;
import java.util.List;

/** Create's sequence operations modify the original JSON rather than rebuilding a reduced recipe model. */
public final class NativeRecipeSequenceEdits {
    public static final List<String> TYPES=List.of("create:deploying","create:pressing","create:cutting","create:filling");
    private NativeRecipeSequenceEdits() { }
    public static void add(NativeRecipeDocument document,List<String> root,String type) {
        if(!TYPES.contains(type))throw new IllegalArgumentException("Unsupported sequenced step");
        var json=document.at(root).getAsJsonObject();var sequence=json.getAsJsonArray("sequence");
        for(var value:sequence)if(value.isJsonObject()&&value.getAsJsonObject().has("type")&&value.getAsJsonObject().get("type").getAsString().equals(type)) {
            document.add(NativeRecipeLayout.append(root,"sequence"),"",value);return;
        }
        var transitional=json.get("transitionalItem").deepCopy();
        String id=transitional.isJsonPrimitive()?transitional.getAsString():transitional.getAsJsonObject().get(transitional.getAsJsonObject().has("id")?"id":"item").getAsString();
        var ingredient=new JsonObject();ingredient.addProperty("item",id);
        var inputs=new JsonArray();inputs.add(ingredient);var outputs=new JsonArray();outputs.add(transitional);
        if(type.equals("create:deploying"))inputs.add(ingredient.deepCopy());
        else if(type.equals("create:filling")) {var fluid=new JsonObject();fluid.addProperty("fluid","minecraft:water");fluid.addProperty("amount",250);inputs.add(fluid);}
        var step=new JsonObject();step.addProperty("type",type);step.add("ingredients",inputs);step.add("results",outputs);
        if(type.equals("create:cutting"))step.addProperty("processingTime",100);
        if(type.equals("create:deploying"))step.addProperty("keepHeldItem",false);
        document.add(NativeRecipeLayout.append(root,"sequence"),"",step);
    }
}
