package com.greenaltambe.vela.auth

import com.greenaltambe.vela.data.LOCAL_ACCOUNT
import com.greenaltambe.vela.data.PlaylistRepository
import com.greenaltambe.vela.data.SearchHistoryRepository
import com.greenaltambe.vela.data.SubscriptionRepository
import com.greenaltambe.vela.data.JvmDatabaseDriverFactory
import com.greenaltambe.vela.data.createDatabase
import kotlin.test.*
import kotlin.time.Instant

class AccountManagerTest {
    private val db = createDatabase(JvmDatabaseDriverFactory())
    private val secrets = InMemorySecretStore()
    private val credentials = CredentialStore(secrets)
    private val manager = AccountManager(db, credentials)
    private val identity = GoogleIdentity("Test Person", "test.person@gmail.com", "https://img/a.png", "UCtest1234567890")
    private val jar = CookieJar(listOf(Cookie("SAPISID", "secret-value", ".youtube.com")))

    @Test fun aLocalAccountAlwaysExistsAndIsActiveFirst() {
        assertEquals(LOCAL_ACCOUNT, manager.active.value.id)
        assertEquals(listOf(AccountKind.LOCAL), manager.all.value.map { it.kind })
    }

    @Test fun addingGoogleStoresCredentialsOutsideTheDatabaseAndActivates() {
        val a = manager.addGoogle(identity, jar)
        assertEquals(a.id, manager.active.value.id)
        assertTrue(a.isSignedIn)
        assertNotNull(manager.cookies(a.id))
        assertEquals(setOf("cookies.${a.id}"), secrets.keys().toSet())
        assertEquals("t***@gmail.com", a.maskedEmail)
        assertFalse(a.label.contains("Test"), "label shows neither name nor e-mail")
    }

    @Test fun theSameIdentityMapsToTheSameAccount() {
        val first = manager.addGoogle(identity, jar)
        val again = manager.addGoogle(identity.copy(displayName = "Renamed"), jar)
        assertEquals(first.id, again.id)
        assertEquals(2, manager.all.value.size)
        assertEquals("Renamed", manager.get(first.id)!!.displayName)
        assertEquals(first.createdAt, again.createdAt)
    }

    @Test fun dataIsSeparatedPerAccountAndSwitchingRemembersTheChoice() {
        val g = manager.addGoogle(identity, jar)
        SubscriptionRepository(db, g.id).subscribe("UC1", "One", null, Instant.fromEpochMilliseconds(1))
        assertEquals(1, SubscriptionRepository(db, g.id).all().size)
        assertEquals(0, SubscriptionRepository(db, LOCAL_ACCOUNT).all().size)
        manager.switchTo(LOCAL_ACCOUNT)
        assertEquals(LOCAL_ACCOUNT, AccountManager(db, credentials).active.value.id, "a new manager restores the stored choice")
    }

    @Test fun signOutDeletesCredentialsKeepsTheAccountAndItsData() {
        val g = manager.addGoogle(identity, jar)
        SubscriptionRepository(db, g.id).subscribe("UC1", "One", null, Instant.fromEpochMilliseconds(1))
        manager.signOut(g.id)
        assertNull(manager.cookies(g.id))
        assertTrue(secrets.keys().isEmpty())
        assertEquals(AuthState.NEEDS_RELOGIN, manager.get(g.id)!!.authState)
        assertEquals(1, SubscriptionRepository(db, g.id).all().size)
    }

    @Test fun removeDeletesCredentialsAndEverythingTheAccountOwned() {
        val g = manager.addGoogle(identity, jar)
        SubscriptionRepository(db, g.id).subscribe("UC1", "One", null, Instant.fromEpochMilliseconds(1))
        SearchHistoryRepository(db, g.id).add("cats", Instant.fromEpochMilliseconds(1))
        val playlists = PlaylistRepository(db, g.id)
        playlists.create("Mine", Instant.fromEpochMilliseconds(1))
        manager.remove(g.id)
        assertNull(manager.get(g.id))
        assertEquals(LOCAL_ACCOUNT, manager.active.value.id)
        assertTrue(secrets.keys().isEmpty())
        assertTrue(SubscriptionRepository(db, g.id).all().isEmpty())
        assertTrue(SearchHistoryRepository(db, g.id).recent().isEmpty())
        assertTrue(db.playlistQueries.selectForAccount(g.id).executeAsList().isEmpty())
    }

    @Test fun localAccountCannotBeRemovedOrSignedOut() {
        manager.remove(LOCAL_ACCOUNT); manager.signOut(LOCAL_ACCOUNT)
        assertEquals(LOCAL_ACCOUNT, manager.active.value.id)
        assertEquals(AuthState.OK, manager.get(LOCAL_ACCOUNT)!!.authState)
    }

    @Test fun signOutEverywhereWipesAllCredentialsAndReturnsToLocal() {
        manager.addGoogle(identity, jar)
        manager.addGoogle(identity.copy(channelId = "UCother", email = "o@x.com"), jar)
        manager.signOutEverywhere()
        assertTrue(secrets.keys().isEmpty())
        assertEquals(LOCAL_ACCOUNT, manager.active.value.id)
        assertTrue(manager.all.value.filter { it.kind == AccountKind.GOOGLE }.all { it.authState == AuthState.NEEDS_RELOGIN })
    }
}
