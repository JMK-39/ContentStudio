//? if >=1.21 {
/*package dev.xyat.contentstudio.villager.util;
public final class TradeItemData {
    private TradeItemData() {}
    public static net.minecraft.world.item.trading.ItemCost cost(net.minecraft.world.item.ItemStack stack) {
        var predicate=net.minecraft.core.component.DataComponentPredicate.builder();
        for (var entry:stack.getComponentsPatch().entrySet()) {
            if(entry.getValue().isEmpty()) return null;
            expect(predicate,entry.getKey(),entry.getValue().get());
        }
        return new net.minecraft.world.item.trading.ItemCost(stack.getItemHolder(),stack.getCount(),predicate.build());
    }
    @SuppressWarnings("unchecked")
    private static <T> void expect(net.minecraft.core.component.DataComponentPredicate.Builder builder, net.minecraft.core.component.DataComponentType<T> type,Object value) { builder.expect(type,(T)value); }
}
*///?}
