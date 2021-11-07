package dev.emortal.parkourtag

import kotlinx.serialization.Serializable
import net.minestom.server.coordinate.Pos
import world.cepi.kstom.serializer.PositionSerializer

@Serializable
data class MapConfig(
    @Serializable(with = PositionSerializer::class)
    val taggerSpawnPosition: Pos = Pos(0.0, 10.0, 0.0),
    @Serializable(with = PositionSerializer::class)
    val goonSpawnPosition: Pos = Pos(0.0, 10.0, 0.0)
)
