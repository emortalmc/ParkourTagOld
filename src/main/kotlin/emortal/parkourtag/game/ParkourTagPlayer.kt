package emortal.parkourtag.game

import net.kyori.adventure.text.Component
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.tag.Tag


object ParkourTagPlayer {
    val isTaggerTag = Tag.Byte("tagger")
    val isTaggedTag = Tag.Byte("tagged")
    var Player.isTagger: Boolean
        get() = this.getTag(isTaggerTag)?.toInt() == 1
        set(value) = this.setTag(isTaggerTag, if (value) 1 else 0)
    var Player.isTagged: Boolean
        get() = this.getTag(isTaggedTag)?.toInt() == 1
        set(value) = this.setTag(isTaggedTag, if (value) 1 else 0)

    fun Player.cleanup() { // To be done on player leave
        this.gameMode = GameMode.ADVENTURE
        this.removeTag(isTaggerTag)
        this.removeTag(isTaggedTag)
    }
}