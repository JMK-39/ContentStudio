package dev.xyat.contentstudiovalidation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.xyat.contentstudio.recipe.client.RecipeJeiBridge;
import dev.xyat.contentstudio.recipe.client.gui.NativeRecipeEditorPage;
import dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeClient;
import dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeNetwork;
import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.client.gui.KineticGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Opt-in sweep of every non-vanilla recipe type in an installed modpack: opens the first recipe of each type in the
 * real editor, logs whether the JEI layout loaded and how many of its slots are bound to recipe fields, and captures
 * the frame. It only reads; nothing is saved.
 */
public final class NativeRecipeCoverage {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(NativeRecipeCoverage.class);
    private static final Path OUT = Path.of(System.getProperty("contentstudio.coverage.output", "contentstudio-coverage"));
    private static final List<String[]> TYPES = new ArrayList<>();
    private static int step, stationPage, stationPages = 1, typeIndex, waitTicks;
    private static boolean waiting, done;
    private static CompletableFuture<Void> op;
    private static final Consumer<NativeRecipeNetwork.Response> CALLBACK = NativeRecipeCoverage::accept;
    private static JsonObject reply;

    private NativeRecipeCoverage() { }

    public static void install() {
        KineticClientEvents.onTick(KineticClientEvents.TickPhase.END, NativeRecipeCoverage::tick);
    }

    private static void tick() {
        if (done) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.getSingleplayerServer() == null) return;
        try {
            if (op == null) {
                mc.options.pauseOnLostFocus = false;
                op = new CompletableFuture<>();
                mc.getSingleplayerServer().execute(() -> {
                    var players = mc.getSingleplayerServer().getPlayerList();
                    //? if >=26.1 {
                    /*players.op(new net.minecraft.server.players.NameAndId(players.getPlayers().get(0).getGameProfile()));*/
                    //?} else {
                    players.op(players.getPlayers().get(0).getGameProfile());
                    //?}
                    op.complete(null);
                });
                return;
            }
            if (!op.isDone() || waiting) return;
            if (step == 0) {
                if (stationPage >= stationPages) { step = 1; LOG.info("CONTENT_COVERAGE_TYPES count={}", TYPES.size()); return; }
                send("stations", "", "", stationPage);
                return;
            }
            if (step == 1) {
                if (typeIndex >= TYPES.size()) { finish(); return; }
                send("list", "", TYPES.get(typeIndex)[1], 0);
                return;
            }
            if (step == 2) {
                if (--waitTicks > 0) return;
                record();
                KineticGui.closeScreen();
                typeIndex++;
                step = 1;
            }
        } catch (Throwable error) {
            LOG.error("CONTENT_COVERAGE_FAIL type=" + (typeIndex < TYPES.size() ? TYPES.get(typeIndex)[1] : "-"), error);
            KineticGui.closeScreen();
            typeIndex++;
            step = 1;
            waiting = false;
        }
    }

    private static void send(String action, String id, String target, int page) {
        waiting = true;
        NativeRecipeClient.expect(NativeRecipeNetwork.request(action, id, target, "", page, 0, "", ""), CALLBACK);
    }

    private static void accept(NativeRecipeNetwork.Response packet) {
        waiting = false;
        reply = JsonParser.parseString(packet.body()).getAsJsonObject();
        String action = reply.get("action").getAsString();
        if (!packet.success()) {
            LOG.warn("CONTENT_COVERAGE_REQUEST_FAIL action={} type={} body={}", action,
                    typeIndex < TYPES.size() ? TYPES.get(typeIndex)[1] : "-", packet.body());
            if (step == 0) stationPage = stationPages; else typeIndex++;
            return;
        }
        switch (action) {
            case "stations" -> {
                stationPages = reply.get("pages").getAsInt();
                for (var row : reply.getAsJsonArray("rows")) {
                    var object = row.getAsJsonObject();
                    String owner = object.get("owner").getAsString();
                    if (!owner.equals("minecraft")) TYPES.add(new String[]{owner, object.get("type").getAsString(),
                            object.has("count") ? object.get("count").getAsString() : "?"});
                }
                stationPage++;
            }
            case "list" -> {
                var rows = reply.getAsJsonArray("rows");
                if (rows.isEmpty()) { LOG.info("CONTENT_COVERAGE type={} result=no_rows", TYPES.get(typeIndex)[1]); typeIndex++; return; }
                send("open", rows.get(0).getAsJsonObject().get("id").getAsString(), "", 0);
            }
            case "open" -> {
                var page = NativeRecipeEditorPage.create(reply, () -> { });
                if (page == null) {
                    LOG.info("CONTENT_COVERAGE type={} id={} result=no_editor jei={}", TYPES.get(typeIndex)[1],
                            reply.get("id").getAsString(), RecipeJeiBridge.jeiAvailable());
                    typeIndex++;
                    return;
                }
                KineticGui.open(page);
                waitTicks = 30;
                step = 2;
            }
            default -> { }
        }
    }

    private static void record() throws Exception {
        String[] type = TYPES.get(typeIndex);
        var page = KineticGui.currentPage();
        String kind = page == null ? "none" : page.getClass().getSimpleName();
        String previewError = "", category = "";
        int editable = -1, bound = -1, fields = -1;
        if (page instanceof NativeRecipeEditorPage editor) {
            var preview = (RecipeJeiBridge.EditorPreview) field(editor, "jeiPreview");
            previewError = String.valueOf(field(editor, "previewError"));
            bound = ((List<?>) field(editor, "slots")).size();
            if (preview != null) {
                editable = (int) preview.slots().stream().filter(RecipeJeiBridge.EditorSlot::editable).count();
                category = preview.category().getString();
            }
            fields = ((List<?>) field(editor, "fields")).size();
        }
        LOG.info("CONTENT_COVERAGE owner={} type={} recipes={} id={} page={} category=\"{}\" jeiEditable={} bound={} parameters={} error={}",
                type[0], type[1], type[2], reply.get("id").getAsString(), kind, category, editable, bound, fields, previewError);
        Files.createDirectories(OUT);
        String name = String.format("%03d-%s.png", typeIndex, type[1].replace(':', '_').replace('/', '_'));
        var mc = Minecraft.getInstance();
        Path path = OUT.resolve(name);
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}
    }

    private static Object field(Object target, String name) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void finish() {
        done = true;
        LOG.info("CONTENT_COVERAGE_DONE types={}", TYPES.size());
        Minecraft.getInstance().execute(Minecraft.getInstance()::stop);
    }
}
