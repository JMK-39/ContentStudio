package dev.xyat.contentstudio.recipe.client.gui;

import dev.xyat.kineticcore.api.client.gui.render.KineticGraphics;
import dev.xyat.kineticcore.api.client.gui.render.KineticTexture;
import dev.xyat.kineticcore.api.client.gui.text.KineticText;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

/** Complete native recipe slot, centred with one scale for both axes. */
final class RecipeSlots {
    private static final KineticTexture BACKGROUND =
            KineticTexture.of("minecraft", "textures/gui/container/crafting_table.png");
    private static final int NATIVE_SIZE = 18;

    private RecipeSlots() {}
    /** Tag identity and quantity share the lower-right corner, two pixels clear of the native frame. */
    static void tagMarker(KineticGraphics graphics,int x,int y,int size,int count) {
        var text=Component.literal(count>1?Integer.toString(count):"").append(Component.literal("#").withStyle(ChatFormatting.GOLD));
        int width=KineticText.width(text),height=KineticText.lineHeight();float scale=Math.min(.75f,Math.min((size-4f)/Math.max(1,width),(size-4f)/Math.max(1,height)));
        graphics.push();
        try {
            graphics.raise(1);graphics.translate(x+size-2-width*scale,y+size-2-height*scale);graphics.scale(scale,scale);
            graphics.text(text,0,0,0xFFFFFFFF,true);
        }finally{graphics.pop();}
    }

    static void draw(KineticGraphics graphics, int x, int y, int areaSize) {
        int size = Math.min(NATIVE_SIZE, areaSize);
        if (size <= 0) return;
        int offset = (areaSize - size) / 2;
        graphics.texture(BACKGROUND, x + offset, y + offset, size, size, 29f, 16f, NATIVE_SIZE, NATIVE_SIZE);
    }
}
