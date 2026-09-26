package dev.xyat.contentstudio.recipe.removal;

import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;

/** Ties action permission to a row supplied by the server's original catalog, never its recipe ID. */
public final class OriginalRecipeRows<R> {
    private final IdentityHashMap<R, RemovalCandidate> candidates = new IdentityHashMap<>();

    public void clear() {
        candidates.clear();
    }

    public void register(R row, RemovalCandidate candidate) {
        candidates.put(row, candidate);
    }

    public @Nullable RemovalCandidate candidateFor(R row) {
        return candidates.get(row);
    }
}
