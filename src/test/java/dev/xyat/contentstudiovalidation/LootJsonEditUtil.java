//? if >=1.21 {
/*package dev.xyat.contentstudiovalidation;
final class LootJsonEditUtil {
    private static Object invoke(String method,Class<?>[] types,Object... args){try{var type=Class.forName("dev.xyat.contentstudio.loot.client.gui.LootJsonEditUtil");var m=type.getDeclaredMethod(method,types);m.setAccessible(true);return m.invoke(null,args);}catch(java.lang.reflect.InvocationTargetException e){if(e.getCause() instanceof RuntimeException r)throw r;throw new IllegalStateException(e);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    static void setItemNbt(com.google.gson.JsonObject entry,net.minecraft.world.item.ItemStack stack){invoke("setItemNbt",new Class<?>[]{com.google.gson.JsonObject.class,net.minecraft.world.item.ItemStack.class},entry,stack);}
    static void setItemNbt(com.google.gson.JsonObject entry,String data){invoke("setItemNbt",new Class<?>[]{com.google.gson.JsonObject.class,String.class},entry,data);}
    static void applyItemNbt(com.google.gson.JsonObject entry,net.minecraft.world.item.ItemStack stack){invoke("applyItemNbt",new Class<?>[]{com.google.gson.JsonObject.class,net.minecraft.world.item.ItemStack.class},entry,stack);}
    static int unknownFunctionCount(com.google.gson.JsonObject entry){return (Integer)invoke("unknownFunctionCount",new Class<?>[]{com.google.gson.JsonObject.class},entry);}
static net.minecraft.world.item.ItemStack setSelectedItemComponents(com.google.gson.JsonObject entry,String id,String text){return (net.minecraft.world.item.ItemStack)invoke("setSelectedItemComponents",new Class<?>[]{com.google.gson.JsonObject.class,String.class,String.class},entry,id,text);}
}
*///?}
