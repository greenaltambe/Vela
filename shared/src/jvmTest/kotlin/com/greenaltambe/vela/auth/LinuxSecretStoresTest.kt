package com.greenaltambe.vela.auth

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.*

class LinuxSecretStoresTest {
    private val dir = Files.createTempDirectory("vela-secrets").toFile()
    @AfterTest fun clean() { dir.deleteRecursively() }

    @Test fun encryptedFileRoundTripsAndNeverHoldsPlaintext() {
        val s = EncryptedFileSecretStore(dir)
        s.put("cookies.a", "SAPISID=super-secret-value"); s.put("cookies.b", "other")
        assertEquals("SAPISID=super-secret-value", EncryptedFileSecretStore(dir).get("cookies.a"))
        assertEquals(setOf("cookies.a", "cookies.b"), s.keys())
        val onDisk = File(dir, "secrets.enc").readBytes().toString(Charsets.ISO_8859_1)
        assertFalse(onDisk.contains("super-secret") || onDisk.contains("cookies.a"), "neither values nor key names in the clear")
        s.delete("cookies.a"); assertNull(s.get("cookies.a")); assertEquals(setOf("cookies.b"), s.keys())
    }

    @Test fun filesAreOwnerOnly() {
        org.junit.Assume.assumeFalse("POSIX permissions do not exist on Windows (the Windows store is DPAPI)", com.greenaltambe.vela.data.isWindows)
        EncryptedFileSecretStore(dir).put("k", "v")
        listOf("secrets.key", "secrets.enc").forEach {
            assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(File(dir, it).toPath())), it)
        }
    }

    @Test fun tamperedFileReadsAsEmptyNotAsGarbage() {
        val s = EncryptedFileSecretStore(dir); s.put("k", "v")
        val f = File(dir, "secrets.enc"); val b = f.readBytes(); b[b.size - 1] = (b.last().toInt() xor 1).toByte(); f.writeBytes(b)
        assertNull(s.get("k")); assertTrue(s.keys().isEmpty())
    }

    @Test fun lostKeyMeansSecretsAreGoneNotExposed() {
        EncryptedFileSecretStore(dir).put("k", "v")
        File(dir, "secrets.key").delete()
        assertNull(EncryptedFileSecretStore(dir).get("k"))
    }

    private class FakeKeyring : CommandRunner {
        val items = LinkedHashMap<String, String>(); val argvSeen = mutableListOf<List<String>>()
        override fun run(command: List<String>, stdin: String?): Pair<Int, String> {
            argvSeen += command
            val key = command.getOrNull(command.indexOf("key") + 1)
            return when (command[1]) {
                "store" -> { items[key!!] = stdin!!; 0 to "" }
                "lookup" -> items[key]?.let { 0 to it } ?: (1 to "")
                "clear" -> { items.remove(key); 0 to "" }
                "search" -> 0 to items.keys.joinToString("\n") { "[/x]\nattribute.key = $it\nattribute.service = vela" }
                else -> 2 to ""
            }
        }
    }

    @Test fun secretToolKeepsSecretsOffTheCommandLine() {
        val kr = FakeKeyring(); val s = SecretToolStore(kr)
        s.put("cookies.a", "SAPISID=top-secret")
        assertEquals("SAPISID=top-secret", s.get("cookies.a"))
        assertEquals(setOf("cookies.a"), s.keys())
        assertTrue(kr.argvSeen.none { cmd -> cmd.any { it.contains("top-secret") } }, "secret only on stdin")
        s.delete("cookies.a"); assertNull(s.get("cookies.a"))
    }

    @Test fun choosesLibsecretOnlyWhenItReallyWorksElseTheFile() {
        assertIs<SecretToolStore>(linuxSecretStore(dir, FakeKeyring()))
        val broken = CommandRunner { _, _ -> 1 to "" }                       // tool present, no keyring daemon
        assertIs<EncryptedFileSecretStore>(linuxSecretStore(dir, broken))
        val missing = CommandRunner { _, _ -> throw java.io.IOException("no such file") }
        assertIs<EncryptedFileSecretStore>(linuxSecretStore(dir, missing))
    }
}
