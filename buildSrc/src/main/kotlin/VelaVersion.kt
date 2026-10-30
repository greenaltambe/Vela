import java.io.File
import java.util.Properties

/**
 * The one version of the app: `VERSION_NAME` in `version.properties` at the repository root (for example `0.1.0-beta.1`).
 * The Android versionName is that string, the Android versionCode is derived from it ([versionCode]), and the desktop packages use the
 * numeric part ([packageVersion]). A release build of a tag `v0.1.0-beta.1` must carry exactly this version (CI checks it).
 */
object VelaVersion {
    class Parsed(val major: Int, val minor: Int, val patch: Int, val stage: String?, val ordinal: Int) {
        val core get() = "$major.$minor.$patch"
    }

    private val pattern = Regex("""^(\d{1,3})\.(\d{1,2})\.(\d{1,2})(?:-(alpha|beta|rc)\.(\d{1,2}))?$""")

    fun parse(name: String): Parsed {
        val m = pattern.matchEntire(name.trim()) ?: throw IllegalArgumentException("Not a version like 1.2.3 or 1.2.3-beta.4: '$name'")
        val (major, minor, patch, stage, ordinal) = m.destructured
        return Parsed(major.toInt(), minor.toInt(), patch.toInt(), stage.ifEmpty { null }, ordinal.ifEmpty { "0" }.toInt())
    }

    /**
     * major*1_000_000 + minor*10_000 + patch*100 + stage, where within one x.y.z: alpha.n = n (1..19), beta.n = 20+n (n 1..29),
     * rc.n = 50+n (n 1..39), final = 99. So 0.1.0-beta.1 < 0.1.0-beta.2 < 0.1.0-rc.1 < 0.1.0 < 0.1.1-beta.1, and a code never goes down.
     */
    fun versionCode(name: String): Int {
        val v = parse(name)
        require(v.minor <= 99 && v.patch <= 99) { "minor and patch must be at most 99" }
        val stage = when (v.stage) {
            "alpha" -> { require(v.ordinal in 1..19) { "alpha.n needs n in 1..19" }; v.ordinal }
            "beta" -> { require(v.ordinal in 1..29) { "beta.n needs n in 1..29" }; 20 + v.ordinal }
            "rc" -> { require(v.ordinal in 1..39) { "rc.n needs n in 1..39" }; 50 + v.ordinal }
            else -> 99
        }
        return v.major * 1_000_000 + v.minor * 10_000 + v.patch * 100 + stage
    }

    /** The numeric x.y.z the desktop packages carry (jpackage accepts only numbers). */
    fun packageVersion(name: String): String = parse(name).core

    /** Reads `VERSION_NAME` from [file]. */
    fun read(file: File): String {
        val p = Properties().also { f -> file.inputStream().use { f.load(it) } }
        return p.getProperty("VERSION_NAME")?.trim()?.takeIf { it.isNotEmpty() } ?: throw IllegalStateException("VERSION_NAME missing in $file")
    }
}
