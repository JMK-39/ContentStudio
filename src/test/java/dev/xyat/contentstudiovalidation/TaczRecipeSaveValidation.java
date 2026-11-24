//? if <1.21 {
package dev.xyat.contentstudiovalidation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeClient;
import dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeNetwork;
import dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeStore;
import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;

import java.util.List;
import java.util.function.Consumer;

/**
 * Pack fixture for saving a TACZ gunsmith table recipe in the native recipe editor, through the editor's own packets.
 * First one datapack reload is timed, as every save used to run one. Then a material count is changed and saved: the
 * server must have the changed recipe at once with no reload, the players get it when the editor is left, and
 * restoring puts the original back the same way. Logs the times and the client's longest pause for each. The recipe
 * files are put back byte for byte at the end.
 */
public final class TaczRecipeSaveValidation {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(TaczRecipeSaveValidation.class);
    private static final String TYPE = "tacz:gun_smith_table_crafting";
    private static final Consumer<NativeRecipeNetwork.Response> CALLBACK = TaczRecipeSaveValidation::accept;
    private static boolean started, finished;
    private static int stage;
    private static long deadline, mark, settleFrom;
    private static long fullReloadMs, fullReloadPause, saveMs, syncMs, syncPause, restoreMs;
    private static volatile long lastTick, pause;
    private static String id;
    private static int oldCount;
    private static String oldGroup;
    private static JsonObject savedResult;
    private static final String TEST_GROUP = "tacz:compat_validation";
    private static long catalogBefore;
    private static JsonObject opened;
    // What the tick waits for: a condition and what follows it.
    private static java.util.function.BooleanSupplier waiting;
    private static Runnable then;

    private TaczRecipeSaveValidation() {
    }

    public static void install() {
        KineticClientEvents.onTick(KineticClientEvents.TickPhase.END, TaczRecipeSaveValidation::tick);
    }

    private static void tick() {
        long now = System.nanoTime();
        if (lastTick != 0L) pause = Math.max(pause, (now - lastTick) / 1_000_000L);
        lastTick = now;
        if (finished) return;
        var mc = Minecraft.getInstance();
        try {
            if (!started) {
                if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null || mc.screen != null) return;
                started = true;
                deadline = System.currentTimeMillis() + 600_000L;
                // What every save used to do: one reload of every datapack.
                var server = mc.getSingleplayerServer();
                require(server.getPlayerList().getPlayers().get(0).hasPermissions(2), "the test world lets its player edit recipes");
                RecipeFiles.snapshot();
                pause = 0L;
                mark = System.nanoTime();
                boolean[] done = {false};
                server.execute(() -> dev.xyat.contentstudio.recipe.RecipeMemoryManager.reloadDatapacks(server).whenComplete((unused, error) -> {
                    fullReloadMs = (System.nanoTime() - mark) / 1_000_000L;
                    done[0] = true;
                }));
                await(() -> done[0] && settled(), () -> {
                    fullReloadPause = pause;
                    LOG.info("CONTENT_TACZ_SAVE_STEP one datapack reload took {} ms on the server; the client paused {} ms", fullReloadMs, fullReloadPause);
                    onServer(TaczRecipeSaveValidation::pickRecipe, () -> send("open", "", null));
                });
                return;
            }
            if (System.currentTimeMillis() > deadline) throw new AssertionError("timed out in stage " + stage);
            if (waiting != null && waiting.getAsBoolean()) {
                Runnable next = then;
                waiting = null;
                then = null;
                next.run();
            }
        } catch (Throwable error) {
            fail(error);
        }
    }

    /** A gunsmith table recipe from the datapacks that nobody has edited, with a material count to change. */
    private static void pickRecipe() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        RecipeManager manager = server.getRecipeManager();
        var sources = dev.xyat.contentstudio.recipe.RecipeMemoryManager.originalCatalog(manager).sources();
        JsonObject local;
        try {
            local = NativeRecipeStore.local();
        } catch (java.io.IOException error) {
            throw new AssertionError(error);
        }
        for (Recipe<?> recipe : manager.getRecipes()) {
            ResourceLocation key = recipe.getId();
            if (!TYPE.equals(String.valueOf(net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType())))) continue;
            if (!sources.containsKey(key) || local.has(key.toString())) continue;
            if (countPath(sources.get(key)) == null) continue;
            // The live recipe and its file agree on the count that is about to change.
            if (sources.get(key).getAsJsonObject().getAsJsonArray("materials").get(0).getAsJsonObject().get("count").getAsInt() != firstCount(recipe)) continue;
            id = key.toString();
            oldCount = firstCount(recipe);
            oldGroup = nativeGroup(recipe);
            catalogBefore = dev.xyat.contentstudio.recipe.RecipeMemoryManager.catalogVersion(manager);
            LOG.info("CONTENT_TACZ_SAVE_STEP recipe={} first material count={}", id, oldCount);
            return;
        }
        throw new AssertionError("no unedited TACZ gunsmith table recipe with a material count");
    }

    private static void send(String action, String json, JsonObject header) {
        deadline = System.currentTimeMillis() + 300_000L;
        NativeRecipeClient.expect(NativeRecipeNetwork.request(action, id, id, "", 0,
                header == null ? 0 : header.get("catalog").getAsLong(), header == null ? "" : header.get("revision").getAsString(), json), CALLBACK);
    }

    private static void accept(NativeRecipeNetwork.Response packet) {
        try {
            JsonObject body = JsonParser.parseString(packet.body()).getAsJsonObject();
            var mc = Minecraft.getInstance();
            switch (stage++) {
                case 0 -> {
                    require(packet.success(), "the recipe opens: " + body);
                    opened = body;
                    JsonObject draft = body.getAsJsonObject("recipe").deepCopy();
                    require(countPath(draft) != null, "the opened recipe has the first material's count");
                    draft.getAsJsonArray("materials").get(0).getAsJsonObject().add("count", new JsonPrimitive(oldCount + 1));
                    draft.getAsJsonObject("result").addProperty("group", TEST_GROUP);
                    savedResult = draft.getAsJsonObject("result").deepCopy();
                    pause = 0L;
                    mark = System.nanoTime();
                    send("save", draft.toString(), body);
                }
                case 1 -> {
                    saveMs = (System.nanoTime() - mark) / 1_000_000L;
                    require(packet.success(), "the changed recipe saves: " + body);
                    require(!body.get("pending").getAsBoolean(), "a gunsmith table recipe needs no datapack reload");
                    require(clientCount() == oldCount, "players are not sent the recipes on every save");
                    onServer(() -> {
                        var manager = mc.getSingleplayerServer().getRecipeManager();
                        require(firstCount(manager.byKey(KineticResourceIds.parse(id)).orElseThrow()) == oldCount + 1, "the server has the changed recipe at once");
                        require(TEST_GROUP.equals(nativeGroup(manager.byKey(KineticResourceIds.parse(id)).orElseThrow())), "the server retains the TaCZ 1.2 result group");
                        require(dev.xyat.contentstudio.recipe.RecipeMemoryManager.catalogVersion(manager) == catalogBefore, "no datapack reload ran for the save");
                        try {
                            require(NativeRecipeStore.local().has(id), "the change is stored");
                            require(savedResult.equals(NativeRecipeStore.local().getAsJsonObject(id).get("result")), "stored output metadata is unchanged");
                        } catch (java.io.IOException error) {
                            throw new AssertionError(error);
                        }
                    }, () -> {
                        // Leaving the recipe editor sends the players the recipes.
                        pause = 0L;
                        mark = System.nanoTime();
                        dev.xyat.contentstudio.recipe.network.RecipeNetwork.sendApplyPendingRecipesRequest();
                        boolean[] arrived = {false};
                        await(() -> {
                            if (!arrived[0] && clientCount() == oldCount + 1) {
                                arrived[0] = true;
                                syncMs = (System.nanoTime() - mark) / 1_000_000L;
                            }
                            return arrived[0] && settled();
                        }, () -> {
                            syncPause = pause;
                            LOG.info("CONTENT_TACZ_SAVE_STEP saved in {} ms; leaving the editor reached the client in {} ms, which paused {} ms", saveMs, syncMs, syncPause);
                            send("open", "", null);
                        });
                    });
                }
                case 2 -> {
                    require(packet.success() && body.get("edited").getAsBoolean(), "the saved recipe opens as edited");
                    require(savedResult.equals(body.getAsJsonObject("recipe").get("result")), "reopening retains all output metadata");
                    require(TEST_GROUP.equals(nativeGroup(mc.level.getRecipeManager().byKey(KineticResourceIds.parse(id)).orElseThrow())), "native client sync retains the TaCZ 1.2 result group");
                    mark = System.nanoTime();
                    send("restore", "", body);
                }
                case 3 -> {
                    restoreMs = (System.nanoTime() - mark) / 1_000_000L;
                    require(packet.success() && !body.get("pending").getAsBoolean(), "restoring needs no datapack reload either: " + body);
                    onServer(() -> {
                        var manager = mc.getSingleplayerServer().getRecipeManager();
                        require(firstCount(manager.byKey(KineticResourceIds.parse(id)).orElseThrow()) == oldCount, "the server has the original recipe back");
                        require(oldGroup.equals(nativeGroup(manager.byKey(KineticResourceIds.parse(id)).orElseThrow())), "restoring retains the original output group");
                        require(dev.xyat.contentstudio.recipe.RecipeMemoryManager.catalogVersion(manager) == catalogBefore, "no datapack reload ran for the restore");
                        try {
                            require(!NativeRecipeStore.local().has(id), "the override is gone from the store");
                        } catch (java.io.IOException error) {
                            throw new AssertionError(error);
                        }
                    }, () -> {
                        dev.xyat.contentstudio.recipe.network.RecipeNetwork.sendApplyPendingRecipesRequest();
                        await(() -> clientCount() == oldCount && settled(), () -> {
                            require(oldGroup.equals(nativeGroup(mc.level.getRecipeManager().byKey(KineticResourceIds.parse(id)).orElseThrow())), "the client has the original output group back");
                            try {
                                RecipeFiles.restore();
                            } catch (java.io.IOException error) {
                                throw new AssertionError(error);
                            }
                            finished = true;
                            LOG.info("CONTENT_TACZ_SAVE_PASS recipe={} fullReloadMs={} fullReloadClientPauseMs={} saveMs={} syncMs={} syncClientPauseMs={} restoreMs={}",
                                    id, fullReloadMs, fullReloadPause, saveMs, syncMs, syncPause, restoreMs);
                            dev.xyat.kineticcore.api.runtime.KineticClientRuntime.stopClient();
                        });
                    });
                }
                default -> throw new AssertionError("unexpected stage " + stage);
            }
        } catch (Throwable error) {
            fail(error);
        }
    }

    /** The path of the first material's count in a gunsmith table recipe, or {@code null} if it has none. */
    private static List<String> countPath(JsonElement recipe) {
        if (!recipe.isJsonObject() || !recipe.getAsJsonObject().has("materials") || !recipe.getAsJsonObject().get("materials").isJsonArray()) return null;
        var materials = recipe.getAsJsonObject().getAsJsonArray("materials");
        if (materials.isEmpty() || !materials.get(0).isJsonObject()) return null;
        JsonObject first = materials.get(0).getAsJsonObject();
        return first.has("count") && first.get("count").isJsonPrimitive() && first.get("count").getAsJsonPrimitive().isNumber()
                ? List.of("materials", "0", "count") : null;
    }

    /** The count of a gunsmith table recipe's first material; TACZ is not on the compile path, so by name. */
    private static int firstCount(Recipe<?> recipe) {
        try {
            List<?> inputs = (List<?>) recipe.getClass().getMethod("getInputs").invoke(recipe);
            Object first = inputs.get(0);
            return (int) first.getClass().getMethod("getCount").invoke(first);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("not a gunsmith table recipe: " + recipe, error);
        }
    }

    /** The first material's count of the recipe as the client knows it. */
    private static int clientCount() {
        var level = Minecraft.getInstance().level;
        if (level == null) return -1;
        return level.getRecipeManager().byKey(KineticResourceIds.parse(id)).map(TaczRecipeSaveValidation::firstCount).orElse(-1);
    }

    private static void await(java.util.function.BooleanSupplier condition, Runnable next) {
        deadline = System.currentTimeMillis() + 300_000L;
        waiting = condition;
        then = next;
    }

    /** Whether five seconds have passed since first asked and the client is ticking without a pause again. */
    private static boolean settled() {
        long now = System.nanoTime();
        if (settleFrom == 0L) {
            settleFrom = now;
            return false;
        }
        if (now - settleFrom < 5_000_000_000L) return false;
        settleFrom = 0L;
        return true;
    }

    private static void onServer(Runnable check, Runnable next) {
        var mc = Minecraft.getInstance();
        mc.getSingleplayerServer().execute(() -> {
            try {
                check.run();
                mc.execute(next);
            } catch (Throwable error) {
                mc.execute(() -> fail(error));
            }
        });
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        LOG.info("CONTENT_TACZ_SAVE_CHECK {}", message);
    }

    private static String nativeGroup(Recipe<?> recipe) {
        try {
            return String.valueOf(recipe.getClass().getMethod("getTab").invoke(recipe));
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Cannot inspect native TaCZ group", error);
        }
    }

    private static void fail(Throwable error) {
        if (finished) return;
        finished = true;
        try {
            RecipeFiles.restore();
        } catch (Throwable restore) {
            error.addSuppressed(restore);
        }
        LOG.error("CONTENT_TACZ_SAVE_FAIL stage=" + stage, error);
        dev.xyat.kineticcore.api.runtime.KineticClientRuntime.stopClient();
    }
}
//?}
