package dev.emortal.parkourtag

import dev.emortal.immortal.config.ConfigHelper
import dev.emortal.immortal.game.GameManager
import dev.emortal.parkourtag.commands.RigCommand
import dev.emortal.parkourtag.game.ParkourTagGame
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.MinecraftServer
import net.minestom.server.extensions.Extension
import net.minestom.server.utils.NamespaceID
import net.minestom.server.world.DimensionType
import world.cepi.kstom.Manager
import world.cepi.kstom.command.register
import world.cepi.kstom.command.unregister
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors
import kotlin.io.path.nameWithoutExtension

class ParkourTagExtension : Extension() {

    companion object {
        lateinit var config: ParkourConfig
    }

    override fun initialize() {
        val maps = Files.list(Path.of("./maps/parkourtag/")).map { it.nameWithoutExtension }.collect(Collectors.toSet())
        logger.info("Found ${maps.size} maps:\n- ${maps.joinToString("\n- ")}")

        val parkourConfig = ParkourConfig()
        val mapConfigMap = ConcurrentHashMap<String, MapConfig>()

        Manager.dimensionType.addDimension(
            DimensionType.builder(NamespaceID.from("immortal:the_end"))
                .effects("minecraft:the_end")
                .build()
        )

        maps.forEach {
            mapConfigMap[it] = MapConfig()
        }

        parkourConfig.mapSpawnPositions = mapConfigMap
        config = ConfigHelper.initConfigFile(Path.of("./parkour.json"), parkourConfig)

        GameManager.registerGame<ParkourTagGame>(
            "parkourtag",
            Component.text("ParkourTag", NamedTextColor.GREEN, TextDecoration.BOLD),
            showsInSlashPlay = true
        )

        RigCommand.register()

        logger.info("[ParkourTag] Initialized!")
    }

    override fun terminate() {
        RigCommand.unregister()

        logger.info("[ParkourTag] Terminated!")
    }

}