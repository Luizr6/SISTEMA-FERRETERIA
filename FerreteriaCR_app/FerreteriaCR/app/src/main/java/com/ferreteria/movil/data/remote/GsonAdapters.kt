package com.ferreteria.movil.data.remote

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

/**
 * Adaptador Gson para deserializar números Double provenientes de DecimalField de Django
 * tanto si vienen en formato numérico (12.50) como si vienen en formato String ("12.50").
 */
class DoubleTypeAdapter : TypeAdapter<Double>() {
    override fun write(out: JsonWriter, value: Double?) {
        if (value == null) {
            out.nullValue()
        } else {
            out.value(value)
        }
    }

    override fun read(reader: JsonReader): Double {
        return when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                0.0
            }
            JsonToken.STRING -> {
                val str = reader.nextString()
                str.toDoubleOrNull() ?: 0.0
            }
            JsonToken.NUMBER -> {
                reader.nextDouble()
            }
            else -> {
                reader.skipValue()
                0.0
            }
        }
    }
}
