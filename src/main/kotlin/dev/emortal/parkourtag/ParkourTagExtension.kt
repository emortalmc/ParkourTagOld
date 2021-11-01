package dev.emortal.parkourtag

import dev.emortal.immortal.game.GameManager
import dev.emortal.immortal.game.GameOptions
import dev.emortal.parkourtag.game.ParkourTagGame
import dev.emortal.parkourtag.utils.ConfigurationHelper
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.extensions.Extension
import java.nio.file.Path

class ParkourTagExtension : Extension() {

    companion object {
        lateinit var config: ParkourConfig
    }

    override fun initialize() {
        config = ConfigurationHelper.initConfigFile(Path.of("./parkour.json"), ParkourConfig())

        GameManager.registerGame<ParkourTagGame>(
            eventNode,
            "parkourtag",
            Component.text("ParkourTag", NamedTextColor.GREEN, TextDecoration.BOLD),
            true,
            GameOptions(
                maxPlayers = 8,
                minPlayers = 2,
                canJoinDuringGame = false,
                showScoreboard = true,
                showsJoinLeaveMessages = true,
            )
        )

        logger.info("[ParkourTag] Initialized!")
    }

    override fun terminate() {
        logger.info("[ParkourTag] Terminated!")
    }

}