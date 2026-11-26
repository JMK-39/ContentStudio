package dev.xyat.contentstudiovalidation;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.xyat.contentstudio.loot.server.LootTableOverrideStore;
import dev.xyat.kineticcore.api.resource.KineticResourceIds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

final class LootRuntimeFallbackChecks {
    private LootRuntimeFallbackChecks() {}

    static void check(MinecraftServer server) throws ReflectiveOperationException {
        var parse = LootTableOverrideStore.class.getDeclaredMethod("deserializeLootTable",
                MinecraftServer.class, ResourceLocation.class, JsonElement.class);
        parse.setAccessible(true);
        var id = KineticResourceIds.parse("contentstudio:chests/runtime_fallback_validation");
        var json = JsonParser.parseString("""
                {"type":"minecraft:chest","pools":[
                  {"rolls":1,"entries":[{"type":"minecraft:item","name":"minecraft:air"}]},
                  {"rolls":1,"entries":[{"type":"minecraft:item","name":"missing:reward"}]},
                  {"rolls":1,"entries":[{"type":"minecraft:item","name":"minecraft:stone"}]},
                  {"rolls":1,"entries":[{"type":"minecraft:item","name":"minecraft:barrier"}]}
                ]}
                """);
        var original = json.deepCopy();
        var params = new LootParams.Builder(server.overworld())
                .withParameter(LootContextParams.ORIGIN, Vec3.ZERO).create(LootContextParamSets.CHEST);
        var table = (LootTable) parse.invoke(null, server, id, json);
        var rewards = table.getRandomItems(params, 42L);
        if (rewards.stream().filter(stack -> stack.is(Items.BARRIER)).mapToInt(ItemStack::getCount).sum() != 1
                || rewards.stream().filter(stack -> stack.is(Items.STONE)).mapToInt(ItemStack::getCount).sum() != 1
                || rewards.size() != 2 || !original.equals(json)) {
            throw new AssertionError("The chest should yield only its explicitly configured stone and barrier: " + rewards);
        }

        for (String invalid : new String[]{"null", "{\"pools\":[{}]}"}) {
            var fallback = (LootTable) parse.invoke(null, server, id, JsonParser.parseString(invalid));
            if (!fallback.getRandomItems(params, 42L).isEmpty()) {
                throw new AssertionError("An unreadable loot table should give no rewards: " + invalid);
            }
        }
    }
}
