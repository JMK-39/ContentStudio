//? if >=1.21 {
/*package dev.xyat.contentstudiovalidation;

import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.client.gui.KineticGui;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.contentstudio.loot.LootEntryInfo;
import dev.xyat.contentstudio.loot.client.gui.LootEditorPage;
import dev.xyat.contentstudio.loot.client.gui.ChestLootEditorPage;
import dev.xyat.contentstudio.recipe.client.gui.*;
import dev.xyat.contentstudio.recipe.removal.*;
import dev.xyat.contentstudio.recipe.client.RecipeJeiBridge;
import dev.xyat.contentstudio.tooltip.TooltipClientHandlers;
import dev.xyat.contentstudio.tooltip.TooltipManager;
import dev.xyat.contentstudio.villager.client.gui.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.trading.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/^** Uses the existing client and unsaved page drafts. Never clicks or saves editor changes. *^/
public final class GuiLongTextValidation {
    private static final Logger LOG=LoggerFactory.getLogger(GuiLongTextValidation.class);
    private static final String ROOT="D:/IDEAWork/ContentStudio/.gradle/gui-long-text-20261004/";
    private static final String[] NAMES={"villager-offer","villager-level","villager-removed","villager-follow-empty","villager-follow",
        "loot-entity","loot-block","loot-entry-entity","loot-entry-block","loot-pool","loot-chest","loot-chest-remove","loot-chest-exclude",
        "recipe-removal","recipe-impact","recipe-preview","recipe-types","recipe-tags","components","components-invalid","tooltip-hub","tooltip-editor","tooltip-wide-left","tooltip-wide-right","tooltip-wrapped-edge","tooltip-tall"};
    private static boolean installed,started,screenshot,finished,originalFullscreen;
    private static String originalLanguage;
    private static int originalScale,originalWidth,originalHeight,phase=-1,page=-1,captures,failures;
    private static long due;
    private static CompletableFuture<Void> reload;
    private static Language stressOriginal;
    private static Map<String,List<TooltipManager.TooltipRule>> originalTooltipData;

    public static void install() { if(installed)return;installed=true;KineticClientEvents.onTick(KineticClientEvents.TickPhase.END,GuiLongTextValidation::tick); }
    private static void tick() {
        if(finished)return;
        try {
            var mc=Minecraft.getInstance();
            if(!started) {
                if(mc.player==null || mc.level==null || mc.getSingleplayerServer()==null)return;
                started=true;originalLanguage=mc.getLanguageManager().getSelected();originalScale=mc.options.guiScale().get();
                originalWidth=mc.getWindow().getWidth();originalHeight=mc.getWindow().getHeight();originalFullscreen=mc.getWindow().isFullscreen();
                originalTooltipData=TooltipClientHandlers.clientData;
                mc.options.guiScale().set(0);
                if(originalFullscreen)mc.getWindow().toggleFullScreen();
                nextPhase();
                return;
            }
            if(reload!=null) {
                if(!reload.isDone() || mc.getOverlay()!=null)return;
                reload.join();reload=null;
                if(phase==4) {
                    stressOriginal=Language.getInstance();
                    Language.inject(new StressLanguage(stressOriginal));
                }
                nextPage();return;
            }
            long now=System.currentTimeMillis();
            if(!screenshot && now>=due) { capture("start");screenshot=true;due=now+(phase==4?3400:550);return; }
            if(screenshot && now>=due) {
                if(phase==4)capture("scroll");
                nextPage();
            }
        } catch(Throwable error) {
            failures++;LOG.error("CONTENT_GUI_FAIL phase="+phase+" page="+page,error);
            finish();
        }
    }
    private static void nextPhase() {
        if(stressOriginal!=null){Language.inject(stressOriginal);stressOriginal=null;}
        phase++;page=-1;
        if(phase>=5){finish();return;}
        var mc=Minecraft.getInstance();
        mc.setScreen(null);
        String lang=phase==2 || phase==3?"zh_cn":"en_us";
        mc.getLanguageManager().setSelected(lang);
        mc.options.languageCode=lang;
        int width=phase==1 || phase==3?1536:854,height=phase==1 || phase==3?864:480;
        mc.getWindow().setWindowed(width,height);mc.resizeDisplay();
        reload=mc.reloadResourcePacks();
        LOG.info("CONTENT_GUI_PHASE phase={} language={} requested={}x{} autoScale=true",phase,lang,width,height);
    }
    private static void nextPage() throws Exception {
        page++;
        String selectedPages=System.getProperty("contentstudio.guiValidation.pages", "");
        while(page<NAMES.length && !selectedPages.isBlank() && !List.of(selectedPages.split(",")).contains(String.valueOf(page)))page++;
        if(page>=NAMES.length){nextPhase();return;}
        openPage(page);
        screenshot=false;due=System.currentTimeMillis()+1000;
        LOG.info("CONTENT_GUI_OPEN phase={} case={} page={}",phase,NAMES[page],KineticGui.currentPage().getClass().getName());
    }
    private static MerchantOffer offer(){return new MerchantOffer(new ItemCost(Items.EMERALD,2),new ItemStack(Items.BREAD,3),16,2,0.05F);}
    private static VillagerTradeEditorPage trade(boolean level)throws Exception {
        var p=new VillagerTradeEditorPage();KineticGui.open(p);
        invoke(p,"trySelectOwnerFromText","minecraft:farmer");
        if(level)invoke(p,"selectLevelSettings",1);
        else { invoke(p,"clearSelectionToNewOffer");invoke(p,"loadOffer",offer(),1); }
        return p;
    }
    private static KineticPage loot(int mode,boolean chest)throws Exception {
        String target=mode==0?"minecraft:zombie":mode==1?"minecraft:diamond_ore":"minecraft:chests/village/village_weaponsmith";
        String table=mode==0?"minecraft:entities/zombie":mode==1?"minecraft:blocks/diamond_ore":target;
        var entry=new LootEntryInfo(mode,target,table,false);
        KineticPage p=chest?new ChestLootEditorPage(List.of(entry)):new LootEditorPage(mode,List.of(entry));
        KineticGui.open(p);
        invoke(p,"applyDetail",mode,target,table,
            "{\"type\":\"minecraft:generic\",\"pools\":[{\"rolls\":1,\"bonus_rolls\":0,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:diamond_sword\",\"weight\":1,\"functions\":[{\"function\":\"minecraft:set_count\",\"count\":{\"min\":1,\"max\":3}}]}]}]}",false);
        return p;
    }
    private static RecipeRemovalPage removal() {
        var p=new RecipeRemovalPage(List.of(new RemovalEntry(RemovalMode.MOD,"example_very_long_mod_namespace_for_scroll","Read-only fixture")),List.of(new ItemStack(Items.DIAMOND_SWORD)));
        KineticGui.open(p);return p;
    }
    @SuppressWarnings("unchecked")
    private static void openPage(int index)throws Exception {
        switch(index) {
            case 0 -> trade(false);
            case 1 -> trade(true);
            case 2 -> {
                var parent=trade(false);
                var p=(KineticPage)construct("dev.xyat.contentstudio.villager.client.gui.VillagerTradeEditorPage$RemovedDefaultTradesPage",parent,"minecraft:farmer");
                KineticGui.open(p);
                var entries=(List<Object>)field(p,"removedEntries");
                entries.add(construct("dev.xyat.contentstudio.villager.client.gui.VillagerTradeEditorPage$RemovedDefaultTradesPage$RemovedDefaultEntry",1,0,offer()));
                invoke(field(p,"listScroll"),"update",entries.size(),invoke(p,"getVisibleRows"));
            }
            case 3 -> KineticGui.open(VillagerFollowItemEditorPage.create(List.of()));
            case 4 -> KineticGui.open(VillagerFollowItemEditorPage.create(List.of("minecraft:emerald","minecraft:bread")));
            case 5 -> loot(0,false);
            case 6 -> loot(1,false);
            case 7,8 -> {
                var parent=loot(index==7?0:1,false);
                var visuals=(List<Object>)field(parent,"dropVisuals");
                if(visuals.isEmpty())throw new AssertionError("Sample loot parsed no drops");
                KineticGui.open((KineticPage)construct("dev.xyat.contentstudio.loot.client.gui.LootEntryEditPage",parent,visuals.get(0)));
            }
            case 9 -> {
                var parent=loot(0,false);
                var root=(com.google.gson.JsonObject)field(parent,"currentRoot");
                KineticGui.open((KineticPage)construct("dev.xyat.contentstudio.loot.client.gui.LootPoolEditPage",parent,0,root.getAsJsonArray("pools").get(0).getAsJsonObject().deepCopy()));
            }
            case 10 -> loot(2,true);
            case 11,12 -> {
                var p=(ChestLootEditorPage)loot(2,true);
                String id=index==11?LootEntryInfo.GLOBAL_CHEST_REMOVE_ID:LootEntryInfo.GLOBAL_CHEST_EXCLUDE_ID;
                invoke(p,"selectEntry",new LootEntryInfo(2,id,id,false));
                if(index==11)p.applyGlobalRemoveDetail(List.of(dev.xyat.contentstudio.loot.GlobalRemoveRule.item("minecraft:diamond_sword")));
                else p.applyGlobalExcludeDetail(List.of("minecraft:chests/village/village_weaponsmith"));
            }
            case 13 -> removal();
            case 14 -> {
                var parent=removal();
                KineticGui.open(new RecipeRemovalImpactPage(parent,new RemovalEntry(RemovalMode.MOD,"minecraft","",List.of(ResourceLocation.parse("minecraft:stale_scroll_fixture"))),null));
            }
            case 15 -> {
                var parent=removal();
                var summary=new RecipeSummary(ResourceLocation.parse("example:a_deliberately_long_recipe_identifier_to_exercise_scrolling"),ResourceLocation.parse("minecraft:crafting"),
                    new ItemStack(Items.DIAMOND_SWORD),List.of(Ingredient.of(Items.DIAMOND),Ingredient.of(Items.STICK)),1);
                var entry=new RecipeJeiBridge.Entry(summary,Component.literal("A deliberately long recipe category title for bounded preview scrolling"),true,()->{},null);
                KineticGui.open((KineticPage)construct("dev.xyat.contentstudio.recipe.client.gui.RecipeRemovalPreviewPage",parent,entry));
            }
            case 16 -> KineticGui.open((KineticPage)construct("dev.xyat.contentstudio.recipe.client.gui.RecipeTypeFilterPage",removal(),Map.of(ResourceLocation.parse("minecraft:crafting"),123L,ResourceLocation.parse("example:a_deliberately_long_type_identifier_for_scrolling"),4L),null));
            case 17 -> KineticGui.open(new RecipeTagSelectionPage(value->{}));
            case 18,19 -> KineticGui.open(new dev.xyat.contentstudio.item.ComponentsEditorPage("minecraft:diamond_sword",index==18?"[damage=1]":"[invalid=]",value->{}));
            case 20,21 -> {
                var rule=new TooltipManager.TooltipRule();rule.text="Read-only GUI validation text";rule.mode=0;rule.line=2;
                TooltipClientHandlers.clientData=new HashMap<>(Map.of("minecraft:diamond_sword",new ArrayList<>(List.of(rule))));
                var hub=new TooltipClientHandlers.TooltipHubPage();
                KineticGui.open(index==20?hub:new TooltipClientHandlers.TooltipEditPage(hub,"minecraft:diamond_sword"));
            }
            case 22,23,24,25 -> KineticGui.open(new TooltipProbePage(index));
        }
    }
    private static Object field(Object target,String name)throws Exception {
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())try {
            var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(target);
        }catch(NoSuchFieldException ignored){}
        throw new NoSuchFieldException(name);
    }
    private static Object invoke(Object target,String name,Object...args)throws Exception {
        for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())for(var m:type.getDeclaredMethods()) {
            if(m.getName().equals(name)&&compatible(m.getParameterTypes(),args)) {
                m.setAccessible(true);return m.invoke(target,args);
            }
        }
        throw new NoSuchMethodException(name);
    }
    private static Object construct(String name,Object...args)throws Exception {
        for(var c:Class.forName(name).getDeclaredConstructors())if(compatible(c.getParameterTypes(),args)){c.setAccessible(true);return c.newInstance(args);}
        throw new NoSuchMethodException(name+" constructor");
    }
    private static boolean compatible(Class<?>[]types,Object[]args) {
        if(types.length!=args.length)return false;
        for(int i=0;i<types.length;i++)if(args[i]!=null && !(types[i].isInstance(args[i]) || types[i]==int.class && args[i] instanceof Integer || types[i]==boolean.class && args[i] instanceof Boolean))return false;
        return true;
    }
    private static void capture(String frame)throws Exception {
        var mc=Minecraft.getInstance();Path path=Path.of(ROOT,String.format("%d-%02d-%s-%s.png",phase,page,NAMES[page],frame));Files.createDirectories(path.getParent());
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}
        captures++;LOG.info("CONTENT_GUI_CAPTURE phase={} case={} image={}x{}",phase,NAMES[page],mc.getWindow().getWidth(),mc.getWindow().getHeight());
    }
    private static void finish() {
        finished=true;
        var mc=Minecraft.getInstance();
        if(stressOriginal!=null){Language.inject(stressOriginal);stressOriginal=null;}
        if(originalTooltipData!=null)TooltipClientHandlers.clientData=originalTooltipData;
        mc.options.guiScale().set(originalScale);
        mc.getLanguageManager().setSelected(originalLanguage);mc.options.languageCode=originalLanguage;
        mc.setScreen(null);
        mc.getWindow().setWindowed(originalWidth,originalHeight);
        if(originalFullscreen && !mc.getWindow().isFullscreen())mc.getWindow().toggleFullScreen();
        LOG.info("CONTENT_GUI_{} pages={} captures={} failures={} userSettingsRestored=true",failures==0?"PASS":"FAIL",NAMES.length,captures,failures);
        dev.xyat.kineticcore.api.runtime.KineticClientRuntime.stopClient();
    }
    private static final class TooltipProbePage extends KineticPage {
        private final int test;
        TooltipProbePage(int test){super(Component.literal("Tooltip screen bounds probe"));this.test=test;}
        @Override protected void build(dev.xyat.kineticcore.api.client.gui.ui.KineticUi ui){}
        @Override protected void renderForeground(dev.xyat.kineticcore.api.client.gui.render.KineticGraphics g,int mx,int my,float pt) {
            g.scrollingText(title(),16,16,width()-32,0xFFFFFF,false);
            var mc=Minecraft.getInstance();int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();
            int anchorX=test==22?4:w-4,anchorY=h-8;
            List<Component> lines=test==25?java.util.stream.IntStream.range(0,50).mapToObj(i->(Component)Component.literal("Tooltip line "+i+" - height budget diagnostic")).toList()
                :List.of(Component.literal("example:".repeat(90)).withStyle(net.minecraft.ChatFormatting.AQUA));
            if(test==24)dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays.requestTooltip(lines,300,anchorX,anchorY);
            else dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays.requestTooltip(lines,anchorX,anchorY);
        }
    }
    private static final class StressLanguage extends Language {
        private final Language delegate;
        StressLanguage(Language delegate){this.delegate=delegate;}
        @Override public String getOrDefault(String key,String fallback) {
            String text=delegate.getOrDefault(key,fallback);
            return key.startsWith("gui.contentstudio.")?text+" - deliberately extended translation to verify text stays inside its own region":text;
        }
        @Override public boolean has(String key){return delegate.has(key);}
        @Override public boolean isDefaultRightToLeft(){return delegate.isDefaultRightToLeft();}
        @Override public net.minecraft.util.FormattedCharSequence getVisualOrder(net.minecraft.network.chat.FormattedText text){return delegate.getVisualOrder(text);}
    }
}
*///?}
