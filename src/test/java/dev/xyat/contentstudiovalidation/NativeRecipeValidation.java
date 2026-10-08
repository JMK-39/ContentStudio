package dev.xyat.contentstudiovalidation;

import com.google.gson.*;
import dev.xyat.contentstudio.recipe.RecipeMemoryManager;
import dev.xyat.contentstudio.recipe.nativeedit.*;
import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import net.minecraft.client.Minecraft;
import java.util.*;
import java.util.function.Consumer;

/** Opt-in real packet/serializer/reload checks in a copied installed profile. Never shipped. */
public final class NativeRecipeValidation {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(NativeRecipeValidation.class);
    public static JsonObject preview;
    private static JsonObject imported, copied;
    private static String id, originalJson;
    private static String changedOriginal;
    private static int stage;
    private static boolean started,finished;
    private static long deadline;
    private static final String COPY="contentstudio:native_recipe_validation";
    private static final Consumer<NativeRecipeNetwork.Response> CALLBACK=NativeRecipeValidation::accept;
    private static Consumer<NativeRecipeNetwork.Response> templateCallback;
    private NativeRecipeValidation() { }
    public static void install() {LOG.info("CONTENT_NATIVE_INSTALL");KineticClientEvents.onTick(KineticClientEvents.TickPhase.END,NativeRecipeValidation::tick);}
    private static void tick() {
        GuiLongTextValidation.prepareOwnedWorld();
        if(finished)return;
        var mc=Minecraft.getInstance();
        if(!started && mc.player!=null && mc.level!=null && mc.getSingleplayerServer()!=null) {
            started=true;deadline=System.currentTimeMillis()+120_000;
            onServer(()->{
                var server=mc.getSingleplayerServer();
                var player=server.getPlayerList().getPlayers().get(0);
                //? if >=26.1 {
                /*server.getPlayerList().op(new net.minecraft.server.players.NameAndId(player.getGameProfile()));
                *///?} else {
                server.getPlayerList().op(player.getGameProfile());
                //?}
                require(player.hasPermissions(2),"owned validation world grants editor permission");
            },()->send("list","","",null,""));
        } else if(started && System.currentTimeMillis()>deadline) fail(new AssertionError("Native recipe stage timed out: "+stage));
    }
    private static void send(String action,String source,String target,JsonObject header,String json) {
        deadline=System.currentTimeMillis()+120_000;
        String query=Boolean.getBoolean("contentstudio.nativeRecipeValidation.thirdParty")?"farmersdelight:cutting/":"minecraft:stick";
        NativeRecipeClient.expect(NativeRecipeNetwork.request(action,source,target,query,0,
                header==null?0:header.get("catalog").getAsLong(),header==null?"":header.get("revision").getAsString(),json),CALLBACK);
    }
    private static void accept(NativeRecipeNetwork.Response packet) {
        try {
            JsonObject body=JsonParser.parseString(packet.body()).getAsJsonObject();
            switch(stage++) {
                case 0 -> {
                    require(packet.success() && !body.getAsJsonArray("rows").isEmpty(),"discover installed recipes");
                    id=body.getAsJsonArray("rows").get(0).getAsJsonObject().get("id").getAsString();
                    LOG.info("CONTENT_NATIVE_DISCOVERY recipe={}",id);send("open",id,"",null,"");
                }
                case 1 -> {
                    require(packet.success(),"import source"); imported=body.deepCopy();preview=body.deepCopy();
                    originalJson=body.getAsJsonObject("recipe").toString();
                    var draft=body.getAsJsonObject("recipe").deepCopy();
                    draft.add("contentstudio_preservation_probe",JsonParser.parseString("{\"precise\":1234567890123456789,\"nested\":[true,\"retained\"]}"));
                    var document=new NativeRecipeDocument(draft);
                    List<String> count=findCount(draft,List.of());
                    if(count==null && draft.get("result").isJsonArray()) {
                        document.add(List.of("result","0"),"count",new JsonPrimitive(1));count=List.of("result","0","count");
                    }
                    if(count==null && draft.get("result").isJsonObject()) {
                        document.add(List.of("result"),"count",new JsonPrimitive(1));count=List.of("result","count");
                    }
                    if(count!=null) document.setPrimitive(count,"2");
                    send("save",id,COPY,body,document.json().toString());
                }
                case 2 -> {
                    require(packet.success(),"copy and reload");
                    onServer(()->{
                        var manager=Minecraft.getInstance().getSingleplayerServer().getRecipeManager();
                        require(RecipeMemoryManager.containsRuntimeRecipe(manager,KineticResourceIds.parse(COPY)),"copy exists after reload");
                        var inactive=imported.getAsJsonObject("recipe").deepCopy();
                        var condition=new JsonObject();condition.addProperty("modid","contentstudio_missing_validation");
                        //? if >=1.21 {
                        /*condition.addProperty("type","neoforge:mod_loaded");String key="neoforge:conditions";
                        *///?} else {
                        condition.addProperty("type","forge:mod_loaded");String key="conditions";
                        //?}
                        var conditions=new JsonArray();conditions.add(condition);inactive.add(key,conditions);
                        boolean rejected=false;
                        try {RecipeMemoryManager.decodeNative(manager,KineticResourceIds.parse(COPY),inactive);}catch(RuntimeException expected){rejected=true;}
                        require(rejected,"inactive native conditions rejected");
                    },()->send("open",COPY,"",null,""));
                }
                case 3 -> {
                    require(packet.success(),"reimport saved copy");copied=body.deepCopy();
                    require(body.getAsJsonObject("recipe").getAsJsonObject("contentstudio_preservation_probe").get("precise").getAsString().equals("1234567890123456789"),"unknown fields survive save and reload");
                    send("save",id,id,imported,originalJson);
                }
                case 4 -> {
                    require(!packet.success() && body.get("message").getAsString().equals("STALE"),"reject stale edit");
                    var invalid=copied.getAsJsonObject("recipe").deepCopy();invalid.addProperty("type","missing:serializer");
                    send("save",COPY,COPY,copied,invalid.toString());
                }
                case 5 -> {
                    require(!packet.success(),"reject missing serializer before persistence");send("open",COPY,"",null,"");
                }
                case 6 -> {
                    require(packet.success() && body.get("recipe").equals(copied.get("recipe")),"invalid draft does not change file");copied=body;
                    send("save",COPY,"minecraft:stick",copied,copied.get("recipe").toString());
                }
                case 7 -> {
                    require(!packet.success() && body.get("message").getAsString().equals("ID_EXISTS"),"reject existing copy ID");
                    send("save",COPY,"contentstudio:_native_validation_reserved",copied,copied.get("recipe").toString());
                }
                case 8 -> {
                    require(!packet.success() && body.get("message").getAsString().equals("ID_EXISTS"),"reject inactive datapack copy ID");
                    send("restore",COPY,COPY,copied,"");
                }
                case 9 -> {
                    require(packet.success(),"restore copied recipe");
                    onServer(()->require(!RecipeMemoryManager.containsRuntimeRecipe(Minecraft.getInstance().getSingleplayerServer().getRecipeManager(),KineticResourceIds.parse(COPY)),"removed override disappears"),()->send("open",id,"",null,""));
                }
                case 10 -> {
                    require(packet.success() && body.get("recipe").toString().equals(originalJson),"source unchanged");
                    var document=new NativeRecipeDocument(body.getAsJsonObject("recipe"));
                    var count=findCount(document.json(),List.of());
                    if(count==null && document.json().get("result").isJsonArray()) {
                        document.add(List.of("result","0"),"count",new JsonPrimitive(1));count=List.of("result","0","count");
                    }
                    if(count==null && document.json().get("result").isJsonObject()) {
                        document.add(List.of("result"),"count",new JsonPrimitive(1));count=List.of("result","count");
                    }
                    require(count!=null,"quantity field editable");document.setPrimitive(count,"3");
                    changedOriginal=document.json().toString();send("save",id,id,body,changedOriginal);
                }
                case 11 -> {
                    require(packet.success(),"replace original ID and reload");send("open",id,"",null,"");
                }
                case 12 -> {
                    require(packet.success() && body.get("recipe").toString().equals(changedOriginal),"modified original survives reimport");
                    copied=body;onServer(()->checkQuantity(3),()->send("restore",id,id,copied,""));
                }
                case 13 -> { require(packet.success(),"restore original ID and reload");send("open",id,"",null,""); }
                case 14 -> {
                    require(packet.success() && body.get("recipe").toString().equals(originalJson),"restore original fields");
                    preview=body;
                    //? if >=26.1 {
                    /*var invalid=body.getAsJsonObject("recipe").deepCopy();
                    invalid.add("result",JsonParser.parseString("{\"id\":\"minecraft:diamond_sword\",\"count\":2}"));
                    String before=NativeRecipeStore.local().toString();
                    templateCallback=reply->{try {
                        require(!reply.success(),"reject native template exceeding maximum stack size");
                        require(NativeRecipeStore.local().toString().equals(before),"invalid native template preserves file");finishNative();
                    }catch(Throwable error){fail(error);}};
                    NativeRecipeClient.expect(NativeRecipeNetwork.request("save",id,id,"",0,body.get("catalog").getAsLong(),body.get("revision").getAsString(),invalid.toString()),templateCallback);
                    *///?} else {
                    finishNative();
                    //?}
                }
                default -> throw new AssertionError("Unexpected native validation stage");
            }
        } catch(Throwable error) {fail(error);}
    }
    private static void finishNative() {
        finished=true;LOG.info("CONTENT_NATIVE_PASS stages={} thirdParty={} recipe={}",stage,Boolean.getBoolean("contentstudio.nativeRecipeValidation.thirdParty"),id);
        if(Boolean.getBoolean("contentstudio.nativeRecipeValidation.thenMatrixCaptureOnly"))NativeRecipeMatrixValidation.install();
        else GuiLongTextValidation.install();
    }
    private static void checkQuantity(int expected) {
        var server=Minecraft.getInstance().getSingleplayerServer();
        for(var holder:server.getRecipeManager().getRecipes()) {
            //? if >=26.1 {
            /*if(!holder.id().identifier().toString().equals(id))continue;var recipe=holder.value();
            *///?} else if >=1.21 {
            /*if(!holder.id().toString().equals(id))continue;var recipe=holder.value();
            *///?} else {
            if(!holder.getId().toString().equals(id))continue;var recipe=holder;
            //?}
            try {
                if(Boolean.getBoolean("contentstudio.nativeRecipeValidation.thirdParty")) {
                    var results=(List<?>)recipe.getClass().getMethod("getRollableResults").invoke(recipe);
                    var first=results.get(0);
                    //? if >=1.21 {
                    /*var stack=(net.minecraft.world.item.ItemStack)first.getClass().getMethod("stack").invoke(first);
                    *///?} else {
                    var stack=(net.minecraft.world.item.ItemStack)first.getClass().getMethod("getStack").invoke(first);
                    //?}
                    require(stack.getCount()==expected,"live third-party recipe output quantity");
                } else {
                    //? if >=26.1
                    /*require(dev.xyat.contentstudio.recipe.RecipeView.output(recipe,server.registryAccess()).getCount()==expected,"live native recipe output quantity");*/
                }
                return;
            } catch(ReflectiveOperationException error) {throw new AssertionError(error);}
        }
        throw new AssertionError("Live edited recipe missing");
    }
    private static List<String> findCount(JsonElement value,List<String> path) {
        if(value.isJsonObject()) for(var entry:value.getAsJsonObject().entrySet()) {
            var next=new ArrayList<>(path);next.add(entry.getKey());
            if(entry.getKey().equals("count") && entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber()) return next;
            var found=findCount(entry.getValue(),next);if(found!=null)return found;
        } else if(value.isJsonArray()) for(int i=0;i<value.getAsJsonArray().size();i++) {
            var next=new ArrayList<>(path);next.add(Integer.toString(i));var found=findCount(value.getAsJsonArray().get(i),next);if(found!=null)return found;
        }
        return null;
    }
    private static void onServer(Runnable check,Runnable next) {
        var mc=Minecraft.getInstance();mc.getSingleplayerServer().execute(()->{
            try {check.run();mc.execute(next);}catch(Throwable error){mc.execute(()->fail(error));}
        });
    }
    private static void require(boolean value,String message) {if(!value)throw new AssertionError(message);LOG.info("CONTENT_NATIVE_CHECK_PASS {}",message);}
    private static void fail(Throwable error) {finished=true;LOG.error("CONTENT_NATIVE_FAIL stage="+stage,error);dev.xyat.kineticcore.api.runtime.KineticClientRuntime.stopClient();}
}
