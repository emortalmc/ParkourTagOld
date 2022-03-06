package dev.emortal.parkourtag.map

import dev.emortal.parkourtag.utils.WorldUtils.generateBatches
import org.krystilize.blocky.sponge.SpongeSchematicData
import net.minestom.server.instance.IChunkLoader
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import net.minestom.server.instance.Chunk
import net.minestom.server.utils.chunk.ChunkUtils
import net.minestom.server.instance.DynamicChunk
import net.minestom.server.instance.Instance
import net.minestom.server.instance.batch.ChunkBatch
import java.util.concurrent.CompletableFuture

class SchematicChunkLoader(vararg data: SpongeSchematicData) : IChunkLoader {
    private val batches: Long2ObjectMap<ChunkBatch>

    init {
        batches = generateBatches({ ChunkBatch() }, *data)
    }

    override fun loadChunk(instance: Instance, chunkX: Int, chunkZ: Int): CompletableFuture<Chunk?> {
        val index = ChunkUtils.getChunkIndex(chunkX, chunkZ)
        val batch = batches[index] ?: return CompletableFuture.completedFuture(null)
        val chunk = DynamicChunk(instance, chunkX, chunkZ)
        val future = CompletableFuture<Chunk?>()
        batch.apply(instance, chunk) { future.complete(it) }
        return future
    }

    override fun saveChunk(chunk: Chunk): CompletableFuture<Void> {
        return CompletableFuture.completedFuture(null)
    }
}