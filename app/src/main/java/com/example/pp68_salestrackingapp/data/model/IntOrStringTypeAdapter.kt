package com.example.pp68_salestrackingapp.data.model

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

class IntOrStringTypeAdapter : TypeAdapter<String>() {
    override fun write(out: JsonWriter, value: String?) { out.value(value) }
    override fun read(reader: JsonReader): String = when (reader.peek()) {
        JsonToken.NUMBER -> reader.nextLong().toString()
        JsonToken.NULL   -> { reader.nextNull(); "" }
        else             -> reader.nextString()
    }
}
