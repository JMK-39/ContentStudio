package dev.xyat.contentstudio.recipe.nativeedit;

import java.util.function.BiPredicate;

/** Uses JEI's ingredient hit testing when the API exposes only a background-inclusive rectangle. */
public final class JeiRecipeSlotBounds {
    private JeiRecipeSlotBounds() { }
    public static int[] resolve(int left,int top,int width,int height,BiPredicate<Double,Double> hit) {
        if(width<0||height<0||(long)width*height>1_000_000)throw new IllegalArgumentException("Invalid JEI slot area");
        int minX=left+width,minY=top+height,maxX=left-1,maxY=top-1;
        for(int y=top;y<top+height;y++)for(int x=left;x<left+width;x++)if(hit.test(x+0.5,y+0.5)) {
            minX=Math.min(minX,x);minY=Math.min(minY,y);maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);
        }
        return maxX<minX?new int[]{left,top,0,0}:new int[]{minX,minY,maxX-minX+1,maxY-minY+1};
    }
}
