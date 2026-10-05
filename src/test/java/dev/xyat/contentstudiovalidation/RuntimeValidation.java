//? if >=1.21 {
/*package dev.xyat.contentstudiovalidation;
@net.neoforged.fml.common.Mod("contentstudio_validation")
public final class RuntimeValidation {
    private static int failures;
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(RuntimeValidation.class);
    public RuntimeValidation() { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(this::started); }
    private void started(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
*///?}
        //? if >=1.21 {
        /*if (Boolean.getBoolean("contentstudio.guiValidation")) { GuiLongTextValidation.install(); return; }
        *///?}
        //? if >=1.21 {
/*        failures=0;
        for(String name:java.util.List.of("dev.xyat.contentstudiovalidation.RuntimeRecipeChecks","dev.xyat.contentstudiovalidation.RuntimeLootRuleChecks","dev.xyat.contentstudiovalidation.RuntimeLootJsonChecks")) {
            try {
                Class<?> type=Class.forName(name); var ctor=type.getDeclaredConstructor(); ctor.setAccessible(true); Object test=ctor.newInstance();
                for(var method:type.getDeclaredMethods()) if(method.isAnnotationPresent(org.junit.jupiter.api.Test.class)) {
                    run(method.getName(),()->{ method.setAccessible(true); method.invoke(test); });
                }
            } catch(Throwable error) { failures++; LOG.error("CONTENTSTUDIO_CHECK_FAIL test-class "+name,error); }
        }
        run("ingredient-network-codec",()->{ var type=Class.forName("dev.xyat.contentstudiovalidation.RuntimeRecipeChecks");var method=type.getDeclaredMethod("networkRoundTrip",net.minecraft.core.RegistryAccess.class); method.setAccessible(true); method.invoke(null,event.getServer().registryAccess()); });
        run("trade-native-components",()->{
            var a=dev.xyat.contentstudio.item.ItemData.compile("minecraft:diamond","[custom_data={BuyKey:1}]");
            var b=dev.xyat.contentstudio.item.ItemData.compile("minecraft:diamond_sword","[damage=4,custom_data={SellKey:2}]");
            var data=new dev.xyat.contentstudio.villager.config.VillagerConfig.TradeOfferData("minecraft:farmer",1,"minecraft:diamond",3,dev.xyat.contentstudio.item.ItemData.format(a),"minecraft:air",0,"[]","minecraft:diamond_sword",1,dev.xyat.contentstudio.item.ItemData.format(b),10,2,0.05F,3,1,false,4,false,1,0);
            var offer=data.createOffer(); require(offer!=null,"offer created"); require(offer.getResult().getDamageValue()==4,"result damage"); require(offer.getUses()==4,"offer uses"); require(!offer.shouldRewardExp(),"reward setting"); require(offer.getItemCostA().test(a.copyWithCount(3)),"buy component predicate"); require(!offer.getItemCostA().test(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND)),"reject missing buy component");
            var ops=event.getServer().registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
            var encoded=net.minecraft.world.item.trading.MerchantOffer.CODEC.encodeStart(ops,offer).getOrThrow();
            var restored=net.minecraft.world.item.trading.MerchantOffer.CODEC.parse(ops,encoded).getOrThrow();
            var meta=(dev.xyat.contentstudio.villager.util.IMerchantOfferAccess)restored;
            require(meta.contentstudio_villager$getCustomTradeId().equals(data.uniqueId()),"custom trade id survives codec");
            require(meta.contentstudio_villager$isRestockDisabled(),"no-restock survives codec"); restored.resetUses(); require(restored.getUses()==4,"no-restock rejects reset");
            var copied=(dev.xyat.contentstudio.villager.util.IMerchantOfferAccess)restored.copy(); require(copied.contentstudio_villager$getCustomTradeId().equals(data.uniqueId())&&copied.contentstudio_villager$isRestockDisabled(),"offer copy metadata");
        });
        run("trade-components-actual-item-validation",()->{
            var good = new dev.xyat.contentstudio.villager.config.VillagerConfig.TradeOfferData("minecraft:farmer",1,"minecraft:diamond",1,"[]","minecraft:air",0,"[]","minecraft:diamond_sword",1,"[max_damage=2000]",10,1,0.05F,0,0,true,0,true,1,0);
            require(dev.xyat.contentstudio.villager.config.VillagerConfig.areValidTradeLists(java.util.List.of(),java.util.List.of(good.toConfigLine()),java.util.List.of()),"sword prototype accepts max damage");
            require(!dev.xyat.contentstudio.villager.config.VillagerConfig.areValidTradeLists(java.util.List.of(),java.util.List.of(good.toConfigLine().replace("diamond_sword","stone")),java.util.List.of()),"stackable stone rejects max damage");
        });
        run("loot-runtime-registry-and-generation",()-> RuntimeLootServices.check(event.getServer()));
        run("original-recipe-catalog-output",()->originalCatalogOutput(event.getServer()));
        run("trade-previews-for-clients",RuntimeValidation::tradePreviewsForClients);
        run("recipes-encode-for-clients",()->recipesEncodeForClients(event.getServer()));
        // A removal rule and a configured recipe reach the live recipe manager through a datapack reload; the original
        // configuration is restored and reloaded afterwards.
        var server=event.getServer();
        var configFile=dev.xyat.contentstudio.recipe.RecipeConfigStore.CONFIG_FILE;
        String savedConfig;
        try { savedConfig=java.nio.file.Files.exists(configFile) ? java.nio.file.Files.readString(configFile) : null; } catch(java.io.IOException error) { savedConfig=null; }
        final String restore=savedConfig;
        var configured=new dev.xyat.contentstudio.recipe.RecipeRecord();
        configured.uuid="0f6c2a1e-7d43-4b8e-9a51-3c2e8f4d6b70";
        configured.editorType="STONECUTTER";
        configured.output=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND);
        configured.inputs.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT));
        configured.inputModes.add(0);
        var planks=net.minecraft.resources.ResourceLocation.parse("minecraft:oak_planks");
        run("recipe-config-save",()->dev.xyat.contentstudio.recipe.RecipeConfigStore.save(java.util.List.of(configured),
                java.util.List.of(new dev.xyat.contentstudio.recipe.removal.RemovalEntry(dev.xyat.contentstudio.recipe.removal.RemovalMode.RECIPE_ID,planks.toString(),""))));
        server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((ignored,reloadError)->server.execute(()->{
            run("datapack-reload",()->{if(reloadError!=null)throw reloadError;require(!server.getRecipeManager().getRecipes().isEmpty(),"recipes reload");originalCatalogOutput(server);RuntimeLootServices.check(server);});
            run("recipe-config-applied",()->{
                var ids=recipeIds(server);
                require(!ids.contains(planks),"removal rule removes the datapack recipe");
                require(ids.contains(dev.xyat.contentstudio.recipe.RecipeConfigStore.memoryRecipeId(configured)),"configured recipe is added");
                require(dev.xyat.contentstudio.recipe.RecipeMemoryManager.originalCatalog(server.getRecipeManager()).entries().get(planks).removed(),"catalog marks the recipe removed");
            });
            run("recipe-config-restore",()->{ if(restore==null) java.nio.file.Files.deleteIfExists(configFile); else java.nio.file.Files.writeString(configFile,restore); });
            server.reloadResources(server.getPackRepository().getSelectedIds()).whenComplete((ignoredAgain,restoreError)->server.execute(()->{
                run("datapack-restore",()->{if(restoreError!=null)throw restoreError;var ids=recipeIds(server);require(ids.contains(planks),"datapack recipe is back");require(!ids.contains(dev.xyat.contentstudio.recipe.RecipeConfigStore.memoryRecipeId(configured)),"configured recipe is gone");});
                LOG.info("CONTENTSTUDIO_VALIDATION_{} failures={}",failures==0 ? "PASS":"FAIL",failures);
            }));
        }));
    }
    // 26.1 clients have no trade sets; the server collects previews and a client thread reads them back.
    @SuppressWarnings("unchecked")
    private static void tradePreviewsForClients() throws Exception {
        var util=dev.xyat.contentstudio.villager.util.VillagerTradeRuntimeUtil.class;
        java.lang.reflect.Method collect;
        try { collect=util.getMethod("collectPreviews"); } catch(NoSuchMethodException tradeTablesOnClient) { return; }
        var previews=(java.util.Map<String,java.util.List<java.util.List<net.minecraft.world.item.trading.MerchantOffer>>>)collect.invoke(null);
        var farmer=previews.get("minecraft:farmer");
        require(farmer!=null&&farmer.size()==5&&farmer.getFirst().stream().anyMatch(java.util.Objects::nonNull),"farmer previews");
        var wandering=previews.get("minecraft:wandering_trader");
        require(wandering!=null&&wandering.size()==2&&!wandering.getFirst().isEmpty(),"wandering trader previews");
        var set=util.getMethod("setRemotePreviews",java.util.Map.class);
        set.invoke(null,previews);
        try {
            int[] count={-1};net.minecraft.world.item.trading.MerchantOffer[] offer={null};
            var client=new Thread(()->{count[0]=dev.xyat.contentstudio.villager.util.VillagerTradeRuntimeUtil.vanillaTradeCount("minecraft:farmer",1);offer[0]=dev.xyat.contentstudio.villager.util.VillagerTradeRuntimeUtil.createPreviewOffer("minecraft:farmer",1,0);});
            client.start();client.join();
            require(count[0]==farmer.getFirst().size(),"client trade count from server previews");
            require(offer[0]!=null&&offer[0]!=farmer.getFirst().getFirst(),"client preview offer is a copy");
        } finally { set.invoke(null,java.util.Map.of()); }
    }
    // Clients receive the recipes through the recipe holder stream codec (26.1 sends every recipe type for the editors).
    private static void recipesEncodeForClients(net.minecraft.server.MinecraftServer server) {
        for(var holder:server.getRecipeManager().getRecipes()) {
            var buffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),server.registryAccess());
            try {
                net.minecraft.world.item.crafting.RecipeHolder.STREAM_CODEC.encode(buffer,holder);
                var decoded=net.minecraft.world.item.crafting.RecipeHolder.STREAM_CODEC.decode(buffer);
                require(decoded.id().equals(holder.id())&&!buffer.isReadable(),"recipe "+holder.id()+" survives the network codec");
            } finally { buffer.release(); }
        }
    }
    // Recipe ids are resource keys from 1.21.2 on.
    private static java.util.Set<net.minecraft.resources.ResourceLocation> recipeIds(net.minecraft.server.MinecraftServer server) {
        java.util.Set<net.minecraft.resources.ResourceLocation> ids=new java.util.HashSet<>();
        for(var holder:server.getRecipeManager().getRecipes()) {
            Object id=holder.id();
            ids.add(id instanceof net.minecraft.resources.ResourceKey<?> key ? key.location() : (net.minecraft.resources.ResourceLocation) id);
        }
        return ids;
    }
    // Datapack recipes are inspected while they load; their output item and its tags must already be known then.
    private static void originalCatalogOutput(net.minecraft.server.MinecraftServer server) {
        var planks=net.minecraft.resources.ResourceLocation.parse("minecraft:oak_planks");
        var entries=dev.xyat.contentstudio.recipe.RecipeMemoryManager.originalCatalog(server.getRecipeManager()).entries();
        require(!entries.containsKey(net.minecraft.resources.ResourceLocation.parse("contentstudio:recipe_bundle")),"the recipe bundle is not a recipe");
        var entry=entries.get(planks);
        require(entry!=null,"oak planks recipe in the original catalog");
        require(planks.equals(entry.candidate().outputItemId()),"original recipe output item");
        require(entry.candidate().outputTagIds().contains(net.minecraft.resources.ResourceLocation.parse("minecraft:planks")),"original recipe output tags");
    }
    private interface Checked { void run() throws Throwable; }
    private static void run(String name,Checked check) { try {check.run();LOG.info("CONTENTSTUDIO_CHECK_PASS {}",name);} catch(Throwable error){failures++;LOG.error("CONTENTSTUDIO_CHECK_FAIL "+name,error);} }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
*///?}
