package dev.emortal.parkourtag

import dev.emortal.immortal.serializer.PositionSerializer
import kotlinx.serialization.Serializable
import net.minestom.server.coordinate.Pos

@Serializable
data class MapConfig(
    @Serializable(with = PositionSerializer::class)
    val taggerSpawnPosition: Pos = Pos(0.0, 10.0, 0.0),
    @Serializable(with = PositionSerializer::class)
    val goonSpawnPosition: Pos = Pos(0.0, 10.0, 0.0)
)
