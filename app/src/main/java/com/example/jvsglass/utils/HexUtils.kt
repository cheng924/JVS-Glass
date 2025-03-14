package com.example.jvsglass.utils

fun ByteArray.toHexString(): String = joinToString(" ") { "%02X".format(it) }
