package dev.emortal.parkourtag.map

import com.google.common.collect.HashBasedTable
import com.google.common.collect.Table
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.metadata.other.ArmorStandMeta
import net.minestom.server.entity.metadata.other.ItemFrameMeta
import net.minestom.server.instance.Chunk
import net.minestom.server.instance.DynamicChunk
import net.minestom.server.instance.IChunkLoader
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemMetaBuilder
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.utils.Rotation
import net.minestom.server.utils.chunk.ChunkUtils
import net.minestom.server.world.biomes.Biome
import org.krystilize.blocky.data.NbtData
import org.krystilize.blocky.sponge.SpongeSchematicData
import world.cepi.kstom.Manager
import java.util.*
import java.util.concurrent.CompletableFuture

class SchematicChunkLoader(val instance: Instance, vararg schematics: SpongeSchematicData) : IChunkLoader {
    private val blocks: Long2ObjectMap<Array<BlockEntry>> = Long2ObjectOpenHashMap()

    private data class BlockEntry(val pos: Int, val block: Block)

    override fun loadChunk(instance: Instance, chunkX: Int, chunkZ: Int): CompletableFuture<Chunk?> {
        val chunkIndex = ChunkUtils.getChunkIndex(chunkX, chunkZ)
        val entries = blocks[chunkIndex]
        if (entries == null || entries.isEmpty()) {
            return CompletableFuture.completedFuture(null)
        }
        val chunk: Chunk = DynamicChunk(instance, chunkX, chunkZ)
        for (entry in entries) {
            val pos = ChunkUtils.getBlockPosition(entry.pos, chunkX, chunkZ)
            chunk.setBlock(pos, entry.block)
        }
        return CompletableFuture.completedFuture(chunk)
    }

    override fun saveChunk(chunk: Chunk): CompletableFuture<Void> {
        return CompletableFuture.completedFuture(null)
    }

    fun generateEntity(spongeEntity: SpongeSchematicData.SpongeEntity) {
        println("Generating entity '${spongeEntity.id}'")
        val entityType = EntityType.fromNamespaceId(spongeEntity.id) ?: return

        val entity = when (entityType) {
            EntityType.ITEM_FRAME, EntityType.GLOW_ITEM_FRAME -> {
                val entity = Entity(entityType)
                val nbt = spongeEntity.nbtData()
                val itemFrameMeta = entity.entityMeta as ItemFrameMeta
                val rotation = nbt.get<Byte>("ItemRotation")
                itemFrameMeta.rotation = Rotation.values()[rotation.toInt()]
                val orientation = nbt.get<Byte>("Facing")
                for (value in ItemFrameMeta.Orientation.values()) {
                    if (value.ordinal == orientation.toInt()) {
                        itemFrameMeta.orientation = value
                    }
                }

                // Item
                val item = nbt.get<NbtData>("Item")
                val tag = item.get<NbtData>("tag")
                val id = item.get<String>("id")
                val cmd = tag.get<Int>("CustomModelData")
                val mat = Material.fromNamespaceId(id)
                Objects.requireNonNull(mat, "mat was null")
                val itemStack = ItemStack.builder(mat!!)
                    .meta { meta: ItemMetaBuilder -> meta.customModelData(cmd) }
                    .build()
                itemFrameMeta.item = itemStack

                entity
            }
            else -> null
        }



        entity?.setInstance(
            instance,
            Pos(spongeEntity.x, spongeEntity.y, spongeEntity.z, spongeEntity.yaw, spongeEntity.pitch)
        )
    }

    init {
        // Group all blocks together to be put into arrays
        val allBlocks: Table<Long, Int, Block> = HashBasedTable.create()

        // Group blocks together
        for (schematic in schematics) {

            for (block in schematic.allBlocks) {
                if (block == null) continue
                val blockString = block.blockString()
                val blockSplit = blockString.replace("]", "").split(Regex("[\\[]"))
                if (blockSplit[0] == "minecraft:air") continue

                val blockNamespace = blockSplit[0]


                val properties = hashMapOf<String, String>()
                if (blockSplit.size > 1) blockSplit[1].split(",").forEach {
                    val split = it.trim().split("=")
                    properties[split[0]] = split[1]
                }

                val minestomBlock = (Block.fromNamespaceId(blockNamespace)
                    ?: throw IllegalStateException("Illegal block state: $blockString"))
                    .withProperties(properties)
                    .withHandler(Manager.block.getHandler(blockNamespace))

                val chunkIndex = ChunkUtils.getChunkIndex(
                    ChunkUtils.getChunkCoordinate(block.x().toDouble()),
                    ChunkUtils.getChunkCoordinate(block.z().toDouble())
                )
                val blockIndex = ChunkUtils.getBlockIndex(block.x(), block.y(), block.z())
                allBlocks.put(chunkIndex, blockIndex, minestomBlock)
            }

            schematic.allEntities.forEach {
                generateEntity(it)
            }
        }

        // Create block entries and the block entry arrays
        for (chunkIndex in allBlocks.rowKeySet()) {
            val chunkBlocks = allBlocks.row(chunkIndex)
            val entries = chunkBlocks.entries
                .map {
                    BlockEntry(it.key, it.value)
                }
                .toTypedArray()
            blocks[chunkIndex as Long] = entries
        }


    }
}