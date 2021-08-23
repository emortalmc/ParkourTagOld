package emortal.parkourtag.game

import emortal.immortal.game.Game
import emortal.immortal.game.GameOptions
import net.minestom.server.entity.Player
import net.minestom.server.event.player.PlayerChatEvent
import world.cepi.kstom.event.listenOnly

class ParkourTagGame(gameOptions: GameOptions) : Game(gameOptions) {

    // Possible seeker and goon lists

    override fun playerJoin(player: Player) {
        // Logic for picking seeker or goon for player and adding to respective list
    }

    override fun playerLeave(player: Player) {
        // Removing from seeker and goon list
    }

    override fun start() {
        // e.g. Unfreeze players / remove cages
    }

    override fun registerEvents() {
        childEventNode.listenOnly<PlayerChatEvent> {
            // Example event
        }

        // e.g. PlayerAttackEvent
    }
}