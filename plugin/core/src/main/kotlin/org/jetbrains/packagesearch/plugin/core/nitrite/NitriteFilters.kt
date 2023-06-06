package org.jetbrains.packagesearch.plugin.core.nitrite

import org.dizitart.no2.objects.ObjectFilter
import org.dizitart.no2.objects.filters.ObjectFilters

object NitriteFilters {
    object Object {
        fun eq(path: DocumentPathBuilder, value: Any): ObjectFilter =
            ObjectFilters.eq(path.build(), value)

        fun `in`(path: DocumentPathBuilder, value: Array<Any>): ObjectFilter =
            ObjectFilters.`in`(path.build(), *value)

        fun `in`(path: DocumentPathBuilder, value: Collection<Any>): ObjectFilter =
            `in`(path, value.toTypedArray())

        fun `in`(path: String, value: Collection<Any>): ObjectFilter =
            ObjectFilters.`in`(path, *value.toTypedArray())
    }
}