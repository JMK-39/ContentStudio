package dev.xyat.contentstudiovalidation;

import com.mojang.blaze3d.platform.NativeImage;
import dev.xyat.contentstudio.tooltip.TooltipClientHandlers;
import dev.xyat.contentstudio.tooltip.TooltipNetwork;
import dev.xyat.contentstudio.tooltip.config.TooltipConfigGui;
import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.client.gui.KineticGui;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.config.client.KTConfigApi;
import dev.xyat.kineticcore.api.network.PacketChannel;
import dev.xyat.kineticcore.api.network.ServerboundSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Opt-in navigation checks using the real config action, page hosts and button/Escape handlers. */
public final class TooltipNavigationValidation {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(TooltipNavigationValidation.class);
    private static boolean finished;
    private static long captureAt;
    private static final List<String> results = new ArrayList<>();

    public static void install() {
        KineticClientEvents.onTick(KineticClientEvents.TickPhase.END, TooltipNavigationValidation::tick);
    }

    @SuppressWarnings("unchecked")
    private static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (captureAt > 0 && System.currentTimeMillis() >= captureAt) {
            captureAt = 0;
            //? if >=26.1 {
            /*Screenshot.takeScreenshot(mc.getMainRenderTarget(), TooltipNavigationValidation::saveScreenshot);
            *///?} else {
            saveScreenshot(Screenshot.takeScreenshot(mc.getMainRenderTarget()));
            //?}
            return;
        }
        if (finished || mc.screen == null || mc.getOverlay() != null
                || KTConfigApi.find(TooltipConfigGui.PAGE_ID).isEmpty()) return;
        finished = true;
        var originalData = TooltipClientHandlers.clientData;
        try {
            // Keep readiness and request handling real; replace only this packet's outbound transport.
            var registered = TooltipNetwork.class.getDeclaredField("registered");
            registered.setAccessible(true);
            boolean wasRegistered = registered.getBoolean(null);
            require(wasRegistered, "Tooltip network must be registered before navigation validation");
            var channelField = TooltipNetwork.class.getDeclaredField("CHANNEL");
            channelField.setAccessible(true);
            var sendersField = PacketChannel.class.getDeclaredField("serverboundSenders");
            sendersField.setAccessible(true);
            var senders = (Map<Class<?>, ServerboundSender<?>>) sendersField.get(channelField.get(null));
            var requests = new ArrayList<TooltipNetwork.RequestEditorPacket>();
            ServerboundSender<TooltipNetwork.RequestEditorPacket> transport = requests::add;
            var originalSender = senders.put(TooltipNetwork.RequestEditorPacket.class, transport);
            try {
                run("unregistered-request-allows-retry", () -> {
                    try {
                        configPage();
                        registered.setBoolean(null, false);
                        int before = requests.size();
                        requestEditor();
                        require(requests.size() == before, "Unregistered request must not reach the transport");
                        registered.setBoolean(null, true);
                        Screen parent = configPage();
                        requestEditor();
                        require(requests.size() == before + 1, "A skipped request must not block the next ready request");
                        configPage();
                        TooltipClientHandlers.openHubScreen("{}");
                        press(backButton());
                        require(mc.screen == parent, "Retried request must return to its new caller");
                    } catch (IllegalAccessException error) {
                        throw new AssertionError(error);
                    } finally {
                        try { registered.setBoolean(null, true); }
                        catch (IllegalAccessException error) { throw new AssertionError(error); }
                    }
                });
                run("legacy-response-button", () -> {
                    Screen parent = configPage();
                    TooltipClientHandlers.openHubScreen("{}");
                    press(backButton());
                    require(mc.screen == parent, "Back button must return to the config page");
                });
                run("legacy-response-escape", () -> {
                    Screen parent = configPage();
                    TooltipClientHandlers.openHubScreen("{}");
                    escape();
                    require(mc.screen == parent, "Escape must return to the config page");
                });
                run("delayed-response-keeps-request-parent", () -> {
                    Screen parent = configPage();
                    requestEditor();
                    configPage();
                    TooltipClientHandlers.openHubScreen("{}");
                    press(backButton());
                    require(mc.screen == parent, "Delayed response must keep the request-time parent");
                });
                run("duplicate-request-keeps-first-parent", () -> {
                    Screen parent = configPage();
                    int before = requests.size();
                    requestEditor();
                    configPage();
                    requestEditor();
                    require(requests.size() == before + 1, "A duplicate request must not send another packet");
                    TooltipClientHandlers.openHubScreen("{}");
                    escape();
                    require(mc.screen == parent, "Duplicate request must not replace the pending parent");
                });
                run("failed-request-clears-parent", () -> {
                    configPage(); requestEditor();
                    TooltipClientHandlers.handleOpenFailure(0);
                    Screen parent = configPage();
                    TooltipClientHandlers.openHubScreen("{}");
                    press(backButton());
                    require(mc.screen == parent, "Failed request must not retain a stale parent");
                });
                for (String invalid : List.of("not-json", "null", "{\"minecraft:stone\":null}")) {
                    run("invalid-payload-clears-parent:" + invalid, () -> {
                        configPage(); requestEditor();
                        Screen current = configPage();
                        TooltipClientHandlers.openHubScreen(invalid);
                        require(mc.screen == current, "Invalid payload must not open a page");
                        Screen parent = configPage();
                        TooltipClientHandlers.openHubScreen("{}");
                        escape();
                        require(mc.screen == parent, "Invalid payload must clear the pending parent");
                    });
                }
                run("back-and-reopen-uses-new-parent", () -> {
                    configPage(); requestEditor(); TooltipClientHandlers.openHubScreen("{}");
                    press(backButton());
                    Screen parent = configPage();
                    requestEditor(); TooltipClientHandlers.openHubScreen("{}");
                    escape();
                    require(mc.screen == parent, "Reopened hub must return to its new caller");
                });
                run("back-label-and-width", () -> {
                    configPage(); TooltipClientHandlers.openHubScreen("{}");
                    Button button = backButton();
                    require(button.getMessage().getString().equals(mc.options.languageCode.equals("zh_cn") ? "返回" : "Back"),
                            "Back button must use the short label");
                    require(button.getWidth() == 40, "Back button must be 40 pixels wide");
                    press(button);
                });
            } finally {
                registered.setBoolean(null, wasRegistered);
                if (originalSender == null) senders.remove(TooltipNetwork.RequestEditorPacket.class);
                else senders.put(TooltipNetwork.RequestEditorPacket.class, originalSender);
            }
        } catch (Throwable error) {
            results.add("FAIL fixture: " + error);
            LOG.error("CONTENT_TOOLTIP_NAVIGATION_FIXTURE_FAIL", error);
        } finally {
            TooltipClientHandlers.clientData = originalData;
            try {
                Path output = Path.of(System.getProperty("contentstudio.tooltipNavigation.output",
                        "build/tooltip-navigation/result.txt"));
                Files.createDirectories(output.toAbsolutePath().getParent());
                Files.write(output, results);
            } catch (Exception error) {
                LOG.error("CONTENT_TOOLTIP_NAVIGATION_RESULT_FAIL", error);
            }
            LOG.info("CONTENT_TOOLTIP_NAVIGATION_{} cases={}",
                    results.stream().anyMatch(line -> line.startsWith("FAIL")) ? "FAIL" : "PASS", results.size());
            KineticOverlays.clearToasts();
            configPage();
            TooltipClientHandlers.openHubScreen("{\"minecraft:diamond\":[]}");
            captureAt = System.currentTimeMillis() + 1000;
        }
    }

    private static void saveScreenshot(NativeImage image) {
        try (image) {
            Path output = Path.of(System.getProperty("contentstudio.tooltipNavigation.output",
                    "build/tooltip-navigation/result.txt")).toAbsolutePath().getParent().resolve("tooltip-hub.png");
            image.writeToFile(output);
            LOG.info("CONTENT_TOOLTIP_NAVIGATION_SCREENSHOT {}", output);
        } catch (Exception error) {
            LOG.error("CONTENT_TOOLTIP_NAVIGATION_SCREENSHOT_FAIL", error);
        } finally {
            Minecraft.getInstance().stop();
        }
    }

    private static Screen configPage() {
        KTConfigApi.openPage(TooltipConfigGui.PAGE_ID);
        return Minecraft.getInstance().screen;
    }

    private static void requestEditor() {
        KTConfigApi.find(TooltipConfigGui.PAGE_ID).orElseThrow().entries().stream()
                .filter(entry -> entry.id().equals("open_editor")).findFirst().orElseThrow().runAction();
    }

    private static Button backButton() {
        require(KineticGui.currentPage() instanceof TooltipClientHandlers.TooltipHubPage, "Tooltip hub must be open");
        return Minecraft.getInstance().screen.children().stream()
                .filter(child -> child instanceof Button button && button.getX() == 20 && button.getY() == 15)
                .map(child -> (Button) child).findFirst().orElseThrow(() -> new AssertionError("Missing Back button"));
    }

    private static void escape() {
        //? if >=26.1 {
        /*Minecraft.getInstance().screen.keyPressed(new net.minecraft.client.input.KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
        *///?} else {
        Minecraft.getInstance().screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        //?}
    }

    private static void press(Button button) {
        //? if >=26.1 {
        /*button.onPress(null);
        *///?} else {
        button.onPress();
        //?}
    }

    private static void run(String name, Runnable check) {
        try {
            check.run();
            results.add("PASS " + name);
            LOG.info("CONTENT_TOOLTIP_NAVIGATION_CASE_PASS {}", name);
        } catch (Throwable error) {
            results.add("FAIL " + name + ": " + error);
            LOG.error("CONTENT_TOOLTIP_NAVIGATION_CASE_FAIL {}", name, error);
            TooltipClientHandlers.handleOpenFailure(1);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
