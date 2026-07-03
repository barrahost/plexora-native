package com.dinfras.plexora.data

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

// Ce fournisseur IPTV renvoie parfois des nombres sous forme de texte (ex.
// stream_id en "1234" au lieu de 1234) selon les entrées du catalogue —
// deja constate plusieurs fois avec ce serveur (encodage, EPG...). Sans
// tolerance, Moshi rejette TOUT le lot des 4000+ chaines des qu'une seule
// entree est mal typee, laissant la liste entierement vide.
private object LenientIntAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): Int {
        return when (reader.peek()) {
            JsonReader.Token.STRING -> reader.nextString().trim().toIntOrNull() ?: 0
            JsonReader.Token.NUMBER -> reader.nextDouble().toInt()
            JsonReader.Token.NULL -> { reader.nextNull<Unit>(); 0 }
            else -> { reader.skipValue(); 0 }
        }
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: Int) {
        writer.value(value)
    }
}

private object LenientDoubleAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): Double {
        return when (reader.peek()) {
            JsonReader.Token.STRING -> reader.nextString().trim().toDoubleOrNull() ?: 0.0
            JsonReader.Token.NUMBER -> reader.nextDouble()
            JsonReader.Token.NULL -> { reader.nextNull<Unit>(); 0.0 }
            else -> { reader.skipValue(); 0.0 }
        }
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: Double) {
        writer.value(value)
    }
}

private object LenientStringAdapter {
    @FromJson
    fun fromJson(reader: JsonReader): String {
        return when (reader.peek()) {
            JsonReader.Token.STRING -> reader.nextString()
            JsonReader.Token.NUMBER -> reader.nextDouble().let { if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString() }
            JsonReader.Token.BOOLEAN -> reader.nextBoolean().toString()
            JsonReader.Token.NULL -> { reader.nextNull<Unit>(); "" }
            else -> { reader.skipValue(); "" }
        }
    }

    @ToJson
    fun toJson(writer: JsonWriter, value: String) {
        writer.value(value)
    }
}

object MoshiProvider {
    val instance: Moshi = Moshi.Builder()
        .add(LenientIntAdapter)
        .add(LenientDoubleAdapter)
        .add(LenientStringAdapter)
        .add(KotlinJsonAdapterFactory())
        .build()
}
