package dev.xyat.contentstudio.loot;

import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record GlobalRemoveRule(String itemId, MatchMode mode, String nbt) {
    public enum MatchMode {
        ITEM("item"),
        NBT_PRESENT("nbt_present"),
        NBT_FUZZY("nbt_fuzzy"),
        NBT_EXACT("nbt_exact");

        private final String id;

        MatchMode(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static MatchMode fromId(String value) {
            if (value == null || value.isBlank()) {
                return ITEM;
            }
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            for (MatchMode mode : values()) {
                if (mode.id.equals(normalized)) {
                    return mode;
                }
            }
            return ITEM;
        }
    }

    public GlobalRemoveRule {
        itemId = itemId == null ? "" : itemId.trim();
        mode = mode == null ? MatchMode.ITEM : mode;
        nbt = normalizeNbt(nbt);
    }

    public static GlobalRemoveRule item(String itemId) {
        return new GlobalRemoveRule(itemId, MatchMode.ITEM, "");
    }

    public static GlobalRemoveRule fromStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return new GlobalRemoveRule("", MatchMode.ITEM, "");
        }
        ResourceLocation id = KineticRegistries.items().id(stack.getItem());
        String nbt = stack.hasTag() && stack.getTag() != null && !stack.getTag().isEmpty()
                ? stack.getTag().toString()
                : "";
        MatchMode mode = nbt.isBlank() ? MatchMode.ITEM : MatchMode.NBT_EXACT;
        return new GlobalRemoveRule(id == null ? "" : id.toString(), mode, nbt);
    }

    public GlobalRemoveRule withMode(MatchMode newMode) {
        return new GlobalRemoveRule(itemId, newMode, nbt);
    }

    public GlobalRemoveRule withNbt(String newNbt) {
        return new GlobalRemoveRule(itemId, mode, newNbt);
    }

    public ResourceLocation itemResourceLocation() {
        return KineticResourceIds.tryParse(itemId);
    }

    public boolean hasConfiguredNbt() {
        return !nbt.isBlank();
    }

    public CompoundTag parseConfiguredNbt() {
        if (nbt.isBlank()) {
            return new CompoundTag();
        }
        try {
            return TagParser.parseTag(nbt);
        } catch (Exception ignored) {
            return new CompoundTag();
        }
    }

    public boolean matches(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation expectedId = itemResourceLocation();
        ResourceLocation actualId = KineticRegistries.items().id(stack.getItem());
        if (expectedId == null || !expectedId.equals(actualId)) {
            return false;
        }
        CompoundTag actual = stack.getTag();
        return switch (mode) {
            case ITEM -> true;
            case NBT_PRESENT -> actual != null && !actual.isEmpty();
            case NBT_FUZZY -> {
                if (nbt.isBlank()) {
                    yield actual != null && !actual.isEmpty();
                }
                CompoundTag expected = parseConfiguredNbt();
                yield actual != null && tagContains(actual, expected);
            }
            case NBT_EXACT -> {
                CompoundTag expected = parseConfiguredNbt();
                CompoundTag normalizedActual = actual == null ? new CompoundTag() : actual;
                yield Objects.equals(normalizedActual, expected);
            }
        };
    }

    public boolean isValid() {
        ResourceLocation id = itemResourceLocation();
        if (id == null || !KineticRegistries.items().contains(id)) {
            return false;
        }
        if (nbt.isBlank()) {
            return true;
        }
        try {
            TagParser.parseTag(nbt);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static boolean tagContains(Tag actual, Tag expected) {
        if (actual == null || expected == null) {
            return actual == expected;
        }
        if (actual instanceof CompoundTag actualCompound && expected instanceof CompoundTag expectedCompound) {
            for (String key : expectedCompound.getAllKeys()) {
                if (!actualCompound.contains(key) || !tagContains(actualCompound.get(key), expectedCompound.get(key))) {
                    return false;
                }
            }
            return true;
        }
        if (actual instanceof ListTag actualList && expected instanceof ListTag expectedList) {
            if (expectedList.isEmpty()) {
                return true;
            }
            if (actualList.size() < expectedList.size()) {
                return false;
            }
            Set<Integer> used = new HashSet<>();
            for (int expectedIndex = 0; expectedIndex < expectedList.size(); expectedIndex++) {
                Tag expectedChild = expectedList.get(expectedIndex);
                boolean found = false;
                for (int actualIndex = 0; actualIndex < actualList.size(); actualIndex++) {
                    if (used.contains(actualIndex)) {
                        continue;
                    }
                    if (tagContains(actualList.get(actualIndex), expectedChild)) {
                        used.add(actualIndex);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    return false;
                }
            }
            return true;
        }
        return actual.equals(expected);
    }

    private static String normalizeNbt(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return "{}".equals(trimmed) ? "" : trimmed;
    }
}
