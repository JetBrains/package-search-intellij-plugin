import kotlinx.serialization.*
import kotlinx.serialization.builtins.serializer
import org.dizitart.no2.Document
import org.jetbrains.packagesearch.plugin.core.nitrite.serialization.NitriteDocumentFormat
import kotlin.test.Test
import kotlin.test.assertEquals

@Serializable
data class Example(val a: Int, val c: Nested)

@Serializable
data class Nested(val s: String, val list: List<Int>)

@Serializable
data class Box<T>(val t: T)

class Tests {

    val example = Example(
        a = 42,
        c = Nested(
            s = "foo",
            list = listOf(0, 1, 2, 3)
        )
    )

    val nd = NitriteDocumentFormat()

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
    fun encodeValue() {
        val doc = nd.encodeToDocument(String.serializer(), "42")
    }

}

fun Document.getDocument(key: String) =
    get(key) as? Document ?: error("Element \"$key\" is of type ${get(key)::class.simpleName}")