package com.jetbrains.packagesearch.plugin.tests.projectservice

import com.jetbrains.packagesearch.plugin.tests.PKGS_TEST_DATA_OUTPUT_DIR
import com.jetbrains.packagesearch.plugin.tests.SerializablePackageSearchModule
import com.jetbrains.packagesearch.plugin.tests.TestResult
import com.jetbrains.packagesearch.plugin.tests.getResourceAbsolutePath
import kotlin.io.path.inputStream
import kotlin.io.path.isRegularFile
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

/**
 * Validates the result of a test scenario.
 *
 * @param projectName the name of the project being tested
 * @param expectedResultPath the path of the expected result JSON file
 */
internal fun TestScope.validateResult(projectName: String, expectedResultPath: String) {
    val outputResultFile = PKGS_TEST_DATA_OUTPUT_DIR.resolve("modules.json")

    assert(outputResultFile.isRegularFile()) {
        "Starter Ide result not found, output result file not found or is not a file: $outputResultFile, " +
                "check DumpPackageSearchModules.kt"
    }

    val result = Json
        .decodeFromStream<TestResult<Map<String, SerializablePackageSearchModule>>>(outputResultFile.inputStream())

    assertNull(result.error, "Test failed with error: \n${result.error}")

    val expected = Json
        .decodeFromStream<TestResult<Map<String, SerializablePackageSearchModule>>>(
            getResourceAbsolutePath(expectedResultPath)
                ?.toFile()
                ?.inputStream()
                ?: error("Assertion data not found for project $projectName")
        )

    assertNotNull(
        expected.value,
        "Deserialization of expected result failed or is null for project $projectName"
    )

    assertNotNull(
        result.value,
        "Deserialization of test result failed or is null for project $projectName"
    )


    assert(expected.value?.keys?.any { it in (result.value?.keys ?: emptySet()) } ?: false) {
        buildString {
            appendLine("expected MODULE keys differ from result keys")
            appendLine("expected: ${expected.value?.keys}")
            appendLine("result: ${result.value?.keys}")
        }
    }

    expected.value?.forEach { (key, value) ->
        assertNotNull(
            result.value?.get(key),
            buildString {
                appendLine("expected module $key not found in result modules")
                appendLine(" ( result module: ${result.value?.keys} )")
                appendLine(" ( expected module: ${expected.value?.keys} )")
            }
        )
        result.value?.get(key)?.let {
            validateModule(value, it)
        }
    }
}

internal fun validateModule(
    expected: SerializablePackageSearchModule,
    result: SerializablePackageSearchModule,
) {
    val json by lazy { Json { prettyPrint = true } }
    val printableJson by lazy {
        buildString {
            appendLine("expected:")
            appendLine(json.encodeToString(SerializablePackageSearchModule.serializer(), expected))
            appendLine("result:")
            appendLine(json.encodeToString(SerializablePackageSearchModule.serializer(), result))
        }
    }

    expected.compatiblePackageTypes.let {
        assert(it.size == result.compatiblePackageTypes.size) {
            buildString {
                appendLine("compatible packageType size differ from expected dump")
                appendLine(printableJson)
            }
        }
        assert(it.all { it in result.compatiblePackageTypes }) {
            buildString {
                appendLine("compatible packageType differ from expected dump")
                appendLine(printableJson)
            }
        }
    }

    expected.declaredRepositories.let {
        assert(it.keys.all { it in result.declaredRepositories.keys }) {
            buildString {
                appendLine("declaredKnownRepositories keys differ from expected dump")
                appendLine(printableJson)
            }
        }
        assert(it.values.all { it in result.declaredRepositories.values }) {
            buildString {
                appendLine("declaredKnownRepositories values differ from expected dump")
                appendLine(printableJson)
            }
        }
    }

    assert(expected.identity == result.identity) {
        buildString {
            appendLine("identity differ from expected dump")
            appendLine(printableJson)
        }
    }

}