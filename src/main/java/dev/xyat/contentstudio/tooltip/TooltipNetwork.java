package dev.xyat.contentstudio.tooltip;

import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import com.google.gson.reflect.TypeToken;
import dev.xyat.kineticcore.api.network.KineticCompression;
import dev.xyat.kineticcore.api.network.NetworkCodec;
import dev.xyat.kineticcore.api.network.NetworkProtocolLimits;
import dev.xyat.kineticcore.api.network.NetworkVersionPolicy;
import dev.xyat.kineticcore.api.network.PacketChannel;
import dev.xyat.kineticcore.api.network.PacketRegistrations;
import dev.xyat.kineticcore.api.network.ServerPacketContext;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;

public final class TooltipNetwork {
    private static final String PROTOCOL_VERSION = "2";
    private static final int MAX_COMPRESSED_BYTES = NetworkProtocolLimits.DEFAULT.maxByteArrayBytes();
    private static final int MAX_DECOMPRESSED_BYTES = KineticCompression.DEFAULT_MAX_DECOMPRESSED_BYTES;
    private static final PacketChannel CHANNEL = PacketChannel.create(
            KineticResourceIds.of(TooltipModule.MODID, "tooltip_network"),
            PROTOCOL_VERSION,
            NetworkVersionPolicy.EXACT
    );

    private static boolean syncRegistered;
    private static boolean saveRegistered;
    private static boolean requestRegistered;
    private static boolean saveResultRegistered;
    private static boolean openFailureRegistered;
    private static boolean rulesRegistered;
    private static boolean registered;

    private TooltipNetwork() {
    }

    public static synchronized void register() {
        PacketRegistrations.runIndependent(
                () -> {
                    if (!syncRegistered) {
                        CHANNEL.registerClientbound(0, SyncToClientPacket.class,
                                NetworkCodec.of(
                                        (buffer, packet) -> buffer.writeByteArray(compress(packet.jsonPayload()), MAX_COMPRESSED_BYTES),
                                        buffer -> new SyncToClientPacket(decompress(buffer.readByteArray(MAX_COMPRESSED_BYTES)))
                                ),
                                TooltipClientPacketHandler::handleSync
                        );
                        syncRegistered = true;
                    }
                },
                () -> {
                    if (!saveRegistered) {
                        CHANNEL.registerServerbound(1, SaveToServerPacket.class,
                                NetworkCodec.of(
                                        (buffer, packet) -> buffer.writeByteArray(compress(packet.jsonPayload()), MAX_COMPRESSED_BYTES),
                                        buffer -> new SaveToServerPacket(decompress(buffer.readByteArray(MAX_COMPRESSED_BYTES)))
                                ),
                                TooltipNetwork::handleSave
                        );
                        saveRegistered = true;
                    }
                },
                () -> {
                    if (!requestRegistered) {
                        CHANNEL.registerServerbound(2, RequestEditorPacket.class,
                                NetworkCodec.of((buffer, packet) -> { }, buffer -> new RequestEditorPacket()),
                                TooltipNetwork::handleRequestEditor
                        );
                        requestRegistered = true;
                    }
                },
                () -> {
                    if (!saveResultRegistered) {
                        CHANNEL.registerClientbound(3, SaveResultPacket.class,
                                NetworkCodec.of(
                                        (buffer, packet) -> buffer.writeBoolean(packet.success()),
                                        buffer -> new SaveResultPacket(buffer.readBoolean())
                                ),
                                TooltipClientPacketHandler::handleSaveResult
                        );
                        saveResultRegistered = true;
                    }
                },
                () -> {
                    if (!openFailureRegistered) {
                        CHANNEL.registerClientbound(4, EditorOpenFailurePacket.class,
                                NetworkCodec.of(
                                        (buffer, packet) -> buffer.writeVarInt(packet.reason()),
                                        buffer -> new EditorOpenFailurePacket(buffer.readVarInt())
                                ),
                                TooltipClientPacketHandler::handleOpenFailure
                        );
                        openFailureRegistered = true;
                    }
                },
                () -> {
                    if (!rulesRegistered) {
                        CHANNEL.registerClientbound(5, RulesToClientPacket.class,
                                NetworkCodec.of(
                                        (buffer, packet) -> buffer.writeByteArray(compress(packet.jsonPayload()), MAX_COMPRESSED_BYTES),
                                        buffer -> new RulesToClientPacket(decompress(buffer.readByteArray(MAX_COMPRESSED_BYTES)))
                                ),
                                TooltipClientPacketHandler::handleRules
                        );
                        rulesRegistered = true;
                    }
                },
                () -> registered = syncRegistered && saveRegistered && requestRegistered
                        && saveResultRegistered && openFailureRegistered && rulesRegistered
        );
    }

    public static void requestEditor() {
        if (registered) {
            CHANNEL.sendToServer(new RequestEditorPacket());
        }
    }

    public static void saveToServer(String jsonPayload) {
        CHANNEL.sendToServer(new SaveToServerPacket(jsonPayload));
    }

    public static void sendRulesTo(ServerPlayer player) {
        if (registered) {
            CHANNEL.sendToPlayer(player, new RulesToClientPacket(TooltipManager.GSON.toJson(TooltipManager.tooltipData)));
        }
    }

    public static void broadcastRules() {
        if (registered) {
            CHANNEL.broadcast(new RulesToClientPacket(TooltipManager.GSON.toJson(TooltipManager.tooltipData)));
        }
    }

    private static void sendSnapshot(ServerPlayer player) {
        if (!TooltipManager.loadForEditor()) {
            CHANNEL.sendToPlayer(player, new EditorOpenFailurePacket(1));
            return;
        }
        String payload = TooltipManager.GSON.toJson(TooltipManager.tooltipData);
        CHANNEL.sendToPlayer(player, new SyncToClientPacket(payload));
    }

    private static void handleSave(SaveToServerPacket packet, ServerPacketContext context) {
        ServerPlayer player = context.sender();
        boolean success = false;
        if (player.hasPermissions(2)) {
            try {
                Map<String, List<TooltipManager.TooltipRule>> next = TooltipManager.GSON.fromJson(
                        packet.jsonPayload(),
                        new TypeToken<Map<String, List<TooltipManager.TooltipRule>>>() {}.getType()
                );
                success = TooltipManager.save(next);
                if (success) broadcastRules();
            } catch (RuntimeException exception) {
                TooltipModule.LOGGER.error("Rejected invalid tooltip save payload", exception);
            }
        }
        CHANNEL.sendToPlayer(player, new SaveResultPacket(success));
    }

    private static void handleRequestEditor(RequestEditorPacket packet, ServerPacketContext context) {
        ServerPlayer player = context.sender();
        if (!player.hasPermissions(2)) {
            CHANNEL.sendToPlayer(player, new EditorOpenFailurePacket(0));
            return;
        }
        sendSnapshot(player);
    }

    private static byte[] compress(String value) {
        return KineticCompression.compressUtf8(value, MAX_COMPRESSED_BYTES, MAX_DECOMPRESSED_BYTES);
    }

    private static String decompress(byte[] value) {
        return KineticCompression.decompressUtf8(value, MAX_DECOMPRESSED_BYTES);
    }

    public record SyncToClientPacket(String jsonPayload) {
    }

    public record RulesToClientPacket(String jsonPayload) {
    }

    public record SaveToServerPacket(String jsonPayload) {
    }

    public record RequestEditorPacket() {
    }

    public record EditorOpenFailurePacket(int reason) {
    }

    public record SaveResultPacket(boolean success) {
    }
}
