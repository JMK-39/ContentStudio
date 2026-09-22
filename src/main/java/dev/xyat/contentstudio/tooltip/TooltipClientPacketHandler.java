package dev.xyat.contentstudio.tooltip;

public final class TooltipClientPacketHandler {
    private TooltipClientPacketHandler() {
    }

    public static void handleSync(TooltipNetwork.SyncToClientPacket packet) {
        TooltipClientHandlers.openHubScreen(packet.jsonPayload());
    }

    public static void handleOpenFailure(TooltipNetwork.EditorOpenFailurePacket packet) {
        TooltipClientHandlers.handleOpenFailure(packet.reason());
    }

    public static void handleSaveResult(TooltipNetwork.SaveResultPacket packet) {
        TooltipClientHandlers.handleSaveResult(packet.success());
    }
}
