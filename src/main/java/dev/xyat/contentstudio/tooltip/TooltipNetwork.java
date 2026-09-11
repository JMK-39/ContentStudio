package dev.xyat.contentstudio.tooltip;

import dev.xyat.kineticcore.api.KTNetworkProtocol;
import com.google.gson.reflect.TypeToken;
import dev.xyat.kineticcore.api.NetworkCompressUtil;
import dev.xyat.contentstudio.tooltip.TooltipModule;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class TooltipNetwork {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TooltipModule.MODID, "tooltip_network"),
            () -> PROTOCOL_VERSION, KTNetworkProtocol::acceptsAnyVersion, KTNetworkProtocol::acceptsAnyVersion
    );

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, SyncToClientPacket.class, SyncToClientPacket::encode, SyncToClientPacket::new, TooltipClientPacketHandler::handleSync);
        CHANNEL.registerMessage(id++, SaveToServerPacket.class, SaveToServerPacket::encode, SaveToServerPacket::new, SaveToServerPacket::handle);
        CHANNEL.registerMessage(id++, RequestEditorPacket.class, RequestEditorPacket::encode, RequestEditorPacket::new, RequestEditorPacket::handle);
        CHANNEL.registerMessage(id++, SaveResultPacket.class, SaveResultPacket::encode, SaveResultPacket::new, TooltipClientPacketHandler::handleSaveResult);
        CHANNEL.registerMessage(id, EditorOpenFailurePacket.class, EditorOpenFailurePacket::encode, EditorOpenFailurePacket::new, TooltipClientPacketHandler::handleOpenFailure);
    }

    public static void requestEditor() {
        CHANNEL.sendToServer(new RequestEditorPacket());
    }

    private static void sendSnapshot(ServerPlayer player) {
        if (!TooltipManager.loadForEditor()) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new EditorOpenFailurePacket(1));
            return;
        }
        String payload = TooltipManager.GSON.toJson(TooltipManager.tooltipData);
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncToClientPacket(payload));
    }

    public record SyncToClientPacket(String jsonPayload) {
        public SyncToClientPacket(FriendlyByteBuf buf) {
            this(NetworkCompressUtil.decompress(buf.readByteArray()));
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeByteArray(NetworkCompressUtil.compress(this.jsonPayload));
        }
    }

    public record SaveToServerPacket(String jsonPayload) {
        public SaveToServerPacket(FriendlyByteBuf buf) {
            this(NetworkCompressUtil.decompress(buf.readByteArray()));
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeByteArray(NetworkCompressUtil.compress(this.jsonPayload));
        }

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;

                boolean success = false;
                if (player.hasPermissions(2)) {
                    try {
                        Map<String, List<TooltipManager.TooltipRule>> next = TooltipManager.GSON.fromJson(
                                this.jsonPayload,
                                new TypeToken<Map<String, List<TooltipManager.TooltipRule>>>(){}.getType()
                        );
                        success = TooltipManager.isValidData(next) && TooltipManager.saveAndGenerateJS(next);
                    } catch (RuntimeException exception) {
                        TooltipModule.LOGGER.error("Rejected invalid tooltip save payload", exception);
                    }
                }
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SaveResultPacket(success));
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class RequestEditorPacket {
        public RequestEditorPacket() {}
        public RequestEditorPacket(FriendlyByteBuf buf) {}
        public void encode(FriendlyByteBuf buf) {}

        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer player = ctx.get().getSender();
                if (player == null) return;
                if (!player.hasPermissions(2)) {
                    CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new EditorOpenFailurePacket(0));
                    return;
                }
                sendSnapshot(player);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public record EditorOpenFailurePacket(int reason) {
        public EditorOpenFailurePacket(FriendlyByteBuf buf) {
            this(buf.readVarInt());
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(reason);
        }
    }

    public record SaveResultPacket(boolean success) {
        public SaveResultPacket(FriendlyByteBuf buf) {
            this(buf.readBoolean());
        }

        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(success);
        }
    }
}
