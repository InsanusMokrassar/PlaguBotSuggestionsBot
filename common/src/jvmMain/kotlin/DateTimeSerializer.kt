package dev.inmo.plagubot.suggestionsbot.common

import korlibs.time.DateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object DateTimeSerializer : KSerializer<DateTime> {
    private val baseSerializer = Double.serializer()

    override val descriptor: SerialDescriptor
        get() = baseSerializer.descriptor

    override fun deserialize(decoder: Decoder): DateTime = DateTime(
        decoder.decodeDouble()
    )

    override fun serialize(encoder: Encoder, value: DateTime) {
        encoder.encodeDouble(value.unixMillis)
    }

}
