package com.greenaltambe.vela.auth

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private fun File.writePrivate(bytes: ByteArray) {
    if (!exists()) {
        try { Files.createFile(toPath(), java.nio.file.attribute.PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))) }
        catch (_: UnsupportedOperationException) { createNewFile() }
    }
    writeBytes(bytes)
}

/**
 * Fallback when no keyring is available: all secrets in one AES-256-GCM encrypted file, the key in a 0600 file beside it.
 * This protects against the secrets file leaking alone (backup, sync folder) but not against someone who can read both,
 * which is why [description] says so and Diagnostics shows it.
 */
class EncryptedFileSecretStore(private val dir: File) : SecretStore {
    override val description = "encrypted file (key stored beside it, mode 0600)"
    private val keyFile = File(dir, "secrets.key")
    private val dataFile = File(dir, "secrets.enc")
    private val random = SecureRandom()

    init { dir.mkdirs() }

    private fun key(): SecretKeySpec {
        if (!keyFile.exists()) keyFile.writePrivate(ByteArray(32).also(random::nextBytes))
        return SecretKeySpec(keyFile.readBytes(), "AES")
    }

    @Synchronized private fun load(): MutableMap<String, String> {
        if (!dataFile.exists()) return LinkedHashMap()
        val raw = dataFile.readBytes()
        if (raw.size < 13) return LinkedHashMap()
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12))) }
            decode(c.doFinal(raw, 12, raw.size - 12))
        } catch (e: Exception) { LinkedHashMap() } // wrong key or tampered file: treat as empty, never as plaintext
    }

    @Synchronized private fun save(map: Map<String, String>) {
        val iv = ByteArray(12).also(random::nextBytes)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        dataFile.writePrivate(iv + c.doFinal(encode(map)))
    }

    // length-prefixed UTF-8 pairs: no JSON dependency, nothing to escape
    private fun encode(map: Map<String, String>): ByteArray = java.io.ByteArrayOutputStream().also { out ->
        java.io.DataOutputStream(out).apply { writeInt(map.size); map.forEach { (k, v) -> writeUTF(k); val b = v.toByteArray(); writeInt(b.size); write(b) } }
    }.toByteArray()

    private fun decode(bytes: ByteArray): MutableMap<String, String> {
        val inp = java.io.DataInputStream(bytes.inputStream()); val out = LinkedHashMap<String, String>()
        repeat(inp.readInt()) { val k = inp.readUTF(); val b = ByteArray(inp.readInt()); inp.readFully(b); out[k] = String(b) }
        return out
    }

    override fun get(key: String) = load()[key]
    override fun put(key: String, value: String) = save(load().also { it[key] = value })
    override fun delete(key: String) = save(load().also { it.remove(key) })
    override fun keys(): Set<String> = load().keys.toSet()
}

/** Runs a command with [stdin] and returns exit code and stdout. A seam so the libsecret store can be tested without a keyring. */
fun interface CommandRunner { fun run(command: List<String>, stdin: String?): Pair<Int, String> }

object SystemCommandRunner : CommandRunner {
    override fun run(command: List<String>, stdin: String?): Pair<Int, String> {
        val p = ProcessBuilder(command).redirectErrorStream(false).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        p.outputStream.use { o -> stdin?.let { o.write(it.toByteArray()) } }
        val out = p.inputStream.readBytes().toString(Charsets.UTF_8)
        if (!p.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) { p.destroyForcibly(); return -1 to "" }
        return p.exitValue() to out
    }
}

/**
 * libsecret through the `secret-tool` command. Secrets go in on stdin (never argv, so not visible in `ps`) and come out
 * on stdout, which is only ever read here. Items are tagged `service=vela`, `key=<name>`.
 */
class SecretToolStore(private val runner: CommandRunner = SystemCommandRunner) : SecretStore {
    override val description = "libsecret (secret-tool)"

    override fun get(key: String): String? =
        runner.run(listOf("secret-tool", "lookup", "service", SERVICE, "key", key), null).let { (code, out) -> if (code == 0 && out.isNotEmpty()) out else null }

    override fun put(key: String, value: String) {
        val (code, _) = runner.run(listOf("secret-tool", "store", "--label=Vela $key", "service", SERVICE, "key", key), value)
        check(code == 0) { "secret-tool store failed (exit $code)" }
    }

    override fun delete(key: String) { runner.run(listOf("secret-tool", "clear", "service", SERVICE, "key", key), null) }

    override fun keys(): Set<String> =
        runner.run(listOf("secret-tool", "search", "--all", "service", SERVICE), null).second.lineSequence()
            .mapNotNull { Regex("^attribute\\.key = (.+)$").find(it)?.groupValues?.get(1) }.toSet()

    companion object {
        const val SERVICE = "vela"

        /** True when `secret-tool` exists AND a keyring answers: a write/read/delete round trip on a probe item. */
        fun works(runner: CommandRunner = SystemCommandRunner): Boolean = try {
            val s = SecretToolStore(runner); val probe = "probe.${System.nanoTime()}"
            s.put(probe, "ok"); val ok = s.get(probe) == "ok"; s.delete(probe); ok
        } catch (_: Exception) { false }
    }
}

/** The Linux store: libsecret when it works, else the encrypted file, whose weaker protection is stated in [SecretStore.description]. */
fun linuxSecretStore(dir: File, runner: CommandRunner = SystemCommandRunner): SecretStore =
    if (SecretToolStore.works(runner)) SecretToolStore(runner) else EncryptedFileSecretStore(dir)

/**
 * Windows: the secrets map is protected with DPAPI (`CryptProtectData`, current-user scope) and kept in one file; another Windows user, or a copy of
 * the file on another machine, cannot read it. There is no plaintext fallback: when DPAPI is unavailable [works] is false and the caller refuses to
 * store account credentials.
 */
class WindowsDpapiSecretStore(private val dir: File) : SecretStore {
    override val description = "Windows DPAPI (protected for this Windows user)"
    private val dataFile = File(dir, "secrets.dpapi")
    private val entropy = "vela-secrets-v1".toByteArray()

    init { dir.mkdirs() }

    @Synchronized private fun load(): MutableMap<String, String> {
        if (!dataFile.exists()) return LinkedHashMap()
        return try {
            decodeSecrets(com.sun.jna.platform.win32.Crypt32Util.cryptUnprotectData(dataFile.readBytes(), entropy, 0, null))
        } catch (e: Exception) { LinkedHashMap() } // another user's file or a damaged one: empty, never plaintext
    }

    @Synchronized private fun save(map: Map<String, String>) {
        dataFile.writeBytes(com.sun.jna.platform.win32.Crypt32Util.cryptProtectData(encodeSecrets(map), entropy, 0, "Vela", null))
    }

    override fun get(key: String) = load()[key]
    override fun put(key: String, value: String) = save(load().also { it[key] = value })
    override fun delete(key: String) = save(load().also { it.remove(key) })
    override fun keys(): Set<String> = load().keys.toSet()

    companion object {
        /** True when a protect/unprotect round trip works on this machine. */
        fun works(): Boolean = try {
            val probe = "dpapi-probe".toByteArray()
            com.sun.jna.platform.win32.Crypt32Util.cryptUnprotectData(com.sun.jna.platform.win32.Crypt32Util.cryptProtectData(probe)).contentEquals(probe)
        } catch (_: Throwable) { false }
    }
}

/** Shown instead of a store where none is safe: nothing is written; account sign-in is refused with the reason. */
class UnavailableSecretStore(val reason: String) : SecretStore {
    override val description = "unavailable: $reason"
    override fun get(key: String): String? = null
    override fun put(key: String, value: String) = throw IllegalStateException(reason)
    override fun delete(key: String) {}
    override fun keys(): Set<String> = emptySet()
}

/**
 * The store for this desktop: libsecret or the encrypted file on Linux; DPAPI on Windows, and on Windows NEVER a plaintext or beside-the-key file:
 * without DPAPI the store is [UnavailableSecretStore] and signing in to an account is refused (see [accountsAvailable]).
 */
fun desktopSecretStore(dir: File, windows: Boolean = com.greenaltambe.vela.data.isWindows): SecretStore = when {
    !windows -> linuxSecretStore(dir)
    WindowsDpapiSecretStore.works() -> WindowsDpapiSecretStore(dir)
    else -> UnavailableSecretStore("Windows data protection (DPAPI) is not available, so Vela will not store account credentials")
}

/** False when account sign-in must be disabled because no safe store exists on this machine. */
fun accountsAvailable(store: SecretStore): Boolean = store !is UnavailableSecretStore

internal fun encodeSecrets(map: Map<String, String>): ByteArray = java.io.ByteArrayOutputStream().also { out ->
    java.io.DataOutputStream(out).apply { writeInt(map.size); map.forEach { (k, v) -> writeUTF(k); val b = v.toByteArray(); writeInt(b.size); write(b) } }
}.toByteArray()

internal fun decodeSecrets(bytes: ByteArray): MutableMap<String, String> {
    val inp = java.io.DataInputStream(bytes.inputStream()); val out = LinkedHashMap<String, String>()
    repeat(inp.readInt()) { val k = inp.readUTF(); val b = ByteArray(inp.readInt()); inp.readFully(b); out[k] = String(b) }
    return out
}
