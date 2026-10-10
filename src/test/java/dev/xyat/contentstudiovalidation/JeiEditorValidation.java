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
    private static boolean started,finished;
    private static long deadline,due;
    private static int phase,index,captures,visuals,fieldsOnly,hidden;
    private static CompletableFuture<?> reload;
    public static void install() {
        deadline=System.currentTimeMillis()+180_000;
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
            if(index<samples.size()) {captureAndCheck();index++;open();return;}
            if(phase++==0) {index=0;mc.getLanguageManager().setSelected("zh_cn");mc.options.languageCode="zh_cn";reload=mc.reloadResourcePacks();return;}
            finished=true;LOG.info("CONTENT_JEI_EDITOR_PASS visuals={} fieldsOnly={} hidden={} captures={} jei={}",visuals,fieldsOnly,hidden,captures,RecipeJeiBridge.jeiAvailable());
            if(Boolean.getBoolean("contentstudio.jeiEditorValidation.thenMatrix"))NativeRecipeMatrixValidation.install();else mc.stop();
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
    private static void captureAndCheck() throws Exception {
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
                    int x=(int)field(editor,"diagramX")+bounds.get(0).x()+bounds.get(0).width()/2;
                    int y=(int)field(editor,"diagramY")+bounds.get(0).y()+bounds.get(0).height()/2;
                    var click=new MouseInput(x,y,MouseButton.RIGHT,1,0);
                    var method=editor.getClass().getDeclaredMethod("onMouseClick",MouseInput.class);method.setAccessible(true);
                    if(x>=26&&x<400&&y>=100&&y<266&&!((boolean)method.invoke(editor,click)))throw new AssertionError("JEI slot cannot be edited: "+type);
                }else LOG.info("CONTENT_JEI_CATEGORY_FIELDS type={} jsonFields={}",type,json.keySet());
                LOG.info("CONTENT_JEI_CATEGORY_PASS type={} category={} editableSlots={} viewerSlots={} size={}x{}",type,preview.category().getString(),slots.size(),preview.slots().size(),preview.width(),preview.height());
            }
            visuals++;
        } else {
            if(!(page instanceof NativeRecipeFieldsPage))throw new AssertionError("Unexpected fallback page");
            if(type.startsWith("minecraft:"))throw new AssertionError("Vanilla recipe lost its visual editor");
            fieldsOnly++;LOG.info("CONTENT_JEI_FIELDS_ONLY type={} jei={}",type,RecipeJeiBridge.jeiAvailable());
        }
        Path path=Path.of(System.getProperty("contentstudio.jeiEditorValidation.output"),phase+"-"+type.replace(':','-')+".png");
        Files.createDirectories(path.getParent());var mc=Minecraft.getInstance();
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}captures++;
    }
    private static Object field(Object value,String name)throws Exception {var field=value.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(value);}
}
