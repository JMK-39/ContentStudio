package dev.xyat.contentstudio.recipe.nativeedit;

import com.google.gson.*;
import dev.xyat.contentstudio.recipe.RecipeMemoryManager;
import dev.xyat.kineticcore.api.network.*;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Bounded on-demand recipe transport: no whole-catalog JSON broadcast. */
public final class NativeRecipeNetwork {
    private static final PacketChannel CHANNEL = PacketChannel.create(KineticResourceIds.of("contentstudio", "native_recipes"), "4", NetworkVersionPolicy.EXACT);
    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final Set<net.minecraft.server.MinecraftServer> RELOADING = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static boolean registered;
    private NativeRecipeNetwork() { }
    public static synchronized void register() {
        if (registered) return;
        CHANNEL.registerServerbound(0, Request.class, NetworkCodec.of(Request::encode, Request::new), NativeRecipeNetwork::handle);
        CHANNEL.registerClientbound(1, Response.class, NetworkCodec.of(Response::encode, Response::new), NativeRecipeClient::accept);
        registered = true;
    }
    public record Request(long sequence, String action, String id, String target, String query, int page,
                          long catalogVersion, String revision, String json) {
        public Request(NetworkBuffer b) { this(b.readLong(), b.readUtf(16), b.readUtf(256), b.readUtf(256), b.readUtf(256), b.readInt(), b.readLong(), b.readUtf(64), b.readUtf(NativeRecipeDocument.MAX_CHARS)); }
        public static void encode(NetworkBuffer b, Request p) {
            b.writeLong(p.sequence); b.writeUtf(p.action,16); b.writeUtf(p.id,256); b.writeUtf(p.target,256); b.writeUtf(p.query,256);
            b.writeInt(p.page); b.writeLong(p.catalogVersion); b.writeUtf(p.revision,64); b.writeUtf(p.json,NativeRecipeDocument.MAX_CHARS);
        }
    }
    public record Response(long sequence, boolean success, String body) {
        public Response(NetworkBuffer b) { this(b.readLong(), b.readBoolean(), b.readUtf(128_000)); }
        public static void encode(NetworkBuffer b, Response p) { b.writeLong(p.sequence); b.writeBoolean(p.success); b.writeUtf(p.body,128_000); }
    }
    public static long request(String action, String id, String target, String query, int page, long version, String revision, String json) {
        long sequence = SEQUENCE.incrementAndGet();
        CHANNEL.sendToServer(new Request(sequence, action, id, target, query, page, version, revision, json));
        return sequence;
    }
    public static void handle(Request packet, ServerPacketContext context) {
        ServerPlayer player = context.sender();
        if (!player.hasPermissions(2)) return;
        try {
            if (RELOADING.contains(player.getServer())) throw new IllegalArgumentException("BUSY");
            var manager = player.getServer().getRecipeManager();
            long version = RecipeMemoryManager.catalogVersion(manager);
            JsonObject local = NativeRecipeStore.local();
            Map<ResourceLocation, JsonElement> source = RecipeMemoryManager.originalCatalog(manager).sources();
            local.entrySet().forEach(e -> source.put(KineticResourceIds.parse(e.getKey()), e.getValue().deepCopy()));
            var views=new HashMap<ResourceLocation,NativeRecipeView>();
            source.forEach((id,value)->{
                if(value.isJsonObject())try {
                    views.put(id,NativeRecipeView.resolve(value.getAsJsonObject(),branch->RecipeMemoryManager.nativeConditionsMatch(manager,branch)));
                }catch(RuntimeException inactive){ }
            });
            var owners=new HashMap<ResourceLocation,String>();
            views.forEach((id,view)->{
                ResourceLocation registered=null;
                var original=RecipeMemoryManager.originalCatalog(manager).entries().get(id);
                if(!local.has(id.toString()) && original!=null)registered=original.candidate().recipeType();
                else try {
                    var decoded=RecipeMemoryManager.decodeNative(manager,id,view.recipe());
                    //? if >=1.21 {
                    /*registered=dev.xyat.kineticcore.api.registry.KineticRegistries.recipeTypes().id(decoded.value().getType());
                    *///?} else {
                    registered=dev.xyat.kineticcore.api.registry.KineticRegistries.recipeTypes().id(decoded.getType());
                    //?}
                }catch(RuntimeException unavailable){ }
                owners.put(id,NativeRecipeTypes.owner(registered==null?null:registered.toString(),view.recipe()));
                NativeRecipeStore.recordType(id,registered);
            });
            NativeRecipeStore.regroup();
            JsonObject response = new JsonObject();
            response.addProperty("action", packet.action);
            response.addProperty("catalog", version);
            response.addProperty("revision", NativeRecipeStore.revision(local));
            switch (packet.action) {
                case "stations" -> {
                    if(packet.page<0 || packet.page>100_000)throw new IllegalArgumentException("Invalid page");
                    var counts=new TreeMap<String,Integer>();
                    views.forEach((id,view)->counts.merge(owners.get(id)+"/"+type(view.recipe()),1,Integer::sum));
                    var types=List.copyOf(counts.keySet());
                    int pages=Math.max(1,(types.size()+23)/24), page=Math.min(packet.page,pages-1);
                    response.addProperty("page",page);response.addProperty("pages",pages);
                    var rows=new JsonArray();
                    for(int i=page*24;i<Math.min(types.size(),(page+1)*24);i++) {
                        String key=types.get(i);int split=key.indexOf('/');String owner=key.substring(0,split),type=key.substring(split+1);
                        var row=new JsonObject();row.addProperty("type",type);row.addProperty("owner",owner);
                        String icon=NativeRecipeStations.icon(type);
                        if(icon.equals("minecraft:knowledge_book")) {
                            for(var entry:RecipeMemoryManager.originalCatalog(manager).entries().entrySet()) {
                                var original=source.get(entry.getKey());
                                if(original==null || !views.containsKey(entry.getKey()) || !owner.equals(owners.get(entry.getKey())) || !type(views.get(entry.getKey()).recipe()).equals(type) || entry.getValue().recipe().isEmpty())continue;
                                try {
                                    //? if >=26.1 {
                                    /*var stack=dev.xyat.contentstudio.recipe.RecipeView.station(entry.getValue().recipe().get().value(),player.getServer().registryAccess());
                                    *///?} else if >=1.21 {
                                    /*var stack=entry.getValue().recipe().get().value().getToastSymbol();
                                    *///?} else {
                                    var stack=entry.getValue().recipe().get().getToastSymbol();
                                    //?}
                                    if(!stack.isEmpty() && (!stack.is(net.minecraft.world.item.Items.CRAFTING_TABLE) || row.get("owner").getAsString().equals("minecraft")))
                                        icon=dev.xyat.kineticcore.api.registry.KineticRegistries.items().id(stack.getItem()).toString();
                                }catch(RuntimeException unavailable){ }
                                break;
                            }
                        }
                        row.addProperty("icon",icon);row.addProperty("count",counts.get(key));rows.add(row);
                    }
                    response.add("rows",rows);
                }
                case "list", "saved" -> {
                    if (packet.page < 0 || packet.page > 100_000) throw new IllegalArgumentException("Invalid page");
                    String query = packet.query.toLowerCase(Locale.ROOT);
                    String modFilter="";
                    if(query.startsWith("@")) {int end=query.indexOf(' ');modFilter=end<0?query.substring(1):query.substring(1,end);query=end<0?"":query.substring(end+1);}
                    final String filterMod=modFilter,filterQuery=query;
                    var mods=new JsonArray();views.entrySet().stream().filter(e->!packet.action.equals("saved")||local.has(e.getKey().toString()))
                            .map(e->owners.get(e.getKey())).distinct().sorted().forEach(mods::add);response.add("mods",mods);
                    var ids = source.entrySet().stream().filter(e -> e.getValue().isJsonObject())
                            .filter(e -> views.containsKey(e.getKey()))
                            .filter(e -> !packet.action.equals("saved")||local.has(e.getKey().toString()))
                            .filter(e -> packet.target.isEmpty() || packet.target.equals("@vanilla")&&owners.get(e.getKey()).equals("minecraft") || type(views.get(e.getKey()).recipe()).equals(packet.target))
                            .filter(e -> filterMod.isEmpty()||owners.get(e.getKey()).equals(filterMod))
                            .filter(e -> (e.getKey() + " " + type(views.get(e.getKey()).recipe())+" "+resultIcon(views.get(e.getKey()).recipe())).toLowerCase(Locale.ROOT).contains(filterQuery))
                            .map(Map.Entry::getKey).sorted(Comparator.comparing(ResourceLocation::toString)).toList();
                    var batches=new ArrayList<JsonArray>();var rows=new JsonArray();int chars=0;
                    for (var id:ids) {
                        JsonObject row = new JsonObject();
                        var view=views.get(id);
                        row.addProperty("id", id.toString()); row.addProperty("type", type(view.recipe()));
                        row.addProperty("owner",owners.get(id));row.add("preview",NativeRecipeListIcon.preview(view.recipe()));
                        String icon=resultIcon(view.recipe());
                        var entry=RecipeMemoryManager.originalCatalog(manager).entries().get(id);
                        if(icon.equals("minecraft:knowledge_book") && entry!=null && entry.candidate().outputItemId()!=null)
                            icon=entry.candidate().outputItemId().toString();
                        row.addProperty("icon",icon);
                        row.addProperty("edited", local.has(id.toString()));
                        int length=row.toString().length()+1;
                        if(rows.size()>0 && (rows.size()>=96 || chars+length>120_000-mods.toString().length())) {batches.add(rows);rows=new JsonArray();chars=0;}
                        rows.add(row);chars+=length;
                    }
                    if(rows.size()>0||batches.isEmpty())batches.add(rows);
                    int page=Math.min(packet.page,batches.size()-1);
                    response.addProperty("page",page);response.addProperty("pages",batches.size());response.addProperty("total",ids.size());
                    response.add("rows",batches.get(page));
                }
                case "open", "template" -> {
                    ResourceLocation id = KineticResourceIds.parse(packet.id);
                    if (!source.containsKey(id)) throw new IllegalArgumentException("Recipe is not in the original datapack catalog");
                    JsonObject json = new NativeRecipeDocument(source.get(id).getAsJsonObject()).json();
                    response.addProperty("id", id.toString()); response.add("recipe", json);
                    response.addProperty("owner",owners.get(id));
                    var path=new JsonArray();views.get(id).path().forEach(path::add);response.add("view_path",path);
                    response.addProperty("edited", local.has(id.toString()));
                    if(packet.action.equals("template"))response=NativeRecipeCreation.draft(response,"contentstudio:custom/"+UUID.randomUUID().toString().replace("-",""));
                    response.addProperty("added",!RecipeMemoryManager.originalCatalog(manager).sources().containsKey(id));
                }
                case "save", "create", "restore" -> {
                    if (packet.catalogVersion != version || !packet.revision.equals(NativeRecipeStore.revision(local)))
                        throw new IllegalArgumentException("STALE");
                    ResourceLocation id = KineticResourceIds.parse(packet.id);
                    boolean creating=packet.action.equals("create");
                    if (!creating&&!source.containsKey(id)) throw new IllegalArgumentException("Source recipe is no longer available");
                    ResourceLocation target = packet.target.isEmpty() ? id : KineticResourceIds.parse(packet.target);
                    if(creating)NativeRecipeCreation.checkTarget(target.toString(),source.keySet().stream().map(ResourceLocation::toString).collect(java.util.stream.Collectors.toSet()));
                    if ((creating||!target.equals(id)) && (source.containsKey(target)
                            || RecipeMemoryManager.originalCatalog(manager).originalIds().contains(target)
                            || RecipeMemoryManager.containsRuntimeRecipe(manager, target))) throw new IllegalArgumentException("ID_EXISTS");
                    JsonObject draft = null;
                    if (packet.action.equals("save")||creating) {
                        draft = new NativeRecipeDocument(JsonParser.parseString(packet.json).getAsJsonObject()).json();
                        // The installed serializer, registry and condition context are authoritative.
                        var decoded=RecipeMemoryManager.decodeNative(manager, target, draft);
                        //? if >=1.21 {
                        /*NativeRecipeStore.recordType(target,dev.xyat.kineticcore.api.registry.KineticRegistries.recipeTypes().id(decoded.value().getType()));
                        *///?} else {
                        NativeRecipeStore.recordType(target,dev.xyat.kineticcore.api.registry.KineticRegistries.recipeTypes().id(decoded.getType()));
                        //?}
                        //? if >=26.1 {
                        /*dev.xyat.contentstudio.recipe.RecipeView.validateOutput(decoded.value());
                        *///?}
                    } else if (!local.has(id.toString())) throw new IllegalArgumentException("No saved override to restore");
                    NativeRecipeStore.update(target, draft, packet.revision);
                    var server=player.getServer();
                    RELOADING.add(server);
                    try { RecipeMemoryManager.reloadDatapacks(server).whenComplete((unused, error) -> {
                            RELOADING.remove(server);
                            server.execute(() -> {
                                JsonObject result = new JsonObject(); result.addProperty("action", packet.action);
                                result.addProperty("id", target.toString());
                                result.addProperty("message", error == null ? "saved" : "reload_failed");
                                if(error==null)try {
                                    var saved=NativeRecipeStore.local();var reloaded=server.getRecipeManager();
                                    result.addProperty("catalog",RecipeMemoryManager.catalogVersion(reloaded));result.addProperty("revision",NativeRecipeStore.revision(saved));
                                    var original=RecipeMemoryManager.originalCatalog(reloaded).sources();
                                    JsonElement current=saved.has(target.toString())?saved.get(target.toString()):original.get(target);
                                    result.addProperty("deleted",current==null);
                                    if(current!=null) {
                                        var json=current.getAsJsonObject();result.add("recipe",json.deepCopy());
                                        var view=NativeRecipeView.resolve(json,branch->RecipeMemoryManager.nativeConditionsMatch(reloaded,branch));
                                        var path=new JsonArray();view.path().forEach(path::add);result.add("view_path",path);
                                        result.addProperty("edited",saved.has(target.toString()));result.addProperty("added",!original.containsKey(target));
                                        var entry=RecipeMemoryManager.originalCatalog(reloaded).entries().get(target);
                                        result.addProperty("owner",!saved.has(target.toString())&&entry!=null?NativeRecipeTypes.owner(entry.candidate().recipeType()==null?null:entry.candidate().recipeType().toString(),view.recipe()):NativeRecipeStore.owner(target.toString(),view.recipe()));
                                    }
                                }catch(Exception hydrationError) {
                                    result.addProperty("message",Objects.toString(hydrationError.getMessage(),"reload_failed"));reply(player,packet.sequence,false,result);return;
                                }
                                reply(player, packet.sequence, error == null, result);
                            });
                        });
                    } catch(RuntimeException error) {RELOADING.remove(server);throw error;}
                    return;
                }
                default -> throw new IllegalArgumentException("Unknown recipe action");
            }
            reply(player, packet.sequence, true, response);
        } catch (Exception error) {
            JsonObject result = new JsonObject(); result.addProperty("action", packet.action);
            String message = Objects.toString(error.getMessage(), error.getClass().getSimpleName());
            result.addProperty("message", message.substring(0, Math.min(512, message.length())));
            reply(player, packet.sequence, false, result);
        }
    }
    private static String type(JsonObject json) {
        try { return NativeRecipeDocument.serializerId(json); } catch (RuntimeException e) { return "?"; }
    }
    private static String resultIcon(JsonObject json) {
        return NativeRecipeListIcon.result(json);
    }
    private static void reply(ServerPlayer player, long sequence, boolean success, JsonObject body) {
        CHANNEL.sendToPlayer(player, new Response(sequence, success, body.toString()));
    }
}
