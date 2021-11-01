package dev.emortal.parkourtag

import kotlinx.serialization.Serializable
import net.minestom.server.coordinate.Pos
import world.cepi.kstom.serializer.PositionSerializer

@Serializable
class ParkourConfig(
    @Serializable(with = PositionSerializer::class)
    val taggerSpawnPos: Pos = Pos(0.0, 10.0, 0.0),
    @Serializable(with = PositionSerializer::class)
    val goonSpawnPos: Pos = Pos(0.0, 10.0, 0.0),
)