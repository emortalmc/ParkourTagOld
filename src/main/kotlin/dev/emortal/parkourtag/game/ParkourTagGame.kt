package dev.emortal.parkourtag.game

import dev.emortal.immortal.config.GameOptions
import dev.emortal.immortal.game.*
import dev.emortal.immortal.util.*
import dev.emortal.parkourtag.MapConfig
import dev.emortal.parkourtag.ParkourTagExtension
import dev.emortal.parkourtag.map.SchematicChunkLoader
import dev.emortal.parkourtag.utils.showFirework
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.sound.Sound.Emitter
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.minestom.server.color.Color
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.other.ArmorStandMeta
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.event.player.PlayerStartFlyingEvent
import net.minestom.server.event.player.PlayerStartSneakingEvent
import net.minestom.server.event.player.PlayerTickEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.firework.FireworkEffect
import net.minestom.server.item.firework.FireworkEffectType
import net.minestom.server.network.packet.server.play.TeamsPacket
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.scoreboard.Sidebar
import net.minestom.server.sound.SoundEvent
import net.minestom.server.utils.NamespaceID
import org.krystilize.blocky.Blocky
import org.krystilize.blocky.Schematics
import org.krystilize.blocky.data.Schemas
import org.tinylog.kotlin.Logger
import world.cepi.kstom.Manager
import world.cepi.kstom.adventure.asMini
import world.cepi.kstom.adventure.sendMiniMessage
import world.cepi.kstom.event.listenOnly
import world.cepi.kstom.util.playSound
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ThreadLocalRandom
import java.util.stream.Collectors
import kotlin.io.path.nameWithoutExtension
import kotlin.math.roundToInt
import kotlin.math.sqrt

class ParkourTagGame(gameOptions: GameOptions) : PvpGame(gameOptions) {
    private val goonsTeam =
        registerTeam(
            Team(
                "Goons",
                NamedTextColor.WHITE,
                nameTagVisibility = TeamsPacket.NameTagVisibility.NEVER,
                canSeeInvisiblePlayers = true
            )
        )
    private val taggersTeam = registerTeam(
        Team(
            "Taggers",
            NamedTextColor.RED,
            nameTagVisibility = TeamsPacket.NameTagVisibility.NEVER
        )
    )

    lateinit var mapConfig: MapConfig

    var timerTask: MinestomRunnable? = null

    private val taggerTitle = Title.title(
        Component.text("SEEKER", NamedTextColor.RED, TextDecoration.BOLD),
        Component.text("Tag all of the hiders!", NamedTextColor.GRAY)
    )
    private val goonTitle = Title.title(
        Component.text("HIDER", NamedTextColor.GREEN, TextDecoration.BOLD),
        Component.text("Run away from the seekers!", NamedTextColor.GRAY)
    )

    override fun playerJoin(player: Player) {
    }

    override fun playerLeave(player: Player) {
    }

    override fun gameStarted() {

        scoreboard?.updateLineContent("infoLine", Component.text("Rolling...", NamedTextColor.GRAY))

        object : MinestomRunnable(coroutineScope = coroutineScope, repeat = Duration.ofMillis(1), iterations = 17) {
            var picked = players.random()
            val offset = ThreadLocalRandom.current().nextInt(players.size)

            override suspend fun run() {
                if (gameState == GameState.ENDING || players.size == 0) {
                    cancel()
                    return
                }

                val currentIter = currentIteration.get()
                repeat = Duration.ofMillis(currentIter * 30L)

                picked.isGlowing = false
                picked = players.elementAt((currentIter + offset) % players.size)
                picked.isGlowing = true

                playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_SNARE, Sound.Source.BLOCK, 1f, 1f))
                showTitle(
                    Title.title(
                        Component.text(picked.username),
                        Component.empty(),
                        Title.Times.times(
                            Duration.ZERO, Duration.ofMillis(500), Duration.ofMillis(200)
                        )
                    )
                )
            }

            override fun cancelled() {
                object : MinestomRunnable(coroutineScope = coroutineScope, repeat = Duration.ofMillis(100), iterations = 10*3) {
                    override suspend fun run() {
                        showTitle(
                            Title.title(
                                "<rainbow:${currentIteration.get()}>${picked.username}".asMini(),
                                Component.text("is the seeker", NamedTextColor.GRAY),
                                Title.Times.times(
                                    Duration.ZERO, Duration.ofMillis(500), Duration.ofMillis(500)
                                )
                            )
                        )
                    }
                }


                playSound(
                    Sound.sound(
                        SoundEvent.ENTITY_ENDER_DRAGON_GROWL,
                        Sound.Source.HOSTILE,
                        1f,
                        1f
                    )
                )

                taggersTeam.add(picked)

                scoreboard?.updateLineContent("infoLine", Component.empty())

                Manager.scheduler.buildTask {
                    setupGame()
                }.delay(Duration.ofSeconds(3)).schedule()
            }
        }
    }

    override fun registerEvents() = with(eventNode) {
        listenOnly<EntityAttackEvent> {
            if (target !is Player || entity !is Player || gameState == GameState.ENDING) return@listenOnly

            val target = target as Player
            val attacker = entity as Player

            if (target.gameMode != GameMode.ADVENTURE || attacker.gameMode != GameMode.ADVENTURE) return@listenOnly

            if (taggersTeam.players.contains(attacker) && !taggersTeam.players.contains(target)) {
                val minDistance = target.position.add(0.0, 1.5, 0.0).distanceSquared(attacker.position.add(0.0, 1.5, 0.0))
                    .coerceAtMost(target.position.distanceSquared(attacker.position.add(0.0, 1.5, 0.0)))
                if (minDistance > 3.5*3.5) return@listenOnly

                kill(target, attacker)
            }
        }

        listenOnly<PlayerTickEvent> {
            if (taggersTeam.players.contains(player)) {
                goonsTeam.players.forEach { goon ->
                    val distance = goon.position.distanceSquared(player.position)
                    if (distance > 25*25) return@forEach

                    if (player.aliveTicks % (sqrt(distance) / 2).roundToInt().coerceAtLeast(2) == 0L)
                        goon.playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_BASEDRUM, Sound.Source.MASTER, 1.5f, 1f), player.position)
                }
            }


            if (player.gameMode == GameMode.ADVENTURE) {
                if (player.instance!!.getBlock(player.position).compare(Block.RAIL) &&
                    player.instance!!.getBlock(player.position.add(0.0, 1.0, 0.0))
                        .compare(Block.STRUCTURE_VOID)
                ) {
                    player.addEffect(Potion(PotionEffect.LEVITATION, 15, 4))
                }

                if (player.position.y < -5) {
                    sendMiniMessage(" <red>☠</red> <dark_gray>|</dark_gray> <gray><red>${player.username}</red> discovered the void")
                    kill(player, null)
                }
                if (player.position.y > 30) {
                    sendMiniMessage(" <red>☠</red> <dark_gray>|</dark_gray> <gray><red>${player.username}</red> fell into the sky")
                    kill(player, null)
                }
            }
        }

        listenOnly<PlayerStartFlyingEvent> {
            if (!taggersTeam.players.contains(player)) return@listenOnly

            player.isFlying = false
            player.isAllowFlying = false
            player.velocity = player.position.direction().mul(15.0).withY(18.0)
            playSound(
                Sound.sound(
                    SoundEvent.ENTITY_GENERIC_EXPLODE,
                    Sound.Source.PLAYER,
                    1f,
                    1.5f
                ),
                player.position
            )

            object : MinestomRunnable(coroutineScope = coroutineScope, repeat = Duration.ofSeconds(1), iterations = 4) {
                override suspend fun run() {
                    player.sendActionBar("<gray>Double jump is on cooldown for <bold><red>${iterations - currentIteration.get()}s".asMini())
                }

                override fun cancelled() {
                    player.isAllowFlying = true
                    player.sendActionBar(Component.empty())
                    player.playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_PLING, Sound.Source.MASTER, 0.7f, 0.8f), Sound.Emitter.self())
                }
            }

        }
    }

    override fun playerDied(player: Player, killer: Entity?) {
        goonsTeam.remove(player)
        taggersTeam.remove(player)

        if (taggersTeam.players.isEmpty()) {
            victory(goonsTeam)
            Logger.warn("Taggers died")
        }
        if (goonsTeam.players.isEmpty()) {
            victory(taggersTeam)
            Logger.warn("goons died")
        }

        scoreboard!!.updateLineContent(
            "goons_left", Component.text("Hiders: ", NamedTextColor.GRAY)
                .append(Component.text(goonsTeam.players.size, NamedTextColor.RED))
        )

        if (killer != null && killer is Player) {
            sendMiniMessage(" <red>☠</red> <dark_gray>|</dark_gray> <gray><white>${killer.username}</white> tagged <red>${player.username}</red>")
        }
        playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_PLING, Sound.Source.AMBIENT, 1f, 1f))

        val rand = ThreadLocalRandom.current()
        players.showFirework(
            player.instance!!,
            player.position.add(0.0, 2.0, 0.0),
            mutableListOf(
                FireworkEffect(
                    false,
                    false,
                    FireworkEffectType.LARGE_BALL,
                    listOf(Color(java.awt.Color.HSBtoRGB(rand.nextFloat(), 1f, 1f))),
                    listOf(Color(java.awt.Color.HSBtoRGB(rand.nextFloat(), 1f, 1f)))
                )
            )
        )

        player.team = null
        player.gameMode = GameMode.SPECTATOR
        player.isInvisible = true
        player.isGlowing = false
    }

    override fun respawn(player: Player) {
        player.sendMessage("Well this is weird...")
    }

    private fun setupGame() {
        players.filter { !taggersTeam.players.contains(it) }.forEach {
            goonsTeam.add(it)
        }

        goonsTeam.players.forEach {
            it.showTitle(goonTitle)
            it.teleport(mapConfig.goonSpawnPosition)
            it.addEffect(Potion(PotionEffect.INVISIBILITY, 1, 7*20))
        }

        val holdingEntity = Entity(EntityType.ARMOR_STAND)
        holdingEntity.setNoGravity(true)
        holdingEntity.isInvisible = true
        holdingEntity.setInstance(instance, mapConfig.taggerSpawnPosition)

        taggersTeam.players.forEach {
            it.showTitle(taggerTitle)
            it.addEffect(Potion(PotionEffect.BLINDNESS, 1, 8*20))
            holdingEntity.addPassenger(it)
            it.isGlowing = true
        }

        scoreboard?.createLine(
            Sidebar.ScoreboardLine(
                "time_left",
                Component.empty(),
                4
            )
        )
        scoreboard?.createLine(
            Sidebar.ScoreboardLine(
                "goons_left",
                Component.text()
                    .append(Component.text("Hiders: ", NamedTextColor.GRAY))
                    .append(Component.text(goonsTeam.players.size, NamedTextColor.RED))
                    .build(),
                2
            )
        )

        scoreboard?.createLine(
            Sidebar.ScoreboardLine(
                "tagger",
                Component.text()
                    .append(Component.text("Seekers: ", NamedTextColor.GRAY))
                    .append(Component.text(taggersTeam.players.joinToString { it.username }, NamedTextColor.GOLD))
                    .build(),
                0
            )
        )

        Manager.scheduler.buildTask { registerEvents() }
            .delay(Duration.ofSeconds(2))
            .schedule()
        Manager.scheduler.buildTask {
            val title = Title.title(Component.empty(), Component.text("Seeker has been released!", NamedTextColor.YELLOW), Title.Times.times(Duration.ZERO, Duration.ofMillis(500), Duration.ofMillis(200)))
            val sound = Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_PLING, Sound.Source.MASTER, 1f, 1f)

            holdingEntity.remove()
            taggersTeam.players.forEach {
                it.teleport(mapConfig.taggerSpawnPosition)
            }
            players.forEach {
                it.clearEffects()
                it.showTitle(title)
                it.playSound(sound, Emitter.self())
            }
        }.delay(Duration.ofSeconds(7)).schedule()

        startTimer()
    }

    fun startTimer() {
        timerTask = object : MinestomRunnable(coroutineScope = coroutineScope, repeat = Duration.ofSeconds(1)) {
            var timeLeft = 90

            override suspend fun run() {
                when {
                    timeLeft == 10 -> {
                        taggersTeam.players.forEach {
                            it.isAllowFlying = true
                            playSound(Sound.sound(SoundEvent.ENTITY_PLAYER_LEVELUP, Sound.Source.MASTER, 1f, 0.8f))
                            showTitle(
                                Title.title(
                                    Component.empty(),
                                    Component.text("Double jump has been enabled!", NamedTextColor.GRAY),
                                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(2))
                                )
                            )
                        }
                    }
                    timeLeft == 30 -> {
                        goonsTeam.players.forEach {
                            it.isGlowing = true
                        }
                        playSound(Sound.sound(SoundEvent.ENTITY_PLAYER_LEVELUP, Sound.Source.MASTER, 1f, 0.8f))
                        showTitle(
                            Title.title(
                                Component.empty(),
                                Component.text("Hiders are now glowing!", NamedTextColor.GRAY),
                                Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(2))
                            )
                        )
                    }
                    timeLeft == 0 -> {
                        Logger.warn("Ran out of time")
                        victory(goonsTeam)
                        return
                    }
                    timeLeft < 10 -> {
                        playSound(Sound.sound(SoundEvent.BLOCK_WOODEN_BUTTON_CLICK_ON, Sound.Source.AMBIENT, 1f, 1f))
                        showTitle(
                            Title.title(
                                Component.empty(),
                                Component.text(timeLeft, NamedTextColor.GOLD),
                                Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(2))
                            )
                        )
                    }
                }
                scoreboard!!.updateLineContent(
                    "time_left",
                    Component.text("Time left: ${timeLeft.parsed()}", NamedTextColor.GREEN)
                )
                timeLeft--
            }
        }
    }

    override fun gameWon(winningPlayers: Collection<Player>) {
        timerTask?.cancel()

        val seekersWon = goonsTeam.players.isEmpty()

        val message = Component.text()
            .append(Component.text(" ${centerText("VICTORY", true)}", NamedTextColor.GOLD, TextDecoration.BOLD))
            .also {
                if (seekersWon) {
                    it.append(Component.text("\n${centerText("All of the hiders were found!")}", NamedTextColor.GRAY))
                } else {
                    it.append(Component.text("\n${centerText("The seekers ran out of time!")}", NamedTextColor.GRAY))
                }
            }
            .append(Component.text("\n\n ${centerSpaces("Winning team: Seeker")}Winning team: ", NamedTextColor.GRAY))
            .also {
                if (seekersWon) {
                    it.append(Component.text("Seekers", NamedTextColor.RED, TextDecoration.BOLD))
                } else {
                    it.append(Component.text("Hiders", NamedTextColor.GREEN, TextDecoration.BOLD))
                        .append(Component.text("\n ${centerSpaces("Survivors: ${goonsTeam.players.joinToString { it.username }}")}Survivors: ", NamedTextColor.GRAY))
                        .append(Component.text(goonsTeam.players.joinToString { it.username }, NamedTextColor.WHITE))
                }
            }
            .armify()

        sendMessage(message)
    }

    override fun gameDestroyed() {
    }

    override fun instanceCreate(): Instance {
        val randomMap = File("./maps/parkourtag/" + Files.list(Path.of("./maps/parkourtag/"))
            .map { it.nameWithoutExtension }
            .collect(Collectors.toSet())
            .random() + ".schem")

        val schematic = Schematics.file(randomMap, Schemas.SPONGE)
        val data = Blocky.builder().compression(true).build().read(schematic)

        val instance = Manager.instance.createInstanceContainer(
            Manager.dimensionType.getDimension(NamespaceID.from("fullbright"))!!
        )
        val schematicChunkLoader = SchematicChunkLoader(data)
        instance.chunkLoader = schematicChunkLoader

        spawnPosition = Pos(data.width / 2.0, 10.0, data.length / 2.0)

        mapConfig = ParkourTagExtension.config.mapSpawnPositions[randomMap.nameWithoutExtension] ?: MapConfig()

        instance.time = 0
        instance.timeRate = 0
        instance.timeUpdate = null

        return instance
    }

}