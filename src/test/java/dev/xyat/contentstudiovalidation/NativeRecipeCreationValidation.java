package dev.xyat.contentstudiovalidation;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import dev.xyat.contentstudio.recipe.RecipeMemoryManager;
import dev.xyat.contentstudio.recipe.client.RecipeJeiBridge;
import dev.xyat.contentstudio.recipe.client.gui.NativeRecipeEditorPage;
import dev.xyat.contentstudio.recipe.client.gui.NativeRecipeBrowserPage;
import dev.xyat.contentstudio.recipe.nativeedit.*;
import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.client.gui.KineticGui;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.Recipe;
import java.util.*;
import java.nio.file.*;
import java.util.function.Consumer;

/** Independent recipe creation, collision protection and persistence without JEI, in an owned test world. */
public final class NativeRecipeCreationValidation {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(NativeRecipeCreationValidation.class);
    private record Sample(String source,String type,JsonObject json,String fingerprint) { }
    private static final List<Sample> samples=new ArrayList<>();
    private static final Consumer<NativeRecipeNetwork.Response> CALLBACK=NativeRecipeCreationValidation::accept;
    private static net.minecraft.server.MinecraftServer server;
    private static boolean started,finished,verify;
    private static int index,phase;
    private static long deadline;
    private static JsonObject draft;
    private static String id,expected,createdRevision;
    public static void install() {deadline=System.currentTimeMillis()+180_000;KineticClientEvents.onTick(KineticClientEvents.TickPhase.END,NativeRecipeCreationValidation::tick);}
    private static void tick() {
        if(finished)return;
        try {
            GuiLongTextValidation.prepareOwnedWorld();var mc=Minecraft.getInstance();
            if(System.currentTimeMillis()>deadline)throw new AssertionError("Creation timeout at "+index+" / "+phase);
            if(started||mc.player==null||mc.getSingleplayerServer()==null)return;
            verify=Boolean.getBoolean("contentstudio.nativeRecipeCreation.verifyWithoutJei");
            if(!verify&&!RecipeJeiBridge.jeiAvailable())return;
            started=true;server=mc.getSingleplayerServer();mc.options.guiScale().set(0);mc.getWindow().setWindowed(1920,1080);mc.resizeDisplay();
            server.execute(()->{try {
                var player=server.getPlayerList().getPlayers().get(0);
                //? if >=26.1 {
                /*server.getPlayerList().op(new net.minecraft.server.players.NameAndId(player.getGameProfile()));
                *///?} else {
                server.getPlayerList().op(player.getGameProfile());
                //?}
                prepare();mc.execute(NativeRecipeCreationValidation::next);
            }catch(Throwable error){mc.execute(()->fail(error));}});
        }catch(Throwable error){fail(error);}
    }
    private static void prepare() throws Exception {
        if(verify) {
            require(!RecipeJeiBridge.jeiAvailable(),"JEI actually absent");
            for(var entry:NativeRecipeStore.local().entrySet())if(entry.getKey().startsWith("contentstudio:_jei_creation/")) {
                require(RecipeMemoryManager.containsRuntimeRecipe(server.getRecipeManager(),KineticResourceIds.parse(entry.getKey())),"created recipe loaded after restart without JEI");
                samples.add(new Sample(entry.getKey(),NativeRecipeDocument.serializerId(entry.getValue().getAsJsonObject()),entry.getValue().getAsJsonObject(),live(entry.getKey())));
                LOG.info("CONTENT_CREATE_PERSIST_WITHOUT_JEI_PASS id={}",entry.getKey());
            }
        } else {
            var chosen=new TreeMap<String,Sample>();
            for(var entry:RecipeMemoryManager.originalCatalog(server.getRecipeManager()).sources().entrySet())try {
                var json=entry.getValue().getAsJsonObject();String type=NativeRecipeDocument.serializerId(json),mod=type.split(":")[0];
                if(NativeRecipeCreation.vanilla(type)||!RecipeMemoryManager.containsRuntimeRecipe(server.getRecipeManager(),entry.getKey()))continue;
                String key=type.equals("create:sequenced_assembly")?"create_sequence":mod;
                String before=fingerprint(decode(entry.getKey().toString(),json));if(!before.equals(fingerprint(decode(entry.getKey().toString(),json))))continue;
                chosen.putIfAbsent(key,new Sample(entry.getKey().toString(),type,json,before));
            }catch(RuntimeException unsupported){ }
            samples.addAll(chosen.values());
        }
        require(!samples.isEmpty(),"third-party samples available");LOG.info("CONTENT_CREATE_PREPARED count={} verify={}",samples.size(),verify);
    }
    private static void next() {
        phase=0;
        if(index>=samples.size()){finished=true;LOG.info("CONTENT_CREATE_PASS count={} verify={}",samples.size(),verify);Minecraft.getInstance().stop();return;}
        if(verify){id=samples.get(index).source();send("open",0,"");}else send("template",0,"");
    }
    private static void send(String action,long catalog,String revision) {
        deadline=System.currentTimeMillis()+180_000;
        String source=action.equals("template")?samples.get(index).source():id;
        NativeRecipeClient.expect(NativeRecipeNetwork.request(action,source,action.equals("saved")?"":""+source,
                action.equals("saved")?"@"+samples.get(index).type().split(":")[0]+" ":"",0,catalog,revision,
                action.equals("create")?draft.getAsJsonObject("recipe").toString():""),CALLBACK);
    }
    private static void accept(NativeRecipeNetwork.Response packet) {
        try {
            var body=JsonParser.parseString(packet.body()).getAsJsonObject();
            if(verify) {
                require(packet.success(),"verify open/delete successful");
                if(phase++==0) {
                    require(NativeRecipeEditorPage.create(body,()->{})==null,"third-party editor hidden without JEI");
                    send("restore",body.get("catalog").getAsLong(),body.get("revision").getAsString());
                }else onServer(()->require(!RecipeMemoryManager.containsRuntimeRecipe(server.getRecipeManager(),KineticResourceIds.parse(id)),"created recipe removed"),()->{index++;next();});
                return;
            }
            if(phase==3) {
                require(!packet.success()&&body.get("message").getAsString().equals("ID_EXISTS"),"duplicate create rejected");
                onServer(()->{require(live(id).equals(expected),"collision kept existing recipe");},()->{phase++;send("saved",0,"");});return;
            }
            require(packet.success(),"server accepted creation stage "+phase+": "+body);
            switch(phase++) {
                case 0 -> {
                    require(body.get("creating").getAsBoolean(),"template response creates an independent draft");
                    require(body.getAsJsonObject("recipe").equals(samples.get(index).json()),"template retains all data");
                    id="contentstudio:_jei_creation/"+samples.get(index).type().replace(':','/');draft=body;
                    var doc=new NativeRecipeDocument(body.getAsJsonObject("recipe"));
                    for(var slot:NativeRecipeLayout.of(doc.json()).slots())if(slot.output()){NativeRecipeSlotEdits.select(doc,slot.path(),"minecraft:diamond",false);break;}
                    draft.add("recipe",doc.json());
                    onServer(()->expected=fingerprint(decode(id,doc.json())),()->{
                        var editor=NativeRecipeEditorPage.create(draft,()->{});require(editor!=null,"new recipe has an editor");
                        KineticGui.open(editor);
                        try {
                            var sessionField=editor.getClass().getDeclaredField("session");sessionField.setAccessible(true);var session=sessionField.get(editor);
                            var target=session.getClass().getDeclaredField("target");target.setAccessible(true);target.set(session,id);
                            if(editor instanceof NativeRecipeEditorPage) {
                                var tick=editor.getClass().getDeclaredMethod("onTick");tick.setAccessible(true);tick.invoke(editor);
                                var previewField=editor.getClass().getDeclaredField("jeiPreview");previewField.setAccessible(true);Object preview=previewField.get(editor);
                                if(preview!=null) {
                                    var layoutField=preview.getClass().getDeclaredField("layout");layoutField.setAccessible(true);Object layout=layoutField.get(preview);
                                    Object recipe=layout.getClass().getMethod("getRecipe").invoke(layout);
                                    //? if >=26.1 {
                                    /*String shown=recipe instanceof net.minecraft.world.item.crafting.RecipeHolder<?> holder?holder.id().identifier().toString():null;
                                    *///?} else if >=1.21 {
                                    /*String shown=recipe instanceof net.minecraft.world.item.crafting.RecipeHolder<?> holder?holder.id().toString():null;
                                    *///?} else {
                                    String shown=recipe instanceof Recipe<?> nativeRecipe?nativeRecipe.getId().toString():null;
                                    //?}
                                    if(shown!=null)require(shown.equals(id),"JEI preview uses the edited target ID: "+shown+" != "+id);
                                }
                            }
                        }catch(ReflectiveOperationException error){throw new AssertionError(error);}
                        send("create",body.get("catalog").getAsLong(),body.get("revision").getAsString());
                    });
                }
                case 1 -> onServer(()->{
                    require(live(id).equals(expected),"new native recipe installed");require(live(samples.get(index).source()).equals(samples.get(index).fingerprint()),"template unchanged");
                },()->send("open",0,""));
                case 2 -> {
                    require(body.getAsJsonObject("recipe").equals(draft.getAsJsonObject("recipe")),"new recipe persisted without field loss");
                    require(body.get("added").getAsBoolean(),"new recipe marked for deletion rather than restoring a template");
                    createdRevision=body.get("revision").getAsString();send("create",body.get("catalog").getAsLong(),createdRevision);
                }
                case 4 -> {
                    require(body.getAsJsonArray("rows").asList().stream().anyMatch(row->row.getAsJsonObject().get("id").getAsString().equals(id)),"saved recipe found by mod filter");
                    LOG.info("CONTENT_CREATE_CASE_PASS type={} id={} source={}",samples.get(index).type(),id,samples.get(index).source());index++;next();
                }
                default -> throw new AssertionError("Unexpected creation phase "+phase);
            }
        }catch(Throwable error){fail(error);}
    }
    private static Recipe<?> decode(String id,JsonObject json) {
        //? if >=1.21 {
        /*return RecipeMemoryManager.decodeNative(server.getRecipeManager(),KineticResourceIds.parse(id),json).value();
        *///?} else {
        return RecipeMemoryManager.decodeNative(server.getRecipeManager(),KineticResourceIds.parse(id),json);
        //?}
    }
    @SuppressWarnings({"rawtypes","unchecked"})private static String fingerprint(Recipe<?> recipe) {
        //? if >=1.21 {
        /*return Recipe.CODEC.encodeStart(net.minecraft.resources.RegistryOps.create(JsonOps.INSTANCE,server.registryAccess()),recipe).getOrThrow().toString();
        *///?} else {
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {((net.minecraft.world.item.crafting.RecipeSerializer)recipe.getSerializer()).toNetwork(buffer,recipe);byte[] bytes=new byte[buffer.readableBytes()];buffer.getBytes(0,bytes);return Base64.getEncoder().encodeToString(bytes);}finally{buffer.release();}
        //?}
    }
    private static String live(String id) {
        for(var recipe:server.getRecipeManager().getRecipes()) {
            //? if >=26.1 {
            /*if(recipe.id().identifier().toString().equals(id))return fingerprint(recipe.value());
            *///?} else if >=1.21 {
            /*if(recipe.id().toString().equals(id))return fingerprint(recipe.value());
            *///?} else {
            if(recipe.getId().toString().equals(id))return fingerprint(recipe);
            //?}
        }throw new AssertionError("Recipe not loaded: "+id);
    }
    private static void onServer(Runnable check,Runnable next) {server.execute(()->{try{check.run();Minecraft.getInstance().execute(()->{try{next.run();}catch(Throwable error){fail(error);}});}catch(Throwable error){Minecraft.getInstance().execute(()->fail(error));}});}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void fail(Throwable error){if(finished)return;finished=true;LOG.error("CONTENT_CREATE_FAIL index="+index+" phase="+phase,error);Minecraft.getInstance().stop();}
}
