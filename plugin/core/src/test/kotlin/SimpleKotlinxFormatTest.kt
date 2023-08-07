@file:Suppress("UNCHECKED_CAST")

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.dizitart.no2.Document
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage
import org.jetbrains.packagesearch.api.v3.ApiMavenPackage.ApiVariant.Attribute
import org.jetbrains.packagesearch.api.v3.http.GetPackageInfoResponse
import com.jetbrains.packagesearch.plugin.core.nitrite.serialization.NitriteDocumentFormat
import com.jetbrains.packagesearch.plugin.core.nitrite.serialization.encodeToDocument
import com.jetbrains.packagesearch.plugin.core.nitrite.serialization.getDocument
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
data class Example(val a: Int, val c: Nested)

@Serializable
data class Nested(val s: String, val list: List<Int>)

@Serializable
data class Box<T>(val t: T)

class NitriteDocumentFormatTest {

    companion object {

        val nd = NitriteDocumentFormat()

        val getPackageInfoResponse
            get() = Json.decodeFromStream<GetPackageInfoResponse>(getResource("packages.json"))

        val document
            get() = nd.encodeToDocument(GetPackageInfoResponse.serializer(), getPackageInfoResponse)

        val example = Example(
            a = 42,
            c = Nested(
                s = "foo",
                list = listOf(0, 1, 2, 3)
            )
        )

    }

    @Test
    fun encoding() {
        val document = nd.encodeToDocument(Example.serializer(), example)
        assertEquals(example.a, document[Example::a.name])
        assertEquals(example.c.s, document.getDocument(Example::c.name)[Nested::s.name])
        assertEquals(example.c.list, document.getDocument(Example::c.name)[Nested::list.name])
    }

    @Test
    fun decoding() {
        val document = nd.encodeToDocument(Example.serializer(), example)
        val decoded = nd.decodeFromDocument(Example.serializer(), document)
        assertEquals(example, decoded)
    }

    @Test
    fun checkPolymorphicEncodingTypeField() {
        val doc = nd.encodeToDocument(GetPackageInfoResponse.serializer(), getPackageInfoResponse)
        val packages = doc["packages"] as List<Document>
        val firstPackage = packages.first()
        assertEquals(firstPackage["type"], "maven")
    }

    @Test
    fun decodePolymorphic() {
        val docs = nd.decodeFromDocument(GetPackageInfoResponse.serializer(), document)
        println(docs)
    }

    @Test
    fun encodeAttributes() {
        val variant =
            getPackageInfoResponse.packages.filterIsInstance<ApiMavenPackage>()
                .first()
                .versions
                .all
                .values
                .filterIsInstance<ApiMavenPackage.GradleVersion>()
                .first()
                .variants
                .first()
        val document = nd.encodeToDocument(variant)
        println(document)
    }

}

@Serializable
data class AttributesContainer(
    val attributes: Map<String, Attribute>
)

fun getResourceOrNull(name: String): InputStream? =
    Thread.currentThread().contextClassLoader
        .getResourceAsStream(name)

fun getResource(name: String) =
    getResourceOrNull(name) ?: error("Resource named \"$name\" not found.")
