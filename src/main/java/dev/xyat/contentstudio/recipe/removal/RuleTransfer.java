package dev.xyat.contentstudio.recipe.removal;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Bounded ordered transfer shared by server draft upload and client state download. */
public final class RuleTransfer {
    public static final int CHUNK_BYTES = 32 * 1024;
    public static final int MAX_BYTES = 4 * 1024 * 1024;

    private RuleTransfer() { }

    public static List<byte[]> split(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Invalid transfer size");
        }
        List<byte[]> result = new ArrayList<>();
        for (int offset = 0; offset < bytes.length; offset += CHUNK_BYTES) {
            result.add(Arrays.copyOfRange(bytes, offset, Math.min(bytes.length, offset + CHUNK_BYTES)));
        }
        return List.copyOf(result);
    }

    public static final class Assembler {
        private final int totalBytes;
        private final int totalChunks;
        private final ByteArrayOutputStream output;
        private int nextIndex;

        public Assembler(int totalBytes, int totalChunks) {
            if (totalBytes < 1 || totalBytes > MAX_BYTES || totalChunks < 1
                    || totalChunks > (MAX_BYTES + CHUNK_BYTES - 1) / CHUNK_BYTES
                    || totalChunks != (totalBytes + CHUNK_BYTES - 1) / CHUNK_BYTES) {
                throw new IllegalArgumentException("Invalid transfer dimensions");
            }
            this.totalBytes = totalBytes;
            this.totalChunks = totalChunks;
            this.output = new ByteArrayOutputStream(totalBytes);
        }

        public void append(int index, byte[] chunk) {
            if (index != nextIndex || chunk == null || chunk.length < 1 || chunk.length > CHUNK_BYTES
                    || output.size() + chunk.length > totalBytes
                    || (index < totalChunks - 1 && chunk.length != CHUNK_BYTES)) {
                throw new IllegalArgumentException("Invalid transfer chunk");
            }
            output.writeBytes(chunk);
            nextIndex++;
        }

        public byte[] finish() {
            if (nextIndex != totalChunks || output.size() != totalBytes) {
                throw new IllegalStateException("Incomplete transfer");
            }
            return output.toByteArray();
        }
    }
}
