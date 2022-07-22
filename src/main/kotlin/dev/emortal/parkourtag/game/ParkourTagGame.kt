package dev.emortal.parkourtag.game

import dev.emortal.immortal.config.GameOptions
import dev.emortal.immortal.game.GameState
import dev.emortal.immortal.game.PvpGame
import dev.emortal.immortal.game.Team
import dev.emortal.immortal.util.*
import dev.emortal.parkourtag.MapConfig
import dev.emortal.parkourtag.ParkourTagExtension
import dev.emortal.parkourtag.utils.showFirework
import dev.emortal.tnt.TNTLoader
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.sound.Sound.Emitter
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.minestom.server.color.Color
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.event.player.PlayerStartFlyingEvent
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
import net.minestom.server.tag.Tag
import net.minestom.server.timer.TaskSchedule
import net.minestom.server.utils.time.TimeUnit
import org.tinylog.kotlin.Logger
import world.cepi.kstom.Manager
import world.cepi.kstom.adventure.asMini
import world.cepi.kstom.adventure.sendMiniMessage
import world.cepi.kstom.event.listenOnly
import world.cepi.kstom.util.playSound
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

    override var spawnPosition = Pos(0.5, 65.0, 0.5)

    lateinit var mapConfig: MapConfig

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

        object : MinestomRunnable(taskGroup = taskGroup, repeat = TaskSchedule.nextTick(), iterations = 17L) {
            var picked = players.random()
            val offset = ThreadLocalRandom.current().nextInt(players.size)

            override fun run() {
                if (gameState == GameState.ENDING || players.size == 0) {
                    cancel()
                    return
                }

                delaySchedule = TaskSchedule.duration((currentIteration / 1.2).toLong().coerceAtLeast(1L), TimeUnit.SERVER_TICK)

                picked.isGlowing = false
                picked = players.elementAt(((currentIteration + offset) % players.size).toInt())
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
                object : MinestomRunnable(taskGroup = taskGroup, repeat = TaskSchedule.tick(2), iterations = 10L*3L) {
                    override fun run() {
                        showTitle(
                            Title.title(
                                "<rainbow:${currentIteration}>${picked.username}".asMini(),
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

        val launchCooldownTag = Tag.Boolean("launchCooldown")
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
                    if (player.hasTag(launchCooldownTag)) return@listenOnly

                    player.playSound(Sound.sound(SoundEvent.ENTITY_BAT_TAKEOFF, Sound.Source.MASTER, 0.5f, 0.8f))
                    player.velocity = Vec(0.0, 22.5, 0.0)
                    player.setTag(launchCooldownTag, true)
                    player.scheduler().buildTask { player.removeTag(launchCooldownTag) }.delay(Duration.ofMillis(1200)).schedule()
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

            object : MinestomRunnable(taskGroup = taskGroup, repeat = Duration.ofSeconds(1), iterations = 3) {
                override fun run() {
                    player.sendActionBar("<gray>Double jump is on cooldown for <bold><red>${iterations - currentIteration}s".asMini())
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

        val rand = ThreadLocalRandom.current()
        goonsTeam.players.forEach {
            it.showTitle(goonTitle)
            it.teleport(mapConfig.goonSpawnPosition.add(rand.nextDouble(-2.0, 2.0), 0.0, rand.nextDouble(-2.0, 2.0)))
            it.addEffect(Potion(PotionEffect.INVISIBILITY, 1, 7*20))
        }

        val holdingEntity = Entity(EntityType.ARMOR_STAND)
        holdingEntity.setNoGravity(true)
        holdingEntity.isInvisible = true

        holdingEntity.setInstance(instance, mapConfig.taggerSpawnPosition).thenRun {
            taggersTeam.players.forEach {
                holdingEntity.addPassenger(it)
            }
        }

        taggersTeam.players.forEach {
            it.showTitle(taggerTitle)
            it.addEffect(Potion(PotionEffect.BLINDNESS, 1, 8*20))
            it.teleport(mapConfig.taggerSpawnPosition)
            it.isGlowing = true
        }

        scoreboard?.updateLineScore("infoLine", 3)
        scoreboard?.createLine(
            Sidebar.ScoreboardLine(
                "time_left",
                Component.empty(),
                5
            )
        )
        scoreboard?.createLine(
            Sidebar.ScoreboardLine(
                "time_left2",
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
                it.askSynchronization() // Seeker is sometimes out of sync once dismounting
            }
        }.delay(Duration.ofSeconds(7)).schedule()

        startTimer()
    }

    private fun startTimer() {
        val playerCount = players.size
        val glowingTime = 15L + ((playerCount * 15L) / 8L) // 30 seconds with 8 players, 18 with 2
        val doubleJumpTime = glowingTime / 2L // 15 seconds with 8 players, 9 with 2
        val playTime = 240L / (12L - playerCount) // 60 seconds with 8 players, 24 with 2

        object : MinestomRunnable(taskGroup = taskGroup, repeat = TaskSchedule.duration(1, TimeUnit.SECOND), delay = TaskSchedule.nextTick(), iterations = 90L) {
            override fun run() {
                val currentIter = currentIteration
                val timeLeft = (iterations - currentIteration) - 1

                if (timeLeft > glowingTime) {
                    scoreboard?.updateLineContent(
                        "time_left2",
                        Component.text()
                            .append(Component.text("Glowing: ", TextColor.color(59, 128, 59)))
                            .append(Component.text((timeLeft - glowingTime).parsed(), NamedTextColor.GREEN))
                            .build()
                    )
                } else if (timeLeft > doubleJumpTime) {
                    scoreboard?.updateLineContent(
                        "time_left2",
                        Component.text()
                            .append(Component.text("Double jump: ", TextColor.color(59, 128, 59)))
                            .append(Component.text((timeLeft - doubleJumpTime).parsed(), NamedTextColor.GREEN))
                            .build()
                    )
                }

                when {
                    timeLeft == doubleJumpTime -> {
                        taggersTeam.players.forEach {
                            it.isAllowFlying = true
                        }

                        scoreboard?.removeLine("time_left2")

                        playSound(Sound.sound(SoundEvent.ENTITY_PLAYER_LEVELUP, Sound.Source.MASTER, 1f, 0.8f))
                        showTitle(
                            Title.title(
                                Component.empty(),
                                Component.text("Double jump has been enabled!", NamedTextColor.GRAY),
                                Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(2))
                            )
                        )
                    }
                    timeLeft == glowingTime -> {
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
                    timeLeft == (doubleJumpTime + 10L) || (timeLeft <= (doubleJumpTime + 5) && timeLeft > doubleJumpTime) -> {
                        playSound(Sound.sound(SoundEvent.BLOCK_WOODEN_BUTTON_CLICK_ON, Sound.Source.MASTER, 1f, 2f))
                        showTitle(
                            Title.title(
                                Component.empty(),
                                Component.text("Double jump will be enabled in ${timeLeft - doubleJumpTime} seconds!", NamedTextColor.GRAY),
                                Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(2))
                            )
                        )
                    }
                    timeLeft == (glowingTime + 10L) || (timeLeft <= (glowingTime + 5) && timeLeft > glowingTime) -> {
                        playSound(Sound.sound(SoundEvent.BLOCK_WOODEN_BUTTON_CLICK_ON, Sound.Source.MASTER, 1f, 2f))
                        showTitle(
                            Title.title(
                                Component.empty(),
                                Component.text("Hiders will glow in ${timeLeft - glowingTime} seconds!", NamedTextColor.GRAY),
                                Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(2))
                            )
                        )
                    }
                    timeLeft == 0L -> {
                        //Logger.warn("Ran out of time - iter: ${currentIteration}")`
                        victory(goonsTeam)
                        return
                    }
                    timeLeft < 10L -> {
                        playSound(Sound.sound(SoundEvent.BLOCK_WOODEN_BUTTON_CLICK_ON, Sound.Source.AMBIENT, 1f, 1f))
                        showTitle(
                            Title.title(
                                Component.empty(),
                                Component.text(timeLeft, NamedTextColor.GOLD),
                                Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(2))
                            )
                        )
                    }
                    else -> {}
                }
                scoreboard!!.updateLineContent(
                    "time_left",
                    Component.text()
                        .append(Component.text("Time left: ", TextColor.color(59, 128, 59)))
                        .append(Component.text(timeLeft.parsed(), NamedTextColor.GREEN))
                        .build()
                )
            }
        }
    }

    override fun gameWon(winningPlayers: Collection<Player>) {
        taskGroup.cancel()

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
        val randomMap = Path.of("./maps/parkourtag/" + Files.list(Path.of("./maps/parkourtag/"))
            .map { it.nameWithoutExtension }
            .collect(Collectors.toSet())
            .random() + ".tnt")

        //val schematic = SpongeSchematic()
        //schematic.read(randomMap.inputStream())

//        val schematic = Schematics.file(randomMap, Schemas.SPONGE)
//        val data = Blocky.builder().compression(true).build().read(schematic)

        val instance = Manager.instance.createInstanceContainer(
            //Manager.dimensionType.getDimension(NamespaceID.from("fullbright"))!!
        )
//        instance.chunkLoader = SchematicChunkLoader.builder()
//            .addSchematic(schematic)
//            .build()
        instance.chunkLoader = TNTLoader(instance, randomMap)

        mapConfig = ParkourTagExtension.config.mapSpawnPositions[randomMap.nameWithoutExtension] ?: MapConfig()

        instance.time = 0
        instance.timeRate = 0
        instance.timeUpdate = null

        return instance
    }

}