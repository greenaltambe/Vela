package com.greenaltambe.vela.sync

import kotlin.test.*

class PassphraseCodecTest {
    @Test fun roundTripAndCodeIsForgiving() {
        val data = "hello sync".encodeToByteArray()
        val enc = passphraseCodec("K7QM-2XPD-9TNA-HW4R").encode(data)
        assertFalse(enc.decodeToString().contains("hello"))
        assertContentEquals(data, passphraseCodec("k7qm 2xpd 9tna hw4r").decode(enc))
    }

    @Test fun wrongCodeOrDamageIsRejected() {
        val enc = passphraseCodec("AAAA-BBBB-CCCC-DDDD").encode("x".encodeToByteArray())
        assertFailsWith<SyncDecodeException> { passphraseCodec("AAAA-BBBB-CCCC-DDDE").decode(enc) }
        enc[enc.size - 1] = (enc.last() + 1).toByte()
        assertFailsWith<SyncDecodeException> { passphraseCodec("AAAA-BBBB-CCCC-DDDD").decode(enc) }
        assertFailsWith<SyncDecodeException> { passphraseCodec("AAAA-BBBB-CCCC-DDDD").decode("junk".encodeToByteArray()) }
    }

    @Test fun generatedCodeShape() {
        assertTrue(Regex("[A-Z2-9]{4}(-[A-Z2-9]{4}){3}").matches(generateSyncCode()))
    }
}
