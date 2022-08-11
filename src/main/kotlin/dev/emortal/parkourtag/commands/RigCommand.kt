package dev.emortal.parkourtag.commands

import dev.emortal.immortal.game.GameManager.game
import dev.emortal.immortal.luckperms.PermissionUtils.hasLuckPermission
import dev.emortal.parkourtag.game.ParkourTagGame
import world.cepi.kstom.command.arguments.ArgumentPlayer
import world.cepi.kstom.command.kommand.Kommand

object RigCommand : Kommand({

    condition {
        sender.hasLuckPermission("parkourtag.rig")
    }

    val playerArg = ArgumentPlayer("player")

    syntax(playerArg) {
        val other = !playerArg
        val game = player.game as? ParkourTagGame ?: return@syntax
        if (!game.players.contains(other)) return@syntax

        game.riggedPlayer = other
        player.sendMessage("Game was rigged")
    }

}, "rig")