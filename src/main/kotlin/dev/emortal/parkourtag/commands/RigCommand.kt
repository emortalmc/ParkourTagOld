package dev.emortal.parkourtag.commands

import dev.emortal.immortal.game.GameManager.game
import dev.emortal.immortal.luckperms.PermissionUtils.hasLuckPermission
import dev.emortal.parkourtag.game.ParkourTagGame
import net.minestom.server.command.builder.Command
import net.minestom.server.entity.Player
import world.cepi.kstom.command.arguments.ArgumentPlayer

object RigCommand : Command("rig") {

    init {

        val playerArg = ArgumentPlayer("player")

        setCondition { sender, _ ->
            sender.hasLuckPermission("parkourtag.rig") || (sender as? Player)?.username == "emortaldev"
        }

        addConditionalSyntax({ sender, _ ->
            sender.hasLuckPermission("parkourtag.rig") || (sender as? Player)?.username == "emortaldev"
        }, { sender, context ->
            val player = sender as? Player ?: return@addConditionalSyntax

            val other = context.get(playerArg)
            val game = player.game as? ParkourTagGame ?: return@addConditionalSyntax
            if (!game.players.contains(other)) return@addConditionalSyntax

            game.riggedPlayer = other
            player.sendMessage("Game was rigged")
        }, playerArg)

    }

}