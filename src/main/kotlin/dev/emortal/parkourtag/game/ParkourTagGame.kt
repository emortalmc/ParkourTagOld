package dev.emortal.parkourtag.game

import dev.emortal.immortal.game.*
import dev.emortal.immortal.game.EndGameQuotes.victory
import dev.emortal.immortal.util.MinestomRunnable
import dev.emortal.parkourtag.MapConfig
import dev.emortal.parkourtag.ParkourTagExtension
import dev.emortal.parkourtag.map.SchematicChunkLoader
import dev.emortal.parkourtag.utils.WorldBorderUtil.hideWarning
import dev.emortal.parkourtag.utils.WorldBorderUtil.showWarning
import dev.emortal.parkourtag.utils.parsed
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.event.player.PlayerStartFlyingEvent
import net.minestom.server.event.player.PlayerTickEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.network.packet.server.play.TeamsPacket
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import net.minestom.server.scoreboard.Sidebar
import net.minestom.server.sound.SoundEvent
import net.minestom.server.timer.Task
import net.minestom.server.utils.NamespaceID
import org.krystilize.blocky.Blocky
import org.krystilize.blocky.Schematics
import org.krystilize.blocky.data.Schemas
import world.cepi.kstom.Manager
import world.cepi.kstom.adventure.asMini
import world.cepi.kstom.adventure.sendMiniMessage
import world.cepi.kstom.event.listenOnly
import world.cepi.kstom.util.playSound
import world.cepi.particle.Particle
import world.cepi.particle.ParticleType
import world.cepi.particle.data.OffsetAndSpeed
import world.cepi.particle.showParticle
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ThreadLocalRandom
import java.util.stream.Collectors
import kotlin.io.path.nameWithoutExtension
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

class ParkourTagGame(gameOptions: GameOptions) : PvpGame(gameOptions) {
    private val goonsTeam =
        registerTeam(
            Team(
                "Goons",
                NamedTextColor.WHITE,
                nameTagVisibility = TeamsPacket.NameTagVisibility.HIDE_FOR_OTHER_TEAMS
            )
        )
    private val taggersTeam = registerTeam(
        Team(
            "Taggers",
            NamedTextColor.RED,
            nameTagVisibility = TeamsPacket.NameTagVisibility.ALWAYS
        )
    )

    lateinit var mapConfig: MapConfig

    lateinit var timerTask: MinestomRunnable

    private val taggerTitle = Title.title(
        Component.text("TAGGER", NamedTextColor.RED, TextDecoration.BOLD),
        Component.text("Tag all of the goons!", NamedTextColor.GRAY)
    )
    private val goonTitle = Title.title(
        Component.text("GOON", NamedTextColor.GREEN, TextDecoration.BOLD),
        Component.text("Run away from the taggers!", NamedTextColor.GRAY)
    )

    override fun playerJoin(player: Player) {
    }

    override fun playerLeave(player: Player) {
    }

    override fun gameStarted() {

        scoreboard?.updateLineContent("infoLine", Component.text("Rolling...", NamedTextColor.GRAY))

        var picked = players.random()
        val offset = ThreadLocalRandom.current().nextInt(players.size)
        (0..15).forEach {
            Manager.scheduler.buildTask {
                if (gameState == GameState.ENDING || players.size == 0) return@buildTask

                picked.isGlowing = false
                picked = players.elementAt((it + offset) % players.size)
                picked.isGlowing = true

                playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_SNARE, Sound.Source.BLOCK, 1f, 1f))
                showTitle(
                    Title.title(
                        Component.text(
                            picked.username,
                            TextColor.lerp((it / 15f), NamedTextColor.WHITE, NamedTextColor.RED)
                        ),
                        Component.empty(),
                        Title.Times.of(
                            Duration.ZERO, Duration.ofMillis(500), Duration.ofMillis(200)
                        )
                    )
                )

                if (it == 15) {
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
            }.delay(Duration.ofMillis((it * 4.0).pow(2.0).toLong())).schedule()
        }
    }

    override fun registerEvents() = with(eventNode) {
        listenOnly<EntityAttackEvent> {
            if (target !is Player || entity !is Player || gameState == GameState.ENDING) return@listenOnly

            val target = target as Player
            val attacker = entity as Player

            if (target.gameMode != GameMode.ADVENTURE || attacker.gameMode != GameMode.ADVENTURE) return@listenOnly

            if (taggersTeam.players.contains(attacker) && !taggersTeam.players.contains(target)) {
                kill(target, attacker)
            }
        }

        listenOnly<PlayerTickEvent> {
            /*if (goonsTeam.players.contains(player)) {
                val distanceToTagger = taggersTeam.players.minOf { it.position.distanceSquared(player.position) }

                if (distanceToTagger > 7*7) {
                    player.hideWarning()
                } else {
                    player.showWarning()
                }
            }*/

            if (taggersTeam.players.contains(player)) {
                val smallestDistanceToGoon = goonsTeam.players.minOfOrNull { goon -> player.position.distanceSquared(goon.position) }
                    ?: return@listenOnly

                if (player.aliveTicks % (sqrt(smallestDistanceToGoon) / 2).roundToInt().coerceAtLeast(2) == 0L)

                goonsTeam.playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_BASEDRUM, Sound.Source.MASTER, 1.5f, 1f), player.position)
            }


            if (player.gameMode == GameMode.ADVENTURE) {
                if (player.instance!!.getBlock(player.position).compare(Block.RAIL) &&
                    player.instance!!.getBlock(player.position.add(0.0, 1.0, 0.0))
                        .compare(Block.STRUCTURE_VOID)
                ) {
                    player.addEffect(Potion(PotionEffect.LEVITATION, 15, 3))
                }

                if (player.position.y < -5) {
                    sendMiniMessage(" <red>☠</red> <dark_gray>|</dark_gray> <gray><red>${player.username}</red> discovered the void")
                    kill(player, null)
                }
                if (player.position.y > 20) {
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

            object : MinestomRunnable(timer = timer, repeat = Duration.ofSeconds(1)) {
                var i = 3

                override fun run() {
                    i--

                    if (i < 0) {
                        player.isAllowFlying = true
                        player.sendActionBar(Component.empty())

                        cancel()
                        return
                    }

                    player.sendActionBar("<gray>Double jump is on cooldown for <bold><red>${i + 1}s".asMini())

                }
            }

        }
    }

    override fun playerDied(player: Player, killer: Entity?) {
        goonsTeam.remove(player)

        scoreboard!!.updateLineContent(
            "goons_left", Component.text("Goons: ", NamedTextColor.GRAY)
                .append(Component.text(goonsTeam.players.size, NamedTextColor.RED))
        )

        if (goonsTeam.players.isEmpty()) {
            victory(taggersTeam)
        }

        sendMiniMessage(" <red>☠</red> <dark_gray>|</dark_gray> <gray><white>${(killer as? Player)?.username}</white> tagged <red>${player.username}</red>")
        playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_PLING, Sound.Source.AMBIENT, 1f, 1f))

        showParticle(
            Particle.particle(
                type = ParticleType.LARGE_SMOKE,
                count = 15,
                data = OffsetAndSpeed(0.25f, 0.25f, 0.25f, 0.05f),
            ),
            player.position.asVec()
        )

        player.gameMode = GameMode.SPECTATOR
        player.isInvisible = true
        player.isGlowing = false
    }

    override fun respawn(player: Player) {

    }

    private fun setupGame() {
        goonsTeam.players.addAll(players.filter { !taggersTeam.players.contains(it) })

        goonsTeam.players.forEach {
            it.showTitle(goonTitle)
            it.teleport(mapConfig.goonSpawnPosition)
        }

        taggersTeam.players.forEach {
            it.showTitle(taggerTitle)
            it.teleport(mapConfig.taggerSpawnPosition)
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
                    .append(Component.text("Goons: ", NamedTextColor.GRAY))
                    .append(Component.text(goonsTeam.players.size, NamedTextColor.RED))
                    .build(),
                2
            )
        )

        scoreboard?.createLine(
            Sidebar.ScoreboardLine(
                "tagger",
                Component.text()
                    .append(Component.text("Taggers: ", NamedTextColor.GRAY))
                    .append(Component.text(taggersTeam.players.joinToString { it.username }, NamedTextColor.GOLD))
                    .build(),
                0
            )
        )

        Manager.scheduler.buildTask { registerEvents() }
            .delay(Duration.ofSeconds(2))
            .schedule()

        startTimer()
    }

    fun startTimer() {
        timerTask = object : MinestomRunnable(timer = timer, repeat = Duration.ofSeconds(1)) {
            var timeLeft = 90

            override fun run() {
                when {
                    timeLeft == 10 -> {
                        taggersTeam.players.forEach {
                            it.isAllowFlying = true
                            playSound(Sound.sound(SoundEvent.ENTITY_PLAYER_LEVELUP, Sound.Source.MASTER, 1f, 0.8f))
                            showTitle(
                                Title.title(
                                    Component.empty(),
                                    Component.text("Double jump has been enabled!", NamedTextColor.GRAY),
                                    Title.Times.of(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(2))
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
                                Component.text("Goons are now glowing!", NamedTextColor.GRAY),
                                Title.Times.of(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(2))
                            )
                        )
                    }
                    timeLeft == 0 -> {
                        victory(goonsTeam)
                        return
                    }
                    timeLeft < 10 -> {
                        playSound(Sound.sound(SoundEvent.BLOCK_WOODEN_BUTTON_CLICK_ON, Sound.Source.AMBIENT, 1f, 1f))
                        showTitle(
                            Title.title(
                                Component.empty(),
                                Component.text(timeLeft, NamedTextColor.GOLD),
                                Title.Times.of(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(2))
                            )
                        )
                    }
                }
                scoreboard!!.updateLineContent(
                    "time_left",
                    Component.text()
                        .append(Component.text("Time left: ", NamedTextColor.GREEN))
                        .append(Component.text(timeLeft.parsed(), NamedTextColor.GREEN))
                        .build()
                )
                timeLeft--
            }
        }
    }

    override fun gameWon(winningPlayers: Collection<Player>) {
        timerTask.cancel()
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

        return instance
    }

}