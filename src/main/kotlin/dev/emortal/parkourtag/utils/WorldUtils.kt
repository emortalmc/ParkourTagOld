package dev.emortal.parkourtag.utils

import net.minestom.server.instance.batch.ChunkBatch
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.ints.Int2ObjectMap
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minestom.server.utils.chunk.ChunkUtils
import it.unimi.dsi.fastutil.longs.Long2ObjectFunction
import net.minestom.server.instance.block.Block
import org.krystilize.blocky.Blocky
import org.krystilize.blocky.Schematics
import org.krystilize.blocky.data.Schemas
import org.krystilize.blocky.sponge.SpongeSchematicData
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.lang.RuntimeException
import java.util.HashMap
import java.util.function.Supplier

object WorldUtils {
    fun generateBatches(
        batchSupplier: Supplier<ChunkBatch>,
        vararg data: SpongeSchematicData
    ): Long2ObjectMap<ChunkBatch> {
        val chunkIndex2Batch: Long2ObjectMap<ChunkBatch> = Long2ObjectOpenHashMap()
        val hash2Block: Int2ObjectMap<Block> = Int2ObjectOpenHashMap()

        // Group blocks into chunk2Blocks
        for (schematic in data) for (block in schematic.allBlocks) {
            if (block == null) {
                continue
            }
            val blockString = block.blockString()
            val hash = blockString.hashCode()

            // Get the block either from the string or from the namespace
            var minestomBlock = hash2Block[hash]
            if (minestomBlock == null) {
                val blockSplit = blockString.replace("]", "").split("[\\[]".toRegex(), 2).toTypedArray()
                val blockNamespace = blockSplit[0]
                minestomBlock = Block.fromNamespaceId(blockNamespace)
                checkNotNull(minestomBlock) { "Illegal block state: $blockString" }
                if (blockSplit.size > 1) {
                    minestomBlock = minestomBlock.withProperties(
                        convertBlockProperties(
                            blockSplit[1]
                        )
                    )
                }
                hash2Block[hash] = minestomBlock
            }
            if (minestomBlock.compare(Block.AIR)) {
                continue
            }

            // ChunkUtils static import
            val chunkIndex = ChunkUtils.getChunkIndex(
                ChunkUtils.getChunkCoordinate(block.x().toDouble()),
                ChunkUtils.getChunkCoordinate(block.z().toDouble())
            )
            val batch = chunkIndex2Batch.computeIfAbsent(
                chunkIndex,
                Long2ObjectFunction { ignored: Long -> batchSupplier.get() })
            batch.setBlock(block.x(), block.y(), block.z(), minestomBlock)
        }
        return chunkIndex2Batch
    }

    fun loadSchematic(path: String?, blocky: Blocky): SpongeSchematicData {
        // Load the schematic into a temp file
        val tempFile = File(System.getProperty("java.io.tmpdir") + "/" + "temp.schematic")
        run {
            val input = Thread.currentThread()
                .contextClassLoader
                .getResourceAsStream(path) ?: throw RuntimeException("Could not find stage.schem in resources")
            try {
                val output: OutputStream = FileOutputStream(tempFile)
                output.write(input.readAllBytes())
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        // Load schematic
        val schematic = Schematics.file(tempFile, Schemas.SPONGE)
        return blocky.read(schematic)
    }

    private fun convertBlockProperties(propertiesStr: String): Map<String, String> {
        val properties: MutableMap<String, String> = HashMap()
        for (property in propertiesStr.split("[,]".toRegex()).toTypedArray()) {
            val keyToValue = property.split("[=]".toRegex()).toTypedArray()
            properties[keyToValue[0]] = keyToValue[1]
        }
        return properties
    }
}