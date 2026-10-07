package dev.xyat.contentstudio.villager.network;

import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.xyat.contentstudio.villager.VillagerModule;
import dev.xyat.contentstudio.villager.config.VillagerConfig;
import dev.xyat.contentstudio.villager.trade.VillagerTradeRegistry;
import dev.xyat.kineticcore.api.network.KineticCompression;
import dev.xyat.kineticcore.api.network.NetworkBuffer;
import dev.xyat.kineticcore.api.network.NetworkCodec;
import dev.xyat.kineticcore.api.network.NetworkVersionPolicy;
import dev.xyat.kineticcore.api.network.PacketChannel;
import dev.xyat.kineticcore.api.network.PacketRegistrations;
import dev.xyat.kineticcore.api.network.ServerPacketContext;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public final class VillagerNetwork {
    private static final String PROTOCOL_VERSION = "3";
    private static final int MAX_CONFIG_ENTRIES = 8192;
    private static final int MAX_CONFIG_STRING_LENGTH = 32767;
    private static final int MAX_COMPRESSED_BYTES = 2 * 1024 * 1024;
    private static final int MAX_DECOMPRESSED_BYTES = 8 * 1024 * 1024;
    private static final Gson GSON = new Gson();
    private static final Type STRING_LIST_TYPE = new TypeToken<List<String>>() { }.getType();
    private static final PacketChannel CHANNEL = PacketChannel.create(
            KineticResourceIds.of(VillagerModule.MODID, "villager"),
            PROTOCOL_VERSION,
            NetworkVersionPolicy.ANY
    );
    private static final boolean[] PACKET_REGISTERED = new boolean[12];
    private static boolean initialized;

    private VillagerNetwork() {
    }

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        PacketRegistrations.runIndependent(
                () -> registerClientbound(0, OpenTradeEditorPacket.class, (buffer, packet) -> packet.encode(buffer), OpenTradeEditorPacket::decode, OpenTradeEditorPacket::handle),
                () -> registerServerbound(1, RequestTradeEditorPacket.class, (buffer, packet) -> packet.encode(buffer), RequestTradeEditorPacket::decode, RequestTradeEditorPacket::handle),
                () -> registerServerbound(2, SaveTradeEditorPacket.class, (buffer, packet) -> packet.encode(buffer), SaveTradeEditorPacket::decode, SaveTradeEditorPacket::handle),
                () -> registerClientbound(3, TradeSaveResultPacket.class, (buffer, packet) -> packet.encode(buffer), TradeSaveResultPacket::decode, TradeSaveResultPacket::handle),
                () -> registerClientbound(4, OpenFollowItemEditorPacket.class, (buffer, packet) -> packet.encode(buffer), OpenFollowItemEditorPacket::decode, OpenFollowItemEditorPacket::handle),
                () -> registerServerbound(5, RequestFollowItemEditorPacket.class, (buffer, packet) -> packet.encode(buffer), RequestFollowItemEditorPacket::decode, RequestFollowItemEditorPacket::handle),
                () -> registerServerbound(6, SaveFollowItemsPacket.class, (buffer, packet) -> packet.encode(buffer), SaveFollowItemsPacket::decode, SaveFollowItemsPacket::handle),
                () -> registerClientbound(7, FollowItemSaveResultPacket.class, (buffer, packet) -> packet.encode(buffer), FollowItemSaveResultPacket::decode, FollowItemSaveResultPacket::handle),
                //? if >=26.1
                /*() -> registerClientbound(8, VanillaTradePreviewsPacket.class, (buffer, packet) -> packet.encode(buffer), VanillaTradePreviewsPacket::decode, VanillaTradePreviewsPacket::handle),*/
                () -> registerServerbound(9, TradeSourceModePacket.class, (buffer, packet) -> packet.encode(buffer), TradeSourceModePacket::decode, TradeSourceModePacket::handle),
                () -> registerClientbound(10, TradeSourceModeResultPacket.class, (buffer, packet) -> packet.encode(buffer), TradeSourceModeResultPacket::decode, TradeSourceModeResultPacket::handle),
                () -> registerClientbound(11, TradeEditorOpenDeniedPacket.class, (buffer, packet) -> packet.encode(buffer), TradeEditorOpenDeniedPacket::decode, TradeEditorOpenDeniedPacket::handle),
                () -> initialized = allPacketsRegistered()
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
        for (int index = 0; index < PACKET_REGISTERED.length; index++) {
            // Packet 8 is reserved for server-provided trade sets on 26.1.
            //? if <26.1
            if (index == 8) continue;
            if (!PACKET_REGISTERED[index]) return false;
        }
        return true;
    }

    public static void requestTradeEditor() {
        CHANNEL.sendToServer(new RequestTradeEditorPacket());
    }

    public static void requestFollowItemEditor() {
        CHANNEL.sendToServer(new RequestFollowItemEditorPacket());
    }

    public static void saveFollowItems(List<String> items) {
        CHANNEL.sendToServer(new SaveFollowItemsPacket(items));
    }

    public static void saveTradeConfig(boolean notifySuccess) {
        saveTradeConfig(
                VillagerConfig.villagerTradeGroups,
                VillagerConfig.villagerTradeOffers,
                VillagerConfig.villagerDefaultTradeOverrides,
                VillagerConfig.enableVillagerTradeLateOverride,
                notifySuccess
        );
    }

    public static void saveTradeConfig(List<String> groups, List<String> offers, List<String> overrides,
                                       boolean lateOverride, boolean notifySuccess) {
        CHANNEL.sendToServer(new SaveTradeEditorPacket(groups, offers, overrides, lateOverride, notifySuccess));
    }

    public static void saveTradeSourceMode(String mode) {
        CHANNEL.sendToServer(new TradeSourceModePacket(mode));
    }

    private static void sendTradeEditor(ServerPlayer player) {
        // 26.1 clients have no villager trade sets; the editor's vanilla trade previews arrive first.
        //? if >=26.1
        /*CHANNEL.sendToPlayer(player, VanillaTradePreviewsPacket.collect(player));*/
        CHANNEL.sendToPlayer(player, new OpenTradeEditorPacket(
                VillagerTradeRegistry.getAuthoritativeTradeGroups(),
                VillagerTradeRegistry.getAuthoritativeTradeOffers(),
                VillagerTradeRegistry.getAuthoritativeTradeOverrides(),
                VillagerTradeRegistry.isActiveTradeLateOverride(),
                VillagerConfig.getTradeSourceModeValue()
        ));
    }

    private static void sendFollowItemEditor(ServerPlayer player) {
        CHANNEL.sendToPlayer(player, new OpenFollowItemEditorPacket(VillagerConfig.villagerFollowItems));
    }

    private static void writeStringList(NetworkBuffer buffer, List<String> values) {
        List<String> safeValues = values == null ? List.of() : values;
        validateStringList(safeValues);
        byte[] compressed = KineticCompression.compressUtf8(
                GSON.toJson(safeValues),
                MAX_COMPRESSED_BYTES,
                MAX_DECOMPRESSED_BYTES
        );
        buffer.writeByteArray(compressed, MAX_COMPRESSED_BYTES);
    }

    private static List<String> readStringList(NetworkBuffer buffer) {
        String json = KineticCompression.decompressUtf8(
                buffer.readByteArray(MAX_COMPRESSED_BYTES),
                MAX_DECOMPRESSED_BYTES
        );
        List<String> values = GSON.fromJson(json, STRING_LIST_TYPE);
        List<String> safeValues = values == null ? new ArrayList<>() : new ArrayList<>(values);
        validateStringList(safeValues);
        return safeValues;
    }

    private static void validateStringList(List<String> values) {
        if (values.size() > MAX_CONFIG_ENTRIES) {
            throw new IllegalArgumentException("Invalid villager config entry count: " + values.size());
        }
        for (String value : values) {
            if (value == null || value.length() > MAX_CONFIG_STRING_LENGTH) {
                throw new IllegalArgumentException("Invalid villager config entry length");
            }
        }
    }

    public static final class OpenFollowItemEditorPacket {
        private final List<String> items;

        private OpenFollowItemEditorPacket(List<String> items) {
            this.items = items == null ? List.of() : new ArrayList<>(items);
        }

        private void encode(NetworkBuffer buffer) {
            writeStringList(buffer, items);
        }

        public static OpenFollowItemEditorPacket decode(NetworkBuffer buffer) {
            return new OpenFollowItemEditorPacket(readStringList(buffer));
        }

        public static void handle(OpenFollowItemEditorPacket packet) {
            dev.xyat.contentstudio.villager.client.VillagerClientActions.openFollowItemEditor(packet.items);
        }
    }

    public static final class RequestFollowItemEditorPacket {
        private final boolean request;

        public RequestFollowItemEditorPacket() {
            this(true);
        }

        private RequestFollowItemEditorPacket(boolean request) {
            this.request = request;
        }

        private void encode(NetworkBuffer buffer) {
            buffer.writeBoolean(request);
        }

        public static RequestFollowItemEditorPacket decode(NetworkBuffer buffer) {
            return new RequestFollowItemEditorPacket(buffer.readBoolean());
        }

        public static void handle(RequestFollowItemEditorPacket packet, ServerPacketContext context) {
            if (packet.request && context.sender().hasPermissions(2)) {
                sendFollowItemEditor(context.sender());
            }
        }
    }

    public static final class SaveFollowItemsPacket {
        private final List<String> items;

        private SaveFollowItemsPacket(List<String> items) {
            this.items = items == null ? List.of() : new ArrayList<>(items);
        }

        private void encode(NetworkBuffer buffer) {
            writeStringList(buffer, items);
        }

        public static SaveFollowItemsPacket decode(NetworkBuffer buffer) {
            return new SaveFollowItemsPacket(readStringList(buffer));
        }

        public static void handle(SaveFollowItemsPacket packet, ServerPacketContext context) {
            ServerPlayer sender = context.sender();
            boolean success = false;
            if (sender.hasPermissions(2) && VillagerConfig.areValidFollowItems(packet.items)) {
                try {
                    VillagerConfig.villagerFollowItems = new ArrayList<>(packet.items);
                    VillagerConfig.save();
                    success = true;
                } catch (Throwable ignored) {
                }
            }
            CHANNEL.sendToPlayer(sender, new FollowItemSaveResultPacket(success));
        }
    }

    public record FollowItemSaveResultPacket(boolean success) {
        private void encode(NetworkBuffer buffer) {
            buffer.writeBoolean(success);
        }

        public static FollowItemSaveResultPacket decode(NetworkBuffer buffer) {
            return new FollowItemSaveResultPacket(buffer.readBoolean());
        }

        public static void handle(FollowItemSaveResultPacket packet) {
            dev.xyat.contentstudio.villager.client.VillagerClientActions.handleFollowItemSaveResult(packet.success);
        }
    }

    //? if >=26.1 {
    /*/^* The vanilla trade previews of every owner and level, each offer as NBT written with the server registries. ^/
    public record VanillaTradePreviewsPacket(java.util.Map<String, List<List<net.minecraft.nbt.CompoundTag>>> previews) {
        private static final int MAX_OWNERS = 1024;
        private static final int MAX_OFFERS = 256;

        static VanillaTradePreviewsPacket collect(ServerPlayer player) {
            var ops = player.level().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
            java.util.Map<String, List<List<net.minecraft.nbt.CompoundTag>>> encoded = new java.util.LinkedHashMap<>();
            dev.xyat.contentstudio.villager.util.VillagerTradeRuntimeUtil.collectPreviews().forEach((owner, levels) -> {
                List<List<net.minecraft.nbt.CompoundTag>> encodedLevels = new ArrayList<>();
                for (var offers : levels) {
                    List<net.minecraft.nbt.CompoundTag> encodedOffers = new ArrayList<>();
                    for (var offer : offers) {
                        encodedOffers.add(offer == null ? new net.minecraft.nbt.CompoundTag()
                                : net.minecraft.world.item.trading.MerchantOffer.CODEC.encodeStart(ops, offer).result()
                                .filter(net.minecraft.nbt.CompoundTag.class::isInstance)
                                .map(net.minecraft.nbt.CompoundTag.class::cast)
                                .orElseGet(net.minecraft.nbt.CompoundTag::new));
                    }
                    encodedLevels.add(encodedOffers);
                }
                encoded.put(owner, encodedLevels);
            });
            return new VanillaTradePreviewsPacket(encoded);
        }

        private void encode(NetworkBuffer buffer) {
            buffer.writeVarInt(previews.size());
            previews.forEach((owner, levels) -> {
                buffer.writeUtf(owner);
                buffer.writeVarInt(levels.size());
                for (var offers : levels) {
                    buffer.writeVarInt(offers.size());
                    offers.forEach(buffer::writeNbt);
                }
            });
        }

        public static VanillaTradePreviewsPacket decode(NetworkBuffer buffer) {
            int owners = buffer.readVarInt();
            if (owners < 0 || owners > MAX_OWNERS) throw new IllegalArgumentException("Invalid trade preview owner count");
            java.util.Map<String, List<List<net.minecraft.nbt.CompoundTag>>> previews = new java.util.LinkedHashMap<>();
            for (int i = 0; i < owners; i++) {
                String owner = buffer.readUtf();
                int levels = buffer.readVarInt();
                if (levels < 0 || levels > 5) throw new IllegalArgumentException("Invalid trade preview level count");
                List<List<net.minecraft.nbt.CompoundTag>> levelOffers = new ArrayList<>();
                for (int level = 0; level < levels; level++) {
                    int count = buffer.readVarInt();
                    if (count < 0 || count > MAX_OFFERS) throw new IllegalArgumentException("Invalid trade preview count");
                    List<net.minecraft.nbt.CompoundTag> offers = new ArrayList<>();
                    for (int offer = 0; offer < count; offer++) offers.add(buffer.readNbt());
                    levelOffers.add(offers);
                }
                previews.put(owner, levelOffers);
            }
            return new VanillaTradePreviewsPacket(previews);
        }

        // An empty compound stands for a trade that could not build an offer; it decodes to null.
        public static void handle(VanillaTradePreviewsPacket packet) {
            var ops = dev.xyat.contentstudio.item.ItemData.registries().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
            java.util.Map<String, List<List<net.minecraft.world.item.trading.MerchantOffer>>> previews = new java.util.LinkedHashMap<>();
            packet.previews.forEach((owner, levels) -> {
                List<List<net.minecraft.world.item.trading.MerchantOffer>> decodedLevels = new ArrayList<>();
                for (var offers : levels) {
                    List<net.minecraft.world.item.trading.MerchantOffer> decoded = new ArrayList<>();
                    for (var tag : offers) {
                        decoded.add(tag == null || tag.isEmpty() ? null
                                : net.minecraft.world.item.trading.MerchantOffer.CODEC.parse(ops, tag).result().orElse(null));
                    }
                    decodedLevels.add(java.util.Collections.unmodifiableList(decoded));
                }
                previews.put(owner, List.copyOf(decodedLevels));
            });
            dev.xyat.contentstudio.villager.util.VillagerTradeRuntimeUtil.setRemotePreviews(previews);
        }
    }

    *///?}
    public static final class OpenTradeEditorPacket {
        private final List<String> groups;
        private final List<String> offers;
        private final List<String> overrides;
        private final boolean lateOverride;
        private final String sourceMode;

        public OpenTradeEditorPacket() {
            this(
                    VillagerConfig.villagerTradeGroups,
                    VillagerConfig.villagerTradeOffers,
                    VillagerConfig.villagerDefaultTradeOverrides,
                    VillagerConfig.enableVillagerTradeLateOverride,
                    VillagerConfig.getTradeSourceModeValue()
            );
        }

        private OpenTradeEditorPacket(List<String> groups, List<String> offers, List<String> overrides, boolean lateOverride, String sourceMode) {
            this.groups = new ArrayList<>(groups);
            this.offers = new ArrayList<>(offers);
            this.overrides = new ArrayList<>(overrides);
            this.lateOverride = lateOverride;
            this.sourceMode = sourceMode;
        }

        private void encode(NetworkBuffer buffer) {
            writeStringList(buffer, groups);
            writeStringList(buffer, offers);
            writeStringList(buffer, overrides);
            buffer.writeBoolean(lateOverride);
            buffer.writeUtf(sourceMode, 32);
        }

        public static OpenTradeEditorPacket decode(NetworkBuffer buffer) {
            return new OpenTradeEditorPacket(
                    readStringList(buffer),
                    readStringList(buffer),
                    readStringList(buffer),
                    buffer.readBoolean(),
                    buffer.readUtf(32)
            );
        }

        public static void handle(OpenTradeEditorPacket packet) {
            dev.xyat.contentstudio.villager.client.VillagerClientActions.openTradeEditor(
                    packet.groups,
                    packet.offers,
                    packet.overrides,
                    packet.lateOverride,
                    packet.sourceMode
            );
        }
    }

    public static final class RequestTradeEditorPacket {
        private final boolean request;

        public RequestTradeEditorPacket() {
            this(true);
        }

        private RequestTradeEditorPacket(boolean request) {
            this.request = request;
        }

        private void encode(NetworkBuffer buffer) {
            buffer.writeBoolean(request);
        }

        public static RequestTradeEditorPacket decode(NetworkBuffer buffer) {
            return new RequestTradeEditorPacket(buffer.readBoolean());
        }

        public static void handle(RequestTradeEditorPacket packet, ServerPacketContext context) {
            if (!packet.request) return;
            ServerPlayer sender = context.sender();
            if (!sender.hasPermissions(2)) {
                CHANNEL.sendToPlayer(sender, new TradeEditorOpenDeniedPacket("permission_denied"));
            } else if (VillagerTradeRegistry.isSessionUnavailable()) {
                CHANNEL.sendToPlayer(sender, new TradeEditorOpenDeniedPacket("server_unavailable"));
            } else sendTradeEditor(sender);
        }
    }

    public static final class SaveTradeEditorPacket {
        private final List<String> groups;
        private final List<String> offers;
        private final List<String> overrides;
        private final boolean lateOverride;
        private final boolean notifySuccess;

        private SaveTradeEditorPacket(
                List<String> groups,
                List<String> offers,
                List<String> overrides,
                boolean lateOverride,
                boolean notifySuccess
        ) {
            this.groups = new ArrayList<>(groups);
            this.offers = new ArrayList<>(offers);
            this.overrides = new ArrayList<>(overrides);
            this.lateOverride = lateOverride;
            this.notifySuccess = notifySuccess;
        }

        private void encode(NetworkBuffer buffer) {
            writeStringList(buffer, groups);
            writeStringList(buffer, offers);
            writeStringList(buffer, overrides);
            buffer.writeBoolean(lateOverride);
            buffer.writeBoolean(notifySuccess);
        }

        public static SaveTradeEditorPacket decode(NetworkBuffer buffer) {
            return new SaveTradeEditorPacket(
                    readStringList(buffer),
                    readStringList(buffer),
                    readStringList(buffer),
                    buffer.readBoolean(),
                    buffer.readBoolean()
            );
        }

        public static void handle(SaveTradeEditorPacket packet, ServerPacketContext context) {
            ServerPlayer sender = context.sender();
            var outcome = !sender.hasPermissions(2)
                    ? VillagerTradeRegistry.TradeSaveOutcome.failure("permission_denied")
                    : VillagerTradeRegistry.applyAndSaveLiveDetailed(
                    packet.groups,
                    packet.offers,
                    packet.overrides,
                    packet.lateOverride
            );
            CHANNEL.sendToPlayer(sender, new TradeSaveResultPacket(outcome.success(), packet.notifySuccess, outcome.failureCode()));
        }
    }

    public static final class TradeSaveResultPacket {
        private final boolean success;
        private final boolean notifySuccess;
        private final String failureCode;

        private TradeSaveResultPacket(boolean success, boolean notifySuccess, String failureCode) {
            this.success = success;
            this.notifySuccess = notifySuccess;
            this.failureCode = failureCode == null ? "unknown" : failureCode;
        }

        private void encode(NetworkBuffer buffer) {
            buffer.writeBoolean(success);
            buffer.writeBoolean(notifySuccess);
            buffer.writeUtf(failureCode, 64);
        }

        public static TradeSaveResultPacket decode(NetworkBuffer buffer) {
            return new TradeSaveResultPacket(buffer.readBoolean(), buffer.readBoolean(), buffer.readUtf(64));
        }

        public static void handle(TradeSaveResultPacket packet) {
            dev.xyat.contentstudio.villager.client.VillagerClientActions.handleTradeSaveResult(
                    packet.success,
                    packet.notifySuccess,
                    packet.failureCode
            );
        }
    }

    public record TradeSourceModePacket(String mode) {
        private void encode(NetworkBuffer buffer) { buffer.writeUtf(mode, 32); }
        public static TradeSourceModePacket decode(NetworkBuffer buffer) { return new TradeSourceModePacket(buffer.readUtf(32)); }
        public static void handle(TradeSourceModePacket packet, ServerPacketContext context) {
            ServerPlayer sender = context.sender();
            var outcome = sender.hasPermissions(2)
                    ? VillagerTradeRegistry.applyAndSaveTradeSourceMode(packet.mode)
                    : VillagerTradeRegistry.TradeSaveOutcome.failure("permission_denied");
            CHANNEL.sendToPlayer(sender, new TradeSourceModeResultPacket(outcome.success(),
                    VillagerConfig.getTradeSourceModeValue(), outcome.failureCode()));
        }
    }

    public record TradeSourceModeResultPacket(boolean success, String mode, String failureCode) {
        private void encode(NetworkBuffer buffer) {
            buffer.writeBoolean(success);
            buffer.writeUtf(mode, 32);
            buffer.writeUtf(failureCode, 64);
        }
        public static TradeSourceModeResultPacket decode(NetworkBuffer buffer) {
            return new TradeSourceModeResultPacket(buffer.readBoolean(), buffer.readUtf(32), buffer.readUtf(64));
        }
        public static void handle(TradeSourceModeResultPacket packet) {
            dev.xyat.contentstudio.villager.client.VillagerClientActions.handleTradeSourceModeResult(packet.success, packet.mode, packet.failureCode);
        }
    }

    public record TradeEditorOpenDeniedPacket(String failureCode) {
        private void encode(NetworkBuffer buffer) { buffer.writeUtf(failureCode, 64); }
        public static TradeEditorOpenDeniedPacket decode(NetworkBuffer buffer) { return new TradeEditorOpenDeniedPacket(buffer.readUtf(64)); }
        public static void handle(TradeEditorOpenDeniedPacket packet) {
            dev.xyat.contentstudio.villager.client.VillagerClientActions.handleTradeEditorOpenDenied(packet.failureCode);
        }
    }
}
