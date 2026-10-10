package dev.xyat.contentstudio.recipe.client.gui;

import com.google.gson.*;
import dev.xyat.contentstudio.recipe.nativeedit.*;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.input.*;
import dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController;
import dev.xyat.kineticcore.api.client.gui.widget.KineticCustomControl;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import dev.xyat.kineticcore.api.runtime.KineticPlatform;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.*;
import static dev.xyat.contentstudio.recipe.client.gui.NativeRecipeBrowserPage.tr;

/** Workstation cards use the shared viewport, including its clipped rendering and pointer routing. */
final class NativeRecipeStationMenu {
    static final int CARD_WIDTH=184;
    private record Card(String key,Component label,ItemStack icon,Component tooltip,Runnable click) { }
    private final Runnable rebuild;
    private final Consumer<KineticPage> open;
    private final BooleanSupplier attached;
    private final String namespace,section;
    private final boolean creating;
    private final KineticScrollController scroll=new KineticScrollController();
    private final Consumer<NativeRecipeNetwork.Response> callback=this::accept;
    private final List<JsonObject> rows=new ArrayList<>();
    private final List<Card> cards=new ArrayList<>();
    String query="";
    private boolean started,ready;
    private int x,y,w,h,visibleRows;
    NativeRecipeStationMenu(Runnable rebuild,Consumer<KineticPage> open,BooleanSupplier attached,String namespace,String section,JsonArray initial) {
        this(rebuild,open,attached,namespace,section,initial,false);
    }
    NativeRecipeStationMenu(Runnable rebuild,Consumer<KineticPage> open,BooleanSupplier attached,String namespace,String section,JsonArray initial,boolean creating) {
        this.rebuild=rebuild;this.open=open;this.attached=attached;this.namespace=namespace;this.section=section;
        this.creating=creating;
        if(initial!=null) {initial.forEach(v->rows.add(v.getAsJsonObject().deepCopy()));started=true;ready=true;filter();}
    }
    private JsonArray snapshot() {var array=new JsonArray();rows.forEach(array::add);return array;}
    void build(KineticUi ui,int x,int y,int w,int h) {
        this.x=x;this.y=y;this.w=w;this.h=h;visibleRows=Math.max(1,h/42);
        scroll.update((cards.size()+2)/3,visibleRows);
        int cardWidth=CARD_WIDTH;
        // Container and canvas pages share this API control; no widget rebuild while scrolling or dragging.
        ui.add(new KineticCustomControl(x,y,w,h) {
            @Override protected void render(KineticGraphics g,int mx,int my,float pt) {
                int shift=(int)Math.round(scroll.smoothOffset()*42);Card hovered=null;
                g.scissor(x,y,x+w-8,y+h);
                for(int i=0;i<cards.size();i++) {
                    int cx=x+i%3*(cardWidth+4),cy=y+i/3*42-shift;
                    if(cy+38<=y || cy>=y+h)continue;
                    var card=cards.get(i);boolean hover=mx>=cx && mx<cx+cardWidth && my>=Math.max(y,cy) && my<Math.min(y+h,cy+38);
                    KineticTheme.button(g,cx,cy,cardWidth,38,Component.empty(),hover,true,false);
                    g.item(card.icon(),cx+8,cy+11);
                    g.scrollingTextCentered(card.label(),cx+105,cy+15,cardWidth-40,0xFFFFFFFF,true);
                    if(hover)hovered=card;
                }
                g.endScissor();setTooltip(hovered==null?null:hovered.tooltip());
            }
            @Override protected boolean onMouseClick(MouseInput input) {
                if(click(input))return true;if(!input.isLeft() || !input.inside(x,y,w-8,h))return false;
                int col=(int)(input.x()-x)/(cardWidth+4),localX=(int)(input.x()-x)%(cardWidth+4);
                double contentY=input.y()-y+scroll.smoothOffset()*42;int row=(int)(contentY/42);
                int i=row*3+col;
                if(col<3 && localX<cardWidth && contentY-row*42<38 && i>=0 && i<cards.size()) {playClickSound();cards.get(i).click().run();return true;}
                return false;
            }
            @Override protected boolean onMouseScroll(ScrollInput input) {return wheel(input);}
            @Override protected boolean onMouseDrag(MouseDragInput input) {return drag(input);}
            @Override protected boolean onMouseRelease(MouseInput input) {return release(input);}
        });
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
                if(dev.xyat.contentstudio.recipe.client.RecipeJeiBridge.jeiAvailable()&&!Set.of("minecraft","forge","neoforge").contains(mod))mods.merge(mod,row,(current,candidate)->
                        current.get("icon").getAsString().equals("minecraft:knowledge_book")
                                && !candidate.get("icon").getAsString().equals("minecraft:knowledge_book")?candidate:current);
            }
            mods.forEach((mod,row)->{
                String name=KineticPlatform.displayName(mod);
                if((mod+" "+name).toLowerCase(Locale.ROOT).contains(q))cards.add(new Card(mod,Component.literal(name),icon(row),Component.literal(mod),
                        ()->open.accept(new NativeRecipeStationsPage(mod,"",snapshot(),creating))));
            });return;
        }
        if(namespace.equals("create") && section.isEmpty()) {
            for(String group:List.of("basic","sequence")) {
                var row=rows.stream().filter(r->r.get("type").getAsString().startsWith("create:"))
                        .filter(r->r.get("type").getAsString().equals("create:sequenced_assembly")==group.equals("sequence")).findFirst();
                if(row.isPresent())cards.add(new Card(group,tr("group."+group),icon(row.get()),tr("group."+group),
                        ()->open.accept(new NativeRecipeStationsPage(namespace,group,snapshot(),creating))));
            }
        } else for(var row:rows) {
            String type=row.get("type").getAsString();
            if(!dev.xyat.contentstudio.recipe.client.RecipeJeiBridge.jeiAvailable()&&!NativeRecipeCreation.vanilla(type))continue;
            boolean vanilla=namespace.equals("minecraft")&&(type.startsWith("forge:")||type.startsWith("neoforge:"));
            if(!type.startsWith(namespace+":")&&!vanilla)continue;
            if(namespace.equals("create") && type.equals("create:sequenced_assembly")!=section.equals("sequence"))continue;
            var icon=icon(row);Component name=icon.isEmpty()?Component.literal(type):icon.getHoverName();
            if((type+" "+name.getString()).toLowerCase(Locale.ROOT).contains(q))cards.add(new Card(type,name,icon,
                    Component.literal(type+" ("+row.get("count").getAsInt()+")"),()->open.accept(new NativeRecipeBrowserPage(type,false,creating))));
        }
    }
    private static ItemStack icon(JsonObject row) {var id=KineticResourceIds.tryParse(row.get("icon").getAsString());return id==null?ItemStack.EMPTY:new ItemStack(KineticRegistries.items().get(id));}
    boolean wheel(ScrollInput input) {
        if(!input.inside(x,y,w,h))return false;
        scroll.scroll(input.deltaY());return true;
    }
    boolean click(MouseInput input) {return scroll.beginDrag(input.x(),input.y(),input.button(),x+w-4,y,4,h,16,0);}
    boolean drag(MouseDragInput input) {return scroll.drag(input.y(),y,h,16);}
    boolean release(MouseInput input) {return scroll.release(input.button());}
    void render(KineticGraphics g,int mx,int my) {
        scroll.render(g,mx,my,x+w-4,y,4,h,16);
        if(started && cards.isEmpty())g.scrollingTextCentered(tr("no_stations"),x+w/2,y+40,w-16,0xFFAAAAAA,false);
    }
}
