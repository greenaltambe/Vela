@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import com.greenaltambe.vela.ui.nav.Route
import org.junit.Test
import kotlin.test.assertTrue

/**
 * The layout rules as assertions, so a regression is caught without anyone looking: for every key screen at every size,
 * nothing is wider than the window, no text overflows its width or is smaller than 12 sp, everything tappable is at least
 * 48 dp, card titles keep at least 120 dp, and the bottom-bar labels are whole. A violation is reported with its
 * screen, size and element, and all of them are listed at once.
 */
class LayoutInvariantsTest {
    private val routes: List<Pair<String, (Fixture) -> Unit>> = listOf(
        "home" to { _ -> },
        "search" to { f -> f.navigator.push(Route.Search("home server")) },
        "search-start" to { f -> f.navigator.push(Route.Search()) },
        "video" to { f -> f.navigator.push(Route.Video(FakeData.videos[0])) },
        "subscriptions" to { f -> f.navigator.selectTab(Route.Subscriptions) },
        "library" to { f -> f.navigator.selectTab(Route.Library) },
        "settings" to { f -> f.navigator.selectTab(Route.Settings) },
        "accounts" to { f -> f.navigator.selectTab(Route.Settings); f.navigator.push(Route.Accounts) },
        "downloads" to { f -> f.navigator.selectTab(Route.Library); f.navigator.push(Route.Downloads) },
        "channel" to { f -> f.navigator.push(Route.Channel("UC1", FakeData.longChannel)) },
        "history" to { f -> f.navigator.selectTab(Route.Library); f.navigator.push(Route.History) },
    )

    private fun insidePlayer(n: SemanticsNode): Boolean {
        // Text on the video, or on a badge, sits on its own background rather than the page.
        var p: SemanticsNode? = n
        while (p != null) { val t = p.config.getOrNull(SemanticsProperties.TestTag); if (t?.startsWith("player.") == true || t == "card.thumbnail") return true; p = p.parent }
        return false
    }

    private fun luminance(c: Color): Float {
        fun ch(v: Float) = if (v <= 0.03928f) v / 12.92f else Math.pow(((v + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
        return 0.2126f * ch(c.red) + 0.7152f * ch(c.green) + 0.0722f * ch(c.blue)
    }

    private fun contrast(a: Color, b: Color): Float {
        val la = luminance(a); val lb = luminance(b)
        return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
    }

    private val pageBackground get() = if (black) Color.Black else Color.White

    private fun walk(node: SemanticsNode, visit: (SemanticsNode) -> Unit) { visit(node); node.children.forEach { walk(it, visit) } }

    private var black = false

    private fun check(screen: Screen, name: String, pureBlack: Boolean, setup: (Fixture) -> Unit): List<String> {
        black = pureBlack
        val problems = mutableListOf<String>()
        runDesktopComposeUiTest(screen.width, screen.height) {
            val f = buildFixture(pureBlack)
            setup(f)
            setContent { AppUnderTest(f) }
            waitForIdle()
            mainClock.advanceTimeBy(600)
            waitForIdle()
            val where = "$name @ ${screen.name}${if (pureBlack) " black" else ""}"
            fun tag(n: SemanticsNode) = n.config.getOrNull(SemanticsProperties.TestTag)

            // Merged tree: what a user can touch.
            walk(onRoot().fetchSemanticsNode()) { n ->
                val b = n.boundsInRoot
                if (b.width <= 0f || b.height <= 0f) return@walk
                if (b.right > screen.width + 1.5f || b.left < -1.5f) problems += "$where: ${tag(n) ?: n.config} extends outside the window horizontally (${b.left}..${b.right} of ${screen.width})"
                // Whole-row and whole-card clickables are judged by height (and cards by their content); controls by both sides.
                val tappable = n.config.getOrNull(SemanticsActions.OnClick) != null && tag(n) != "component.video_card"
                // touchBoundsInRoot includes the padding the minimum-touch-size rule adds around a small control.
                val t = n.touchBoundsInRoot
                if (tappable && (t.height < 47.5f || (t.width < 200f && t.width < 47.5f))) {
                    problems += "$where: tappable ${tag(n) ?: n.config.getOrNull(SemanticsProperties.ContentDescription)} touch area is ${t.width.toInt()}x${t.height.toInt()}dp (drawn ${b.width.toInt()}x${b.height.toInt()}), below 48"
                }
                if (tag(n) == "card.title" && b.width < 120f) problems += "$where: a card title is only ${b.width.toInt()}dp wide (needs 120)"
            }
            // Unmerged tree: every Text on its own.
            walk(onRoot(useUnmergedTree = true).fetchSemanticsNode()) { n ->
                val results = mutableListOf<TextLayoutResult>()
                n.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
                results.forEach { r ->
                    val text = r.layoutInput.text.text
                    // A line wider than the box it was laid out in (an unbreakable word, a no-wrap label): ellipsized lines are fine.
                    val widest = (0 until r.lineCount).filter { !r.isLineEllipsized(it) }.maxOfOrNull { r.getLineRight(it) - r.getLineLeft(it) } ?: 0f
                    val overflows = widest > r.size.width + 0.5f
                    if (overflows) problems += "$where: text overflows its width: \"${text.take(40)}\""
                    val size = r.layoutInput.style.fontSize
                    if (size.isSpecified && size.value < 12f) problems += "$where: text ${size.value}sp is below 12sp: \"${text.take(40)}\""
                    // Readable against the page: not a text colour that is the background's own (black on Pure Black).
                    val color = r.layoutInput.style.color
                    if (color.isSpecified && !insidePlayer(n) && contrast(color, pageBackground) < 3f) {
                        problems += "$where: text \"${text.take(30)}\" has contrast ${"%.1f".format(contrast(color, pageBackground))}:1 against the page (needs 3:1)"
                    }
                    if (tag(n) == "nav.label" && (r.lineCount != 1 || overflows || r.isLineEllipsized(0))) problems += "$where: nav label \"$text\" is clipped"
                }
            }
            f.dir.deleteRecursively()
        }
        return problems
    }

    private fun runAll(pureBlack: Boolean, sizes: List<Screen>): List<String> =
        sizes.flatMap { s -> routes.flatMap { (name, setup) -> check(s, name, pureBlack, setup) } }.distinct()

    @Test fun lightLayoutHoldsAtEverySize() {
        val problems = runAll(pureBlack = false, Screens)
        assertTrue(problems.isEmpty(), "${problems.size} layout problems:\n" + problems.joinToString("\n"))
    }

    @Test fun pureBlackLayoutHoldsOnPhoneAndDesktop() {
        val problems = runAll(pureBlack = true, listOf(Screens[0], Screens[1], Screens[4]))
        assertTrue(problems.isEmpty(), "${problems.size} layout problems:\n" + problems.joinToString("\n"))
    }
}
