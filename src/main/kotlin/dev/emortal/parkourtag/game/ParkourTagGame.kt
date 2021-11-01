package dev.emortal.parkourtag.game

import dev.emortal.immortal.game.GameOptions
import dev.emortal.immortal.game.PvpGame
import dev.emortal.immortal.util.SuperflatGenerator
import dev.emortal.parkourtag.ParkourTagExtension
import dev.emortal.parkourtag.utils.parsed
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.minestom.server.entity.Entity
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.event.player.PlayerStartFlyingEvent
import net.minestom.server.instance.Instance
import net.minestom.server.scoreboard.Sidebar
import net.minestom.server.sound.SoundEvent
import net.minestom.server.timer.Task
import world.cepi.kstom.Manager
import world.cepi.kstom.adventure.asMini
import world.cepi.kstom.adventure.sendMiniMessage
import world.cepi.kstom.event.listenOnly
import world.cepi.kstom.util.MinestomRunnable
import world.cepi.particle.Particle
import world.cepi.particle.ParticleType
import world.cepi.particle.data.OffsetAndSpeed
import world.cepi.particle.showParticle
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

class ParkourTagGame(gameOptions: GameOptions) : PvpGame(gameOptions) {
    private val goons: MutableSet<Player> = ConcurrentHashMap.newKeySet()
    private val taggers: MutableSet<Player> = ConcurrentHashMap.newKeySet()

    private var timer: Task? = null

    private val taggerTitle = Title.title(
        Component.text("TAGGER", NamedTextColor.RED, TextDecoration.BOLD),
        Component.text("Tag all of the goons!", NamedTextColor.GREEN)
    )

    private val goonTitle = Title.title(
        Component.text("GOON", NamedTextColor.GREEN, TextDecoration.BOLD),
        Component.text("Run away from the taggers!", NamedTextColor.RED)
    )

    private val taggerVictoryTitle = Title.title(
        Component.text("Taggers Win!", NamedTextColor.RED, TextDecoration.BOLD),
        Component.text("the taggers tagged all of the goons", NamedTextColor.GREEN)
    )

    private val goonVictoryTitle = Title.title(
        Component.text("Goons Win!", NamedTextColor.GREEN, TextDecoration.BOLD),
        Component.text("The taggers ran out of time", NamedTextColor.RED)
    )

    override fun playerJoin(player: Player) {
        if (player.instance != instance) player.setInstance(instance)

    }

    override fun playerLeave(player: Player) {
        taggers.remove(player)
        goons.remove(player)
    }

    override fun gameStarted() {

        object : MinestomRunnable() {
            var loop = 30

            override fun run() {
                val picked = players.random()

                playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_SNARE, Sound.Source.BLOCK, 1f, 1f))
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
                            0.5f
                        )
                    )

                    taggers.add(picked)
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

            if (taggers.contains(attacker) && !taggers.contains(target)) {
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

        eventNode.listenOnly<PlayerStartFlyingEvent> {
            if (!taggers.contains(player)) return@listenOnly

            player.isFlying = false
            player.isAllowFlying = false
            player.velocity = player.position.direction().mul(20.0).withY(15.0)
            player.viewersAsAudience.playSound(
                Sound.sound(
                    SoundEvent.ENTITY_GENERIC_EXPLODE,
                    Sound.Source.PLAYER,
                    1f,
                    1.5f
                )
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
        goons.remove(player)

        scoreboard!!.updateLineContent(
            "goons_left", Component.text("Goons: ", NamedTextColor.GREEN)
                .append(Component.text(goons.size, NamedTextColor.RED))
        )

        if (goons.size == 0) {
            winTaggers()
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
                goons.addAll(players.filter { !taggers.contains(it) })

                goons.forEach {
                    it.showTitle(goonTitle)
                    it.teleport(ParkourTagExtension.config.goonSpawnPos)
                }

                taggers.forEach {
                    it.showTitle(taggerTitle)
                    it.teleport(ParkourTagExtension.config.taggerSpawnPos)
                    it.isGlowing = true
                }

                scoreboard!!.createLine(
                    Sidebar.ScoreboardLine(
                        "time_left",
                        Component.text("Time left: ${90.parsed()}"),
                        4
                    )
                )
                scoreboard!!.createLine(
                    Sidebar.ScoreboardLine(
                        "goons_left",
                        Component.text("Goons: ", NamedTextColor.GREEN)
                            .append(Component.text(goons.size, NamedTextColor.RED)),
                        2
                    )
                )

                scoreboard!!.createLine(
                    Sidebar.ScoreboardLine(
                        "tagger",
                        "<green>Taggers: <dark_green>${taggers.joinToString { it.username }}".asMini(),
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
                    60 -> {
                        taggers.forEach {
                            it.isAllowFlying = true
                            it.sendMessage(Component.text("Double jump enabled!", NamedTextColor.GRAY))
                        }
                    }
                    30 -> {
                        goons.forEach {
                            it.isGlowing = true
                        }
                        players.forEach {
                            it.sendMessage(Component.text("Goons are now glowing!", NamedTextColor.GRAY))
                        }
                    }
                    0 -> {
                        winGoons()
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

    fun winGoons() {
        timer?.cancel()

        players.forEach {
            it.showTitle(goonVictoryTitle)
        }
    }

    fun winTaggers() {
        timer?.cancel()

        players.forEach {
            it.showTitle(taggerVictoryTitle)
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
        val instance = Manager.instance.createInstanceContainer()
        instance.chunkGenerator = SuperflatGenerator

        return instance
    }

}