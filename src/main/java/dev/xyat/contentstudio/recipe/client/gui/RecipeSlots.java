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
    /** Tag identity is at the upper left; quantity remains at the lower right. */
    static void tagMarker(KineticGraphics graphics,int x,int y,int size,int count) {
        var text=Component.literal("#").withStyle(ChatFormatting.GOLD);
        int width=KineticText.width(text),height=KineticText.lineHeight();float scale=Math.min(.75f,Math.min((size-4f)/Math.max(1,width),(size-4f)/Math.max(1,height)));
        graphics.push();
        try {
            graphics.raise(1);graphics.translate(x+2,y+2);graphics.scale(scale,scale);
            graphics.text(text,0,0,0xFFFFFFFF,true);
        }finally{graphics.pop();}
        if(count>1) {
            String quantity=Integer.toString(count);
            float quantityScale=Math.min(1f,(size-4f)/Math.max(1,KineticText.width(quantity)));
            graphics.push();
            try {
                graphics.raise(1);graphics.translate(x+size-2-KineticText.width(quantity)*quantityScale,y+size-2-height*quantityScale);graphics.scale(quantityScale,quantityScale);
                graphics.text(quantity,0,0,0xFFFFFFFF,true);
            }finally{graphics.pop();}
        }
    }

    static void draw(KineticGraphics graphics, int x, int y, int areaSize) {
        int size = Math.min(NATIVE_SIZE, areaSize);
        if (size <= 0) return;
        int offset = (areaSize - size) / 2;
        graphics.texture(BACKGROUND, x + offset, y + offset, size, size, 29f, 16f, NATIVE_SIZE, NATIVE_SIZE);
    }
}
