package dev.xyat.contentstudio.recipe.client.gui;

import com.google.gson.*;
import dev.xyat.contentstudio.recipe.nativeedit.*;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.input.*;
import dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticPlatform;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.*;
import static dev.xyat.contentstudio.recipe.client.gui.NativeRecipeBrowserPage.tr;

/** Whole-row scrolling keeps every static workstation icon complete, including at viewport edges. */
final class NativeRecipeStationMenu {
    static final int CARD_WIDTH=184;
    private record Card(String key,Component label,ItemStack icon,Component tooltip,Runnable click) { }
    private final Runnable rebuild;
    private final Consumer<KineticPage> open;
    private final BooleanSupplier attached;
    private final String namespace,section;
    private final KineticScrollController scroll=new KineticScrollController();
    private final Consumer<NativeRecipeNetwork.Response> callback=this::accept;
    private final List<JsonObject> rows=new ArrayList<>();
    private final List<Card> cards=new ArrayList<>();
    String query="";
    private boolean started,ready;
    private int x,y,w,h,visibleRows;
    NativeRecipeStationMenu(Runnable rebuild,Consumer<KineticPage> open,BooleanSupplier attached,String namespace,String section,JsonArray initial) {
        this.rebuild=rebuild;this.open=open;this.attached=attached;this.namespace=namespace;this.section=section;
        if(initial!=null) {initial.forEach(v->rows.add(v.getAsJsonObject().deepCopy()));started=true;ready=true;filter();}
    }
    private JsonArray snapshot() {var array=new JsonArray();rows.forEach(array::add);return array;}
    void build(KineticUi ui,int x,int y,int w,int h) {
        this.x=x;this.y=y;this.w=w;this.h=h;visibleRows=Math.max(1,h/42);
        scroll.update((cards.size()+2)/3,visibleRows);
        int cardWidth=CARD_WIDTH;
        for(int i=scroll.offset()*3;i<Math.min(cards.size(),(scroll.offset()+visibleRows)*3);i++) {
            var card=cards.get(i);int n=i-scroll.offset()*3;
            ui.itemButton(x+n%3*(cardWidth+4),y+n/3*42,cardWidth,card.icon()).text(card.label()).tooltip(card.tooltip()).onClick(card.click()).build();
        }
        if(!started) {started=true;request(0);}
    }
    void search() {scroll.setOffset(0);if(ready)filter();rebuild.run();}
    private void request(int page) {NativeRecipeClient.expect(NativeRecipeNetwork.request("stations","","","",page,0,"",""),callback);}
    private void accept(NativeRecipeNetwork.Response packet) {
        if(!attached.getAsBoolean())return;
        var body=JsonParser.parseString(packet.body()).getAsJsonObject();
        if(!packet.success()) {NativeRecipeBrowserPage.notifyError(body);return;}
        body.getAsJsonArray("rows").forEach(v->rows.add(v.getAsJsonObject()));
        int page=body.get("page").getAsInt();
        if(page+1<body.get("pages").getAsInt())request(page+1);
        else {ready=true;filter();rebuild.run();}
    }
    private void filter() {
        cards.clear();String q=query.toLowerCase(Locale.ROOT).trim();
        if(namespace.isEmpty()) {
            var mods=new TreeMap<String,JsonObject>();
            for(var row:rows) {
                String type=row.get("type").getAsString(),mod=type.split(":",2)[0];
                if(!mod.equals("minecraft"))mods.merge(mod,row,(current,candidate)->
                        current.get("icon").getAsString().equals("minecraft:knowledge_book")
                                && !candidate.get("icon").getAsString().equals("minecraft:knowledge_book")?candidate:current);
            }
            mods.forEach((mod,row)->{
                String name=KineticPlatform.displayName(mod);
                if((mod+" "+name).toLowerCase(Locale.ROOT).contains(q))cards.add(new Card(mod,Component.literal(name),icon(row),Component.literal(mod),
                        ()->open.accept(new NativeRecipeStationsPage(mod,"",snapshot()))));
            });return;
        }
        if(namespace.equals("create") && section.isEmpty()) {
            for(String group:List.of("basic","sequence")) {
                var row=rows.stream().filter(r->r.get("type").getAsString().startsWith("create:"))
                        .filter(r->r.get("type").getAsString().equals("create:sequenced_assembly")==group.equals("sequence")).findFirst();
                if(row.isPresent())cards.add(new Card(group,tr("group."+group),icon(row.get()),tr("group."+group),
                        ()->open.accept(new NativeRecipeStationsPage(namespace,group,snapshot()))));
            }
        } else for(var row:rows) {
            String type=row.get("type").getAsString();if(!type.startsWith(namespace+":"))continue;
            if(namespace.equals("create") && type.equals("create:sequenced_assembly")!=section.equals("sequence"))continue;
            var icon=icon(row);Component name=icon.isEmpty()?Component.literal(type):icon.getHoverName();
            if((type+" "+name.getString()).toLowerCase(Locale.ROOT).contains(q))cards.add(new Card(type,name,icon,
                    Component.literal(type+" ("+row.get("count").getAsInt()+")"),()->open.accept(new NativeRecipeBrowserPage(type))));
        }
    }
    private static ItemStack icon(JsonObject row) {var id=KineticResourceIds.tryParse(row.get("icon").getAsString());return id==null?ItemStack.EMPTY:new ItemStack(KineticRegistries.items().get(id));}
    boolean wheel(ScrollInput input) {
        if(!input.inside(x,y,w,h))return false;
        if(scroll.scroll(input.deltaY()))rebuild.run();return true;
    }
    boolean click(MouseInput input) {return scroll.beginDrag(input.x(),input.y(),input.button(),x+w-4,y,4,h,16,0);}
    boolean drag(MouseDragInput input) {if(scroll.drag(input.y(),y,h,16)){rebuild.run();return true;}return false;}
    boolean release(MouseInput input) {return scroll.release(input.button());}
    void render(KineticGraphics g,int mx,int my) {
        scroll.render(g,mx,my,x+w-4,y,4,h,16);
        if(started && cards.isEmpty())g.scrollingTextCentered(tr("no_stations"),x+w/2,y+40,w-16,0xFFAAAAAA,false);
    }
}
