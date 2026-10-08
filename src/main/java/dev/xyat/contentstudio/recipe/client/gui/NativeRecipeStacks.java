package dev.xyat.contentstudio.recipe.client.gui;

import com.google.gson.*;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticClientRuntime;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Native stack formats stay native; components are never rewritten as legacy NBT. */
final class NativeRecipeStacks {
    private NativeRecipeStacks() { }
    static List<ItemStack> read(JsonElement json) {
        var result=new ArrayList<ItemStack>();read(json,result);return List.copyOf(result);
    }
    private static void read(JsonElement value,List<ItemStack> result) {
        if(result.size()>=128)return;
        if(value.isJsonArray()) {value.getAsJsonArray().forEach(v->read(v,result));return;}
        if(value.isJsonObject()) {
            var object=value.getAsJsonObject();
            if(object.has("FluidName") && object.get("FluidName").isJsonPrimitive()) {
                var bucket=fluidBucket(object.get("FluidName").getAsString());if(!bucket.isEmpty())result.add(bucket);return;
            }
            if(object.has("type") && object.get("type").isJsonPrimitive()
                    && Set.of("gun","ammo","attachment").contains(object.get("type").getAsString())) {
                var stack=dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeCompat.taczPreview(object);
                if(!stack.isEmpty()) {result.add(stack);return;}
            }
            if(object.has("item") && object.get("item").isJsonObject()) {
                int start=result.size();read(object.get("item"),result);
                if(object.has("count"))try {
                    int count=Math.max(1,Math.min(999,object.get("count").getAsInt()));
                    for(int i=start;i<result.size();i++)result.get(i).setCount(count);
                }catch(RuntimeException invalidCount){ }
                return;
            }
            //? if <1.21 {
            // Touhou's native 1.20.1 item-entity output stores a complete stack in nbt.Item.
            if(object.has("type") && object.get("type").isJsonPrimitive() && object.get("type").getAsString().equals("minecraft:item")
                    && object.has("nbt") && object.get("nbt").isJsonObject() && object.getAsJsonObject("nbt").has("Item")) {
                try {
                    var stack=ItemStack.of(net.minecraft.nbt.TagParser.parseTag(object.getAsJsonObject("nbt").get("Item").toString()));
                    if(!stack.isEmpty())result.add(stack);
                }catch(Exception invalidStack){ }
                return;
            }
            //?}
            // Only documented ingredient wrappers; never recurse into components, NBT or arbitrary custom data.
            if(object.has("fluid") && object.get("fluid").isJsonObject()) {
                var fluid=object.getAsJsonObject("fluid");
                if(fluid.has("id") && fluid.get("id").isJsonPrimitive()) {
                    var bucket=fluidBucket(fluid.get("id").getAsString());if(!bucket.isEmpty())result.add(bucket);
                }
                return;
            }
            for(String wrapper:List.of("ingredient","base_ingredient","baseIngredient","children","alternatives","items","values","block"))
                if(object.has(wrapper)){read(object.get(wrapper),result);return;}
            String key=object.has("id")?"id":object.has("item")?"item":object.has("tag")?"tag":object.has("fluid")?"fluid":null;
            if(key==null)return;
            var identifier=object.get(key);if(!identifier.isJsonPrimitive())return;
            if(key.equals("tag")) {tag(identifier.getAsString(),result);return;}
            if(key.equals("fluid")) {var bucket=fluidBucket(identifier.getAsString());if(!bucket.isEmpty())result.add(bucket);return;}
            var stack=stack(identifier.getAsString());if(stack.isEmpty())return;
            try {
                //? if >=1.21 {
                /*if(object.has("id") && KineticClientRuntime.currentLevel()!=null) {
                    var ops=net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,KineticClientRuntime.currentLevel().registryAccess());
                    stack=ItemStack.CODEC.parse(ops,object).result().orElse(stack);
                }
                *///?} else {
                if(object.has("nbt")) {
                    var nbt=object.get("nbt");stack.setTag(net.minecraft.nbt.TagParser.parseTag(nbt.isJsonPrimitive()?nbt.getAsString():nbt.toString()));
                }
                //?}
                if(object.has("count"))stack.setCount(Math.max(1,Math.min(999,object.get("count").getAsInt())));
            } catch(Exception ignored) { }
            result.add(stack);return;
        }
        if(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            String id=value.getAsString();if(id.startsWith("#"))tag(id.substring(1),result);
            else {var stack=stack(id);if(!stack.isEmpty())result.add(stack);}
        }
    }
    private static ItemStack stack(String value) {
        var id=KineticResourceIds.tryParse(value);return id==null || !KineticRegistries.items().contains(id)?ItemStack.EMPTY:new ItemStack(KineticRegistries.items().get(id));
    }
    private static ItemStack fluidBucket(String value) {
        var id=KineticResourceIds.tryParse(value);if(id==null || !KineticRegistries.fluids().contains(id))return ItemStack.EMPTY;
        var bucket=KineticRegistries.fluids().get(id).getBucket();
        return bucket==net.minecraft.world.item.Items.AIR?ItemStack.EMPTY:new ItemStack(bucket);
    }
    private static void tag(String value,List<ItemStack> result) {
        var id=KineticResourceIds.tryParse(value);if(id==null)return;
        for(var item:KineticRegistries.items().values())if(KineticRegistries.items().isInTag(item,id)) {
            result.add(new ItemStack(item));if(result.size()>=128)break;
        }
    }
}
