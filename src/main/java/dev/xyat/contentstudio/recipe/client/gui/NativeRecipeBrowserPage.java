package dev.xyat.contentstudio.recipe.client.gui;

import com.google.gson.*;
import dev.xyat.contentstudio.recipe.nativeedit.*;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.widget.list.ItemGridItem;
import dev.xyat.kineticcore.api.client.gui.widget.list.ItemGridDensity;
import dev.xyat.kineticcore.api.client.gui.widget.list.ItemGridOutline;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.text.KineticI18n;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

/** Discovered native serializers; no mod-specific dependency or replacement of workstation pages. */
public final class NativeRecipeBrowserPage extends KineticPage {
    private String query = "";
    private int page, pages = 1, total, scrollRow;
    private dev.xyat.kineticcore.api.client.gui.widget.list.KineticItemGrid grid;
    private boolean started;
    private boolean listPending, resumeList;
    private String listingQuery="";
    private JsonArray rows = new JsonArray();
    private final Consumer<NativeRecipeNetwork.Response> callback = this::accept;
    private final String serializer;
    private final boolean savedOnly;
    private boolean creating;
    private String mod="";
    // Live search: the list reloads once typing has paused for this long (the server sends it in batches).
    private static final long SEARCH_DELAY_MS = 300;
    private long searchDue;
    private JsonArray mods=new JsonArray();
    public NativeRecipeBrowserPage() { this(""); }
    public NativeRecipeBrowserPage(String serializer) {this(serializer,false,false);}
    NativeRecipeBrowserPage(String serializer,boolean savedOnly,boolean creating) { super(tr(savedOnly?"saved_recipes":creating?"choose_template":"title"));this.serializer=serializer;this.savedOnly=savedOnly;this.creating=creating;setPausesGame(false); }
    NativeRecipeBrowserPage forMod(String owner) {mod=owner;return this;}
    public static NativeRecipeBrowserPage saved() {return new NativeRecipeBrowserPage("",true,false);}
    static Component tr(String suffix, Object... args) { return KineticI18n.translatable("gui.contentstudio.recipe.native." + suffix, args); }
    @Override protected void build(KineticUi ui) {
        // Header: Back, the live search field, the mod filter, the count, then the new-recipe button.
        ui.textField(48,4,252).value(query).maxLength(220).placeholder(tr("search")).onChange(value -> {
            query = value;
            searchDue = System.currentTimeMillis() + SEARCH_DELAY_MS;
        }).build();
        ui.button(304,4,96).expands().text(mod.isEmpty()?tr("all_mods"):NativeRecipeStationMenu.modLabel(mod)).onClick(()-> {
            var choices=new ArrayList<KineticOverlays.MenuItem>();
            choices.add(KineticOverlays.MenuItem.action(tr("all_mods"),()->{mod="";page=0;scrollRow=0;grid=null;refresh();}));
            for(var entry:mods) {String id=entry.getAsString();if(!dev.xyat.contentstudio.recipe.client.RecipeJeiBridge.jeiAvailable()&&!NativeRecipeCreation.vanilla(id+":"))continue;
                choices.add(KineticOverlays.MenuItem.action(NativeRecipeStationMenu.modLabel(id),()->{mod=id;page=0;scrollRow=0;grid=null;refresh();}));}
            openContextMenu(304,24,choices,180);
        }).build();
        // Standard slots without inner padding: 632 x 334 fits 26 x 14 slots from right under the header.
        int gridY = savedOnly ? 42 : 22;
        grid = ui.itemGrid(4,gridY,632,356-gridY,ItemGridDensity.COMPACT_FLUSH,gridItems())
                .decorations(true).scrollOffset(scrollRow)
                .onClick(i->send(creating?"template":"open",rows.get(i).getAsJsonObject().get("id").getAsString())).build();
        ui.button(4,4,40).text(tr("back")).onClick(this::close).build();
        if(savedOnly)RecipeManagementTabs.build(ui,348,24,288,RecipeManagementTabs.THIRD_PARTY,this);
        if(!savedOnly)ui.button(548,4,88).text(tr(creating?"choose_template":"new_recipe")).tooltip(tr("new_recipe_hint"))
                .onClick(()->{creating=!creating;rebuild();}).build();
        if(!started) { started=true; refresh(); }
        else resumePendingList();
    }
    private List<ItemGridItem> gridItems() {
        List<ItemGridItem> items = new ArrayList<>();
        for (var value : rows) {
            var row=value.getAsJsonObject();
            var previews=row.has("preview")&&!row.get("preview").isJsonNull()?NativeRecipeStacks.read(row.get("preview")):List.<ItemStack>of();
            var id=KineticResourceIds.tryParse(row.has("icon")?row.get("icon").getAsString():"minecraft:knowledge_book");
            var stack=!previews.isEmpty()?previews.get(0):row.has("preview")&&!row.get("preview").isJsonNull()?ItemStack.EMPTY:id==null?ItemStack.EMPTY:new ItemStack(KineticRegistries.items().get(id));
            if(stack.isEmpty())stack=new ItemStack(Items.KNOWLEDGE_BOOK);
            var tooltip=stack.getHoverName().copy().append("\n").append(Component.literal(row.get("id").getAsString()).withStyle(net.minecraft.ChatFormatting.GRAY))
                    .append("\n").append(tr("recipe_type",row.get("type").getAsString()));
            items.add(new ItemGridItem(stack,tooltip,true,false,false,row.get("edited").getAsBoolean()?ItemGridOutline.WARNING:null));
        }
        return items;
    }
    // The server sends the list in batches; refresh loads all of them into the one scrolling grid.
    public void refresh() {
        page=0;
        listingQuery=(mod.isEmpty()?"":"@"+mod+" ")+query;
        requestListBatch();
    }
    private String listAction() { return savedOnly?"saved":"list"; }
    private void requestListBatch() {
        // A hidden parent's listener can be preempted by another editor request without removing it again.
        // Keep that batch eligible until its reply arrives or the parent becomes visible and re-requests it.
        resumeList=!isOpen();
        listPending=true;
        send(listAction(),"");
    }
    private void resumePendingList() {
        if(resumeList && isOpen())requestListBatch();
    }
    private void send(String action,String id) {
        boolean listing=Set.of("list","saved").contains(action);
        // The client has one expected reply. Opening a row preempts the next list batch until Back.
        if(!listing && listPending)resumeList=true;
        String target=serializer.isEmpty()&&!dev.xyat.contentstudio.recipe.client.RecipeJeiBridge.jeiAvailable()?"@vanilla":serializer;
        NativeRecipeClient.expect(NativeRecipeNetwork.request(action,id,target,listing?listingQuery:(mod.isEmpty()?"":"@"+mod+" ")+query,page,0,"",""),callback);
    }
    private void accept(NativeRecipeNetwork.Response packet) {
        if(!isAttached()) return;
        var body=JsonParser.parseString(packet.body()).getAsJsonObject();
        String action=body.get("action").getAsString();
        boolean listing=Set.of("list","saved").contains(action);
        // An editor refreshes its attached parent while it is hidden; only the displayed page can open a child.
        if(!listing&&!isOpen()) return;
        if(listing) {
            if(!listPending || !action.equals(listAction()))return;
            if(packet.success() && body.get("page").getAsInt()!=page) {
                // A reload can shrink the catalog while a child is open. The server clamps that request to its
                // last batch; reload from zero with the submitted criteria rather than leaving a partial list.
                if(body.get("page").getAsInt()<page && body.get("pages").getAsInt()<=page) {
                    page=0;requestListBatch();
                }
                return;
            }
            listPending=false;
            resumeList=false;
        }
        if(!packet.success()) {
            if(isOpen())notifyError(body);
            if(listing)resumeList=true;
            else resumePendingList();
            return;
        }
        if(listing) {
            mods=body.getAsJsonArray("mods");
            int batch=body.get("page").getAsInt();
            if(batch==0)rows=new JsonArray();
            rows.addAll(body.getAsJsonArray("rows"));pages=body.get("pages").getAsInt();total=body.get("total").getAsInt();
            if(batch+1<pages) {page=batch+1;requestListBatch();}
            // The first batch rebuilds the page (keeping the scroll row); later batches extend the grid in place.
            // A new search updates the grid in place, so the search field keeps its focus while typing.
            if(batch==0) {
                if(grid!=null && searchUpdate) {grid.setItems(gridItems());grid.setScrollOffset(0);scrollRow=0;}
                else {if(grid!=null)scrollRow=grid.scrollOffset();if(isOpen())rebuild();}
                searchUpdate=false;
            }
            else if(grid!=null)grid.setItems(gridItems());
        } else if(Set.of("open","template").contains(action)) {
            var editor=NativeRecipeEditorPage.create(body,this::refresh);if(editor!=null)openChild(editor);
        }
    }
    private boolean searchUpdate;

    @Override protected void onTick() {
        if (searchDue != 0 && System.currentTimeMillis() >= searchDue) {
            searchDue = 0;
            page = 0;
            searchUpdate = grid != null;
            refresh();
        }
    }

    @Override protected void onRemoved() {
        if(grid!=null)scrollRow=grid.scrollOffset();
        if(listPending)resumeList=true;
        NativeRecipeClient.cancel(callback);
    }
    static void notifyError(JsonObject body) {
        String message=body.has("message") ? body.get("message").getAsString() : "Unknown error";
        Component detail=message.startsWith("DERIVED_ID:") ? tr("derived_id",message.substring("DERIVED_ID:".length()))
                : switch(message) { case "STALE" -> tr("stale"); case "ID_EXISTS" -> tr("id_exists"); case "BUSY" -> tr("busy"); case "reload_failed" -> tr("reload_failed"); default -> Component.literal(message); };
        KineticOverlays.toast(tr("error",detail));
    }
    @Override protected void renderBackground(KineticGraphics g,int mx,int my,float pt) {
        KineticTheme.panel(g,0,0,640,360);
        if(savedOnly)g.scrollingText(title(),548,8,88,0xFFFFAA00,false);
        g.scrollingTextCentered(Component.literal(rows.size()<total?rows.size()+" / "+total:String.valueOf(total)),474,8,140,0xFFFFFFFF,false);
    }
}
