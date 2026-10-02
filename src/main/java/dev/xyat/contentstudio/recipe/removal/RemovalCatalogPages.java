package dev.xyat.contentstudio.recipe.removal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.nio.charset.StandardCharsets;

public final class RemovalCatalogPages {
    private static final int MAX_PAGE_BYTES = 60 * 1024;
    public record Page(int index, int total, long catalogVersion, List<RemovalCandidate> candidates) { }

    private RemovalCatalogPages() { }

    public static List<Page> of(List<RemovalCandidate> candidates, long version, int pageSize) {
        if (pageSize < 1 || pageSize > 128) throw new IllegalArgumentException("Invalid page size");
        List<RemovalCandidate> sorted = candidates.stream()
                .sorted(Comparator.comparing(candidate -> candidate.id().toString())).toList();
        List<List<RemovalCandidate>> groups = new ArrayList<>();
        List<RemovalCandidate> current = new ArrayList<>();
        int bytes = 0;
        for (RemovalCandidate candidate : sorted) {
            int cost = encodedEstimate(candidate);
            if (cost > MAX_PAGE_BYTES) throw new IllegalArgumentException("Candidate too large for page");
            if (!current.isEmpty() && (current.size() >= pageSize || bytes + cost > MAX_PAGE_BYTES)) {
                groups.add(List.copyOf(current));
                current.clear();
                bytes = 0;
            }
            current.add(candidate);
            bytes += cost;
        }
        groups.add(List.copyOf(current));
        int total = groups.size();
        List<Page> pages = new ArrayList<>(total);
        for (int index = 0; index < total; index++) pages.add(new Page(index, total, version, groups.get(index)));
        return List.copyOf(pages);
    }

    private static int encodedEstimate(RemovalCandidate candidate) {
        if (candidate.outputTagIds().size() > 512) {
            throw new IllegalArgumentException("Candidate has too many output tags");
        }
        int bytes = 64 + candidate.id().toString().getBytes(StandardCharsets.UTF_8).length;
        if (candidate.recipeType() != null) bytes += candidate.recipeType().toString().getBytes(StandardCharsets.UTF_8).length;
        if (candidate.outputItemId() != null) bytes += candidate.outputItemId().toString().getBytes(StandardCharsets.UTF_8).length;
        for (var tag : candidate.outputTagIds()) bytes += 8 + tag.toString().getBytes(StandardCharsets.UTF_8).length;
        return bytes;
    }

    public static boolean matchesVersion(Page page, long version) {
        return page.catalogVersion() == version;
    }
}
