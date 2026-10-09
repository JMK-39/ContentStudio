package dev.xyat.contentstudio.recipe.client.gui;

import com.google.gson.*;
import dev.xyat.contentstudio.recipe.nativeedit.*;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.render.*;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.input.MouseInput;
import dev.xyat.kineticcore.api.client.gui.selector.KineticSelectors;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.client.gui.text.KineticText;
import dev.xyat.kineticcore.api.client.gui.widget.KineticEntityPreview;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.Consumer;
import static dev.xyat.contentstudio.recipe.client.gui.NativeRecipeBrowserPage.tr;

/** Clickable workstation diagram. JEI is neither required nor consulted by this editor. */
public final class NativeRecipeEditorPage extends KineticPage {
    private final NativeRecipeFieldsPage.Session session;
    private final Consumer<NativeRecipeNetwork.Response> callback=this::accept;
    private NativeRecipeLayout layout;
    private List<NativeRecipeLayout.Slot> slots=List.of();
    private List<List<ItemStack>> stacks=List.of();
    private List<NativeRecipeVisuals.Icon> nativeIcons=List.of();
    private List<List<String>> fields=List.of();
    private int selected=-1,fieldPage,diagramPage,diagramPages=1;
    private float scale;
    private int diagramX,diagramY;
    private String stamp="";
    private final KineticEntityPreview entityPreview=KineticEntityPreview.create();
    private List<List<String>> ingredientTags=List.of();
    private boolean legacyTwilight,powahBackground;
    private int slotSize() {return layout.kind().equals("twilight_entities")?34:18;}
    public NativeRecipeEditorPage(JsonObject body,Runnable refresh) {
        super(tr("visual"));setPausesGame(false);session=new NativeRecipeFieldsPage.Session(body,refresh);session.visualOwner=true;
    }
    private void refreshDiagram() {
        var json=session.document.json();stamp=json.toString();layout=NativeRecipeLayout.of(json,diagramPage);diagramPages=1;
        if(layout.kind().equals("create_sequence"))diagramPages=Math.max(1,(json.getAsJsonArray("sequence").size()+2)/3);
        // Large custom arrays use whole pages, never a cropped or microscopic item grid.
        if(layout.kind().equals("generic")) {
            diagramPages=Math.max(1,(layout.slots().size()+11)/12);diagramPage=Math.min(diagramPage,diagramPages-1);
            var all=layout.slots().subList(diagramPage*12,Math.min(layout.slots().size(),(diagramPage+1)*12));
            var visible=new ArrayList<NativeRecipeLayout.Slot>();int input=0,output=0;
            for(var slot:all) {int n=slot.output()?output++:input++;visible.add(new NativeRecipeLayout.Slot(slot.path(),(slot.output()?112:0)+n%3*20,n/3*20,slot.output()));}
            slots=List.copyOf(visible);
        } else slots=layout.slots();
        stacks=slots.stream().map(slot->{
            var value=session.document.at(slot.path());
            if(slot.path().size()==2 && slot.path().get(0).equals("sequence") && value.isJsonObject() && value.getAsJsonObject().has("type")
                    && value.getAsJsonObject().get("type").isJsonPrimitive())
                return NativeRecipeStacks.read(new JsonPrimitive(NativeRecipeStations.icon(value.getAsJsonObject().get("type").getAsString())));
            var items=NativeRecipeStacks.read(value);
            if(layout.kind().equals("feasts_cuisine")&&slot.path().equals(List.of("base"))&&value.isJsonPrimitive()&&items.isEmpty())
                items=NativeRecipeCompat.cuisineBasePreview(value.getAsString());
            if(layout.kind().equals("twilight_uncrafting")&&slot.path().equals(List.of("input"))&&json.has("input_count"))try {
                int count=Math.max(1,Math.min(999,json.get("input_count").getAsInt()));
                items=items.stream().map(stack->{var copy=stack.copy();copy.setCount(count);return copy;}).toList();
            }catch(RuntimeException invalidCount){ }
            return items;
        }).toList();
        legacyTwilight=layout.kind().equals("twilight_entities")&&NativeRecipeVisuals.hasTexture("twilightforest","textures/gui/transformation_jei.png");
        powahBackground=layout.kind().equals("powah_energizing")&&NativeRecipeVisuals.hasTexture("powah","textures/gui/jei/energizing.png");
        nativeIcons=slots.stream().map(slot->NativeRecipeVisuals.read(session.document.at(slot.path()))).toList();
        ingredientTags=slots.stream().map(slot->NativeRecipeStacks.tagIds(session.document.at(slot.path()))).toList();
        scale=Math.min(2.5f,Math.min(358f/Math.max(1,layout.width()),160f/Math.max(60,layout.kind().equals("generic")?80:layout.height())));
        diagramX=22+(386-(int)(layout.width()*scale))/2;diagramY=92+(174-(int)((layout.kind().equals("generic")?80:layout.height())*scale))/2;
        if(selected>=slots.size())selected=-1;
    }
    private List<String> selectedPath() {return selected<0?List.of():slots.get(selected).path();}
    @Override protected void build(KineticUi ui) {
        refreshDiagram();
        ui.textField(90,42,528).enabled(!session.busy).value(session.target).maxLength(256).onChange(v->session.target=v).build();
        fields=NativeRecipeLayout.fields(session.document,selectedPath());
        if(selected<0)fields=fields.stream().filter(p->p.size()==1 && !p.get(0).equals("type")).toList();
        else if(slots.get(selected).output()) {
            var path=selectedPath();var value=session.document.at(path);
            if(value.isJsonObject() && value.getAsJsonObject().has("item") && value.getAsJsonObject().get("item").isJsonObject()) {
                path=NativeRecipeLayout.append(path,"item");value=session.document.at(path);
            }
            if(value.isJsonObject() && (value.getAsJsonObject().has("item") || value.getAsJsonObject().has("id")) && !value.getAsJsonObject().has("count")) {
                var all=new ArrayList<>(fields);all.add(NativeRecipeLayout.append(path,"count"));fields=List.copyOf(all);
            }
        }
        fields=fields.stream().sorted(Comparator.comparingInt(p->switch(p.get(p.size()-1)){case "id","item","tag"->0;case "count","amount"->1;case "chance"->2;default->3;})).toList();
        fieldPage=Math.min(fieldPage,Math.max(0,(fields.size()-1)/4));
        for(int row=0;row<4;row++) {
            int index=fieldPage*4+row;if(index>=fields.size())break;
            var field=fields.get(index);var value=valueAt(field);int y=112+row*40;
            boolean picker=isItem(field,value);
            if(value.isBoolean())ui.toggle(426,y,184).enabled(!session.busy).value(value.getAsBoolean()).labels(Component.literal("true"),Component.literal("false"))
                    .onChange(v->edit(field,v.toString())).build();
            else ui.textField(426,y,picker?132:184).enabled(!session.busy).value(session.invalid.getOrDefault(field,value.getAsString())).maxLength(NativeRecipeDocument.MAX_CHARS)
                    .validator(v->!session.invalid.containsKey(field)).onChange(v->edit(field,v)).build();
            if(picker)ui.button(562,y,48).enabled(!session.busy).text(tr("pick")).onClick(()->pick(field)).build();
        }
        ui.button(22,286,96).text(tr("parameters")).onClick(()->{selected=-1;fieldPage=0;rebuild();}).build();
        ui.button(122,286,132).text(tr("slot_fields")).enabled(selected>=0).onClick(()->openChild(new NativeRecipeFieldsPage(session,selectedPath()))).build();
        ui.button(426,286,36).text(Component.literal("<")).enabled(fieldPage>0).onClick(()->{fieldPage--;rebuild();}).build();
        ui.button(574,286,36).text(Component.literal(">")).enabled((fieldPage+1)*4<fields.size()).onClick(()->{fieldPage++;rebuild();}).build();
        if(diagramPages>1) {
            ui.button(310,286,40).text(Component.literal("<")).enabled(diagramPage>0).onClick(()->{diagramPage--;selected=-1;rebuild();}).build();
            ui.button(354,286,40).text(Component.literal(">")).enabled(diagramPage+1<diagramPages).onClick(()->{diagramPage++;selected=-1;rebuild();}).build();
        }
        ui.button(22,320,84).text(tr("back")).enabled(!session.busy).onClick(this::close).build();
        ui.button(114,320,132).text(tr("advanced")).enabled(!session.busy).onClick(()->openChild(new NativeRecipeFieldsPage(session,List.of()))).build();
        ui.button(354,320,128).text(tr("restore")).enabled(session.edited&&!session.busy).onClick(()->openDialog(tr("restore"),tr("restore_confirm"),tr("restore"),tr("back"),()->send("restore"),()->{})).build();
        ui.button(490,320,128).text(tr("save")).enabled(!session.busy).onClick(()->send("save")).build();
    }
    private static boolean isItem(List<String> path,JsonPrimitive value) {
        if(!value.isString())return false;String key=path.get(path.size()-1);
        if(Set.of("type","fluid","sound","action").contains(key))return false;
        String text=value.getAsString();var id=KineticResourceIds.tryParse(text.startsWith("#")?text.substring(1):text);
        return id!=null && (key.equals("tag") || text.startsWith("#") || KineticRegistries.items().contains(id));
    }
    private void edit(List<String> field,String text) {
        try {
            boolean exists;try{session.document.at(field);exists=true;}catch(IllegalArgumentException absent){exists=false;}
            if(!exists && field.get(field.size()-1).equals("count")) {
                int count=Integer.parseInt(text);if(count<1)throw new IllegalArgumentException("Expected a positive quantity");
                session.document.add(field.subList(0,field.size()-1),"count",new JsonPrimitive(count));
            } else session.document.setPrimitive(field,text);
            session.invalid.remove(field);refreshDiagram();
        }
        catch(IllegalArgumentException e){session.invalid.put(field,text);}
    }
    private JsonPrimitive valueAt(List<String> path) {try{return session.document.at(path).getAsJsonPrimitive();}catch(IllegalArgumentException absent){return new JsonPrimitive(1);}}
    private void pick(List<String> field) {
        boolean tag=field.get(field.size()-1).equals("tag") || session.document.at(field).getAsString().startsWith("#");
        KineticSelectors.openItemSelector(selection->{
            if(selection==null)return;
            if(tag && selection.isTag())edit(field,(field.get(field.size()-1).equals("tag")?"":"#")+selection.value());
            else if(!tag && selection.isItem())edit(field,KineticRegistries.items().id(selection.stack().getItem()).toString());
        });
    }
    @Override protected void onTick() {if(!stamp.equals(session.document.json().toString()))rebuild();}
    @Override protected boolean onMouseClick(MouseInput input) {
        if(session.busy || !input.isLeft() && !input.isRight())return false;
        for(int i=0;i<slots.size();i++) {
            var slot=slots.get(i);double x=diagramX+slot.x()*scale,y=diagramY+slot.y()*scale;
            if(input.x()>=x && input.x()<x+slotSize()*scale && input.y()>=y && input.y()<y+slotSize()*scale) {
                selected=i;fieldPage=0;rebuild();
                if(input.isRight())openContextMenu(input.x(),input.y(),slotMenu(),184);
                else fields.stream().filter(p->isItem(p,valueAt(p))).findFirst().ifPresent(this::pick);
                return true;
            }
        }return false;
    }
    private List<KineticOverlays.MenuItem> slotMenu() {
        var path=selectedPath();
        var editPath=session.document.at(path).isJsonPrimitive()?List.copyOf(path.subList(0,path.size()-1)):path;
        return List.of(
                KineticOverlays.MenuItem.action(tr("edit_slot"),()->openChild(new NativeRecipeFieldsPage(session,editPath))),
                KineticOverlays.MenuItem.danger(tr("remove_slot"),this::removeSelectedSlot));
    }
    private void removeSelectedSlot() {
        if(session.busy || selected<0)return;
        if(!session.invalid.isEmpty()){KineticOverlays.toast(tr("invalid"));return;}
        try {
            NativeRecipeSlotEdits.remove(session.document,new NativeRecipeLayout(layout.kind(),layout.width(),layout.height(),slots),selected);
            selected=-1;fieldPage=0;rebuild();
        }catch(RuntimeException error){KineticOverlays.toast(tr("error",Component.literal(error.getMessage())));}
    }
    @Override protected void renderBackground(KineticGraphics g,int mx,int my,float pt) {
        KineticTheme.panel(g,14,14,612,332);
        g.scrollingText(title(),22,24,596,0xFFFFAA00,false);g.scrollingText(tr("id"),22,48,64,0xFFFFFFFF,false);
        KineticTheme.panelAlt(g,20,74,390,206);KineticTheme.panelAlt(g,418,74,200,206);
        g.scrollingText(tr(layout.kind().equals("generic")?"generic_preview":"click_slot"),24,80,382,0xFFAAAAAA,false);
        g.scrollingText(selected<0?tr("parameters"):tr(slots.get(selected).output()?"output_slot":"input_slot"),426,82,184,0xFFFFAA00,false);
        for(int row=0;row<4;row++) {int i=fieldPage*4+row;if(i<fields.size())g.scrollingText(fieldLabel(fields.get(i)),426,100+row*40,184,0xFFFFFFFF,false);}
        g.scrollingTextCentered(Component.literal((fieldPage+1)+" / "+Math.max(1,(fields.size()+3)/4)),518,292,100,0xFFFFFFFF,false);
        g.push();try {
            g.translate(diagramX,diagramY);g.scale(scale,scale);background(g);
            for(int i=0;i<slots.size();i++) {
                var slot=slots.get(i);int x=slot.x(),y=slot.y();
                if(layout.kind().equals("twilight_entities")) {
                    if(legacyTwilight)g.texture(KineticTexture.of("twilightforest","textures/gui/transformation_jei.png"),x,y,7,10,34,34);
                    else g.texture(KineticTexture.of("twilightforest","textures/gui/big_slot.png",34,34),x,y,0,0,34,34);
                    if(i==selected)KineticTheme.stateOutline(g,x,y,34,34,true,false,false);
                    continue;
                }
                RecipeSlots.draw(g,x,y,18);
                var variants=stacks.get(i);var nativeIcon=nativeIcons.get(i);
                if(nativeIcon!=null)nativeIcon.draw(g,x+1,y+1);
                else if(!variants.isEmpty()) {
                    var stack=NativeRecipeStacks.frame(variants,System.currentTimeMillis());g.item(stack,x+1,y+1);
                    if(!ingredientTags.get(i).isEmpty())RecipeSlots.tagMarker(g,x,y,18,stack.getCount());
                    else if(stack.getCount()>1) {
                        String count=Integer.toString(stack.getCount());float countScale=Math.min(1f,14f/Math.max(1,KineticText.width(count)));
                        g.push();g.raise(1);g.translate(x+16-KineticText.width(count)*countScale,y+16-KineticText.lineHeight()*countScale);
                        g.scale(countScale,countScale);g.text(count,0,0,0xFFFFFFFF,true);g.pop();
                    }
                } else if(!ingredientTags.get(i).isEmpty())RecipeSlots.tagMarker(g,x,y,18,1);
                else g.scrollingTextCentered(Component.literal("?"),x+9,y+5,14,0xFF555555,false);
                if(i==selected)KineticTheme.stateOutline(g,x,y,18,18,true,false,false);
            }
        }finally{g.pop();}
        if(layout.kind().equals("twilight_entities"))for(int i=0;i<slots.size();i++) {
            var slot=slots.get(i);var value=session.document.at(slot.path());
            String id=entityId(value);int x=diagramX+Math.round((slot.x()+2)*scale),y=diagramY+Math.round((slot.y()+2)*scale),size=Math.round(30*scale);
            // Preview clipping uses page coordinates; draw outside the diagram's additional transform.
            if(!entityPreview.render(g,id,session.id+"/"+i,x,y,size,size,false))g.scrollingTextCentered(Component.literal(id.isEmpty()?"?":id),x+size/2,y+size/2,size,0xFF555555,false);
        }
    }
    private static String entityId(JsonElement value) {
        if(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString())return value.getAsString();
        if(value.isJsonObject())for(String key:List.of("entity","type","id")) {
            var id=value.getAsJsonObject().get(key);if(id!=null&&id.isJsonPrimitive()&&id.getAsJsonPrimitive().isString())return id.getAsString();
        }
        return "";
    }
    private void background(KineticGraphics g) {
        g.fill(-2,-2,layout.width()+2,(layout.kind().equals("generic")?80:layout.height())+2,0xFFC6C6C6);
        if(layout.kind().equals("powah_energizing")) {
            if(powahBackground)g.texture(KineticTexture.of("powah","textures/gui/jei/energizing.png"),0,0,0,0,160,38);
            else if(slots.stream().filter(s->!s.output()).count()<6)recipeArrow(g,108,4);
            var json=session.document.json();
            if(json.has("energy")&&json.get("energy").isJsonPrimitive())g.scrollingText(Component.literal(json.get("energy").getAsString()+" FE"),4,30,152,0xFF444444,false);
        } else if(layout.kind().equals("twilight_entities")) {
            var json=session.document.json();
            boolean reversible=json.has("reversible")&&json.get("reversible").isJsonPrimitive()&&json.get("reversible").getAsJsonPrimitive().isBoolean()&&json.get("reversible").getAsBoolean();
            String name=reversible?"transformation_double_arrow.png":"transformation_arrow.png";
            if(legacyTwilight)g.texture(KineticTexture.of("twilightforest","textures/gui/transformation_jei.png"),46,19,116,reversible?16:0,23,15);
            else g.texture(KineticTexture.of("twilightforest","textures/gui/"+name,23,30),46,12,0,0,23,30);
        } else if(layout.kind().equals("twilight_uncrafting")) {
            recipeArrow(g,34,20);
        } else if(layout.kind().equals("twilight_blocks")) {
            recipeArrow(g,46,18);
        } else if(layout.kind().equals("feasts_kettle")) {
            var texture=KineticTexture.of("youkaisfeasts","textures/gui/kettle.png");
            g.texture(texture,0,0,29,16,116,56);recipeArrow(g,52,12);
            g.texture(texture,15,48,176,0,17,10);
        } else if(layout.kind().equals("feasts_ferment")) {
            recipeArrow(g,72,20);
        } else if(layout.kind().equals("feasts_heating")) {
            recipeArrow(g,26,20);
            g.texture(KineticTexture.of("minecraft","textures/gui/container/furnace.png"),2,24,56,36,14,14);
        } else if(layout.kind().equals("feasts_cuisine")) {
            recipeArrow(g,132,20);
        } else if(layout.kind().equals("feasts_pot")) {
            recipeArrow(g,110,20);
        } else if(layout.kind().equals("feasts_basin")||layout.kind().equals("workstation_drying")) {
            recipeArrow(g,28,0);
        } else if(layout.kind().equals("cutting")) {
            var texture=KineticTexture.of("farmersdelight","textures/gui/jei/cutting_board.png");
            g.texture(texture,4,27,4,27,39,27);g.texture(texture,48,19,48,19,24,17);
        } else if(layout.kind().equals("cooking_pot")) {
            var texture=KineticTexture.of("farmersdelight","textures/gui/jei/cooking_pot.png");
            g.texture(texture,64,2,61,2,22,28);g.texture(texture,90,0,89,0,27,33);g.texture(texture,20,42,18,39,18,15);
        } else if(layout.kind().startsWith("create_")) {
            var arrow=KineticTexture.of("minecraft","textures/gui/container/furnace.png");
            if(layout.kind().equals("create_sequence")) {
                for(var slot:slots)if(slot.path().size()==2 && slot.path().get(0).equals("sequence"))g.texture(arrow,slot.x()-22,25,79,34,22,17);
            } else {
                g.texture(arrow,86,0,79,34,24,17);
                var icon=NativeRecipeStacks.read(new JsonPrimitive(NativeRecipeStations.icon(session.document.json().get("type").getAsString())));
                if(!icon.isEmpty())g.item(icon.get(0),84,-22);
            }
        } else {
            var texture=KineticTexture.of("minecraft","textures/gui/container/furnace.png");
            int arrowX=layout.kind().equals("crafting")?layout.width()-47:layout.kind().equals("furnace")?24:86;
            int arrowY=slots.stream().filter(NativeRecipeLayout.Slot::output).findFirst().map(NativeRecipeLayout.Slot::y).orElse(0);
            g.texture(texture,arrowX,arrowY,79,34,24,17);
            if(layout.kind().equals("furnace"))g.texture(texture,3,28,56,36,14,14);
        }
    }
    private static void recipeArrow(KineticGraphics g,int x,int y) {
        g.texture(KineticTexture.of("minecraft","textures/gui/container/furnace.png"),x,y,79,34,24,17);
    }
    private static Component fieldLabel(List<String> path) {
        String key=path.get(path.size()-1);
        String known=switch(key) {case "id","item"->"item";case "tag"->"tag";case "count","amount","Amount"->"quantity";
            case "chance"->"chance";case "cookingtime","cookingTime","processingTime","processing_time","duration","time","timeCost","smeltingTime"->"time";
            case "experience"->"experience";case "loops"->"loops";case "heatRequirement"->"heat";default->null;};
        return known==null?Component.literal(key):tr("field."+known);
    }
    @Override protected void renderTooltips(int mx,int my) {
        for(int i=0;i<slots.size();i++) {
            var s=slots.get(i);float x=diagramX+s.x()*scale,y=diagramY+s.y()*scale;
            if(mx>=x && mx<x+slotSize()*scale && my>=y && my<y+slotSize()*scale) {
                var lines=new ArrayList<Component>();var choices=stacks.get(i);
                if(!choices.isEmpty())lines.add(NativeRecipeStacks.frame(choices,System.currentTimeMillis()).getHoverName());
                for(String id:ingredientTags.get(i))lines.add(Component.literal("#"+id).withStyle(net.minecraft.ChatFormatting.GOLD));
                if(nativeIcons.get(i)!=null)lines.add(Component.literal(nativeIcons.get(i).id()));
                lines.add(Component.literal(String.join(" / ",s.path())));lines.add(tr("click_slot"));showTooltip(lines,260);return;
            }
        }
    }
    private void send(String action) {
        if(session.busy)return;
        if(action.equals("save") && (!session.invalid.isEmpty() || KineticResourceIds.tryParse(session.target)==null)){KineticOverlays.toast(tr("invalid"));return;}
        session.busy=true;rebuild();NativeRecipeClient.expect(NativeRecipeNetwork.request(action,session.id,action.equals("restore")?session.id:session.target,"",0,session.catalog,session.revision,action.equals("save")?session.document.json().toString():""),callback);
    }
    private void accept(NativeRecipeNetwork.Response packet) {
        if(!isAttached())return;session.busy=false;var body=JsonParser.parseString(packet.body()).getAsJsonObject();
        if(packet.success()){KineticOverlays.toast(tr("saved"));navigateBack();session.refresh.run();}else{NativeRecipeBrowserPage.notifyError(body);rebuild();}
    }
    @Override protected boolean onCloseRequested() {
        if(session.busy)return true;
        if(!session.invalid.isEmpty() || !stamp.equals(session.original) || !session.id.equals(session.target)) {
            openDialog(tr("discard"),tr("discard_confirm"),tr("discard"),tr("back"),this::navigateBack,()->{});return true;
        }return false;
    }
}
