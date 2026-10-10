package dev.xyat.contentstudio.recipe.client;

import dev.xyat.kineticcore.api.text.KineticI18n;
import dev.xyat.kineticcore.api.runtime.KineticPlatform;
import dev.xyat.contentstudio.recipe.removal.RecipeSummary;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public final class RecipeJeiBridge {
    public enum Viewer {
        JEI("jei"),
        EMI("emi"),
        REI("rei");

        private final String id;

        Viewer(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public Component displayName() {
            return KineticI18n.translatable("gui.contentstudio.recipe.removal.viewer." + id);
        }
    }

    public record Entry(RecipeSummary recipe, Component category, boolean removable, Runnable show,
                        Supplier<Preview> preview) {}

    public interface Preview {
        int width();
        int height();
        void draw(KineticGraphics graphics, int mouseX, int mouseY);
        void drawOverlays(KineticGraphics graphics, int mouseX, int mouseY);
        void tick();
    }

    public record EditorSlot(int x, int y, int width, int height, String role,
                             Set<String> ingredients, String name) {
        public EditorSlot { ingredients = Set.copyOf(ingredients); }
        public boolean output() { return role.equals("OUTPUT"); }
        public boolean editable() { return dev.xyat.contentstudio.recipe.nativeedit.JeiRecipeSlotBindings.isEditableRole(role); }
    }

    public interface EditorPreview extends Preview {
        List<EditorSlot> slots();
        Component category();
    }

    public interface Access {
        List<Entry> recipes(ItemStack output);
        void show(ItemStack output);
        default EditorPreview editor(ResourceLocation id, JsonObject recipe) { return null; }
    }

    private static Access access;
    private static long generation;

    private RecipeJeiBridge() {}

    public static void setAccess(Access value) {
        access = value;
        generation++;
    }

    public static boolean available() {
        return !availableViewers().isEmpty();
    }

    public static List<Viewer> availableViewers() {
        List<Viewer> viewers = new ArrayList<>(3);
        if (access != null) viewers.add(Viewer.JEI);
        if (isLoaded("emi") && classPresent("dev.emi.emi.api.EmiApi") && classPresent("dev.emi.emi.api.stack.EmiStack")) {
            viewers.add(Viewer.EMI);
        }
        if (isLoaded("roughlyenoughitems")
                && classPresent("me.shedaniel.rei.api.client.view.ViewSearchBuilder")
                && classPresent("me.shedaniel.rei.api.common.util.EntryStacks")) {
            viewers.add(Viewer.REI);
        }
        return List.copyOf(viewers);
    }

    public static List<Entry> recipes(ItemStack output) {
        return access == null ? List.of() : access.recipes(output);
    }

    public static boolean jeiAvailable() { return access != null; }
    public static long generation() { return generation; }

    /** Optional JEI code is isolated in the plugin; vanilla and field editors never load its classes. */
    public static EditorPreview editor(ResourceLocation id, JsonObject recipe) {
        return access == null ? null : access.editor(id, recipe);
    }

    public static boolean show(Viewer viewer, ItemStack output) {
        if (viewer == null || output == null || output.isEmpty()) return false;
        return switch (viewer) {
            case JEI -> showJei(output);
            case EMI -> showEmi(output);
            case REI -> showRei(output);
        };
    }

    public static boolean show(Viewer viewer, ItemStack output, Entry entry) {
        if (viewer == Viewer.JEI && entry != null && entry.show() != null) {
            try {
                entry.show().run();
                return true;
            } catch (RuntimeException ignored) {
                return false;
            }
        }
        return show(viewer, output);
    }

    private static boolean showJei(ItemStack output) {
        if (access == null) return false;
        try {
            access.show(output);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean showEmi(ItemStack output) {
        if (!isLoaded("emi")) return false;
        try {
            Class<?> emiStackClass = Class.forName("dev.emi.emi.api.stack.EmiStack");
            Method stackFactory = emiStackClass.getMethod("of", ItemStack.class);
            Object emiStack = stackFactory.invoke(null, output.copy());
            Class<?> emiApiClass = Class.forName("dev.emi.emi.api.EmiApi");
            Method displayRecipes = Arrays.stream(emiApiClass.getMethods())
                    .filter(method -> method.getName().equals("displayRecipes"))
                    .filter(method -> Modifier.isStatic(method.getModifiers()))
                    .filter(method -> method.getParameterCount() == 1)
                    .filter(method -> method.getParameterTypes()[0].isInstance(emiStack))
                    .findFirst()
                    .orElse(null);
            if (displayRecipes == null) return false;
            displayRecipes.invoke(null, emiStack);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static boolean showRei(ItemStack output) {
        if (!isLoaded("roughlyenoughitems")) return false;
        try {
            Class<?> entryStacksClass = Class.forName("me.shedaniel.rei.api.common.util.EntryStacks");
            Method stackFactory = entryStacksClass.getMethod("of", ItemStack.class);
            Object entryStack = stackFactory.invoke(null, output.copy());

            Class<?> builderClass = Class.forName("me.shedaniel.rei.api.client.view.ViewSearchBuilder");
            Object builder = builderClass.getMethod("builder").invoke(null);
            Method addRecipesFor = Arrays.stream(builderClass.getMethods())
                    .filter(method -> method.getName().equals("addRecipesFor"))
                    .filter(method -> method.getParameterCount() == 1)
                    .findFirst()
                    .orElse(null);
            if (addRecipesFor == null) return false;

            Class<?> parameterType = addRecipesFor.getParameterTypes()[0];
            Object argument;
            if (parameterType.isArray()) {
                Object array = Array.newInstance(parameterType.getComponentType(), 1);
                Array.set(array, 0, entryStack);
                argument = array;
            } else if (parameterType.isInstance(entryStack)) {
                argument = entryStack;
            } else if (Iterable.class.isAssignableFrom(parameterType)) {
                argument = List.of(entryStack);
            } else {
                return false;
            }

            Object result = addRecipesFor.invoke(builder, argument);
            if (builderClass.isInstance(result)) builder = result;
            builderClass.getMethod("open").invoke(builder);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static boolean isLoaded(String modId) {
        try {
            return KineticPlatform.isModLoaded(modId);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean classPresent(String name) {
        try {
            Class.forName(name, false, RecipeJeiBridge.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }
}
