package dev.emortal.parkourtag

import dev.emortal.immortal.Immortal
import dev.emortal.immortal.config.ConfigHelper
import dev.emortal.immortal.game.GameManager
import dev.emortal.parkourtag.commands.RigCommand
import dev.emortal.parkourtag.game.ParkourTagGame
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.MinecraftServer
import org.tinylog.kotlin.Logger
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors
import kotlin.io.path.nameWithoutExtension

fun main() {
    Immortal.initAsServer()

    val maps = Files.list(Path.of("./maps/parkourtag/")).map { it.nameWithoutExtension }.collect(Collectors.toSet())
    Logger.info("Found ${maps.size} maps:\n- ${maps.joinToString("\n- ")}")

    val parkourConfig = ParkourConfig()
    val mapConfigMap = ConcurrentHashMap<String, MapConfig>()

    maps.forEach {
        mapConfigMap[it] = MapConfig()
    }

    parkourConfig.mapSpawnPositions = mapConfigMap
    ParkourTagMain.config = ConfigHelper.initConfigFile(Path.of("./parkour.json"), parkourConfig)

    GameManager.registerGame<ParkourTagGame>(
        "parkourtag",
        Component.text("ParkourTag", NamedTextColor.GREEN, TextDecoration.BOLD),
        showsInSlashPlay = true
    )

    val cm = MinecraftServer.getCommandManager()
    cm.register(RigCommand)
}

class ParkourTagMain {

    companion object {
        lateinit var config: ParkourConfig
    }

}