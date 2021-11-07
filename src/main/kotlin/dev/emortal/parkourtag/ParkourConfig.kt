package dev.emortal.parkourtag

import kotlinx.serialization.Serializable

@Serializable
class ParkourConfig(
    var mapSpawnPositions: HashMap<String, MapConfig> = hashMapOf()
)