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
    private static final PacketChannel CHANNEL = PacketChannel.create(KineticResourceIds.of("contentstudio", "native_recipes"), "1", NetworkVersionPolicy.EXACT);
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
        public Response(NetworkBuffer b) { this(b.readLong(), b.readBoolean(), b.readUtf(16_000)); }
        public static void encode(NetworkBuffer b, Response p) { b.writeLong(p.sequence); b.writeBoolean(p.success); b.writeUtf(p.body,16_000); }
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
            JsonObject response = new JsonObject();
            response.addProperty("action", packet.action);
            response.addProperty("catalog", version);
            response.addProperty("revision", NativeRecipeStore.revision(local));
            switch (packet.action) {
                case "stations" -> {
                    if(packet.page<0 || packet.page>100_000)throw new IllegalArgumentException("Invalid page");
                    var counts=new TreeMap<String,Integer>();
                    source.values().stream().filter(JsonElement::isJsonObject).forEach(value->counts.merge(type(value.getAsJsonObject()),1,Integer::sum));
                    var types=List.copyOf(counts.keySet());
                    int pages=Math.max(1,(types.size()+23)/24), page=Math.min(packet.page,pages-1);
                    response.addProperty("page",page);response.addProperty("pages",pages);
                    var rows=new JsonArray();
                    for(int i=page*24;i<Math.min(types.size(),(page+1)*24);i++) {
                        String type=types.get(i);var row=new JsonObject();row.addProperty("type",type);
                        String icon=NativeRecipeStations.icon(type);
                        if(icon.equals("minecraft:knowledge_book")) {
                            for(var entry:RecipeMemoryManager.originalCatalog(manager).entries().entrySet()) {
                                var original=source.get(entry.getKey());
                                if(original==null || !original.isJsonObject() || !type(original.getAsJsonObject()).equals(type) || entry.getValue().recipe().isEmpty())continue;
                                try {
                                    //? if >=26.1 {
                                    /*var stack=dev.xyat.contentstudio.recipe.RecipeView.station(entry.getValue().recipe().get().value(),player.getServer().registryAccess());
                                    *///?} else if >=1.21 {
                                    /*var stack=entry.getValue().recipe().get().value().getToastSymbol();
                                    *///?} else {
                                    var stack=entry.getValue().recipe().get().getToastSymbol();
                                    //?}
                                    if(!stack.isEmpty() && (!stack.is(net.minecraft.world.item.Items.CRAFTING_TABLE) || type.startsWith("minecraft:")))
                                        icon=dev.xyat.kineticcore.api.registry.KineticRegistries.items().id(stack.getItem()).toString();
                                }catch(RuntimeException unavailable){ }
                                break;
                            }
                        }
                        row.addProperty("icon",icon);row.addProperty("count",counts.get(type));rows.add(row);
                    }
                    response.add("rows",rows);
                }
                case "list" -> {
                    if (packet.page < 0 || packet.page > 100_000) throw new IllegalArgumentException("Invalid page");
                    String query = packet.query.toLowerCase(Locale.ROOT);
                    var ids = source.entrySet().stream().filter(e -> e.getValue().isJsonObject())
                            .filter(e -> packet.target.isEmpty() || type(e.getValue().getAsJsonObject()).equals(packet.target))
                            .filter(e -> (e.getKey() + " " + type(e.getValue().getAsJsonObject())).toLowerCase(Locale.ROOT).contains(query))
                            .map(Map.Entry::getKey).sorted(Comparator.comparing(ResourceLocation::toString)).toList();
                    int pages = Math.max(1, (ids.size() + 15) / 16);
                    int page = Math.min(packet.page, pages - 1);
                    response.addProperty("page", page); response.addProperty("pages", pages); response.addProperty("total", ids.size());
                    JsonArray rows = new JsonArray();
                    for (int i = page * 16; i < Math.min(ids.size(), (page + 1) * 16); i++) {
                        var id = ids.get(i); JsonObject row = new JsonObject();
                        row.addProperty("id", id.toString()); row.addProperty("type", type(source.get(id).getAsJsonObject()));
                        row.addProperty("edited", local.has(id.toString())); rows.add(row);
                    }
                    response.add("rows", rows);
                }
                case "open" -> {
                    ResourceLocation id = KineticResourceIds.parse(packet.id);
                    if (!source.containsKey(id)) throw new IllegalArgumentException("Recipe is not in the original datapack catalog");
                    JsonObject json = new NativeRecipeDocument(source.get(id).getAsJsonObject()).json();
                    response.addProperty("id", id.toString()); response.add("recipe", json);
                    response.addProperty("edited", local.has(id.toString()));
                }
                case "save", "restore" -> {
                    if (packet.catalogVersion != version || !packet.revision.equals(NativeRecipeStore.revision(local)))
                        throw new IllegalArgumentException("STALE");
                    ResourceLocation id = KineticResourceIds.parse(packet.id);
                    if (!source.containsKey(id)) throw new IllegalArgumentException("Source recipe is no longer available");
                    ResourceLocation target = packet.target.isEmpty() ? id : KineticResourceIds.parse(packet.target);
                    if (!target.equals(id) && (source.containsKey(target)
                            || RecipeMemoryManager.originalCatalog(manager).originalIds().contains(target)
                            || RecipeMemoryManager.containsRuntimeRecipe(manager, target))) throw new IllegalArgumentException("ID_EXISTS");
                    JsonObject draft = null;
                    if (packet.action.equals("save")) {
                        draft = new NativeRecipeDocument(JsonParser.parseString(packet.json).getAsJsonObject()).json();
                        // The installed serializer, registry and condition context are authoritative.
                        var decoded=RecipeMemoryManager.decodeNative(manager, target, draft);
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
    private static void reply(ServerPlayer player, long sequence, boolean success, JsonObject body) {
        CHANNEL.sendToPlayer(player, new Response(sequence, success, body.toString()));
    }
}
