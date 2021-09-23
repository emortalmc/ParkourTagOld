package dev.emortal.parkourtag.game

import net.minestom.server.instance.ChunkGenerator
import net.minestom.server.instance.ChunkPopulator
import net.minestom.server.instance.batch.ChunkBatch
import net.minestom.server.instance.block.Block
import net.minestom.server.world.biomes.Biome
import java.util.*

object FlatWorldGenerator : ChunkGenerator {
    override fun generateChunkData(batch: ChunkBatch, chunkX: Int, chunkZ: Int) {
        for (x in 0..15) {
            for (z in 0..15) {
                batch.setBlock(x, 0, z, Block.GRASS_BLOCK)
            }
        }
    }

    override fun fillBiomes(biomes: Array<out Biome>, chunkX: Int, chunkZ: Int) = Arrays.fill(biomes, Biome.PLAINS)
    override fun getPopulators(): MutableList<ChunkPopulator>? = null
}