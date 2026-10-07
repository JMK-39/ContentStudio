package dev.xyat.contentstudiovalidation;

import dev.xyat.contentstudio.villager.client.VillagerClientActions;
import dev.xyat.contentstudio.villager.client.gui.VillagerTradeEditorPage;
import dev.xyat.contentstudio.villager.config.VillagerConfig;
import dev.xyat.contentstudio.villager.network.VillagerNetwork;
import dev.xyat.contentstudio.villager.trade.VillagerTradeRegistry;
import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.client.gui.KineticGui;
import dev.xyat.kineticcore.api.network.NetworkBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** Opt-in installed-profile checks. All saves are restored and all entities stay out of the world. */
public final class VillagerParityValidation {
    private static final Logger LOG = LoggerFactory.getLogger(VillagerParityValidation.class);
    private static final String BAD = "minecraft:farmer|1|minecraft:emerald|0|" + data() + "|minecraft:air|0|" + data()
            + "|minecraft:bread|3|" + data() + "|16|2|0.05|0|0|true|0|true|1";
    private static final Path OUTPUT = Path.of(System.getProperty("contentstudio.villagerParity.output"));
    private static boolean installed, started, finished, originalFullscreen;
    private static int step, checks, captures, originalScale, originalWidth, originalHeight, phase = -1, view;
    private static long deadline, due;
    private static String originalLanguage;
    private static byte[] originalConfig;
    private static Path configPath;
    private static VillagerTradeEditorPage page;
    private static CompletableFuture<Void> serverWork, reload;

    public static void install() {
        if (installed) return;
        installed = true;
        KineticClientEvents.onTick(KineticClientEvents.TickPhase.END, VillagerParityValidation::tick);
    }

    private static String data() {
        //? if >=1.21 {
        /*return "[]";
        *///?} else {
        return "{}";
        //?}
    }

    private static String good() {
        return new VillagerConfig.TradeOfferData("minecraft:farmer", 1, "minecraft:emerald", 2, data(),
                "minecraft:air", 0, data(), "minecraft:bread", 3, data(), 16, 2, 0.05F,
                0, 0, true, 0, true, 1, 0).toConfigLine();
    }

    private static String legacyGood() { String line = good(); return line.substring(0, line.lastIndexOf('|')); }

    private static void tick() {
        if (finished) return;
        var mc = Minecraft.getInstance();
        try {
            if (!started) {
                if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
                started = true;
                Files.createDirectories(OUTPUT);
                configPath = (Path) staticField(VillagerConfig.class, "CONFIG_PATH");
                originalConfig = Files.exists(configPath) ? Files.readAllBytes(configPath) : null;
                if (originalConfig != null) Files.write(OUTPUT.resolve("villager-original.toml"), originalConfig);
                originalLanguage = mc.getLanguageManager().getSelected();
                originalScale = mc.options.guiScale().get();
                originalWidth = mc.getWindow().getWidth(); originalHeight = mc.getWindow().getHeight();
                originalFullscreen = mc.getWindow().isFullscreen();
                codecChecks();
                serverWork = onServer(() -> {
                    // Forge unit JVMs lack loader transforms; run registry-dependent assertions here instead.
                    runValidationMethods();
                    require(VillagerTradeRegistry.applyAndSaveTradeSourceMode("merge_all").success(), "baseline source applies");
                    require(VillagerTradeRegistry.applyAndSaveLiveDetailed(List.of("minecraft:farmer|1|add|2"),
                            List.of(good()), List.of(), false).success(), "baseline valid save applies");
                });
                next(1); return;
            }
            if (System.currentTimeMillis() > deadline) throw new AssertionError("Timed out at step " + step);
            if (step == 1) {
                if (!serverWork.isDone()) return; serverWork.join();
                mc.setScreen(null);
                VillagerClientActions.openTradeEditor(List.of("minecraft:farmer|1|add|2"), List.of(good(), BAD), List.of(), false, "merge_all");
                page = KineticGui.currentPage(VillagerTradeEditorPage.class);
                require(page != null, "current Kinetic page opens");
                VillagerConfig.villagerTradeOffers = new java.util.ArrayList<>(List.of(legacyGood(), BAD));
                invoke(page, "refreshEntries");
                invoke(page, "trySelectOwnerFromText", "minecraft:farmer");
                VillagerConfig.enableVillagerTradeLateOverride = true; // Unsaved draft must not reach the server through source save.
                invoke(page, "requestTradeSourceMode", VillagerConfig.TradeSourceMode.LOCAL_ONLY);
                require(!((dev.xyat.kineticcore.api.client.gui.widget.KineticControl) field(page, "professionBox")).isEnabled(),
                        "source transaction temporarily blocks draft controls");
                next(2); return;
            }
            if (step == 2) {
                if (page.isSavePending()) return;
                require(VillagerConfig.isLocalCustomTradesOnly(), "source ACK updates mode");
                require(VillagerConfig.villagerTradeOffers.contains(BAD), "source ACK retains invalid draft");
                require(VillagerConfig.enableVillagerTradeLateOverride, "source ACK retains late draft");
                serverWork = onServer(() -> {
                    String stored = Files.readString(configPath);
                    require(!stored.contains(BAD), "source save excludes unsaved rules");
                    require(!VillagerTradeRegistry.isActiveTradeLateOverride(), "source save excludes unsaved late override");
                    var npc = new Villager(EntityType.VILLAGER, Minecraft.getInstance().getSingleplayerServer().overworld());
                    var marked = VillagerConfig.TradeOfferData.parse(good(), 0).createOffer();
                    marked.increaseUses();
                    var offers = new MerchantOffers();
                    // An unmarked offer with identical item stacks must still be removed.
                    offers.add(vanillaOffer()); offers.add(marked);
                    setOffers(npc, offers);
                    require(npc.getOffers().size() == 1 && npc.getOffers().get(0) == marked,
                            "local-only filters unmarked offers, keeping original custom object");
                    require(marked.getUses() == 1, "filter retains existing usage metadata");
                    require(VillagerTradeRegistry.hasActiveLevelChanges("minecraft:armorer", 5), "local-only covers untouched professions");
                });
                next(3); return;
            }
            if (step == 3) {
                if (!serverWork.isDone()) return; serverWork.join();
                invoke(page, "saveValidTradeConfig"); next(4); return;
            }
            if (step == 4) {
                if (page.isSavePending()) return;
                require(VillagerConfig.villagerTradeOffers.contains(BAD), "partial save retains invalid raw draft");
                require(VillagerConfig.villagerTradeOffers.contains(good()), "partial save retains valid data after canonicalization");
                require(VillagerTradeRegistry.getAuthoritativeTradeOffers().equals(List.of(good())), "partial ACK matches canonical saved snapshot");
                require(VillagerTradeRegistry.getActiveTradeOffers("minecraft:farmer", 1).size() == 1, "partial save publishes only valid offer");
                require(VillagerTradeRegistry.isActiveTradeLateOverride(), "partial save applies requested late override");
                require(((Integer) field(page, "editingCustomIndex")) == 1, "error navigation focuses invalid row original index");
                require(!((List<?>) field(page, "validationIssues")).isEmpty(), "partial save retains exact issues");
                invoke(page, "closeEditorSession", true); // Discard rejected draft; preserve acknowledged valid save.
                require(VillagerConfig.villagerTradeOffers.size() == 1 && !VillagerConfig.villagerTradeOffers.contains(BAD), "discard returns to last accepted subset");
                require(VillagerConfig.isLocalCustomTradesOnly(), "discard leaves independent source setting alone");
                require(VillagerClientActions.requestTradeEditor(), "normal request uses server permission path");
                next(5); return;
            }
            if (step == 5) {
                page = KineticGui.currentPage(VillagerTradeEditorPage.class); if (page == null) return;
                require(dev.xyat.contentstudio.villager.util.VillagerTradeRuntimeUtil.vanillaTradeCount("minecraft:farmer", 1) > 0,
                        "normal editor request receives vanilla trade previews");
                invoke(page, "trySelectOwnerFromText", "minecraft:farmer");
                invoke(page, "clearSelectionToNewOffer");
                invoke(page, "setSlotStack", 0, new ItemStack(Items.EMERALD, 7));
                invoke(page, "setSlotStack", 2, new ItemStack(Items.BREAD, 3));
                invoke(page, "setSlotStack", 0, new ItemStack(Items.DIAMOND, 1));
                require(Objects.equals(field(page, "buyACount"), "7"), "replacement preserves payment quantity");
                var rightClick = new dev.xyat.kineticcore.api.client.gui.input.MouseInput(
                        (Integer) field(page, "rightX") + 18, (Integer) field(page, "rightY") + 114,
                        dev.xyat.kineticcore.api.client.gui.input.MouseButton.RIGHT, 1, 0);
                require(Objects.equals(invoke(page, "onMouseClickCapture", rightClick), true), "right click consumes slot action");
                require(Objects.equals(field(page, "buyAId"), "minecraft:air"), "right click clears payment slot");
                invoke(page, "setSlotStack", 0, new ItemStack(Items.DIAMOND, 7));
                set(page, "rewardExp", false); set(page, "allowRestock", false); invoke(page, "updateBoolButtons");
                require(Objects.equals(invoke(field(page, "rewardButton"), "value"), false), "reward toggle internal value synchronizes");
                require(Objects.equals(invoke(field(page, "restockButton"), "value"), false), "restock toggle internal value synchronizes");
                invoke(page, "saveCurrentOffer");
                require(Objects.equals(invoke(page, "hasUnsavedEdits"), true), "inline save is an unsaved configuration draft");
                require(VillagerClientActions.toggleTradeEditor(), "shortcut suspends editor");
                require(KineticGui.currentPage(VillagerTradeEditorPage.class) == null, "suspended editor exits to parent");
                require(VillagerClientActions.toggleTradeEditor(), "shortcut resumes editor");
                require(KineticGui.currentPage(VillagerTradeEditorPage.class) == page, "shortcut resumes same draft session");
                require(Objects.equals(field(page, "buyACount"), "7"), "shortcut retains form quantity");
                require(Objects.equals(invoke(page, "hasUnsavedEdits"), true), "shortcut resume reestablishes unsaved draft tracking");
                VillagerConfig.villagerTradeOffers.add(BAD);
                invoke(page, "saveValidTradeConfig"); next(7); return;
            }
            if (step == 7) {
                if (page.isSavePending()) return;
                require(VillagerTradeRegistry.getAuthoritativeTradeOffers().size() == 2, "resumed partial save accepts new valid draft");
                require(VillagerConfig.villagerTradeOffers.contains(BAD), "resumed partial save retains rejected raw draft");
                invoke(page, "closeEditorSession", true);
                require(VillagerConfig.villagerTradeOffers.equals(VillagerTradeRegistry.getAuthoritativeTradeOffers()), "shortcut-resumed discard restores accepted rules");
                VillagerClientActions.openTradeEditor(VillagerTradeRegistry.getAuthoritativeTradeGroups(),
                        VillagerTradeRegistry.getAuthoritativeTradeOffers(), VillagerTradeRegistry.getAuthoritativeTradeOverrides(), true, "local_only");
                page = KineticGui.currentPage(VillagerTradeEditorPage.class);
                VillagerConfig.villagerTradeGroups.add("minecraft:farmer|1|add|99");
                VillagerConfig.villagerTradeOffers.add("broken");
                VillagerConfig.villagerDefaultTradeOverrides.add("minecraft:farmer|1|0|perhaps|4");
                set(page, "validationActive", true); invoke(page, "refreshEntries");
                for (var kind : VillagerConfig.TradeIssueKind.values()) {
                    var issue = VillagerConfig.validateTradeListsDetailed(VillagerConfig.villagerTradeGroups,
                            VillagerConfig.villagerTradeOffers, VillagerConfig.villagerDefaultTradeOverrides).issues().stream()
                            .filter(value -> value.kind() == kind).findFirst().orElseThrow();
                    @SuppressWarnings("unchecked") var before = (List<String>) invoke(page, "rawProblemList", kind);
                    String badLine = before.get(issue.index());
                    invoke(page, "removeInvalidEntry", issue, badLine);
                    require(!((List<?>) invoke(page, "rawProblemList", kind)).contains(badLine), "raw problem removal: " + kind);
                    invoke(page, "undoLastChange");
                    require(((List<?>) invoke(page, "rawProblemList", kind)).contains(badLine), "raw problem removal undo: " + kind);
                }
                invoke(page, "closeEditorSession", true);
                openUnchangedInvalidDraft();
                invoke(page, "saveValidTradeConfig"); next(8); return;
            }
            if (step == 8) {
                if (page.isSavePending()) return;
                require(VillagerConfig.villagerTradeOffers.contains(BAD), "unchanged original partial draft retains rejected row");
                invoke(page, "closeEditorSession", true);
                require(VillagerConfig.villagerTradeOffers.equals(VillagerTradeRegistry.getAuthoritativeTradeOffers()),
                        "unchanged original partial draft discard restores accepted subset");
                openUnchangedInvalidDraft();
                invoke(page, "saveValidTradeConfig"); next(9); return;
            }
            if (step == 9) {
                if (page.isSavePending()) return;
                require(VillagerClientActions.toggleTradeEditor(), "unchanged partial draft shortcut suspends");
                require(VillagerConfig.villagerTradeOffers.equals(VillagerTradeRegistry.getAuthoritativeTradeOffers()),
                        "unchanged partial draft suspension leaves accepted rules active");
                require(VillagerClientActions.toggleTradeEditor(), "unchanged partial draft shortcut resumes");
                require(VillagerConfig.villagerTradeOffers.contains(BAD), "unchanged partial draft shortcut retains rejected row for repair");
                invoke(page, "closeEditorSession", true);
                require(VillagerConfig.villagerTradeOffers.equals(VillagerTradeRegistry.getAuthoritativeTradeOffers()),
                        "unchanged partial draft resume discard restores accepted subset");
                mc.options.guiScale().set(0);
                if (originalFullscreen) mc.getWindow().toggleFullScreen();
                nextPhase(); next(6); return;
            }
            if (step == 6) {
                if (reload != null) {
                    if (!reload.isDone() || mc.getOverlay() != null) return;
                    reload.join(); reload = null; openView(); return;
                }
                if (System.currentTimeMillis() < due) return;
                capture();
                view++;
                if (view == 4) nextPhase(); else openView();
            }
        } catch (Throwable error) {
            LOG.error("VILLAGER_PARITY_FAIL step=" + step, error); finish(false);
        }
    }

    private static void next(int target) { step = target; deadline = System.currentTimeMillis() + 90000; }

    private static void openUnchangedInvalidDraft() throws Exception {
        var raw = new java.util.ArrayList<>(VillagerTradeRegistry.getAuthoritativeTradeOffers()); raw.add(BAD);
        VillagerClientActions.openTradeEditor(VillagerTradeRegistry.getAuthoritativeTradeGroups(), raw,
                VillagerTradeRegistry.getAuthoritativeTradeOverrides(), true, "local_only");
        page = KineticGui.currentPage(VillagerTradeEditorPage.class);
        require(Objects.equals(invoke(page, "hasUnsavedEdits"), false), "opening invalid persisted rows starts at original Core baseline");
    }

    private static void nextPhase() {
        phase++; view = 0;
        if (phase >= 4) { finish(true); return; }
        var mc = Minecraft.getInstance();
        String language = phase < 2 ? "en_us" : "zh_cn";
        mc.getLanguageManager().setSelected(language); mc.options.languageCode = language;
        mc.getWindow().setWindowed(phase % 2 == 0 ? 854 : 1920, phase % 2 == 0 ? 480 : 1080); mc.resizeDisplay();
        reload = mc.reloadResourcePacks(); deadline = System.currentTimeMillis() + 90000;
        LOG.info("VILLAGER_PARITY_PHASE language={} requestedWidth={}", language, phase % 2 == 0 ? 854 : 1920);
    }

    private static void openView() throws Exception {
        if (view == 0) {
            // No network save here: the remaining views use deliberately invalid, unsaved UI drafts.
            var previous = KineticGui.currentPage(VillagerTradeEditorPage.class);
            if (previous != null) invoke(previous, "closeEditorSession", true);
            VillagerClientActions.openTradeEditor(List.of("minecraft:farmer|1|add|2"), List.of(good(), BAD), List.of(), true, "local_only");
            page = KineticGui.currentPage(VillagerTradeEditorPage.class);
            set(page, "validationActive", true); invoke(page, "refreshEntries");
            invoke(page, "trySelectOwnerFromText", "minecraft:farmer"); invoke(page, "focusCurrentProblem");
            require(VillagerConfig.villagerTradeOffers.contains(BAD) && !((List<?>) field(page, "validationIssues")).isEmpty(), "capture contains rejected draft and exact issue status");
        } else if (view == 1) {
            invoke(page, "openProblemMenu", (Integer) field(page, "rightX") + 14, (Integer) field(page, "rightY") + 68);
        } else if (view == 2) {
            invoke(page, "closeContextMenu");
            invoke(page, "selectLevelSettings", 1);
            invoke(page, "openTradeSourceMenu", (Integer) field(page, "rightX") + 246, (Integer) field(page, "rightY") + 268);
        } else {
            invoke(page, "closeContextMenu");
            invoke(page, "clearSelectionToNewOffer");
            invoke(page, "setSlotStack", 0, new ItemStack(Items.EMERALD, 7));
            invoke(page, "setSlotStack", 2, new ItemStack(Items.BREAD, 3));
            invoke(page, "onCloseRequested");
        }
        due = System.currentTimeMillis() + 1300;
    }

    private static void capture() throws Exception {
        var mc = Minecraft.getInstance();
        if (view != 0) checkMenuBounds();
        Path path = OUTPUT.resolve(String.format("%d-%d-%s.png", phase, view, new String[]{"errors", "problem-menu", "source-menu", "close-menu"}[view]));
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}
        captures++; LOG.info("VILLAGER_PARITY_CAPTURE phase={} view={} image={}x{}", phase, view, mc.getWindow().getWidth(), mc.getWindow().getHeight());
    }

    private static void checkMenuBounds() throws Exception {
        var mc = Minecraft.getInstance();
        Object runtime = invoke(mc.screen, "kineticRuntime");
        Object overlays = invoke(runtime, "overlays");
        Object context = field(overlays, "contextMenu");
        require(context != null, "expected popup is visible");
        var boundsMethod = overlays.getClass().getDeclaredMethod("menuBounds", context.getClass(), int.class, int.class,
                net.minecraft.client.gui.Font.class, int.class);
        boundsMethod.setAccessible(true);
        Object bounds = boundsMethod.invoke(null, context, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), mc.font, 0);
        Object canvas = invoke(runtime, "canvas");
        int left = (Integer) invoke(canvas, "menuX", ((Integer) field(page, "rootX")).doubleValue());
        int top = (Integer) invoke(canvas, "menuY", ((Integer) field(page, "rootY")).doubleValue());
        int right = (Integer) invoke(canvas, "menuX", ((Integer) field(page, "rootX") + (Integer) field(page, "rootW")) * 1.0);
        int bottom = (Integer) invoke(canvas, "menuY", ((Integer) field(page, "rootY") + (Integer) field(page, "rootH")) * 1.0);
        int x = (Integer) invoke(bounds, "x"), y = (Integer) invoke(bounds, "y");
        require(x > left && x + (Integer) invoke(bounds, "width") < right
                && y > top && y + (Integer) invoke(bounds, "height") < bottom,
                "popup including buttons stays inside editor frame");
    }

    private static void finish(boolean passed) {
        if (finished) return;
        finished = true;
        var mc = Minecraft.getInstance();
        CompletableFuture<Void> restore = onServer(() -> {
            if (configPath != null) {
                if (originalConfig == null) Files.deleteIfExists(configPath); else Files.write(configPath, originalConfig);
                VillagerTradeRegistry.reloadFromDisk();
            }
        });
        restore.whenComplete((ignored, error) -> mc.execute(() -> {
            try {
                mc.setScreen(null);
                mc.options.guiScale().set(originalScale);
                mc.getLanguageManager().setSelected(originalLanguage); mc.options.languageCode = originalLanguage;
                mc.getWindow().setWindowed(originalWidth, originalHeight);
                if (originalFullscreen && !mc.getWindow().isFullscreen()) mc.getWindow().toggleFullScreen();
                if (error != null) LOG.error("VILLAGER_PARITY_RESTORE_FAIL", error);
                LOG.info("VILLAGER_PARITY_{} checks={} captures={} configRestored={}", passed && error == null ? "PASS" : "FAILED", checks, captures, error == null);
            } finally { dev.xyat.kineticcore.api.runtime.KineticClientRuntime.stopClient(); }
        }));
    }

    private static CompletableFuture<Void> onServer(Check action) {
        return CompletableFuture.runAsync(() -> {
            try { action.run(); } catch (Throwable error) { throw new java.util.concurrent.CompletionException(error); }
        }, Minecraft.getInstance().getSingleplayerServer());
    }

    //? if >=1.21 {
    /*private static net.minecraft.world.item.trading.MerchantOffer vanillaOffer() {
        return new net.minecraft.world.item.trading.MerchantOffer(new net.minecraft.world.item.trading.ItemCost(Items.EMERALD, 2), new ItemStack(Items.BREAD, 3), 16, 2, 0.05F);
    }
    *///?} else {
    private static net.minecraft.world.item.trading.MerchantOffer vanillaOffer() {
        return new net.minecraft.world.item.trading.MerchantOffer(new ItemStack(Items.EMERALD, 2), new ItemStack(Items.BREAD, 3), 16, 2, 0.05F);
    }
    //?}

    private static void setOffers(Villager npc, MerchantOffers offers) throws Exception {
        // Type lookup also works in Forge's reobfuscated installed environment.
        for (var field : net.minecraft.world.entity.npc.AbstractVillager.class.getDeclaredFields()) {
            if (field.getType() == MerchantOffers.class) { field.setAccessible(true); field.set(npc, offers); return; }
        }
        throw new NoSuchFieldException("MerchantOffers backing field");
    }

    private static void runValidationMethods() throws Exception {
        var type = Class.forName("dev.xyat.contentstudiovalidation.VillagerParityChecks");
        var ctor = type.getDeclaredConstructor(); ctor.setAccessible(true); Object test = ctor.newInstance();
        for (var method : type.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(org.junit.jupiter.api.Test.class)) continue;
            if (method.getName().equals("sourceModeSaveWritesAcknowledgedRulesAndRestoresTheRawEditorDraft")) continue;
            method.setAccessible(true); method.invoke(test); require(true, "initialized-loader validation: " + method.getName());
        }
    }

    private static void codecChecks() throws Exception {
        var open = roundTrip(new VillagerNetwork.OpenTradeEditorPacket());
        require(Objects.equals(field(open, "sourceMode"), VillagerConfig.getTradeSourceModeValue()), "open codec carries source mode");
        require(Objects.equals(field(open, "lateOverride"), VillagerConfig.enableVillagerTradeLateOverride), "open codec carries late override");
        var ctor = VillagerNetwork.SaveTradeEditorPacket.class.getDeclaredConstructor(List.class, List.class, List.class, boolean.class, boolean.class); ctor.setAccessible(true);
        var save = roundTrip(ctor.newInstance(List.of("group"), List.of("validated offer"), List.of("override"), true, false));
        require(Objects.equals(field(save, "offers"), List.of("validated offer")), "save codec keeps validated subset");
        require(Objects.equals(field(save, "lateOverride"), true) && Objects.equals(field(save, "notifySuccess"), false), "save codec retains independent flags");
        var result = (VillagerNetwork.TradeSourceModeResultPacket) roundTrip(new VillagerNetwork.TradeSourceModeResultPacket(false, "merge_all", "permission_denied"));
        require(result.mode().equals("merge_all") && result.failureCode().equals("permission_denied"), "source result codec carries mode and denial");
        var denied = (VillagerNetwork.TradeEditorOpenDeniedPacket) roundTrip(new VillagerNetwork.TradeEditorOpenDeniedPacket("server_unavailable"));
        require(denied.failureCode().equals("server_unavailable"), "open denial codec carries reason");
    }

    private static Object roundTrip(Object packet) throws Exception {
        var queue = new ArrayDeque<Object>();
        var buffer = (NetworkBuffer) Proxy.newProxyInstance(NetworkBuffer.class.getClassLoader(), new Class<?>[]{NetworkBuffer.class}, (proxy, method, args) -> {
            if (method.getName().startsWith("write")) { queue.add(args[0]); return null; }
            if (method.getName().startsWith("read")) return queue.remove();
            throw new UnsupportedOperationException(method.getName());
        });
        var encode = packet.getClass().getDeclaredMethod("encode", NetworkBuffer.class); encode.setAccessible(true); encode.invoke(packet, buffer);
        Object decoded = packet.getClass().getMethod("decode", NetworkBuffer.class).invoke(null, buffer);
        require(queue.isEmpty(), "packet decoder consumes complete payload"); return decoded;
    }

    private static Object staticField(Class<?> type, String name) throws Exception { var f = type.getDeclaredField(name); f.setAccessible(true); return f.get(null); }
    private static Object field(Object object, String name) throws Exception { var f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object); }
    private static void set(Object object, String name, Object value) throws Exception { var f = object.getClass().getDeclaredField(name); f.setAccessible(true); f.set(object, value); }
    private static Object invoke(Object object, String name, Object... args) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            for (var method : type.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
                method.setAccessible(true); return method.invoke(object, args);
            }
        }
        for (var method : object.getClass().getMethods()) if (method.getName().equals(name) && method.getParameterCount() == args.length) { method.setAccessible(true); return method.invoke(object, args); }
        throw new NoSuchMethodException(name);
    }
    private static void require(boolean condition, String description) {
        if (!condition) throw new AssertionError(description); checks++; LOG.info("VILLAGER_PARITY_CHECK {}", description);
    }
    @FunctionalInterface private interface Check { void run() throws Throwable; }
}
