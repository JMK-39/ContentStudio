package dev.xyat.contentstudio.recipe.nativeedit;

import com.google.gson.*;
import java.util.List;
import java.util.Set;

/** Complete native item values shared by item-field icons and item selections. */
public final class NativeRecipeItemData {
    private static final Set<String> ITEM_FIELDS=Set.of("item","id","tag");
    private static final Set<String> DATA_FIELDS=Set.of("item","id","tag","nbt","components");
    private NativeRecipeItemData() { }

    public static JsonElement preview(NativeRecipeDocument document,List<String> field) {
        if(!field.isEmpty() && ITEM_FIELDS.contains(field.get(field.size()-1))) {
            var parent=document.at(field.subList(0,field.size()-1));
            if(parent.isJsonObject())return parent;
        }
        return document.at(field);
    }

    /** Replacing a selected item replaces its data, while quantities and recipe-specific fields stay in place. */
    public static void select(NativeRecipeDocument document,List<String> path,JsonObject selected,boolean output) {
        //? if <1.21 {
        if(output && !path.isEmpty() && path.get(path.size()-1).equals("result") && selected.has("nbt")) {
            var owner=document.at(path.subList(0,path.size()-1));
            if(owner.isJsonObject() && owner.getAsJsonObject().has("type")
                    && NativeRecipeDocument.serializerId(owner.getAsJsonObject()).equals("minecraft:stonecutting"))
                throw new IllegalArgumentException("This serializer cannot encode NBT outputs");
        }
        //?}
        var current=document.at(path);
        if(current.isJsonObject()) {
            var object=current.getAsJsonObject();
            if(object.has("type") && Set.of("gun","ammo","attachment","custom").contains(object.get("type").getAsString())) {
                selectTacz(document,path,object,selected);return;
            }
            for(String wrapper:List.of("ingredient","base_ingredient","baseIngredient","item"))
                if(object.has(wrapper) && !object.get(wrapper).isJsonNull()
                        && (!wrapper.equals("item") || !object.get(wrapper).isJsonPrimitive())) {
                    select(document,NativeRecipeLayout.append(path,wrapper),selected,output);return;
                }
        }
        var replacement=current.isJsonObject()?current.getAsJsonObject():new JsonObject();
        boolean componentIngredient=replacement.has("neoforge:ingredient_type")
                && replacement.get("neoforge:ingredient_type").getAsString().equals("contentstudio:component_patch")
                || replacement.has("type") && replacement.get("type").getAsString().equals("contentstudio:component_patch");
        DATA_FIELDS.forEach(replacement::remove);
        for(String discriminator:List.of("type","neoforge:ingredient_type"))
            if(replacement.has(discriminator) && Set.of("forge:nbt","forge:partial_nbt","contentstudio:component_patch").contains(replacement.get(discriminator).getAsString()))
                replacement.remove(discriminator);
        selected.entrySet().stream().filter(e->!e.getKey().equals("count"))
                .forEach(e->replacement.add(e.getKey(),e.getValue().deepCopy()));
        if(!output && replacement.has("id"))replacement.add("item",replacement.remove("id"));
        if(!output && selected.has("nbt"))replacement.addProperty("type","forge:nbt");
        if(!output && selected.has("components")) {
            //? if >=26.1 {
            /*replacement.addProperty("neoforge:ingredient_type","contentstudio:component_patch");
            *///?} else {
            replacement.addProperty("type","contentstudio:component_patch");
            //?}
        }
        //? if >=26.1 {
        /*if(!output && componentIngredient && !selected.has("components")) {
            document.setValue(path,replacement.has("tag")?new JsonPrimitive("#"+replacement.get("tag").getAsString()):replacement.get("item"));return;
        }
        *///?}
        if(current.isJsonPrimitive() && replacement.size()==1) {
            String key=replacement.has("id")?"id":replacement.has("item")?"item":"tag";
            document.setValue(path,key.equals("tag")?new JsonPrimitive("#"+replacement.get(key).getAsString()):replacement.get(key));
        }else document.setValue(path,replacement);
    }

    private static void selectTacz(NativeRecipeDocument document,List<String> path,JsonObject original,JsonObject selected) {
        var replacement=original.deepCopy();
        String item=selected.get(selected.has("id")?"id":"item").getAsString();
        String type=switch(item) {
            case "tacz:modern_kinetic_gun" -> "gun";
            case "tacz:ammo" -> "ammo";
            case "tacz:attachment" -> "attachment";
            default -> "custom";
        };
        JsonElement count=original.get("type").getAsString().equals("custom") && original.has("item") && original.get("item").isJsonObject()
                ?original.getAsJsonObject("item").get("count"):original.get("count");
        if(count==null)count=new JsonPrimitive(1);
        if(type.equals("custom")) {
            var stack=selected.deepCopy();stack.add("count",count.deepCopy());
            replacement.addProperty("type","custom");replacement.remove("id");replacement.remove("nbt");replacement.remove("extraData");
            replacement.remove("count");
            replacement.add("item",stack);document.setValue(path,replacement);return;
        }
        String id=item;
        if(!type.equals("custom")) {
            if(!selected.has("nbt"))throw new IllegalArgumentException("Select a configured TACZ item");
            var data=selected.get("nbt");
            try {
                var tag=net.minecraft.nbt.TagParser.parseTag(data.isJsonPrimitive()?data.getAsString():data.toString());
                String key=type.equals("gun")?"GunId":type.equals("ammo")?"AmmoId":"AttachmentId";
                //? if >=26.1 {
                /*id=tag.getStringOr(key,"");
                *///?} else {
                id=tag.getString(key);
                //?}
            }catch(com.mojang.brigadier.exceptions.CommandSyntaxException invalid){throw new IllegalArgumentException("Invalid selected item data",invalid);}
            if(id.isBlank())throw new IllegalArgumentException("Select a configured TACZ item");
        }
        replacement.addProperty("type",type);replacement.addProperty("id",id);
        replacement.remove("item");replacement.remove("nbt");replacement.remove("extraData");
        replacement.add("count",count.deepCopy());
        if(selected.has("nbt"))replacement.add("nbt",selected.get("nbt").deepCopy());
        document.setValue(path,replacement);
    }

    public static void selectField(NativeRecipeDocument document,List<String> field,JsonObject selected,boolean output) {
        var path=!field.isEmpty() && ITEM_FIELDS.contains(field.get(field.size()-1))
                ?field.subList(0,field.size()-1):field;
        select(document,path,selected,output);
    }

    public static boolean outputField(NativeRecipeDocument document,List<String> field) {
        for(int depth=field.size()-1;depth>=0;depth--) {
            var parent=document.at(field.subList(0,depth));
            if(!parent.isJsonObject() || !parent.getAsJsonObject().has("type"))continue;
            var relative=field.subList(depth,field.size());
            if(NativeRecipeLayout.of(parent.getAsJsonObject()).slots().stream().anyMatch(slot->slot.output()
                    && relative.size()>=slot.path().size() && relative.subList(0,slot.path().size()).equals(slot.path())))return true;
        }
        return false;
    }
}
