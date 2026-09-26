package dev.xyat.contentstudio.recipe.removal;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** The only write boundary for a complete, validated removal-rule draft. */
public final class RemovalDraftCommitter {
    public enum Status { APPLIED, REJECTED, PERSISTED_RELOAD_FAILED }

    public record Outcome(Status status, Throwable cause) { }

    @FunctionalInterface
    public interface Writer {
        void write(List<RemovalEntry> rules) throws IOException;
    }

    private RemovalDraftCommitter() { }

    public static CompletableFuture<Outcome> commit(List<RemovalEntry> draft, Writer writer,
                                                     Supplier<CompletableFuture<Void>> reload) {
        final List<RemovalEntry> rules;
        try {
            rules = RemovalStateCodec.decodeRules(RemovalStateCodec.encodeRules(draft));
        } catch (RuntimeException exception) {
            return CompletableFuture.completedFuture(new Outcome(Status.REJECTED, exception));
        }
        try {
            writer.write(rules);
        } catch (Exception exception) {
            return CompletableFuture.completedFuture(new Outcome(Status.REJECTED, exception));
        }
        try {
            return reload.get().handle((unused, error) -> error == null
                    ? new Outcome(Status.APPLIED, null)
                    : new Outcome(Status.PERSISTED_RELOAD_FAILED, error));
        } catch (Exception exception) {
            return CompletableFuture.completedFuture(new Outcome(Status.PERSISTED_RELOAD_FAILED, exception));
        }
    }
}
