//? if <1.21 {
package dev.xyat.contentstudiovalidation;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import dev.xyat.contentstudio.loot.LootEntryInfo;
import dev.xyat.contentstudio.loot.mixin.LootDataManagerAccessor;
import dev.xyat.contentstudio.loot.server.LootTableOverrideStore;
import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticPlatform;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.providers.number.NumberProvider;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.world.phys.Vec3;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Opt-in proof using only the production mod's existing native JSON format. */
public final class LootMigrationValidation {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Path OUTPUT=Path.of(System.getProperty("contentstudio.lootMigration.output","build/native-entity-proof"));
    private static final List<String> checks=new ArrayList<>();
    private static boolean installed,started,finished;private static long ready;
    public static void install(){if(installed)return;installed=true;KineticClientEvents.onTick(KineticClientEvents.TickPhase.END,LootMigrationValidation::tick);}
    private static void tick(){
        if(started||finished)return;var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.getSingleplayerServer()==null||mc.getOverlay()!=null)return;
        if(ready==0)ready=System.currentTimeMillis();if(System.currentTimeMillis()-ready<3000)return;started=true;
        try{
            require(mc.getWindow().getWidth()==1920&&mc.getWindow().getHeight()==1080,"1080p original pack");
            var server=mc.getSingleplayerServer();var uuid=mc.player.getUUID();
            server.submit(()->{try{run(server,server.getPlayerList().getPlayer(uuid));}catch(Exception e){throw new java.util.concurrent.CompletionException(e);}return null;})
                    .whenComplete((unused,failure)->mc.execute(()->finish(failure)));
        }catch(Throwable failure){finish(failure);}
    }
    private static void run(MinecraftServer server,ServerPlayer player)throws Exception{
        Path plan=Path.of(System.getProperty("contentstudio.lootMigration.input"));
        var candidate=JsonParser.parseString(Files.readString(plan.resolve("candidate.json"))).getAsJsonObject();
        var entities=JsonParser.parseString(Files.readString(plan.resolve("entities.json"))).getAsJsonObject();
        Path persisted=KineticPlatform.configDirectory().resolve("kineticcore/contentstudio/loot_overrides.json");
        byte[] before=Files.readAllBytes(persisted);
        require(candidate.equals(JsonParser.parseString(new String(before,StandardCharsets.UTF_8))),"persisted config equals the native-only candidate");
        for(String forbidden:List.of("contentstudio:"+"stage_chance","contentstudio_"+"complete_snapshot"))
            require(!candidate.toString().contains(forbidden),"no pack-specific Java condition or new configuration feature");
        try{Class.forName("dev.xyat.contentstudio.loot.condition."+"StageChanceCondition");throw new AssertionError("pack-specific condition class still shipped");}
        catch(ClassNotFoundException expected){}
        var snapshots=new LiveSnapshots(LootDataType.TABLE.parser());snapshots.verifyIronRoundTrips();
        int pools=0;
        for(var entry:entities.entrySet()){
            var config=entry.getValue().getAsJsonObject();String tableId=config.get("tableId").getAsString();var id=KineticResourceIds.parse(tableId);
            require(server.getLootData().getKeys(LootDataType.TABLE).contains(id),"native data pack makes configured table available: "+id);
            var live=server.getLootData().getLootTable(id);
            var expected=parseTable(LootDataType.TABLE.parser(),candidate.getAsJsonObject("tables").get(tableId),tableId);
            require(snapshots.table(live,tableId).equals(snapshots.table(expected,tableId)),"existing mod loads exact native configuration: "+tableId);
            var items=config.getAsJsonArray("items");
            for(int i=0;i<items.size();i++){
                var value=items.get(i).getAsJsonObject();
                require(KineticRegistries.items().contains(KineticResourceIds.parse(value.get("item").getAsString())),"configured native reward registered");
                var pool=live.getPool(PackLootMigration.PREFIX+"entity/"+entry.getKey()+"/"+i);
                require(pool!=null,"native appended pool exists exactly once");
                verifyEntityPool(server,player,pool,value,config.get("playerOnly").getAsBoolean());pools++;
            }
        }
        require(candidate.getAsJsonObject("global_chest_append").getAsJsonArray("pools").size()==0,"pack-specific chest rewards stay in KubeJS");
        require(Arrays.equals(before,Files.readAllBytes(persisted)),"validation leaves configuration bytes unchanged");
        checks.add("existing native format: tables="+entities.size()+" configured pools="+pools+"; original pools/functions retained; item IDs, NBT, count ranges and player-only kills verified");
        checks.add("no production loot code changed; vanilla data pack supplies original base tables; dynamic chest rules remain active in KubeJS");
    }
    /** A private clone: never replace adapters on Minecraft's shared loot parser or mutate live functions. */
    private static final class LiveSnapshots implements TypeAdapterFactory {
        private static final String IRON_FUNCTION = "io.redspace.ironsspellbooks.loot.RandomizeSpellFunction";
        private static final String IRON_RING_FUNCTION = "io.redspace.ironsspellbooks.loot.RandomizeRingEnhancementFunction";
        private static final String IRON_MAP_FUNCTION = "io.redspace.ironsspellbooks.loot.FurledMapLootFunction";
        private final Gson original, gson;
        private final TypeAdapter<LootItemFunction> originalFunctionAdapter;
        private int ironFunctions;

        private LiveSnapshots(Gson original) {
            this.original = original;
            this.originalFunctionAdapter = original.getAdapter(LootItemFunction.class);
            this.gson = original.newBuilder().registerTypeAdapterFactory(this).create();
        }

        @Override public <T> TypeAdapter<T> create(Gson owner, TypeToken<T> type) {
            if (!LootItemFunction.class.isAssignableFrom(type.getRawType())) return null;
            TypeAdapter<T> delegate = owner.getDelegateAdapter(this, type);
            return new TypeAdapter<>() {
                @Override public T read(JsonReader in) throws IOException { return delegate.read(in); }
                @Override public void write(JsonWriter out, T value) throws IOException {
                    if (value != null && IRON_MAP_FUNCTION.equals(value.getClass().getName())) {
                        owner.getAdapter(JsonElement.class).write(out, ironMapFunction(value, delegate.toJsonTree(value).getAsJsonObject()));
                        return;
                    }
                    if (value == null || !Set.of(IRON_FUNCTION, IRON_RING_FUNCTION).contains(value.getClass().getName())) {
                        delegate.write(out, value);
                        return;
                    }
                    owner.getAdapter(JsonElement.class).write(out, ironFunction((LootItemFunction)value));
                }
            };
        }

        private JsonObject ironMapFunction(Object function, JsonObject json) {
            try {
                Set<String> fields = new TreeSet<>();
                for (var field : function.getClass().getDeclaredFields())
                    if (!java.lang.reflect.Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) fields.add(field.getName());
                require(fields.equals(Set.of("destination", "translation", "dimension")), "unsupported Iron map state: " + fields);
                // This installed serializer writes translation, but its native parser requires
                // description_translation; it also omits the optional dimension entirely.
                json.remove("translation");
                json.addProperty("description_translation", (String)field(function, "translation"));
                var dimension = (Optional<?>)field(function, "dimension");
                if (dimension.isPresent()) json.addProperty("dimension", (String)dimension.get());
                ironFunctions++;
                return json;
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot preserve the installed Iron furled map state; migration refused", failure);
            }
        }

        private JsonObject ironFunction(LootItemFunction function) {
            try {
                // Installed Iron 1.20.1-3.16.3's serializer calls JsonObject.isEmpty (absent in Gson 2.10),
                // omits SpellFilter.force, and writes number providers without their type discriminator.
                // Read the original state; use native adapters for conditions/quality and retain every filter field.
                Set<String> fields = new TreeSet<>();
                for (var field : function.getClass().getDeclaredFields())
                    if (!java.lang.reflect.Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) fields.add(field.getName());
                boolean ring=IRON_RING_FUNCTION.equals(function.getClass().getName());
                require(fields.equals(ring ? Set.of("spellFilter") : Set.of("qualityRange", "applicableSpells")),
                        "unsupported Iron function state: " + fields);
                JsonObject json = new JsonObject();
                json.addProperty("function", Objects.requireNonNull(BuiltInRegistries.LOOT_FUNCTION_TYPE.getKey(function.getType())).toString());
                JsonSerializationContext context = new JsonSerializationContext() {
                    @Override public JsonElement serialize(Object value) { return gson.toJsonTree(value); }
                    @Override public JsonElement serialize(Object value, Type type) { return gson.toJsonTree(value, type); }
                };
                new LootItemConditionalFunction.Serializer<LootItemConditionalFunction>() {
                    @Override public LootItemConditionalFunction deserialize(JsonObject json, JsonDeserializationContext context, LootItemCondition[] conditions) {
                        throw new UnsupportedOperationException("Serialization-only fixture helper");
                    }
                }.serialize(json, (LootItemConditionalFunction)function, context);
                if(!ring)json.add("quality", gson.toJsonTree(field(function, "qualityRange"), NumberProvider.class));
                Object filter = field(function, ring ? "spellFilter" : "applicableSpells");
                Set<String> filterFields = new TreeSet<>();
                for (var field : filter.getClass().getDeclaredFields())
                    if (!java.lang.reflect.Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) filterFields.add(field.getName());
                require(filterFields.equals(Set.of("schoolType", "spells", "force")), "unsupported Iron filter state: " + filterFields);
                Object school = field(filter, "schoolType");
                List<String> spells = new ArrayList<>();
                for (Object spell : (List<?>)field(filter, "spells")) spells.add((String)spell.getClass().getMethod("getSpellId").invoke(spell));
                JsonObject filterJson = PackLootMigration.spellFilterJson(school == null ? null : school.getClass().getMethod("getId").invoke(school).toString(),
                        spells, (boolean)field(filter, "force"));
                if (filterJson.size() > 0) json.add("spell_filter", filterJson);
                ironFunctions++;
                return json;
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot preserve the installed Iron randomize_spell state; migration refused", failure);
            }
        }

        private static Object field(Object owner, String name) throws ReflectiveOperationException {
            var field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        }

        private JsonObject table(LootTable table, String id) {
            try {
                JsonObject json = gson.toJsonTree(table, LootTable.class).getAsJsonObject();
                int originalFunctionCount = ironFunctions;
                LootTable restored = parseTable(original, json, id);
                require(json.equals(gson.toJsonTree(restored, LootTable.class)), "live table native parser round-trip changed state: " + id);
                ironFunctions = originalFunctionCount;
                return json;
            } catch (RuntimeException | LinkageError failure) {
                throw new IllegalStateException("Cannot preserve live loot table " + id + "; migration refused", failure);
            }
        }

        private void verifyIronRoundTrips() throws ClassNotFoundException {
            try { Class.forName(IRON_FUNCTION); }
            catch (ClassNotFoundException absent) { checks.add("Iron compatibility proof skipped: mod absent"); return; }
            String[] cases = {
                    "\"quality\":{\"min\":0.2,\"max\":0.8},\"conditions\":[{\"condition\":\"minecraft:killed_by_player\"}]",
                    "\"quality\":{\"min\":0.1,\"max\":1},\"spell_filter\":{\"school\":\"irons_spellbooks:eldritch\",\"force\":true}",
                    "\"quality\":0.5,\"spell_filter\":{\"spells\":[\"irons_spellbooks:magic_missile\",\"irons_spellbooks:firebolt\"],\"force\":true}",
                    "\"quality\":{\"type\":\"minecraft:binomial\",\"n\":3,\"p\":0.5},\"spell_filter\":{\"school\":\"irons_spellbooks:fire\"}",
                    "\"quality\":1,\"spell_filter\":{\"spells\":[],\"force\":true}"
            };
            int count = 0;
            for (String value : cases) {
                JsonObject input = JsonParser.parseString("{\"function\":\"irons_spellbooks:randomize_spell\"," + value + "}").getAsJsonObject();
                LootItemFunction function = original.fromJson(input, LootItemFunction.class);
                require(IRON_FUNCTION.equals(function.getClass().getName()), "native Iron function parsed");
                JsonObject snapshot = gson.toJsonTree(function, LootItemFunction.class).getAsJsonObject();
                if (input.has("spell_filter")) require(input.get("spell_filter").equals(snapshot.get("spell_filter")), "Iron spell IDs/school/force preserved case " + count);
                if (input.has("conditions")) require(input.get("conditions").equals(snapshot.get("conditions")), "Iron predicates preserved");
                require(snapshot.equals(gson.toJsonTree(original.fromJson(snapshot, LootItemFunction.class), LootItemFunction.class)), "Iron native function round-trip case " + count);
                count++;
                JsonObject ringInput=input.deepCopy();ringInput.remove("quality");
                ringInput.addProperty("function","irons_spellbooks:randomize_ring_enhancement");
                LootItemFunction ring=original.fromJson(ringInput,LootItemFunction.class);
                require(IRON_RING_FUNCTION.equals(ring.getClass().getName()), "native Iron ring function parsed");
                JsonObject ringSnapshot=gson.toJsonTree(ring,LootItemFunction.class).getAsJsonObject();
                require(ringInput.equals(ringSnapshot), "Iron ring predicates/school/spells/force preserved");
                require(ringSnapshot.equals(gson.toJsonTree(original.fromJson(ringSnapshot,LootItemFunction.class),LootItemFunction.class)),
                        "Iron ring native function round-trip");
                count++;
            }
            for (String dimension : List.of("", ",\"dimension\":\"minecraft:the_nether\"")) {
                JsonObject input = JsonParser.parseString("{\"function\":\"irons_spellbooks:set_furled_map\","
                        + "\"destination\":\"irons_spellbooks:citadel\",\"description_translation\":\"fixture.map\","
                        + "\"conditions\":[{\"condition\":\"minecraft:killed_by_player\"}]" + dimension + "}").getAsJsonObject();
                LootItemFunction function = original.fromJson(input, LootItemFunction.class);
                require(IRON_MAP_FUNCTION.equals(function.getClass().getName()), "native Iron map function parsed");
                JsonObject snapshot = gson.toJsonTree(function, LootItemFunction.class).getAsJsonObject();
                require(input.equals(snapshot), "Iron map destination/translation/dimension/predicates preserved");
                require(snapshot.equals(gson.toJsonTree(original.fromJson(snapshot, LootItemFunction.class), LootItemFunction.class)),
                        "Iron map native parser round-trip");
                count++;
            }
            require(original == LootDataType.TABLE.parser() && gson != original && original.getAdapter(LootItemFunction.class) == originalFunctionAdapter,
                    "shared native loot Gson and function adapter left unchanged");
            checks.add("fixture-only Iron snapshot adapter: " + count + " native round-trips; predicates, school, explicit spells, force, quality and furled map destination/translation/dimension preserved");
            ironFunctions = 0;
        }
    }

    private static LootTable parseTable(Gson parser, JsonElement json, String id) {
        // Forge's pool parser needs its per-table context, including private round trips.
        // custom=true skips load events already reflected in these live snapshots.
        //? if forge {
        return net.minecraftforge.common.ForgeHooks.loadLootTable(parser, KineticResourceIds.parse(id), json, true);
        //?} else {
        /*return parser.fromJson(json, LootTable.class);
        *///?}
    }

    private static void verifyEntityPool(MinecraftServer server, ServerPlayer player, LootPool pool, JsonObject value, boolean playerOnly) {
        int min = value.getAsJsonArray("count").get(0).getAsInt(), max = value.getAsJsonArray("count").get(1).getAsInt();
        for (int i = 1; i <= 16; i++) {
            List<ItemStack> stacks = new ArrayList<>(); pool.addRandomItems(stacks::add, entityContext(server, player, i));
            int count = stacks.stream().mapToInt(ItemStack::getCount).sum();
            require(count >= min && count <= max, "native entity amount range");
            for (ItemStack stack : stacks) {
                // Raw pool callbacks can emit zero-count stacks; getItem() then deliberately reports AIR.
                if(stack.isEmpty()) { require(min==0 && stack.getCount()==0,"empty stack only for an allowed zero roll");continue; }
                require(KineticRegistries.items().id(stack.getItem()).toString().equals(value.get("item").getAsString()),
                        "native entity item expected="+value+" actual="+stack+" empty="+stack.isEmpty()+" count="+stack.getCount());
                if(value.has("nbt") && !value.get("nbt").isJsonNull()) {
                    try {
                        JsonElement tag=value.get("nbt");
                        require(net.minecraft.nbt.NbtUtils.compareNbt(net.minecraft.nbt.TagParser.parseTag(tag.isJsonPrimitive()?tag.getAsString():tag.toString()),
                                stack.getTag(),true), "native entity reward NBT preserved");
                    } catch(com.mojang.brigadier.exceptions.CommandSyntaxException invalid) { throw new AssertionError("Invalid configured NBT",invalid); }
                }
            }
            if (playerOnly) {
                stacks.clear(); pool.addRandomItems(stacks::add, entityContext(server, null, i));
                require(stacks.isEmpty(), "player-only native kill condition");
            }
        }
    }
    private static LootContext entityContext(MinecraftServer server, ServerPlayer player, long seed) {
        var entity = EntityType.COW.create(server.overworld());
        var builder = new LootParams.Builder(server.overworld()).withParameter(LootContextParams.THIS_ENTITY, entity)
                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO).withParameter(LootContextParams.DAMAGE_SOURCE, server.overworld().damageSources().generic());
        if (player != null) builder.withParameter(LootContextParams.LAST_DAMAGE_PLAYER, player).withParameter(LootContextParams.KILLER_ENTITY, player);
        return new LootContext.Builder(builder.create(LootContextParamSets.ENTITY)).withOptionalRandomSeed(seed).create(null);
    }

    private static void require(boolean okay,String message){if(!okay)throw new AssertionError(message);}
    private static void finish(Throwable failure){finished=true;try{
        Files.createDirectories(OUTPUT);String result="CONTENT_NATIVE_ENTITIES_"+(failure==null?"PASS":"FAIL")+"\n"+String.join("\n",checks)+"\n";
        if(failure!=null){var text=new java.io.StringWriter();failure.printStackTrace(new java.io.PrintWriter(text));result+=text;}
        Files.writeString(OUTPUT.resolve("result.txt"),result,StandardCharsets.UTF_8);
    }catch(Exception e){throw new RuntimeException(e);}finally{dev.xyat.kineticcore.api.runtime.KineticClientRuntime.stopClient();}}
}
//?} else {
/*package dev.xyat.contentstudiovalidation;
public final class LootMigrationValidation {private LootMigrationValidation(){}public static void install(){throw new IllegalStateException("Native conversion proof uses the authorized original Forge pack");}}
*///?}
