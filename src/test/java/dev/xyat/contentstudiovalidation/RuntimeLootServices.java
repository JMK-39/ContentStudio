//? if >=1.21 {
/*package dev.xyat.contentstudiovalidation;
import net.minecraft.world.level.storage.loot.*;
import net.minecraft.world.level.storage.loot.entries.*;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.parameters.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.*;
import net.minecraft.world.item.Items;
import java.util.*;
final class RuntimeLootServices {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void check(net.minecraft.server.MinecraftServer server) throws Exception {
        var lootAccess=dev.xyat.contentstudio.loot.server.LootTableOverrideStore.class.getDeclaredMethod("lootAccess",net.minecraft.server.ReloadableServerRegistries.Holder.class);lootAccess.setAccessible(true);
        var registry=((net.minecraft.core.RegistryAccess)lootAccess.invoke(null,server.reloadableRegistries())).registryOrThrow(Registries.LOOT_TABLE);
        var parentId=ResourceLocation.parse("minecraft:chests/simple_dungeon");
        var childId=ResourceLocation.parse("minecraft:chests/abandoned_mineshaft");
        var parentHolder=registry.getHolder(parentId).orElseThrow();var childHolder=registry.getHolder(childId).orElseThrow();
        var oldParent=parentHolder.value();var oldChild=childHolder.value();int numeric=registry.getId(oldParent);
        var parentTags=parentHolder.tags().toList();
        var appended=LootPool.lootPool().name("contentstudio_global_append#validation").setRolls(ConstantValue.exactly(1)).add(LootItem.lootTableItem(Items.DIAMOND));
        var child=LootTable.lootTable().setParamSet(LootContextParamSets.CHEST).withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1)).add(LootItem.lootTableItem(Items.STONE))).withPool(appended).build();child.setLootTableId(childId);child.freeze();
        var parent=LootTable.lootTable().setParamSet(LootContextParamSets.CHEST).withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1)).add(NestedLootTable.lootTableReference(ResourceKey.create(Registries.LOOT_TABLE,childId)))).withPool(LootPool.lootPool().name("contentstudio_global_append#validation").setRolls(ConstantValue.exactly(1)).add(LootItem.lootTableItem(Items.DIAMOND))).build();parent.setLootTableId(parentId);parent.freeze();
        Class<?> store=dev.xyat.contentstudio.loot.server.LootTableOverrideStore.class;
        var replace=store.getDeclaredMethod("replaceLootTables",net.minecraft.server.ReloadableServerRegistries.Holder.class,Map.class);replace.setAccessible(true);
        Map<String,Object> savedFields=new HashMap<>();
        try {
            replace.invoke(null,server.reloadableRegistries(),Map.of(parentId,parent,childId,child));
            require(registry.getHolder(parentId).orElseThrow()==parentHolder,"holder identity");require(parentHolder.value()==parent,"holder value");require(registry.byId(numeric)==parent&&registry.getId(parent)==numeric,"numeric indexes");require(registry.getKey(oldParent)==null&&parentId.equals(registry.getKey(parent)),"reverse indexes");require(parentTags.equals(parentHolder.tags().toList()),"holder tags");var frozenField=registry.getClass().getDeclaredField("frozen");frozenField.setAccessible(true);require(frozenField.getBoolean(registry),"frozen registry");
            for(String field:List.of("globalRemovalTargetTables","removedRuleCache","removedRuleCacheReady")){var f=store.getDeclaredField(field);f.setAccessible(true);savedFields.put(field,f.get(null));}
            set(store,"globalRemovalTargetTables",Set.of());
            var params=new LootParams.Builder(server.overworld()).withParameter(LootContextParams.ORIGIN,net.minecraft.world.phys.Vec3.ZERO).create(LootContextParamSets.CHEST);
            var generated=parent.getRandomItems(params,42L);
            require(generated.stream().filter(stack->stack.is(Items.DIAMOND)).mapToInt(net.minecraft.world.item.ItemStack::getCount).sum()==1,"nested append once");
            require(generated.stream().filter(stack->stack.is(Items.STONE)).mapToInt(net.minecraft.world.item.ItemStack::getCount).sum()==1,"nested table generation");
            set(store,"globalRemovalTargetTables",Set.of(parent));set(store,"removedRuleCache",List.of(dev.xyat.contentstudio.loot.GlobalRemoveRule.item("minecraft:diamond")));set(store,"removedRuleCacheReady",true);
            var filtered=parent.getRandomItems(params,42L);require(filtered.stream().noneMatch(stack->stack.is(Items.DIAMOND))&&filtered.stream().anyMatch(stack->stack.is(Items.STONE)),"global removal mixin");
        } finally {
            for(var field:savedFields.entrySet())set(store,field.getKey(),field.getValue());
            replace.invoke(null,server.reloadableRegistries(),Map.of(parentId,oldParent,childId,oldChild));
        }
    }
    private static void set(Class<?> type,String name,Object value)throws Exception{var field=type.getDeclaredField(name);field.setAccessible(true);field.set(null,value);}
}
*///?}
