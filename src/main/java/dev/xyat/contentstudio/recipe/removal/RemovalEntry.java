package dev.xyat.contentstudio.recipe.removal;

import dev.xyat.kineticcore.api.network.NetworkBuffer;

import java.util.Objects;

public record RemovalEntry(RemovalMode mode, String value, String comment) {
    public RemovalEntry(RemovalMode mode, String value, String comment) {
        this.mode = mode;
        this.value = value;
        this.comment = comment != null ? comment : "";
    }

    public void toNetwork(NetworkBuffer buffer) {
        buffer.writeEnum(mode);
        buffer.writeUtf(value);
        buffer.writeUtf(comment);
    }

    public static RemovalEntry fromNetwork(NetworkBuffer buffer) {
        return new RemovalEntry(buffer.readEnum(RemovalMode.class), buffer.readUtf(), buffer.readUtf());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RemovalEntry that = (RemovalEntry) o;
        return mode == that.mode && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mode, value);
    }
}
