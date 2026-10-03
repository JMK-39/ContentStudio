//? if >=1.21 {
/*package dev.xyat.contentstudiovalidation;
@net.neoforged.fml.common.Mod("contentstudio_validation")
public final class RuntimeValidation {
    private static int failures;
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(RuntimeValidation.class);
    public RuntimeValidation() { net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(this::started); }
    private void started(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        failures=0;
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
        event.getServer().reloadResources(event.getServer().getPackRepository().getSelectedIds()).whenComplete((ignored,reloadError)->event.getServer().execute(()->{
            run("datapack-reload",()->{if(reloadError!=null)throw reloadError;require(!event.getServer().getRecipeManager().getRecipes().isEmpty(),"recipes reload");RuntimeLootServices.check(event.getServer());});
            LOG.info("CONTENTSTUDIO_VALIDATION_{} failures={}",failures==0 ? "PASS":"FAIL",failures);
        }));
    }
    private interface Checked { void run() throws Throwable; }
    private static void run(String name,Checked check) { try {check.run();LOG.info("CONTENTSTUDIO_CHECK_PASS {}",name);} catch(Throwable error){failures++;LOG.error("CONTENTSTUDIO_CHECK_FAIL "+name,error);} }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
*///?}
