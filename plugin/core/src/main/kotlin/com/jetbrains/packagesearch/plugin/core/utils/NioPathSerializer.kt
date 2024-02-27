package com.jetbrains.packagesearch.plugin.core.utils

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object NioPathSerializer : KSerializer<Path> {
    override val descriptor = String.serializer().descriptor

    override fun deserialize(decoder: Decoder): Path =
        Paths.get(String.serializer().deserialize(decoder))

    override fun serialize(encoder: Encoder, value: Path) {
        String.serializer().serialize(encoder, value.absolutePathString())
    }

}
