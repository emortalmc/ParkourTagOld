package dev.emortal.parkourtag.game

import dev.emortal.immortal.game.*
import dev.emortal.parkourtag.MapConfig
import dev.emortal.parkourtag.ParkourTagExtension
import dev.emortal.parkourtag.map.SchematicChunkLoader
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
import world.cepi.kstom.util.MinestomRunnable
import world.cepi.kstom.util.playSound
import world.cepi.particle.Particle
import world.cepi.particle.ParticleType
import world.cepi.particle.data.OffsetAndSpeed
import world.cepi.particle.showParticle
import java.io.File
import java.time.Duration
import kotlin.math.pow


class ParkourTagGame(gameOptions: GameOptions) : PvpGame(gameOptions) {
    private val goonsTeam =
        registerTeam(
            Team(
                "goons",
                NamedTextColor.WHITE,
                nameTagVisibility = TeamsPacket.NameTagVisibility.HIDE_FOR_OTHER_TEAMS
            )
        )
    private val taggersTeam = registerTeam(
        Team(
            "taggers",
            NamedTextColor.RED,
            nameTagVisibility = TeamsPacket.NameTagVisibility.ALWAYS
        )
    )

    lateinit var spawnPos: Pos
    lateinit var mapConfig: MapConfig

    private var timerTask: Task? = null


    private val taggerTitle = Title.title(
        Component.text("TAGGER", NamedTextColor.RED, TextDecoration.BOLD),
        Component.text("Tag all of the goons!", NamedTextColor.GRAY)
    )
    private val goonTitle = Title.title(
        Component.text("GOON", NamedTextColor.GREEN, TextDecoration.BOLD),
        Component.text("Run away from the taggers!", NamedTextColor.GRAY)
    )

    override fun playerJoin(player: Player) {
        player.respawnPoint = spawnPos
        player.teleport(spawnPos)
    }

    override fun playerLeave(player: Player) {
        if (players.size == 1) {
            if (gameState != GameState.PLAYING) return
            if (goonsTeam.players.contains(players.first())) {
                victory(ParkourTagTeam.GOONS)
            } else {
                victory(ParkourTagTeam.TAGGERS)
            }
        }
        if (taggersTeam.players.isEmpty()) {
            if (gameState != GameState.PLAYING) return
            victory(ParkourTagTeam.GOONS)
        }
    }

    override fun gameStarted() {

        scoreboard?.updateLineContent("infoLine", Component.text("Rolling...", NamedTextColor.GRAY))

        var picked = players.random()
        (0..15).forEach {
            Manager.scheduler.buildTask {
                picked.isGlowing = false
                picked = players.random()
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

                    setupGame()
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
                sendMiniMessage(" <red>☠</red> <dark_gray>|</dark_gray> <gray><white>${attacker.username}</white> tagged <red>${target.username}</red>")
                attacker.playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_PLING, Sound.Source.AMBIENT, 1f, 1f))

                showParticle(
                    Particle.particle(
                        type = ParticleType.LARGE_SMOKE,
                        count = 15,
                        data = OffsetAndSpeed(1f, 1f, 1f, 0.04f),
                    ),
                    target.position.asVec()
                )

                kill(target, null)
            }
        }

        listenOnly<PlayerTickEvent> {

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

            object : MinestomRunnable() {
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
            }.repeat(Duration.ofSeconds(1)).schedule()

        }
    }

    override fun playerDied(player: Player, killer: Entity?) {
        goonsTeam.remove(player)

        scoreboard!!.updateLineContent(
            "goons_left", Component.text("Goons: ", NamedTextColor.GREEN)
                .append(Component.text(goonsTeam.players.size, NamedTextColor.RED))
        )

        if (goonsTeam.players.isEmpty()) {
            victory(ParkourTagTeam.TAGGERS)
        }

        player.gameMode = GameMode.SPECTATOR
        player.isInvisible = true
        player.isGlowing = false
    }

    override fun respawn(player: Player) {

    }

    private fun setupGame() {

        object : MinestomRunnable() {
            override fun run() {
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
        }.delay(Duration.ofSeconds(3)).schedule()

    }

    fun startTimer() {
        timerTask = object : MinestomRunnable() {
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
                        victory(ParkourTagTeam.GOONS)
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
                        .append(Component.text("Time left: ", NamedTextColor.YELLOW))
                        .append(Component.text(timeLeft.parsed(), NamedTextColor.GOLD))
                        .build()
                )
                timeLeft--
            }
        }.repeat(Duration.ofSeconds(1)).schedule()
    }

    fun victory(winningTeam: ParkourTagTeam) {
        gameState = GameState.ENDING
        timerTask?.cancel()

        scoreboard?.removeLine("tagger")
        scoreboard?.removeLine("goons_left")
        scoreboard?.removeLine("time_left")

        val victoryTitle = Title.title(
            Component.text("VICTORY!", NamedTextColor.GOLD, TextDecoration.BOLD),
            Component.text(EndGameQuotes.victory.random(), NamedTextColor.GRAY),
            Title.Times.of(Duration.ZERO, Duration.ofSeconds(3), Duration.ofSeconds(3))
        )
        val defeatTitle = Title.title(
            Component.text("DEFEAT!", NamedTextColor.RED, TextDecoration.BOLD),
            Component.text(EndGameQuotes.defeat.random(), NamedTextColor.GRAY),
            Title.Times.of(Duration.ZERO, Duration.ofSeconds(3), Duration.ofSeconds(3))
        )

        if (winningTeam == ParkourTagTeam.TAGGERS) {
            players.forEach {
                if (taggersTeam.players.contains(it)) {
                    it.showTitle(victoryTitle)
                } else {
                    it.showTitle(defeatTitle)
                }
            }
        } else {
            players.forEach {
                if (taggersTeam.players.contains(it)) {
                    it.showTitle(defeatTitle)
                } else {
                    it.showTitle(victoryTitle)
                }
            }
        }

        Manager.scheduler.buildTask { destroy() }.delay(Duration.ofSeconds(5)).schedule()

    }

    override fun gameDestroyed() {
    }

    override fun instanceCreate(): Instance {
        val randomMap = File("./maps/parkourtag/").listFiles().random()

        val schematic = Schematics.file(randomMap, Schemas.SPONGE)
        val data = Blocky.builder().compression(true).build().read(schematic)

        val instance = Manager.instance.createInstanceContainer(
            Manager.dimensionType.getDimension(NamespaceID.from("fullbright"))!!
        )
        val schematicChunkLoader = SchematicChunkLoader(instance, data)
        instance.chunkLoader = schematicChunkLoader
        spawnPos = Pos(data.width / 2.0, 10.0, data.length / 2.0)

        mapConfig = ParkourTagExtension.config.mapSpawnPositions[randomMap.nameWithoutExtension] ?: MapConfig()

        return instance
    }

}