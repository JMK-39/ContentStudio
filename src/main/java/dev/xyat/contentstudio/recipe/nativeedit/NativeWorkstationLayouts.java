package dev.xyat.contentstudio.recipe.nativeedit;

import com.google.gson.*;
import java.util.*;
import static dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeLayout.Slot;

/** Independent geometry informed by the mods' own viewer categories; no viewer classes or runtime dependency. */
final class NativeWorkstationLayouts {
    private NativeWorkstationLayouts() { }
    static NativeRecipeLayout of(String type,JsonObject json) {
        var slots=new ArrayList<Slot>();
        if(type.equals("minecraft:stonecutting")) {
            add(json,"ingredient",slots,0,8,1,false);add(json,"result",slots,62,8,1,true);
            return layout("stonecutting",82,34,slots);
        }
        if(type.equals("goety:ritual")) {
            int[][] pedestals={{56,22},{96,62},{56,102},{16,62},{76,22},{96,22},
                    {36,102},{16,102},{36,22},{96,102},{76,102},{16,22}};
            if(json.has("ingredients")&&json.get("ingredients").isJsonArray()) {
                var ingredients=json.getAsJsonArray("ingredients");if(ingredients.size()>pedestals.length)return null;
                for(int i=0;i<ingredients.size();i++)slots.add(new Slot(List.of("ingredients",Integer.toString(i)),pedestals[i][0],pedestals[i][1],false));
            }
            add(json,"activation_item",slots,56,52,1,false);add(json,"result",slots,151,52,1,true);
            return layout("goety_ritual",174,140,slots);
        }
        if(type.equals("goety:cursed_infuser")||type.equals("goety:cursed_infuser_recipes")) {
            add(json,"ingredient",slots,0,2,1,false);add(json,"result",slots,62,2,1,true);
            return layout("goety_infuser",82,56,slots);
        }
        if(type.equals("powah:energizing")) {
            // Six horizontal inputs, the orb's result and an energy caption below them.
            add(json,"ingredients",slots,4,4,6,false);
            add(json,"result",slots,136,4,1,true);
            if(slots.stream().filter(s->!s.output()).count()>6)return null;
            return layout("powah_energizing",160,42,slots);
        }
        if(type.equals("twilightforest:uncrafting") && validPattern(json)) {
            add(json,"input",slots,4,20,1,false);
            var pattern=json.getAsJsonArray("pattern");var keys=json.getAsJsonObject("key");
            int columns=0;
            for(int y=0;y<pattern.size();y++) {
                String row=pattern.get(y).getAsString();columns=Math.max(columns,row.length());
                for(int x=0;x<row.length();x++) {
                    String symbol=String.valueOf(row.charAt(x));
                    if(!symbol.equals(" ")&&keys.has(symbol))slots.add(new Slot(List.of("key",symbol),64+x*20,y*20,true));
                }
            }
            return layout("twilight_uncrafting",Math.max(124,64+columns*20),Math.max(60,pattern.size()*20),slots);
        }
        if(type.equals("twilightforest:crumble_horn")) {
            add(json,"from",slots,18,18,1,false);add(json,"to",slots,80,18,1,true);
            return layout("twilight_blocks",116,56,slots);
        }
        if(Set.of("twilightforest:transformation_powder","twilightforest:ominous_fire").contains(type)) {
            add(json,"from",slots,6,10,1,false);add(json,"to",slots,76,10,1,true);
            return layout("twilight_entities",116,56,slots);
        }
        if(type.equals("twilightforest:drying")) {
            add(json,json.has("input")?"input":"ingredient",slots,0,0,1,false);add(json,"result",slots,60,0,1,true);
            return layout("workstation_drying",82,40,slots);
        }
        if(!type.startsWith("youkaisfeasts:"))return null;
        String name=type.substring(type.indexOf(':')+1);
        switch(name) {
            case "steaming", "drying_rack" -> {
                add(json,"ingredient",slots,0,0,1,false);add(json,"result",slots,60,20,1,true);
                return layout("feasts_heating",82,60,slots);
            }
            case "simple_basin" -> {
                add(json,"input",slots,0,0,1,false);add(json,"output",slots,60,0,1,true);
                return layout("feasts_basin",82,40,slots);
            }
            case "kettle" -> {
                add(json,"input",slots,6,2,2,false);add(json,"result",slots,92,12,1,true);
                return layout("feasts_kettle",116,Math.max(60,bottom(slots)),slots);
            }
            case "simple_fermentation" -> {
                add(json,"ingredients",slots,0,0,3,false);
                if(nonemptyFluid(json,"inputFluid"))addAtEnd(json,"inputFluid",slots,0,3,false);
                add(json,"results",slots,100,0,3,true);
                if(nonemptyFluid(json,"outputFluid"))addAtEnd(json,"outputFluid",slots,100,3,true);
                return layout("feasts_ferment",160,Math.max(60,bottom(slots)),slots);
            }
            case "cuisine_mixed", "cuisine_ordered", "cuisine_fixed" -> {
                add(json,"base",slots,0,20,1,false);
                add(json,"first",slots,30,0,5,false);
                int firstRows=(arraySize(json,"first")+4)/5;
                add(json,"second",slots,30,Math.max(20,firstRows*20),5,false);
                add(json,"input",slots,30,0,5,false);
                add(json,"result",slots,164,20,1,true);
                return layout("feasts_cuisine",188,Math.max(60,bottom(slots)),slots);
            }
            case "unordered_cooking", "immediate_soup" -> {
                add(json,"input",slots,0,0,5,false);add(json,"result",slots,136,10,1,true);
                // Immediate soups produce a named internal soup, not an item stack. Its id/color remain root parameters.
                return layout("feasts_pot",160,Math.max(60,bottom(slots)),slots);
            }
            default -> {return null;}
        }
    }
    private static NativeRecipeLayout layout(String kind,int width,int height,List<Slot> slots) {return new NativeRecipeLayout(kind,width,height,slots);}
    private static int bottom(List<Slot> slots) {return slots.stream().mapToInt(s->s.y()+18).max().orElse(0);}
    private static int arraySize(JsonObject json,String key) {return json.has(key)&&json.get(key).isJsonArray()?json.getAsJsonArray(key).size():json.has(key)?1:0;}
    private static boolean validPattern(JsonObject json) {
        return json.has("pattern")&&json.get("pattern").isJsonArray()&&json.has("key")&&json.get("key").isJsonObject()
                &&json.getAsJsonArray("pattern").asList().stream().allMatch(e->e.isJsonPrimitive()&&e.getAsJsonPrimitive().isString());
    }
    private static boolean nonemptyFluid(JsonObject json,String key) {
        if(!json.has(key))return false;
        var value=json.get(key);
        if(value.isJsonObject()) {
            var object=value.getAsJsonObject();
            if(object.size()==0)return false; // Modern FluidStack.EMPTY is encoded as an empty object.
            for(String field:List.of("fluid","id","FluidName"))
                if(object.has(field)&&object.get(field).isJsonPrimitive()&&object.get(field).getAsString().equals("minecraft:empty"))return false;
            if(object.has("amount")&&object.get("amount").isJsonPrimitive()&&object.get("amount").getAsJsonPrimitive().isNumber())return object.get("amount").getAsDouble()>0;
        }
        return true;
    }
    private static void addAtEnd(JsonObject json,String key,List<Slot> slots,int x,int columns,boolean output) {
        int n=(int)slots.stream().filter(s->s.output()==output).count();add(json,key,slots,x+n%columns*20,n/columns*20,1,output);
    }
    private static void add(JsonObject json,String key,List<Slot> slots,int x,int y,int columns,boolean output) {
        if(!json.has(key))return;var value=json.get(key);
        if(value.isJsonArray())for(int i=0;i<value.getAsJsonArray().size();i++)slots.add(new Slot(List.of(key,Integer.toString(i)),x+i%columns*20,y+i/columns*20,output));
        else slots.add(new Slot(List.of(key),x,y,output));
    }
}
