//? if <1.21 {
package dev.xyat.contentstudiovalidation;

import com.google.gson.*;
import dev.xyat.contentstudio.recipe.client.RecipeJeiBridge;
import dev.xyat.contentstudio.recipe.client.gui.*;
import dev.xyat.contentstudio.recipe.nativeedit.*;
import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.client.gui.KineticGui;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import java.nio.file.*;
import java.util.*;

/** Opt-in regression in a full copied client: native Create lookup, recipe packets, JEI and NBT icons. */
public final class RecipeRepairValidation {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(RecipeRepairValidation.class);
    private static final String EMX="create:kjs/37dazy8ngdran6yb6bzbwu50f";
    private static final JsonObject report=new JsonObject();
    private static int stage,ticks,delay;
    private static boolean finished;
    private static JsonObject emxBody;
    private static Path directory;
    private RecipeRepairValidation() { }
    public static void install() {KineticClientEvents.onTick(KineticClientEvents.TickPhase.END,RecipeRepairValidation::tick);}
    private static void tick() {
        if(finished)return;
        try {
            GuiLongTextValidation.prepareOwnedWorld();
            var mc=Minecraft.getInstance();
            if(mc.level==null || mc.player==null || mc.getSingleplayerServer()==null || mc.getOverlay()!=null)return;
            if(++ticks>3000)throw new AssertionError("Client validation timed out at stage "+stage);
            if(delay-->0)return;
            switch(stage) {
                case 0 -> {
                    if(!RecipeJeiBridge.jeiAvailable())return;
                    directory=mc.gameDirectory.toPath().resolve("recipe-repair-validation");Files.createDirectories(directory);
                    mc.options.guiScale().set(2);mc.getWindow().setWindowed(1280,720);mc.resizeDisplay();
                    var server=mc.getSingleplayerServer();
                    report.add("sequences",server.submit(()->{try{return checkSequences(server.overworld());}catch(Exception error){throw new RuntimeException(error);}}).get());
                    require(mc.level.getRecipeManager().byKey(KineticResourceIds.parse(EMX)).isPresent(),"EMX reached the client recipe manager");
                    report.addProperty("clientEmx",true);
                    stage=1;
                    NativeRecipeClient.expect(NativeRecipeNetwork.request("list","","create:sequenced_assembly","kubejs:incomplete_emx_b1",0,0,"",""),search->{
                        try {
                            var rows=JsonParser.parseString(search.body()).getAsJsonObject().getAsJsonArray("rows");
                            require(search.success() && rows.asList().stream().anyMatch(row->row.getAsJsonObject().get("id").getAsString().equals(EMX)),"EMX is searchable by its intermediate item");
                            report.addProperty("intermediateSearch",true);
                            NativeRecipeClient.expect(NativeRecipeNetwork.request("open",EMX,"","",0,0,"",""),packet->{
                                try {
                                    require(packet.success(),"EMX opens over the recipe protocol: "+packet.body());
                                    emxBody=JsonParser.parseString(packet.body()).getAsJsonObject();
                                    KineticGui.open(NativeRecipeEditorPage.create(emxBody,()->{}));stage=2;delay=30;
                                }catch(Throwable error){finish(error);}
                            });
                        }catch(Throwable error){finish(error);}
                    });
                }
                case 1 -> { }
                case 2 -> {
                    screenshot("emx-editor.png");
                    var output=new ItemStack(BuiltInRegistries.ITEM.get(KineticResourceIds.parse("kubejs:package_emx_b1")));
                    var entries=RecipeJeiBridge.recipes(output);
                    report.addProperty("jeiEmxRecipes",entries.size());
                    require(entries.stream().anyMatch(e->e.recipe().id().toString().equals(EMX)),"JEI exposes the EMX recipe");
                    var result=JsonParser.parseString("{\"item\":\"tacz:ammo\",\"count\":64,\"nbt\":\"{AmmoId:'tacz:9mm'}\"}");
                    var json=JsonParser.parseString("{\"type\":\"create:cutting\",\"ingredients\":[{\"item\":\"kubejs:package_9mm\"}],\"results\":[]}").getAsJsonObject();
                    json.getAsJsonArray("results").add(result);
                    var doc=new NativeRecipeDocument(json);
                    var fieldPreview=NativeRecipeItemData.preview(doc,List.of("results","0","item"));
                    var stacks=read(fieldPreview);
                    require(stacks.size()==1 && stacks.get(0).getTag().getString("AmmoId").equals("tacz:9mm"),"Field icon retains the ammo model ID");
                    require(stacks.get(0).getCount()==64,"Field icon retains the output count");
                    var raw=JsonParser.parseString("{\"type\":\"ammo\",\"id\":\"tacz:9mm\",\"count\":32,\"nbt\":\"{RepairMarker:1b}\"}").getAsJsonObject();
                    var tacz=NativeRecipeCompat.taczPreview(raw);
                    require(!tacz.isEmpty() && tacz.getCount()==32 && tacz.getTag().getBoolean("RepairMarker"),"TACZ native result preview honors custom NBT");
                    var customDoc=new NativeRecipeDocument(JsonParser.parseString("{\"type\":\"tacz:gun_smith_table_crafting\",\"result\":{\"type\":\"ammo\",\"id\":\"tacz:9mm\",\"count\":32}}").getAsJsonObject());
                    NativeRecipeItemData.select(customDoc,List.of("result"),JsonParser.parseString("{\"item\":\"minecraft:diamond\",\"nbt\":\"{RepairMarker:1b}\"}").getAsJsonObject(),true);
                    var custom=NativeRecipeCompat.taczPreview(customDoc.at(List.of("result")).getAsJsonObject());
                    require(custom.is(net.minecraft.world.item.Items.DIAMOND) && custom.getCount()==32 && custom.getTag().getBoolean("RepairMarker"),"TACZ ordinary result replacement preserves quantity and NBT");
                    var taggedDoc=new NativeRecipeDocument(JsonParser.parseString("{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"minecraft:stone\"}],\"result\":{\"item\":\"minecraft:diamond\"}}").getAsJsonObject());
                    NativeRecipeItemData.select(taggedDoc,List.of("ingredients","0"),result.getAsJsonObject(),false);
                    var ingredient=net.minecraftforge.common.crafting.CraftingHelper.getIngredient(taggedDoc.at(List.of("ingredients","0")),false);
                    require(ingredient.test(stacks.get(0)),"Selected NBT ingredient matches its native ammo variant");
                    var other=stacks.get(0).copy();other.getOrCreateTag().putString("AmmoId","tacz:12g");
                    require(!ingredient.test(other),"Selected NBT ingredient rejects a different ammo variant");
                    report.addProperty("selectedNbtIngredient",true);report.addProperty("nativeTaczReplacement",true);
                    report.addProperty("nbtFieldPreview",true);report.addProperty("nativeTaczPreview",true);
                    var body=emxBody.deepCopy();body.add("recipe",json);
                    var page=new NativeRecipeFieldsPage(body,()->{});
                    var path=NativeRecipeFieldsPage.class.getDeclaredField("path");path.setAccessible(true);path.set(page,List.of("results","0"));
                    KineticGui.open(page);stage=3;delay=30;
                }
                case 3 -> {screenshot("ammo-nbt-field.png");finish(null);}
                default -> throw new AssertionError("Unknown validation stage");
            }
        }catch(Throwable error){finish(error);}
    }
    @SuppressWarnings("unchecked") private static List<ItemStack> read(JsonElement value) throws Exception {
        var type=Class.forName("dev.xyat.contentstudio.recipe.client.gui.NativeRecipeStacks");
        var method=type.getDeclaredMethod("read",JsonElement.class);method.setAccessible(true);
        return (List<ItemStack>)method.invoke(null,value);
    }
    private static JsonArray checkSequences(Level level) throws Exception {
        var results=new JsonArray();
        Class<?> assemblyClass=Class.forName("com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe");
        var lookup=assemblyClass.getMethod("getRecipe",Level.class,Container.class,RecipeType.class,Class.class);
        var targetItems=Set.of("kubejs:incomplete_emx_b1","kubejs:incomplete_12g","kubejs:incomplete_3006","kubejs:incomplete_rocket","kubejs:incomplete_76239","kubejs:incomplete_arrow");
        for(var recipe:level.getRecipeManager().getRecipes()) {
            if(!assemblyClass.isInstance(recipe))continue;
            var transitional=(ItemStack)assemblyClass.getMethod("getTransitionalItem").invoke(recipe);
            if(!targetItems.contains(BuiltInRegistries.ITEM.getKey(transitional.getItem()).toString()))continue;
            var input=(Ingredient)assemblyClass.getMethod("getIngredient").invoke(recipe);
            ItemStack stack=input.getItems()[0].copy();
            var sequence=(List<?>)assemblyClass.getMethod("getSequence").invoke(recipe);
            int loops=(int)assemblyClass.getMethod("getLoops").invoke(recipe);
            for(int step=0;step<sequence.size()*loops;step++) {
                var wrapped=sequence.get(step%sequence.size());
                var nativeStep=(Recipe<?>)wrapped.getClass().getMethod("getRecipe").invoke(wrapped);
                var items=new ItemStackHandler(2);items.setStackInSlot(0,stack);
                if(nativeStep.getIngredients().size()>1)items.setStackInSlot(1,nativeStep.getIngredients().get(1).getItems()[0].copy());
                var found=(Optional<?>)lookup.invoke(null,level,new RecipeWrapper(items),nativeStep.getType(),nativeStep.getClass());
                require(found.isPresent(),recipe.getId()+" missing native step "+step);
                var outputs=(List<?>)found.get().getClass().getMethod("rollResults").invoke(found.get());
                require(!outputs.isEmpty(),recipe.getId()+" empty output at step "+step);
                stack=(ItemStack)outputs.get(0);
            }
            require(stack.is(recipe.getResultItem(level.registryAccess()).getItem()),recipe.getId()+" final product");
            var row=new JsonObject();row.addProperty("id",recipe.getId().toString());row.addProperty("steps",sequence.size()*loops);row.addProperty("passed",true);results.add(row);
        }
        require(results.size()==6,"All six migrated sequences are present");return results;
    }
    private static void screenshot(String name) throws Exception {
        var mc=Minecraft.getInstance();try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(directory.resolve(name));}
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void finish(Throwable error) {
        if(finished)return;finished=true;report.addProperty("passed",error==null);
        if(error!=null){report.addProperty("error",error.toString());LOG.error("RECIPE_REPAIR_VALIDATION_FAIL",error);}
        else LOG.info("RECIPE_REPAIR_VALIDATION_PASS {}",report);
        try {if(directory==null)directory=Minecraft.getInstance().gameDirectory.toPath().resolve("recipe-repair-validation");Files.createDirectories(directory);Files.writeString(directory.resolve("result.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report));}
        catch(Exception writeError){LOG.error("Could not write recipe repair receipt",writeError);}
        Minecraft.getInstance().stop();
    }
}
//?}
