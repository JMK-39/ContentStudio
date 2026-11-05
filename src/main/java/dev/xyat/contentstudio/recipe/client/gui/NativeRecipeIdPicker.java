package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.list.SelectionItem;
import dev.xyat.kineticcore.api.client.search.KineticSearch;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import static dev.xyat.contentstudio.recipe.client.gui.NativeRecipeBrowserPage.tr;

/** Searchable choice of a registry ID without an item icon, such as a fluid or a status effect. */
final class NativeRecipeIdPicker extends KineticPage {
    record Choice(String id, Component name) {}

    private static final int PANEL_W = 420, PANEL_H = 300;
    private final List<Choice> all;
    private final String current;
    private final Consumer<String> done;
    private String query = "";
    private List<Choice> shown = List.of();
    private int left, top;
    private dev.xyat.kineticcore.api.client.gui.widget.list.KineticSelectionList list;

    NativeRecipeIdPicker(Component title, List<Choice> choices, String current, Consumer<String> done) {
        super(title);
        setPausesGame(false);
        this.all = List.copyOf(choices);
        this.current = current == null ? "" : current;
        this.done = done;
    }

    static List<Choice> fluids() {
        var result = new ArrayList<Choice>();
        KineticRegistries.fluids().entries().forEach((id, fluid) -> {
            if (fluid == Fluids.EMPTY || !fluid.isSource(fluid.defaultFluidState())) return;
            String key = "fluid_type." + id.getNamespace() + "." + id.getPath();
            if (!KineticI18n.hasTranslation(key)) key = "block." + id.getNamespace() + "." + id.getPath();
            result.add(new Choice(id.toString(), KineticI18n.hasTranslation(key) ? KineticI18n.translatable(key) : Component.empty()));
        });
        result.sort(Comparator.comparing(Choice::id));
        return result;
    }

    static List<Choice> effects() {
        var result = new ArrayList<Choice>();
        KineticRegistries.mobEffects().entries().forEach((id, effect) -> result.add(new Choice(id.toString(), effect.getDisplayName())));
        result.sort(Comparator.comparing(Choice::id));
        return result;
    }

    @Override protected void build(KineticUi ui) {
        left = (width() - PANEL_W) / 2;
        top = (height() - PANEL_H) / 2;
        ui.button(left + 4, top + 4, 40).text(tr("back")).onClick(this::close).build();
        ui.textField(left + 4, top + 28, PANEL_W - 8).value(query).maxLength(128).placeholder(tr("picker_search"))
                .onChange(value -> { query = value; refresh(); }).build();
        list = ui.selectionList(left + 4, top + 50, PANEL_W - 8, PANEL_H - 54, List.of()).textRows()
                .onSelect(index -> { if (index >= 0 && index < shown.size()) { done.accept(shown.get(index).id()); close(); } }).build();
        refresh();
    }

    // The list is refreshed in place so the search field keeps its focus while typing.
    private void refresh() {
        shown = all.stream().filter(choice -> query.isBlank() || KineticSearch.match(choice.id() + " " + choice.name().getString(), query)).toList();
        var rows = new ArrayList<SelectionItem>();
        int selected = -1;
        for (int i = 0; i < shown.size(); i++) {
            var choice = shown.get(i);
            if (choice.id().equals(current)) selected = i;
            rows.add(new SelectionItem(Component.literal(choice.id()).withStyle(ChatFormatting.WHITE),
                    choice.name().copy().withStyle(ChatFormatting.GOLD), Component.literal(choice.id()), true, false));
        }
        if (list == null) return;
        list.setItems(rows);
        list.setSelectedIndex(selected);
    }

    @Override protected void renderBackground(KineticGraphics g, int mx, int my, float pt) {
        KineticTheme.panel(g, left, top, PANEL_W, PANEL_H);
        g.scrollingTextCentered(title(), left + PANEL_W / 2, top + 10, PANEL_W - 2 * 48, 0xFFFFAA00, false);
    }
}
