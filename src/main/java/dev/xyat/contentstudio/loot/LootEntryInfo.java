package dev.xyat.contentstudio.loot;

import net.minecraft.network.FriendlyByteBuf;

public record LootEntryInfo(int mode, String targetId, String lootTableId, boolean overridden) {
    public static final int MODE_ENTITY = 0;
    public static final int MODE_BLOCK = 1;
    public static final int MODE_CHEST = 2;

    public static final String GLOBAL_CHEST_APPEND_ID = "contentstudio:global/chest_append";
    public static final String GLOBAL_CHEST_REMOVE_ID = "contentstudio:global/chest_remove";
    public static final String GLOBAL_CHEST_EXCLUDE_ID = "contentstudio:global/chest_exclude";

    public boolean isGlobalChestAppend() {
        return GLOBAL_CHEST_APPEND_ID.equals(targetId) && GLOBAL_CHEST_APPEND_ID.equals(lootTableId);
    }

    public boolean isGlobalChestRemove() {
        return GLOBAL_CHEST_REMOVE_ID.equals(targetId) && GLOBAL_CHEST_REMOVE_ID.equals(lootTableId);
    }

    public boolean isGlobalChestExclude() {
        return GLOBAL_CHEST_EXCLUDE_ID.equals(targetId) && GLOBAL_CHEST_EXCLUDE_ID.equals(lootTableId);
    }

    public boolean isGlobalChestEntry() {
        return isGlobalChestAppend() || isGlobalChestRemove() || isGlobalChestExclude();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(mode);
        buf.writeUtf(targetId);
        buf.writeUtf(lootTableId);
        buf.writeBoolean(overridden);
    }

    public static LootEntryInfo decode(FriendlyByteBuf buf) {
        return new LootEntryInfo(buf.readVarInt(), buf.readUtf(), buf.readUtf(), buf.readBoolean());
    }
}
