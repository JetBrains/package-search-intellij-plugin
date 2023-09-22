import com.jetbrains.packagesearch.plugin.core.nitrite.*
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.jetbrains.packagesearch.api.v3.ApiPackage
import org.junit.jupiter.api.BeforeAll

class NitriteDbTest {

    companion object {

        val db = buildDefaultNitrate(
            System.getenv("DB_PATH")
                ?.let { File(it) }
                ?.apply { parentFile.mkdirs() }
                ?: error("DB_PATH env variable is not set")
        )

        @JvmStatic
        @BeforeAll
        fun insertPackages(): Unit = runTest {
            val repository = db.getRepository<TestEntry>("entries")
            repository.documentCollection.removeAll()
            repository.insert(Array(20) { TestEntry() })
        }
    }

    @Test
    fun searchById() = runTest(timeout = 1.days) {
        val ids = NitriteDocumentFormatTest.getPackageInfoResponse.packages.map { it.id }
        val repository = db.getRepository<ApiPackageCacheEntry>("packages")
        val results = repository.find(
            NitriteFilters.Object.`in`(
                path = ApiPackageCacheEntry::data / ApiPackage::id,
                value = ids
            )
        ).toList()
            .map { it.data }
        results.forEach { aPackage ->
            assertEquals(
                expected = NitriteDocumentFormatTest.getPackageInfoResponse.packages.first { it.id == aPackage.id },
                actual = aPackage
            )
        }
    }

}