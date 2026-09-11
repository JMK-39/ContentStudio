package dev.xyat.contentstudio.loot.network;

import dev.xyat.kineticcore.api.KTNetworkProtocol;
import dev.xyat.contentstudio.loot.LootModule;
import dev.xyat.contentstudio.loot.LootEntryInfo;
import dev.xyat.contentstudio.loot.GlobalRemoveRule;
import dev.xyat.kineticcore.api.NetworkCompressUtil;
import dev.xyat.contentstudio.loot.client.LootClientHandler;
import dev.xyat.contentstudio.loot.server.LootEvents;
import dev.xyat.contentstudio.loot.server.LootTableOverrideStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class LootNetwork {
    private static final String PROTOCOL = "3";
    public static SimpleChannel CHANNEL;

    private static int packetId = 0;
    private static boolean registered = false;


    public static void register(IEventBus modEventBus) {
        if (registered) {
            return;
        }

        LootEvents.register();

        CHANNEL = NetworkRegistry.ChannelBuilder
                .named(new ResourceLocation(LootModule.MODID, "loots"))
                .networkProtocolVersion(() -> PROTOCOL)
                .clientAcceptedVersions(KTNetworkProtocol::acceptsAnyVersion)
                .serverAcceptedVersions(KTNetworkProtocol::acceptsAnyVersion)
                .simpleChannel();

        CHANNEL.messageBuilder(OpenScreenPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(OpenScreenPacket::encode)
                .decoder(OpenScreenPacket::decode)
                .consumerMainThread(OpenScreenPacket::handle)
                .add();

        CHANNEL.messageBuilder(RequestDetailPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RequestDetailPacket::encode)
                .decoder(RequestDetailPacket::decode)
                .consumerMainThread(RequestDetailPacket::handle)
                .add();

        CHANNEL.messageBuilder(DetailPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(DetailPacket::encode)
                .decoder(DetailPacket::decode)
                .consumerMainThread(DetailPacket::handle)
                .add();

        CHANNEL.messageBuilder(RequestResetPreviewPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RequestResetPreviewPacket::encode)
                .decoder(RequestResetPreviewPacket::decode)
                .consumerMainThread(RequestResetPreviewPacket::handle)
                .add();

        CHANNEL.messageBuilder(ResetPreviewPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ResetPreviewPacket::encode)
                .decoder(ResetPreviewPacket::decode)
                .consumerMainThread(ResetPreviewPacket::handle)
                .add();

        CHANNEL.messageBuilder(SavePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SavePacket::encode)
                .decoder(SavePacket::decode)
                .consumerMainThread(SavePacket::handle)
                .add();

        CHANNEL.messageBuilder(ResetPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ResetPacket::encode)
                .decoder(ResetPacket::decode)
                .consumerMainThread(ResetPacket::handle)
                .add();

        CHANNEL.messageBuilder(SaveResultPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SaveResultPacket::encode)
                .decoder(SaveResultPacket::decode)
                .consumerMainThread(SaveResultPacket::handle)
                .add();

        CHANNEL.messageBuilder(RequestGlobalRemovePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RequestGlobalRemovePacket::encode)
                .decoder(RequestGlobalRemovePacket::decode)
                .consumerMainThread(RequestGlobalRemovePacket::handle)
                .add();

        CHANNEL.messageBuilder(GlobalRemoveDetailPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GlobalRemoveDetailPacket::encode)
                .decoder(GlobalRemoveDetailPacket::decode)
                .consumerMainThread(GlobalRemoveDetailPacket::handle)
                .add();

        CHANNEL.messageBuilder(SaveGlobalRemovePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SaveGlobalRemovePacket::encode)
                .decoder(SaveGlobalRemovePacket::decode)
                .consumerMainThread(SaveGlobalRemovePacket::handle)
                .add();

        CHANNEL.messageBuilder(GlobalRemoveSaveResultPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GlobalRemoveSaveResultPacket::encode)
                .decoder(GlobalRemoveSaveResultPacket::decode)
                .consumerMainThread(GlobalRemoveSaveResultPacket::handle)
                .add();

        CHANNEL.messageBuilder(RequestGlobalExcludePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RequestGlobalExcludePacket::encode)
                .decoder(RequestGlobalExcludePacket::decode)
                .consumerMainThread(RequestGlobalExcludePacket::handle)
                .add();

        CHANNEL.messageBuilder(GlobalExcludeDetailPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GlobalExcludeDetailPacket::encode)
                .decoder(GlobalExcludeDetailPacket::decode)
                .consumerMainThread(GlobalExcludeDetailPacket::handle)
                .add();

        CHANNEL.messageBuilder(SaveGlobalExcludePacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SaveGlobalExcludePacket::encode)
                .decoder(SaveGlobalExcludePacket::decode)
                .consumerMainThread(SaveGlobalExcludePacket::handle)
                .add();

        CHANNEL.messageBuilder(GlobalExcludeSaveResultPacket.class, packetId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(GlobalExcludeSaveResultPacket::encode)
                .decoder(GlobalExcludeSaveResultPacket::decode)
                .consumerMainThread(GlobalExcludeSaveResultPacket::handle)
                .add();

        CHANNEL.messageBuilder(RequestOpenScreenPacket.class, packetId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RequestOpenScreenPacket::encode)
                .decoder(RequestOpenScreenPacket::decode)
                .consumerMainThread(RequestOpenScreenPacket::handle)
                .add();

        registered = true;
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        if (CHANNEL != null && player != null && packet != null) {
            CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }

    public static void sendToServer(Object packet) {
        if (CHANNEL != null && packet != null) {
            CHANNEL.sendToServer(packet);
        }
    }

    public static void requestOpenEditor(int mode) {
        sendToServer(new RequestOpenScreenPacket(mode));
    }

    private static void writeStringList(FriendlyByteBuf buf, List<String> values) {
        buf.writeVarInt(values.size());
        for (String value : values) {
            buf.writeUtf(value);
        }
    }

    private static List<String> readStringList(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<String> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(buf.readUtf());
        }
        return values;
    }

    private static void writeGlobalRemoveRules(FriendlyByteBuf buf, List<GlobalRemoveRule> rules) {
        buf.writeVarInt(rules.size());
        for (GlobalRemoveRule rule : rules) {
            buf.writeUtf(rule.itemId());
            buf.writeEnum(rule.mode());
            writeCompressedString(buf, rule.nbt());
        }
    }

    private static List<GlobalRemoveRule> readGlobalRemoveRules(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<GlobalRemoveRule> rules = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            rules.add(new GlobalRemoveRule(
                    buf.readUtf(),
                    buf.readEnum(GlobalRemoveRule.MatchMode.class),
                    readCompressedString(buf)
            ));
        }
        return rules;
    }

    private static void writeCompressedString(FriendlyByteBuf buf, String text) {
        buf.writeByteArray(NetworkCompressUtil.compress(text == null ? "" : text));
    }

    private static String readCompressedString(FriendlyByteBuf buf) {
        return NetworkCompressUtil.decompress(buf.readByteArray(1048576));
    }

    private static boolean isEditorMode(int mode) {
        return mode == LootEntryInfo.MODE_ENTITY
                || mode == LootEntryInfo.MODE_BLOCK
                || mode == LootEntryInfo.MODE_CHEST;
    }

    public record RequestOpenScreenPacket(int mode) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(mode);
        }

        public static RequestOpenScreenPacket decode(FriendlyByteBuf buf) {
            return new RequestOpenScreenPacket(buf.readVarInt());
        }

        public static void handle(RequestOpenScreenPacket packet, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.hasPermissions(2) && isEditorMode(packet.mode)) {
                sendToPlayer(player, new OpenScreenPacket(
                        packet.mode,
                        LootTableOverrideStore.buildEntries(player.server, packet.mode)
                ));
            }
            ctx.get().setPacketHandled(true);
        }
    }

    public record OpenScreenPacket(int mode, List<LootEntryInfo> entries) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(mode);
            buf.writeVarInt(entries.size());
            for (LootEntryInfo entry : entries) {
                entry.encode(buf);
            }
        }

        public static OpenScreenPacket decode(FriendlyByteBuf buf) {
            int mode = buf.readVarInt();
            int size = buf.readVarInt();
            List<LootEntryInfo> entries = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                entries.add(LootEntryInfo.decode(buf));
            }
            return new OpenScreenPacket(mode, entries);
        }

        public static void handle(OpenScreenPacket packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LootClientHandler.openScreen(packet.mode, packet.entries)));
            ctx.get().setPacketHandled(true);
        }
    }

    public record RequestDetailPacket(int mode, String targetId, String lootTableId) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(mode);
            buf.writeUtf(targetId);
            buf.writeUtf(lootTableId);
        }

        public static RequestDetailPacket decode(FriendlyByteBuf buf) {
            return new RequestDetailPacket(buf.readVarInt(), buf.readUtf(), buf.readUtf());
        }

        public static void handle(RequestDetailPacket packet, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.hasPermissions(2)) {
                ResourceLocation lootTableId = ResourceLocation.tryParse(packet.lootTableId);
                if (lootTableId == null) {
                    ctx.get().setPacketHandled(true);
                    return;
                }
                String json = LootTableOverrideStore.readJson(player.server, lootTableId);
                boolean overridden = LootTableOverrideStore.hasOverride(lootTableId);
                sendToPlayer(player, new DetailPacket(packet.mode, packet.targetId, packet.lootTableId, json, overridden));
            }
            ctx.get().setPacketHandled(true);
        }
    }

    public record DetailPacket(int mode, String targetId, String lootTableId, String json, boolean overridden) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(mode);
            buf.writeUtf(targetId);
            buf.writeUtf(lootTableId);
            writeCompressedString(buf, json);
            buf.writeBoolean(overridden);
        }

        public static DetailPacket decode(FriendlyByteBuf buf) {
            return new DetailPacket(buf.readVarInt(), buf.readUtf(), buf.readUtf(), readCompressedString(buf), buf.readBoolean());
        }

        public static void handle(DetailPacket packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LootClientHandler.applyDetail(packet.mode, packet.targetId, packet.lootTableId, packet.json, packet.overridden)));
            ctx.get().setPacketHandled(true);
        }
    }

    public record RequestResetPreviewPacket(int mode, String targetId, String lootTableId) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(mode);
            buf.writeUtf(targetId);
            buf.writeUtf(lootTableId);
        }

        public static RequestResetPreviewPacket decode(FriendlyByteBuf buf) {
            return new RequestResetPreviewPacket(buf.readVarInt(), buf.readUtf(), buf.readUtf());
        }

        public static void handle(RequestResetPreviewPacket packet, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.hasPermissions(2)) {
                ResourceLocation lootTableId = ResourceLocation.tryParse(packet.lootTableId);
                if (lootTableId != null) {
                    String json = LootTableOverrideStore.previewResetJson(player.server, lootTableId);
                    sendToPlayer(player, new ResetPreviewPacket(packet.mode, packet.targetId, packet.lootTableId, json));
                }
            }
            ctx.get().setPacketHandled(true);
        }
    }

    public record ResetPreviewPacket(int mode, String targetId, String lootTableId, String json) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(mode);
            buf.writeUtf(targetId);
            buf.writeUtf(lootTableId);
            writeCompressedString(buf, json);
        }

        public static ResetPreviewPacket decode(FriendlyByteBuf buf) {
            return new ResetPreviewPacket(buf.readVarInt(), buf.readUtf(), buf.readUtf(), readCompressedString(buf));
        }

        public static void handle(ResetPreviewPacket packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LootClientHandler.applyResetPreview(packet.mode, packet.targetId, packet.lootTableId, packet.json)));
            ctx.get().setPacketHandled(true);
        }
    }

    public record SavePacket(int mode, String targetId, String lootTableId, String json) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(mode);
            buf.writeUtf(targetId);
            buf.writeUtf(lootTableId);
            writeCompressedString(buf, json);
        }

        public static SavePacket decode(FriendlyByteBuf buf) {
            return new SavePacket(buf.readVarInt(), buf.readUtf(), buf.readUtf(), readCompressedString(buf));
        }

        public static void handle(SavePacket packet, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ResourceLocation lootTableId = ResourceLocation.tryParse(packet.lootTableId);
            if (player != null && lootTableId != null && player.hasPermissions(2)) {
                LootTableOverrideStore.SaveResult result = LootTableOverrideStore.save(player.server, lootTableId, packet.json);
                String responseJson = result.success() ? "" : result.json();
                sendToPlayer(player, new SaveResultPacket(packet.mode, packet.targetId, packet.lootTableId, responseJson, result.overridden(), result.success(), result.messageKey()));
            } else if (player != null && lootTableId != null) {
                sendToPlayer(player, new SaveResultPacket(packet.mode, packet.targetId, packet.lootTableId, packet.json, LootTableOverrideStore.hasOverride(lootTableId), false, "msg.contentstudio.loot.loots.no_permission"));
            }
            ctx.get().setPacketHandled(true);
        }
    }

    public record ResetPacket(int mode, String targetId, String lootTableId) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(mode);
            buf.writeUtf(targetId);
            buf.writeUtf(lootTableId);
        }

        public static ResetPacket decode(FriendlyByteBuf buf) {
            return new ResetPacket(buf.readVarInt(), buf.readUtf(), buf.readUtf());
        }

        public static void handle(ResetPacket packet, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            ResourceLocation lootTableId = ResourceLocation.tryParse(packet.lootTableId);
            if (player != null && lootTableId != null && player.hasPermissions(2)) {
                LootTableOverrideStore.SaveResult result = LootTableOverrideStore.reset(player.server, lootTableId);
                sendToPlayer(player, new SaveResultPacket(packet.mode, packet.targetId, packet.lootTableId, result.json(), result.overridden(), result.success(), result.messageKey()));
            } else if (player != null && lootTableId != null) {
                sendToPlayer(player, new SaveResultPacket(packet.mode, packet.targetId, packet.lootTableId, LootTableOverrideStore.readJson(player.server, lootTableId), LootTableOverrideStore.hasOverride(lootTableId), false, "msg.contentstudio.loot.loots.no_permission"));
            }
            ctx.get().setPacketHandled(true);
        }
    }

    public record SaveResultPacket(int mode, String targetId, String lootTableId, String json, boolean overridden, boolean success, String messageKey) {
        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(mode);
            buf.writeUtf(targetId);
            buf.writeUtf(lootTableId);
            writeCompressedString(buf, json);
            buf.writeBoolean(overridden);
            buf.writeBoolean(success);
            buf.writeUtf(messageKey);
        }

        public static SaveResultPacket decode(FriendlyByteBuf buf) {
            return new SaveResultPacket(buf.readVarInt(), buf.readUtf(), buf.readUtf(), readCompressedString(buf), buf.readBoolean(), buf.readBoolean(), buf.readUtf());
        }

        public static void handle(SaveResultPacket packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LootClientHandler.applySaveResult(packet.mode, packet.targetId, packet.lootTableId, packet.json, packet.overridden, packet.success, Component.translatable(packet.messageKey))));
            ctx.get().setPacketHandled(true);
        }
    }

    public record RequestGlobalRemovePacket() {
        public void encode(FriendlyByteBuf buf) {
        }

        public static RequestGlobalRemovePacket decode(FriendlyByteBuf buf) {
            return new RequestGlobalRemovePacket();
        }

        public static void handle(RequestGlobalRemovePacket packet, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.hasPermissions(2)) {
                sendToPlayer(player, new GlobalRemoveDetailPacket(LootTableOverrideStore.getGlobalRemovedRules()));
            }
            ctx.get().setPacketHandled(true);
        }
    }

    public record GlobalRemoveDetailPacket(List<GlobalRemoveRule> rules) {
        public void encode(FriendlyByteBuf buf) {
            writeGlobalRemoveRules(buf, rules);
        }

        public static GlobalRemoveDetailPacket decode(FriendlyByteBuf buf) {
            return new GlobalRemoveDetailPacket(readGlobalRemoveRules(buf));
        }

        public static void handle(GlobalRemoveDetailPacket packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LootClientHandler.applyGlobalRemoveDetail(packet.rules)));
            ctx.get().setPacketHandled(true);
        }
    }

    public record SaveGlobalRemovePacket(List<GlobalRemoveRule> rules) {
        public void encode(FriendlyByteBuf buf) {
            writeGlobalRemoveRules(buf, rules);
        }

        public static SaveGlobalRemovePacket decode(FriendlyByteBuf buf) {
            return new SaveGlobalRemovePacket(readGlobalRemoveRules(buf));
        }

        public static void handle(SaveGlobalRemovePacket packet, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
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
            ctx.get().setPacketHandled(true);
        }
    }

    public record GlobalRemoveSaveResultPacket(List<GlobalRemoveRule> rules, boolean success, String messageKey) {
        public void encode(FriendlyByteBuf buf) {
            writeGlobalRemoveRules(buf, rules);
            buf.writeBoolean(success);
            buf.writeUtf(messageKey);
        }

        public static GlobalRemoveSaveResultPacket decode(FriendlyByteBuf buf) {
            return new GlobalRemoveSaveResultPacket(readGlobalRemoveRules(buf), buf.readBoolean(), buf.readUtf());
        }

        public static void handle(GlobalRemoveSaveResultPacket packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LootClientHandler.applyGlobalRemoveSaveResult(
                    packet.rules,
                    packet.success,
                    Component.translatable(packet.messageKey)
            )));
            ctx.get().setPacketHandled(true);
        }
    }


    public record RequestGlobalExcludePacket() {
        public void encode(FriendlyByteBuf buf) {
        }

        public static RequestGlobalExcludePacket decode(FriendlyByteBuf buf) {
            return new RequestGlobalExcludePacket();
        }

        public static void handle(RequestGlobalExcludePacket packet, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.hasPermissions(2)) {
                sendToPlayer(player, new GlobalExcludeDetailPacket(LootTableOverrideStore.getGlobalExcludedLootTables()));
            }
            ctx.get().setPacketHandled(true);
        }
    }

    public record GlobalExcludeDetailPacket(List<String> lootTableIds) {
        public void encode(FriendlyByteBuf buf) {
            writeStringList(buf, lootTableIds);
        }

        public static GlobalExcludeDetailPacket decode(FriendlyByteBuf buf) {
            return new GlobalExcludeDetailPacket(readStringList(buf));
        }

        public static void handle(GlobalExcludeDetailPacket packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LootClientHandler.applyGlobalExcludeDetail(packet.lootTableIds)));
            ctx.get().setPacketHandled(true);
        }
    }

    public record SaveGlobalExcludePacket(List<String> lootTableIds) {
        public void encode(FriendlyByteBuf buf) {
            writeStringList(buf, lootTableIds);
        }

        public static SaveGlobalExcludePacket decode(FriendlyByteBuf buf) {
            return new SaveGlobalExcludePacket(readStringList(buf));
        }

        public static void handle(SaveGlobalExcludePacket packet, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer player = ctx.get().getSender();
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
            ctx.get().setPacketHandled(true);
        }
    }

    public record GlobalExcludeSaveResultPacket(List<String> lootTableIds, boolean success, String messageKey) {
        public void encode(FriendlyByteBuf buf) {
            writeStringList(buf, lootTableIds);
            buf.writeBoolean(success);
            buf.writeUtf(messageKey);
        }

        public static GlobalExcludeSaveResultPacket decode(FriendlyByteBuf buf) {
            return new GlobalExcludeSaveResultPacket(readStringList(buf), buf.readBoolean(), buf.readUtf());
        }

        public static void handle(GlobalExcludeSaveResultPacket packet, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> LootClientHandler.applyGlobalExcludeSaveResult(
                    packet.lootTableIds,
                    packet.success,
                    Component.translatable(packet.messageKey)
            )));
            ctx.get().setPacketHandled(true);
        }
    }


}
