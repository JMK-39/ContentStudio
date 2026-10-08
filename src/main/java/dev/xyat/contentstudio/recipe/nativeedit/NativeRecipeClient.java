package dev.xyat.contentstudio.recipe.nativeedit;

import java.lang.ref.WeakReference;
import java.util.function.Consumer;

/** Only referenced by client callbacks; replies cannot open an abandoned page. */
public final class NativeRecipeClient {
    private static WeakReference<Consumer<NativeRecipeNetwork.Response>> listener = new WeakReference<>(null);
    private static long sequence;
    private NativeRecipeClient() { }
    public static void expect(long request, Consumer<NativeRecipeNetwork.Response> callback) {
        sequence = request; listener = new WeakReference<>(callback);
    }
    public static void cancel(Consumer<NativeRecipeNetwork.Response> callback) {
        if(listener.get()==callback)listener.clear();
    }
    public static void accept(NativeRecipeNetwork.Response response) {
        var callback = listener.get();
        if (callback != null && response.sequence() == sequence) callback.accept(response);
    }
}
