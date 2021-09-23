package dev.emortal.parkourtag

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.minestom.server.coordinate.Pos


class Utils {
    companion object{
        fun componentToString(component: Component): String{
            return PlainTextComponentSerializer.plainText().serialize(component);
        }
        fun parseLocations(locations: Locations): List<Pos> {
            val taggerSplit = locations.taggerSpawn?.split(";")
            val goonSplit = locations.goonSpawn?.split(";")
            val preGameSpawnSplit = locations.goonSpawn?.split(";")
            val taggerSpawnPositionParsed: Pos = Pos(taggerSplit!![0].toDouble(), taggerSplit[1].toDouble(), taggerSplit[2].toDouble(), taggerSplit[3].toFloat(), taggerSplit[4].toFloat())
            val goonSpawnPositionParsed: Pos = Pos(goonSplit!![0].toDouble(), goonSplit[1].toDouble(), goonSplit[2].toDouble(), goonSplit[3].toFloat(), goonSplit[4].toFloat())
            val preGameSpawnPositionParsed: Pos = Pos(preGameSpawnSplit!![0].toDouble(), preGameSpawnSplit[1].toDouble(), preGameSpawnSplit[2].toDouble(), preGameSpawnSplit[3].toFloat(), preGameSpawnSplit[4].toFloat())
            return listOf(taggerSpawnPositionParsed, goonSpawnPositionParsed, preGameSpawnPositionParsed)
        }
        fun secondsToParsed(inputSeconds: Int): String{
            var string = "";
            val hours = inputSeconds / 3600
            val minutes = (inputSeconds % 3600) / 60
            val seconds = inputSeconds % 60

            if(hours > 0){
                string += "${hours}h"
            }
            if(minutes > 0){
                string += " ${minutes}m "
            }
            string += "${seconds}s"
            return string
        }
    }
}