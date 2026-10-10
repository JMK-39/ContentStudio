package dev.xyat.contentstudiovalidation;

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

/*** Uses the existing client and unsaved page drafts. Never clicks or saves editor changes. */
public final class GuiLongTextValidation {
    private static final Logger LOG=LoggerFactory.getLogger(GuiLongTextValidation.class);
    private static final String ROOT=System.getProperty("contentstudio.guiValidation.output","D:/IDEAWork/ContentStudio/.gradle/gui-long-text-20261004/");
    private static String[] NAMES={"villager-offer","villager-level","villager-removed","villager-follow-empty","villager-follow",
        "loot-entity","loot-block","loot-entry-entity","loot-entry-block","loot-pool","loot-chest","loot-chest-remove","loot-chest-exclude",
        "recipe-removal","recipe-impact","recipe-preview","recipe-types","recipe-tags","components","components-invalid","tooltip-hub","tooltip-editor","tooltip-wide-left","tooltip-wide-right","tooltip-wrapped-edge","tooltip-tall",
        "recipe-hub","recipe-crafting","recipe-furnace","recipe-blast","recipe-smoker","recipe-smithing","recipe-stonecutter","shared-item-selector","recipe-browser","recipe-jei",
        "native-recipe-browser","native-recipe-fields","native-recipe-nested","native-recipe-item","native-recipe-add-field",
        "native-workstations","vanilla-workstations","native-visual","native-visual-slot"};
    private static final BitSet capturedPages = new BitSet();
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
            pages.add(String.valueOf(45+i));
        }
        NAMES=names.toArray(String[]::new);System.setProperty("contentstudio.guiValidation.pages",String.join(",",pages));System.setProperty("contentstudio.guiValidation.phases","4");install();
    }

    public static void install() { if(installed)return;installed=true;KineticClientEvents.onTick(KineticClientEvents.TickPhase.END,GuiLongTextValidation::tick); }
    private static void tick() {
        if(finished)return;
        try {
            var mc=Minecraft.getInstance();
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
                } else nextPhase();
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
                if(Boolean.getBoolean("contentstudio.nativeRecipeMatrix.captureOnly") && (page==26 || page==41)) {
                    var current=KineticGui.currentPage();var menu=field(current,page==26?"stations":"menu");
                    if(((List<?>)field(menu,"cards")).isEmpty()) {
                        if(now>due+30_000)throw new AssertionError("Workstation navigation never loaded: ready="+field(menu,"ready")+", rows="+((List<?>)field(menu,"rows")).size()+", jei="+RecipeJeiBridge.jeiAvailable());
                        return;
                    }
                    if(page==41 && phase==0 && !nativeNavigationChecked) {
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
            failures++;LOG.error("CONTENT_GUI_FAIL phase="+phase+" page="+page,error);
            finish();
        }
    }
    private static boolean tagCapture(){return Boolean.getBoolean("contentstudio.guiValidation.tags")&&(page==27||page>=45&&nativeSamples.get(page-45).has("tagFixture"));}
    private static void nextPhase() {
        if(stressOriginal!=null){Language.inject(stressOriginal);stressOriginal=null;}
        phase++;page=-1;
        boolean fullHdOnly=Boolean.getBoolean("contentstudio.guiValidation.fullHdOnly");
        if(phase>=(fullHdOnly?2:Integer.getInteger("contentstudio.guiValidation.phases",5))){finish();return;}
        var mc=Minecraft.getInstance();
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
        page++;
        String selectedPages=System.getProperty("contentstudio.guiValidation.pages", "");
        while(page<NAMES.length && !selectedPages.isBlank() && !List.of(selectedPages.split(",")).contains(String.valueOf(page)))page++;
        if(page>=NAMES.length){nextPhase();return;}
        openPage(page);
        screenshot=false;slotMenuOpened=false;due=System.currentTimeMillis()+1000;
        LOG.info("CONTENT_GUI_OPEN phase={} case={} page={}",phase,NAMES[page],KineticGui.currentPage()!=null?KineticGui.currentPage().getClass().getName():String.valueOf(Minecraft.getInstance().screen));
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
        if(index>=45) {
            var body=nativeSamples.get(index-45);
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
                String editLabel=Component.translatable("gui.contentstudio.recipe.native.edit_slot").getString();
                String removeLabel=Component.translatable("gui.contentstudio.recipe.native.remove_slot").getString();
                var edit=actions.stream().filter(action->action.label().getString().equals(editLabel)).findFirst().orElseThrow(()->new AssertionError("Missing native edit action"));
                if(actions.stream().noneMatch(action->action.label().getString().equals(removeLabel)))throw new AssertionError("Missing native remove action");
                invoke(preview,"closeContextMenu");edit.action().run();
                if(!(KineticGui.currentPage() instanceof NativeRecipeFieldsPage))throw new AssertionError("Edit action did not open slot fields");
                if(((List<?>)field(KineticGui.currentPage(),"keys")).isEmpty())throw new AssertionError("Primitive ingredient edit opened an empty page");
                KineticGui.open(preview);actions=(List<dev.xyat.kineticcore.api.client.gui.overlay.KineticOverlays.MenuItem>)invoke(preview,"slotMenu");
                actions.stream().filter(action->action.label().getString().equals(removeLabel)).findFirst().orElseThrow(()->new AssertionError("Missing native remove action")).action().run();
                var document=(dev.xyat.contentstudio.recipe.nativeedit.NativeRecipeDocument)field(field(preview,"session"),"document");
                if(document.json().getAsJsonArray("ingredients").size()!=0 || !document.json().get("result").equals(body.getAsJsonObject("recipe").get("result")))
                    throw new AssertionError("Native remove action damaged unrelated data");
                LOG.info("CONTENT_GUI_NATIVE_SLOT_MENU_PASS leftSelect=true rightMenu=true edit=true remove=true");
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
                var selected=p.getClass().getDeclaredField("selected");selected.setAccessible(true);selected.setInt(p,2);invoke(p,"rebuild");
            }
            case 36 -> {
                var p=new NativeRecipeBrowserPage();
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
        var mc=Minecraft.getInstance();Path path=Path.of(ROOT,String.format("%d-%02d-%s-%s.png",phase,page,NAMES[page],frame));Files.createDirectories(path.getParent());
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(path);}
        capturedPages.set(page);
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
        LOG.info("CONTENT_GUI_{} pages={} captures={} failures={} userSettingsRestored=true",failures==0?"PASS":"FAIL",capturedPages.cardinality(),captures,failures);
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
