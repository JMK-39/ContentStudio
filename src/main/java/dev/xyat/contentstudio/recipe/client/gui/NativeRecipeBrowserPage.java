package dev.xyat.contentstudio.recipe.client.gui;

import com.google.gson.*;
import dev.xyat.contentstudio.recipe.nativeedit.*;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.list.SelectionItem;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

/** Discovered native serializers; no mod-specific dependency or replacement of workstation pages. */
public final class NativeRecipeBrowserPage extends KineticPage {
    private String query = "";
    private int page, pages = 1, total;
    private boolean started;
    private JsonArray rows = new JsonArray();
    private final Consumer<NativeRecipeNetwork.Response> callback = this::accept;
    private final String serializer;
    public NativeRecipeBrowserPage() { this(""); }
    public NativeRecipeBrowserPage(String serializer) { super(tr("title"));this.serializer=serializer;setPausesGame(false); }
    static Component tr(String suffix, Object... args) { return KineticI18n.translatable("gui.contentstudio.recipe.native." + suffix, args); }
    @Override protected void build(KineticUi ui) {
        ui.textField(22,42,470).value(query).maxLength(256).placeholder(tr("search")).onChange(value -> query = value).build();
        ui.button(498,42,120).text(tr("search_button")).onClick(() -> {page=0; refresh();}).build();
        List<SelectionItem> items = new ArrayList<>();
        for (var value : rows) {
            var row=value.getAsJsonObject();
            items.add(new SelectionItem(Component.literal(row.get("id").getAsString()).withStyle(net.minecraft.ChatFormatting.WHITE),
                    null, Component.literal(row.get("type").getAsString()),true,false));
        }
        ui.selectionList(22,72,596,240,items).textRows().onSelect(index -> {
            if(index>=0 && index<rows.size()) send("open",rows.get(index).getAsJsonObject().get("id").getAsString());
        }).build();
        ui.button(22,320,90).text(tr("back")).onClick(this::close).build();
        ui.button(384,320,70).text(Component.literal("<")).enabled(page>0).onClick(() -> {page--;refresh();}).build();
        ui.button(548,320,70).text(Component.literal(">")).enabled(page+1<pages).onClick(() -> {page++;refresh();}).build();
        if(!started) { started=true; refresh(); }
    }
    public void refresh() { send("list",""); }
    private void send(String action,String id) {
        NativeRecipeClient.expect(NativeRecipeNetwork.request(action,id,serializer,query,page,0,"",""),callback);
    }
    private void accept(NativeRecipeNetwork.Response packet) {
        if(!isOpen()) return;
        var body=JsonParser.parseString(packet.body()).getAsJsonObject();
        if(!packet.success()) { notifyError(body); return; }
        if(body.get("action").getAsString().equals("list")) {
            rows=body.getAsJsonArray("rows"); page=body.get("page").getAsInt(); pages=body.get("pages").getAsInt();total=body.get("total").getAsInt();rebuild();
        } else if(body.get("action").getAsString().equals("open")) {
            openChild(new NativeRecipeEditorPage(body,this::refresh));
        }
    }
    @Override protected void onRemoved() { NativeRecipeClient.cancel(callback); }
    static void notifyError(JsonObject body) {
        String message=body.has("message") ? body.get("message").getAsString() : "Unknown error";
        Component detail=message.startsWith("DERIVED_ID:") ? tr("derived_id",message.substring("DERIVED_ID:".length()))
                : switch(message) { case "STALE" -> tr("stale"); case "ID_EXISTS" -> tr("id_exists"); case "BUSY" -> tr("busy"); case "reload_failed" -> tr("reload_failed"); default -> Component.literal(message); };
        KineticOverlays.toast(tr("error",detail));
    }
    @Override protected void renderBackground(KineticGraphics g,int mx,int my,float pt) {
        KineticTheme.panel(g,14,14,612,332);
        g.scrollingText(title(),22,24,596,0xFFFFAA00,false);
        g.scrollingTextCentered(Component.literal((page+1)+" / "+pages+" ("+total+")"),501,326,86,0xFFFFFFFF,false);
    }
}
