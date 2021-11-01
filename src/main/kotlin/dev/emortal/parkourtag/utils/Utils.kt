package dev.emortal.parkourtag.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

fun Component.toString(): String = PlainTextComponentSerializer.plainText().serialize(this);
fun Int.parsed(): String {
    var string = "";
    val hours = this / 3600
    val minutes = (this % 3600) / 60
    val seconds = this % 60

    if (hours > 0) {
        string += "${hours}h "
    }
    if (minutes > 0) {
        string += "${minutes}m "
    }
    string += "${seconds}s"
    return string
}