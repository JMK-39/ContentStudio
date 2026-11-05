package dev.xyat.contentstudio.recipe.compat.jei;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Some mods show their JEI recipes as their own display objects rather than as the recipes themselves, so an edited
 * draft cannot be drawn directly. For those categories the draft recipe is turned into the mod's display object, by
 * reflection so the mods stay optional. An adapter that does not fit the installed mod version returns {@code null},
 * and the editor falls back to its built-in diagram.
 */
final class JeiDisplayAdapters {
    record Adapter(ResourceLocation category, Builder builder) {}

    @FunctionalInterface
    interface Builder {
        Object build(Object recipe) throws ReflectiveOperationException;
    }

    private static final Map<String, Adapter> BY_SERIALIZER = Map.of(
            "irons_spellbooks:alchemist_cauldron_brew",
            new Adapter(dev.xyat.kineticcore.api.resource.KineticResourceIds.of("irons_spellbooks", "alchemist_cauldron"), JeiDisplayAdapters::ironsCauldronBrew),
            "touhou_little_maid:altar_crafting",
            new Adapter(dev.xyat.kineticcore.api.resource.KineticResourceIds.of("touhou_little_maid", "altar"), JeiDisplayAdapters::touhouAltar));

    private JeiDisplayAdapters() {}

    static Adapter of(String serializer) {
        return BY_SERIALIZER.get(serializer);
    }

    /** Iron's Spells brew: reagent, input fluid, result fluids and the optional byproduct, as its JEI record. */
    private static Object ironsCauldronBrew(Object recipe) throws ReflectiveOperationException {
        Object reagent = call(recipe, "reagent"), fluid = call(recipe, "fluidIn"), results = call(recipe, "results");
        Object byproduct = ((Optional<?>) call(recipe, "byproduct")).<Object>map(value -> value).orElse(ItemStack.EMPTY);
        var constructor = Class.forName("io.redspace.ironsspellbooks.jei.AlchemistCauldronJeiRecipe").getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(reagent, fluid, results, byproduct);
    }

    /** Touhou Little Maid altar: the mod's own helper reads the recipe, then the wrapper is built as its JEI plugin does. */
    private static Object touhouAltar(Object recipe) throws ReflectiveOperationException {
        //? if <1.21 {
        var util = Class.forName("com.github.tartaricacid.touhoulittlemaid.util.JERIUtil");
        var makerType = Class.forName("com.github.tartaricacid.touhoulittlemaid.util.JERIUtil$AltarRecipeMaker");
        Object[][] captured = new Object[1][];
        Object maker = Proxy.newProxyInstance(makerType.getClassLoader(), new Class<?>[]{makerType}, (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> "AltarRecipeMaker";
                };
            }
            captured[0] = args;
            return null;
        });
        util.getMethod("recipeWarp", List.class, makerType).invoke(null, List.of(recipe), maker);
        if (captured[0] == null || captured[0].length < 5) return null;
        Object[] row = captured[0];
        List<List<ItemStack>> inputs = ((List<?>) row[1]).stream()
                .map(net.minecraft.world.item.crafting.Ingredient.class::cast)
                .filter(ingredient -> !ingredient.isEmpty())
                .map(ingredient -> List.of(ingredient.getItems()))
                .toList();
        var constructor = Class.forName("com.github.tartaricacid.touhoulittlemaid.compat.jei.altar.AltarRecipeWrapper").getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(inputs, row[2], row[3], row[4]);
        //?} else {
        /*return null;
        *///?}
    }

    private static Object call(Object target, String method) throws ReflectiveOperationException {
        var accessor = target.getClass().getMethod(method);
        accessor.setAccessible(true);
        return accessor.invoke(target);
    }
}
