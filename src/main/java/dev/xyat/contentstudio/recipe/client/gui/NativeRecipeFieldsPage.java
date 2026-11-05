package dev.xyat.contentstudio.recipe.client.gui;

import com.google.gson.*;
import dev.xyat.contentstudio.recipe.nativeedit.*;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.theme.KineticTheme;
import dev.xyat.kineticcore.api.client.gui.ui.KineticUi;
import dev.xyat.kineticcore.api.client.gui.selector.KineticSelectors;
import dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays;
import dev.xyat.kineticcore.api.registry.KineticRegistries;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.Consumer;
import static dev.xyat.contentstudio.recipe.client.gui.NativeRecipeBrowserPage.tr;

/** Typed structural fields retain the serializer's original format, including NBT versus components. */
public final class NativeRecipeFieldsPage extends KineticPage {
    // Header: Back, title, page arrows and the actions; then the save ID row and a divider.
    private static final int ACTION_W=48, SAVE_X=636-ACTION_W, RESTORE_X=SAVE_X-4-ACTION_W, ADD_W=56, ADD_X=RESTORE_X-4-ADD_W;
    private static final int NEXT_X=ADD_X-4-16, PAGE_TEXT_W=28, PAGE_TEXT_X=NEXT_X-4-PAGE_TEXT_W/2, PREV_X=NEXT_X-4-PAGE_TEXT_W-4-16;
    private static final int DIVIDER_Y=44;
    // Field rows: label, then the value (an item icon, a short number field, a text field or an expand button).
    private static final int SLOT=dev.xyat.kineticcore.api.client.gui.theme.KineticSizes.ITEM_SLOT;
    private static final int ROW_Y=DIVIDER_Y+4, ROW_PITCH=SLOT+2, PAGE_ROWS=(356-ROW_Y)/ROW_PITCH;
    private static final int LABEL_W=170, VALUE_X=8+LABEL_W+4, DUPLICATE_X=600, REMOVE_X=620;
    private final Map<String,List<ItemStack>> previewCache=new HashMap<>();
    static final class Session {
        NativeRecipeDocument document;
        List<String> viewPath;
        String id, revision, original;
        long catalog;
        boolean edited,creating,added,vanilla,removed;
        final Runnable refresh;
        final Map<List<String>,String> invalid=new HashMap<>();
        String target;
        boolean busy;
        boolean visualOwner;
        Session(JsonObject body,Runnable refresh) {
            document=new NativeRecipeDocument(body.getAsJsonObject("recipe"));
            viewPath=body.has("view_path")?body.getAsJsonArray("view_path").asList().stream().map(JsonElement::getAsString).toList():List.of();
            original=document.json().toString(); id=body.get("id").getAsString();target=id;
            revision=body.get("revision").getAsString(); catalog=body.get("catalog").getAsLong();
            edited=body.get("edited").getAsBoolean();this.refresh=refresh;
            creating=body.has("creating")&&body.get("creating").getAsBoolean();added=body.has("added")&&body.get("added").getAsBoolean();
            removed=body.has("removed")&&body.get("removed").getAsBoolean();
            vanilla=body.has("owner")?body.get("owner").getAsString().equals("minecraft"):NativeRecipeCreation.vanilla(NativeRecipeDocument.serializerId(NativeRecipeView.at(document,viewPath).recipe()));
        }
        void saved(JsonObject body) {
            document=new NativeRecipeDocument(body.getAsJsonObject("recipe"));original=document.json().toString();
            id=body.get("id").getAsString();target=id;catalog=body.get("catalog").getAsLong();revision=body.get("revision").getAsString();
            viewPath=body.has("view_path")?body.getAsJsonArray("view_path").asList().stream().map(JsonElement::getAsString).toList():List.of();
            edited=body.get("edited").getAsBoolean();added=body.has("added")&&body.get("added").getAsBoolean();creating=false;
            vanilla=body.has("owner")?body.get("owner").getAsString().equals("minecraft"):NativeRecipeCreation.vanilla(NativeRecipeDocument.serializerId(NativeRecipeView.at(document,viewPath).recipe()));
            invalid.clear();busy=false;
        }
    }
    private final Session session;
    private List<String> path;
    private List<String> keys=List.of();
    private int page;
    private final Consumer<NativeRecipeNetwork.Response> callback=this::accept;
    public NativeRecipeFieldsPage(JsonObject body,Runnable refresh) {this(new Session(body,refresh));}
    private NativeRecipeFieldsPage(Session session) {this(session,session.viewPath);}
    NativeRecipeFieldsPage(Session session,List<String> path) {
        super(tr("fields"));this.session=session;this.path=List.copyOf(path);setPausesGame(false);
    }
    private List<String> child(String key) {var result=new ArrayList<>(path);result.add(key);return List.copyOf(result);}
    @Override protected void build(KineticUi ui) {
        previewCache.clear();
        keys=session.document.children(path);
        page=Math.min(page,Math.max(0,(keys.size()-1)/PAGE_ROWS));
        // Header row: Back, the title and every action, all at the top.
        ui.button(4,4,40).text(tr("back")).onClick(this::close).build();
        ui.button(PREV_X,4,16).text(Component.literal("<")).enabled(page>0).onClick(() -> {page--;rebuild();}).build();
        ui.button(NEXT_X,4,16).text(Component.literal(">")).enabled((page+1)*PAGE_ROWS<keys.size()).onClick(() -> {page++;rebuild();}).build();
        ui.button(ADD_X,4,ADD_W).enabled(!session.busy).text(tr("add_field")).onClick(() -> {
            if(!session.invalid.isEmpty()) {KineticOverlays.toast(tr("invalid"));return;}
            openChild(new NativeRecipeAddFieldPage(session.document,path));
        }).build();
        if(path.equals(session.viewPath) && !session.visualOwner) {
            ui.button(RESTORE_X,4,ACTION_W).text(tr(session.added?"delete_recipe":"restore")).enabled(session.edited && !session.busy).onClick(() -> openDialog(tr(session.added?"delete_recipe":"restore"),tr(session.added?"delete_recipe_confirm":"restore_confirm"),tr(session.added?"delete_recipe":"restore"),tr("back"),()->send("restore"),()->{})).build();
            ui.button(SAVE_X,4,ACTION_W).text(tr("save")).enabled(!session.busy).onClick(() -> send("save")).build();
        }
        // Second row: the ID the recipe is saved under.
        ui.textField(48,24,588).enabled(!session.busy).value(session.target).maxLength(256).validator(value -> KineticResourceIds.tryParse(value)!=null)
                .onChange(value -> session.target=value).build();
        for(int row=0;row<PAGE_ROWS;row++) {
            int index=page*PAGE_ROWS+row;if(index>=keys.size()) break;
            String key=keys.get(index);List<String> field=child(key);JsonElement value=session.document.at(field);
            int y=ROW_Y+row*ROW_PITCH+3;
            if(value.isJsonObject() || value.isJsonArray()) {
                ui.button(VALUE_X,y,120).enabled(!session.busy).text(tr("open_fields",session.document.children(field).size()))
                        .onClick(() -> openChild(new NativeRecipeFieldsPage(session,field))).build();
            } else if(value.isJsonPrimitive()) {
                var primitive=value.getAsJsonPrimitive();
                if(primitive.isBoolean()) {
                    ui.toggle(VALUE_X,y,60).enabled(!session.busy).value(primitive.getAsBoolean())
                            .labels(tr("field.true"),tr("field.false"))
                            .onChange(enabled -> edit(field,enabled.toString())).build();
                } else if(!(primitive.isString() && isItemField(field,primitive.getAsString()))) {
                    // Items are icons (click to change); numbers get a short field, other text a longer one.
                    int fieldW=primitive.isNumber()?80:300;
                    ui.textField(VALUE_X,y,fieldW).enabled(!session.busy).value(session.invalid.getOrDefault(field,primitive.getAsString())).maxLength(NativeRecipeDocument.MAX_CHARS)
                            .validator(text -> !session.invalid.containsKey(field)).onChange(text -> edit(field,text)).build();
                }
            }
            if(session.document.at(path).isJsonArray()) {
                ui.button(DUPLICATE_X,y,16).enabled(!session.busy).text(Component.literal("+")).tooltip(tr("duplicate")).onClick(() -> mutate(field,true)).build();
            }
            if(!field.equals(NativeRecipeLayout.append(session.viewPath,"type"))) ui.button(REMOVE_X,y,16).enabled(!session.busy).text(Component.literal("\u2212")).tooltip(tr("remove"))
                    .onClick(() -> openDialog(tr("remove"),Component.literal(String.join(" / ",field)),tr("remove"),tr("back"),()->mutate(field,false),()->{})).build();
        }
    }

    /** Picks an item or tag for an item field (the icon of the row). */
    private void pickItem(List<String> field) {
        if(session.busy) return;
        String key=field.get(field.size()-1);
        String current=session.document.at(field).getAsString();
        KineticSelectors.openItemSelector(selection -> {
            if(selection==null) return;
            boolean tag=key.equals("tag") || current.startsWith("#");
            try {
                if(tag && selection.isTag()) edit(field,(key.equals("tag")?"":"#")+selection.value());
                else if(!tag && selection.isItem()) NativeRecipeItemData.selectField(session.document,field,NativeRecipeStacks.encode(selection.stack()),NativeRecipeItemData.outputField(session.document,field));
            }catch(IllegalArgumentException invalid){KineticOverlays.toast(tr("invalid"));}
            rebuild();
        });
    }

    private int itemRowAt(double mx,double my) {
        if(mx<VALUE_X || mx>=VALUE_X+SLOT || my<ROW_Y) return -1;
        int row=(int)((my-ROW_Y)/ROW_PITCH);
        if(row>=PAGE_ROWS || my-ROW_Y-row*ROW_PITCH>=SLOT) return -1;
        int index=page*PAGE_ROWS+row;
        if(index>=keys.size()) return -1;
        var field=child(keys.get(index));var value=session.document.at(field);
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && isItemField(field,value.getAsString()) ? row : -1;
    }

    @Override protected boolean onMouseClick(dev.xyat.kineticcore.api.client.gui.input.MouseInput input) {
        int row=itemRowAt(input.x(),input.y());
        if(row<0 || !input.isLeft()) return false;
        pickItem(child(keys.get(page*PAGE_ROWS+row)));
        return true;
    }

    private void edit(List<String> field,String text) {
        try {session.document.setPrimitive(field,text);session.invalid.remove(field);}
        catch(IllegalArgumentException e) {session.invalid.put(field,text);}
    }
    private void mutate(List<String> field,boolean duplicate) {
        if(!session.invalid.isEmpty()) {KineticOverlays.toast(tr("invalid"));return;}
        try {if(duplicate) session.document.duplicate(field);else session.document.remove(field);rebuild();}
        catch(RuntimeException e) {KineticOverlays.toast(tr("error",Component.literal(e.getMessage())));}
    }
    private static boolean isItemField(List<String> path,String value) {
        String key=path.get(path.size()-1);
        if(key.equals("item") || key.equals("tag")) return true;
        if(key.equals("type") || key.equals("fluid") || key.equals("sound")) return false;
        var id=KineticResourceIds.tryParse(value.startsWith("#")?value.substring(1):value);
        return id!=null && (value.startsWith("#") || KineticRegistries.items().contains(id));
    }
    private void send(String action) {
        if(session.busy) return;
        if(action.equals("save") && (!session.invalid.isEmpty() || KineticResourceIds.tryParse(session.target)==null)) {
            KineticOverlays.toast(tr("invalid"));return;
        }
        if(action.equals("save")&&session.creating)action="create";
        session.busy=true;rebuild();
        NativeRecipeClient.expect(NativeRecipeNetwork.request(action,session.id,action.equals("restore")?session.id:session.target,
                "",0,session.catalog,session.revision,action.equals("save")||action.equals("create")?session.document.json().toString():""),callback);
    }
    private void accept(NativeRecipeNetwork.Response packet) {
        if(!isAttached()) return;
        session.busy=false;
        var body=JsonParser.parseString(packet.body()).getAsJsonObject();
        if(packet.success()) {
            KineticOverlays.toast(tr("saved"));session.refresh.run();
            if(body.has("deleted")&&body.get("deleted").getAsBoolean())navigateBack();
            else {session.saved(body);path=session.viewPath;rebuild();}
        }
        else {NativeRecipeBrowserPage.notifyError(body);rebuild();}
    }
    @Override protected boolean onCloseRequested() {
        if(session.busy) return true;
        if(path.equals(session.viewPath) && !session.visualOwner && (session.creating || !session.invalid.isEmpty() || !session.document.json().toString().equals(session.original)
                || !session.id.equals(session.target))) {
            openDialog(tr("discard"),tr("discard_confirm"),tr("discard"),tr("back"),this::navigateBack,()->{});return true;
        }
        return false;
    }
    /** The field path in readable labels: "Result / Entry 1" instead of "result / 0". */
    private Component pathLabel() {
        if(path.isEmpty()) return Component.literal(session.id);
        var recipe=NativeRecipeView.at(session.document,session.viewPath).recipe();
        var label=Component.empty();
        for(int i=0;i<path.size();i++) {
            String part=path.get(i);
            if(i>0) label.append(" / ");
            label.append(part.chars().allMatch(Character::isDigit)?tr("entry",Integer.parseInt(part)+1):NativeRecipeEditorPage.fieldLabel(recipe,part));
        }
        return label;
    }

    @Override protected void renderBackground(KineticGraphics g,int mx,int my,float pt) {
        KineticTheme.panel(g,0,0,640,360);
        Component heading=Component.empty().append(title()).append(" \u00b7 ").append(pathLabel());
        // Centred on the screen, clear of Back and the pager.
        g.scrollingTextCentered(heading,320,8,2*Math.min(320-48,PREV_X-4-320),0xFFFFAA00,false);
        g.scrollingTextCentered(Component.literal((page+1)+"/"+Math.max(1,(keys.size()+PAGE_ROWS-1)/PAGE_ROWS)),PAGE_TEXT_X,8,PAGE_TEXT_W,0xFFFFFFFF,false);
        g.scrollingText(tr("id"),4,28,40,0xFFFFFFFF,false);
        g.hLine(4,636,DIVIDER_Y,KineticTheme.current().accentHover());
        var recipe=NativeRecipeView.at(session.document,session.viewPath).recipe();
        for(int row=0;row<PAGE_ROWS;row++) {
            int index=page*PAGE_ROWS+row;if(index>=keys.size())break;
            int y=ROW_Y+row*ROW_PITCH;
            g.fill(4,y,636,y+ROW_PITCH-2,row%2==0?0xFF292929:0xFF222222);
            g.scrollingText(NativeRecipeEditorPage.fieldLabel(recipe,keys.get(index)),8,y+(ROW_PITCH-2-8)/2,LABEL_W,0xFFFFFFFF,false);
            var field=child(keys.get(index));var value=session.document.at(field);
            if(value.isJsonNull()) g.scrollingText(Component.literal("null"),VALUE_X,y+(ROW_PITCH-2-8)/2,300,0xFFAAAAAA,false);
            if(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && isItemField(field,value.getAsString())) {
                String text=value.getAsString();boolean tag=field.get(field.size()-1).equals("tag")||text.startsWith("#");
                var nativeValue=NativeRecipeItemData.preview(session.document,field);
                var choices=previewCache.computeIfAbsent(nativeValue.toString(),key->NativeRecipeStacks.read(nativeValue));
                boolean hovered=mx>=VALUE_X && mx<VALUE_X+SLOT && my>=y && my<y+SLOT;
                KineticTheme.itemSlot(g,VALUE_X,y,SLOT,hovered);
                if(!choices.isEmpty()) {
                    KineticTheme.item(g,NativeRecipeStacks.frame(choices,System.currentTimeMillis()),VALUE_X,y,SLOT,1.0F,false);
                    if(tag)RecipeSlots.tagMarker(g,VALUE_X,y,SLOT,1);
                }
            }
        }
    }
    @Override protected void renderTooltips(int mx,int my) {
        int row=itemRowAt(mx,my);
        if(row<0) return;
        var field=child(keys.get(page*PAGE_ROWS+row));var value=session.document.at(field);
        String text=value.getAsString();
        var nativeValue=NativeRecipeItemData.preview(session.document,field);
        var choices=previewCache.computeIfAbsent(nativeValue.toString(),key->NativeRecipeStacks.read(nativeValue));
        var lines=List.<Component>of(Component.literal(text).withStyle(net.minecraft.ChatFormatting.GRAY),tr("item_click"));
        if(!choices.isEmpty())showItemTooltip(NativeRecipeStacks.frame(choices,System.currentTimeMillis()),lines);
        else showTooltip(lines);
        tooltipAnchor(VALUE_X,ROW_Y+row*ROW_PITCH,SLOT,SLOT);
    }
}
