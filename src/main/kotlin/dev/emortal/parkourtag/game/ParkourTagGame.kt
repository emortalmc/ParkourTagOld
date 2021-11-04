package dev.emortal.parkourtag.game

import dev.emortal.immortal.game.EndGameQuotes
import dev.emortal.immortal.game.GameOptions
import dev.emortal.immortal.game.PvpGame
import dev.emortal.immortal.game.Team
import dev.emortal.parkourtag.ParkourTagExtension
import dev.emortal.parkourtag.map.SchematicChunkLoader
import dev.emortal.parkourtag.utils.parsed
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
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


class ParkourTagGame(gameOptions: GameOptions) : PvpGame(gameOptions) {
    private val goonsTeam = registerTeam(Team("goons", NamedTextColor.WHITE))
    private val taggersTeam = registerTeam(Team("taggers", NamedTextColor.RED))

    lateinit var spawnPos: Pos

    private var timer: Task? = null

    private val taggerTitle = Title.title(
        Component.text("TAGGER", NamedTextColor.RED, TextDecoration.BOLD),
        Component.text("Tag all of the goons!", NamedTextColor.GREEN)
    )
    private val goonTitle = Title.title(
        Component.text("GOON", NamedTextColor.GREEN, TextDecoration.BOLD),
        Component.text("Run away from the taggers!", NamedTextColor.RED)
    )

    override fun playerJoin(player: Player) {
        player.teleport(spawnPos)
        player.respawnPoint = spawnPos
    }

    override fun playerLeave(player: Player) {
        if (players.size == 1) {
            if (goonsTeam.players.contains(players.first())) {
                victory(ParkourTagTeam.GOONS)
            } else {
                victory(ParkourTagTeam.TAGGERS)
            }
        }
    }

    override fun gameStarted() {

        scoreboard?.updateLineContent("InfoLine", Component.text("Rolling...", NamedTextColor.GRAY))

        object : MinestomRunnable() {
            var loop = 15

            override fun run() {
                val picked = players.random()

                playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_SNARE, Sound.Source.BLOCK, 1f, 2f))
                showTitle(
                    Title.title(
                        Component.text(picked.username, NamedTextColor.GREEN, TextDecoration.BOLD),
                        Component.empty(),
                        Title.Times.of(
                            Duration.ZERO, Duration.ofSeconds(2), Duration.ZERO
                        )
                    )
                )

                if (loop < 1) {
                    playSound(
                        Sound.sound(
                            SoundEvent.ENTITY_ENDER_DRAGON_GROWL,
                            Sound.Source.HOSTILE,
                            1f,
                            1f
                        )
                    )

                    taggersTeam.add(picked)
                    scoreboard?.updateLineContent("InfoLine", Component.empty())


                    setupGame()
                    cancel()
                    return
                }

                loop--
            }
        }.repeat(Duration.ofMillis(100)).schedule()
    }

    override fun registerEvents() {
        eventNode.listenOnly<EntityAttackEvent> {
            if (target !is Player || entity !is Player) return@listenOnly

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

        eventNode.listenOnly<PlayerTickEvent> {
            if (player.instance!!.getBlock(player.position).compare(Block.RAIL)) {
                player.addEffect(Potion(PotionEffect.LEVITATION, 15, 3))
            }
        }

        eventNode.listenOnly<PlayerStartFlyingEvent> {
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
            object : MinestomRunnable() {
                override fun run() {
                    destroy()
                }
            }.delay(Duration.ofSeconds(8)).schedule()
        }

        player.gameMode = GameMode.SPECTATOR
        player.isInvisible = true
    }

    override fun respawn(player: Player) {

    }

    private fun setupGame() {

        object : MinestomRunnable() {
            override fun run() {
                goonsTeam.players.addAll(players.filter { !taggersTeam.players.contains(it) })

                goonsTeam.showTitle(goonTitle)

                goonsTeam.players.forEach {
                    it.teleport(ParkourTagExtension.config.goonSpawnPos)
                }

                taggersTeam.players.forEach {
                    it.showTitle(taggerTitle)
                    it.teleport(ParkourTagExtension.config.taggerSpawnPos)
                    it.isGlowing = true
                }

                scoreboard?.createLine(
                    Sidebar.ScoreboardLine(
                        "time_left",
                        Component.text("Time left: ${90.parsed()}"),
                        4
                    )
                )
                scoreboard?.createLine(
                    Sidebar.ScoreboardLine(
                        "goons_left",
                        Component.text("Goons: ", NamedTextColor.GREEN)
                            .append(Component.text(goonsTeam.players.size, NamedTextColor.RED)),
                        2
                    )
                )

                scoreboard?.createLine(
                    Sidebar.ScoreboardLine(
                        "tagger",
                        "<green>Taggers: <dark_green>${taggersTeam.players.joinToString { it.username }}".asMini(),
                        0
                    )
                )

                startTimer()
            }
        }.delay(Duration.ofSeconds(3)).schedule()

    }

    fun startTimer() {
        timer = object : MinestomRunnable() {
            var timeLeft = 90

            override fun run() {
                when (timeLeft) {
                    10 -> {
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
                    30 -> {
                        taggersTeam.players.forEach {
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
                    0 -> {
                        victory(ParkourTagTeam.GOONS)
                        Manager.scheduler.buildTask { destroy() }.delay(Duration.ofSeconds(5)).schedule()
                        return
                    }
                }
                scoreboard!!.updateLineContent(
                    "time_left",
                    Component.text("Time left: ${timeLeft.parsed()}")
                )
                timeLeft--
            }
        }.repeat(Duration.ofSeconds(1)).schedule()
    }

    fun victory(winningTeam: ParkourTagTeam) {
        timer?.cancel()

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
            taggersTeam.showTitle(victoryTitle)
            goonsTeam.showTitle(defeatTitle)
        } else {
            goonsTeam.showTitle(victoryTitle)
            taggersTeam.showTitle(defeatTitle)
        }

    }

    override fun gameDestroyed() {
        instance.players.forEach {
            it.isInvisible = false
            it.isGlowing = false
            it.isAllowFlying = false
            it.isFlying = false
        }
    }

    override fun instanceCreate(): Instance {
        val schematic = Schematics.file(File("./map.schem"), Schemas.SPONGE)
        val data = Blocky.builder().compression(true).build().read(schematic)

        val instance = Manager.instance.createInstanceContainer(
            Manager.dimensionType.getDimension(NamespaceID.from("fullbright"))!!
        )
        instance.chunkLoader = SchematicChunkLoader(data)

        spawnPos = Pos(-data.offset[0].toDouble(), -data.offset[1].toDouble() + 3, -data.offset[2].toDouble())

        return instance
    }

}