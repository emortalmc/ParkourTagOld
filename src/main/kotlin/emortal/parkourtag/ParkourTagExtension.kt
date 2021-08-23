package emortal.parkourtag

import emortal.immortal.game.GameManager
import emortal.immortal.game.GameOptions
import emortal.immortal.game.GameTypeInfo
import emortal.parkourtag.game.FlatWorldGenerator
import emortal.parkourtag.game.ParkourTagGame
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.extensions.Extension
import world.cepi.kstom.Manager

class ParkourTagExtension : Extension() {

    override fun initialize() {
        val flatWorldInstance = Manager.instance.createInstanceContainer()
        flatWorldInstance.chunkGenerator = FlatWorldGenerator

        GameManager.registerGame<ParkourTagGame>(
            GameTypeInfo(
                eventNode,
                "parkourtag",
                Component.text("ParkourTag", NamedTextColor.GREEN, TextDecoration.BOLD),
                true,
                GameOptions(
                    { flatWorldInstance },
                    8,
                    2,
                    false,
                    true,
                    true,
                    true
                )
            )
        )

        logger.info("[LobbyExtension] Initialized!")
    }

    override fun terminate() {
        logger.info("[LobbyExtension] Terminated!")
    }

}