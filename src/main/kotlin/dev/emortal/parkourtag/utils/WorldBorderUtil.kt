package dev.emortal.parkourtag.utils

import net.minestom.server.entity.Player
import net.minestom.server.network.packet.server.play.WorldBorderWarningReachPacket
import java.util.*

object WorldBorderUtil {
    val playersWithWarning = mutableSetOf<UUID>()

    fun Player.showWarning() {
        if (playersWithWarning.contains(uuid)) return

        playersWithWarning.add(uuid)
        val packet = WorldBorderWarningReachPacket(Integer.MAX_VALUE)
        sendPackets(packet)
    }

    fun Player.hideWarning() {
        if (!playersWithWarning.contains(uuid)) return

        playersWithWarning.remove(uuid)
        val packet = WorldBorderWarningReachPacket(0)
        sendPackets(packet)
    }
}