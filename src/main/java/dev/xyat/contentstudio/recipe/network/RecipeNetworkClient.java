package dev.xyat.contentstudio.recipe.network;

import dev.xyat.contentstudio.recipe.RecipeDatabase;
import dev.xyat.contentstudio.recipe.client.gui.RecipeHubPage;
import dev.xyat.contentstudio.recipe.client.gui.RecipePreviewPage;
import dev.xyat.contentstudio.recipe.client.gui.RecipeRemovalPage;
import dev.xyat.contentstudio.recipe.client.gui.RecipePage;
import dev.xyat.contentstudio.recipe.removal.RemovalEntry;
import dev.xyat.contentstudio.recipe.removal.RemovalMode;
import dev.xyat.contentstudio.recipe.removal.RemovalStateCodec;
import dev.xyat.contentstudio.recipe.removal.RuleTransfer;
import dev.xyat.contentstudio.recipe.removal.RecipeSummary;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.KineticGui;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

// NeoForge 26.1 no longer strips @OnlyIn members and warns about the annotation; this class is only used on the client.
//? if <26.1
@OnlyIn(Dist.CLIENT)
public final class RecipeNetworkClient {
    private static java.lang.ref.WeakReference<RecipeRemovalPage> recipeBrowser = new java.lang.ref.WeakReference<>(null);
    private static RuleTransfer.Assembler stateAssembler;
    private static RecipeNetwork.RuleStateBeginPacket stateHeader;
    private static long deliveredStateRequestId = Long.MIN_VALUE;
    private static int nextOutputPage;
    private static final List<ResourceLocation> affectedOutputs = new ArrayList<>();
    private static long itemRequestId;
    private static int nextItemPage;
    private static long itemCatalogVersion;
    private static boolean itemDataError;
    private static ItemStack requestedItem = ItemStack.EMPTY;
    private static final List<RecipeSummary> itemSummaries = new ArrayList<>();
    private static java.lang.ref.WeakReference<ImpactListener> impactListener = new java.lang.ref.WeakReference<>(null);

    public interface ImpactListener {
        void acceptImpactPage(RecipeNetwork.RuleImpactPagePacket page);
    }

    private RecipeNetworkClient() {
    }

    public static void registerRemovalScreen(RecipeRemovalPage screen) {
        recipeBrowser = new java.lang.ref.WeakReference<>(screen);
    }

    public static void requestItemRecipes(RecipeRemovalPage screen, ItemStack item) {
        recipeBrowser = new java.lang.ref.WeakReference<>(screen);
        requestedItem = item.copy();
        itemSummaries.clear();
        nextItemPage = 0;
        itemCatalogVersion = 0L;
        itemDataError = false;
        itemRequestId = RecipeNetwork.requestItemRecipes(item);
    }

    public static void handleItemRecipes(RecipeNetwork.ItemRecipesPacket packet) {
        if (packet.requestId() != itemRequestId || !packet.item().is(requestedItem.getItem())) return;
        if (packet.stale()) {
            RecipeRemovalPage screen = recipeBrowser.get();
            if (screen != null) requestItemRecipes(screen, requestedItem);
            return;
        }
        if (packet.page() != nextItemPage || packet.totalPages() < 1
                || (nextItemPage > 0 && packet.catalogVersion() != itemCatalogVersion)) return;
        if (nextItemPage == 0) itemCatalogVersion = packet.catalogVersion();
        itemSummaries.addAll(packet.recipes());
        itemDataError |= packet.dataError();
        nextItemPage++;
        if (nextItemPage < packet.totalPages()) {
            RecipeNetwork.requestItemRecipes(requestedItem, itemRequestId, nextItemPage, itemCatalogVersion);
        } else {
            RecipeRemovalPage screen = recipeBrowser.get();
            if (screen != null) screen.acceptRecipes(packet.item(), List.copyOf(itemSummaries), itemDataError);
        }
    }

    public static void handleRuleStateBegin(RecipeNetwork.RuleStateBeginPacket packet) {
        try {
            stateAssembler = new RuleTransfer.Assembler(packet.totalBytes(), packet.totalChunks());
            stateHeader = packet;
        } catch (IllegalArgumentException exception) {
            stateAssembler = null;
            stateHeader = null;
        }
    }

    public static void handleRuleStateChunk(RecipeNetwork.RuleStateChunkPacket packet) {
        if (stateAssembler == null || stateHeader == null || packet.requestId() != stateHeader.requestId()) return;
        try {
            stateAssembler.append(packet.index(), packet.data());
            if (packet.index() + 1 != stateHeader.totalChunks()) return;
            List<RemovalEntry> rules = RemovalStateCodec.decodeRules(stateAssembler.finish());
            RecipeNetwork.RuleStateBeginPacket header = stateHeader;
            stateAssembler = null;
            stateHeader = null;
            deliveredStateRequestId = header.requestId();
            nextOutputPage = 0;
            affectedOutputs.clear();
            if (header.status() == RecipeNetwork.SaveStatus.SYNC) {
                if (!(KineticGui.currentPage() instanceof RecipeRemovalPage)) {
                    KineticGui.openChild(new RecipeRemovalPage(rules, List.of()));
                }
            } else {
                RecipeRemovalPage screen = recipeBrowser.get();
                if (screen == null && KineticGui.currentPage() instanceof RecipeRemovalPage value) screen = value;
                if (screen != null) screen.acceptSaveResult(header.requestId(), header.status(), rules);
            }
        } catch (RuntimeException exception) {
            stateAssembler = null;
            stateHeader = null;
        }
    }

    public static void handleRuleStateOutputs(RecipeNetwork.RuleStateOutputPagePacket packet) {
        if (packet.requestId() != deliveredStateRequestId || packet.page() != nextOutputPage
                || packet.totalPages() < 1 || packet.page() >= packet.totalPages()) return;
        affectedOutputs.addAll(packet.outputIds());
        nextOutputPage++;
        if (nextOutputPage != packet.totalPages()) return;
        RecipeRemovalPage screen = recipeBrowser.get();
        if (screen == null && KineticGui.currentPage() instanceof RecipeRemovalPage value) screen = value;
        if (screen != null) screen.acceptAffectedOutputs(List.copyOf(affectedOutputs), true);
    }

    public static long requestImpact(ImpactListener listener, RemovalMode mode, String value,
                                     int page, long version, long requestId) {
        impactListener = new java.lang.ref.WeakReference<>(listener);
        return RecipeNetwork.requestRuleImpact(mode, value, page, version, requestId);
    }

    public static void handleRuleImpactPage(RecipeNetwork.RuleImpactPagePacket page) {
        ImpactListener listener = impactListener.get();
        if (listener != null) listener.acceptImpactPage(page);
    }

    public static void handleRecipeRecords(RecipeNetwork.RecipeRecordsSyncPacket packet) {
        RecipeDatabase.setClientRecords(packet.records());
        KineticPage current = KineticGui.currentPage();
        if (current instanceof RecipePreviewPage preview) {
            preview.refreshFromServer();
        } else if (!(current instanceof RecipePage)) {
            KineticGui.openChild(new RecipePreviewPage());
        }
    }

    public static void handleToast(RecipeNetwork.ToastPacket packet) {
        KineticPage screen = KineticGui.currentPage();
        if (screen instanceof RecipeHubPage value) {
            value.showToast(packet.message());
        } else if (screen instanceof RecipePage value) {
            value.showToast(packet.message());
        } else if (screen instanceof RecipeRemovalPage value) {
            value.showToast(packet.message());
        } else if (screen instanceof RecipePreviewPage value) {
            value.showToast(packet.message());
        } else {
            KineticOverlays.toast(packet.message());
        }
    }
}
