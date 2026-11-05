package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.contentstudio.recipe.network.RecipeNetwork;
import dev.xyat.kineticcore.api.client.gui.KineticGui;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.search.KineticItemSearch;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The single recipe management entry: added recipes, removed original recipes and changed third-party recipes are
 * tabs of one place. Switching closes the current tab first, so any unsaved-change prompt still applies.
 */
final class RecipeManagementTabs {
    static final int ADDED = 0, REMOVED = 1, THIRD_PARTY = 2;

    private RecipeManagementTabs() {}

    static void build(KineticUi ui, int x, int y, int width, int current, KineticPage page) {
        ui.tabBar(x, y, width, List.of(label("added"), label("removed"), label("third_party")))
                .tooltips(List.of(label("added.tooltip"), label("removed.tooltip"), label("third_party.tooltip")))
                .selected(current)
                .onSelect(index -> {
                    if (index == current) return;
                    page.close();
                    if (!page.isAttached()) open(index);
                })
                .build();
    }

    /** Opens one tab from the recipe hub or from another tab that has just closed. */
    static void open(int index) {
        switch (index) {
            case ADDED -> RecipeNetwork.requestRecipeRecords();
            case REMOVED -> KineticItemSearch.prepare(RecipeNetwork::requestOpen);
            default -> KineticGui.openChild(NativeRecipeBrowserPage.saved());
        }
    }

    /** The three tabs as views for screenshot tools; each opens its tab the same way the tab bar does. */
    static List<KineticPage.ReviewView> views() {
        return List.of(new KineticPage.ReviewView("added", () -> open(ADDED)),
                new KineticPage.ReviewView("removed", () -> open(REMOVED)),
                new KineticPage.ReviewView("third_party", () -> open(THIRD_PARTY)));
    }

    static Component label(String key) {
        return KineticI18n.translatable("gui.contentstudio.recipe.manage." + key);
    }
}
