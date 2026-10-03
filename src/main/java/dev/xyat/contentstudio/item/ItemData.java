//? if >=1.21 {
/*package dev.xyat.contentstudio.item;
import net.minecraft.world.item.ItemStack;
public final class ItemData {
    private ItemData() {}
    // CustomData's native codec accepts SNBT strings as component values. Use that
    // representation to preserve numeric tag types when a patch is written as JSON.
    public static final com.mojang.serialization.Codec<net.minecraft.core.component.DataComponentPatch> COMPONENT_PATCH_CODEC = new com.mojang.serialization.Codec<>() {
        @Override public <T> com.mojang.serialization.DataResult<com.mojang.datafixers.util.Pair<net.minecraft.core.component.DataComponentPatch,T>> decode(com.mojang.serialization.DynamicOps<T> ops,T input) {
            return net.minecraft.core.component.DataComponentPatch.CODEC.decode(ops,input);
        }
        @Override public <T> com.mojang.serialization.DataResult<T> encode(net.minecraft.core.component.DataComponentPatch patch,com.mojang.serialization.DynamicOps<T> ops,T prefix) {
            var overrides = new java.util.HashMap<String,T>();
            for (var entry : patch.entrySet()) if (entry.getValue().orElse(null) instanceof net.minecraft.world.item.component.CustomData custom) {
                String key = net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(entry.getKey()).toString();
                overrides.put(key,ops.createString(custom.copyTag().toString()));
            }
            return net.minecraft.core.component.DataComponentPatch.CODEC.encode(patch,ops,prefix).flatMap(encoded -> ops.getMap(encoded).map(map -> ops.createMap(map.entries().map(pair -> {
                String key = ops.getStringValue(pair.getFirst()).result().orElse("");
                return overrides.containsKey(key) ? com.mojang.datafixers.util.Pair.of(pair.getFirst(),overrides.get(key)) : pair;
            }))));
        }
    };
    public static ItemStack compile(String id, String data) { return compile(id,data,registries()); }
    public static ItemStack compile(String id, String data, net.minecraft.core.HolderLookup.Provider lookup) {
        String components = data == null || data.isBlank() ? "[]" : data.trim();
        if (!components.startsWith("[")) throw new IllegalArgumentException("Expected [components]");
        var reader = new com.mojang.brigadier.StringReader(id + components);
        try {
            var result = new net.minecraft.commands.arguments.item.ItemParser(lookup).parse(reader);
            if (reader.canRead()) throw new IllegalArgumentException("Trailing item component text");
            return new ItemStack(result.item(), 1, result.components());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException invalid) {
            throw new IllegalArgumentException(invalid.getMessage(), invalid);
        }
    }
    public static boolean hasWorldContext() {
        if (dev.xyat.kineticcore.api.runtime.KineticServerRuntime.currentServer() != null) return true;
        return Boolean.TRUE.equals(dev.xyat.kineticcore.api.runtime.KineticPlatform.callOnClient(() -> () -> dev.xyat.kineticcore.api.runtime.KineticClientRuntime.currentLevel() != null, false));
    }
    public static boolean validConstraint(String id,String data) {
        String text=data==null || data.isBlank() ? "[]" : data.trim();
        if(text.length()>32767 || !text.startsWith("[") || !text.endsWith("]")) return false;
        if(!hasWorldContext()) return net.minecraft.resources.ResourceLocation.tryParse(id) != null;
        try { compile(id,text); return true; } catch(RuntimeException invalid) { return false; }
    }
    public static String format(ItemStack stack) {
        String text = dev.xyat.kineticcore.api.inventory.KineticItemText.format(stack);
        int start = text.indexOf('[');
        return start < 0 ? "[]" : text.substring(start);
    }
    public static net.minecraft.core.HolderLookup.Provider registries() {
        var server = dev.xyat.kineticcore.api.runtime.KineticServerRuntime.currentServer();
        if (server != null) {
            var lookups = new java.util.LinkedHashMap<net.minecraft.resources.ResourceKey<?>, net.minecraft.core.HolderLookup.RegistryLookup<?>>();
            server.registryAccess().listRegistries().forEach(key -> lookups.put(key, server.registryAccess().lookupOrThrow(key)));
            server.reloadableRegistries().get().listRegistries().forEach(key -> lookups.put(key, server.reloadableRegistries().get().lookupOrThrow(key)));
            return net.minecraft.core.HolderLookup.Provider.create(lookups.values().stream());
        }
        var lookup = dev.xyat.kineticcore.api.runtime.KineticPlatform.callOnClient(() -> () -> {
            var level = dev.xyat.kineticcore.api.runtime.KineticClientRuntime.currentLevel();
            return level == null ? null : level.registryAccess();
        }, null);
        return lookup != null ? lookup : net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
    }
    public static net.minecraft.nbt.CompoundTag save(ItemStack stack) { return (net.minecraft.nbt.CompoundTag) stack.saveOptional(registries()); }
    public static ItemStack load(net.minecraft.nbt.CompoundTag saved) { return ItemStack.parseOptional(registries(), saved); }
    public static net.minecraft.nbt.CompoundTag customData(ItemStack stack) { return stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag(); }
    public static void updateCustomData(ItemStack stack, java.util.function.Consumer<net.minecraft.nbt.CompoundTag> action) { net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, action); }
    public static void reset(ItemStack stack) { if (!stack.isEmpty()) ((net.minecraft.core.component.PatchedDataComponentMap) stack.getComponents()).restorePatch(net.minecraft.core.component.DataComponentPatch.EMPTY); }
    public static void edit(String initial, java.util.function.Consumer<String> saved) { edit("minecraft:stone", initial, saved); }
    public static void edit(String id, String initial, java.util.function.Consumer<String> saved) { dev.xyat.kineticcore.api.client.gui.KineticGui.openChild(new ComponentsEditorPage(id, initial, saved)); }
}
*///?}
