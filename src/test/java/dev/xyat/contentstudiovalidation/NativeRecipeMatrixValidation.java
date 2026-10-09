package dev.xyat.contentstudiovalidation;

import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import dev.xyat.contentstudio.recipe.RecipeMemoryManager;
import dev.xyat.contentstudio.recipe.nativeedit.*;
import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Real installed serializers, real network saves and live recipe-manager verification. Opt-in, never shipped. */
public final class NativeRecipeMatrixValidation {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(NativeRecipeMatrixValidation.class);
    private record Case(String id,String type,List<String> path,String value,JsonObject draft,String before,String after) { }
    private static final List<Case> cases=new ArrayList<>();
    private static final List<JsonObject> previews=new ArrayList<>();
    private static final JsonArray results=new JsonArray();
    private static final Consumer<NativeRecipeNetwork.Response> CALLBACK=NativeRecipeMatrixValidation::accept;
    private static Consumer<NativeRecipeNetwork.Response> identityCallback;
    private static MinecraftServer server;
    private static int index,phase;
    private static boolean started,finished;
    private static long deadline;
    public static void install(){
        if(Boolean.getBoolean("contentstudio.nativeRecipeMatrix.resume"))try {
            var path=Path.of(System.getProperty("contentstudio.nativeRecipeMatrix.output"));
            if(Files.exists(path))JsonParser.parseString(Files.readString(path)).getAsJsonArray().forEach(row->{if(row.getAsJsonObject().get("status").getAsString().equals("PASS"))results.add(row);});
        }catch(Exception error){throw new IllegalStateException("Cannot resume matrix evidence",error);}
        KineticClientEvents.onTick(KineticClientEvents.TickPhase.END,NativeRecipeMatrixValidation::tick);
    }
    private static void tick() {
        GuiLongTextValidation.prepareOwnedWorld();
        if(!started && Minecraft.getInstance().screen!=null && Minecraft.getInstance().level==null
                && System.currentTimeMillis()%10_000<60)
            LOG.info("CONTENT_MATRIX_WAIT_SCREEN {}",Minecraft.getInstance().screen.getClass().getName());
        if(finished)return;var mc=Minecraft.getInstance();
        if(!started && mc.player!=null && mc.getSingleplayerServer()!=null) {
            started=true;server=mc.getSingleplayerServer();deadline=System.currentTimeMillis()+180_000;
            onServer(()->{
                var player=server.getPlayerList().getPlayers().get(0);
                //? if >=26.1 {
                /*server.getPlayerList().op(new net.minecraft.server.players.NameAndId(player.getGameProfile()));
                *///?} else {
                server.getPlayerList().op(player.getGameProfile());
                //?}
                prepare();
            },NativeRecipeMatrixValidation::identityPreflight);
        }else if(started && System.currentTimeMillis()>deadline)fail(new AssertionError("Matrix timeout at "+index+" / "+phase));
    }
    private static void identityPreflight() {
        //? if <1.21 {
        var id=KineticResourceIds.parse("ars_nouveau:protection_1");
        var source=RecipeMemoryManager.originalCatalog(server.getRecipeManager()).sources().get(id);
        if(source!=null)try {
            String fileBefore=NativeRecipeStore.local().toString(),oneBefore=live(id.toString()),twoBefore=live("ars_nouveau:protection_2");
            var draft=source.getAsJsonObject().deepCopy();draft.addProperty("level",2);
            var local=NativeRecipeStore.local();
            identityCallback=packet->{
                try {
                    var body=JsonParser.parseString(packet.body()).getAsJsonObject();
                    require(!packet.success() && body.get("message").getAsString().equals("DERIVED_ID:ars_nouveau:protection_2"),"derived ID change rejected");
                    onServer(()->{
                        try {require(NativeRecipeStore.local().toString().equals(fileBefore),"identity rejection preserves file");}
                        catch(java.io.IOException error){throw new AssertionError(error);}
                        require(live(id.toString()).equals(oneBefore) && live("ars_nouveau:protection_2").equals(twoBefore),"identity rejection preserves both recipes");
                        LOG.info("CONTENT_MATRIX_IDENTITY_GUARD_PASS source={} conflicting=ars_nouveau:protection_2",id);
                    },NativeRecipeMatrixValidation::next);
                }catch(Throwable error){fail(error);}
            };
            NativeRecipeClient.expect(NativeRecipeNetwork.request("save",id.toString(),id.toString(),"",0,
                    RecipeMemoryManager.catalogVersion(server.getRecipeManager()),NativeRecipeStore.revision(local),draft.toString()),identityCallback);return;
        }catch(Throwable error){fail(error);return;}
        //?}
        next();
    }
    private static void prepare() {
        var manager=server.getRecipeManager();var grouped=new TreeMap<String,List<Map.Entry<net.minecraft.resources.ResourceLocation,JsonElement>>>();
        String filter=System.getProperty("contentstudio.nativeRecipeMatrix.mods","");
        for(var entry:RecipeMemoryManager.originalCatalog(manager).sources().entrySet()) {
            String type=NativeRecipeDocument.serializerId(entry.getValue().getAsJsonObject()), mod=type.split(":",2)[0];
            if(mod.equals("minecraft") || mod.equals("contentstudio") || (!filter.isBlank() && !List.of(filter.split(",")).contains(mod)))continue;
            if(RecipeMemoryManager.containsRuntimeRecipe(manager,entry.getKey()))grouped.computeIfAbsent(type,key->new ArrayList<>()).add(entry);
        }
        var previewMods=new HashSet<String>();
        for(var group:grouped.entrySet()) {
            if(results.asList().stream().anyMatch(v->v.getAsJsonObject().get("type").getAsString().equals(group.getKey()) && v.getAsJsonObject().get("status").getAsString().equals("PASS")))continue;
            Case chosen=null;
            if(Set.of("powah","youkaisfeasts","twilightforest").contains(group.getKey().split(":",2)[0]))
                group.getValue().sort((left,right)->Integer.compare(NativeRecipeLayout.of(right.getValue().getAsJsonObject()).slots().size(),NativeRecipeLayout.of(left.getValue().getAsJsonObject()).slots().size()));
            for(var entry:group.getValue()) {
                try {chosen=probe(entry.getKey(),group.getKey(),entry.getValue().getAsJsonObject());}catch(RuntimeException rejected){ }
                if(chosen!=null)break;
            }
            if(chosen!=null) {
                cases.add(chosen);
                String mod=group.getKey().split(":",2)[0];
                if(previewMods.add(mod) || Set.of("farmersdelight:cooking","farmersdelight:cutting","create:sequenced_assembly","extendedcrafting:shaped_table").contains(group.getKey())
                        ||Set.of("powah","youkaisfeasts","twilightforest").contains(mod)) {
                    var body=new JsonObject();body.addProperty("id",chosen.id());body.addProperty("catalog",RecipeMemoryManager.catalogVersion(manager));body.addProperty("revision","");body.addProperty("edited",false);
                    body.add("recipe",RecipeMemoryManager.originalCatalog(manager).sources().get(KineticResourceIds.parse(chosen.id())).deepCopy());
                    var path=new JsonArray();chosen.path().forEach(path::add);body.add("testPath",path);body.addProperty("testValue",chosen.value());body.add("testDraft",chosen.draft());previews.add(body);
                }
            } else record(group.getKey(),"","UNVERIFIED","No serializer-visible editable primitive found");
        }
        LOG.info("CONTENT_MATRIX_PREPARED types={} editable={} priorPassed={}",grouped.size(),cases.size(),results.asList().stream().filter(v->v.getAsJsonObject().get("status").getAsString().equals("PASS")).count());writeReport();
    }
    private static Case probe(net.minecraft.resources.ResourceLocation id,String type,JsonObject json) {
        var original=new NativeRecipeDocument(json);String before=fingerprint(decode(id,json));
        String repeated=fingerprint(decode(id,json));
        if(!before.equals(repeated)) {
            LOG.warn("CONTENT_MATRIX_UNSTABLE_SAMPLE type={} id={} firstDifference={}",type,id,firstDifference(before,repeated));
            return null; // Some result stacks receive random capability data; use a deterministic recipe of this type.
        }
        var paths=NativeRecipeLayout.fields(original,List.of());
        for(var path:paths) {
            if(path.equals(List.of("type")))continue;
            var primitive=original.at(path).getAsJsonPrimitive();String key=path.get(path.size()-1), value=null;
            if(primitive.isNumber()) {
                var number=primitive.getAsBigDecimal();value=(key.toLowerCase(Locale.ROOT).contains("chance") || key.toLowerCase(Locale.ROOT).contains("probability")) && number.signum()>0 && number.compareTo(java.math.BigDecimal.ONE)<=0
                        ?number.divide(java.math.BigDecimal.valueOf(2)).toPlainString():number.add(java.math.BigDecimal.ONE).toPlainString();
            }else if(primitive.isBoolean())value=Boolean.toString(!primitive.getAsBoolean());
            else if(primitive.isString() && !Set.of("type","dragon_type","tier","mode","category","group","sound","action").contains(key)) {
                var item=KineticResourceIds.tryParse(primitive.getAsString());
                boolean itemField=primitive.getAsString().contains(":") || Set.of("item","id","input","output","ingredient","reagent","catalyst").contains(key) || key.endsWith("_item") || key.endsWith("_cell");
                if(itemField && item!=null && KineticRegistries.items().contains(item))value=primitive.getAsString().equals("minecraft:diamond")?"minecraft:emerald":"minecraft:diamond";
                if(type.equals("twilightforest:crumble_horn")&&Set.of("from","to").contains(key))value="minecraft:diamond_block";
            }
            if(value==null)continue;
            try {
                var document=new NativeRecipeDocument(json);document.setPrimitive(path,value);var draft=document.json();String after=fingerprint(decode(id,draft));
                if(!after.equals(before))return new Case(id.toString(),type,List.copyOf(path),value,draft,before,after);
            }catch(RuntimeException rejected){ }
        }return null;
    }
    private static Recipe<?> decode(net.minecraft.resources.ResourceLocation id,JsonObject json) {
        //? if >=1.21 {
        /*return RecipeMemoryManager.decodeNative(server.getRecipeManager(),id,json).value();
        *///?} else {
        return RecipeMemoryManager.decodeNative(server.getRecipeManager(),id,json);
        //?}
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static String fingerprint(Recipe<?> recipe) {
        //? if >=1.21 {
        /*var ops=net.minecraft.resources.RegistryOps.create(JsonOps.INSTANCE,server.registryAccess());
        return Recipe.CODEC.encodeStart(ops,recipe).getOrThrow().toString();
        *///?} else {
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {
            ((net.minecraft.world.item.crafting.RecipeSerializer)recipe.getSerializer()).toNetwork(buffer,recipe);
            byte[] bytes=new byte[buffer.readableBytes()];buffer.getBytes(0,bytes);return java.util.Base64.getEncoder().encodeToString(bytes);
        }finally{buffer.release();}
        //?}
    }
    private static String live(String id) {
        for(var holder:server.getRecipeManager().getRecipes()) {
            //? if >=26.1 {
            /*if(holder.id().identifier().toString().equals(id))return fingerprint(holder.value());
            *///?} else if >=1.21 {
            /*if(holder.id().toString().equals(id))return fingerprint(holder.value());
            *///?} else {
            if(holder.getId().toString().equals(id))return fingerprint(holder);
            //?}
        }throw new AssertionError("Recipe disappeared: "+id);
    }
    private static void next() {
        phase=0;if(index>=cases.size() || Boolean.getBoolean("contentstudio.nativeRecipeMatrix.captureOnly")){finish();return;}send("open",null);
    }
    private static void send(String action,JsonObject header) {
        deadline=System.currentTimeMillis()+180_000;var current=cases.get(index);
        NativeRecipeClient.expect(NativeRecipeNetwork.request(action,current.id(),current.id(),"",0,header==null?0:header.get("catalog").getAsLong(),
                header==null?"":header.get("revision").getAsString(),action.equals("save")?current.draft().toString():""),CALLBACK);
    }
    private static void accept(NativeRecipeNetwork.Response packet) {
        try {
            var body=JsonParser.parseString(packet.body()).getAsJsonObject();var current=cases.get(index);
            if(!packet.success())throw new AssertionError("Server rejected "+current.type()+": "+body);
            switch(phase++) {
                case 0 -> send("save",body);
                case 1 -> onServer(()->require(live(current.id()).equals(current.after()),"changed native recipe installed"),()->send("open",null));
                case 2 -> {require(body.getAsJsonObject("recipe").equals(current.draft()),"unmodified fields retained on disk");send("restore",body);}
                case 3 -> onServer(()->{
                    String actual=live(current.id());
                    if(!actual.equals(current.before())) {
                        var source=RecipeMemoryManager.originalCatalog(server.getRecipeManager()).sources().get(KineticResourceIds.parse(current.id()));
                        String fresh=fingerprint(decode(KineticResourceIds.parse(current.id()),source.getAsJsonObject()));
                        LOG.error("CONTENT_MATRIX_RESTORE_DIAGNOSTIC type={} originalLength={} liveLength={} freshLength={} liveMatchesFresh={} firstDifference={}",current.type(),current.before().length(),actual.length(),fresh.length(),actual.equals(fresh),firstDifference(current.before(),actual));
                    }
                    require(actual.equals(current.before()),"original native recipe restored");
                },()->{
                    record(current.type(),current.id(),"PASS",String.join(" / ",current.path())+" = "+current.value());
                    LOG.info("CONTENT_MATRIX_CASE_PASS type={} id={} field={} value={}",current.type(),current.id(),current.path(),current.value());index++;next();
                });
                default -> throw new AssertionError("Invalid matrix stage");
            }
        }catch(Throwable error){fail(error);}
    }
    private static int firstDifference(String left,String right) {int i=0;while(i<left.length() && i<right.length() && left.charAt(i)==right.charAt(i))i++;return i;}
    private static void record(String type,String id,String status,String detail) {
        var row=new JsonObject();row.addProperty("type",type);row.addProperty("id",id);row.addProperty("status",status);row.addProperty("detail",detail);results.add(row);writeReport();
    }
    private static void writeReport() {
        try {var path=Path.of(System.getProperty("contentstudio.nativeRecipeMatrix.output","native-recipe-matrix.json"));Files.createDirectories(path.toAbsolutePath().getParent());Files.writeString(path,results.toString());}
        catch(Exception error){throw new IllegalStateException(error);}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void onServer(Runnable check,Runnable next) {
        server.execute(()->{try{check.run();Minecraft.getInstance().execute(next);}catch(Throwable error){Minecraft.getInstance().execute(()->fail(error));}});
    }
    private static void fail(Throwable error) {
        if(finished)return;finished=true;String type=index<cases.size()?cases.get(index).type():"prepare";
        record(type,index<cases.size()?cases.get(index).id():"","FAIL",error.toString());LOG.error("CONTENT_MATRIX_FAIL index="+index+" phase="+phase,error);Minecraft.getInstance().stop();
    }
    private static void finish() {
        finished=true;long unverified=results.asList().stream().filter(v->!v.getAsJsonObject().get("status").getAsString().equals("PASS")).count();
        LOG.info("CONTENT_MATRIX_FINISH pass={} unverified={} types={}",index,unverified,results.size());
        if(Boolean.getBoolean("contentstudio.nativeRecipeMatrix.captureOnly") || Boolean.getBoolean("contentstudio.nativeRecipeMatrix.thenCapture")) {
            System.setProperty("contentstudio.nativeRecipeMatrix.captureOnly","true");GuiLongTextValidation.installNativeSamples(previews);
        }
        else Minecraft.getInstance().stop();
    }
}
