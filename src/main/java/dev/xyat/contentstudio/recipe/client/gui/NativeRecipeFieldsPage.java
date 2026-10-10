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
    private static final int PAGE_ROWS=8, ROW_Y=76, ROW_PITCH=28;
    private final Map<String,List<ItemStack>> previewCache=new HashMap<>();
    static final class Session {
        final NativeRecipeDocument document;
        final List<String> viewPath;
        final String id, revision, original;
        final long catalog;
        final boolean edited,creating,added;
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
        }
    }
    private final Session session;
    private final List<String> path;
    private List<String> keys=List.of();
    private int page;
    private final Consumer<NativeRecipeNetwork.Response> callback=this::accept;
    public NativeRecipeFieldsPage(JsonObject body,Runnable refresh) {this(new Session(body,refresh),List.of());}
    NativeRecipeFieldsPage(Session session,List<String> path) {
        super(tr("fields"));this.session=session;this.path=List.copyOf(path);setPausesGame(false);
    }
    private List<String> child(String key) {var result=new ArrayList<>(path);result.add(key);return List.copyOf(result);}
    @Override protected void build(KineticUi ui) {
        previewCache.clear();
        keys=session.document.children(path);
        page=Math.min(page,Math.max(0,(keys.size()-1)/PAGE_ROWS));
        ui.textField(90,42,528).enabled(!session.busy).value(session.target).maxLength(256).validator(value -> KineticResourceIds.tryParse(value)!=null)
                .onChange(value -> session.target=value).build();
        for(int row=0;row<PAGE_ROWS;row++) {
            int index=page*PAGE_ROWS+row;if(index>=keys.size()) break;
            String key=keys.get(index);List<String> field=child(key);JsonElement value=session.document.at(field);
            int y=ROW_Y+row*ROW_PITCH+2;
            if(value.isJsonObject() || value.isJsonArray()) {
                ui.button(208,y,352).enabled(!session.busy).text(tr("open_fields",session.document.children(field).size()))
                        .onClick(() -> openChild(new NativeRecipeFieldsPage(session,field))).build();
            } else if(value.isJsonPrimitive()) {
                var primitive=value.getAsJsonPrimitive();
                if(primitive.isBoolean()) {
                    ui.toggle(208,y,352).enabled(!session.busy).value(primitive.getAsBoolean()).labels(Component.literal("true"),Component.literal("false"))
                            .onChange(enabled -> edit(field,enabled.toString())).build();
                } else {
                    ui.textField(208,y,300).enabled(!session.busy).value(session.invalid.getOrDefault(field,primitive.getAsString())).maxLength(NativeRecipeDocument.MAX_CHARS)
                            .validator(text -> !session.invalid.containsKey(field)).onChange(text -> edit(field,text)).build();
                    if(primitive.isString() && isItemField(field,primitive.getAsString())) {
                        ui.button(512,y,48).enabled(!session.busy).text(tr("pick")).onClick(() -> KineticSelectors.openItemSelector(selection -> {
                            if(selection==null) return;
                            boolean tag=key.equals("tag") || primitive.getAsString().startsWith("#");
                            if(tag && selection.isTag()) edit(field,(key.equals("tag")?"":"#")+selection.value());
                            else if(!tag && selection.isItem()) edit(field,KineticRegistries.items().id(selection.stack().getItem()).toString());
                        })).build();
                    }
                }
            }
            if(session.document.at(path).isJsonArray()) {
                ui.button(584,y,16).enabled(!session.busy).text(Component.literal("+")).tooltip(tr("duplicate")).onClick(() -> mutate(field,true)).build();
            }
            if(!field.equals(List.of("type"))) ui.button(604,y,16).enabled(!session.busy).text(Component.literal("−")).tooltip(tr("remove"))
                    .onClick(() -> openDialog(tr("remove"),Component.literal(String.join(" / ",field)),tr("remove"),tr("back"),()->mutate(field,false),()->{})).build();
        }
        ui.button(22,320,84).text(tr("back")).onClick(this::close).build();
        ui.button(114,320,40).text(Component.literal("<")).enabled(page>0).onClick(() -> {page--;rebuild();}).build();
        ui.button(198,320,40).text(Component.literal(">")).enabled((page+1)*PAGE_ROWS<keys.size()).onClick(() -> {page++;rebuild();}).build();
        ui.button(246,320,92).enabled(!session.busy).text(tr("add_field")).onClick(() -> {
            if(!session.invalid.isEmpty()) {KineticOverlays.toast(tr("invalid"));return;}
            openChild(new NativeRecipeAddFieldPage(session.document,path));
        }).build();
        if(path.isEmpty() && !session.visualOwner) {
            ui.button(346,320,132).text(tr(session.added?"delete_recipe":"restore")).enabled(session.edited && !session.busy).onClick(() -> openDialog(tr(session.added?"delete_recipe":"restore"),tr(session.added?"delete_recipe_confirm":"restore_confirm"),tr(session.added?"delete_recipe":"restore"),tr("back"),()->send("restore"),()->{})).build();
            ui.button(486,320,132).text(tr("save")).enabled(!session.busy).onClick(() -> send("save")).build();
        }
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
        if(packet.success()) {KineticOverlays.toast(tr("saved"));navigateBack();session.refresh.run();}
        else {NativeRecipeBrowserPage.notifyError(body);rebuild();}
    }
    @Override protected boolean onCloseRequested() {
        if(session.busy) return true;
        if(path.isEmpty() && !session.visualOwner && (session.creating || !session.invalid.isEmpty() || !session.document.json().toString().equals(session.original)
                || !session.id.equals(session.target))) {
            openDialog(tr("discard"),tr("discard_confirm"),tr("discard"),tr("back"),this::navigateBack,()->{});return true;
        }
        return false;
    }
    @Override protected void renderBackground(KineticGraphics g,int mx,int my,float pt) {
        KineticTheme.panel(g,14,14,612,332);
        g.scrollingText(title(),22,24,596,0xFFFFAA00,false);
        g.scrollingText(tr("id"),22,48,64,0xFFFFFFFF,false);
        g.scrollingText(Component.literal(path.isEmpty()?session.id:String.join(" / ",path)),22,66,596,0xFFAAAAAA,false);
        for(int row=0;row<PAGE_ROWS;row++) {
            int index=page*PAGE_ROWS+row;if(index>=keys.size())break;
            int y=ROW_Y+row*ROW_PITCH;
            g.fill(20,y,622,y+26,row%2==0?0xFF292929:0xFF222222);
            g.scrollingText(NativeRecipeEditorPage.fieldLabel(NativeRecipeView.at(session.document,session.viewPath).recipe(),keys.get(index)),24,y+8,180,0xFFFFFFFF,false);
            var field=child(keys.get(index));var value=session.document.at(field);
            if(value.isJsonNull()) g.scrollingText(Component.literal("null"),208,y+8,352,0xFFAAAAAA,false);
            if(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && isItemField(field,value.getAsString())) {
                String text=value.getAsString();boolean tag=field.get(field.size()-1).equals("tag")||text.startsWith("#");
                String ref=tag&&!text.startsWith("#")?"#"+text:text;
                var choices=previewCache.computeIfAbsent(ref,key->NativeRecipeStacks.read(new JsonPrimitive(key)));
                if(!choices.isEmpty()) {
                    RecipeSlots.draw(g,564,y+4,18);
                    g.item(NativeRecipeStacks.frame(choices,System.currentTimeMillis()),565,y+5);
                    if(tag)RecipeSlots.tagMarker(g,564,y+4,18,1);
                }
            }
        }
        g.scrollingTextCentered(Component.literal((page+1)+"/"+Math.max(1,(keys.size()+PAGE_ROWS-1)/PAGE_ROWS)),176,326,38,0xFFFFFFFF,false);
    }
}
