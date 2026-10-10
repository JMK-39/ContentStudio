package dev.xyat.contentstudio.recipe.nativeedit;

import com.google.gson.*;
import java.util.*;
import java.util.function.Predicate;

/** A view into a conditional recipe; edits retain the surrounding conditions and inactive branches. */
public record NativeRecipeView(JsonObject recipe, List<String> path) {
    public NativeRecipeView { path=List.copyOf(path); }
    public static NativeRecipeView resolve(JsonObject source, Predicate<JsonObject> matches) {
        var recipe=source;var path=new ArrayList<String>();
        for(int depth=0;depth<32 && NativeRecipeDocument.serializerId(recipe).equals("forge:conditional");depth++) {
            var candidates=recipe.getAsJsonArray("recipes");JsonObject selected=null;
            for(int i=0;i<candidates.size();i++) {
                var branch=candidates.get(i).getAsJsonObject();
                if(matches.test(branch)) {selected=branch.getAsJsonObject("recipe");path.addAll(List.of("recipes",Integer.toString(i),"recipe"));break;}
            }
            if(selected==null)throw new IllegalArgumentException("Recipe conditions are inactive");
            recipe=selected;
        }
        return new NativeRecipeView(recipe,List.copyOf(path));
    }
    public static NativeRecipeView at(NativeRecipeDocument document,List<String> path) {
        return new NativeRecipeView(document.at(path).getAsJsonObject(),path);
    }
    public NativeRecipeLayout layout(int page) {
        var layout=NativeRecipeLayout.of(recipe,page);
        return new NativeRecipeLayout(layout.kind(),layout.width(),layout.height(),layout.slots().stream().map(slot->{
            var full=new ArrayList<>(path);full.addAll(slot.path());
            return new NativeRecipeLayout.Slot(full,slot.x(),slot.y(),slot.output());
        }).toList());
    }
}
