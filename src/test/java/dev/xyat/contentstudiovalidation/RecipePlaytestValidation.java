package dev.xyat.contentstudiovalidation;

import dev.xyat.contentstudio.recipe.client.gui.NativeRecipeBrowserPage;
import dev.xyat.contentstudio.recipe.client.gui.NativeRecipeEditorPage;
import dev.xyat.contentstudio.recipe.client.gui.RecipeHubPage;
import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.client.gui.KineticGui;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.internal.client.gui.screen.KineticScreenHost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Edits third-party recipes the way a player does: the recipe hub, a workstation card, the recipe list, the visual
 * editor with its slots, the item selector and the Save, Restore, Remove and Delete buttons. Every result is checked
 * on the server's live recipe manager. 1.20.1 only, where the modpack provides the third-party recipe types.
 */
public final class RecipePlaytestValidation {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(RecipePlaytestValidation.class);
    private static final List<String> TYPES = List.of("create:milling", "farmersdelight:cutting", "create:crushing", "create:pressing", "ae2:inscriber", "ae2:charger");
    private static final String CREATED = "contentstudio:playtest_created";
    private record Step(String name, Check check) { }
    private interface Check { boolean run() throws Exception; }

    private static final List<Step> steps = new ArrayList<>();
    private static final List<String> passes = new ArrayList<>();
    private static boolean installed, finished;
    private static int index, wait, waited;
    private static String type, recipeId;
    private static int originalCount;
    private static String typedSearch;
    private static boolean originalInvulnerable;
    private static Object lastStationPage;
    private static Field mouseX, mouseY;
    private static double originalMouseX, originalMouseY;

    private RecipePlaytestValidation() { }

    public static void install() {
        if (installed) return;
        installed = true;
        buildSteps();
        KineticClientEvents.onTick(KineticClientEvents.TickPhase.END, RecipePlaytestValidation::tick);
    }

    private static void tick() {
        if (finished) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) return;
        if (++wait < 6) return;
        Step step = steps.get(index);
        try {
            if (step.check().run()) {
                LOG.info("RECIPE_PLAYTEST_STEP_PASS {}", step.name());
                index++; wait = 0; waited = 0;
                if (index >= steps.size()) finish(null);
            } else if (++waited > 400) throw new AssertionError("step did not complete: " + step.name());
        } catch (Throwable error) {
            finish(new AssertionError("step " + step.name() + ": " + error.getMessage(), error));
        }
    }

    private static void buildSteps() {
        // A world saved with a dead player opens on the death screen; respawn first, as its Respawn button does.
        steps.add(new Step("respawn", () -> {
            var mc = Minecraft.getInstance();
            if (mc.player.isDeadOrDying()) { mc.player.respawn(); return false; }
            if (mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen) { mc.setScreen(null); return false; }
            return true;
        }));
        steps.add(new Step("prepare", () -> {
            // Every check runs in a 1920x1080 window, like the other fixtures; dev runs open smaller by default.
            var mc = Minecraft.getInstance();
            mc.options.guiScale().set(0); mc.getWindow().setWindowed(1920, 1080); mc.resizeDisplay();
            var server = Minecraft.getInstance().getSingleplayerServer();
            var id = Minecraft.getInstance().player.getUUID();
            type = server.submit(() -> {
                var player = server.getPlayerList().getPlayer(id);
                //? if >=26.1 {
                /*server.getPlayerList().op(new net.minecraft.server.players.NameAndId(player.getGameProfile()));
                *///?} else {
                server.getPlayerList().op(player.getGameProfile());
                //?}
                // Nothing in the test world may kill the player while the GUI is driven; restored when the test ends.
                originalInvulnerable = player.getAbilities().invulnerable;
                player.getAbilities().invulnerable = true; player.onUpdateAbilities();
                for (String candidate : TYPES) {
                    for (var recipe : recipes(server)) {
                        var key = net.minecraft.core.registries.BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer());
                        if (key != null && key.toString().equals(candidate)) return candidate;
                    }
                }
                return null;
            }).get();
            require(type != null, "the modpack has none of the third-party recipe types " + TYPES);
            return true;
        }));
        // Recipe hub -> workstation card -> recipe list -> first recipe of the type.
        openBrowserSteps("modify");
        steps.add(new Step("modify-open-recipe", () -> {
            var rows = asList(field(KineticGui.currentPage(), "rows"));
            if (rows.isEmpty()) return false;
            recipeId = ((com.google.gson.JsonObject) rows.get(0)).get("id").getAsString();
            originalCount = outputCount(recipeId);
            clickFirstRecipe();
            return true;
        }));
        steps.add(new Step("modify-editor", NativeRecipePlay::editorReady));
        // A left click selects the output slot and opens the item selector a moment later; close it to edit the count.
        steps.add(new Step("modify-count", () -> { clickSlot(true); return true; }));
        steps.add(new Step("modify-count-selector", () -> {
            var screen = Minecraft.getInstance().screen;
            if (screen == null || !screen.getClass().getSimpleName().equals("ItemSelectorScreen")) return false;
            key(256);
            return true;
        }));
        steps.add(new Step("modify-count-field", () -> {
            if (!NativeRecipePlay.editorReady()) return false;
            int row = fieldRow("count");
            clickField(row);
            for (int i = 0; i < 10; i++) key(259);
            typeText(Integer.toString(originalCount + 2));
            clickLabel("save");
            return true;
        }));
        steps.add(new Step("modify-count-saved", () -> {
            if (busy()) return false;
            int count = outputCount(recipeId);
            if (count != originalCount + 2) return false;
            passes.add(type + " " + recipeId + ": output count changed in the editor (" + originalCount + " -> " + count + ")");
            return true;
        }));
        // The save rebuilds the editor; wait for it before picking the input slot.
        steps.add(new Step("modify-input", () -> {
            if (!NativeRecipePlay.editorReady()) {
                if (waited % 40 == 39) LOG.info("RECIPE_PLAYTEST_WAIT input screen={} page={}", Minecraft.getInstance().screen, KineticGui.currentPage());
                return false;
            }
            clickSlot(false);
            return true;
        }));
        steps.add(new Step("modify-input-pick", () -> pickItem("minecraft:diamond")));
        steps.add(new Step("modify-input-save", () -> {
            if (!(KineticGui.currentPage() instanceof NativeRecipeEditorPage)) return false;
            clickLabel("save");
            return true;
        }));
        steps.add(new Step("modify-input-saved", () -> {
            if (busy()) return false;
            if (!takesDiamond(recipeId)) return false;
            passes.add(type + " " + recipeId + ": input replaced with a diamond through the item selector");
            return true;
        }));
        // Saves, restores and removals rebuild the editor: these steps wait until the control is back.
        steps.add(new Step("modify-restore", () -> tryClickLabel("restore", "delete_recipe")));
        steps.add(new Step("modify-restore-confirm", RecipePlaytestValidation::confirmDialog));
        steps.add(new Step("modify-restored", () -> {
            if (busy()) return false;
            if (outputCount(recipeId) != originalCount || takesDiamond(recipeId)) {
                if (waited % 40 == 39) LOG.info("RECIPE_PLAYTEST_WAIT restore count={} diamond={} screen={} page={}", outputCount(recipeId),
                        takesDiamond(recipeId), Minecraft.getInstance().screen, KineticGui.currentPage());
                return false;
            }
            passes.add(type + " " + recipeId + ": Restore brought the original recipe back");
            return true;
        }));
        steps.add(new Step("remove", () -> !busy() && tryClickLabel("disable_recipe")));
        steps.add(new Step("removed", () -> {
            if (busy() || exists(recipeId)) return false;
            return tryClickLabel("enable_recipe");
        }));
        steps.add(new Step("re-enabled", () -> {
            if (busy() || !exists(recipeId)) return false;
            passes.add(type + " " + recipeId + ": Remove took the recipe out and Enable put it back");
            clickLabel("back");
            return true;
        }));
        // New recipe from a template of the same type.
        steps.add(new Step("create-template", () -> {
            if (!(KineticGui.currentPage() instanceof NativeRecipeBrowserPage)) return false;
            clickLabel("new_recipe");
            clickFirstRecipe();
            return true;
        }));
        steps.add(new Step("create-editor", NativeRecipePlay::editorReady));
        steps.add(new Step("create-id", () -> {
            clickIdField();
            // The click puts the cursor where it lands; go to the end before clearing the template's ID.
            key(269);
            for (int i = 0; i < 128; i++) key(259);
            typeText(CREATED);
            clickSlot(false);
            return true;
        }));
        steps.add(new Step("create-input-pick", () -> pickItem("minecraft:diamond")));
        steps.add(new Step("create-save", () -> {
            if (!(KineticGui.currentPage() instanceof NativeRecipeEditorPage)) return false;
            clickLabel("save");
            return true;
        }));
        steps.add(new Step("created", () -> {
            if (busy() || !exists(CREATED)) {
                var page = KineticGui.currentPage();
                if (waited % 40 == 39) LOG.info("RECIPE_PLAYTEST_WAIT created busy={} exists={} target={} page={}", busy(), exists(CREATED),
                        page instanceof NativeRecipeEditorPage ? field(field(page, "session"), "target") : null, page);
                return false;
            }
            require(takesDiamond(CREATED), "the created recipe does not take the chosen diamond");
            passes.add(type + ": a new recipe was created from a template with a diamond input");
            return true;
        }));
        // What a player sees: hovering a slot says whether it is an input or an output, and an added material looks
        // like the recipe's own slots. Screenshots for review; the added material is never saved (the recipe is deleted).
        steps.add(new Step("visual-hover-input", () -> { hoverSlot(false); return true; }));
        steps.add(new Step("visual-hover-input-capture", () -> settleAndCapture("hover-input")));
        steps.add(new Step("visual-hover-output", () -> { hoverSlot(true); return true; }));
        steps.add(new Step("visual-hover-output-capture", () -> settleAndCapture("hover-output")));
        steps.add(new Step("visual-add-material", () -> { hover(2, 358); clickLabel("add_material"); return true; }));
        steps.add(new Step("visual-add-menu", () -> {
            if (waited < 5) return false;
            // A recipe without a list of materials (AE2's inscriber has fixed top, middle and bottom inputs) opens the
            // field page straight away instead of a menu of lists.
            if (!(KineticGui.currentPage() instanceof NativeRecipeEditorPage)) return true;
            capture("add-menu");
            clickMenuRow(menuRows().get(0));
            return true;
        }));
        steps.add(new Step("visual-added-fields", () -> settleAndCapture("added-fields")));
        steps.add(new Step("visual-added-back", () -> { clickLabel("back"); return true; }));
        steps.add(new Step("visual-added-editor", () -> NativeRecipePlay.editorReady() && settleAndCapture("added-editor")));
        steps.add(new Step("visual-added-hover", () -> { hoverLastInput(); return true; }));
        steps.add(new Step("visual-added-hover-capture", () -> settleAndCapture("added-hover")));
        steps.add(new Step("delete", () -> { clickLabel("restore", "delete_recipe"); return true; }));
        steps.add(new Step("delete-confirm", RecipePlaytestValidation::confirmDialog));
        steps.add(new Step("deleted", () -> {
            if (busy() || exists(CREATED)) return false;
            passes.add(type + ": Delete removed the created recipe");
            return true;
        }));
    }

    private static void openBrowserSteps(String name) {
        steps.add(new Step(name + "-hub", () -> {
            dev.xyat.contentstudio.recipe.client.gui.RecipeNavigationState.requestHub();
            return true;
        }));
        steps.add(new Step(name + "-hub-open", () -> {
            if (KineticGui.currentPage() instanceof RecipeHubPage) return true;
            if (waited % 40 == 39) LOG.info("RECIPE_PLAYTEST_WAIT hub screen={} page={}", Minecraft.getInstance().screen, KineticGui.currentPage());
            return false;
        }));
        // Mod card, then Create's group card, then the recipe type card; each is found by its key and scrolled into view.
        steps.add(new Step(name + "-stations", () -> {
            var page = KineticGui.currentPage();
            if (page instanceof NativeRecipeBrowserPage) return true;
            if (page == lastStationPage) return false;
            Object menu = page instanceof RecipeHubPage ? field(page, "stations") : field(page, "menu");
            List<?> cards = asList(field(menu, "cards"));
            if (cards.isEmpty()) return false;
            String namespace = type.split(":", 2)[0];
            var keys = List.of(type, "basic", namespace);
            int card = -1;
            for (String key : keys) {
                for (int i = 0; i < cards.size() && card < 0; i++) if (key.equals(call(cards.get(i), "key"))) card = i;
                if (card >= 0) break;
            }
            require(card >= 0, "no workstation card leads to " + type);
            int x = (int) field(menu, "x"), y = (int) field(menu, "y"), visible = (int) field(menu, "visibleRows");
            var scroll = (dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController) field(menu, "scroll");
            int row = card / 3 - scroll.offset();
            if (row < 0 || row >= visible) { wheel(x + 100, y + 20, row < 0 ? 1 : -1); return false; }
            if (Math.abs(scroll.smoothOffset() - scroll.offset()) > 0.01) return false;
            click(x + (card % 3) * 188 + 92, y + row * 42 + 19);
            lastStationPage = page;
            return false;
        }));
        steps.add(new Step(name + "-list", () -> {
            var page = KineticGui.currentPage();
            return page instanceof NativeRecipeBrowserPage && !asList(field(page, "rows")).isEmpty();
        }));
    }

    /** Editor page helpers. */
    private static final class NativeRecipePlay {
        static boolean editorReady() throws Exception {
            var page = KineticGui.currentPage();
            return page instanceof NativeRecipeEditorPage && !asList(field(page, "slots")).isEmpty() && !busy();
        }
    }

    /** Left-clicks the first output or input slot of the diagram; this selects it and opens the item selector. */
    private static void clickSlot(boolean output) throws Exception {
        var page = KineticGui.currentPage();
        List<?> slots = asList(field(page, "slots")), jeiSlots = asList(field(page, "jeiSlots"));
        int diagramX = (int) field(page, "diagramX"), diagramY = (int) field(page, "diagramY");
        float scale = (float) field(page, "scale");
        for (int i = 0; i < slots.size(); i++) {
            Object slot = slots.get(i);
            if ((boolean) call(slot, "output") != output) continue;
            int width = jeiSlots.isEmpty() ? 18 : (int) call(jeiSlots.get(i), "width"), height = jeiSlots.isEmpty() ? 18 : (int) call(jeiSlots.get(i), "height");
            click(diagramX + (int) call(slot, "x") * scale + width * scale / 2, diagramY + (int) call(slot, "y") * scale + height * scale / 2);
            return;
        }
        throw new AssertionError("the recipe has no " + (output ? "output" : "input") + " slot bound for editing");
    }

    /** Centre of the first (or last) input or output slot of the diagram, in canvas coordinates. */
    private static double[] slotCenter(boolean output, boolean last) throws Exception {
        var page = KineticGui.currentPage();
        List<?> slots = asList(field(page, "slots")), jeiSlots = asList(field(page, "jeiSlots"));
        int diagramX = (int) field(page, "diagramX"), diagramY = (int) field(page, "diagramY");
        float scale = (float) field(page, "scale");
        double[] found = null;
        for (int i = 0; i < slots.size(); i++) {
            Object slot = slots.get(i);
            if ((boolean) call(slot, "output") != output) continue;
            int width = jeiSlots.isEmpty() ? 18 : (int) call(jeiSlots.get(i), "width"), height = jeiSlots.isEmpty() ? 18 : (int) call(jeiSlots.get(i), "height");
            found = new double[]{diagramX + (int) call(slot, "x") * scale + width * scale / 2, diagramY + (int) call(slot, "y") * scale + height * scale / 2};
            if (!last) break;
        }
        require(found != null, "the recipe has no " + (output ? "output" : "input") + " slot bound for editing");
        return found;
    }

    private static void hoverSlot(boolean output) throws Exception { var at = slotCenter(output, false); hover(at[0], at[1]); lastHover = at; }

    private static double[] lastHover;

    /** Diagnostics: why a page tooltip is or is not drawn at the hovered point. */
    private static void tooltipDiagnostics() throws Exception {
        if (lastHover == null) return;
        var screen = Minecraft.getInstance().screen;
        var under = new ArrayList<String>();
        for (var child : screen.children()) if (child.isMouseOver(lastHover[0], lastHover[1])) under.add(child.getClass().getSimpleName());
        Object runtime = field(screen, "runtime");
        boolean allowed = (boolean) runtime.getClass().getMethod("businessTooltipsAllowed", boolean.class).invoke(runtime, true);
        Object overlays = runtime.getClass().getMethod("overlays").invoke(runtime);
        boolean blocks = (boolean) overlays.getClass().getMethod("blocksInput").invoke(overlays);
        Object shown;
        try { shown = field(overlays, "tooltip"); } catch (NoSuchFieldException none) { shown = "n/a " + overlays.getClass().getSimpleName(); }
        LOG.info("RECIPE_PLAYTEST_TOOLTIP at={},{} under={} businessAllowed={} overlaysBlock={} lastFrameTooltip={}", lastHover[0], lastHover[1], under, allowed, blocks, shown);
    }

    private static void hoverLastInput() throws Exception { var at = slotCenter(false, true); hover(at[0], at[1]); }

    /** Updates only cached game coordinates; the test never moves the system cursor or focuses its window. */
    private static void hover(double canvasX, double canvasY) throws Exception {
        var mc = Minecraft.getInstance();
        var host = (KineticScreenHost) mc.screen;
        if (mouseX == null) {
            mouseX = mouseCoordinate(mc.mouseHandler, "xpos", "f_91507_");
            mouseY = mouseCoordinate(mc.mouseHandler, "ypos", "f_91508_");
            originalMouseX = mouseX.getDouble(mc.mouseHandler);
            originalMouseY = mouseY.getDouble(mc.mouseHandler);
        }
        mouseX.setDouble(mc.mouseHandler, host.toScreenX(canvasX) * mc.getWindow().getWidth() / mc.getWindow().getGuiScaledWidth());
        mouseY.setDouble(mc.mouseHandler, host.toScreenY(canvasY) * mc.getWindow().getHeight() / mc.getWindow().getGuiScaledHeight());
    }

    private static Field mouseCoordinate(Object mouse, String... names) throws NoSuchFieldException {
        for (String name : names) try {
            var coordinate = mouse.getClass().getDeclaredField(name);
            coordinate.setAccessible(true);
            return coordinate;
        } catch (NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(Arrays.toString(names));
    }

    /** Waits a few ticks so the frame shows the new state, then saves a screenshot. */
    private static boolean settleAndCapture(String name) throws Exception {
        if (waited < 10) return false;
        if (name.contains("hover")) tooltipDiagnostics();
        capture(name);
        return true;
    }

    private static void capture(String name) throws Exception {
        var mc = Minecraft.getInstance();
        var path = java.nio.file.Path.of(System.getProperty("contentstudio.guiValidation.output", "D:/IDEAWork/ContentStudio/.gradle/recipe-play/"),
                "recipe-play-" + type.replace(':', '_') + "-" + name + ".png");
        java.nio.file.Files.createDirectories(path.getParent());
        // Written exactly like the other fixtures: the 26.1 build rewrites this line into the callback form.
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}
        LOG.info("RECIPE_PLAYTEST_CAPTURE {}", path.getFileName());
    }

    private static List<?> menuRows() throws Exception { return asList(field(menuBounds(), "rows")); }

    private static void clickMenuRow(Object row) throws Exception {
        Object bounds = menuBounds();
        clickScreen((int) field(bounds, "x") + 10, (int) field(row, "y") + (int) field(row, "height") / 2.0);
    }

    private static Object menuBounds() throws Exception {
        var screen = Minecraft.getInstance().screen;
        Object runtime = field(screen, "runtime");
        Object overlays = runtime.getClass().getMethod("overlays").invoke(runtime);
        Object menu = field(overlays, "contextMenu");
        require(menu != null, "the menu did not open");
        for (var method : overlays.getClass().getDeclaredMethods()) {
            if (method.getName().equals("menuBounds")) {
                method.setAccessible(true);
                return method.invoke(null, menu, screen.width, screen.height, Minecraft.getInstance().font, field(overlays, "menuScroll"));
            }
        }
        throw new AssertionError("core menu bounds unavailable");
    }

    /** Searches the core item selector and clicks the item; the selector closes and returns to the editor. */
    private static boolean pickItem(String id) throws Exception {
        var screen = Minecraft.getInstance().screen;
        if (screen == null || !screen.getClass().getSimpleName().equals("ItemSelectorScreen")) return false;
        int gridX = (int) field(screen, "gridX"), gridY = (int) field(screen, "gridY");
        if (!id.equals(typedSearch)) {
            // A cast, not reflection: vanilla method names are obfuscated in the 1.20.1 production game.
            var searchBox = (net.minecraft.client.gui.components.AbstractWidget) field(screen, "searchBox");
            click(searchBox.getX() + 20, searchBox.getY() + 8);
            typeText(id);
            typedSearch = id;
            return false;
        }
        List<?> display = asList(field(screen, "displayList"));
        int found = -1;
        for (int i = 0; i < display.size(); i++) {
            ItemStack stack = (ItemStack) call(display.get(i), "stack");
            if (KineticRegistries.items().id(stack.getItem()).toString().equals(id)) { found = i; break; }
        }
        if (found < 0) return false;
        Object grid = field(screen, "itemGrid");
        int columns = (int) call(grid, "columns");
        require(found < columns * 12, id + " is not on the first page of the item selector");
        click(gridX + 2 + (found % columns) * 24 + 11, gridY + 2 + (found / columns) * 24 + 11);
        typedSearch = null;
        return true;
    }

    private static int fieldRow(String key) throws Exception {
        var page = KineticGui.currentPage();
        List<?> fields = asList(field(page, "fields"));
        int fieldPage = (int) field(page, "fieldPage");
        for (int i = 0; i < fields.size(); i++) {
            List<?> path = (List<?>) fields.get(i);
            if (path.get(path.size() - 1).equals(key)) {
                require(i / 8 == fieldPage, "the " + key + " field is not on the shown properties page");
                return i % 8;
            }
        }
        throw new AssertionError("the selected slot has no " + key + " field");
    }

    private static boolean busy() throws Exception {
        var page = KineticGui.currentPage();
        if (!(page instanceof NativeRecipeEditorPage)) return false;
        return (boolean) field(field(page, "session"), "busy");
    }

    private static boolean confirmDialog() throws Exception {
        var screen = Minecraft.getInstance().screen;
        Object runtime = field(screen, "runtime");
        Object overlays = runtime.getClass().getMethod("overlays").invoke(runtime);
        Object dialog = field(overlays, "dialog");
        // The dialog opens on the click and lays out its buttons when it is first drawn; wait for both.
        if (dialog == null) return false;
        var button = (net.minecraft.client.gui.components.AbstractWidget) call(dialog, "confirmButton");
        if (button == null || button.getX() == 0 && button.getY() == 0) return false;
        LOG.info("RECIPE_PLAYTEST_CONFIRM at {},{} {}x{} active={}", button.getX(), button.getY(), button.getWidth(), button.getHeight(), button.active);
        // Dialog buttons are laid out in screen coordinates, not on the virtual canvas.
        clickScreen(button.getX() + button.getWidth() / 2.0, button.getY() + button.getHeight() / 2.0);
        return true;
    }

    // ------------------------------------------------------------------ layout-independent clicks
    // Buttons are found by their label and fields by the page's own constants, so layout changes do not break the test.

    /** Clicks the visible control labelled with one of these native recipe texts. */
    private static void clickLabel(String... keys) {
        if (!tryClickLabel(keys)) throw new AssertionError("no control labelled " + String.join(" / ", keys));
    }

    /** Like clickLabel, but false (try again next tick) while the page is still rebuilding without that control. */
    private static boolean tryClickLabel(String... keys) {
        var screen = Minecraft.getInstance().screen;
        if (screen == null) return false;
        for (String key : keys) {
            String text = dev.xyat.kineticcore.api.text.KineticI18n.translatable("gui.contentstudio.recipe.native." + key).getString();
            for (var child : screen.children())
                if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget && widget.visible && widget.getMessage().getString().equals(text)) {
                    click(widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0);
                    return true;
                }
        }
        return false;
    }

    private static int editorLayout(String name) throws Exception {
        var field = NativeRecipeEditorPage.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(null);
    }

    /** The property field on this row of the editor's right column. */
    private static void clickField(int row) throws Exception {
        click(editorLayout("FIELD_CONTROL_X") + editorLayout("FIELD_CONTROL_W") / 2.0, editorLayout("FIELD_Y") + row * editorLayout("FIELD_PITCH") + 8);
    }

    /** The first recipe of the browser's grid. */
    private static void clickFirstRecipe() throws Exception {
        Object grid = field(KineticGui.currentPage(), "grid");
        int x = (int) grid.getClass().getMethod("controlX").invoke(grid), y = (int) grid.getClass().getMethod("controlY").invoke(grid);
        click(x + 13, y + 13);
    }

    /** The recipe ID field: the text box that holds the editor's current target ID. */
    private static void clickIdField() throws Exception {
        String target = (String) field(field(KineticGui.currentPage(), "session"), "target");
        for (var child : Minecraft.getInstance().screen.children())
            if (child instanceof net.minecraft.client.gui.components.EditBox box && target.equals(box.getValue())) {
                click(box.getX() + 20, box.getY() + box.getHeight() / 2.0);
                return;
            }
        throw new AssertionError("no recipe ID field holds " + target);
    }

    // ------------------------------------------------------------------ server checks
    // The live recipe in the server's recipe manager, never the editor's draft: what the player would craft with.

    private static Recipe<?> recipe(String id) throws Exception {
        var server = Minecraft.getInstance().getSingleplayerServer();
        ResourceLocation key = KineticResourceIds.parse(id);
        //? if >=26.1 {
        /*return server.submit(() -> server.getRecipeManager().byKey(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.RECIPE, key)).map(holder -> (Recipe<?>) holder.value()).orElse(null)).get();
        *///?} else if >=1.21 {
        /*return server.submit(() -> server.getRecipeManager().byKey(key).map(holder -> (Recipe<?>) holder.value()).orElse(null)).get();
        *///?} else {
        return server.submit(() -> server.getRecipeManager().byKey(key).orElse(null)).get();
        //?}
    }

    /** Every loaded recipe, for finding a third-party type to test. Call on the server thread. */
    private static List<Recipe<?>> recipes(net.minecraft.server.MinecraftServer server) {
        var list = new ArrayList<Recipe<?>>();
        //? if >=1.21 {
        /*for (var holder : server.getRecipeManager().getRecipes()) list.add(holder.value());
        *///?} else {
        list.addAll(server.getRecipeManager().getRecipes());
        //?}
        return list;
    }

    private static boolean exists(String id) throws Exception { return recipe(id) != null; }

    //? if >=1.21 {
    /*/^* The live recipe written back with its own codec: the same shape as its datapack JSON, for any mod's type. ^/
    private static com.google.gson.JsonElement liveJson(String id) throws Exception {
        var recipe = recipe(id);
        require(recipe != null, id + " is not loaded");
        var server = Minecraft.getInstance().getSingleplayerServer();
        return server.submit(() -> Recipe.CODEC.encodeStart(net.minecraft.resources.RegistryOps.create(
                com.mojang.serialization.JsonOps.INSTANCE, server.registryAccess()), recipe).getOrThrow()).get();
    }

    /^* Count of the first result: "result", "results", "output" or "outputs", an entry or the first of a list. ^/
    private static int outputCount(String id) throws Exception {
        var json = liveJson(id);
        if (!json.isJsonObject()) return 1;
        for (String key : List.of("result", "results", "output", "outputs")) {
            var value = json.getAsJsonObject().get(key);
            if (value == null) continue;
            if (value.isJsonArray()) { if (value.getAsJsonArray().isEmpty()) continue; value = value.getAsJsonArray().get(0); }
            if (!value.isJsonObject()) return 1;
            var entry = value.getAsJsonObject();
            if (entry.has("item") && entry.get("item").isJsonObject()) entry = entry.getAsJsonObject("item");
            return entry.has("count") ? entry.get("count").getAsInt() : 1;
        }
        throw new AssertionError(id + " has no result in " + json);
    }

    private static boolean takesDiamond(String id) throws Exception {
        return recipe(id) != null && liveJson(id).toString().contains("\"minecraft:diamond\"");
    }
    *///?} else {
    private static int outputCount(String id) throws Exception {
        var recipe = recipe(id);
        require(recipe != null, id + " is not loaded");
        return recipe.getResultItem(Minecraft.getInstance().getSingleplayerServer().registryAccess()).getCount();
    }

    private static boolean takesDiamond(String id) throws Exception {
        var recipe = recipe(id);
        return recipe != null && recipe.getIngredients().stream().anyMatch(ingredient -> ingredient.test(new ItemStack(Items.DIAMOND)));
    }
    //?}

    private static void finish(Throwable failure) {
        finished = true;
        if (mouseX != null) try {
            var mouse = Minecraft.getInstance().mouseHandler;
            mouseX.setDouble(mouse, originalMouseX);
            mouseY.setDouble(mouse, originalMouseY);
        } catch (IllegalAccessException restoreFailure) { LOG.error("RECIPE_PLAYTEST_MOUSE_RESTORE_FAIL", restoreFailure); }
        try {
            var server = Minecraft.getInstance().getSingleplayerServer(); var id = Minecraft.getInstance().player.getUUID();
            server.submit(() -> { var player = server.getPlayerList().getPlayer(id); if (player != null) { player.getAbilities().invulnerable = originalInvulnerable; player.onUpdateAbilities(); } }).get();
        } catch (Exception restoreFailure) { LOG.error("RECIPE_PLAYTEST_RESTORE_FAIL", restoreFailure); }
        if (failure != null) LOG.error("RECIPE_PLAYTEST_FAIL", failure);
        passes.forEach(pass -> LOG.info("RECIPE_PLAYTEST_CHECK {}", pass));
        LOG.info("RECIPE_PLAYTEST_GUI_{} type={} checks={} steps={}/{}", failure == null ? "PASS" : "FAIL", type, passes.size(), index, steps.size());
        KineticGui.closeScreen();
        dev.xyat.kineticcore.api.runtime.KineticClientRuntime.stopClient();
    }

    // ------------------------------------------------------------------ input helpers

    private static void click(double canvasX, double canvasY) {
        var screen = Minecraft.getInstance().screen;
        require(screen instanceof KineticScreenHost, "no Kinetic screen is open");
        var host = (KineticScreenHost) screen;
        clickScreen(host.toScreenX(canvasX), host.toScreenY(canvasY));
    }

    private static void wheel(double canvasX, double canvasY, double delta) {
        var screen = Minecraft.getInstance().screen;
        var host = (KineticScreenHost) screen;
        //? if >=1.21 {
        /*screen.mouseScrolled(host.toScreenX(canvasX), host.toScreenY(canvasY), 0, delta);
        *///?} else {
        screen.mouseScrolled(host.toScreenX(canvasX), host.toScreenY(canvasY), delta);
        //?}
    }

    private static void clickScreen(double x, double y) {
        var screen = Minecraft.getInstance().screen;
        //? if >=26.1 {
        /*var input = new net.minecraft.client.input.MouseButtonEvent(x, y, new net.minecraft.client.input.MouseButtonInfo(0, 0));
        screen.mouseClicked(input, false); screen.mouseReleased(input);
        *///?} else {
        screen.mouseClicked(x, y, 0); screen.mouseReleased(x, y, 0);
        //?}
    }

    private static void typeText(String text) {
        for (char character : text.toCharArray()) {
            //? if >=26.1 {
            /*Minecraft.getInstance().screen.charTyped(new net.minecraft.client.input.CharacterEvent(character));*/
            //?} else {
            Minecraft.getInstance().screen.charTyped(character, 0);
            //?}
        }
    }

    private static void key(int code) {
        //? if >=26.1 {
        /*Minecraft.getInstance().screen.keyPressed(new net.minecraft.client.input.KeyEvent(code, 0, 0));*/
        //?} else {
        Minecraft.getInstance().screen.keyPressed(code, 0, 0);
        //?}
    }

    private static List<?> asList(Object value) {
        if (value instanceof List<?> list) return list;
        if (value instanceof com.google.gson.JsonArray array) { var list = new ArrayList<Object>(); array.forEach(list::add); return list; }
        return List.of();
    }

    private static Object call(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { var method = type.getDeclaredMethod(name); method.setAccessible(true); return method.invoke(object); }
            catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(name);
    }

    private static Object field(Object object, String name) throws Exception {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
