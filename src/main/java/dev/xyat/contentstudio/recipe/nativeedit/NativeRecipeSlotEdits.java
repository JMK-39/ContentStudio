package dev.xyat.contentstudio.recipe.nativeedit;

import java.util.List;

/** Slot removal keeps a shared shaped ingredient until its last physical cell is cleared. */
public final class NativeRecipeSlotEdits {
    private NativeRecipeSlotEdits() { }
    public static void select(NativeRecipeDocument document,List<String> path,String id,boolean tag) {
        var value=document.at(path);
        if(value.isJsonObject()) {
            var object=value.getAsJsonObject();
            for(String wrapper:List.of("ingredient","base_ingredient","baseIngredient","item"))
                if(object.has(wrapper)&&!object.get(wrapper).isJsonNull()
                        &&(!wrapper.equals("item")||!object.get(wrapper).isJsonPrimitive())) {
                    select(document,NativeRecipeLayout.append(path,wrapper),id,tag);return;
                }
            String key=tag?"tag":object.has("id")?"id":"item";
            object.remove("tag");object.remove("item");object.remove("id");object.addProperty(key,id);
            document.setValue(path,object);
        }else if(value.isJsonPrimitive())document.setPrimitive(path,(tag?"#":"")+id);
        else {var object=new com.google.gson.JsonObject();object.addProperty(tag?"tag":"item",id);document.setValue(path,object);}
    }
    public static void remove(NativeRecipeDocument document, NativeRecipeLayout layout, int index) {
        var path=layout.slots().get(index).path();
        var prefix=path.size()>=2?path.subList(0,path.size()-2):List.<String>of();
        var container=document.at(prefix);
        var json=container.isJsonObject()?container.getAsJsonObject():document.json();
        if(path.size()>=2 && path.get(path.size()-2).equals("key") && json.has("pattern") && json.get("pattern").isJsonArray()) {
            String symbol=path.get(path.size()-1);
            int occurrence=0;
            for(int i=0;i<index;i++)if(layout.slots().get(i).path().equals(path))occurrence++;
            var pattern=json.getAsJsonArray("pattern");
            boolean cleared=false,remaining=false;
            for(int row=0;row<pattern.size();row++) {
                String text=pattern.get(row).getAsString();
                for(int col=0;col<text.length();col++)if(text.substring(col,col+1).equals(symbol)) {
                    if(!cleared && occurrence--==0) {
                        text=text.substring(0,col)+" "+text.substring(col+1);
                        document.setPrimitive(NativeRecipeLayout.append(NativeRecipeLayout.append(prefix,"pattern"),Integer.toString(row)),text);cleared=true;
                    }else remaining=true;
                }
            }
            if(!cleared)throw new IllegalArgumentException("Pattern slot no longer exists");
            if(!remaining)document.remove(path);
        }else document.remove(path);
    }
}
