package dev.xyat.contentstudio.recipe.nativeedit;

import com.google.gson.*;
import java.util.*;

/** Pure recipe geometry. No viewer classes: layouts remain available without JEI, EMI or REI. */
public record NativeRecipeLayout(String kind, int width, int height, List<Slot> slots) {
    public record Slot(List<String> path, int x, int y, boolean output) {
        public Slot { path=List.copyOf(path); }
    }
    public NativeRecipeLayout { slots=List.copyOf(slots); }
    public static List<String> stationCandidates(String type) {
        String[] parts=type.split(":",2);if(parts.length!=2)return List.of();
        String path=parts[1];
        String alias=switch(type) {
            case "farmersdelight:cutting" -> "cutting_board";
            case "farmersdelight:cooking" -> "cooking_pot";
            case "create:pressing", "create:compacting" -> "mechanical_press";
            case "create:mixing" -> "mechanical_mixer";
            case "create:deploying", "create:sequenced_assembly" -> "deployer";
            case "create:filling" -> "spout"; case "create:emptying" -> "item_drain";
            case "create:cutting" -> "mechanical_saw"; case "create:milling" -> "millstone";
            case "create:crushing" -> "crushing_wheel"; case "create:haunting" -> "encased_fan";
            case "create:splashing" -> "encased_fan"; case "create:sandpaper_polishing" -> "sand_paper";
            case "create:mechanical_crafting" -> "mechanical_crafter";
            case "ae2:entropy" -> "entropy_manipulator"; case "ae2:transform" -> "fluix_crystal";
            case "ars_nouveau:glyph", "ars_nouveau:book_upgrade" -> "scribes_table";
            case "ars_nouveau:armor_upgrade", "ars_nouveau:enchantment", "ars_nouveau:reactive_enchantment",
                    "ars_nouveau:prestidigitation", "ars_nouveau:spell_write" -> "enchanting_apparatus";
            case "ars_nouveau:imbuement" -> "imbuement_chamber";
            case "ars_nouveau:crush" -> "glyph_crush";
            case "avaritia:compressor" -> "neutron_compressor";
            case "avaritia:extreme_smithing" -> "extreme_smithing_table";
            case "avaritia:shaped_table", "avaritia:shapeless_table", "avaritia:infinity_catalyst",
                    "avaritia:eternal_singularity", "avaritia:full_matter_cluster", "avaritia:no_consume_catalyst_shaped" -> "extreme_crafting_table";
            case "cataclysm:weapon_fusion" -> "mechanical_fusion_anvil";
            case "cataclysm:amethyst_bless" -> "altar_of_amethyst";
            case "draconicevolution:fusion_crafting" -> "fusion_crafting_core";
            case "eidolon:crucible", "eidolon_repraised:crucible" -> "crucible";
            case "eidolon:ritual_brazier", "eidolon_repraised:ritual_brazier",
                    "eidolon_repraised:ritual_brazier_crafting", "eidolon_repraised:ritual_brazier_summoning",
                    "eidolon_repraised:ritual_brazier_location" -> "brazier";
            case "enderio:alloy_smelting" -> "alloy_smelter"; case "enderio:sag_milling" -> "sag_mill";
            case "enderio:slicing" -> "slice_and_splice"; case "enderio:soul_binding" -> "soul_binder";
            case "enderio:vat_fermenting" -> "vat"; case "enderio:painting" -> "painting_machine";
            case "alloy_smelter:smelting" -> "forge_controller_tier1";
            case "iceandfire:dragonforge" -> "dragonforge_fire_core";
            case "tacz:gun_smith_table_crafting" -> "gun_smith_table";
            case "immersiveengineering:alloy" -> "alloybrick";
            case "immersiveengineering:coke_oven" -> "cokebrick";
            case "immersiveengineering:blast_furnace", "immersiveengineering:blast_furnace_fuel" -> "blastbrick";
            case "immersiveengineering:blueprint" -> "workbench";
            case "extendedcrafting:shaped_table", "extendedcrafting:shapeless_table" -> "ultimate_table";
            case "extendedcrafting:shaped_ender_crafter", "extendedcrafting:shapeless_ender_crafter" -> "ender_crafter";
            case "extendedcrafting:shaped_flux_crafter", "extendedcrafting:shapeless_flux_crafter" -> "flux_crafter";
            case "farm_and_charm:pot_cooking" -> "cooking_pot"; case "farm_and_charm:drying" -> "silo_wood";
            case "farm_and_charm:cutting_board_assembly" -> "cutting_board";
            case "goety:ritual" -> "dark_altar"; case "goety:brazier" -> "necro_brazier";
            case "goety:brewing", "goety:cauldron", "goety:cauldron_sus" -> "witch_cauldron";
            case "goety:pulverize" -> "pulverize_focus"; case "goety:cursed_infuser_recipes" -> "cursed_infuser";
            case "goety:soul_absorber_recipes" -> "soul_absorber";
            case "industrialforegoing:crusher", "industrialforegoing:stonework_generate" -> "material_stonework_factory";
            case "industrialforegoing:laser_drill_ore" -> "ore_laser_base";
            case "industrialforegoing:laser_drill_fluid" -> "fluid_laser_base";
            case "irons_spellbooks:alchemist_cauldron_brew", "irons_spellbooks:alchemist_cauldron_empty",
                    "irons_spellbooks:alchemist_cauldron_fill" -> "alchemist_cauldron";
            case "irons_spellbooks:arcane_anvil_transform", "irons_spellbooks:smithing_transform_no_addition" -> "arcane_anvil";
            case "kaleidoscope_cookery:flex_pot" -> "pot"; case "kaleidoscope_cookery:flex_stockpot" -> "stockpot";
            case "mekanism:crushing" -> "crusher"; case "mekanism:enriching" -> "enrichment_chamber";
            case "mekanism:smelting" -> "energized_smelter"; case "mekanism:chemical_infusing" -> "chemical_infuser";
            case "mekanism:combining" -> "combiner"; case "mekanism:separating" -> "electrolytic_separator";
            case "mekanism:washing" -> "chemical_washer"; case "mekanism:evaporating" -> "thermal_evaporation_controller";
            case "mekanism:activating" -> "solar_neutron_activator"; case "mekanism:centrifuging" -> "isotopic_centrifuge";
            case "mekanism:crystallizing" -> "chemical_crystallizer"; case "mekanism:dissolution" -> "chemical_dissolution_chamber";
            case "mekanism:compressing" -> "osmium_compressor"; case "mekanism:purifying", "mekanism:chemical_conversion" -> "purification_chamber";
            case "mekanism:injecting" -> "chemical_injection_chamber"; case "mekanism:nucleosynthesizing" -> "antiprotonic_nucleosynthesizer";
            case "mekanism:energy_conversion" -> "basic_energy_cube"; case "mekanism:oxidizing" -> "chemical_oxidizer";
            case "mekanism:pigment_extracting" -> "pigment_extractor"; case "mekanism:pigment_mixing" -> "pigment_mixer";
            case "mekanism:metallurgic_infusing" -> "metallurgic_infuser"; case "mekanism:painting" -> "painting_machine";
            case "mekanism:reaction" -> "pressurized_reaction_chamber"; case "mekanism:sawing" -> "precision_sawmill";
            case "mekanism:rotary", "mekanism:condensentrating", "mekanism:decondensentrating" -> "rotary_condensentrator";
            case "mysticalagriculture:infusion" -> "infusion_altar"; case "mysticalagriculture:awakening" -> "awakening_altar";
            case "mysticalagriculture:reprocessor" -> "seed_reprocessor"; case "mysticalagriculture:soul_extraction" -> "soul_extractor";
            case "spore:surgery", "spore:grafting" -> "surgery_table";
            case "touhou_little_maid:altar_recipe_serializers", "touhou_little_maid:altar_recipe" -> "altar";
            case "powah:energizing" -> "energizing_orb";
            case "twilightforest:uncrafting" -> "uncrafting_table";
            case "twilightforest:crumble_horn" -> "crumble_horn";
            case "twilightforest:transformation_powder" -> "transformation_powder";
            case "twilightforest:drying" -> "sorting_drying_rack";
            case "twilightforest:ominous_fire" -> "exanimate_essence";
            case "youkaisfeasts:simple_basin" -> "wood_basin";
            case "youkaisfeasts:simple_fermentation" -> "fermentation_tank";
            case "youkaisfeasts:steaming" -> "steamer_pot";
            case "youkaisfeasts:cuisine_mixed", "youkaisfeasts:cuisine_ordered", "youkaisfeasts:cuisine_fixed" -> "cuisine_board";
            case "youkaisfeasts:unordered_cooking", "youkaisfeasts:immediate_soup" -> "stockpot";
            case "minecraft:crafting_shaped", "minecraft:crafting_shapeless" -> "crafting_table";
            case "minecraft:smelting" -> "furnace"; case "minecraft:blasting" -> "blast_furnace";
            case "minecraft:smoking" -> "smoker"; case "minecraft:campfire_cooking" -> "campfire";
            case "minecraft:stonecutting" -> "stonecutter";
            case "minecraft:smithing_transform", "minecraft:smithing_trim" -> "smithing_table";
            default -> path;
        };
        return List.of(parts[0]+":"+alias,parts[0]+":"+path);
    }
    public static NativeRecipeLayout of(JsonObject json) {return of(json,0);}
    public static NativeRecipeLayout of(JsonObject json,int page) {
        String type=NativeRecipeDocument.serializerId(json);var slots=new ArrayList<Slot>();
        var workstation=NativeWorkstationLayouts.of(type,json);
        if(workstation!=null)return workstation;
        if(type.equals("create:sequenced_assembly") && json.has("sequence") && json.get("sequence").isJsonArray()) {
            if(json.has("ingredient"))slots.add(new Slot(List.of("ingredient"),0,24,false));
            if(json.has("transitionalItem"))slots.add(new Slot(List.of("transitionalItem"),0,64,false));
            if(json.has("transitional_item"))slots.add(new Slot(List.of("transitional_item"),0,64,false));
            var steps=json.getAsJsonArray("sequence");int start=Math.min(Math.max(0,page)*3,Math.max(0,steps.size()-1));
            int end=Math.min(steps.size(),start+3);
            for(int i=start;i<end;i++) {
                int x=40+(i-start)*40;slots.add(new Slot(List.of("sequence",Integer.toString(i)),x,24,false));
                var step=steps.get(i);if(!step.isJsonObject())continue;
                var ingredients=step.getAsJsonObject().get("ingredients");
                if(ingredients!=null && ingredients.isJsonArray())for(int j=1;j<ingredients.getAsJsonArray().size();j++)
                    slots.add(new Slot(List.of("sequence",Integer.toString(i),"ingredients",Integer.toString(j)),x,64+(j-1)*20,false));
            }
            int outputX=40+(end-start)*40;add(json,"results",slots,outputX,24,3,true);
            int maxY=slots.stream().mapToInt(Slot::y).max().orElse(64);
            return new NativeRecipeLayout("create_sequence",outputX+60,Math.max(90,maxY+20),slots);
        }
        if(type.equals("farmersdelight:cutting")) {
            if(json.has("tool")) slots.add(new Slot(List.of("tool"),16,7,false));
            add(json,"ingredients",slots,16,27,1,false);
            add(json,"result",slots,76,8,2,true);
            return new NativeRecipeLayout("cutting",118,60,slots);
        }
        if(type.equals("farmersdelight:cooking")) {
            add(json,"ingredients",slots,0,0,3,false);
            if(json.has("container"))slots.add(new Slot(List.of("container"),64,40,false));
            if(json.has("result"))slots.add(new Slot(List.of("result"),98,10,true));
            return new NativeRecipeLayout("cooking_pot",118,60,slots);
        }
        if(json.has("pattern") && json.get("pattern").isJsonArray()
                && java.util.stream.StreamSupport.stream(json.getAsJsonArray("pattern").spliterator(),false)
                    .allMatch(row->row.isJsonPrimitive() && row.getAsJsonPrimitive().isString())
                && json.has("key") && json.get("key").isJsonObject()) {
            var pattern=json.getAsJsonArray("pattern");var key=json.getAsJsonObject("key");
            int columns=0;
            for(int y=0;y<pattern.size();y++) {
                String row=pattern.get(y).getAsString();
                columns=Math.max(columns,row.length());
                for(int x=0;x<row.length();x++) {
                    String symbol=String.valueOf(row.charAt(x));
                    if(!symbol.equals(" ") && key.has(symbol))slots.add(new Slot(List.of("key",symbol),x*20,y*20,false));
                }
            }
            int outputX=Math.max(100,columns*20+40);
            if(json.has("result"))slots.add(new Slot(List.of("result"),outputX,Math.max(0,(pattern.size()*20-20)/2),true));
            return new NativeRecipeLayout("crafting",outputX+20,Math.max(60,pattern.size()*20),slots);
        }
        boolean crafting=type.equals("minecraft:crafting_shapeless");
        boolean furnace=Set.of("minecraft:smelting","minecraft:blasting","minecraft:smoking","minecraft:campfire_cooking").contains(type);
        for(String key:List.of("ingredients","ingredient","input","inputs","base","addition","template",
                "pedestalItems","pedestal_items","focus_items","focusItems","invariant_items","invariantItems","reagent","reagents","catalyst","additives","mold","soil","container","carrier","finisher",
                "item_input","itemInput","main_input","mainInput","extra_input","extraInput","left_input","leftInput","right_input","rightInput",
                "chemical_input","chemicalInput","gasInput","slurryInput","fluid_input","base_fluid","baseFluid","inputFluid","fluidInput","input0","input1","body","head","tail","activation_item","essences","materials","blood"))
            add(json,key,slots,0,0,crafting?3:4,false);
        if((type.equals("eidolon:crucible") || type.equals("eidolon_repraised:crucible")) && json.has("steps") && json.get("steps").isJsonArray()) {
            var steps=json.getAsJsonArray("steps");
            for(int step=0;step<steps.size();step++)if(steps.get(step).isJsonObject()) {
                var items=steps.get(step).getAsJsonObject().get("items");
                if(items!=null && items.isJsonArray())for(int i=0;i<items.getAsJsonArray().size();i++)
                    slots.add(new Slot(List.of("steps",Integer.toString(step),"items",Integer.toString(i)),0,0,false));
            }
        }
        // Each root field is a separate group. Never recursively mistake component/NBT data for ingredients.
        int n=0;for(var slot:List.copyOf(slots)) {
            int columns=crafting?3:4;slots.set(n,new Slot(slot.path(),(n%columns)*20,(n/columns)*20,false));n++;
        }
        for(String key:List.of("result","results","output","outputs","item_result","item_output","itemOutput","main_output","mainOutput","secondary_output","secondaryOutput",
                "chemical_output","gasOutput","fluid_output","fluidOutput","left_chemical_output","leftGasOutput","right_chemical_output","rightGasOutput","secondaryOutputs","strippingSecondaries","slag"))add(json,key,slots,112,0,3,true);
        int output=0;
        for(int i=0;i<slots.size();i++)if(slots.get(i).output()) {
            var slot=slots.get(i);slots.set(i,new Slot(slot.path(),(crafting?100:furnace?60:112)+output%3*20,(crafting?20:0)+output/3*20,true));output++;
        }
        int maxY=slots.stream().mapToInt(Slot::y).max().orElse(0);
        return new NativeRecipeLayout(crafting?"crafting":furnace?"furnace":type.startsWith("create:")?"create_processing":"generic",crafting?120:furnace?80:174,Math.max(60,maxY+20),slots);
    }
    private static void add(JsonObject json,String key,List<Slot> slots,int x,int y,int columns,boolean output) {
        if(!json.has(key))return;var value=json.get(key);
        if(value.isJsonObject() && value.getAsJsonObject().has("drops") && value.getAsJsonObject().get("drops").isJsonArray()) {
            var drops=value.getAsJsonObject().getAsJsonArray("drops");
            for(int i=0;i<drops.size();i++)slots.add(new Slot(List.of(key,"drops",Integer.toString(i)),x+i%columns*20,y+i/columns*20,output));
        }
        else if(value.isJsonArray())for(int i=0;i<value.getAsJsonArray().size();i++)
            slots.add(new Slot(List.of(key,Integer.toString(i)),x+i%columns*20,y+i/columns*20,output));
        else if(key.equals("ingredients") && value.isJsonObject() && value.getAsJsonObject().keySet().stream().anyMatch(Set.of("top","middle","bottom")::contains)) {
            int i=0;for(String name:List.of("top","middle","bottom"))if(value.getAsJsonObject().has(name)) {
                slots.add(new Slot(List.of(key,name),x+i%columns*20,y+i/columns*20,output));i++;
            }
        }
        else slots.add(new Slot(List.of(key),x,y,output));
    }
    /** ID/tag and count fields inside one stack/ingredient; alternatives are kept separate and never flattened away. */
    public static List<List<String>> fields(NativeRecipeDocument document,List<String> path) {
        var result=new ArrayList<List<String>>();collect(document.at(path),path,result);return List.copyOf(result);
    }
    private static void collect(JsonElement value,List<String> path,List<List<String>> result) {
        if(value.isJsonPrimitive()) {result.add(List.copyOf(path));return;}
        if(value.isJsonArray()) {for(int i=0;i<value.getAsJsonArray().size();i++)collect(value.getAsJsonArray().get(i),append(path,Integer.toString(i)),result);}
        else if(value.isJsonObject())for(var e:value.getAsJsonObject().entrySet())
            if(!Set.of("components","nbt","conditions","forge:conditions","neoforge:conditions").contains(e.getKey()))collect(e.getValue(),append(path,e.getKey()),result);
    }
    public static List<String> append(List<String> path,String key) {var list=new ArrayList<>(path);list.add(key);return List.copyOf(list);}
}
