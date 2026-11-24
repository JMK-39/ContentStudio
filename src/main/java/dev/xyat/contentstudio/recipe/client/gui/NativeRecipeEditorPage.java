package dev.xyat.contentstudio.recipe.client.gui;

import com.google.gson.*;
import dev.xyat.contentstudio.recipe.nativeedit.*;
import dev.xyat.contentstudio.recipe.client.RecipeJeiBridge;
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

/** Vanilla workstation diagrams and optional, directly rendered JEI categories share the same lossless draft. */
public final class NativeRecipeEditorPage extends KineticPage {
    private final NativeRecipeFieldsPage.Session session;
    private final Consumer<NativeRecipeNetwork.Response> callback=this::accept;
    private NativeRecipeLayout layout;
    private List<NativeRecipeLayout.Slot> slots=List.of();
    private List<List<ItemStack>> stacks=List.of();
    private List<NativeRecipeVisuals.Icon> nativeIcons=List.of();
    private List<List<String>> fields=List.of();
    private int selected=-1,fieldPage,diagramPage,diagramPages=1;
    private static final int FIELD_ROWS=12,FIELD_Y=64,FIELD_PITCH=22,FIELD_LABEL_X=424,FIELD_LABEL_W=88,FIELD_CONTROL_X=516,FIELD_CONTROL_W=116;
    private static final int DIAGRAM_X=8,DIAGRAM_Y=64,DIAGRAM_HEIGHT=262;
    private float scale;
    private int diagramX,diagramY;
    private String stamp="";
    private String previewTarget="";
    private final KineticEntityPreview entityPreview=KineticEntityPreview.create();
    private List<List<String>> ingredientTags=List.of();
    private boolean legacyTwilight,powahBackground;
    private RecipeJeiBridge.EditorPreview jeiPreview;
    private List<RecipeJeiBridge.EditorSlot> jeiSlots=List.of();
    private String previewError="";
    private long previewGeneration;
    private final dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController horizontal=new dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController();
    private final dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController vertical=new dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController();
    private int slotSize() {return layout.kind().equals("twilight_entities")?34:18;}
    public static KineticPage create(JsonObject body,Runnable refresh) {
        // Third-party recipes use their JEI display when one exists and the built-in diagram otherwise; either way the
        // slots and properties are edited visually, never on the raw field page.
        return new NativeRecipeEditorPage(body,refresh);
    }
    public NativeRecipeEditorPage(JsonObject body,Runnable refresh) {
        super(tr("visual"));setPausesGame(false);session=new NativeRecipeFieldsPage.Session(body,refresh);session.visualOwner=true;
    }
    private void refreshDiagram() {
        previewTarget=session.target;
        var json=NativeRecipeView.at(session.document,session.viewPath).recipe();stamp=session.document.json().toString();layout=NativeRecipeView.at(session.document,session.viewPath).layout(diagramPage);diagramPages=1;
        if(layout.kind().equals("create_sequence"))diagramPages=Math.max(1,(json.getAsJsonArray("sequence").size()+2)/3);
        // Large custom arrays use whole pages, never a cropped or microscopic item grid.
        if(layout.kind().equals("generic")) {
            diagramPages=Math.max(1,(layout.slots().size()+11)/12);diagramPage=Math.min(diagramPage,diagramPages-1);
            var all=layout.slots().subList(diagramPage*12,Math.min(layout.slots().size(),(diagramPage+1)*12));
            var visible=new ArrayList<NativeRecipeLayout.Slot>();int input=0,output=0;
            for(var slot:all) {int n=slot.output()?output++:input++;visible.add(new NativeRecipeLayout.Slot(slot.path(),(slot.output()?112:0)+n%3*20,n/3*20,slot.output()));}
            slots=List.copyOf(visible);
        } else slots=layout.slots();
        jeiPreview=null;jeiSlots=List.of();previewError="";
        previewGeneration=RecipeJeiBridge.generation();
        boolean thirdParty=!session.vanilla;
        if(thirdParty) {
            try {
                jeiPreview=RecipeJeiBridge.editor(KineticResourceIds.parse(session.target),json);
            } catch(RuntimeException error) {previewError="jei_preview_failed";}
            if(jeiPreview!=null)bindJeiSlots();
            else previewError="";
        }
        stacks=slots.stream().map(slot->{
            var value=session.document.at(slot.path());
            if(slot.path().size()==2 && slot.path().get(0).equals("sequence") && value.isJsonObject() && value.getAsJsonObject().has("type")
                    && value.getAsJsonObject().get("type").isJsonPrimitive())
                return NativeRecipeStacks.read(new JsonPrimitive(NativeRecipeStations.icon(value.getAsJsonObject().get("type").getAsString())));
            var items=NativeRecipeStacks.read(value);
            if(layout.kind().equals("stonecutting")&&slot.output()&&json.has("count")) {
                int count=json.get("count").getAsInt();items=items.stream().map(stack->{var copy=stack.copy();copy.setCount(count);return copy;}).toList();
            }
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
        scale=1.0f;
        updateDiagramGeometry();
        if(selected>=slots.size())selected=-1;
    }
    private void bindJeiSlots() {
        var view=NativeRecipeView.at(session.document,session.viewPath);
        var candidates=new ArrayList<>(view.layout(0).slots());
        if(layout.kind().equals("create_sequence"))for(int page=1;page<diagramPages;page++)
            for(var slot:view.layout(page).slots())if(!candidates.contains(slot))candidates.add(slot);
        // A shaped layout lists a pattern key once per cell; the recipe has that key once, so keep one entry per path.
        var seenPaths=new HashSet<List<String>>();candidates.removeIf(slot->!seenPaths.add(slot.path()));
        var tokens=candidates.stream().map(slot->{
            Set<String> values=new LinkedHashSet<>();var value=session.document.at(slot.path());
            for(var stack:NativeRecipeStacks.read(value)){values.add("item:"+KineticRegistries.items().id(stack.getItem()));values.add("count:"+stack.getCount());}
            var icon=NativeRecipeVisuals.read(value);if(icon!=null)values.add("resource:"+icon.id());
            // A mob shown by the viewer is identified by its entity type ID.
            for(var field:NativeRecipeLayout.fields(session.document,slot.path()))if(isEntity(field))values.add("resource:"+valueAt(field).getAsString());
            return values;
        }).toList();
        var displayed=jeiPreview.slots().stream().filter(RecipeJeiBridge.EditorSlot::editable).toList();
        int[] binding=JeiRecipeSlotBindings.match(displayed.stream().map(RecipeJeiBridge.EditorSlot::output).toList(),
                displayed.stream().map(RecipeJeiBridge.EditorSlot::ingredients).toList(),
                candidates.stream().map(NativeRecipeLayout.Slot::output).toList(),tokens);
        var mapped=new ArrayList<NativeRecipeLayout.Slot>();var bounds=new ArrayList<RecipeJeiBridge.EditorSlot>();
        for(int i=0;i<binding.length;i++)if(binding[i]>=0) {
            var visible=displayed.get(i);var source=candidates.get(binding[i]);
            mapped.add(new NativeRecipeLayout.Slot(source.path(),visible.x(),visible.y(),source.output()));bounds.add(visible);
        }
        slots=List.copyOf(mapped);jeiSlots=List.copyOf(bounds);
        if(Boolean.getBoolean("contentstudio.nativeCoverage")&&mapped.size()<displayed.size()) {
            var log=org.slf4j.LoggerFactory.getLogger(NativeRecipeEditorPage.class);
            for(int i=0;i<displayed.size();i++)log.info("CONTENT_COVERAGE_SHOWN {} #{} {} bound={} {}",session.id,i,displayed.get(i).role(),binding[i],displayed.get(i).ingredients().stream().limit(4).toList());
            for(int j=0;j<candidates.size();j++)log.info("CONTENT_COVERAGE_CANDIDATE {} #{} out={} {} {}",session.id,j,candidates.get(j).output(),String.join("/",candidates.get(j).path()),tokens.get(j).stream().limit(4).toList());
        }
        layout=new NativeRecipeLayout("jei",jeiPreview.width(),jeiPreview.height(),slots);diagramPages=1;
    }
    private List<String> selectedPath() {return selected<0?session.viewPath:slots.get(selected).path();}
    private int diagramWidth() { return fields.isEmpty()?620:400; }
    private int diagramRight() { return DIAGRAM_X+diagramWidth(); }
    private boolean insideDiagram(double x,double y) {
        return x>=DIAGRAM_X && x<diagramRight() && y>=DIAGRAM_Y && y<DIAGRAM_Y+DIAGRAM_HEIGHT;
    }
    private void updateDiagramGeometry() {
        horizontal.updateContent(layout.width(),diagramWidth(),1);
        vertical.updateContent(layout.height(),DIAGRAM_HEIGHT,1);
        positionDiagram();
    }
    private void positionDiagram() {
        diagramX=DIAGRAM_X+Math.max(0,(diagramWidth()-layout.width())/2)-(int)Math.round(horizontal.smoothOffset());
        diagramY=DIAGRAM_Y+Math.max(0,(DIAGRAM_HEIGHT-layout.height())/2)-(int)Math.round(vertical.smoothOffset());
    }
    @Override protected void build(KineticUi ui) {
        bindDeleteAction(() -> !session.busy && (selected >= 0 || session.added && session.edited),
                () -> { if (selected >= 0) removeSelectedSlot(); else confirmRecipeRestore(); });
        fieldIcons.clear();
        refreshDiagram();
        ui.textField(48,24,232).enabled(!session.busy).value(session.target).maxLength(256).onChange(v->session.target=v).build();
        fields=NativeRecipeLayout.fields(session.document,selectedPath());
        if(selected<0)fields=NativeRecipeLayout.parameters(session.document,session.viewPath);
        else if(slots.get(selected).output()) {
            var path=selectedPath();var value=session.document.at(path);
            if(value.isJsonObject() && value.getAsJsonObject().has("item") && value.getAsJsonObject().get("item").isJsonObject()) {
                path=NativeRecipeLayout.append(path,"item");value=session.document.at(path);
            }
            if(value.isJsonObject() && (value.getAsJsonObject().has("item") || value.getAsJsonObject().has("id")) && !value.getAsJsonObject().has("count")) {
                var all=new ArrayList<>(fields);all.add(NativeRecipeLayout.append(path,"count"));fields=List.copyOf(all);
            }
        }
        fields=fields.stream().distinct().sorted(Comparator.comparingInt(p->switch(p.get(p.size()-1)){case "id","item","tag"->0;case "count","amount"->1;case "chance"->2;default->3;})).toList();
        // A single identity already has the same picker on the diagram. Keep quantities and every other property;
        // the complete slot structure remains available through Slot Fields and Advanced.
        if(selected>=0 && nativeIcons.get(selected)==null && fields.stream().noneMatch(this::isEntity)) {
            var identities=fields.stream().filter(field->kind(field,valueAt(field))==FieldKind.ITEM||kind(field,valueAt(field))==FieldKind.TAG).toList();
            if(identities.size()==1 && identities.get(0).size()<=selectedPath().size()+1 && !session.invalid.containsKey(identities.get(0)))
                fields=fields.stream().filter(field->!field.equals(identities.get(0))).toList();
        }
        updateDiagramGeometry();
        fieldPage=Math.min(fieldPage,Math.max(0,(fields.size()-1)/FIELD_ROWS));
        for(int row=0;row<FIELD_ROWS;row++) {
            int index=fieldPage*FIELD_ROWS+row;if(index>=fields.size())break;
            buildFieldControl(ui,fields.get(index),FIELD_Y+row*FIELD_PITCH);
        }
        // The actions share the ID row at the top, right of the ID field.
        ui.button(284,24,36).text(tr("parameters")).onClick(()->{selected=-1;fieldPage=0;rebuild();}).build();
        ui.button(324,24,56).text(tr("slot_fields")).enabled(selected>=0&&!session.busy).onClick(()->{
            var path=selectedPath();
            var value=session.document.at(path);
            if((value.isJsonPrimitive()||value.isJsonNull())&&!path.isEmpty())path=path.subList(0,path.size()-1);
            openChild(new NativeRecipeFieldsPage(session,path));
        }).build();
        var root=NativeRecipeView.at(session.document,session.viewPath).recipe();
        if(NativeRecipeDocument.serializerId(root).equals("create:sequenced_assembly"))
            ui.button(384,24,56).enabled(!session.busy).text(tr("sequence")).tooltip(tr("sequence_hint")).onClick(()->openChild(new NativeRecipeSequencePage(session))).build();
        else if(diagramPages==1)ui.button(384,24,56).enabled(!session.busy).text(tr("add_material")).tooltip(tr("add_material_hint")).onClick(this::addMaterial).build();
        if(fields.size()>FIELD_ROWS) {
            ui.button(424,334,36).text(Component.literal("<")).enabled(fieldPage>0).onClick(()->{fieldPage--;rebuild();}).build();
            ui.button(596,334,36).text(Component.literal(">")).enabled((fieldPage+1)*FIELD_ROWS<fields.size()).onClick(()->{fieldPage++;rebuild();}).build();
        }
        if(diagramPages>1) {
            ui.button(4,334,40).text(Component.literal("<")).enabled(diagramPage>0).onClick(()->{diagramPage--;selected=-1;rebuild();}).build();
            ui.button(48,334,40).text(Component.literal(">")).enabled(diagramPage+1<diagramPages).onClick(()->{diagramPage++;selected=-1;rebuild();}).build();
        }
        // The scrollbars are controls of their own along the diagram's bottom and right edges. A control over the whole
        // diagram would hide the slot tooltips (a foreground control without a tooltip hides the ones behind it), so
        // the wheel over the diagram is handled by the page instead.
        ui.add(new dev.xyat.kineticcore.api.client.gui.widget.KineticCustomControl(DIAGRAM_X,DIAGRAM_Y+DIAGRAM_HEIGHT,diagramWidth(),6) {
            @Override protected void render(KineticGraphics g,int mx,int my,float pt) {horizontal.renderHorizontal(g,mx,my,DIAGRAM_X,DIAGRAM_Y+DIAGRAM_HEIGHT+2,diagramWidth(),3,20);}
            @Override protected boolean onMouseClick(MouseInput input) {return horizontal.beginHorizontalDrag(input.x(),input.y(),input.button(),DIAGRAM_X,DIAGRAM_Y+DIAGRAM_HEIGHT+2,diagramWidth(),3,20);}
            @Override protected boolean onMouseDrag(dev.xyat.kineticcore.api.client.gui.input.MouseDragInput input) {return horizontal.dragHorizontal(input.x(),DIAGRAM_X,diagramWidth(),20);}
            @Override protected boolean onMouseRelease(MouseInput input) {return horizontal.release(input.button());}
        });
        ui.add(new dev.xyat.kineticcore.api.client.gui.widget.KineticCustomControl(diagramRight(),DIAGRAM_Y,6,DIAGRAM_HEIGHT) {
            @Override protected void render(KineticGraphics g,int mx,int my,float pt) {vertical.render(g,mx,my,diagramRight()+2,DIAGRAM_Y,3,DIAGRAM_HEIGHT,20);}
            @Override protected boolean onMouseClick(MouseInput input) {return vertical.beginDrag(input.x(),input.y(),input.button(),diagramRight()+2,DIAGRAM_Y,3,DIAGRAM_HEIGHT,20);}
            @Override protected boolean onMouseDrag(dev.xyat.kineticcore.api.client.gui.input.MouseDragInput input) {return vertical.drag(input.y(),DIAGRAM_Y,DIAGRAM_HEIGHT,20);}
            @Override protected boolean onMouseRelease(MouseInput input) {return vertical.release(input.button());}
        });
        ui.button(4,4,40).text(tr("back")).enabled(!session.busy).onClick(this::close).build();
        ui.button(444,24,36).text(tr("advanced")).enabled(!session.busy).onClick(()->openChild(new NativeRecipeFieldsPage(session,List.of()))).build();
        // An original recipe can be taken out of the game here, or brought back; recipes added by players use Delete instead.
        if(!session.added&&!session.creating)ui.button(484,24,56).text(tr(session.removed?"enable_recipe":"disable_recipe")).tooltip(tr(session.removed?"enable_recipe_hint":"disable_recipe_hint"))
                .enabled(!session.busy).onClick(()->send(session.removed?"enable":"disable")).build();
        ui.button(544,24,48).text(tr(session.added?"delete_recipe":"restore")).enabled(session.edited&&!session.busy).onClick(this::confirmRecipeRestore).build();
        ui.button(596,24,40).text(tr("save")).enabled(!session.busy).onClick(()->send("save")).build();
    }
    private enum FieldKind {BOOLEAN,INTEGER,DECIMAL,ITEM,TAG,FLUID,EFFECT,ENTITY,CHOICE,TEXT}
    private record FieldIcon(int x,int y,ItemStack stack) {}
    private final List<FieldIcon> fieldIcons=new ArrayList<>();
    // Keys whose numbers are fractions even when the current value happens to be whole.
    private static final Set<String> DECIMAL_KEYS=Set.of("chance","experience","xp","exp","probability","saturation","saturationModifier","multiplier");
    private static final Set<String> FLUID_KEYS=Set.of("fluid","fluidName","FluidName","fluid_id");
    private static final Set<String> EFFECT_KEYS=Set.of("effect","mob_effect","potion_effect");
    // Closed value sets that players pick from instead of typing.
    private static final Map<String,List<String>> CHOICES=Map.of("heatRequirement",List.of("none","heated","superheated"));
    private FieldKind kind(List<String> field,JsonPrimitive value) {
        String key=field.get(field.size()-1);
        if(value.isBoolean())return FieldKind.BOOLEAN;
        if(value.isNumber())return DECIMAL_KEYS.contains(key)||value.getAsString().contains(".")?FieldKind.DECIMAL:FieldKind.INTEGER;
        if(CHOICES.containsKey(key))return FieldKind.CHOICE;
        if(isEntity(field))return FieldKind.ENTITY;
        String text=value.getAsString();
        if(key.equals("tag")||text.startsWith("#"))return FieldKind.TAG;
        var id=KineticResourceIds.tryParse(text);
        if(id!=null&&(FLUID_KEYS.contains(key)||KineticRegistries.fluids().contains(id)&&!KineticRegistries.items().contains(id)))return FieldKind.FLUID;
        if(id!=null&&EFFECT_KEYS.contains(key)&&KineticRegistries.mobEffects().contains(id))return FieldKind.EFFECT;
        if(isItem(field,value))return FieldKind.ITEM;
        return FieldKind.TEXT;
    }
    /** One property row's control, chosen from the field's value so nothing needs a typed field name or ID. */
    private void buildFieldControl(KineticUi ui,List<String> field,int y) {
        var value=valueAt(field);var tooltip=fieldTooltip(field);boolean enabled=!session.busy;
        int x=FIELD_CONTROL_X,w=FIELD_CONTROL_W;
        switch(kind(field,value)) {
            case BOOLEAN -> ui.toggle(x,y,w).enabled(enabled).value(value.getAsBoolean()).labels(tr("field.true"),tr("field.false"))
                    .tooltip(tooltip).onChange(v->edit(field,v.toString())).build();
            case INTEGER,DECIMAL -> ui.numberField(x,y,w,kind(field,value)==FieldKind.INTEGER?dev.xyat.kineticcore.api.client.gui.ui.NumberType.LONG:dev.xyat.kineticcore.api.client.gui.ui.NumberType.DECIMAL)
                    .enabled(enabled).allowNegative(true).value(value.getAsNumber()).tooltip(tooltip).onChange(v->edit(field,v)).build();
            case ITEM,TAG -> {
                var stacks=NativeRecipeStacks.read(NativeRecipeItemData.preview(session.document,field));
                var icon=stacks.isEmpty()?new ItemStack(net.minecraft.world.item.Items.BARRIER):NativeRecipeStacks.frame(stacks,System.currentTimeMillis());
                Component name=value.getAsString().startsWith("#")||field.get(field.size()-1).equals("tag")
                        ?Component.literal("#"+value.getAsString().replaceFirst("^#","")):stacks.isEmpty()?Component.literal(value.getAsString()):icon.getHoverName();
                // A standard-height button keeps every row the same height; the item icon is drawn in its own slot to the left.
                fieldIcons.add(new FieldIcon(x,y,icon));
                ui.button(x+18,y,w-18).enabled(enabled).text(name).tooltip(tooltip.copy().append("\n").append(tr("pick_hint"))).onClick(()->{if(!session.busy)pick(field);}).build();
            }
            case FLUID -> ui.button(x,y,w).enabled(enabled).text(idName(NativeRecipeIdPicker.fluids(),value.getAsString())).tooltip(tooltip.copy().append("\n").append(tr("pick_hint")))
                    .onClick(()->openChild(new NativeRecipeIdPicker(fieldLabel(field),NativeRecipeIdPicker.fluids(),value.getAsString(),id->{edit(field,id);rebuild();}))).build();
            case EFFECT -> ui.button(x,y,w).enabled(enabled).text(idName(NativeRecipeIdPicker.effects(),value.getAsString())).tooltip(tooltip.copy().append("\n").append(tr("pick_hint")))
                    .onClick(()->openChild(new NativeRecipeIdPicker(fieldLabel(field),NativeRecipeIdPicker.effects(),value.getAsString(),id->{edit(field,id);rebuild();}))).build();
            case ENTITY -> ui.button(x,y,w).enabled(enabled).text(entityName(value.getAsString())).tooltip(tooltip.copy().append("\n").append(tr("pick_hint")))
                    .onClick(()->pick(field)).build();
            case CHOICE -> {
                String key=field.get(field.size()-1);
                var options=CHOICES.get(key).stream().map(choice->new dev.xyat.kineticcore.api.client.gui.widget.KineticDropdown.Option(choice,tr("choice."+key+"."+choice))).toList();
                ui.dropdown(x,y,w,options).enabled(enabled).selected(value.getAsString()).tooltip(tooltip).onChange(v->edit(field,v)).build();
            }
            default -> ui.textField(x,y,w).enabled(enabled).value(session.invalid.getOrDefault(field,value.getAsString())).maxLength(NativeRecipeDocument.MAX_CHARS)
                    .tooltip(tooltip).validator(v->!session.invalid.containsKey(field)).onChange(v->edit(field,v)).build();
        }
    }
    private static Component idName(List<NativeRecipeIdPicker.Choice> choices,String id) {
        for(var choice:choices)if(choice.id().equals(id)&&!choice.name().getString().isEmpty())return choice.name();
        return Component.literal(id);
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
        if(isEntity(field)) {
            KineticSelectors.openEntitySelector(fieldLabel(field),List.of(valueAt(field).getAsString()),values->{if(!values.isEmpty()){edit(field,values.get(0));rebuild();}});return;
        }
        boolean tag=field.get(field.size()-1).equals("tag") || session.document.at(field).getAsString().startsWith("#");
        KineticSelectors.openItemSelector(selection->{
            if(selection==null)return;
            try {
                if(tag && selection.isTag())edit(field,(field.get(field.size()-1).equals("tag")?"":"#")+selection.value());
                else if(!tag && selection.isItem())NativeRecipeItemData.selectField(session.document,field,NativeRecipeStacks.encode(selection.stack()),NativeRecipeItemData.outputField(session.document,field));
            }catch(IllegalArgumentException invalid){KineticOverlays.toast(tr("invalid"));}
            rebuild();
        });
    }
    @Override protected void onTick() {
        if(!stamp.equals(session.document.json().toString())||!previewTarget.equals(session.target)||previewGeneration!=RecipeJeiBridge.generation())rebuild();
        positionDiagram();
        if(jeiPreview!=null)try{jeiPreview.tick();}catch(RuntimeException error){previewError="jei_preview_failed";jeiPreview=null;}
    }
    @Override protected boolean onMouseScroll(dev.xyat.kineticcore.api.client.gui.input.ScrollInput input) {
        if(!insideDiagram(input.x(),input.y()))return false;
        return vertical.canScroll()?vertical.scroll(input.deltaY(),24):horizontal.scroll(input.deltaY(),24);
    }
    @Override protected boolean onMouseClick(MouseInput input) {
        if(session.busy || !input.isLeft() && !input.isRight() || !insideDiagram(input.x(),input.y()))return false;
        for(int i=0;i<slots.size();i++) {
            var slot=slots.get(i);double x=diagramX+slot.x()*scale,y=diagramY+slot.y()*scale;
            int width=jeiPreview==null?slotSize():jeiSlots.get(i).width(),height=jeiPreview==null?slotSize():jeiSlots.get(i).height();
            if(input.x()>=x && input.x()<x+width*scale && input.y()>=y && input.y()<y+height*scale) {
                selected=i;fieldPage=0;rebuild();
                if(input.isRight())openContextMenu(input.x(),input.y(),slotMenu(),184);
                else chooseSelectedSlot();
                return true;
            }
        }return false;
    }
    private static final Set<String> ENTITY_KEYS=Set.of("entity","entity_type","entityType","mob");
    /** A field naming a registered mob; it is chosen in the core's entity selector with model previews. */
    private boolean isEntity(List<String> field) {
        JsonPrimitive value;
        try{value=session.document.at(field).getAsJsonPrimitive();}catch(RuntimeException notPrimitive){return false;}
        if(!value.isString())return false;
        var id=KineticResourceIds.tryParse(value.getAsString());
        if(id==null||!KineticRegistries.entityTypes().contains(id))return false;
        return ENTITY_KEYS.contains(field.get(field.size()-1))||!KineticRegistries.items().contains(id);
    }
    private static Component entityName(String id) {
        var key=KineticResourceIds.tryParse(id);
        var type=key==null?null:KineticRegistries.entityTypes().get(key);
        return type==null?Component.literal(id):type.getDescription();
    }
    private void chooseSelectedSlot() {
        var path=selectedPath();
        boolean output=slots.get(selected).output();
        // A mob slot opens the core entity selector with model previews.
        for(var field:NativeRecipeLayout.fields(session.document,path))if(isEntity(field)){pick(field);return;}
        if(nativeIcons.get(selected)!=null) {
            // A fluid slot opens the fluid list; any other resource slot is edited in the properties panel.
            for(var field:NativeRecipeLayout.fields(session.document,path))
                if(kind(field,valueAt(field))==FieldKind.FLUID) {
                    openChild(new NativeRecipeIdPicker(fieldLabel(field),NativeRecipeIdPicker.fluids(),valueAt(field).getAsString(),id->{edit(field,id);rebuild();}));return;
                }
            return;
        }
        KineticSelectors.openItemSelector(selection->{
            if(selection==null||selection.isMod()||output&&selection.isTag())return;
            String id=selection.isTag()?selection.value():KineticRegistries.items().id(selection.stack().getItem()).toString();
            try {
                if(selection.isTag()) {var tag=new JsonObject();tag.addProperty("tag",id);NativeRecipeItemData.select(session.document,path,tag,false);}
                else NativeRecipeItemData.select(session.document,path,NativeRecipeStacks.encode(selection.stack()),output);
            }catch(IllegalArgumentException invalid){KineticOverlays.toast(tr("invalid"));return;}
            session.invalid.keySet().removeIf(field->field.size()>=path.size()&&field.subList(0,path.size()).equals(path));rebuild();
        });
    }
    private List<KineticOverlays.MenuItem> slotMenu() {
        var path=selectedPath();
        var actions=new ArrayList<KineticOverlays.MenuItem>();
        // The selected slot's quantity, chance and other fields are edited in the properties panel on the right.
        if(!path.isEmpty()&&session.document.at(path.subList(0,path.size()-1)).isJsonArray())
            actions.add(KineticOverlays.MenuItem.action(tr("duplicate_material"),()->{if(!session.invalid.isEmpty()){KineticOverlays.toast(tr("invalid"));return;}session.document.duplicate(path);selected=-1;rebuild();}));
        actions.add(KineticOverlays.MenuItem.danger(tr("remove_slot"),this::removeSelectedSlot));return List.copyOf(actions);
    }
    private void addMaterial() {
        if(!session.invalid.isEmpty()){KineticOverlays.toast(tr("invalid"));return;}
        var paths=new LinkedHashSet<List<String>>();var root=NativeRecipeView.at(session.document,session.viewPath);
        for(var slot:root.layout(0).slots())if(!slot.output()&&!slot.path().isEmpty()) {
            var parent=slot.path().subList(0,slot.path().size()-1);if(session.document.at(parent).isJsonArray())paths.add(parent);
        }
        if(paths.isEmpty()){openChild(new NativeRecipeFieldsPage(session,session.viewPath));return;}
        openContextMenu(184,336,paths.stream().map(path->KineticOverlays.MenuItem.action(Component.literal(String.join(" / ",path)),()->{
            var array=session.document.at(path).getAsJsonArray();
            if(array.isEmpty()){openChild(new NativeRecipeFieldsPage(session,path));return;}
            session.document.duplicate(NativeRecipeLayout.append(path,Integer.toString(array.size()-1)));rebuild();
            var added=NativeRecipeLayout.append(path,Integer.toString(array.size()));
            openChild(new NativeRecipeFieldsPage(session,session.document.at(added).isJsonPrimitive()?path:added));
        })).toList(),196);
    }
    private void confirmRecipeRestore() {
        openDialog(tr(session.added?"delete_recipe":"restore"), tr(session.added?"delete_recipe_confirm":"restore_confirm"),
                tr(session.added?"delete_recipe":"restore"), tr("back"), () -> send("restore"), () -> {});
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
        positionDiagram();
        KineticTheme.panel(g,0,0,640,360);
        // The workstation is named after its JEI category, so a Create crusher reads "Recipe Editor · Crushing".
        g.scrollingTextCentered(jeiPreview==null?title():title().copy().append(" · ").append(jeiPreview.category()),320,8,2*(320-48),0xFFFFAA00,false);g.scrollingText(tr("id"),4,28,40,0xFFFFFFFF,false);
        KineticTheme.surface(g,4,44,fields.isEmpty()?632:412,312,KineticTheme.Surface.PANEL_ALT);
        if(!fields.isEmpty()) {
            KineticTheme.surface(g,420,44,216,312,KineticTheme.Surface.PANEL_ALT);
            g.vLine(418,44,356,KineticTheme.current().accentHover());
            g.scrollingText(selected<0?tr("parameters"):tr(slots.get(selected).output()?"output_slot":"input_slot"),424,48,208,0xFFFFAA00,false);
        }
        g.hLine(4,636,44,KineticTheme.current().accentHover());
        // Each row: the translated field name on the left, its control on the right, both centred on the row.
        for(int row=0;row<FIELD_ROWS;row++) {int i=fieldPage*FIELD_ROWS+row;if(i<fields.size())g.scrollingText(fieldLabel(fields.get(i)),FIELD_LABEL_X,FIELD_Y+row*FIELD_PITCH+4,FIELD_LABEL_W,0xFFFFFFFF,false);}
        for(var icon:fieldIcons){KineticTheme.itemSlot(g,icon.x(),icon.y(),16,4,false);KineticTheme.item(g,icon.stack(),icon.x(),icon.y(),16,0.75F,false);}
        if(fields.size()>FIELD_ROWS)g.scrollingTextCentered(Component.literal((fieldPage+1)+" / "+Math.max(1,(fields.size()+FIELD_ROWS-1)/FIELD_ROWS)),528,324,128,0xFFFFFFFF,false);
        if(!previewError.isEmpty()) {g.wrappedText(tr(previewError),DIAGRAM_X,DIAGRAM_Y+16,diagramWidth(),0xFFAAAAAA);return;}
        if(jeiPreview!=null&&previewGeneration==RecipeJeiBridge.generation()) {
            g.clipped(DIAGRAM_X,DIAGRAM_Y,diagramRight(),DIAGRAM_Y+DIAGRAM_HEIGHT,()->{
                g.push();try {
                    g.translate(diagramX,diagramY);jeiPreview.draw(g,mx-diagramX,my-diagramY);
                    for(int i=0;i<slots.size();i++) {
                        var slot=slots.get(i);var bounds=jeiSlots.get(i);
                        if(!ingredientTags.get(i).isEmpty())RecipeSlots.tagMarker(g,slot.x(),slot.y(),Math.min(bounds.width(),bounds.height()),1);
                        if(i==selected)KineticTheme.stateOutline(g,slot.x(),slot.y(),bounds.width(),bounds.height(),true,false,false);
                    }
                }catch(RuntimeException error){previewError="jei_preview_failed";jeiPreview=null;}finally{g.pop();}
            });return;
        }
        g.scissor(DIAGRAM_X,DIAGRAM_Y,diagramRight(),DIAGRAM_Y+DIAGRAM_HEIGHT);
        try {
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
        } finally {g.endScissor();}
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
        if(layout.kind().equals("goety_ritual")) {
            var pedestal=NativeRecipeStacks.read(new JsonPrimitive("goety:pedestal"));
            for(var slot:slots)if(slot.path().size()>=2&&slot.path().get(slot.path().size()-2).equals("ingredients")&&!pedestal.isEmpty())g.item(pedestal.get(0),slot.x()+1,slot.y()+20);
            var altar=NativeRecipeStacks.read(new JsonPrimitive("goety:dark_altar"));
            if(!altar.isEmpty()){g.item(altar.get(0),57,75);g.item(altar.get(0),152,75);}
            recipeArrow(g,124,52);return;
        }
        if(layout.kind().equals("stonecutting")||layout.kind().equals("goety_infuser")) {
            recipeArrow(g,28,8);return;
        }
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
                var icon=NativeRecipeStacks.read(new JsonPrimitive(NativeRecipeStations.icon(NativeRecipeView.at(session.document,session.viewPath).recipe().get("type").getAsString())));
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
    private Component fieldLabel(List<String> path) {
        String key=path.get(path.size()-1);
        return fieldLabel(NativeRecipeView.at(session.document,session.viewPath).recipe(),key);
    }
    static Component fieldLabel(JsonObject json,String key) {
        String type=NativeRecipeDocument.serializerId(json);
        String mod=type.substring(0,type.indexOf(':'));
        if(mod.equals("goety")&&key.equals("duration"))return tr("field.seconds");
        if(dev.xyat.kineticcore.api.text.KineticI18n.hasTranslation("jei."+mod+"."+key)) {
            String label=dev.xyat.kineticcore.api.text.KineticI18n.translatable("jei."+mod+"."+key, "").getString().replaceAll("[:：]\\s*$", "");
            if(!label.contains("%s")&&!key.equals("duration"))return Component.literal(label);
        }
        String known=switch(key) {case "id","item"->"item";case "tag"->"tag";case "count","amount","Amount"->"quantity";
            case "ritual_type"->"ritual_type";case "craftType"->"ritual_kind";case "soulCost"->"soul_cost";case "research"->"research";
            case "chance"->"chance";case "cookingtime","cookingTime","processingTime","processing_time","duration","time","timeCost","smeltingTime"->"time";
            case "experience"->"experience";case "loops"->"loops";case "heatRequirement"->"heat";
            case "energy","energyCost"->"energy";case "acceptMirrored"->"mirror";case "keepHeldItem"->"keep_tool";
            case "from"->"from";case "to"->"to";case "reversible"->"reversible";case "temperature"->"temperature";
            case "fluid","fluidName","FluidName","fluid_id"->"fluid";case "group"->"group";case "category"->"category";
            case "show_notification"->"show_notification";case "weight"->"weight";case "level","tier"->"level";
            case "damage"->"damage";case "mana","source"->"mana";case "sound"->"sound";case "probability"->"chance";
            case "xp","exp"->"experience";case "cookTime","cooking_time","cook_time","burnTime","burn_time"->"time";
            case "effect","mob_effect"->"effect";case "entity","entity_type"->"entity";case "block"->"block";
            case "container","holder","holderItem","holder_item","bowl"->"container";
            case "saturation","saturationBonus","saturation_bonus","saturationModifier","saturation_modifier","saturationBonusModifier"->"saturation";
            case "nutrition","hunger","food"->"nutrition";case "capacity","capacityExtra","capacity_extra"->"capacity";
            case "min","minimum"->"min";case "max","maximum"->"max";case "score"->"score";case "bonus"->"bonus";
            case "type"->"recipe_type";case "ingredients","ingredient","inputs","input"->"ingredients";
            case "result","results","output","outputs"->"result";case "key"->"pattern_key";case "pattern"->"pattern";
            case "base"->"base";case "addition"->"addition";case "template"->"template";case "nbt"->"nbt";
            case "components"->"components";case "conditions","forge:conditions","neoforge:conditions"->"conditions";
            default->null;};
        return known==null?Component.literal(humanize(key)):tr("field."+known);
    }
    // An unmapped key reads as words ("processingTime" -> "Processing Time"); the raw key stays in the tooltip.
    private static String humanize(String key) {
        var words=key.replaceAll("([a-z0-9])([A-Z])","$1 $2").replace('_',' ').replace('-',' ').trim().split("\\s+");
        var result=new StringBuilder();
        for(String word:words)if(!word.isEmpty())result.append(result.length()==0?"":" ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        return result.length()==0?key:result.toString();
    }
    private Component fieldTooltip(List<String> path) {
        String key=path.get(path.size()-1);
        Component label=fieldLabel(path).copy().append("\n").append(Component.literal(key).withStyle(net.minecraft.ChatFormatting.GRAY));
        if(key.equals("craftType")) {
            String translation=dev.xyat.kineticcore.api.client.search.KineticSearch.resolveTranslation("jei.goety.craftType."+valueAt(path).getAsString());
            if(translation!=null)label=label.copy().append("\n").append(Component.literal(translation).withStyle(net.minecraft.ChatFormatting.GOLD));
        }
        if(Set.of("ritual_type","craftType","soulCost","duration","research").contains(key)
                &&NativeRecipeDocument.serializerId(NativeRecipeView.at(session.document,session.viewPath).recipe()).startsWith("goety:"))
            label=label.copy().append("\n").append(tr("goety."+key+".hint"));
        return label;
    }
    // Every slot says what it is and what clicking does, so input and output slots are never confused.
    @Override protected void renderTooltips(int mx,int my) {
        for(var icon:fieldIcons)if(mx>=icon.x()&&mx<icon.x()+16&&my>=icon.y()&&my<icon.y()+16) {
            if(!icon.stack().isEmpty())showItemTooltip(icon.stack());
            return;
        }
        if(!insideDiagram(mx,my))return;
        for(int i=0;i<slots.size();i++) {
            var s=slots.get(i);float x=diagramX+s.x()*scale,y=diagramY+s.y()*scale;
            int width=jeiPreview==null?slotSize():jeiSlots.get(i).width(),height=jeiPreview==null?slotSize():jeiSlots.get(i).height();
            if(mx>=x && mx<x+width*scale && my>=y && my<y+height*scale) {
                var lines=new ArrayList<Component>();var choices=stacks.get(i);
                for(String id:ingredientTags.get(i))lines.add(Component.literal("#"+id).withStyle(net.minecraft.ChatFormatting.GOLD));
                lines.add(slotRole(s.output()));
                String mob=null;for(var field:NativeRecipeLayout.fields(session.document,s.path()))if(isEntity(field)){mob=valueAt(field).getAsString();break;}
                if(mob!=null){lines.add(0,entityName(mob));lines.add(tr("slot_hint.entity"));}
                else if(nativeIcons.get(i)!=null)lines.add(tr("slot_hint.resource"));
                else lines.add(tr(s.output()?"slot_hint.output":"slot_hint.input"));
                lines.add(tr("slot_hint.menu"));
                if(!choices.isEmpty())showItemTooltip(NativeRecipeStacks.frame(choices,System.currentTimeMillis()),lines);
                else showTooltip(lines,260);
                return;
            }
        }
        // A slot the viewer shows but that is not tied to a recipe field can still be changed under Advanced.
        if(jeiPreview==null)return;
        for(var slot:jeiPreview.slots()) {
            if(!slot.editable()||jeiSlots.contains(slot))continue;
            if(mx>=diagramX+slot.x()&&mx<diagramX+slot.x()+slot.width()&&my>=diagramY+slot.y()&&my<diagramY+slot.y()+slot.height()) {
                var lines=new ArrayList<Component>();
                if(!slot.name().isEmpty())lines.add(Component.literal(slot.name()));
                lines.add(slotRole(slot.output()));lines.add(tr("slot_hint.unbound"));
                showTooltip(lines,260);return;
            }
        }
    }
    private static Component slotRole(boolean output) {
        return tr(output?"slot_role.output":"slot_role.input").copy().withStyle(output?net.minecraft.ChatFormatting.GOLD:net.minecraft.ChatFormatting.AQUA);
    }
    @Override protected void renderForeground(KineticGraphics g,int mx,int my,float pt) {
        if(jeiPreview==null||previewGeneration!=RecipeJeiBridge.generation()||!insideDiagram(mx,my))return;
        for(int i=0;i<slots.size();i++) {
            var slot=slots.get(i);var bounds=jeiSlots.get(i);
            if(mx>=diagramX+slot.x()&&mx<diagramX+slot.x()+bounds.width()
                    &&my>=diagramY+slot.y()&&my<diagramY+slot.y()+bounds.height())return;
        }
        // Unbound ingredient slots also get the editor's own tooltip instead of the viewer's.
        for(var slot:jeiPreview.slots())if(slot.editable()&&mx>=diagramX+slot.x()&&mx<diagramX+slot.x()+slot.width()
                &&my>=diagramY+slot.y()&&my<diagramY+slot.y()+slot.height())return;
        g.push();try{g.translate(diagramX,diagramY);jeiPreview.drawOverlays(g,mx-diagramX,my-diagramY);}
        catch(RuntimeException error){previewError="jei_preview_failed";jeiPreview=null;}finally{g.pop();}
    }
    private void send(String action) {
        if(session.busy)return;
        if(action.equals("save") && (!session.invalid.isEmpty() || KineticResourceIds.tryParse(session.target)==null)){KineticOverlays.toast(tr("invalid"));return;}
        if(action.equals("save")&&session.creating)action="create";
        session.busy=true;rebuild();NativeRecipeClient.expect(NativeRecipeNetwork.request(action,session.id,action.equals("restore")?session.id:session.target,"",0,session.catalog,session.revision,action.equals("save")||action.equals("create")?session.document.json().toString():""),callback);
    }
    private void accept(NativeRecipeNetwork.Response packet) {
        if(!isAttached())return;session.busy=false;var body=JsonParser.parseString(packet.body()).getAsJsonObject();
        String action=body.has("action")?body.get("action").getAsString():"";
        if(packet.success()&&(action.equals("disable")||action.equals("enable"))) {
            // Only the removal rule changed: keep the draft, take the new catalog version so later saves are not stale.
            session.removed=body.get("removed").getAsBoolean();session.catalog=body.get("catalog").getAsLong();session.revision=body.get("revision").getAsString();
            RecipeEditSessionState.markPendingRecipeApply();
            KineticOverlays.toast(tr(session.removed?"disabled":"enabled"));session.refresh.run();rebuild();return;
        }
        if(packet.success()) {
            // Saves are applied together when the recipe pages are left; "pending" says a datapack reload is among them.
            RecipeEditSessionState.markPendingRecipeApply();
            KineticOverlays.toast(tr(body.has("pending")&&body.get("pending").getAsBoolean()?"saved_pending":"saved"));session.refresh.run();
            if(body.has("deleted")&&body.get("deleted").getAsBoolean())navigateBack();else {session.saved(body);rebuild();}
        }else {NativeRecipeBrowserPage.notifyError(body);rebuild();}
    }
    @Override protected boolean onCloseRequested() {
        if(session.busy)return true;
        if(session.creating || !session.invalid.isEmpty() || !stamp.equals(session.original) || !session.id.equals(session.target)) {
            openDialog(tr("discard"),tr("discard_confirm"),tr("discard"),tr("back"),this::navigateBack,()->{});return true;
        }return false;
    }
}
