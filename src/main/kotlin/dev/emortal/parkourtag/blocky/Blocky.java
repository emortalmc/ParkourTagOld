package dev.emortal.parkourtag.blocky;

import java.util.function.Function;
import org.jetbrains.annotations.NotNull;
import org.krystilize.blocky.Schematic;
import org.krystilize.blocky.SchematicSchema;

public final class Blocky {
    private final boolean readCompressed;

    private final boolean writeCompressed;

    private Blocky(@NotNull Builder<Blocky> builder) {
        this.readCompressed = builder.readCompressed;
        this.writeCompressed = builder.writeCompressed;
    }

    public static Builder<Blocky> builder() {
        return new Builder<>(Blocky::new);
    }

    public <D extends org.krystilize.blocky.data.SchematicData> void read(@NotNull Schematic<D> schematic, @NotNull D data) {
        SchematicSchema<D> schema = schematic.getSchema();
        try {
            schema.read(schematic.getReader(this.readCompressed), data);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public <D extends org.krystilize.blocky.data.SchematicData> D read(@NotNull Schematic<D> schematic) {
        D data = schematic.getSchema().createData();
        read(schematic, data);
        return data;
    }

    public <D extends org.krystilize.blocky.data.SchematicData> void write(@NotNull D data, @NotNull Schematic<D> schematic) {
        write(data, schematic, true);
    }

    public <D extends org.krystilize.blocky.data.SchematicData> void write(@NotNull D data, @NotNull Schematic<D> schematic, boolean flush) {
        SchematicSchema<D> schema = schematic.getSchema();
        try {
            schema.write(data, schematic.getWriter(this.writeCompressed));
            if (flush)
                schematic.flush();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static class Builder<B extends Blocky> {
        @NotNull
        final Function<Builder<B>, B> blockyConstructor;

        boolean readCompressed = true;

        boolean writeCompressed = true;

        private Builder(@NotNull Function<Builder<B>, B> blockyConstructor) {
            this.blockyConstructor = blockyConstructor;
        }

        public Builder<B> compression(boolean compression) {
            this.readCompressed = compression;
            this.writeCompressed = compression;
            return this;
        }

        public Builder<B> readCompression(boolean readCompressed) {
            this.readCompressed = readCompressed;
            return this;
        }

        public Builder<B> writeCompression(boolean writeCompressed) {
            this.writeCompressed = writeCompressed;
            return this;
        }

        @NotNull
        public B build() {
            return this.blockyConstructor.apply(this);
        }
    }
}
