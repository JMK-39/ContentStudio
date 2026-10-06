package dev.xyat.contentstudiovalidation;

import dev.xyat.contentstudio.recipe.network.RecipeNetwork;
import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.client.gui.KineticGui;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Connected to a dedicated server as an operator, opens the recipe manager and each of its sub-pages the way the
 * buttons do, and logs every step and any disconnect. Never edits or saves.
 */
public final class ServerRecipeValidation {
    private static final Logger LOG = LoggerFactory.getLogger(ServerRecipeValidation.class);
    private static final String[] STEPS = {"open-hub", "added-recipes", "removal"};
    private static boolean installed, finished;
    private static int step = -1;
    private static long due;
    private static String lastScreen = "";
    private static long lastTick, longestStall;

    public static void install() {
        if (installed) return;
        installed = true;
        KineticClientEvents.onTick(KineticClientEvents.TickPhase.END, ServerRecipeValidation::tick);
    }

    private static void tick() {
        if (finished) return;
        var mc = Minecraft.getInstance();
        long tickNow = System.currentTimeMillis();
        // The longest gap between client ticks shows how long a step froze the client.
        if (lastTick != 0 && step >= 0) longestStall = Math.max(longestStall, tickNow - lastTick);
        lastTick = tickNow;
        String screen = describe(mc);
        if (!screen.equals(lastScreen)) {
            LOG.info("CS_SERVER_SCREEN step={} screen={}", step, screen);
            lastScreen = screen;
        }
        if (mc.screen instanceof net.minecraft.client.gui.screens.DisconnectedScreen disconnected) {
            LOG.error("CS_SERVER_FAIL step={} disconnected: {}", step < 0 ? "join" : STEPS[step], disconnected.getTitle().getString());
            finish(false);
            return;
        }
        if (mc.player == null || mc.getConnection() == null || mc.isLocalServer()) return;
        long now = System.currentTimeMillis();
        if (step < 0) {
            if (due == 0) { due = now + 4000; return; }
            if (now < due) return;
        } else if (now < due) {
            return;
        }
        if (step >= 0) LOG.info("CS_SERVER_STALL step={} longestTickGapMs={}", STEPS[step], longestStall);
        longestStall = 0;
        step++;
        if (step >= STEPS.length) {
            LOG.info("CS_SERVER_PASS steps={} still connected", STEPS.length);
            finish(true);
            return;
        }
        LOG.info("CS_SERVER_STEP {}", STEPS[step]);
        switch (step) {
            case 0 -> RecipeNetwork.requestOpenHub();
            case 1 -> RecipeNetwork.requestRecipeRecords();
            default -> RecipeNetwork.requestOpen();
        }
        due = now + 6000;
    }

    private static String describe(Minecraft mc) {
        var page = KineticGui.currentPage();
        if (page != null) return page.getClass().getSimpleName();
        return mc.screen == null ? "none" : mc.screen.getClass().getSimpleName();
    }

    private static void finish(boolean pass) {
        finished = true;
        LOG.info("CS_SERVER_{}", pass ? "DONE" : "STOP");
        dev.xyat.kineticcore.api.runtime.KineticClientRuntime.stopClient();
    }
}
