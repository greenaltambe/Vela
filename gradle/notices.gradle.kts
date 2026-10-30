// Third-party licence notices, generated from the dependency graph that actually ships (desktop runtime + Android release runtime).
//   ./gradlew generateThirdPartyNotices   rewrites the generated block of THIRD_PARTY_NOTICES.md
//   ./gradlew checkThirdPartyNotices      fails when that block is out of date (CI runs this)
// Licences are read from each artifact's POM (and its parent POMs); an artifact without one is listed as "not declared".
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import javax.xml.parsers.DocumentBuilderFactory

val noticesFile = rootProject.layout.projectDirectory.file("THIRD_PARTY_NOTICES.md").asFile
val beginMark = "<!-- BEGIN GENERATED DEPENDENCIES -->"
val endMark = "<!-- END GENERATED DEPENDENCIES -->"

/** Artifacts whose POM carries no licence; what their projects state (checked by hand, revisit when they change). */
val overrides = mapOf(
    "com.google.guava:guava" to "Apache-2.0 (not in the POM; stated by the project)",
    "io.coil-kt.coil3:coil-core" to "Apache-2.0 (not in the POM; stated by the project)",
    "io.coil-kt.coil3:coil-compose-core" to "Apache-2.0 (not in the POM; stated by the project)",
    "io.coil-kt.coil3:coil-network-core" to "Apache-2.0 (not in the POM; stated by the project)",
    "com.github.TeamNewPipe:nanojson" to "Apache-2.0 (not in the POM; upstream grack/nanojson states Apache-2.0, to be confirmed)",
)

/** The generated table, built at execution time (resolves configurations, so these tasks opt out of the configuration cache). */
fun Project.dependencyTable(): String {
    val shipped = listOf(":desktopApp" to "runtimeClasspath", ":androidApp" to "releaseRuntimeClasspath")
    val ids = sortedMapOf<String, ModuleComponentIdentifier>()
    for ((path, name) in shipped) {
        val cfg = project(path).configurations.getByName(name)
        cfg.incoming.resolutionResult.allComponents.forEach { c ->
            val id = c.id
            if (id is ModuleComponentIdentifier) ids["${id.group}:${id.module}"] = id
        }
    }
    fun pomFile(g: String, a: String, v: String): File? = runCatching {
        configurations.detachedConfiguration(dependencies.create("$g:$a:$v@pom")).apply { isTransitive = false }.resolve().firstOrNull()
    }.getOrNull()
    fun parse(f: File) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false; setFeature("http://apache.org/xml/features/disallow-doctype-decl", false) }
        .newDocumentBuilder().parse(f).documentElement
    fun org.w3c.dom.Element.child(tag: String) = (0 until childNodes.length).map { childNodes.item(it) }.filterIsInstance<org.w3c.dom.Element>().firstOrNull { it.tagName == tag }
    fun licencesOf(pom: org.w3c.dom.Element): List<String> {
        val names = pom.child("licenses")?.let { l -> (0 until l.childNodes.length).map { l.childNodes.item(it) }.filterIsInstance<org.w3c.dom.Element>().mapNotNull { it.child("name")?.textContent?.trim() } }.orEmpty()
        return names.filter { it.isNotEmpty() }
    }
    val rows = ids.values.map { id ->
        var file = pomFile(id.group, id.module, id.version)
        var found: List<String> = emptyList()
        var hops = 0
        while (file != null && found.isEmpty() && hops < 5) {
            val root = runCatching { parse(file!!) }.getOrNull() ?: break
            found = licencesOf(root)
            if (found.isEmpty()) {
                val p = root.child("parent") ?: break
                file = pomFile(p.child("groupId")?.textContent?.trim().orEmpty(), p.child("artifactId")?.textContent?.trim().orEmpty(), p.child("version")?.textContent?.trim().orEmpty())
            }
            hops++
        }
        val key = "${id.group}:${id.module}"
        Triple(key, id.version, found.distinct().joinToString("; ").ifEmpty { overrides[key] ?: "not declared in the POM" })
    }
    return buildString {
        appendLine("| Artifact | Version | Licence (as declared) |")
        appendLine("|---|---|---|")
        rows.sortedBy { it.first }.forEach { (a, v, l) -> appendLine("| $a | $v | $l |") }
    }
}

fun newBlock(table: String) = "$beginMark\n$table$endMark"

tasks.register("generateThirdPartyNotices") {
    group = "documentation"
    description = "Rewrites the generated dependency table of THIRD_PARTY_NOTICES.md."
    notCompatibleWithConfigurationCache("resolves the shipped classpaths at execution time")
    doLast {
        val text = noticesFile.readText()
        val a = text.indexOf(beginMark); val b = text.indexOf(endMark)
        require(a >= 0 && b > a) { "THIRD_PARTY_NOTICES.md has no generated block markers" }
        noticesFile.writeText(text.substring(0, a) + newBlock(project.dependencyTable()) + text.substring(b + endMark.length))
        println("THIRD_PARTY_NOTICES.md updated")
    }
}

tasks.register("checkThirdPartyNotices") {
    group = "verification"
    description = "Fails when the generated dependency table of THIRD_PARTY_NOTICES.md is out of date."
    notCompatibleWithConfigurationCache("resolves the shipped classpaths at execution time")
    doLast {
        val text = noticesFile.readText()
        val a = text.indexOf(beginMark); val b = text.indexOf(endMark)
        require(a >= 0 && b > a) { "THIRD_PARTY_NOTICES.md has no generated block markers" }
        // Compare WHICH artifacts at WHICH versions ship; the licence column comes from POM lookups and is only informative, so a lookup that differs
        // between machines must not fail the check. On a mismatch the differences are printed.
        fun keys(block: String) = block.lineSequence().filter { it.startsWith("| ") && !it.startsWith("| Artifact") && !it.startsWith("|---") }
            .map { row -> row.split("|").map { it.trim() }.let { c -> "${c[1]} ${c[2]}" } }.toSortedSet()
        val committed = keys(text.substring(a, b))
        val actual = keys(project.dependencyTable())
        if (committed != actual) {
            val missing = (actual - committed).joinToString("\n  ") { "+ $it" }
            val extra = (committed - actual).joinToString("\n  ") { "- $it" }
            throw GradleException("THIRD_PARTY_NOTICES.md is out of date (+ ships but is not listed, - listed but not shipped):\n  $missing\n  $extra\nRun ./gradlew generateThirdPartyNotices and commit the result")
        }
    }
}
