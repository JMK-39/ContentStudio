package dev.xyat.contentstudio.tooltip;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class TooltipClientPacketHandler {
    public static void handleSync(TooltipNetwork.SyncToClientPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TooltipClientHandlers.openHubScreen(packet.jsonPayload()))
        );
        ctx.get().setPacketHandled(true);
    }

    public static void handleOpenFailure(TooltipNetwork.EditorOpenFailurePacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TooltipClientHandlers.handleOpenFailure(packet.reason()))
        );
        ctx.get().setPacketHandled(true);
    }

    public static void handleSaveResult(TooltipNetwork.SaveResultPacket packet, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> TooltipClientHandlers.handleSaveResult(packet.success()))
        );
        ctx.get().setPacketHandled(true);
    }
}
