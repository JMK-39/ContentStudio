//? if >=1.21 {
/*package dev.xyat.contentstudio.villager;
import dev.xyat.contentstudio.villager.config.VillagerConfig;
import dev.xyat.contentstudio.item.ItemData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import static org.junit.jupiter.api.Assertions.*;
class TradeOfferComponentsTest {
@BeforeAll static void setup() throws java.io.IOException { if(net.neoforged.fml.loading.LoadingModList.get()==null) net.neoforged.fml.loading.LoadingModList.of(java.util.List.of(),java.util.List.of(),java.util.List.of(),java.util.List.of(),java.util.Map.of()); net.neoforged.fml.loading.FMLPaths.loadAbsolutePaths(java.nio.file.Files.createTempDirectory("trade-tests-")); net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();}
@Test void offerCodecPreservesCustomDataNumericTypesAndPredicate() {var buy=ItemData.compile("minecraft:diamond","[custom_data={Key:1}]");var data=new VillagerConfig.TradeOfferData("minecraft:farmer",1,"minecraft:diamond",3,ItemData.format(buy),"minecraft:air",0,"[]","minecraft:diamond_sword",1,"[damage=4]",10,2,0.05F,3,1,false,4,false,1,0);var offer=data.createOffer();assertNotNull(offer);assertTrue(offer.getItemCostA().test(buy));assertFalse(offer.getItemCostA().test(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND)));assertEquals(4,offer.getUses());assertEquals(4,offer.getResult().getDamageValue());assertFalse(offer.shouldRewardExp());}
}
*///?}
