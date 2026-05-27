package com.example.demo.local

import androidx.room3.TypeConverter
import com.example.demo.core.Visibility

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String = value.joinToString(",")

    @TypeConverter
    fun toStringList(value: String): List<String> =
        value.split(",").filter { it.isNotEmpty() }

    @TypeConverter
    fun fromVisibility(visibility: Visibility): String = visibility.name

    @TypeConverter
    fun toVisibility(value: String): Visibility = Visibility.valueOf(value)
}