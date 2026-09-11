package dev.xyat.contentstudio.villager.network;

import dev.xyat.kineticcore.api.KTNetworkProtocol;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.xyat.kineticcore.api.NetworkCompressUtil;
import dev.xyat.contentstudio.villager.VillagerModule;
import dev.xyat.contentstudio.villager.config.VillagerConfig;
import dev.xyat.contentstudio.villager.trade.VillagerTradeRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.nio.charset.StandardCharsets;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class VillagerNetwork {
    private static final String PROTOCOL_VERSION = "2";
    private static final int MAX_CONFIG_ENTRIES = 8192;
    private static final int MAX_CONFIG_STRING_LENGTH = 32767;
    private static final int MAX_COMPRESSED_BYTES = 2 * 1024 * 1024;
    private static final int MAX_DECOMPRESSED_BYTES = 8 * 1024 * 1024;
    private static final Gson GSON = new Gson();
    private static final Type STRING_LIST_TYPE = new TypeToken<List<String>>() { }.getType();

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(VillagerModule.MODID, "villager"),
            () -> PROTOCOL_VERSION,
            KTNetworkProtocol::acceptsAnyVersion,
            KTNetworkProtocol::acceptsAnyVersion
    );

    private static int packetId;
    private static boolean initialized;

    private VillagerNetwork() {
    }

    public static void init() {
        if (initialized) {
            return;
        }

        synchronized (VillagerNetwork.class) {
            if (initialized) {
                return;
            }

            CHANNEL.registerMessage(packetId++, OpenTradeEditorPacket.class, OpenTradeEditorPacket::encode, OpenTradeEditorPacket::decode, OpenTradeEditorPacket::handle);
            CHANNEL.registerMessage(packetId++, RequestTradeEditorPacket.class, RequestTradeEditorPacket::encode, RequestTradeEditorPacket::decode, RequestTradeEditorPacket::handle);
            CHANNEL.registerMessage(packetId++, SaveTradeEditorPacket.class, SaveTradeEditorPacket::encode, SaveTradeEditorPacket::decode, SaveTradeEditorPacket::handle);
            CHANNEL.registerMessage(packetId++, TradeSaveResultPacket.class, TradeSaveResultPacket::encode, TradeSaveResultPacket::decode, TradeSaveResultPacket::handle);
            CHANNEL.registerMessage(packetId++, OpenFollowItemEditorPacket.class, OpenFollowItemEditorPacket::encode, OpenFollowItemEditorPacket::decode, OpenFollowItemEditorPacket::handle);
            CHANNEL.registerMessage(packetId++, RequestFollowItemEditorPacket.class, RequestFollowItemEditorPacket::encode, RequestFollowItemEditorPacket::decode, RequestFollowItemEditorPacket::handle);
            CHANNEL.registerMessage(packetId++, SaveFollowItemsPacket.class, SaveFollowItemsPacket::encode, SaveFollowItemsPacket::decode, SaveFollowItemsPacket::handle);
            CHANNEL.registerMessage(packetId++, FollowItemSaveResultPacket.class, FollowItemSaveResultPacket::encode, FollowItemSaveResultPacket::decode, FollowItemSaveResultPacket::handle);
            initialized = true;
        }
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
                notifySuccess
        ));
    }

    private static void sendTradeEditor(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenTradeEditorPacket(
                VillagerConfig.villagerTradeGroups,
                VillagerConfig.villagerTradeOffers,
                VillagerConfig.villagerDefaultTradeOverrides
        ));
    }

    private static void sendFollowItemEditor(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new OpenFollowItemEditorPacket(
                VillagerConfig.villagerFollowItems
        ));
    }

    private static void writeStringList(FriendlyByteBuf buffer, List<String> values) {
        List<String> safeValues = values == null ? List.of() : values;
        validateStringList(safeValues);
        byte[] compressed = NetworkCompressUtil.compress(GSON.toJson(safeValues));
        if (compressed.length > MAX_COMPRESSED_BYTES) {
            throw new IllegalArgumentException("Villager config payload exceeds compressed limit");
        }
        buffer.writeByteArray(compressed);
    }

    private static List<String> readStringList(FriendlyByteBuf buffer) {
        byte[] compressed = buffer.readByteArray(MAX_COMPRESSED_BYTES);
        String json = new String(
                NetworkCompressUtil.decompressBytes(compressed, MAX_DECOMPRESSED_BYTES),
                StandardCharsets.UTF_8
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

        public static void encode(OpenFollowItemEditorPacket packet, FriendlyByteBuf buffer) {
            writeStringList(buffer, packet.items);
        }

        public static OpenFollowItemEditorPacket decode(FriendlyByteBuf buffer) {
            return new OpenFollowItemEditorPacket(readStringList(buffer));
        }

        public static void handle(OpenFollowItemEditorPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    dev.xyat.contentstudio.villager.client.VillagerClientActions.openFollowItemEditor(packet.items)
            ));
            context.setPacketHandled(true);
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

        public static void encode(RequestFollowItemEditorPacket packet, FriendlyByteBuf buffer) {
            buffer.writeBoolean(packet.request);
        }

        public static RequestFollowItemEditorPacket decode(FriendlyByteBuf buffer) {
            return new RequestFollowItemEditorPacket(buffer.readBoolean());
        }

        public static void handle(RequestFollowItemEditorPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            ServerPlayer sender = context.getSender();
            if (packet.request && sender != null) {
                context.enqueueWork(() -> {
                    if (sender.hasPermissions(2)) {
                        sendFollowItemEditor(sender);
                    }
                });
            }
            context.setPacketHandled(true);
        }
    }

    public static final class SaveFollowItemsPacket {
        private final List<String> items;

        private SaveFollowItemsPacket(List<String> items) {
            this.items = items == null ? List.of() : new ArrayList<>(items);
        }

        public static void encode(SaveFollowItemsPacket packet, FriendlyByteBuf buffer) {
            writeStringList(buffer, packet.items);
        }

        public static SaveFollowItemsPacket decode(FriendlyByteBuf buffer) {
            return new SaveFollowItemsPacket(readStringList(buffer));
        }

        public static void handle(SaveFollowItemsPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                context.setPacketHandled(true);
                return;
            }

            context.enqueueWork(() -> {
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
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender), new FollowItemSaveResultPacket(success));
            });
            context.setPacketHandled(true);
        }
    }

    public record FollowItemSaveResultPacket(boolean success) {
        public static void encode(FollowItemSaveResultPacket packet, FriendlyByteBuf buffer) {
            buffer.writeBoolean(packet.success);
        }

        public static FollowItemSaveResultPacket decode(FriendlyByteBuf buffer) {
            return new FollowItemSaveResultPacket(buffer.readBoolean());
        }

        public static void handle(FollowItemSaveResultPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    dev.xyat.contentstudio.villager.client.VillagerClientActions.handleFollowItemSaveResult(packet.success)
            ));
            context.setPacketHandled(true);
        }
    }

    public static final class OpenTradeEditorPacket {
        private final List<String> groups;
        private final List<String> offers;
        private final List<String> overrides;

        public OpenTradeEditorPacket() {
            this(
                    VillagerConfig.villagerTradeGroups,
                    VillagerConfig.villagerTradeOffers,
                    VillagerConfig.villagerDefaultTradeOverrides
            );
        }

        private OpenTradeEditorPacket(List<String> groups, List<String> offers, List<String> overrides) {
            this.groups = new ArrayList<>(groups);
            this.offers = new ArrayList<>(offers);
            this.overrides = new ArrayList<>(overrides);
        }

        public static void encode(OpenTradeEditorPacket packet, FriendlyByteBuf buffer) {
            writeStringList(buffer, packet.groups);
            writeStringList(buffer, packet.offers);
            writeStringList(buffer, packet.overrides);
        }

        public static OpenTradeEditorPacket decode(FriendlyByteBuf buffer) {
            return new OpenTradeEditorPacket(
                    readStringList(buffer),
                    readStringList(buffer),
                    readStringList(buffer)
            );
        }

        public static void handle(OpenTradeEditorPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    dev.xyat.contentstudio.villager.client.VillagerClientActions.openTradeEditor(
                            packet.groups,
                            packet.offers,
                            packet.overrides
                    )
            ));
            context.setPacketHandled(true);
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

        public static void encode(RequestTradeEditorPacket packet, FriendlyByteBuf buffer) {
            buffer.writeBoolean(packet.request);
        }

        public static RequestTradeEditorPacket decode(FriendlyByteBuf buffer) {
            return new RequestTradeEditorPacket(buffer.readBoolean());
        }

        public static void handle(RequestTradeEditorPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            ServerPlayer sender = context.getSender();
            if (packet.request && sender != null) {
                context.enqueueWork(() -> {
                    if (sender.hasPermissions(2)) {
                        sendTradeEditor(sender);
                    }
                });
            }
            context.setPacketHandled(true);
        }
    }

    public static final class SaveTradeEditorPacket {
        private final List<String> groups;
        private final List<String> offers;
        private final List<String> overrides;
        private final boolean notifySuccess;

        private SaveTradeEditorPacket(
                List<String> groups,
                List<String> offers,
                List<String> overrides,
                boolean notifySuccess
        ) {
            this.groups = new ArrayList<>(groups);
            this.offers = new ArrayList<>(offers);
            this.overrides = new ArrayList<>(overrides);
            this.notifySuccess = notifySuccess;
        }

        public static void encode(SaveTradeEditorPacket packet, FriendlyByteBuf buffer) {
            writeStringList(buffer, packet.groups);
            writeStringList(buffer, packet.offers);
            writeStringList(buffer, packet.overrides);
            buffer.writeBoolean(packet.notifySuccess);
        }

        public static SaveTradeEditorPacket decode(FriendlyByteBuf buffer) {
            return new SaveTradeEditorPacket(
                    readStringList(buffer),
                    readStringList(buffer),
                    readStringList(buffer),
                    buffer.readBoolean()
            );
        }

        public static void handle(SaveTradeEditorPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            ServerPlayer sender = context.getSender();
            if (sender == null) {
                context.setPacketHandled(true);
                return;
            }

            context.enqueueWork(() -> {
                boolean success = sender.hasPermissions(2) && VillagerTradeRegistry.applyAndSaveLive(
                        packet.groups,
                        packet.offers,
                        packet.overrides
                );
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> sender), new TradeSaveResultPacket(success, packet.notifySuccess));
            });
            context.setPacketHandled(true);
        }
    }

    public static final class TradeSaveResultPacket {
        private final boolean success;
        private final boolean notifySuccess;

        private TradeSaveResultPacket(boolean success, boolean notifySuccess) {
            this.success = success;
            this.notifySuccess = notifySuccess;
        }

        public static void encode(TradeSaveResultPacket packet, FriendlyByteBuf buffer) {
            buffer.writeBoolean(packet.success);
            buffer.writeBoolean(packet.notifySuccess);
        }

        public static TradeSaveResultPacket decode(FriendlyByteBuf buffer) {
            return new TradeSaveResultPacket(buffer.readBoolean(), buffer.readBoolean());
        }

        public static void handle(TradeSaveResultPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                    dev.xyat.contentstudio.villager.client.VillagerClientActions.handleTradeSaveResult(
                            packet.success,
                            packet.notifySuccess
                    )
            ));
            context.setPacketHandled(true);
        }
    }
}
