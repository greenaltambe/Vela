import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VelaVersionTest {
    private val ordered = listOf("0.1.0-alpha.1", "0.1.0-alpha.2", "0.1.0-beta.1", "0.1.0-beta.2", "0.1.0-rc.1", "0.1.0", "0.1.1-beta.1", "0.1.1", "0.2.0-beta.1", "0.2.0", "1.0.0-rc.1", "1.0.0", "1.0.1", "1.1.0", "2.0.0-beta.1")

    @Test fun codesAreStrictlyIncreasingInReleaseOrder() {
        val codes = ordered.map { VelaVersion.versionCode(it) }
        for (i in 1 until codes.size) assertTrue(codes[i] > codes[i - 1], "${ordered[i]} (${codes[i]}) must be above ${ordered[i - 1]} (${codes[i - 1]})")
    }

    @Test fun theFirstBetaHasAKnownCode() = assertEquals(10021, VelaVersion.versionCode("0.1.0-beta.1"))

    @Test fun packageVersionIsTheNumericCore() {
        assertEquals("0.1.0", VelaVersion.packageVersion("0.1.0-beta.1"))
        assertEquals("1.2.3", VelaVersion.packageVersion("1.2.3"))
    }

    @Test fun badVersionsAreRefused() {
        for (bad in listOf("1.0", "v1.0.0", "1.0.0-beta", "1.0.0-beta.0", "1.0.0-beta.30", "1.0.0-snapshot.1", "1.100.0")) assertFailsWith<IllegalArgumentException>(bad) { VelaVersion.versionCode(bad) }
    }
}
