package dev.xyat.contentstudiovalidation;

import dev.xyat.kineticcore.api.client.event.KineticClientEvents;
import dev.xyat.kineticcore.api.client.gui.KineticGui;
import dev.xyat.kineticcore.api.client.gui.page.KineticPage;
import dev.xyat.contentstudio.loot.LootEntryInfo;
import dev.xyat.contentstudio.loot.client.gui.LootEditorPage;
import dev.xyat.contentstudio.loot.client.gui.ChestLootEditorPage;
import dev.xyat.kineticcore.api.client.gui.layout.KineticLayout.Rect;
import dev.xyat.kineticcore.api.client.gui.widget.KineticButton;
import dev.xyat.kineticcore.api.client.gui.scroll.KineticScrollController;
import dev.xyat.kineticcore.internal.client.gui.screen.KineticScreenHost;
import dev.xyat.kineticcore.internal.client.gui.LayoutFrameRecorder;
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

/*** Uses the existing client and unsaved page drafts. Loot checks click only fixture-owned drafts, never Save. */
public final class GuiLongTextValidation {
    private static final Logger LOG=LoggerFactory.getLogger(GuiLongTextValidation.class);
    private static final String ROOT=System.getProperty("contentstudio.guiValidation.output","D:/IDEAWork/ContentStudio/.gradle/gui-long-text-20261004/");
    private static String[] NAMES={"villager-offer","villager-level","villager-removed","villager-follow-empty","villager-follow",
        "loot-entity","loot-block","loot-entry-entity","loot-entry-block","loot-pool","loot-chest","loot-chest-remove","loot-chest-exclude",
        "recipe-removal","recipe-impact","recipe-preview","recipe-types","recipe-tags","components","components-invalid","tooltip-hub","tooltip-editor","tooltip-wide-left","tooltip-wide-right","tooltip-wrapped-edge","tooltip-tall",
        "recipe-hub","recipe-crafting","recipe-furnace","recipe-blast","recipe-smoker","recipe-smithing","recipe-stonecutter","shared-item-selector","recipe-browser","recipe-jei",
        "native-recipe-browser","native-recipe-fields","native-recipe-nested","native-recipe-item","native-recipe-add-field",
        "native-workstations","vanilla-workstations","native-visual","native-visual-slot",
        "native-sequence","native-fluid-picker","native-effect-picker",
        "config-villager","config-editors","config-tooltip","config-loot","config-recipe"};
    private static final int NATIVE_SAMPLE_START=53;
    private static final List<String> CONFIG_PAGE_IDS=List.of("contentstudio:villager","contentstudio:editors","contentstudio:tooltip","contentstudio:loots","contentstudio:recipehud");
    private static net.minecraft.client.gui.screens.Screen configFixtureScreen;
    private static final BitSet capturedPages = new BitSet();
    private static final Set<String> capturedCases = new LinkedHashSet<>();
    private static final List<String> checks = new ArrayList<>();
    private static String failureDetail="";
    private static boolean installed,started,screenshot,finished,originalFullscreen,nativeNavigationChecked,slotMenuOpened,preparingSearch,ownedWorldPrepared,ownedRespawnRequested;
    private static String originalLanguage;
    private static int originalScale,originalWidth,originalHeight,phase=-1,page=-1,captures,failures;
    private static long due;
    private static CompletableFuture<Void> reload;
    private static Language stressOriginal;
    private static Map<String,List<TooltipManager.TooltipRule>> originalTooltipData;
    private static List<com.google.gson.JsonObject> nativeSamples=List.of();

    static void prepareOwnedWorld() {
        var mc=Minecraft.getInstance();
        if(mc.level==null && mc.screen!=null && mc.screen.getClass()==net.minecraft.client.gui.screens.ConfirmScreen.class
                && mc.gameDirectory.getName().startsWith(".codex-native-recipes-")) {
            LOG.info("CONTENT_NATIVE_OWNED_COPY_CONFIRM {}",mc.screen.getTitle().getString());
            try {((it.unimi.dsi.fastutil.booleans.BooleanConsumer)field(mc.screen,"callback")).accept(true);}
            catch(Exception error){throw new AssertionError(error);}
            return;
        }
        if(mc.level==null && mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen
                && mc.gameDirectory.getName().startsWith(".codex-native-recipes-")) {
            for(var child:mc.screen.children())if(child instanceof net.minecraft.client.gui.components.Button button
                    && button.getMessage().getString().equals(Component.translatable("selectWorld.backupJoinSkipButton").getString())) {
                //? if >=26.1 {
                /*button.onPress(null);
                *///?} else {
                button.onPress();
                //?}
                LOG.info("CONTENT_NATIVE_OWNED_COPY_UPGRADE_CONFIRMED");break;
            }
        }
    }

    public static void installNativeSamples(List<com.google.gson.JsonObject> samples) {
        try {
            var reader=Class.forName("dev.xyat.contentstudio.recipe.client.gui.NativeRecipeStacks").getDeclaredMethod("read",com.google.gson.JsonElement.class);
            reader.setAccessible(true);
            var nested=(List<?>)reader.invoke(null,com.google.gson.JsonParser.parseString("{\"item\":{\"item\":\"minecraft:diamond\"},\"count\":8}"));
            if(nested.isEmpty() || ((ItemStack)nested.get(0)).getCount()!=8)throw new AssertionError("Nested native ingredient quantity lost");
            var fluid=(List<?>)reader.invoke(null,com.google.gson.JsonParser.parseString("{\"FluidName\":\"minecraft:water\",\"Amount\":1000}"));
            if(fluid.isEmpty() || !((ItemStack)fluid.get(0)).is(Items.WATER_BUCKET))throw new AssertionError("Native cauldron fluid preview lost");
            var spriteReader=Class.forName("dev.xyat.contentstudio.recipe.client.gui.NativeRecipeVisuals").getDeclaredMethod("read",com.google.gson.JsonElement.class);
            spriteReader.setAccessible(true);
            var waterSprite=spriteReader.invoke(null,com.google.gson.JsonParser.parseString("{\"id\":\"minecraft:water\",\"amount\":1000}"));
            if(waterSprite==null)throw new AssertionError("Native fluid sprite missing");
            var tintReader=waterSprite.getClass().getDeclaredMethod("tint");tintReader.setAccessible(true);
            if((int)tintReader.invoke(waterSprite)==0xFFFFFFFF)throw new AssertionError("Native water tint missing");
            if(spriteReader.invoke(null,com.google.gson.JsonParser.parseString("{\"id\":\"minecraft:lava\",\"amount\":1000}"))==null)
                throw new AssertionError("Native untinted fluid sprite missing");
            var chemicalId=dev.xyat.kineticcore.api.resource.KineticResourceIds.parse("mekanism:polonium");
            boolean chemical=java.util.stream.Stream.of("gas","chemical").anyMatch(registry->
                    dev.xyat.kineticcore.api.registry.KineticRegistries.custom(dev.xyat.kineticcore.api.resource.KineticResourceIds.of("mekanism",registry))
                            .filter(view->view.contains(chemicalId)).isPresent());
            if(chemical && spriteReader.invoke(null,com.google.gson.JsonParser.parseString("{\"gas\":\"mekanism:polonium\",\"amount\":10}"))==null)
                throw new AssertionError("Native chemical sprite missing");
            LOG.info("CONTENT_GUI_NATIVE_SPRITES_PASS water=true lava=true chemicalPresent={}",chemical);
            //? if <1.21 {
            var entity=(List<?>)reader.invoke(null,com.google.gson.JsonParser.parseString("{\"type\":\"minecraft:item\",\"nbt\":{\"Item\":{\"id\":\"minecraft:diamond\",\"Count\":4}}}"));
            if(entity.isEmpty() || !((ItemStack)entity.get(0)).is(Items.DIAMOND) || ((ItemStack)entity.get(0)).getCount()!=4)throw new AssertionError("Native item-entity output preview lost");
            //?}
            LOG.info("CONTENT_GUI_NATIVE_STACKS_PASS nestedQuantity=8");
            var browser=new NativeRecipeBrowserPage();KineticGui.open(browser);browser.close();
            var previous=KineticGui.currentPage();var delayed=samples.get(0).deepCopy();delayed.addProperty("action","open");
            invoke(browser,"accept",new dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeNetwork.Response(0,true,delayed.toString()));
            if(KineticGui.currentPage()!=previous)throw new AssertionError("Delayed reply reopened abandoned browser");
            for(String draft:List.of(
                    "{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\"A\",{}],\"key\":{\"A\":\"minecraft:stone\"}}",
                    "{\"type\":\"create:sequenced_assembly\",\"sequence\":{}}")) {
                var invalid=samples.get(0).deepCopy();invalid.add("recipe",com.google.gson.JsonParser.parseString(draft));
                KineticGui.open(new NativeRecipeEditorPage(invalid,()->{}));
            }
            LOG.info("CONTENT_GUI_NATIVE_DRAFT_NAVIGATION_PASS");
        }catch(Exception error){throw new AssertionError(error);}
        var expanded=new ArrayList<>(samples);
        if(Boolean.getBoolean("contentstudio.guiValidation.tags")) {
            var body=samples.get(0).deepCopy();body.addProperty("id","contentstudio:_tag_preview");body.addProperty("tagFixture",true);
            //? if >=1.21 {
            /*body.add("recipe",com.google.gson.JsonParser.parseString("{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[\"#minecraft:planks\"],\"result\":{\"id\":\"minecraft:stick\",\"count\":4}}"));
            *///?} else {
            body.add("recipe",com.google.gson.JsonParser.parseString("{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"tag\":\"minecraft:planks\"}],\"result\":{\"item\":\"minecraft:stick\",\"count\":4}}"));
            //?}
            body.add("testPath",com.google.gson.JsonParser.parseString("[\"result\",\"count\"]"));body.addProperty("testValue","5");
            var draft=body.getAsJsonObject("recipe").deepCopy();draft.getAsJsonObject("result").addProperty("count",5);body.add("testDraft",draft);expanded.add(body);
            var fields=body.deepCopy();fields.addProperty("tagFields",true);fields.remove("tagFixture");
            fields.add("recipe",com.google.gson.JsonParser.parseString("{\"type\":\"example:tag_fields\",\"input\":{\"tag\":\"minecraft:planks\"}}"));expanded.add(fields);
            try {
            var reader=Class.forName("dev.xyat.contentstudio.recipe.client.gui.NativeRecipeStacks");
            var read=reader.getDeclaredMethod("read",com.google.gson.JsonElement.class);read.setAccessible(true);
            var variants=(List<ItemStack>)read.invoke(null,com.google.gson.JsonParser.parseString("{\"tag\":\"minecraft:planks\",\"count\":8}"));
            if(variants.size()<2||variants.stream().anyMatch(s->s.getCount()!=8||s.is(Items.PAPER)))throw new AssertionError("Tag preview lost real alternatives/count");
            var frame=reader.getDeclaredMethod("frame",List.class,long.class);frame.setAccessible(true);
            if(((ItemStack)frame.invoke(null,variants,0L)).getItem()==((ItemStack)frame.invoke(null,variants,1000L)).getItem())throw new AssertionError("Tag preview does not cycle");
            LOG.info("CONTENT_GUI_TAG_CYCLE_PASS alternatives={} quantity=8",variants.size());
            }catch(ReflectiveOperationException error){throw new AssertionError(error);}
        }
        nativeSamples=List.copyOf(expanded);var names=new ArrayList<>(List.of(NAMES));var pages=new ArrayList<String>(List.of("26","41","42"));
        if(Boolean.getBoolean("contentstudio.guiValidation.tags"))pages.add("27");
        for(int i=0;i<expanded.size();i++) {
            var body=expanded.get(i);names.add(body.has("tagFixture")?"native-tag-preview":body.has("tagFields")?"native-tag-fields":"native-mod-"+body.get("recipe").getAsJsonObject().get("type").getAsString().replace(':','-'));
            pages.add(String.valueOf(NATIVE_SAMPLE_START+i));
        }
        NAMES=names.toArray(String[]::new);System.setProperty("contentstudio.guiValidation.pages",String.join(",",pages));System.setProperty("contentstudio.guiValidation.phases","4");install();
    }

    public static void install() { if(installed)return;installed=true;KineticClientEvents.onTick(KineticClientEvents.TickPhase.END,GuiLongTextValidation::tick); }

    // Without the native recipe checks (development worlds), the native recipe pages still need one recipe opened by
    // the server. Only "list" and "open" are sent; nothing is saved.
    private static final java.util.function.Consumer<dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeNetwork.Response> PREVIEW_CALLBACK=GuiLongTextValidation::acceptPreview;
    private static boolean previewListed;
    private static void fetchPreview() {
        preparingSearch=true;
        var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();var playerId=mc.player.getUUID();
        server.execute(()->{
            var player=server.getPlayerList().getPlayer(playerId);
            //? if >=26.1 {
            /*server.getPlayerList().op(new net.minecraft.server.players.NameAndId(player.getGameProfile()));
            *///?} else {
            server.getPlayerList().op(player.getGameProfile());
            //?}
            mc.execute(()->previewRequest("list",""));
        });
    }
    private static void previewRequest(String action,String source) {
        dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeClient.expect(
                dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeNetwork.request(action,source,
                        System.getProperty("contentstudio.guiValidation.previewType",""),
                        System.getProperty("contentstudio.guiValidation.previewQuery","minecraft:stick"),0,0,"",""),PREVIEW_CALLBACK);
    }
    private static void acceptPreview(dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeNetwork.Response packet) {
        try {
            var body=com.google.gson.JsonParser.parseString(packet.body()).getAsJsonObject();
            if(!packet.success())throw new AssertionError("native preview "+(previewListed?"open":"list")+": "+packet.body());
            if(!previewListed) {
                previewListed=true;
                previewRequest("open",body.getAsJsonArray("rows").get(0).getAsJsonObject().get("id").getAsString());
                return;
            }
            NativeRecipeValidation.preview=body;
            LOG.info("CONTENT_GUI_NATIVE_PREVIEW recipe={}",body.has("id")?body.get("id").getAsString():"");
            preparingSearch=false;nextPhase();
        } catch(Throwable error) {
            preparingSearch=false;failures++;LOG.error("CONTENT_GUI_FAIL preparing native preview",error);finish();
        }
    }
    private static void tick() {
        if(finished)return;
        try {
            var mc=Minecraft.getInstance();
            CompactUi.positionMouse();
            if(!started) {
                if(mc.player==null || mc.level==null || mc.getSingleplayerServer()==null)return;
                if(mc.gameDirectory.getName().startsWith(".codex-native-recipes-") && mc.player.isDeadOrDying()) {
                    if(!ownedRespawnRequested) {mc.player.respawn();ownedRespawnRequested=true;}
                    return;
                }
                started=true;originalLanguage=mc.getLanguageManager().getSelected();originalScale=mc.options.guiScale().get();
                originalWidth=mc.getWindow().getWidth();originalHeight=mc.getWindow().getHeight();originalFullscreen=mc.getWindow().isFullscreen();
                originalTooltipData=TooltipClientHandlers.clientData;
                mc.options.guiScale().set(0);
                if(originalFullscreen)mc.getWindow().toggleFullScreen();
                if(mc.gameDirectory.getName().startsWith(".codex-native-recipes-")) {
                    preparingSearch=true;
                    var server=mc.getSingleplayerServer();var testPlayerId=mc.player.getUUID();
                    server.execute(()->{
                        try {
                        var player=server.getPlayerList().getPlayer(testPlayerId);
                        if(player==null)throw new AssertionError("Owned test player missing: "+testPlayerId);
                        boolean respawned=player.isDeadOrDying();
                        if(respawned) {
                            //? if >=1.21 {
                            /*player=server.getPlayerList().respawn(player,false,net.minecraft.world.entity.Entity.RemovalReason.KILLED);
                            *///?} else {
                            player=server.getPlayerList().respawn(player,false);
                            //?}
                            // Match the vanilla command handler: the connection must follow the replacement player.
                            player.connection.player=player;
                        }
                        // The GUI may leave the owned copy running while capturing screenshots.
                        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                        player.setInvulnerable(true);
                        player.getAbilities().invulnerable=true;
                        player.onUpdateAbilities();
                        player.setHealth(player.getMaxHealth());
                        player.getFoodData().setFoodLevel(20);
                        player.clearFire();
                        player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetHealthPacket(player.getHealth(),20,player.getFoodData().getSaturationLevel()));
                        //? if >=26.1 {
                        /*server.getPlayerList().op(new net.minecraft.server.players.NameAndId(player.getGameProfile()));
                        *///?} else {
                        server.getPlayerList().op(player.getGameProfile());
                        //?}
                        LOG.info("CONTENT_GUI_OWNED_PLAYER_SAFE creative=true invulnerable=true health={} respawned={}",player.getHealth(),respawned||ownedRespawnRequested);
                        mc.execute(()->{preparingSearch=false;ownedWorldPrepared=true;});
                        } catch(Throwable error) {
                            mc.execute(()->{preparingSearch=false;failures++;LOG.error("CONTENT_GUI_FAIL preparing owned world",error);finish();});
                        }
                    });
                } else if(lootOnlyRun()) nextPhase();
                else if(NativeRecipeValidation.preview==null) fetchPreview();
                else nextPhase();
                return;
            }
            if(ownedWorldPrepared) {
                // Wait for the server's respawn/health packets before replacing a death screen with a fixture page.
                if(mc.player==null || mc.level==null || mc.player.isDeadOrDying())return;
                ownedWorldPrepared=false;nextPhase();return;
            }
            if(reload!=null) {
                if(!reload.isDone() || mc.getOverlay()!=null)return;
                reload.join();reload=null;
                if(phase==4) {
                    stressOriginal=Language.getInstance();
                    Language.inject(new StressLanguage(stressOriginal));
                }
                if(Boolean.getBoolean("contentstudio.guiValidation.tags")) {
                    preparingSearch=true;
                    dev.xyat.kineticcore.api.client.search.KineticItemSearch.prepare(()->{
                        preparingSearch=false;
                        try{nextPage();}catch(Throwable error){failures++;LOG.error("CONTENT_GUI_FAIL preparing item selection",error);finish();}
                    });
                }else nextPage();
                return;
            }
            if(preparingSearch)return;
            long now=System.currentTimeMillis();
            if(!screenshot && now>=due) {
                if(LootDensity.active() && !LootDensity.advance()){due=now+350;return;}
                if(Boolean.getBoolean("contentstudio.nativeRecipeMatrix.captureOnly") && (page==26 || page==41)) {
                    var current=KineticGui.currentPage();var menu=field(current,page==26?"stations":"menu");
                    if(((List<?>)field(menu,"cards")).isEmpty()) {
                        if(now>due+30_000)throw new AssertionError("Workstation navigation never loaded: ready="+field(menu,"ready")+", rows="+((List<?>)field(menu,"rows")).size()+", jei="+RecipeJeiBridge.jeiAvailable());
                        return;
                    }
                    if(page==41 && !nativeNavigationChecked) {
                        nativeNavigationChecked=true;
                        var cards=(List<?>)field(menu,"cards");
                        String mod=cards.stream().map(card->{try{return (String)field(card,"key");}catch(Exception e){throw new AssertionError(e);}})
                                .filter(key->key.equals("create")).findFirst().orElse((String)field(cards.get(0),"key"));
                        var query=menu.getClass().getDeclaredField("query");query.setAccessible(true);query.set(menu,mod);invoke(menu,"search");
                        cards=(List<?>)field(menu,"cards");if(cards.size()!=1)throw new AssertionError("Mod search failed: "+mod);
                        ((Runnable)field(cards.get(0),"click")).run();
                        var child=KineticGui.currentPage();if(!(child instanceof NativeRecipeStationsPage))throw new AssertionError("Mod entry did not open workstation types");
                        var types=(List<?>)field(field(child,"menu"),"cards");
                        if(types.isEmpty() || mod.equals("create") && types.size()!=2)throw new AssertionError("Workstation hierarchy missing");
                        LOG.info("CONTENT_GUI_NATIVE_HIERARCHY_PASS mod={} cards={}",mod,types.size());openPage(page);due=now+1000;return;
                    }
                }
                capture("start");screenshot=true;due=now+(tagCapture()?1300:phase==4?3400:550);return;
            }
            if(screenshot && now>=due) {
                if(slotMenuOpened){capture("menu");nextPage();return;}
                if(phase==4)capture("scroll");
                if(tagCapture()) {
                    capture("cycle");openSlotContextMenu();slotMenuOpened=true;due=now+450;return;
                }
                nextPage();
            }
        } catch(Throwable error) {
            failures++;failureDetail=errorDetail(error,"phase="+phase+" page="+page+" "+LootDensity.diagnostics());
            LOG.error("CONTENT_GUI_FAIL phase="+phase+" page="+page,error);
            finish();
        }
    }
    private static boolean tagCapture(){return Boolean.getBoolean("contentstudio.guiValidation.tags")&&(page==27||page>=NATIVE_SAMPLE_START&&nativeSamples.get(page-NATIVE_SAMPLE_START).has("tagFixture"));}
    private static boolean lootOnlyRun() {
        String selected=System.getProperty("contentstudio.guiValidation.pages","");
        return !selected.isBlank() && Arrays.stream(selected.split(",")).allMatch(p->Set.of("5","6","10","11","12").contains(p.trim()));
    }
    private static void nextPhase() {
        if(stressOriginal!=null){Language.inject(stressOriginal);stressOriginal=null;}
        phase++;page=-1;
        // One English 1080p round by default; list phases (0-4) in contentstudio.guiValidation.phases to run more.
        String selectedPhases=System.getProperty("contentstudio.guiValidation.phases","1");
        while(phase<5 && !Boolean.getBoolean("contentstudio.guiValidation.fullHdOnly") && !List.of(selectedPhases.split(",")).contains(String.valueOf(phase)))phase++;
        boolean fullHdOnly=Boolean.getBoolean("contentstudio.guiValidation.fullHdOnly");
        if(phase>=(fullHdOnly?2:5)){finish();return;}
        var mc=Minecraft.getInstance();
        configFixtureScreen=null;
        mc.setScreen(null);
        String lang=(fullHdOnly?phase==1:phase==2 || phase==3)?"zh_cn":"en_us";
        mc.getLanguageManager().setSelected(lang);
        mc.options.languageCode=lang;
        int width=fullHdOnly||phase==1 || phase==3?1920:854,height=fullHdOnly||phase==1 || phase==3?1080:480;
        mc.getWindow().setWindowed(width,height);mc.resizeDisplay();
        reload=mc.reloadResourcePacks();
        LOG.info("CONTENT_GUI_PHASE phase={} language={} requested={}x{} autoScale=true",phase,lang,width,height);
    }
    private static void nextPage() throws Exception {
        CompactUi.verifyVanillaSidebar();
        page++;
        String selectedPages=System.getProperty("contentstudio.guiValidation.pages", "");
        while(page<NAMES.length && !selectedPages.isBlank() && !List.of(selectedPages.split(",")).contains(String.valueOf(page)))page++;
        if(page>=NAMES.length){nextPhase();return;}
        LootDensity.reset();
        openPage(page);
        configFixtureScreen=page>=48&&page<=52?Minecraft.getInstance().screen:null;
        assertConfigFixture();
        screenshot=false;slotMenuOpened=false;due=System.currentTimeMillis()+1000;
        var current=KineticGui.currentPage();var screen=Minecraft.getInstance().screen;
        LOG.info("CONTENT_GUI_OPEN phase={} case={} page={} screen={}",phase,NAMES[page],
                current==null?"none":current.getClass().getName(),screen==null?"none":screen.getClass().getName());
    }
    //? if >=1.21 {
    /*private static MerchantOffer offer(){return new MerchantOffer(new ItemCost(Items.EMERALD,2),new ItemStack(Items.BREAD,3),16,2,0.05F);}
    private static void editItemData(boolean valid){dev.xyat.contentstudio.item.ItemData.edit("minecraft:diamond_sword",valid?"[damage=1]":"[invalid=]",value->{});}
    *///?} else {
    private static MerchantOffer offer(){return new MerchantOffer(new ItemStack(Items.EMERALD,2),new ItemStack(Items.BREAD,3),16,2,0.05F);}
    // Forge edits item NBT in the same Core editor that 1.21 uses for components.
    private static void editItemData(boolean valid){dev.xyat.kineticcore.api.client.gui.selector.KineticSelectors.openNbtEditor(valid?"{Damage:1}":"{invalid:",value->{});}
    //?}
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
        boolean dense=page==5 || page==6 || page==10;
        var targets=new ArrayList<LootEntryInfo>();targets.add(entry);
        if(dense)for(String id:mode==0?List.of("minecraft:skeleton","minecraft:creeper","minecraft:spider")
                :mode==1?List.of("minecraft:stone","minecraft:gold_ore","minecraft:emerald_ore")
                :List.of("minecraft:chests/simple_dungeon","minecraft:chests/abandoned_mineshaft","minecraft:chests/desert_pyramid")) {
            targets.add(new LootEntryInfo(mode,id,mode==0?"minecraft:entities/"+id.substring(id.indexOf(':')+1)
                    :mode==1?"minecraft:blocks/"+id.substring(id.indexOf(':')+1):id,false));
        }
        if(dense&&mode==0&&Boolean.getBoolean("contentstudio.guiValidation.compact"))
            for(String name:List.of("enderman","witch","slime","pig","cow","sheep","chicken","wolf","cat","horse","donkey","mule","llama","rabbit","fox","bee","goat","turtle","dolphin","squid"))
                targets.add(new LootEntryInfo(mode,"minecraft:"+name,"minecraft:entities/"+name,false));
        // Keep the real pages and navigation, while preventing delayed server details from replacing local samples.
        KineticPage p=chest?new ChestLootEditorPage(targets){@Override protected void requestSelectedDetail(){}}
                :new LootEditorPage(mode,targets){@Override protected void requestSelectedDetail(){}};
        KineticGui.open(p);
        invoke(p,"selectEntry",entry);
        invoke(p,"applyDetail",mode,target,table,
            dense?LootDensity.sample():"{\"type\":\"minecraft:generic\",\"pools\":[{\"rolls\":1,\"bonus_rolls\":0,\"entries\":[{\"type\":\"minecraft:item\",\"name\":\"minecraft:diamond_sword\",\"weight\":1,\"functions\":[{\"function\":\"minecraft:set_count\",\"count\":{\"min\":1,\"max\":3}}]}]}]}",false);
        if(dense)LootDensity.begin(p);
        return p;
    }
    private static RecipeRemovalPage removal() {
        var p=new RecipeRemovalPage(List.of(new RemovalEntry(RemovalMode.MOD,"example_very_long_mod_namespace_for_scroll","Read-only fixture")),List.of(new ItemStack(Items.DIAMOND_SWORD)));
        KineticGui.open(p);return p;
    }
    @SuppressWarnings("unchecked")
    private static void openPage(int index)throws Exception {
        if(index>=NATIVE_SAMPLE_START) {
            var body=nativeSamples.get(index-NATIVE_SAMPLE_START);
            if(body.has("tagFields")) {
                var fields=new NativeRecipeFieldsPage(body,()->{});var session=field(fields,"session");
                KineticGui.open((KineticPage)construct("dev.xyat.contentstudio.recipe.client.gui.NativeRecipeFieldsPage",session,List.of("input")));return;
            }
            var preview=new NativeRecipeEditorPage(body,()->{});KineticGui.open(preview);
            if(body.has("tagFixture")) {
                var tags=(List<List<String>>)field(preview,"ingredientTags");
                var items=(List<List<ItemStack>>)field(preview,"stacks");
                if(!tags.get(0).equals(List.of("minecraft:planks"))||items.get(0).size()<2)throw new AssertionError("Native ingredient tag identity lost");
                LOG.info("CONTENT_GUI_NATIVE_TAG_PASS schema={} alternatives={}",body.get("recipe"),items.get(0).size());
                invoke(preview,"onMouseClick",diagramClick(preview,false));
                if(KineticGui.currentPage()==preview)throw new AssertionError("Left click did not open ingredient selection");
                KineticGui.open(preview);
                invoke(preview,"onMouseClick",diagramClick(preview,true));
                if(KineticGui.currentPage()!=preview)throw new AssertionError("Right click opened a picker instead of a menu");
                var actions=(List<dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays.MenuItem>)invoke(preview,"slotMenu");
                String removeLabel=Component.translatable("gui.contentstudio.recipe.native.remove_slot").getString();
                if(actions.stream().noneMatch(action->action.label().getString().equals(removeLabel)))throw new AssertionError("Missing native remove action");
                invoke(preview,"closeContextMenu");
                LootDensity.clickLabel("gui.contentstudio.recipe.native.slot_fields");
                if(!(KineticGui.currentPage() instanceof NativeRecipeFieldsPage))throw new AssertionError("Slot Fields button did not open slot fields");
                if(((List<?>)field(KineticGui.currentPage(),"keys")).isEmpty())throw new AssertionError("Primitive ingredient edit opened an empty page");
                KineticGui.open(preview);actions=(List<dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays.MenuItem>)invoke(preview,"slotMenu");
                actions.stream().filter(action->action.label().getString().equals(removeLabel)).findFirst().orElseThrow(()->new AssertionError("Missing native remove action")).action().run();
                var document=(dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeDocument)field(field(preview,"session"),"document");
                if(document.json().getAsJsonArray("ingredients").size()!=0 || !document.json().get("result").equals(body.getAsJsonObject("recipe").get("result")))
                    throw new AssertionError("Native remove action damaged unrelated data");
                LOG.info("CONTENT_GUI_NATIVE_SLOT_MENU_PASS leftSelect=true rightMenu=true slotFields=true remove=true");
                preview=new NativeRecipeEditorPage(body,()->{});KineticGui.open(preview);
            }
            var original=body.getAsJsonObject("recipe");
            String type=original.get("type").getAsString();
            if(type.equals("twilightforest:uncrafting")) {
                int expected=original.has("input_count")?original.get("input_count").getAsInt():original.getAsJsonObject("input").has("count")?original.getAsJsonObject("input").get("count").getAsInt():1;
                var input=(List<List<ItemStack>>)field(preview,"stacks");
                if(input.isEmpty()||input.get(0).isEmpty()||input.get(0).get(0).getCount()!=expected)throw new AssertionError("Uncrafting input quantity lost");
                LOG.info("CONTENT_GUI_UNCRAFTING_COUNT_PASS count={}",expected);
            }
            if(type.startsWith("youkaisfeasts:cuisine_")&&original.has("base")) {
                var input=(List<List<ItemStack>>)field(preview,"stacks");
                if(input.isEmpty()||input.get(0).isEmpty())throw new AssertionError("Cuisine base definition has no native ingredient preview: "+original.get("base"));
                LOG.info("CONTENT_GUI_CUISINE_BASE_PASS id={}",original.get("base"));
            }
            var path=body.getAsJsonArray("testPath").asList().stream().map(com.google.gson.JsonElement::getAsString).toList();
            invoke(preview,"edit",path,body.get("testValue").getAsString());
            var document=(dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeDocument)field(field(preview,"session"),"document");
            if(!document.json().equals(body.get("testDraft")))throw new AssertionError("GUI edit lost native data: "+body.get("id"));
            var slots=(List<dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeLayout.Slot>)field(preview,"slots");
            int selected=-1;
            for(int i=0;i<slots.size();i++)if(path.size()>=slots.get(i).path().size() && path.subList(0,slots.get(i).path().size()).equals(slots.get(i).path())){selected=i;break;}
            var selection=preview.getClass().getDeclaredField("selected");selection.setAccessible(true);selection.setInt(preview,selected);invoke(preview,"rebuild");return;
        }
        switch(index) {
            case 41 -> KineticGui.open(new NativeRecipeStationsPage());
            case 42 -> KineticGui.open(new VanillaRecipeHubPage());
            case 43 -> KineticGui.open(new NativeRecipeEditorPage(NativeRecipeValidation.preview,()->{}));
            case 44 -> {
                var p=new NativeRecipeEditorPage(NativeRecipeValidation.preview,()->{});KineticGui.open(p);
                var selected=p.getClass().getDeclaredField("selected");selected.setAccessible(true);selected.setInt(p,Boolean.getBoolean("contentstudio.guiValidation.compact")?0:2);invoke(p,"rebuild");
            }
            case 36 -> {
                var p=new NativeRecipeBrowserPage();
                if(Boolean.getBoolean("contentstudio.guiValidation.compact")) {
                    CompactUi.interruptedBrowserRoundTrip();
                    CompactUi.set(p,"started",true);
                    var rows=new com.google.gson.JsonArray();
                    for(int i=0;i<400;i++) {
                        var row=new com.google.gson.JsonObject();row.addProperty("id","contentstudio:compact_fixture_"+i);
                        row.addProperty("type","minecraft:crafting_shapeless");row.addProperty("icon","minecraft:diamond");row.addProperty("edited",false);rows.add(row);
                    }
                    CompactUi.set(p,"rows",rows);CompactUi.set(p,"total",400);KineticGui.open(p);break;
                }
                KineticGui.open(p);
                var search=p.getClass().getDeclaredField("query");search.setAccessible(true);
                search.set(p,Boolean.getBoolean("contentstudio.nativeRecipeValidation.thirdParty")?"farmersdelight:":"minecraft:");
                p.refresh();
            }
            case 37,38,39,40 -> {
                var p=new NativeRecipeFieldsPage(NativeRecipeValidation.preview,()->{});
                KineticGui.open(p);
                if(index>=38) {
                    var session=field(p,"session");
                    var json=NativeRecipeValidation.preview.getAsJsonObject("recipe");
                    String key=json.has("result")?"result":json.has("ingredients")?"ingredients":"key";
                    List<String> path=new ArrayList<>(List.of(key));
                    if(index==39 && json.get(key).isJsonArray()) {
                        path.add("0");var result=json.getAsJsonArray(key).get(0).getAsJsonObject();
                        if(result.has("item") && result.get("item").isJsonObject())path.add("item");
                    }
                    if(index==40) KineticGui.open((KineticPage)construct("dev.xyat.contentstudio.recipe.client.gui.NativeRecipeAddFieldPage",field(session,"document"),List.of()));
                    else KineticGui.open((KineticPage)construct("dev.xyat.contentstudio.recipe.client.gui.NativeRecipeFieldsPage",session,path));
                }
            }
            case 45 -> {
                var body=NativeRecipeValidation.preview.deepCopy();
                body.add("recipe",com.google.gson.JsonParser.parseString("{\"type\":\"create:sequenced_assembly\",\"ingredient\":{\"item\":\"minecraft:iron_ingot\"},\"sequence\":[{\"type\":\"create:deploying\",\"ingredients\":[{\"item\":\"minecraft:iron_ingot\"},{\"item\":\"minecraft:redstone\"}],\"results\":[{\"item\":\"minecraft:gold_ingot\"}]}],\"results\":[{\"item\":\"minecraft:gold_ingot\"}]}"));
                body.add("view_path",new com.google.gson.JsonArray());
                var fields=new NativeRecipeFieldsPage(body,()->{});
                var session=field(fields,"session");
                var sequence=(KineticPage)construct("dev.xyat.contentstudio.recipe.client.gui.NativeRecipeSequencePage",session);
                KineticGui.open(sequence);
                var document=(dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeDocument)field(session,"document");
                var before=document.json();var invalid=(Map<List<String>,String>)field(session,"invalid");
                invalid.put(List.of("sequence","0","ingredients","0","count"),"-");
                try {
                    invoke(sequence,"mutate",(Runnable)()->document.remove(List.of("sequence","0")));
                    if(!before.equals(document.json()))throw new AssertionError("Sequence mutation shifted an invalid field path");
                    checks.add("sequence-invalid-draft-retained");
                } finally {invalid.clear();}
            }
            case 46,47 -> {
                var type=Class.forName("dev.xyat.contentstudio.recipe.client.gui.NativeRecipeIdPicker");
                var choices=type.getDeclaredMethod(index==46?"fluids":"effects");choices.setAccessible(true);
                KineticGui.open((KineticPage)construct(type.getName(),Component.literal(index==46?"Fluid selector":"Effect selector"),
                        choices.invoke(null),index==46?"minecraft:water":"minecraft:speed",(java.util.function.Consumer<String>)value->{}));
            }
            case 48,49,50,51,52 -> dev.xyat.kineticcore.api.config.client.KTConfigApi.openPage(
                    CONFIG_PAGE_IDS.get(index-48));
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
            case 18,19 -> editItemData(index==18);
            case 20,21 -> {
                var rule=new TooltipManager.TooltipRule();rule.text="Read-only GUI validation text";rule.mode=0;rule.line=2;
                TooltipClientHandlers.clientData=new HashMap<>(Map.of("minecraft:diamond_sword",new ArrayList<>(List.of(rule))));
                var hub=new TooltipClientHandlers.TooltipHubPage();
                KineticGui.open(index==20?hub:new TooltipClientHandlers.TooltipEditPage(hub,"minecraft:diamond_sword"));
            }
            case 22,23,24,25 -> KineticGui.open(new TooltipProbePage(index));
            // Recipe editors are container pages drawn on the vanilla workstation textures.
            case 26 -> containerPage(new dev.xyat.contentstudio.recipe.client.gui.RecipeHubPage(new dev.xyat.contentstudio.recipe.RecipeMenu(0,Minecraft.getInstance().player.getInventory()),Component.literal("GUI validation")));
            case 27 -> {
                var menu=new dev.xyat.contentstudio.recipe.UniversalRecipeMenu(0,Minecraft.getInstance().player.getInventory(),dev.xyat.contentstudio.recipe.RecipeRegistry.EditorType.CRAFTING,null);
                var p=new RecipePage(menu,Component.literal("Recipe tag preview"));containerPage(p);
                if(Boolean.getBoolean("contentstudio.guiValidation.tags")) {
                    var selection=construct("dev.xyat.kineticcore.api.client.gui.selector.KineticSelectors$ItemSelection",dev.xyat.kineticcore.api.client.gui.selector.KineticSelectors.ItemSelectionType.TAG,ItemStack.EMPTY,"minecraft:planks");
                    invoke(p,"handleItemSelectorResult",selection,0,menu.inputContainer,true);
                    if(menu.inputContainer.getItem(0).is(Items.PAPER)||menu.inputContainer.getItem(0).isEmpty())throw new AssertionError("Tag selector retained a paper icon");
                    var write=dev.xyat.contentstudio.recipe.RecipeConfigStore.class.getDeclaredMethod("writeStack",ItemStack.class,Integer.class);write.setAccessible(true);
                    var encoded=(com.google.gson.JsonObject)write.invoke(null,menu.inputContainer.getItem(0),0);
                    if(!encoded.has("tag")||!encoded.get("tag").getAsString().equals("#minecraft:planks")||encoded.has("item"))throw new AssertionError("Tag preview saved as a concrete item");
                    LOG.info("CONTENT_GUI_TAG_SERIALIZATION_PASS tag=#minecraft:planks concreteItem=false");
                    var saved=new ItemStack(Items.PAPER);
                    //? if >=1.21 {
                    /*dev.xyat.contentstudio.item.ItemData.updateCustomData(saved,tag->tag.putString("kt_tag","#minecraft:planks"));
                    *///?} else {
                    saved.getOrCreateTag().putString("kt_tag","#minecraft:planks");
                    //?}
                    menu.inputContainer.setItem(1,saved);menu.outputContainer.setItem(0,new ItemStack(Items.STICK,4));
                    var actions=(List<dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays.MenuItem>)invoke(p,"slotMenu",0,menu.inputContainer);
                    actions.get(actions.size()-1).action().run();
                    if(!menu.inputContainer.getItem(0).isEmpty()||!menu.inputContainer.getItem(1).is(Items.PAPER))throw new AssertionError("Vanilla remove action damaged a neighbour");
                    invoke(p,"handleItemSelectorResult",selection,0,menu.inputContainer,true);
                    actions=(List<dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays.MenuItem>)invoke(p,"slotMenu",0,menu.inputContainer);
                    actions.get(0).action().run();
                    if(KineticGui.currentPage()==p)throw new AssertionError("Vanilla choose action did not open item selection");
                    var selector=Minecraft.getInstance().screen;
                    var filterType=selector.getClass().getDeclaredField("activeFilterType");filterType.setAccessible(true);filterType.setInt(selector,2);
                    var filterValue=selector.getClass().getDeclaredField("activeFilterValue");filterValue.setAccessible(true);filterValue.set(selector,"minecraft:planks");
                    invoke(selector,"applyFilterAsResult");
                    if(KineticGui.currentPage()!=p || menu.inputContainer.getItem(0).isEmpty() || !menu.inputContainer.getItem(1).is(Items.PAPER)
                            || !menu.outputContainer.getItem(0).is(Items.STICK) || menu.outputContainer.getItem(0).getCount()!=4)
                        throw new AssertionError("Choosing a tag lost the surrounding recipe draft");
                    LOG.info("CONTENT_GUI_VANILLA_SLOT_MENU_PASS choose=true remove=true neighboursRetained=true");
                    LOG.info("CONTENT_GUI_VANILLA_TAG_PASS selectedRealItem=true legacyCarrierRetained=true");
                }
            }
            case 33 -> dev.xyat.kineticcore.api.client.gui.selector.KineticSelectors.openItemSelector(
                    new dev.xyat.kineticcore.api.client.gui.selector.KineticSelectors.ItemSelectorPreset(
                            null, null, "", "", ""), selection -> {});
            case 34 -> {
                var preview = new RecipePreviewPage();
                KineticGui.open(preview);
                @SuppressWarnings("unchecked")
                var records = (List<dev.xyat.contentstudio.recipe.RecipeRecord>)field(preview, "displayRecords");
                records.clear();
                for (int i = 0; i < 90; i++) {
                    var record = new dev.xyat.contentstudio.recipe.RecipeRecord();
                    record.editorType = "CRAFTING";
                    int count = new int[]{2, 16, 64, 128}[(i / 2) % 4];
                    record.output = new ItemStack(i % 2 == 0 ? Items.EMERALD : Items.DIAMOND_SWORD, i % 2 == 0 ? count : 1);
                    record.invalidConfig = i % 7 == 0;
                    records.add(record);
                }
            }
            case 35 -> {
                var entry = RecipeJeiBridge.recipes(new ItemStack(Items.DIAMOND_SWORD)).stream()
                        .filter(candidate -> candidate.preview() != null).findFirst()
                        .orElseThrow(() -> new IllegalStateException("Installed JEI recipe layout is required for this selected case"));
                KineticGui.open((KineticPage)construct("dev.xyat.contentstudio.recipe.client.gui.RecipeRemovalPreviewPage",removal(),entry));
            }
            default -> containerPage(new dev.xyat.contentstudio.recipe.client.gui.RecipePage(new dev.xyat.contentstudio.recipe.UniversalRecipeMenu(0,Minecraft.getInstance().player.getInventory(),dev.xyat.contentstudio.recipe.RecipeRegistry.EditorType.values()[index-27],null),Component.literal("GUI validation")));
        }
    }
    private static dev.xyat.kineticcore.api.client.gui.input.MouseInput diagramClick(Object page,boolean right)throws Exception {
        double x=((Number)field(page,"diagramX")).doubleValue()+3*((Number)field(page,"scale")).doubleValue();
        double y=((Number)field(page,"diagramY")).doubleValue()+3*((Number)field(page,"scale")).doubleValue();
        return new dev.xyat.kineticcore.api.client.gui.input.MouseInput(x,y,
                right?dev.xyat.kineticcore.api.client.gui.input.MouseButton.RIGHT:dev.xyat.kineticcore.api.client.gui.input.MouseButton.LEFT,right?1:0,0);
    }
    @SuppressWarnings("unchecked")
    private static void openSlotContextMenu()throws Exception {
        var page=KineticGui.currentPage();
        if(page instanceof NativeRecipeEditorPage)invoke(page,"onMouseClick",diagramClick(page,true));
        else if(page instanceof RecipePage recipe) {
            var menu=recipe.menu();
            var actions=(List<dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays.MenuItem>)invoke(recipe,"slotMenu",0,menu.inputContainer);
            invoke(recipe,"openContextMenu",((Number)invoke(recipe,"leftPos")).doubleValue()+menu.slots.get(0).x+18.0,
                    ((Number)invoke(recipe,"topPos")).doubleValue()+menu.slots.get(0).y+18.0,actions,184);
        }else throw new AssertionError("Unexpected tag menu page: "+page+" screen="+Minecraft.getInstance().screen);
    }
    private static void containerPage(Object page)throws Exception {
        var mc=Minecraft.getInstance();
        mc.setScreen((net.minecraft.client.gui.screens.Screen)construct("dev.xyat.kineticcore.internal.client.gui.page.PageContainerScreen",page,mc.player.getInventory(),Component.literal("GUI validation")));
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
        for(int i=0;i<types.length;i++)if(args[i]!=null && !(types[i].isInstance(args[i]) || types[i]==int.class && args[i] instanceof Integer || types[i]==double.class && args[i] instanceof Double || types[i]==boolean.class && args[i] instanceof Boolean))return false;
        return true;
    }
    private static void capture(String frame)throws Exception {
        assertConfigFixture();
        CompactUi.assertOwnedSidebarAbsent();
        assertContentFrame();
        if(Boolean.getBoolean("contentstudio.guiValidation.compact"))CompactUi.check();
        var mc=Minecraft.getInstance();Path path=Path.of(ROOT,String.format("%d-%02d-%s-%s.png",phase,page,NAMES[page],frame));Files.createDirectories(path.getParent());
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}
        capturedPages.set(page);
        capturedCases.add(phase+":"+page);
        captures++;LOG.info("CONTENT_GUI_CAPTURE phase={} case={} image={}x{}",phase,NAMES[page],mc.getWindow().getWidth(),mc.getWindow().getHeight());
    }
    private static void assertConfigFixture()throws Exception {
        if(page<48||page>52)return;
        var screen=Minecraft.getInstance().screen;
        if(screen==null||screen!=configFixtureScreen||KineticGui.currentPage()!=null
                ||!screen.getClass().getName().equals("dev.xyat.kineticcore.internal.client.config.KTConfigScreen"))
            throw new AssertionError("Expected registered ContentStudio Core settings screen, got "+screen);
        var config=(dev.xyat.kineticcore.api.config.client.KTConfigPage)field(screen,"configPage");
        String expected=CONFIG_PAGE_IDS.get(page-48);
        if(!expected.equals(config.id()))throw new AssertionError("Expected config "+expected+", got "+config.id());
    }
    private static void assertContentFrame() {
        var current=KineticGui.currentPage();
        boolean config=page>=48&&page<=52;
        if(!LayoutFrameRecorder.ENABLED || !config&&(current==null || !current.getClass().getName().startsWith("dev.xyat.contentstudio.")))return;
        var frames=LayoutFrameRecorder.lastFrame().stream()
                .filter(box->box.kind()==LayoutFrameRecorder.Kind.FRAME && box.width()>100 && box.height()>60).toList();
        if(frames.size()!=1)throw new AssertionError("Owned page must draw one outer frame without content-region boxes: "+frames);
        if(config&&(frames.get(0).x()!=0||frames.get(0).y()!=0||frames.get(0).width()!=640||frames.get(0).height()!=360))
            throw new AssertionError("Core settings must retain the full canvas frame: "+frames);
    }
    private static void finish() {
        finished=true;
        configFixtureScreen=null;
        var mc=Minecraft.getInstance();
        CompactUi.restoreMouse();
        if(stressOriginal!=null){Language.inject(stressOriginal);stressOriginal=null;}
        if(originalTooltipData!=null)TooltipClientHandlers.clientData=originalTooltipData;
        mc.options.guiScale().set(originalScale);
        mc.getLanguageManager().setSelected(originalLanguage);mc.options.languageCode=originalLanguage;
        mc.setScreen(null);
        mc.getWindow().setWindowed(originalWidth,originalHeight);
        if(originalFullscreen && !mc.getWindow().isFullscreen())mc.getWindow().toggleFullScreen();
        String next=System.getProperty("contentstudio.guiValidation.thenValidation","").trim();
        if(next.isBlank() && Boolean.getBoolean("contentstudio.guiValidation.thenTooltip"))next=TooltipNavigationValidation.class.getName()+".install()";
        try {writeResult(next);}catch(Exception error){failures++;LOG.error("CONTENT_GUI_RESULT_FAIL",error);}
        LOG.info("CONTENT_GUI_{} pages={} captures={} failures={} userSettingsRestored=true",failures==0?"PASS":"FAIL",capturedPages.cardinality(),captures,failures);
        if(failures==0 && !next.isBlank())try {
            String type=next.endsWith(".install()")?next.substring(0,next.length()-".install()".length()):next;
            var install=Class.forName(type).getMethod("install");
            if(type.equals(TooltipNavigationValidation.class.getName())) {
                // That fixture needs a caller screen and translations matching the restored language.
                String successor=next;
                mc.reloadResourcePacks().whenComplete((unused,reloadFailure)->mc.execute(()->{
                    try {
                        if(reloadFailure!=null)throw new IllegalStateException("Tooltip chain language reload failed",reloadFailure);
                        dev.xyat.kineticcore.api.config.client.KTConfigApi.openPage(dev.xyat.contentstudio.tooltip.config.TooltipConfigGui.PAGE_ID);
                        install.invoke(null);LOG.info("CONTENT_GUI_CHAIN_STARTED {}",successor);
                    }catch(Exception error) {
                        failures++;failureDetail=errorDetail(error,"chain="+successor);LOG.error("CONTENT_GUI_CHAIN_FAIL {}",successor,error);
                        try{writeResult(successor);}catch(Exception resultError){LOG.error("CONTENT_GUI_RESULT_FAIL",resultError);}
                        dev.xyat.kineticcore.api.runtime.KineticClientRuntime.stopClient();
                    }
                }));
            }else {install.invoke(null);LOG.info("CONTENT_GUI_CHAIN_STARTED {}",next);}
            return;
        }catch(Exception error) {
            failures++;failureDetail=errorDetail(error,"chain="+next);LOG.error("CONTENT_GUI_CHAIN_FAIL {}",next,error);
            try{writeResult(next);}catch(Exception resultError){LOG.error("CONTENT_GUI_RESULT_FAIL",resultError);}
        }
        dev.xyat.kineticcore.api.runtime.KineticClientRuntime.stopClient();
    }
    private static void writeResult(String next)throws java.io.IOException {
        Path output=Path.of(ROOT).toAbsolutePath();Files.createDirectories(output);
        Files.writeString(output.resolve("result.txt"),"CONTENT_GUI_"+(failures==0?"PASS":"FAIL")+"\npages="+capturedPages
                +"\nphasePages="+capturedCases+"\ncaptures="+captures+"\nfailures="+failures+"\nchecks="+checks
                +"\nnext="+(failures==0?next:"")+"\nfailureDetail="+failureDetail+"\n");
    }
    private static String errorDetail(Throwable error,String context) {
        var trace=new java.io.StringWriter();error.printStackTrace(new java.io.PrintWriter(trace));return context+"\n"+trace;
    }

    /** Real grid hit mapping and lossless editor structure, independent of the page's layout constants. */
    private static final class CompactUi {
        private static boolean vanillaChecked;
        private static java.lang.reflect.Field mouseX,mouseY;
        private static double savedMouseX,savedMouseY;
        static void positionMouse()throws Exception {
            if(!Boolean.getBoolean("contentstudio.guiValidation.compact")||page!=44||!(KineticGui.currentPage() instanceof NativeRecipeEditorPage))return;
            var mc=Minecraft.getInstance();var current=KineticGui.currentPage();
            if(mouseX==null) {
                mouseX=mouseCoordinate(mc.mouseHandler,"xpos","f_91507_");mouseY=mouseCoordinate(mc.mouseHandler,"ypos","f_91508_");
                savedMouseX=mouseX.getDouble(mc.mouseHandler);savedMouseY=mouseY.getDouble(mc.mouseHandler);
            }
            var slots=(List<dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeLayout.Slot>)field(current,"slots");
            int selected=(int)field(current,"selected");if(selected<0||selected>=slots.size())return;
            var slot=slots.get(selected);var host=(KineticScreenHost)mc.screen;
            double x=((Number)field(current,"diagramX")).doubleValue()+slot.x()+8;
            double y=((Number)field(current,"diagramY")).doubleValue()+slot.y()+8;
            mouseX.setDouble(mc.mouseHandler,host.toScreenX(x)*mc.getWindow().getWidth()/mc.getWindow().getGuiScaledWidth());
            mouseY.setDouble(mc.mouseHandler,host.toScreenY(y)*mc.getWindow().getHeight()/mc.getWindow().getGuiScaledHeight());
        }
        private static java.lang.reflect.Field mouseCoordinate(Object mouse,String... names)throws Exception {
            for(String name:names)try {var f=mouse.getClass().getDeclaredField(name);f.setAccessible(true);return f;}
            catch(NoSuchFieldException ignored){}
            throw new NoSuchFieldException(Arrays.toString(names));
        }
        static void restoreMouse() {
            if(mouseX==null)return;
            try {var mouse=Minecraft.getInstance().mouseHandler;mouseX.setDouble(mouse,savedMouseX);mouseY.setDouble(mouse,savedMouseY);}
            catch(IllegalAccessException error){throw new IllegalStateException("Cannot restore fixture mouse coordinates",error);}
        }
        static void verifyVanillaSidebar()throws Exception {
            if(vanillaChecked)return;
            vanillaChecked=true;
            Class<?> client;
            try {client=Class.forName("dev.ftb.mods.ftblibrary.FTBLibraryClient");}
            catch(ClassNotFoundException absent){LOG.info("CONTENT_FTB_SIDEBAR_SKIP modAbsent=true");return;}
            var mc=Minecraft.getInstance();var previous=mc.screen;
            var inventory=new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player);
            try {
                mc.setScreen(inventory);
                var visible=client.getDeclaredMethod("areButtonsVisible",net.minecraft.client.gui.screens.Screen.class);visible.setAccessible(true);
                var shown=mc.screen;
                boolean eligible=(boolean)visible.invoke(null,shown);
                boolean present=hasSidebar(shown);
                require(!eligible||present,"an enabled FTB sidebar remains on the vanilla inventory");
                checks.add("ftb-vanilla-inventory-eligible="+eligible+"-present="+present);
                LOG.info("CONTENT_FTB_VANILLA_PASS eligible={} present={}",eligible,present);
            } finally {mc.setScreen(previous);}
        }
        static void assertOwnedSidebarAbsent() {
            var screen=Minecraft.getInstance().screen;
            if(screen instanceof KineticScreenHost)require(!hasSidebar(screen),"Kinetic pages must not inherit FTB inventory sidebar buttons");
        }
        private static boolean hasSidebar(net.minecraft.client.gui.components.events.GuiEventListener listener) {
            for(Class<?> type=listener.getClass();type!=null;type=type.getSuperclass())
                if(type.getName().equals("dev.ftb.mods.ftblibrary.sidebar.SidebarGroupGuiButton"))return true;
            if(listener instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container)
                for(var child:container.children())if(hasSidebar(child))return true;
            return false;
        }
        static void set(Object target,String name,Object value)throws Exception {
            for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())try {
                var f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);return;
            }catch(NoSuchFieldException ignored){}
            throw new NoSuchFieldException(name);
        }
        /** The real single-listener client must resume a preempted batch after an unsaved editor returns. */
        static void interruptedBrowserRoundTrip()throws Exception {
            var browser=new NativeRecipeBrowserPage();set(browser,"query","fixture-search");KineticGui.open(browser);
            long firstRequest=expectedRecipeSequence();
            var first=listBatch(0,0);deliverRecipe(firstRequest,first);
            long interruptedRequest=expectedRecipeSequence();
            var grid=(dev.xyat.kineticcore.api.client.gui.widget.list.KineticItemGrid)field(browser,"grid");
            grid.setScrollOffset(2);
            for(var child:Minecraft.getInstance().screen.children())if(child instanceof dev.xyat.kineticcore.api.client.gui.widget.KineticTextField text)
                text.setTextValue("unsent-draft-query");
            LootDensity.clickPage(17,37);
            long openRequest=expectedRecipeSequence();
            require(openRequest!=interruptedRequest,"clicking a loaded row sends its own open request");
            deliverRecipe(interruptedRequest,listBatch(1,400));
            require(((com.google.gson.JsonArray)field(browser,"rows")).size()==400,"superseded batch reply is ignored while opening a recipe");
            var body=NativeRecipeValidation.preview.deepCopy();body.addProperty("action","open");body.addProperty("creating",false);
            deliverRecipe(openRequest,body);
            require(KineticGui.currentPage() instanceof NativeRecipeEditorPage,"real recipe response opens the editor");
            deliverRecipe(interruptedRequest,listBatch(1,400));
            require(KineticGui.currentPage() instanceof NativeRecipeEditorPage,"late list response cannot replace the editor");
            KineticGui.currentPage().close();
            require(KineticGui.currentPage()==browser,"unsaved Back returns to the same browser");
            long resumedRequest=expectedRecipeSequence();
            require(resumedRequest!=openRequest,"returning before the final batch resumes loading");
            require(((com.google.gson.JsonArray)field(browser,"rows")).size()==400,"resuming retains the first batch");
            require(((dev.xyat.kineticcore.api.client.gui.widget.list.KineticItemGrid)field(browser,"grid")).scrollOffset()==2
                    && field(browser,"query").equals("unsent-draft-query"),"Back retains the scroll row and unsubmitted search text");
            deliverRecipe(openRequest,body);
            require(KineticGui.currentPage()==browser,"late open reply cannot reopen the abandoned child");
            deliverRecipe(resumedRequest,listBatch(1,400));
            require(((com.google.gson.JsonArray)field(browser,"rows")).size()==800
                    && ((dev.xyat.kineticcore.api.client.gui.widget.list.KineticItemGrid)field(browser,"grid")).items().size()==800,
                    "the resumed batch completes the full grid without restarting or duplicating the first batch");
            require(((dev.xyat.kineticcore.api.client.gui.widget.list.KineticItemGrid)field(browser,"grid")).scrollOffset()==2,
                    "appending the final batch retains the restored scroll row");
            LootDensity.clickPage(17,37);deliverRecipe(expectedRecipeSequence(),body);
            var editor=KineticGui.currentPage();
            require(editor instanceof NativeRecipeEditorPage,"the second round trip opens its real editor");
            deliverEditorReply(editor,true,body); // Successful Save reply refreshes the hidden parent; no Save packet is sent.
            deliverRecipe(expectedRecipeSequence(),listBatch(0,0));
            long hiddenBatch=expectedRecipeSequence();
            deliverEditorReply(editor,false,body); // A failed second editor request takes the sole expected callback.
            long failedEditorRequest=expectedRecipeSequence();
            editor.close();
            require(KineticGui.currentPage()==browser && expectedRecipeSequence()!=failedEditorRequest,
                    "Back resumes a hidden refresh preempted by a failed editor request");
            resumedRequest=expectedRecipeSequence();
            deliverRecipe(hiddenBatch,listBatch(1,400));
            require(((com.google.gson.JsonArray)field(browser,"rows")).size()==400,"the preempted hidden batch remains stale after Back");
            deliverRecipe(resumedRequest,listBatch(1,400));
            require(((com.google.gson.JsonArray)field(browser,"rows")).size()==800
                    && ((dev.xyat.kineticcore.api.client.gui.widget.list.KineticItemGrid)field(browser,"grid")).scrollOffset()==2,
                    "the hidden refresh completes with the original scroll row after editor failure");
            LootDensity.clickPage(17,37);deliverRecipe(expectedRecipeSequence(),body);editor=KineticGui.currentPage();
            deliverEditorReply(editor,true,body);
            deliverRecipe(expectedRecipeSequence(),listBatch(0,0));deliverRecipe(expectedRecipeSequence(),listBatch(1,400));
            long hiddenCompleted=expectedRecipeSequence();editor.close();
            require(KineticGui.currentPage()==browser && expectedRecipeSequence()==hiddenCompleted
                    && ((com.google.gson.JsonArray)field(browser,"rows")).size()==800,
                    "a hidden refresh completed before Back neither re-requests nor duplicates its final batch");
            browser.refresh();deliverRecipe(expectedRecipeSequence(),listBatch(0,0));
            long beforeShrink=expectedRecipeSequence();
            var smaller=listBatch(0,0);smaller.addProperty("pages",1);smaller.addProperty("total",400);
            deliverRecipe(beforeShrink,smaller);
            long restarted=expectedRecipeSequence();
            require(restarted!=beforeShrink,"a server-clamped batch after catalog shrink starts a fresh list request");
            deliverRecipe(restarted,smaller);
            require(((com.google.gson.JsonArray)field(browser,"rows")).size()==400
                    && ((dev.xyat.kineticcore.api.client.gui.widget.list.KineticItemGrid)field(browser,"grid")).items().size()==400,
                    "catalog reset replaces the old chain without appending duplicate batches");
            checks.add(phase+":browser-interrupted-batch-open-back-completes");
            LOG.info("CONTENT_BROWSER_BATCH_RESUME_PASS initial=400 completed=800 scroll=2 staleListIgnored=true staleOpenIgnored=true hiddenRefreshFailureRecovered=true hiddenCompleteNotRepeated=true catalogShrinkReset=400");
        }
        private static com.google.gson.JsonObject listBatch(int page,int start) {
            var body=new com.google.gson.JsonObject();body.addProperty("action","list");body.addProperty("page",page);
            body.addProperty("pages",2);body.addProperty("total",800);body.add("mods",new com.google.gson.JsonArray());
            var rows=new com.google.gson.JsonArray();
            for(int i=0;i<400;i++) {
                var row=new com.google.gson.JsonObject();row.addProperty("id",start+i==52?NativeRecipeValidation.preview.get("id").getAsString():"contentstudio:batch_fixture_"+(start+i));
                row.addProperty("type","minecraft:crafting_shapeless");row.addProperty("icon","minecraft:diamond");row.addProperty("edited",false);rows.add(row);
            }
            body.add("rows",rows);return body;
        }
        private static long expectedRecipeSequence()throws Exception {
            var f=dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeClient.class.getDeclaredField("sequence");f.setAccessible(true);return f.getLong(null);
        }
        private static void deliverRecipe(long sequence,com.google.gson.JsonObject body) {
            dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeClient.accept(
                    new dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeNetwork.Response(sequence,true,body.toString()));
        }
        @SuppressWarnings("unchecked")
        private static void deliverEditorReply(KineticPage editor,boolean success,com.google.gson.JsonObject recipe)throws Exception {
            var callback=(java.util.function.Consumer<dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeNetwork.Response>)field(editor,"callback");
            long sequence=Long.MAX_VALUE-(success?1:2);
            dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeClient.expect(sequence,callback);
            var reply=success?recipe.deepCopy():new com.google.gson.JsonObject();
            reply.addProperty("action",success?"save":"disable");
            if(!success)reply.addProperty("message","STALE");
            dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeClient.accept(
                    new dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeNetwork.Response(sequence,success,reply.toString()));
        }
        static void check()throws Exception {
            var current=KineticGui.currentPage();
            if(page==26||page==41) {
                var menu=field(current,page==26?"stations":"menu");
                require(((Number)field(menu,"x")).intValue()==4&&((Number)field(menu,"w")).intValue()==632,"station cards use the full width with four-point outer margins");
                require(((Number)field(menu,"h")).intValue()>=(page==26?248:294),"moving the footer to the top provides additional complete entry rows");
                var method=menu.getClass().getDeclaredMethod("modLabel",String.class);method.setAccessible(true);
                if(phase==3) {
                    require(((Component)method.invoke(null,"create")).getString().equals("机械动力"),"Create entry uses its Chinese label");
                    require(((Component)method.invoke(null,"farmersdelight")).getString().equals("农夫乐事"),"Farmer's Delight entry uses its Chinese label");
                    require(((Component)method.invoke(null,"goety")).getString().equals("诡厄巫法"),"Goety entry uses the requested Chinese label");
                    set(menu,"query","机械动力");invoke(menu,"filter");
                    require(((List<?>)field(menu,"cards")).size()==1,"localized mod search finds its recipe entry");
                    set(menu,"query","");invoke(menu,"filter");
                }
                for(var child:Minecraft.getInstance().screen.children())if(child instanceof dev.xyat.kineticcore.api.client.gui.widget.KineticButton button
                        && !(button instanceof dev.xyat.kineticcore.api.client.gui.widget.KineticItemButton))
                    require(button.controlY()<48,"recipe entry actions stay in the header instead of reserving a footer");
                LOG.info("CONTENT_STATION_LAYOUT_PASS phase={} page={} fullWidth=true headerActions=true localizedSearch=true",phase,page);
            }
            if(page==36) {
                var grid=(dev.xyat.kineticcore.api.client.gui.widget.list.KineticItemGrid)field(current,"grid");
                require(grid.columns()==26 && grid.visibleRows()==13,"browser displays 26 columns and 13 complete compact rows");
                require(grid.itemAt(17,37)==0 && grid.itemAt(617,325)==337,"first and last visible slots retain their recipe indexes");
                require(grid.itemAt(28,37)==-1 && grid.itemAt(634,37)==-1 && grid.itemAt(17,340)==-1,"slot gaps, scrollbar and footer do not open recipes");
                for(var child:Minecraft.getInstance().screen.children())if(child instanceof dev.xyat.kineticcore.api.client.gui.widget.KineticControl control && control!=grid)
                    require(control.controlY()==4 && control.controlX()>=4 && control.controlX()+control.controlWidth()<=636,"all browser filters/actions share the safe top row");
            }
            if((page==43||page==44) && current instanceof NativeRecipeEditorPage) {
                var session=field(current,"session");var document=(dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeDocument)field(session,"document");
                require(document.json().toString().equals(field(session,"original")),"layout and slot selection preserve every source JSON field");
                boolean hasProperties=!((List<?>)field(current,"fields")).isEmpty();
                int diagramWidth=(int)invoke(current,"diagramWidth");
                require(hasProperties?diagramWidth>=400:diagramWidth>=620,"empty property sidebar returns its width to the recipe diagram");
                for(var child:Minecraft.getInstance().screen.children())if(child instanceof dev.xyat.kineticcore.api.client.gui.widget.KineticControl control && control.controlVisible())
                    require(control.controlX()>=4 && control.controlX()+control.controlWidth()<=636
                            && control.controlY()>=4 && control.controlY()+control.controlHeight()<=356,"editor controls retain the four-pixel outer inset");
                if(page==44) {
                    Object tooltip=field(((KineticScreenHost)Minecraft.getInstance().screen).kineticRuntime().overlays(),"tooltip");
                    require(tooltip!=null,"a hovered native recipe slot retains its tooltip after sidebar collapse");
                    var lines=(List<Component>)field(tooltip,"lines");
                    String role=Component.translatable("gui.contentstudio.recipe.native.slot_role.input").getString();
                    require(lines.stream().anyMatch(line->line.getString().contains(role)),"native slot tooltip keeps the input role and existing instructions");
                }
            }
            if(page==36||page==43||page==44)LOG.info("CONTENT_COMPACT_UI_PASS phase={} page={} sourceUnchanged=true",phase,page);
        }
        private static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    }

    private static final class LootDensity {
        private static KineticPage owner;
        private static String baseline;
        private static int step;
        private static Object editedVisual;
        private static int bottomOffset;

        static boolean active(){return page==5 || page==6 || page==10 || page==11 || page==12;}
        static void reset(){owner=null;baseline="";step=0;editedVisual=null;bottomOffset=0;}
        static String diagnostics() {
            var current=KineticGui.currentPage();var screen=Minecraft.getInstance().screen;
            return "lootStep="+step+" currentPage="+(current==null?"null":current.getClass().getName())
                    +" screen="+(screen==null?"null":screen.getClass().getName());
        }

        static String sample() {
            var root=new com.google.gson.JsonObject();root.addProperty("type","minecraft:generic");
            var pools=new com.google.gson.JsonArray();
            String[] ids={"minecraft:diamond_sword","minecraft:emerald","minecraft:gold_ingot","minecraft:iron_ingot"};
            for(int i=0;i<18;i++) {
                var pool=new com.google.gson.JsonObject();pool.addProperty("rolls",1+i%3);pool.addProperty("bonus_rolls",0);
                var entries=new com.google.gson.JsonArray();
                for(int j=0;j<2;j++) {
                    var entry=new com.google.gson.JsonObject();entry.addProperty("type","minecraft:item");entry.addProperty("name",ids[(i+j)%ids.length]);entry.addProperty("weight",j+1);
                    var function=new com.google.gson.JsonObject();function.addProperty("function","minecraft:set_count");
                    var count=new com.google.gson.JsonObject();count.addProperty("min",1);count.addProperty("max",3+j);function.add("count",count);
                    var functions=new com.google.gson.JsonArray();functions.add(function);entry.add("functions",functions);entries.add(entry);
                }
                pool.add("entries",entries);pools.add(pool);
            }
            root.add("pools",pools);return root.toString();
        }

        static void begin(KineticPage page)throws Exception {
            owner=page;baseline=field(owner,"currentRoot").toString();
            expanded().clear();invoke(owner,"refreshGroupedLayout");scroller().setOffset(0);invoke(owner,"updateVisibleGroupedRows");
        }

        static boolean advance()throws Exception {
            var mc=Minecraft.getInstance();require(mc.getWindow().getWidth()==1920 && mc.getWindow().getHeight()==1080,"Loot validation requires 1920x1080");
            if(page==11 || page==12){structure();record("special-one-outer-frame");return true;}
            switch(step++) {
                case 0 -> {
                    require(KineticGui.currentPage()==owner,"independent loot sample is open");structure();geometry();
                    require(rows().size()>=12 && button("groupPoolEditButtons",11).controlVisible(),"twelfth pool row has a live edit button");
                    record("one-outer-frame-two-pixel-viewport-twelve-pool-actions");capture("twelve-pools");
                    click(button("groupPoolEditButtons",11));return false;
                }
                case 1 -> {
                    var child=KineticGui.currentPage();
                    require(child!=null && child.getClass().getSimpleName().equals("LootPoolEditPage") && field(child,"parent")==owner
                            && ((Number)field(child,"poolIndex")).intValue()==11,"twelfth button edits pool twelve");
                    capture("twelfth-pool-editor");clickLabel("gui.contentstudio.loot.loots.pool_editor.cancel");return false;
                }
                case 2 -> {
                    unchanged();geometry();
                    var host=(KineticScreenHost)Minecraft.getInstance().screen;
                    require(!host.kineticRuntime().overlays().blocksInput(),"parent is ready for Delete");
                    pressKey(261);
                    require(host.kineticRuntime().overlays().blocksInput(),"Delete on the selected pool opens its existing confirmation");
                    pressKey(256);
                    require(!host.kineticRuntime().overlays().blocksInput(),"Escape cancels the pool deletion confirmation");
                    unchanged();record("selected-pool-delete-confirm-cancel-keeps-draft");
                    click(button("groupArrowButtons",11));return false;
                }
                case 3 -> {
                    require(expanded().contains(11),"twelfth arrow expands its pool");
                    click(button("groupArrowButtons",11));return false;
                }
                case 4 -> {
                    require(!expanded().contains(11),"twelfth arrow collapses the same pool");
                    record("twelfth-pool-edit-back-expand-collapse");
                    for(int i=0;i<18;i++)expanded().add(i);
                    scroller().setOffset(0);invoke(owner,"refreshGroupedLayout");return false;
                }
                case 5 -> {
                    geometry();capture("expanded-rewards");
                    Rect viewport=viewport();wheel(viewport.x()+100,viewport.y()+30,-3);return false;
                }
                case 6 -> {
                    require(scroller().offset()>0,"real wheel scrolls grouped rows");geometry();capture("wheel-scrolled");
                    Rect bar=(Rect)invoke(owner,"groupScrollbar");drag(bar.x()+2,bar.y()+4,bar.x()+2,bar.bottom()-1);return false;
                }
                case 7 -> {
                    require(scroller().offset()==scroller().maxOffset(),"scrollbar drag reaches the final rows");
                    bottomOffset=scroller().offset();geometry();capture("bottom-rewards");
                    var visible=rows();var all=(List<?>)field(owner,"groupedRows");Object last=all.get(all.size()-1);
                    int slot=visible.indexOf(last);require(slot>=0,"final reward is rendered at maximum offset");
                    Rect bounds=(Rect)invoke(owner,"groupRowBounds",last);
                    require(bounds.y()>=viewport().y() && bounds.bottom()<=viewport().bottom(),"final reward is fully inside the same viewport");
                    editedVisual=field(last,"visual");require(editedVisual!=null,"final row is a reward");
                    clickPage(bounds.x()+90,bounds.y()+12);
                    require(field(owner,"lastClickedGroupVisual")==editedVisual
                            && field(owner,"lastClickedGroupPool").equals(field(last,"poolIndex")),"row hit mapping agrees with its drawn reward");
                    click(button("groupEntryEditButtons",slot));return false;
                }
                case 8 -> {
                    var child=KineticGui.currentPage();
                    require(child!=null && child.getClass().getSimpleName().equals("LootEntryEditPage") && field(child,"parent")==owner
                            && field(child,"original")==editedVisual,"last-row edit button opens the matching reward");
                    capture("last-reward-editor");clickLabel("gui.contentstudio.loot.loots.entry.cancel");return false;
                }
                case 9 -> {
                    unchanged();require(scroller().offset()==bottomOffset,"return keeps grouped scroll position");geometry();
                    record("wheel-drag-last-reward-hit-edit-back");
                    expanded().clear();expanded().addAll(List.of(0,1,2));scroller().setOffset(0);invoke(owner,"refreshGroupedLayout");return false;
                }
                default -> {unchanged();structure();geometry();record("local-draft-unchanged-no-save");return true;}
            }
        }

        private static void structure() {
            require(LayoutFrameRecorder.ENABLED,"Enable kineticcore.layoutCheck for structural frame validation");
            var panels=LayoutFrameRecorder.lastFrame().stream().filter(box->box.kind()==LayoutFrameRecorder.Kind.FRAME && box.width()>100 && box.height()>60).toList();
            require(panels.size()==1 && panels.get(0).x()==0 && panels.get(0).y()==0 && panels.get(0).width()==640 && panels.get(0).height()==360,
                    "only the normal outer frame encloses content: "+panels);
        }

        private static void geometry()throws Exception {
            if(page==5) {
                require((int)invoke(owner,"cellSize")==50 && (int)invoke(owner,"cellPitch")==52 && (int)invoke(owner,"gridCols")==4,
                        "entity loot cards remain 50px with two-pixel gaps in the wider left column");
                var entries=(List<?>)field(owner,"displayEntries");
                var targetScroll=(KineticScrollController)field(owner,"targetScroller");
                int first=(int)Math.floor(targetScroll.smoothOffset()+1.0E-6D)*4;
                // Hit-test rendered centers and the gutter through the actual page implementation.
                if(first<entries.size())require(invoke(owner,"targetEntryAt",29.0,53.0)==entries.get(first),"first standard entity card is clickable");
                require(invoke(owner,"targetEntryAt",55.0,53.0)==null,"two-pixel entity gutter is not clickable");
                for(String name:List.of("saveButton","resetButton","addPoolHeaderButton","overrideModeButton"))
                    require(((dev.xyat.kineticcore.api.client.gui.widget.KineticControl)field(owner,name)).controlY()==4,"right entity toolbar aligns with left controls: "+name);
                var search=(dev.xyat.kineticcore.api.client.gui.widget.KineticControl)field(owner,"searchBox");
                require(search.controlX()==48 && search.controlX()+search.controlWidth()<=210,"entity search follows Back with a four-pixel gap and stays within the actual four-card column");
                Rect entityViewport=viewport();
                require(entityViewport.x()==222 && entityViewport.width()==412 && entityViewport.y()==34,
                        "entity rewards reclaim the left gutter and start eight pixels higher beneath the compact header");
            }
            Rect viewport=viewport();
            // 2026-10-22: block loot uses the entity header height and rows run 4 px further right (scrollbar 3 px inside the frame).
            // Only the block/entity loot editor uses the compact 24 px header; the other loot pages keep 32 px.
            boolean compact=(int)invoke(owner,"groupedHeaderHeight")==24;
            require(viewport.y()==(compact?34:42) && viewport.right()==634 && viewport.bottom()==350
                    && viewport.width()>=(page==5?412:372),"grouped viewport retains its inner padding and reclaimed reward width and height: "+viewport);
            for(String name:List.of("groupArrowButtons","groupAddButtons","groupPoolEditButtons","groupPoolDeleteButtons","groupEntryEditButtons","groupEntryDeleteButtons")) {
                var buttons=(KineticButton[])field(owner,name);require(buttons.length>=12,"at least twelve row action slots");
                for(int slot=0;slot<buttons.length;slot++) {
                    KineticButton button=buttons[slot];if(!button.controlVisible())continue;
                    require(slot<rows().size(),"row button maps to a visible row");
                    Rect row=(Rect)invoke(owner,"groupRowBounds",rows().get(slot));
                    require(button.controlY()>=viewport.y() && button.controlY()+button.controlHeight()<=viewport.bottom(),"visible action is inside clipping viewport");
                    require(button.controlX()>=row.x()+3 && button.controlX()+button.controlWidth()<=row.right()-3
                            && button.controlY()>=row.y()+3 && button.controlY()+button.controlHeight()<=row.bottom()-3,"row action keeps two pixels from its frame lines: "+name+"/"+slot);
                }
            }
            Rect bar=(Rect)invoke(owner,"groupScrollbar");require(bar.y()==viewport.y() && bar.height()==viewport.height(),"scrollbar render and hit share viewport");
        }
        private static void unchanged()throws Exception {
            require(KineticGui.currentPage()==owner,"editor Back returns to the same parent instance");
            require(baseline.equals(field(owner,"currentRoot").toString()) && !(boolean)field(owner,"dirty")
                    && ((Map<?,?>)field(owner,"pendingTableDrafts")).isEmpty(),"editing and cancelling leave only the independent sample unchanged");
        }
        @SuppressWarnings("unchecked") private static Set<Integer> expanded()throws Exception{return (Set<Integer>)field(owner,"expandedPools");}
        private static KineticScrollController scroller()throws Exception{return (KineticScrollController)field(owner,"groupScroller");}
        private static List<?> rows()throws Exception{return (List<?>)field(owner,"visibleGroupRows");}
        private static Rect viewport()throws Exception{return (Rect)invoke(owner,"groupViewport");}
        private static KineticButton button(String name,int slot)throws Exception{return ((KineticButton[])field(owner,name))[slot];}
        private static void record(String name){checks.add(phase+":"+page+":"+name);LOG.info("CONTENT_LOOT_DENSITY_CHECK phase={} page={} {}",phase,page,name);}
        private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
        private static void clickLabel(String key) {
            String label=Component.translatable(key).getString();
            for(var listener:Minecraft.getInstance().screen.children())if(listener instanceof KineticButton button && button.controlVisible() && button.text().getString().equals(label)){click(button);return;}
            throw new AssertionError("Missing real button "+key);
        }
        private static void click(KineticButton button) {
            require(button.controlVisible() && button.isEnabled(),"clicked action is visible and enabled");
            clickPage(button.controlX()+button.controlWidth()/2.0,button.controlY()+button.controlHeight()/2.0);
        }
        private static void clickPage(double x,double y) {
            var screen=Minecraft.getInstance().screen;var host=(KineticScreenHost)screen;
            double sx=host.toScreenX(x),sy=host.toScreenY(y);
            //? if >=26.1 {
            /*var event=new net.minecraft.client.input.MouseButtonEvent(sx,sy,new net.minecraft.client.input.MouseButtonInfo(0,0));
            screen.mouseClicked(event,false);screen.mouseReleased(event);
            *///?} else {
            screen.mouseClicked(sx,sy,0);screen.mouseReleased(sx,sy,0);
            //?}
        }
        private static void wheel(double x,double y,double delta) {
            var screen=Minecraft.getInstance().screen;var host=(KineticScreenHost)screen;
            //? if >=1.21 {
            /*screen.mouseScrolled(host.toScreenX(x),host.toScreenY(y),0,delta);
            *///?} else {
            screen.mouseScrolled(host.toScreenX(x),host.toScreenY(y),delta);
            //?}
        }
        private static void pressKey(int code) {
            //? if >=26.1 {
            /*Minecraft.getInstance().screen.keyPressed(new net.minecraft.client.input.KeyEvent(code,0,0));
            *///?} else {
            Minecraft.getInstance().screen.keyPressed(code,0,0);
            //?}
        }
        private static void drag(double x,double y,double endX,double endY) {
            var screen=Minecraft.getInstance().screen;var host=(KineticScreenHost)screen;
            double sx=host.toScreenX(x),sy=host.toScreenY(y),ex=host.toScreenX(endX),ey=host.toScreenY(endY);
            //? if >=26.1 {
            /*var press=new net.minecraft.client.input.MouseButtonEvent(sx,sy,new net.minecraft.client.input.MouseButtonInfo(0,0));
            var move=new net.minecraft.client.input.MouseButtonEvent(ex,ey,new net.minecraft.client.input.MouseButtonInfo(0,0));
            screen.mouseClicked(press,false);screen.mouseDragged(move,ex-sx,ey-sy);screen.mouseReleased(move);
            *///?} else {
            screen.mouseClicked(sx,sy,0);screen.mouseDragged(ex,ey,0,ex-sx,ey-sy);screen.mouseReleased(ex,ey,0);
            //?}
        }
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
