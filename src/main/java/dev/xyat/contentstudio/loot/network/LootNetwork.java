package dev.xyat.contentstudio.loot.network;

import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.contentstudio.loot.GlobalRemoveRule;
import dev.xyat.contentstudio.loot.LootEntryInfo;
import dev.xyat.contentstudio.loot.LootModule;
import dev.xyat.contentstudio.loot.client.LootClientHandler;
import dev.xyat.contentstudio.loot.server.LootEvents;
import dev.xyat.contentstudio.loot.server.LootTableOverrideStore;
import dev.xyat.kineticcore.api.network.KineticCompression;
import dev.xyat.kineticcore.api.network.NetworkBuffer;
import dev.xyat.kineticcore.api.network.NetworkCodec;
import dev.xyat.kineticcore.api.network.NetworkProtocolLimits;
import dev.xyat.kineticcore.api.network.NetworkVersionPolicy;
import dev.xyat.kineticcore.api.network.PacketChannel;
import dev.xyat.kineticcore.api.network.PacketRegistrations;
import dev.xyat.kineticcore.api.network.ServerPacketContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

public final class LootNetwork {
    private static final String PROTOCOL = "3";
    private static final int MAX_COMPRESSED_BYTES = NetworkProtocolLimits.DEFAULT.maxByteArrayBytes();
    private static final int MAX_DECOMPRESSED_BYTES = KineticCompression.DEFAULT_MAX_DECOMPRESSED_BYTES;
    private static final PacketChannel CHANNEL = PacketChannel.create(
            KineticResourceIds.of(LootModule.MODID, "loots"),
            PROTOCOL,
            NetworkVersionPolicy.ANY
    );
    private static final boolean[] PACKET_REGISTERED = new boolean[17];
    private static boolean registered;

    private LootNetwork() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        LootEvents.register();
        PacketRegistrations.runIndependent(
                () -> registerClientbound(0, OpenScreenPacket.class, (buffer, packet) -> packet.encode(buffer), OpenScreenPacket::decode, OpenScreenPacket::handle),
                () -> registerServerbound(1, RequestDetailPacket.class, (buffer, packet) -> packet.encode(buffer), RequestDetailPacket::decode, RequestDetailPacket::handle),
                () -> registerClientbound(2, DetailPacket.class, (buffer, packet) -> packet.encode(buffer), DetailPacket::decode, DetailPacket::handle),
                () -> registerServerbound(3, RequestResetPreviewPacket.class, (buffer, packet) -> packet.encode(buffer), RequestResetPreviewPacket::decode, RequestResetPreviewPacket::handle),
                () -> registerClientbound(4, ResetPreviewPacket.class, (buffer, packet) -> packet.encode(buffer), ResetPreviewPacket::decode, ResetPreviewPacket::handle),
                () -> registerServerbound(5, SavePacket.class, (buffer, packet) -> packet.encode(buffer), SavePacket::decode, SavePacket::handle),
                () -> registerServerbound(6, ResetPacket.class, (buffer, packet) -> packet.encode(buffer), ResetPacket::decode, ResetPacket::handle),
                () -> registerClientbound(7, SaveResultPacket.class, (buffer, packet) -> packet.encode(buffer), SaveResultPacket::decode, SaveResultPacket::handle),
                () -> registerServerbound(8, RequestGlobalRemovePacket.class, (buffer, packet) -> packet.encode(buffer), RequestGlobalRemovePacket::decode, RequestGlobalRemovePacket::handle),
                () -> registerClientbound(9, GlobalRemoveDetailPacket.class, (buffer, packet) -> packet.encode(buffer), GlobalRemoveDetailPacket::decode, GlobalRemoveDetailPacket::handle),
                () -> registerServerbound(10, SaveGlobalRemovePacket.class, (buffer, packet) -> packet.encode(buffer), SaveGlobalRemovePacket::decode, SaveGlobalRemovePacket::handle),
                () -> registerClientbound(11, GlobalRemoveSaveResultPacket.class, (buffer, packet) -> packet.encode(buffer), GlobalRemoveSaveResultPacket::decode, GlobalRemoveSaveResultPacket::handle),
                () -> registerServerbound(12, RequestGlobalExcludePacket.class, (buffer, packet) -> packet.encode(buffer), RequestGlobalExcludePacket::decode, RequestGlobalExcludePacket::handle),
                () -> registerClientbound(13, GlobalExcludeDetailPacket.class, (buffer, packet) -> packet.encode(buffer), GlobalExcludeDetailPacket::decode, GlobalExcludeDetailPacket::handle),
                () -> registerServerbound(14, SaveGlobalExcludePacket.class, (buffer, packet) -> packet.encode(buffer), SaveGlobalExcludePacket::decode, SaveGlobalExcludePacket::handle),
                () -> registerClientbound(15, GlobalExcludeSaveResultPacket.class, (buffer, packet) -> packet.encode(buffer), GlobalExcludeSaveResultPacket::decode, GlobalExcludeSaveResultPacket::handle),
                () -> registerServerbound(16, RequestOpenScreenPacket.class, (buffer, packet) -> packet.encode(buffer), RequestOpenScreenPacket::decode, RequestOpenScreenPacket::handle),
                () -> registered = allPacketsRegistered()
        );
    }

    private static <T> void registerServerbound(
            int id,
            Class<T> type,
            java.util.function.BiConsumer<NetworkBuffer, T> encoder,
            java.util.function.Function<NetworkBuffer, T> decoder,
            dev.xyat.kineticcore.api.network.ServerboundPacketHandler<T> handler
    ) {
        if (PACKET_REGISTERED[id]) return;
        CHANNEL.registerServerbound(id, type, NetworkCodec.of(encoder, decoder), handler);
        PACKET_REGISTERED[id] = true;
    }

    private static <T> void registerClientbound(
            int id,
            Class<T> type,
            java.util.function.BiConsumer<NetworkBuffer, T> encoder,
            java.util.function.Function<NetworkBuffer, T> decoder,
            java.util.function.Consumer<T> handler
    ) {
        if (PACKET_REGISTERED[id]) return;
        CHANNEL.registerClientbound(id, type, NetworkCodec.of(encoder, decoder), handler);
        PACKET_REGISTERED[id] = true;
    }

    private static boolean allPacketsRegistered() {
        for (boolean value : PACKET_REGISTERED) {
            if (!value) return false;
        }
        return true;
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        if (registered && player != null && packet != null) {
            CHANNEL.sendToPlayer(player, packet);
        }
    }

    public static void sendToServer(Object packet) {
        if (registered && packet != null) {
            CHANNEL.sendToServer(packet);
        }
    }

    public static void requestOpenEditor(int mode) {
        sendToServer(new RequestOpenScreenPacket(mode));
    }

    private static void writeStringList(NetworkBuffer buffer, List<String> values) {
        buffer.writeStringList(values);
    }

    private static List<String> readStringList(NetworkBuffer buffer) {
        return buffer.readStringList();
    }

    private static void writeGlobalRemoveRules(NetworkBuffer buffer, List<GlobalRemoveRule> rules) {
        buffer.writeVarInt(rules.size());
        for (GlobalRemoveRule rule : rules) {
            buffer.writeUtf(rule.itemId());
            buffer.writeEnum(rule.mode());
            writeCompressedString(buffer, rule.nbt());
        }
    }

    private static List<GlobalRemoveRule> readGlobalRemoveRules(NetworkBuffer buffer) {
        int size = buffer.readVarInt();
        List<GlobalRemoveRule> rules = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rules.add(new GlobalRemoveRule(
                    buffer.readUtf(),
                    buffer.readEnum(GlobalRemoveRule.MatchMode.class),
                    readCompressedString(buffer)
            ));
        }
        return rules;
    }

    private static void writeCompressedString(NetworkBuffer buffer, String text) {
        byte[] compressed = KineticCompression.compressUtf8(
                text == null ? "" : text,
                MAX_COMPRESSED_BYTES,
                MAX_DECOMPRESSED_BYTES
        );
        buffer.writeByteArray(compressed, MAX_COMPRESSED_BYTES);
    }

    private static String readCompressedString(NetworkBuffer buffer) {
        return KineticCompression.decompressUtf8(
                buffer.readByteArray(MAX_COMPRESSED_BYTES),
                MAX_DECOMPRESSED_BYTES
        );
    }

    private static boolean isEditorMode(int mode) {
        return mode == LootEntryInfo.MODE_ENTITY
                || mode == LootEntryInfo.MODE_BLOCK
                || mode == LootEntryInfo.MODE_CHEST;
    }

    public record RequestOpenScreenPacket(int mode) {
        public void encode(NetworkBuffer buf) {
            buf.writeVarInt(mode);
        }

        public static RequestOpenScreenPacket decode(NetworkBuffer buf) {
            return new RequestOpenScreenPacket(buf.readVarInt());
        }

        public static void handle(RequestOpenScreenPacket packet, ServerPacketContext context) {
            ServerPlayer player = context.sender();
            if (player != null && player.hasPermissions(2) && isEditorMode(packet.mode)) {
                sendToPlayer(player, new OpenScreenPacket(
                        packet.mode,
                        LootTableOverrideStore.buildEntries(player.server, packet.mode)
                ));
            }
            
        }
    }

    public record OpenScreenPacket(int mode, List<LootEntryInfo> entries) {
        public void encode(NetworkBuffer buf) {
            buf.writeVarInt(mode);
            buf.writeVarInt(entries.size());
            for (LootEntryInfo entry : entries) {
                entry.encode(buf);
            }
        }

        public static OpenScreenPacket decode(NetworkBuffer buf) {
            int mode = buf.readVarInt();
            int size = buf.readVarInt();
            List<LootEntryInfo> entries = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                entries.add(LootEntryInfo.decode(buf));
            }
            return new OpenScreenPacket(mode, entries);
        }

        public static void handle(OpenScreenPacket packet) {
            LootClientHandler.openScreen(packet.mode, packet.entries);
        }
    }

    public record RequestDetailPacket(int mode, String targetId, String lootTableId) {
        public void encode(NetworkBuffer buf) {
            buf.writeVarInt(mode);
            buf.writeUtf(targetId);
            buf.writeUtf(lootTableId);
        }

        public static RequestDetailPacket decode(NetworkBuffer buf) {
            return new RequestDetailPacket(buf.readVarInt(), buf.readUtf(), buf.readUtf());
        }

        public static void handle(RequestDetailPacket packet, ServerPacketContext context) {
            ServerPlayer player = context.sender();
            if (player != null && player.hasPermissions(2)) {
                ResourceLocation lootTableId = KineticResourceIds.tryParse(packet.lootTableId);
                if (lootTableId == null) {
                    
                    return;
                }
                String json = LootTableOverrideStore.readJson(player.server, lootTableId);
                boolean overridden = LootTableOverrideStore.hasOverride(lootTableId);
                sendToPlayer(player, new DetailPacket(packet.mode, packet.targetId, packet.lootTableId, json, overridden));
            }
            
        }
    }

    public record DetailPacket(int mode, String targetId, String lootTableId, String json, boolean overridden) {
        public void encode(NetworkBuffer buf) {
            buf.writeVarInt(mode);
            buf.writeUtf(targetId);
            buf.writeUtf(lootTableId);
            writeCompressedString(buf, json);
            buf.writeBoolean(overridden);
        }

        public static DetailPacket decode(NetworkBuffer buf) {
            return new DetailPacket(buf.readVarInt(), buf.readUtf(), buf.readUtf(), readCompressedString(buf), buf.readBoolean());
        }

        public static void handle(DetailPacket packet) {
            LootClientHandler.applyDetail(packet.mode, packet.targetId, packet.lootTableId, packet.json, packet.overridden);
        }
    }

    public record RequestResetPreviewPacket(int mode, String targetId, String lootTableId) {
        public void encode(NetworkBuffer buf) {
            buf.writeVarInt(mode);
            buf.writeUtf(targetId);
            buf.writeUtf(lootTableId);
        }

        public static RequestResetPreviewPacket decode(NetworkBuffer buf) {
            return new RequestResetPreviewPacket(buf.readVarInt(), buf.readUtf(), buf.readUtf());
        }

        public static void handle(RequestResetPreviewPacket packet, ServerPacketContext context) {
            ServerPlayer player = context.sender();
            if (player != null && player.hasPermissions(2)) {
                ResourceLocation lootTableId = KineticResourceIds.tryParse(packet.lootTableId);
                if (lootTableId != null) {
                    String json = LootTableOverrideStore.previewResetJson(player.server, lootTableId);
                    sendToPlayer(player, new ResetPreviewPacket(packet.mode, packet.targetId, packet.lootTableId, json));
                }
            }
            
        }
    }

    public record ResetPreviewPacket(int mode, String targetId, String lootTableId, String json) {
        public void encode(NetworkBuffer buf) {
            buf.writeVarInt(mode);
            buf.writeUtf(targetId);
            buf.writeUtf(lootTableId);
            writeCompressedString(buf, json);
        }

        public static ResetPreviewPacket decode(NetworkBuffer buf) {
            return new ResetPreviewPacket(buf.readVarInt(), buf.readUtf(), buf.readUtf(), readCompressedString(buf));
        }

        public static void handle(ResetPreviewPacket packet) {
            LootClientHandler.applyResetPreview(packet.mode, packet.targetId, packet.lootTableId, packet.json);
        }
    }

    public record SavePacket(int mode, String targetId, String lootTableId, String json) {
        public void encode(NetworkBuffer buf) {
            buf.writeVarInt(mode);
            buf.writeUtf(targetId);
            buf.writeUtf(lootTableId);
            writeCompressedString(buf, json);
        }

        public static SavePacket decode(NetworkBuffer buf) {
            return new SavePacket(buf.readVarInt(), buf.readUtf(), buf.readUtf(), readCompressedString(buf));
        }

        public static void handle(SavePacket packet, ServerPacketContext context) {
            ServerPlayer player = context.sender();
            ResourceLocation lootTableId = KineticResourceIds.tryParse(packet.lootTableId);
            if (player != null && lootTableId != null && player.hasPermissions(2)) {
                LootTableOverrideStore.SaveResult result = LootTableOverrideStore.save(player.server, lootTableId, packet.json);
                String responseJson = result.success() ? "" : result.json();
                sendToPlayer(player, new SaveResultPacket(packet.mode, packet.targetId, packet.lootTableId, responseJson, result.overridden(), result.success(), result.messageKey()));
            } else if (player != null && lootTableId != null) {
                sendToPlayer(player, new SaveResultPacket(packet.mode, packet.targetId, packet.lootTableId, packet.json, LootTableOverrideStore.hasOverride(lootTableId), false, "msg.contentstudio.loot.loots.no_permission"));
            }
            
        }
    }

    public record ResetPacket(int mode, String targetId, String lootTableId) {
        public void encode(NetworkBuffer buf) {
            buf.writeVarInt(mode);
            buf.writeUtf(targetId);
            buf.writeUtf(lootTableId);
        }

        public static ResetPacket decode(NetworkBuffer buf) {
            return new ResetPacket(buf.readVarInt(), buf.readUtf(), buf.readUtf());
        }

        public static void handle(ResetPacket packet, ServerPacketContext context) {
            ServerPlayer player = context.sender();
            ResourceLocation lootTableId = KineticResourceIds.tryParse(packet.lootTableId);
            if (player != null && lootTableId != null && player.hasPermissions(2)) {
                LootTableOverrideStore.SaveResult result = LootTableOverrideStore.reset(player.server, lootTableId);
                sendToPlayer(player, new SaveResultPacket(packet.mode, packet.targetId, packet.lootTableId, result.json(), result.overridden(), result.success(), result.messageKey()));
            } else if (player != null && lootTableId != null) {
                sendToPlayer(player, new SaveResultPacket(packet.mode, packet.targetId, packet.lootTableId, LootTableOverrideStore.readJson(player.server, lootTableId), LootTableOverrideStore.hasOverride(lootTableId), false, "msg.contentstudio.loot.loots.no_permission"));
            }
            
        }
    }

    public record SaveResultPacket(int mode, String targetId, String lootTableId, String json, boolean overridden, boolean success, String messageKey) {
        public void encode(NetworkBuffer buf) {
            buf.writeVarInt(mode);
            buf.writeUtf(targetId);
            buf.writeUtf(lootTableId);
            writeCompressedString(buf, json);
            buf.writeBoolean(overridden);
            buf.writeBoolean(success);
            buf.writeUtf(messageKey);
        }

        public static SaveResultPacket decode(NetworkBuffer buf) {
            return new SaveResultPacket(buf.readVarInt(), buf.readUtf(), buf.readUtf(), readCompressedString(buf), buf.readBoolean(), buf.readBoolean(), buf.readUtf());
        }

        public static void handle(SaveResultPacket packet) {
            LootClientHandler.applySaveResult(packet.mode, packet.targetId, packet.lootTableId, packet.json, packet.overridden, packet.success, Component.translatable(packet.messageKey));
        }
    }

    public record RequestGlobalRemovePacket() {
        public void encode(NetworkBuffer buf) {
        }

        public static RequestGlobalRemovePacket decode(NetworkBuffer buf) {
            return new RequestGlobalRemovePacket();
        }

        public static void handle(RequestGlobalRemovePacket packet, ServerPacketContext context) {
            ServerPlayer player = context.sender();
            if (player != null && player.hasPermissions(2)) {
                sendToPlayer(player, new GlobalRemoveDetailPacket(LootTableOverrideStore.getGlobalRemovedRules()));
            }
            
        }
    }

    public record GlobalRemoveDetailPacket(List<GlobalRemoveRule> rules) {
        public void encode(NetworkBuffer buf) {
            writeGlobalRemoveRules(buf, rules);
        }

        public static GlobalRemoveDetailPacket decode(NetworkBuffer buf) {
            return new GlobalRemoveDetailPacket(readGlobalRemoveRules(buf));
        }

        public static void handle(GlobalRemoveDetailPacket packet) {
            LootClientHandler.applyGlobalRemoveDetail(packet.rules);
        }
    }

    public record SaveGlobalRemovePacket(List<GlobalRemoveRule> rules) {
        public void encode(NetworkBuffer buf) {
            writeGlobalRemoveRules(buf, rules);
        }

        public static SaveGlobalRemovePacket decode(NetworkBuffer buf) {
            return new SaveGlobalRemovePacket(readGlobalRemoveRules(buf));
        }

        public static void handle(SaveGlobalRemovePacket packet, ServerPacketContext context) {
            ServerPlayer player = context.sender();
            if (player != null && player.hasPermissions(2)) {
                LootTableOverrideStore.GlobalRemoveResult result = LootTableOverrideStore.saveGlobalRemovedItems(player.server, packet.rules);
                sendToPlayer(player, new GlobalRemoveSaveResultPacket(result.rules(), result.success(), result.messageKey()));
            } else if (player != null) {
                sendToPlayer(player, new GlobalRemoveSaveResultPacket(
                        LootTableOverrideStore.getGlobalRemovedRules(),
                        false,
                        "msg.contentstudio.loot.loots.no_permission"
                ));
            }
            
        }
    }

    public record GlobalRemoveSaveResultPacket(List<GlobalRemoveRule> rules, boolean success, String messageKey) {
        public void encode(NetworkBuffer buf) {
            writeGlobalRemoveRules(buf, rules);
            buf.writeBoolean(success);
            buf.writeUtf(messageKey);
        }

        public static GlobalRemoveSaveResultPacket decode(NetworkBuffer buf) {
            return new GlobalRemoveSaveResultPacket(readGlobalRemoveRules(buf), buf.readBoolean(), buf.readUtf());
        }

        public static void handle(GlobalRemoveSaveResultPacket packet) {
            LootClientHandler.applyGlobalRemoveSaveResult(
                    packet.rules,
                    packet.success,
                    Component.translatable(packet.messageKey)
            );
        }
    }


    public record RequestGlobalExcludePacket() {
        public void encode(NetworkBuffer buf) {
        }

        public static RequestGlobalExcludePacket decode(NetworkBuffer buf) {
            return new RequestGlobalExcludePacket();
        }

        public static void handle(RequestGlobalExcludePacket packet, ServerPacketContext context) {
            ServerPlayer player = context.sender();
            if (player != null && player.hasPermissions(2)) {
                sendToPlayer(player, new GlobalExcludeDetailPacket(LootTableOverrideStore.getGlobalExcludedLootTables()));
            }
            
        }
    }

    public record GlobalExcludeDetailPacket(List<String> lootTableIds) {
        public void encode(NetworkBuffer buf) {
            writeStringList(buf, lootTableIds);
        }

        public static GlobalExcludeDetailPacket decode(NetworkBuffer buf) {
            return new GlobalExcludeDetailPacket(readStringList(buf));
        }

        public static void handle(GlobalExcludeDetailPacket packet) {
            LootClientHandler.applyGlobalExcludeDetail(packet.lootTableIds);
        }
    }

    public record SaveGlobalExcludePacket(List<String> lootTableIds) {
        public void encode(NetworkBuffer buf) {
            writeStringList(buf, lootTableIds);
        }

        public static SaveGlobalExcludePacket decode(NetworkBuffer buf) {
            return new SaveGlobalExcludePacket(readStringList(buf));
        }

        public static void handle(SaveGlobalExcludePacket packet, ServerPacketContext context) {
            ServerPlayer player = context.sender();
            if (player != null && player.hasPermissions(2)) {
                LootTableOverrideStore.GlobalExcludeResult result = LootTableOverrideStore.saveGlobalExcludedLootTables(player.server, packet.lootTableIds);
                sendToPlayer(player, new GlobalExcludeSaveResultPacket(result.lootTableIds(), result.success(), result.messageKey()));
            } else if (player != null) {
                sendToPlayer(player, new GlobalExcludeSaveResultPacket(
                        LootTableOverrideStore.getGlobalExcludedLootTables(),
                        false,
                        "msg.contentstudio.loot.loots.no_permission"
                ));
            }
            
        }
    }

    public record GlobalExcludeSaveResultPacket(List<String> lootTableIds, boolean success, String messageKey) {
        public void encode(NetworkBuffer buf) {
            writeStringList(buf, lootTableIds);
            buf.writeBoolean(success);
            buf.writeUtf(messageKey);
        }

        public static GlobalExcludeSaveResultPacket decode(NetworkBuffer buf) {
            return new GlobalExcludeSaveResultPacket(readStringList(buf), buf.readBoolean(), buf.readUtf());
        }

        public static void handle(GlobalExcludeSaveResultPacket packet) {
            LootClientHandler.applyGlobalExcludeSaveResult(
                    packet.lootTableIds,
                    packet.success,
                    Component.translatable(packet.messageKey)
            );
        }
    }


}
