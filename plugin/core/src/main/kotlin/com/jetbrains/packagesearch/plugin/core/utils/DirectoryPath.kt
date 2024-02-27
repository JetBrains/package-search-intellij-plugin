package com.jetbrains.packagesearch.plugin.core.utils

import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

fun Path.toDirectory() = DirectoryPath(this)

@Serializable(with = DirectoryPath.Companion::class)
class DirectoryPath(path: Path) : Path by path {
    init {
        require(path.isDirectory()) { "Path $path is not a directory" }
    }

    companion object : KSerializer<DirectoryPath> {
        override val descriptor: SerialDescriptor
            get() = NioPathSerializer.descriptor

        override fun deserialize(decoder: Decoder): DirectoryPath =
            NioPathSerializer.deserialize(decoder).toDirectory()

        override fun serialize(encoder: Encoder, value: DirectoryPath) {
            NioPathSerializer.serialize(encoder, value)
        }
    }
}