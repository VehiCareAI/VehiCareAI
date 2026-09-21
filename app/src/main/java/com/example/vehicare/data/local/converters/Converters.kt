package com.example.vehicare.data.local.converters

import androidx.room.TypeConverter

/**
 * Room type converters for the list/map/set columns used by assessments and their results.
 *
 * All collections are persisted with [StringCollectionCodec] (a documented, escape-safe text
 * format) so the schema stays readable, migration-friendly and free of any serialization
 * dependency. Order is preserved for lists and sets (insertion order); maps keep insertion order.
 */
class Converters {

    @TypeConverter
    fun fromStringList(value: List<String>?): String = StringCollectionCodec.encodeList(value)

    @TypeConverter
    fun toStringList(value: String?): List<String> = StringCollectionCodec.decodeList(value)

    @TypeConverter
    fun fromStringSet(value: Set<String>?): String = StringCollectionCodec.encodeSet(value)

    @TypeConverter
    fun toStringSet(value: String?): Set<String> = StringCollectionCodec.decodeSet(value)

    @TypeConverter
    fun fromStringMap(value: Map<String, String>?): String = StringCollectionCodec.encodeMap(value)

    @TypeConverter
    fun toStringMap(value: String?): Map<String, String> = StringCollectionCodec.decodeMap(value)
}
