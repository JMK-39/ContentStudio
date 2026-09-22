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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public final class VillagerNetwork {
    private static final String PROTOCOL_VERSION = "2";
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
    private static final boolean[] PACKET_REGISTERED = new boolean[8];
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
        for (boolean value : PACKET_REGISTERED) {
            if (!value) return false;
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
        VillagerConfig.normalizeTradeLists();
        CHANNEL.sendToServer(new SaveTradeEditorPacket(
                VillagerConfig.villagerTradeGroups,
                VillagerConfig.villagerTradeOffers,
                VillagerConfig.villagerDefaultTradeOverrides,
                VillagerConfig.enableVillagerTradeLateOverride,
                notifySuccess
        ));
    }

    private static void sendTradeEditor(ServerPlayer player) {
        CHANNEL.sendToPlayer(player, new OpenTradeEditorPacket(
                VillagerConfig.villagerTradeGroups,
                VillagerConfig.villagerTradeOffers,
                VillagerConfig.villagerDefaultTradeOverrides,
                VillagerConfig.enableVillagerTradeLateOverride
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
                    success = false;
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

    public static final class OpenTradeEditorPacket {
        private final List<String> groups;
        private final List<String> offers;
        private final List<String> overrides;
        private final boolean lateOverride;

        public OpenTradeEditorPacket() {
            this(
                    VillagerConfig.villagerTradeGroups,
                    VillagerConfig.villagerTradeOffers,
                    VillagerConfig.villagerDefaultTradeOverrides,
                    VillagerConfig.enableVillagerTradeLateOverride
            );
        }

        private OpenTradeEditorPacket(List<String> groups, List<String> offers, List<String> overrides, boolean lateOverride) {
            this.groups = new ArrayList<>(groups);
            this.offers = new ArrayList<>(offers);
            this.overrides = new ArrayList<>(overrides);
            this.lateOverride = lateOverride;
        }

        private void encode(NetworkBuffer buffer) {
            writeStringList(buffer, groups);
            writeStringList(buffer, offers);
            writeStringList(buffer, overrides);
            buffer.writeBoolean(lateOverride);
        }

        public static OpenTradeEditorPacket decode(NetworkBuffer buffer) {
            return new OpenTradeEditorPacket(
                    readStringList(buffer),
                    readStringList(buffer),
                    readStringList(buffer),
                    buffer.readBoolean()
            );
        }

        public static void handle(OpenTradeEditorPacket packet) {
            dev.xyat.contentstudio.villager.client.VillagerClientActions.openTradeEditor(
                    packet.groups,
                    packet.offers,
                    packet.overrides,
                    packet.lateOverride
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
            if (packet.request && context.sender().hasPermissions(2)) {
                sendTradeEditor(context.sender());
            }
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
            boolean success = sender.hasPermissions(2) && VillagerTradeRegistry.applyAndSaveLive(
                    packet.groups,
                    packet.offers,
                    packet.overrides,
                    packet.lateOverride
            );
            CHANNEL.sendToPlayer(sender, new TradeSaveResultPacket(success, packet.notifySuccess));
        }
    }

    public static final class TradeSaveResultPacket {
        private final boolean success;
        private final boolean notifySuccess;

        private TradeSaveResultPacket(boolean success, boolean notifySuccess) {
            this.success = success;
            this.notifySuccess = notifySuccess;
        }

        private void encode(NetworkBuffer buffer) {
            buffer.writeBoolean(success);
            buffer.writeBoolean(notifySuccess);
        }

        public static TradeSaveResultPacket decode(NetworkBuffer buffer) {
            return new TradeSaveResultPacket(buffer.readBoolean(), buffer.readBoolean());
        }

        public static void handle(TradeSaveResultPacket packet) {
            dev.xyat.contentstudio.villager.client.VillagerClientActions.handleTradeSaveResult(
                    packet.success,
                    packet.notifySuccess
            );
        }
    }
}
