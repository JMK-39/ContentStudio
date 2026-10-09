package dev.xyat.contentstudio.recipe.nativeedit;

import java.util.List;

/** Slot removal keeps a shared shaped ingredient until its last physical cell is cleared. */
public final class NativeRecipeSlotEdits {
    private NativeRecipeSlotEdits() { }
    public static void remove(NativeRecipeDocument document, NativeRecipeLayout layout, int index) {
        var path=layout.slots().get(index).path();
        var json=document.json();
        if(path.size()==2 && path.get(0).equals("key") && json.has("pattern") && json.get("pattern").isJsonArray()) {
            String symbol=path.get(1);
            int occurrence=0;
            for(int i=0;i<index;i++)if(layout.slots().get(i).path().equals(path))occurrence++;
            var pattern=json.getAsJsonArray("pattern");
            boolean cleared=false,remaining=false;
            for(int row=0;row<pattern.size();row++) {
                String text=pattern.get(row).getAsString();
                for(int col=0;col<text.length();col++)if(text.substring(col,col+1).equals(symbol)) {
                    if(!cleared && occurrence--==0) {
                        text=text.substring(0,col)+" "+text.substring(col+1);
                        document.setPrimitive(List.of("pattern",Integer.toString(row)),text);cleared=true;
                    }else remaining=true;
                }
            }
            if(!cleared)throw new IllegalArgumentException("Pattern slot no longer exists");
            if(!remaining)document.remove(path);
        }else document.remove(path);
    }
}
