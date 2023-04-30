package dev.emortal.parkourtag

import dev.emortal.immortal.Immortal
import dev.emortal.immortal.config.ConfigHelper
import dev.emortal.immortal.game.GameManager
import dev.emortal.parkourtag.ParkourTagMain.Companion.config
import dev.emortal.parkourtag.ParkourTagMain.Companion.instances
import dev.emortal.parkourtag.ParkourTagMain.Companion.mapNameTag
import dev.emortal.parkourtag.commands.RigCommand
import dev.emortal.parkourtag.game.ParkourTagGame
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.MinecraftServer
import net.minestom.server.instance.AnvilLoader
import net.minestom.server.instance.Chunk
import net.minestom.server.instance.InstanceContainer
import net.minestom.server.tag.Tag
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors
import kotlin.io.path.nameWithoutExtension

private val LOGGER = LoggerFactory.getLogger(ParkourTagMain::class.java)

fun main() {
    Immortal.initAsServer()

    val maps = Files.list(Path.of("./maps/parkourtag/")).collect(Collectors.toSet())

    val parkourConfig = ParkourConfig()
    val mapConfigMap = ConcurrentHashMap<String, MapConfig>()

    maps.forEach {
        mapConfigMap[it.nameWithoutExtension] = MapConfig()
    }

    parkourConfig.mapSpawnPositions = mapConfigMap
    config = ConfigHelper.initConfigFile(Path.of("./parkour.json"), parkourConfig)
    LOGGER.info("Loaded parkourtag config")

    maps.forEach {
        preloadInstance(it)
        LOGGER.info("Preloading map ${it.nameWithoutExtension}")
    }

    GameManager.registerGame(
        { ParkourTagGame() },
        "parkourtag"
    )

    val cm = MinecraftServer.getCommandManager()
    cm.register(RigCommand)
}

fun preloadInstance(map: Path): InstanceContainer {
    val newInstance = MinecraftServer.getInstanceManager().createInstanceContainer()

    newInstance.chunkLoader = AnvilLoader(map)

    newInstance.setTag(mapNameTag, map.nameWithoutExtension)
    newInstance.setTag(GameManager.doNotUnregisterTag, true)
    newInstance.setTag(GameManager.doNotAutoUnloadChunkTag, true)

    newInstance.time = 0
    newInstance.timeRate = 0
    newInstance.timeUpdate = null

    newInstance.enableAutoChunkLoad(false)
    newInstance.setTag(Tag.Boolean("doNotAutoUnloadChunk"), true)

    val radius = 5
    val chunkFutures = mutableListOf<CompletableFuture<Chunk>>()
    var i = 0
    for (x in -radius..radius) {
        for (z in -radius..radius) {
            chunkFutures.add(newInstance.loadChunk(x, z))
            i++
        }
    }

    instances.add(newInstance)

    return newInstance
}

class ParkourTagMain {

    companion object {
        lateinit var config: ParkourConfig
        val instances: MutableSet<InstanceContainer> = ConcurrentHashMap.newKeySet()
        val mapNameTag = Tag.String("mapName")
    }

}