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
    private int page, pages = 1, total;
    private boolean started;
    private JsonArray rows = new JsonArray();
    private final Consumer<NativeRecipeNetwork.Response> callback = this::accept;
    private final String serializer;
    private final boolean savedOnly;
    private boolean creating;
    private String mod="";
    private JsonArray mods=new JsonArray();
    public NativeRecipeBrowserPage() { this(""); }
    public NativeRecipeBrowserPage(String serializer) {this(serializer,false,false);}
    NativeRecipeBrowserPage(String serializer,boolean savedOnly,boolean creating) { super(tr(savedOnly?"saved_recipes":creating?"choose_template":"title"));this.serializer=serializer;this.savedOnly=savedOnly;this.creating=creating;setPausesGame(false); }
    public static NativeRecipeBrowserPage saved() {return new NativeRecipeBrowserPage("",true,false);}
    static Component tr(String suffix, Object... args) { return KineticI18n.translatable("gui.contentstudio.recipe.native." + suffix, args); }
    @Override protected void build(KineticUi ui) {
        ui.button(22,42,120).text(mod.isEmpty()?tr("all_mods"):Component.literal(dev.xyat.kineticcore.api.runtime.KineticPlatform.displayName(mod))).onClick(()-> {
            var choices=new ArrayList<KineticOverlays.MenuItem>();
            choices.add(KineticOverlays.MenuItem.action(tr("all_mods"),()->{mod="";page=0;refresh();}));
            for(var entry:mods) {String id=entry.getAsString();if(!dev.xyat.contentstudio.recipe.client.RecipeJeiBridge.jeiAvailable()&&!NativeRecipeCreation.vanilla(id+":"))continue;
                choices.add(KineticOverlays.MenuItem.action(Component.literal(dev.xyat.kineticcore.api.runtime.KineticPlatform.displayName(id)),()->{mod=id;page=0;refresh();}));}
            openContextMenu(22,62,choices,180);
        }).build();
        ui.textField(148,42,344).value(query).maxLength(220).placeholder(tr("search")).onChange(value -> query = value).build();
        ui.button(498,42,120).text(tr("search_button")).onClick(() -> {page=0; refresh();}).build();
        List<ItemGridItem> items = new ArrayList<>();
        for (var value : rows) {
            var row=value.getAsJsonObject();
            var id=KineticResourceIds.tryParse(row.has("icon")?row.get("icon").getAsString():"minecraft:knowledge_book");
            var stack=id==null?ItemStack.EMPTY:new ItemStack(KineticRegistries.items().get(id));
            if(stack.isEmpty())stack=new ItemStack(Items.KNOWLEDGE_BOOK);
            var tooltip=stack.getHoverName().copy().append("\n").append(Component.literal(row.get("id").getAsString()).withStyle(net.minecraft.ChatFormatting.GRAY))
                    .append("\n").append(tr("recipe_type",row.get("type").getAsString()));
            items.add(new ItemGridItem(stack,tooltip,true,false,false,row.get("edited").getAsBoolean()?ItemGridOutline.WARNING:null));
        }
        ui.add(new dev.xyat.kineticcore.api.client.gui.widget.KineticCustomControl(22,72,596,240) {
            private int index(double mx,double my) {
                int x=(int)mx-24,y=(int)my-74;
                if(x<0||y<0||x/24>=24||x%24>=22||y%24>=22)return -1;
                int i=y/24*24+x/24;return i<items.size()?i:-1;
            }
            @Override protected void render(KineticGraphics g,int mx,int my,float pt) {
                int hovered=index(mx,my);setTooltip(hovered<0?null:items.get(hovered).tooltip());
                for(int i=0;i<items.size();i++) {
                    int x=24+i%24*24,y=74+i/24*24;
                    RecipeSlots.draw(g,x+2,y+2,18);g.item(items.get(i).stack(),x+3,y+3);
                    if(hovered==i||items.get(i).outline()!=null)KineticTheme.stateOutline(g,x,y,22,22,false,hovered==i,items.get(i).outline()!=null);
                }
            }
            @Override protected boolean onMouseClick(dev.xyat.kineticcore.api.client.gui.input.MouseInput input) {
                int i=index(input.x(),input.y());if(!input.isLeft()||i<0)return false;
                playClickSound();send(creating?"template":"open",rows.get(i).getAsJsonObject().get("id").getAsString());return true;
            }
        });
        ui.button(22,320,90).text(tr("back")).onClick(this::close).build();
        if(!savedOnly)ui.button(118,320,160).text(tr(creating?"choose_template":"new_recipe")).tooltip(tr("new_recipe_hint"))
                .onClick(()->{creating=!creating;rebuild();}).build();
        ui.button(384,320,70).text(Component.literal("<")).enabled(page>0).onClick(() -> {page--;refresh();}).build();
        ui.button(548,320,70).text(Component.literal(">")).enabled(page+1<pages).onClick(() -> {page++;refresh();}).build();
        if(!started) { started=true; refresh(); }
    }
    public void refresh() { send(savedOnly?"saved":"list",""); }
    private void send(String action,String id) {
        String target=serializer.isEmpty()&&!dev.xyat.contentstudio.recipe.client.RecipeJeiBridge.jeiAvailable()?"@vanilla":serializer;
        NativeRecipeClient.expect(NativeRecipeNetwork.request(action,id,target,(mod.isEmpty()?"":"@"+mod+" ")+query,page,0,"",""),callback);
    }
    private void accept(NativeRecipeNetwork.Response packet) {
        if(!isOpen()) return;
        var body=JsonParser.parseString(packet.body()).getAsJsonObject();
        if(!packet.success()) { notifyError(body); return; }
        if(Set.of("list","saved").contains(body.get("action").getAsString())) {
            mods=body.getAsJsonArray("mods");
            rows=body.getAsJsonArray("rows"); page=body.get("page").getAsInt(); pages=body.get("pages").getAsInt();total=body.get("total").getAsInt();rebuild();
        } else if(Set.of("open","template").contains(body.get("action").getAsString())) {
            var editor=NativeRecipeEditorPage.create(body,this::refresh);if(editor!=null)openChild(editor);
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
