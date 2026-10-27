package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.CookieJar
import org.junit.Assume
import java.io.File

/** The throwaway account's cookies: `VELA_TEST_COOKIES`, else `~/.vela-test/cookies.txt`. Tests skip when neither exists. */
object LiveCookies {
    fun jarOrSkip(): CookieJar {
        val path = System.getenv("VELA_TEST_COOKIES") ?: File(System.getProperty("user.home"), ".vela-test/cookies.txt").path
        val f = File(path)
        Assume.assumeTrue("no cookies at VELA_TEST_COOKIES / ~/.vela-test/cookies.txt: skipping", f.isFile)
        val jar = CookieJar.parseNetscape(f.readText())
        Assume.assumeTrue("cookies file has no Google session", jar.looksSignedIn)
        return jar
    }
}
