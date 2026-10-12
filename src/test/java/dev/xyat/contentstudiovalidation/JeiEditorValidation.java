package dev.xyat.contentstudiovalidation;

import com.google.gson.*;
import dev.xyat.contentstudio.recipe.RecipeMemoryManager;
import dev.xyat.contentstudio.recipe.client.RecipeJeiBridge;
import dev.xyat.contentstudio.recipe.client.gui.*;
import dev.xyat.contentstudio.recipe.nativeedit.*;
import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.client.gui.KineticGui;
import dev.xyat.kineticcore.api.client.gui.input.*;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Opt-in validation against real installed JEI categories. Never included in a release JAR. */
public final class JeiEditorValidation {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(JeiEditorValidation.class);
    private static List<JsonObject> samples;
    private static boolean started,finished,checked;
    private static long deadline,due;
    private static int phase,index,captures,visuals,fieldsOnly,hidden;
    private static CompletableFuture<?> reload;
    public static void install() {
        deadline=System.currentTimeMillis()+240_000;
        KineticClientEvents.onTick(KineticClientEvents.TickPhase.END,JeiEditorValidation::tick);
    }
    private static void tick() {
        if(finished)return;
        try {
            GuiLongTextValidation.prepareOwnedWorld();var mc=Minecraft.getInstance();
            if(System.currentTimeMillis()>deadline)throw new AssertionError("JEI editor validation timeout");
            if(!started) {
                if(mc.player==null||mc.getSingleplayerServer()==null)return;
                if(!Boolean.getBoolean("contentstudio.jeiEditorValidation.withoutJei")&&!RecipeJeiBridge.jeiAvailable())return;
                started=true;mc.options.guiScale().set(0);mc.getWindow().setWindowed(1920,1080);mc.resizeDisplay();
                mc.getSingleplayerServer().execute(()->prepare(mc));return;
            }
            if(samples==null)return;
            if(reload!=null) {if(!reload.isDone()||mc.getOverlay()!=null)return;reload.join();reload=null;due=System.currentTimeMillis()+800;open();return;}
            if(System.currentTimeMillis()<due)return;
            if(index<samples.size()) {
                if(!checked) {checkCurrent();checked=true;due=System.currentTimeMillis()+250;return;}
                capture();checked=false;index++;open();return;
            }
            if(phase++==0) {index=0;mc.getLanguageManager().setSelected("zh_cn");mc.options.languageCode="zh_cn";reload=mc.reloadResourcePacks();return;}
            finished=true;LOG.info("CONTENT_JEI_EDITOR_PASS visuals={} fieldsOnly={} hidden={} captures={} jei={}",visuals,fieldsOnly,hidden,captures,RecipeJeiBridge.jeiAvailable());
            if(Boolean.getBoolean("contentstudio.jeiEditorValidation.thenMatrix"))NativeRecipeMatrixValidation.install();
            else if(Boolean.getBoolean("contentstudio.jeiEditorValidation.thenGui"))GuiLongTextValidation.install();
            else mc.stop();
        }catch(Throwable error){finished=true;LOG.error("CONTENT_JEI_EDITOR_FAIL phase="+phase+" index="+index,error);Minecraft.getInstance().stop();}
    }
    private static void prepare(Minecraft mc) {
        try {
            var manager=mc.getSingleplayerServer().getRecipeManager();var byType=new TreeMap<String,JsonObject>();
            String filter=System.getProperty("contentstudio.jeiEditorValidation.mods","");
            for(var entry:RecipeMemoryManager.originalCatalog(manager).sources().entrySet()) {
                if(!RecipeMemoryManager.containsRuntimeRecipe(manager,entry.getKey()))continue;
                var source=entry.getValue().getAsJsonObject();
                var view=NativeRecipeView.resolve(source,branch->RecipeMemoryManager.nativeConditionsMatch(manager,branch));
                String type=NativeRecipeDocument.serializerId(view.recipe()),mod=type.split(":",2)[0];
                if(!filter.isBlank()&&!List.of(filter.split(",")).contains(mod))continue;
                if(type.contains("crafting_special")||type.equals("forge:conditional"))continue;
                var body=new JsonObject();body.addProperty("id",entry.getKey().toString());body.add("recipe",source.deepCopy());
                var path=new JsonArray();view.path().forEach(path::add);body.add("view_path",path);
                body.addProperty("revision","");body.addProperty("catalog",0);body.addProperty("edited",false);
                byType.putIfAbsent(type,body);
            }
            if(byType.isEmpty())throw new AssertionError("No installed recipe samples found");
            mc.execute(()->{
                samples=List.copyOf(byType.values());mc.getLanguageManager().setSelected("en_us");mc.options.languageCode="en_us";
                reload=mc.reloadResourcePacks();LOG.info("CONTENT_JEI_EDITOR_SAMPLES types={}",byType.keySet());
            });
        }catch(Throwable error){mc.execute(()->{finished=true;LOG.error("CONTENT_JEI_EDITOR_FAIL prepare",error);mc.stop();});}
    }
    private static void open() {
        if(index>=samples.size()){due=System.currentTimeMillis()+100;return;}
        var page=NativeRecipeEditorPage.create(samples.get(index),()->{});if(page!=null)KineticGui.open(page);
        dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays.clearToasts();due=System.currentTimeMillis()+650;
    }
    private static void checkCurrent() throws Exception {
        var body=samples.get(index);var json=NativeRecipeView.at(new NativeRecipeDocument(body.getAsJsonObject("recipe")),
                body.getAsJsonArray("view_path").asList().stream().map(JsonElement::getAsString).toList()).recipe();
        String type=NativeRecipeDocument.serializerId(json);KineticPage page=KineticGui.currentPage();
        if(!RecipeJeiBridge.jeiAvailable()&&!NativeRecipeCreation.vanilla(type)) {
            if(NativeRecipeEditorPage.create(body,()->{})!=null)throw new AssertionError("Third-party editor remains accessible without JEI");
            hidden++;LOG.info("CONTENT_JEI_HIDDEN_WITHOUT_JEI type={}",type);return;
        }
        if(page instanceof NativeRecipeEditorPage editor) {
            var preview=(RecipeJeiBridge.EditorPreview)field(editor,"jeiPreview");
            if(!type.startsWith("minecraft:")&&preview==null)throw new AssertionError("Third-party visual page lacks a JEI drawable: "+type);
            if(preview!=null) {
                var slots=(List<NativeRecipeLayout.Slot>)field(editor,"slots");
                var bounds=(List<RecipeJeiBridge.EditorSlot>)field(editor,"jeiSlots");
                if(!slots.isEmpty()) {
                    int selected=-1;
                    for(int i=0;i<bounds.size();i++) {
                        int x=(int)field(editor,"diagramX")+bounds.get(i).x()+bounds.get(i).width()/2;
                        int y=(int)field(editor,"diagramY")+bounds.get(i).y()+bounds.get(i).height()/2;
                        if(x<26||x>=400||y<100||y>=266)continue;
                        var screen=Minecraft.getInstance().screen;
                        int canvasX=(int)screen.getClass().getMethod("canvasX").invoke(screen);
                        int canvasY=(int)screen.getClass().getMethod("canvasY").invoke(screen);
                        float scale=(float)screen.getClass().getMethod("canvasScale").invoke(screen);
                        boolean handled=click(canvasX+x*scale,canvasY+y*scale);
                        if((int)field(editor,"selected")!=i) {
                            capture();
                            throw new AssertionError("JEI mouse click selected another slot: "+type
                                +" expected="+i+" actual="+field(editor,"selected")+" logical="+x+","+y
                                +" canvas="+canvasX+","+canvasY+" scale="+scale+" bounds="+bounds.get(i)+" slots="+slots
                                +" afterSlots="+field(editor,"slots")+" error="+field(editor,"previewError")
                                +" busy="+field(field(editor,"session"),"busy")+" handled="+handled
                                +" pageSame="+(KineticGui.currentPage()==editor)+" hits="+screen.children().stream()
                                .filter(child->child.isMouseOver(x,y)).map(child->child.getClass().getName()).toList());
                        }
                        var runtime=field(screen,"runtime");var overlays=runtime.getClass().getMethod("overlays").invoke(runtime);
                        if(field(overlays,"contextMenu")==null)throw new AssertionError("JEI slot menu did not open: "+type);
                        selected=i;break;
                    }
                    if(selected<0)throw new AssertionError("No editable JEI slot is visible: "+type);
                    LOG.info("CONTENT_JEI_SLOT_MENU_PASS type={} selected={}",type,selected);
                }else LOG.info("CONTENT_JEI_CATEGORY_FIELDS type={} jsonFields={}",type,json.keySet());
                LOG.info("CONTENT_JEI_CATEGORY_PASS type={} category={} editableSlots={} viewerSlots={} size={}x{}",type,preview.category().getString(),slots.size(),preview.slots().size(),preview.width(),preview.height());
            }
            visuals++;
        } else {
            if(!(page instanceof NativeRecipeFieldsPage))throw new AssertionError("Unexpected fallback page");
            if(type.startsWith("minecraft:"))throw new AssertionError("Vanilla recipe lost its visual editor");
            fieldsOnly++;LOG.info("CONTENT_JEI_FIELDS_ONLY type={} jei={}",type,RecipeJeiBridge.jeiAvailable());
        }
    }
    private static void capture() throws Exception {
        var json=NativeRecipeView.at(new NativeRecipeDocument(samples.get(index).getAsJsonObject("recipe")),
                samples.get(index).getAsJsonArray("view_path").asList().stream().map(JsonElement::getAsString).toList()).recipe();
        String type=NativeRecipeDocument.serializerId(json);
        Path path=Path.of(System.getProperty("contentstudio.jeiEditorValidation.output"),phase+"-"+type.replace(':','-')+".png");
        Files.createDirectories(path.getParent());var mc=Minecraft.getInstance();
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}captures++;
    }
    private static boolean click(double x,double y) {
        //? if >=26.1 {
        /*return Minecraft.getInstance().screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(x,y,
                new net.minecraft.client.input.MouseButtonInfo(1,0)),false);*/
        //?} else {
        return Minecraft.getInstance().screen.mouseClicked(x,y,1);
        //?}
    }
    private static Object field(Object value,String name)throws Exception {
        for(Class<?> type=value.getClass();type!=null;type=type.getSuperclass())try {
            var field=type.getDeclaredField(name);field.setAccessible(true);return field.get(value);
        }catch(NoSuchFieldException ignored) { }
        throw new NoSuchFieldException(name);
    }
}
