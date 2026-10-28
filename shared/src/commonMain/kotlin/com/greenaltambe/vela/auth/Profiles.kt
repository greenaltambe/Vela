package com.greenaltambe.vela.auth

import com.greenaltambe.vela.core.settings.SettingLayer
import com.greenaltambe.vela.core.settings.SettingsStore

/** A channel (profile) the Google account can act as. [pageId] null is the default (main) one. */
data class AccountProfile(val name: String, val pageId: String?)

/** What is chosen for each account. `verified` means the identity was confirmed by a read before anything else used it. */
data class ChosenProfile(val profile: AccountProfile, val verified: Boolean)

/** One settings row per account: `name <TAB> pageId <TAB> verified`. Absent = the default profile. */
class ProfileStore(private val store: SettingsStore) {
    private fun key(accountId: String) = "profile.$accountId"
    fun chosen(accountId: String): ChosenProfile? = store.get(key(accountId), SettingLayer.Global)?.split('\t')?.takeIf { it.size >= 3 }
        ?.let { ChosenProfile(AccountProfile(it[0], it[1].ifBlank { null }), it[2] == "1") }
    /** The `X-Goog-PageId` to send: only a non-default, chosen profile has one. */
    fun pageId(accountId: String): String? = chosen(accountId)?.profile?.pageId
    fun save(accountId: String, c: ChosenProfile) = store.put(key(accountId), SettingLayer.Global, "${c.profile.name.replace('\t', ' ')}\t${c.profile.pageId.orEmpty()}\t${if (c.verified) 1 else 0}")
    fun clear(accountId: String) = store.remove(key(accountId), SettingLayer.Global)
    /** True while a non-default profile is chosen but not confirmed: no write may go out. */
    fun blocksWrites(accountId: String): Boolean = chosen(accountId)?.let { it.profile.pageId != null && !it.verified } == true
}

/** Profile choice for the active account. Implemented in `:innertube-auth`; every request goes through the account's governor. */
interface AccountProfiles {
    fun current(): ChosenProfile?
    /** The profiles the account offers (a read), or null when it cannot be read. */
    suspend fun list(): List<AccountProfile>?
    /**
     * Chooses [profile] (null = back to the default). A non-default choice is first checked with a read-only call made AS that profile: if it fails or
     * YouTube answers as someone else, the choice is NOT kept and [ActionResult.Failed] says why.
     */
    suspend fun select(profile: AccountProfile?): ActionResult
}
