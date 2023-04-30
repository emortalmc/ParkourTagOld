package dev.emortal.parkourtag.game

import dev.emortal.immortal.game.GameManager
import dev.emortal.immortal.game.GameState
import dev.emortal.immortal.game.PvpGame
import dev.emortal.immortal.game.Team
import dev.emortal.immortal.util.*
import dev.emortal.parkourtag.MapConfig
import dev.emortal.parkourtag.ParkourTagMain
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.sound.Sound.Emitter
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.title.Title
import net.minestom.server.MinecraftServer
import net.minestom.server.color.Color
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.event.player.PlayerStartFlyingEvent
import net.minestom.server.event.player.PlayerTickEvent
import net.minestom.server.event.trait.InstanceEvent
import net.minestom.server.instance.AnvilLoader
import net.minestom.server.instance.Chunk
import net.minestom.server.instance.Instance
import net.minestom.server.instance.SharedInstance
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
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Collectors
import kotlin.io.path.nameWithoutExtension
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val LOGGER = LoggerFactory.getLogger(ParkourTagGame::class.java)

class ParkourTagGame : PvpGame() {


    private val goonsTeam =
        Team(
            "Goons",
            NamedTextColor.WHITE,
            nameTagVisibility = TeamsPacket.NameTagVisibility.NEVER,
            canSeeInvisiblePlayers = true
        )
    private val taggersTeam =
        Team(
            "Taggers",
            NamedTextColor.RED,
            nameTagVisibility = TeamsPacket.NameTagVisibility.NEVER
        )


    override val maxPlayers: Int = 8
    override val minPlayers: Int = 2
    override val countdownSeconds: Int = 20
    override val canJoinDuringGame: Boolean = false
    override val showScoreboard: Boolean = true
    override val showsJoinLeaveMessages: Boolean = true
    override val allowsSpectators: Boolean = true

    override val gameName = "parkourtag"
    override val gameComponent = Component.text("ParkourTag", NamedTextColor.GREEN, TextDecoration.BOLD)


    private val miniMessage = MiniMessage.miniMessage()

    private val booped = Tag.Boolean("booped")
    private val launchCooldownTag = Tag.Boolean("launchCooldown")

    override fun getSpawnPosition(player: Player, spectator: Boolean): Pos = Pos(0.5, 65.0, 0.5)

    var riggedPlayer: Player? = null
    val canHitPlayers = AtomicBoolean(false)
    val countDownFinished = AtomicBoolean(false)

    lateinit var mapConfig: MapConfig

    private val taggerTitle = Title.title(
        Component.text("TAGGER", NamedTextColor.RED, TextDecoration.BOLD),
        Component.text("Tag all of the goons!", NamedTextColor.GRAY)
    )
    private val goonTitle = Title.title(
        Component.text("GOON", NamedTextColor.GREEN, TextDecoration.BOLD),
        Component.text("Run away from the taggers!", NamedTextColor.GRAY)
    )

    override fun playerJoin(player: Player) {
        player.sendMessage(
            Component.text()
                .append(Component.text("${centerSpaces("Welcome to Parkour Tag")}Welcome to ", NamedTextColor.GRAY))
                .append(miniMessage.deserialize("<green><bold>Parkour Tag"))
                .append(
                    miniMessage.deserialize("\n\n" +
                        "<yellow>Parkour Tag is a simple game of <color:#ffcc55>hide and seek</color>." +
                        "\nAt the start of the game you are assigned <red><bold>Tagger</bold></red> or <green><bold>Goon</bold></green>." +
                        "\nLeft click on players to tag them!"
                ))
                .armify()
        )
    }

    override fun playerLeave(player: Player) {

        if (gameState == GameState.PLAYING && countDownFinished.get()) {
            goonsTeam.remove(player)
            taggersTeam.remove(player)

            if (taggersTeam.players.isEmpty()) {
                victory(goonsTeam)
            }
            if (goonsTeam.players.isEmpty()) {
                victory(taggersTeam)
            }
            return
        }

        if (players.size <= 1) {
            if (players.isNotEmpty()) {
                victory(players.first())
            }
        }
    }

    override fun gameStarted() {

        scoreboard?.updateLineContent("infoLine", Component.text("Rolling...", NamedTextColor.GRAY))

        var picked = players.random()
        val offset = ThreadLocalRandom.current().nextInt(players.size)

        var nameIter = 0 // max 17
        var ticksUntil = 1
        instance!!.scheduler().submitTask {
            if (gameState == GameState.ENDING || players.isEmpty()) {
                return@submitTask TaskSchedule.stop()
            }

            ticksUntil--

            if (ticksUntil == 0) {
                if (nameIter == 17) {
                    val tagger = if (riggedPlayer == null) {
                        picked
                    } else {
                        picked.isGlowing = false
                        riggedPlayer!!.isGlowing = true
                        riggedPlayer!!
                    }

                    var i = 0

                    instance!!.scheduler().submitTask {
                        if (i > 30) {
                            return@submitTask TaskSchedule.stop()
                        }
                        showTitle(
                            Title.title(
                                miniMessage.deserialize("<rainbow:${i}>${tagger.username}"),
                                Component.text("is the tagger", NamedTextColor.GRAY),
                                Title.Times.times(
                                    Duration.ZERO, Duration.ofMillis(500), Duration.ofMillis(500)
                                )
                            )
                        )
                        i++

                        TaskSchedule.tick(2)
                    }

                    playSound(
                        Sound.sound(
                            SoundEvent.ENTITY_ENDER_DRAGON_GROWL,
                            Sound.Source.HOSTILE,
                            1f,
                            1f
                        )
                    )

                    taggersTeam.add(tagger)

                    scoreboard?.updateLineContent("infoLine", Component.empty())

                    instance!!.scheduler().buildTask {
                        setupGame()
                    }.delay(Duration.ofSeconds(3)).schedule()
                    return@submitTask TaskSchedule.stop()
                }

                ticksUntil = (nameIter / 1.2).toInt().coerceAtLeast(1)

                picked.isGlowing = false
                picked = players.elementAt(((nameIter + offset) % players.size))
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

                nameIter++
            }

            TaskSchedule.nextTick()
        }
    }

    override fun registerEvents(eventNode: EventNode<InstanceEvent>) {
        eventNode.addListener(EntityAttackEvent::class.java) { e ->
            if (e.target !is Player || e.entity !is Player || !canHitPlayers.get()) return@addListener

            val target = e.target as Player
            val attacker = e.entity as Player

            if (target.gameMode != GameMode.ADVENTURE || attacker.gameMode != GameMode.ADVENTURE) return@addListener

            // booping hehe
            if (!taggersTeam.players.contains(attacker) && taggersTeam.players.contains(target)) {
                if (attacker.hasTag(booped)) return@addListener
                attacker.setTag(booped, true)
                attacker.sendMessage(Component.text("You booped ${target.username}!", NamedTextColor.GREEN))
                attacker.playSound(Sound.sound(SoundEvent.ENTITY_DONKEY_CHEST, Sound.Source.MASTER, 1f, 2f), target.position)

                target.scheduler().buildTask {
                    attacker.removeTag(booped)

                    if (!attacker.isOnline) return@buildTask
                    target.sendMessage(Component.text("${attacker.username} booped you!", NamedTextColor.GREEN))
                    target.playSound(Sound.sound(SoundEvent.ENTITY_DONKEY_CHEST, Sound.Source.MASTER, 1f, 2f), attacker.position)
                }.delay(Duration.ofSeconds(3)).schedule()

                return@addListener
            }

            if (taggersTeam.players.contains(attacker) && !taggersTeam.players.contains(target)) {
                val minDistance = target.position.add(0.0, 1.5, 0.0).distanceSquared(attacker.position.add(0.0, 1.5, 0.0))
                    .coerceAtMost(target.position.distanceSquared(attacker.position.add(0.0, 1.5, 0.0)))
                if (minDistance > 4.5*4.5) return@addListener

                kill(target, attacker)
            }
        }


        eventNode.addListener(PlayerTickEvent::class.java) { e ->
            val player = e.player

            if (taggersTeam.players.contains(player)) {
                goonsTeam.players.forEach { goon ->
                    val distance = goon.position.distanceSquared(player.position)
                    if (distance > 25*25) return@forEach

                    if (player.aliveTicks % (sqrt(distance) / 2).roundToInt().coerceAtLeast(2) == 0L)
                        goon.playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_BASEDRUM, Sound.Source.MASTER, 1.5f, 1f), player.position)
                }
            }


            if (player.gameMode == GameMode.ADVENTURE) {
                if ((player.instance!!.getBlock(player.position).compare(Block.RAIL) &&
                    player.instance!!.getBlock(player.position.add(0.0, 1.0, 0.0))
                        .compare(Block.STRUCTURE_VOID))
                    || player.instance!!.getBlock(player.position).compare(Block.AMETHYST_CLUSTER)
                ) {
                    if (player.hasTag(launchCooldownTag)) return@addListener

                    player.playSound(Sound.sound(SoundEvent.ENTITY_BAT_TAKEOFF, Sound.Source.MASTER, 0.5f, 0.8f))
                    player.velocity = Vec(0.0, 22.5, 0.0)
                    player.setTag(launchCooldownTag, true)
                    player.scheduler().buildTask { player.removeTag(launchCooldownTag) }.delay(Duration.ofMillis(1200)).schedule()
                }
            }
        }

        eventNode.addListener(PlayerStartFlyingEvent::class.java) { e ->
            val player = e.player
            if (!taggersTeam.players.contains(player)) return@addListener

            player.isFlying = false
            player.isAllowFlying = false
            player.velocity = player.position.direction().mul(18.0).withY(14.0)
            playSound(
                Sound.sound(
                    SoundEvent.ENTITY_GENERIC_EXPLODE,
                    Sound.Source.PLAYER,
                    1f,
                    1.5f
                ),
                player.position
            )

            var i = 0
            e.instance.scheduler().submitTask {
                if (i >= 3) {
                    player.isAllowFlying = true
                    player.sendActionBar(Component.empty())
                    player.playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_PLING, Sound.Source.MASTER, 0.7f, 0.8f), Sound.Emitter.self())
                    return@submitTask TaskSchedule.stop()
                }

                player.sendActionBar(miniMessage.deserialize("<gray>Double jump is on cooldown for <bold><red>${3 - i}s"))

                i++

                TaskSchedule.seconds(1)
            }

        }
    }

    override fun playerDied(player: Player, killer: Entity?) {
        goonsTeam.remove(player)
        taggersTeam.remove(player)

        if (taggersTeam.players.isEmpty()) {
            victory(goonsTeam)
            LOGGER.warn("Taggers died")
        }
        if (goonsTeam.players.isEmpty()) {
            victory(taggersTeam)
            LOGGER.warn("goons died")
        }

        scoreboard!!.updateLineContent(
            "goons_left", Component.text("Goons: ", NamedTextColor.GRAY)
                .append(Component.text(goonsTeam.players.size, NamedTextColor.RED))
        )

        if (killer != null && killer is Player) {
            sendMessage(miniMessage.deserialize(" <red>☠</red> <dark_gray>|</dark_gray> <gray><white>${killer.username}</white> tagged <red>${player.username}</red>"))
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

        instance?.let {
            holdingEntity.setInstance(it, mapConfig.taggerSpawnPosition).thenRun {
                taggersTeam.players.forEach {
                    holdingEntity.addPassenger(it)
                }
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

        taggersTeam.players.forEach {
            it.updateViewerRule { viewEnt -> viewEnt.entityId == holdingEntity.entityId }
        }

        countDownFinished.set(true)

        instance!!.scheduler().buildTask {

            taggersTeam.players.forEach {
                it.updateViewerRule { true }
            }

            val title = Title.title(Component.empty(), Component.text("Tagger has been released!", NamedTextColor.YELLOW), Title.Times.times(Duration.ZERO, Duration.ofMillis(500), Duration.ofMillis(200)))
            val sound = Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_PLING, Sound.Source.MASTER, 1f, 1f)

            canHitPlayers.set(true)

            holdingEntity.remove()
            taggersTeam.players.forEach {
                it.teleport(mapConfig.taggerSpawnPosition)
            }
            players.forEach {
                it.clearEffects()
                it.showTitle(title)
                it.playSound(sound, Emitter.self())
                it.askSynchronization() // Tagger is sometimes out of sync once dismounting
            }
        }.delay(Duration.ofSeconds(7)).schedule()

        startTimer()
    }

    private fun startTimer() {
        val playerCount = players.size
        val glowingTime = 15 + ((playerCount * 15) / 8) // 30 seconds with 8 players, 18 with 2
        val doubleJumpTime = glowingTime / 2 // 15 seconds with 8 players, 9 with 2
        val playTime = 240 / (12 - playerCount) // 60 seconds with 8 players, 24 with 2

        var i = 0
        instance!!.scheduler().submitTask {
            val timeLeft = (90 - i) - 1

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
                    taggersTeam.showTitle(
                        Title.title(
                            Component.empty(),
                            Component.text("You can now double jump!", NamedTextColor.GRAY),
                            Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(2))
                        )
                    )
                    goonsTeam.showTitle(
                        Title.title(
                            Component.empty(),
                            Component.text("The tagger can now double jump!", NamedTextColor.GRAY),
                            Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(2))
                        )
                    )
                }
                timeLeft == glowingTime -> {
                    goonsTeam.players.forEach {
                        it.isGlowing = true
                    }
                    playSound(Sound.sound(SoundEvent.ENTITY_PLAYER_LEVELUP, Sound.Source.MASTER, 1f, 0.8f))
                    taggersTeam.showTitle(
                        Title.title(
                            Component.empty(),
                            Component.text("Goons are now glowing!", NamedTextColor.GRAY),
                            Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(2))
                        )
                    )
                    goonsTeam.showTitle(
                        Title.title(
                            Component.empty(),
                            Component.text("You are now glowing!", NamedTextColor.GRAY),
                            Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(2))
                        )
                    )
                }
                timeLeft == (doubleJumpTime + 10) || (timeLeft <= (doubleJumpTime + 5) && timeLeft > doubleJumpTime) -> {
                    playSound(Sound.sound(SoundEvent.BLOCK_WOODEN_BUTTON_CLICK_ON, Sound.Source.MASTER, 1f, 2f))
                    showTitle(
                        Title.title(
                            Component.empty(),
                            Component.text("Double jump will be enabled in ${timeLeft - doubleJumpTime} seconds!", NamedTextColor.GRAY),
                            Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(2))
                        )
                    )
                }
                timeLeft == (glowingTime + 10) || (timeLeft <= (glowingTime + 5) && timeLeft > glowingTime) -> {
                    playSound(Sound.sound(SoundEvent.BLOCK_WOODEN_BUTTON_CLICK_ON, Sound.Source.MASTER, 1f, 2f))
                    taggersTeam.showTitle(
                        Title.title(
                            Component.empty(),
                            Component.text("Goons will glow in ${timeLeft - glowingTime} seconds!", NamedTextColor.GRAY),
                            Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(2))
                        )
                    )
                    goonsTeam.showTitle(
                        Title.title(
                            Component.empty(),
                            Component.text("You will glow in ${timeLeft - glowingTime} seconds!", NamedTextColor.GRAY),
                            Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ofSeconds(2))
                        )
                    )
                }
                timeLeft == 0 -> {
                    //Logger.warn("Ran out of time - iter: ${currentIteration}")`
                    victory(goonsTeam)
                    return@submitTask TaskSchedule.stop()
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

            i++
            TaskSchedule.seconds(1)
        }
    }

    override fun gameWon(winningPlayers: Collection<Player>) {
        canHitPlayers.set(false)

        val taggersWon = goonsTeam.players.isEmpty()

        val message = Component.text()
            .append(Component.text(" ${centerText("VICTORY", true)}", NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(Component.text("\n${centerText(if (taggersWon) "All of the Goons were found!" else "The taggers ran out of time!")}", NamedTextColor.GRAY))
            .append(Component.text("\n\n ${centerSpaces("Winning team: Tagger")}Winning team: ", NamedTextColor.GRAY))
            .also {
                if (taggersWon) {
                    it.append(Component.text("Taggers", NamedTextColor.RED, TextDecoration.BOLD))
                } else {
                    it.append(Component.text("Goons", NamedTextColor.GREEN, TextDecoration.BOLD))
                        .append(Component.text("\n ${centerSpaces("Survivors: ${goonsTeam.players.joinToString { it.username }}")}Survivors: ", NamedTextColor.GRAY))
                        .append(Component.text(goonsTeam.players.joinToString { it.username }, NamedTextColor.WHITE))
                }
            }
            .armify()

        sendMessage(message)
    }

    override fun gameEnded() {
    }

    override fun instanceCreate(): CompletableFuture<Instance> {
        val randomInstance = ParkourTagMain.instances.random()
        mapConfig = ParkourTagMain.config.mapSpawnPositions[randomInstance.getTag(ParkourTagMain.mapNameTag)] ?: MapConfig()

        val shared = MinecraftServer.getInstanceManager().createSharedInstance(randomInstance)
        shared.setTag(GameManager.doNotAutoUnloadChunkTag, true)

        shared.time = 0
        shared.timeRate = 0
        shared.timeUpdate = null

        return CompletableFuture.completedFuture(shared)
    }

}