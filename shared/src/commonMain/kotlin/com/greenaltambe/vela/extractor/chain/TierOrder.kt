package com.greenaltambe.vela.extractor.chain

import com.greenaltambe.vela.core.settings.AccountFallbackMode

/**
 * The order tiers are tried in, kept as data (a comma list in the `account.tierOrder` setting, overridable by remote
 * config) and validated here. The account is spent last: anonymous tiers are cheaper and cannot flag an account.
 */
object TierOrder {
    val Default: List<TierId> = listOf(TierId.ANONYMOUS, TierId.ALT_CLIENT, TierId.YT_DLP, TierId.AUTHENTICATED)
    const val DefaultText = "ANONYMOUS,ALT_CLIENT,YT_DLP,AUTHENTICATED"

    /** Unknown names are dropped, repeats removed, missing tiers appended in default order. A blank or unusable list gives [Default]. */
    fun parse(text: String): List<TierId> {
        val given = text.split(',').mapNotNull { name -> TierId.entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } }.distinct()
        if (given.isEmpty()) return Default
        return given + Default.filter { it !in given }
    }

    /** Unless the mode is ALWAYS, the account tier goes after every anonymous tier, whatever the list says. */
    fun sanitize(order: List<TierId>, mode: AccountFallbackMode): List<TierId> =
        if (mode == AccountFallbackMode.ALWAYS) listOf(TierId.AUTHENTICATED) + order.filter { it != TierId.AUTHENTICATED }
        else order.filter { it != TierId.AUTHENTICATED } + TierId.AUTHENTICATED
}

/** What the download chain does with the account: nothing, unless the user allowed it (then only after the anonymous tiers failed). */
fun downloadAccountMode(allowed: Boolean): AccountFallbackMode = if (allowed) AccountFallbackMode.ONLY_ON_FAILURE else AccountFallbackMode.NEVER
