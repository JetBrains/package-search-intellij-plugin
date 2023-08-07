import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.dizitart.no2.Nitrite
import org.jetbrains.packagesearch.api.v3.ApiPackage
import com.jetbrains.packagesearch.plugin.core.nitrite.*
import org.junit.jupiter.api.BeforeAll
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.days

class NitriteDbTest {

    companion object {

        val db = Nitrite.builder()
            .kotlinxNitriteMapper()
            .filePath(File(System.getenv("DB_PATH")).apply { parentFile.mkdirs() })
            .openOrCreate()
            .asCoroutine()

        @JvmStatic
        @BeforeAll
        fun insertPackages(): Unit = runTest {
            val repository = db.getRepository<ApiPackageCacheEntry>("packages")
            repository.documentCollection.removeAll()
            repository.insert(NitriteDocumentFormatTest.getPackageInfoResponse.packages.map { it.asCacheEntry() })
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