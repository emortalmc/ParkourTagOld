package dev.emortal.parkourtag.game

import emortal.immortal.game.Game
import emortal.immortal.game.GameOptions
import dev.emortal.parkourtag.Locations
import dev.emortal.parkourtag.Utils
import dev.emortal.parkourtag.game.ParkourTagPlayer.cleanup
import dev.emortal.parkourtag.game.ParkourTagPlayer.isTagged
import dev.emortal.parkourtag.game.ParkourTagPlayer.isTagger
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.event.player.PlayerStartFlyingEvent
import net.minestom.server.scoreboard.Sidebar
import net.minestom.server.sound.SoundEvent
import net.minestom.server.timer.Task
import org.yaml.snakeyaml.Yaml
import world.cepi.kstom.event.listenOnly
import world.cepi.kstom.util.MinestomRunnable
import java.io.File
import java.io.FileInputStream
import java.time.Duration

class ParkourTagGame(gameOptions: GameOptions) : Game(gameOptions) {
    private val goons = mutableListOf<Player>()
    private val tagged = mutableListOf<Player>()
    private val taggers = mutableListOf<Player>()
    private val cooldownMap = mutableMapOf<Player, Long>()
    private val path = MinecraftServer.getExtensionManager().extensionFolder.path + "/ParkourTag"
    private val locations = File("$path/locations.yaml")
    private val yaml = Yaml()
    private val locs: Locations = yaml.load(FileInputStream(locations))
    val parsedLocs: List<Pos> = Utils.parseLocations(locs)

    private var picked: Player? = null
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

    }

    override fun start() {

        object : MinestomRunnable() {
            var loop = 12

            override fun run() {
                if (loop < 1) {
                    picked = players.random()
                    playerAudience.playSound(Sound.sound(SoundEvent.ENTITY_ENDER_DRAGON_GROWL, Sound.Source.HOSTILE, 1f, 0.5f))
                    playerAudience.showTitle(
                        Title.title(
                            Component.text(Utils.componentToString(picked!!.name), NamedTextColor.RED, TextDecoration.BOLD),
                            Component.empty(),
                            Title.Times.of(
                                Duration.ZERO, Duration.ofSeconds(3), Duration.ofMillis(250)
                            )
                        )
                    )

                    setupGame()

                    cancel()
                    return
                }

                playerAudience.playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_SNARE, Sound.Source.BLOCK, 1f, 1f))
                playerAudience.showTitle(
                    Title.title(
                        Component.text(Utils.componentToString(players.random().name), NamedTextColor.GREEN, TextDecoration.BOLD),
                        Component.empty(),
                        Title.Times.of(
                            Duration.ZERO, Duration.ofSeconds(2), Duration.ofMillis(250)
                        )
                    )
                )

                loop--
            }
        }.repeat(Duration.ofMillis(300)).schedule()
    }

    override fun registerEvents() {
        eventNode.listenOnly<EntityAttackEvent> {
            if(target !is Player || entity !is Player) return@listenOnly
            val target = target as Player
            val attacker = entity as Player
            if(attacker.isTagger && !target.isTagger && !target.isTagged){
                players.forEach {
                    it.sendMessage(
                        Component.text("☠ | ", NamedTextColor.WHITE)
                            .append(Component.text(Utils.componentToString(attacker.name), NamedTextColor.RED, TextDecoration.BOLD))
                            .append(Component.text(" tagged ", NamedTextColor.GRAY))
                            .append(Component.text(Utils.componentToString(target.name), NamedTextColor.GREEN, TextDecoration.BOLD))
                            .append(Component.text("!", NamedTextColor.GRAY))
                    )
                }
                attacker.playSound(Sound.sound(SoundEvent.ENTITY_EXPERIENCE_ORB_PICKUP, Sound.Source.AMBIENT, 1f, 1.2f))
                tag(target)
            }
        }

        eventNode.listenOnly<PlayerStartFlyingEvent> {
            player.isFlying = false

            if(!player.isTagger) return@listenOnly
            if(cooldownMap[player]!! > System.currentTimeMillis()){
                val timeLeft = cooldownMap[player]!! - System.currentTimeMillis()
                player.sendActionBar(
                    Component.text("Double jump is on cooldown for ", NamedTextColor.GRAY)
                        .append(
                            Component.text(java.util.concurrent.TimeUnit.MILLISECONDS.toSeconds(timeLeft) + 1,
                                NamedTextColor.RED,
                                TextDecoration.BOLD
                            )
                        )
                        .append(
                            Component.text(" seconds",
                                NamedTextColor.GRAY
                            )
                        )
                )
                return@listenOnly
            }
            cooldownMap[player] = System.currentTimeMillis() + 3000
            val vec: Vec = player.position.direction().mul(14.0).withY(10.0)
            player.velocity = vec
        }
    }

    private fun setupGame(){

        object : MinestomRunnable(){
            override fun run() {
                addTagger(picked!!)
                players.forEach { player ->
                    if(!player.isTagger){
                        goons.add(player)
                    }
                }

                taggers.forEach {
                    it.showTitle(
                        taggerTitle
                    )
                    cooldownMap[it] = System.currentTimeMillis()
                    it.teleport(parsedLocs[0])
                    it.isGlowing = true
                }

                goons.forEach {
                    it.showTitle(
                        goonTitle
                    )
                    it.teleport(parsedLocs[1])
                }

                scoreboard!!.createLine(Sidebar.ScoreboardLine(
                    "time_left",
                    Component.text("Time left: ${Utils.secondsToParsed(90)}"),
                    4
                ))
                scoreboard!!.createLine(Sidebar.ScoreboardLine(
                    "goons_left",
                    Component.text("Goons: ", NamedTextColor.GREEN)
                        .append(Component.text(goons.size, NamedTextColor.RED)),
                    2
                ))

                scoreboard!!.createLine(Sidebar.ScoreboardLine(
                    "tagger",
                    Component.text("Tagger: ", NamedTextColor.GREEN)
                        .append(Component.text(Utils.componentToString(picked!!.name), NamedTextColor.RED)),
                    0
                ))

                startTimer()
            }
        }.delay(Duration.ofSeconds(3)).schedule()

    }

    private fun addTagger(player: Player){
        taggers.add(player)
        player.isTagger = true
    }
    private fun tag(player: Player){
        goons.remove(player)

        scoreboard!!.updateLineContent("goons_left", Component.text("Goons: ", NamedTextColor.GREEN)
            .append(Component.text(goons.size, NamedTextColor.RED)))

        if(goons.size == 0){
            winTaggers()
            object : MinestomRunnable() {
                override fun run() {
                    destroy()
                }
            }.delay(Duration.ofSeconds(8)).schedule()
        }

        tagged.add(player)
        player.gameMode = GameMode.SPECTATOR
        player.isTagged = true
        player.isInvisible = true
    }


    fun startTimer(){
        timer = object : MinestomRunnable(){
            var timeLeft = 90


            override fun run() {
                when(timeLeft) {
                    60 ->{
                        taggers.forEach {
                            it.isAllowFlying = true
                            it.sendMessage(Component.text("Double jump enabled!", NamedTextColor.GRAY))
                        }
                    }
                    30 ->{
                        taggers.forEach {
                            it.isGlowing = false
                            it.sendMessage(Component.text("Goons are now glowing!", NamedTextColor.GRAY))
                        }
                        goons.forEach {
                            it.isGlowing = true
                        }
                        players.forEach {
                            it.sendMessage(Component.text("Taggers are hidden!", NamedTextColor.GRAY))
                        }
                    }
                    0 -> {
                        winGoons()
                        object : MinestomRunnable() {
                            override fun run() {
                                destroy()
                            }
                        }.delay(Duration.ofSeconds(8)).schedule()
                        cancel()
                        return
                    }
                }
                scoreboard!!.updateLineContent(
                    "time_left",
                    Component.text("Time left: ${Utils.secondsToParsed(timeLeft)}")
                )
                timeLeft--
            }
        }.repeat(Duration.ofSeconds(1)).schedule()
    }

    fun winGoons(){
        players.forEach {
            it.showTitle(
                goonVictoryTitle
            )
        }

    }
    fun winTaggers(){
        timer!!.cancel()
        players.forEach {
            it.showTitle(
                taggerVictoryTitle
            )
        }

    }

    override fun postDestroy() {

        instance.players.forEach {
            it.cleanup()
            it.isInvisible = false
            it.teleport(parsedLocs[2])
        }
    }
}