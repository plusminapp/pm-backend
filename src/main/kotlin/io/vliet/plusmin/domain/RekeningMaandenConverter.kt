package io.vliet.plusmin.domain

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter
class RekeningMaandenConverter : AttributeConverter<Set<Int>, Int> {
    override fun convertToDatabaseColumn(attribute: Set<Int>?): Int {
        if (attribute == null) return 0
        return attribute.fold(0) { acc, maand -> acc or (1 shl (maand - 1)) }
    }

    override fun convertToEntityAttribute(dbData: Int?): Set<Int> {
        if (dbData == null) return emptySet()
        return (1..12).filter { maand -> dbData and (1 shl (maand - 1)) != 0 }.toSet()
    }
}