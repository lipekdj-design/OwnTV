package tv.own.owntv.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import tv.own.owntv.core.brand.AppIcon
import tv.own.owntv.core.brand.AppIconSwitcher
import tv.own.owntv.core.brand.PixelMark as CorePixelMark
import tv.own.owntv.core.settings.SettingsRepository
import tv.own.owntv.ui.theme.LocalMainFontFamily
import tv.own.owntv.ui.theme.stageAccent

/**
 * The icon colour the launcher shows right now. The in-app logo follows it, not the saved choice, so
 * logo and home-screen icon change together after the restart.
 */
@Composable
fun rememberAppliedIcon(): AppIcon {
    val context = LocalContext.current
    return remember(context) { AppIconSwitcher.applied(context) }
}

/** Settings › Accent-colored logo: the play triangle of every in-app logo in the accent (default on). */
@Composable
private fun accentTriangle(): Color? {
    val settings: SettingsRepository = koinInject()
    val on by settings.brandAccentTriangle.collectAsStateWithLifecycle(initialValue = true)
    return if (on) stageAccent.accent else null
}

/**
 * The icon's flat mark (no shadow, no next card); at 32 dp and below the simpler small drawing. Pixel
 * above 32 dp is its dot matrix ([PixelMark]). With Accent-colored logo on, the play triangle takes the
 * accent: on a card a white triangle is drawn over the card's own and tinted (Pixel has none).
 * [followAccent] false draws the icon as the launcher shows it (the icon pickers).
 */
@Composable
fun BrandMark(icon: AppIcon, size: Dp, modifier: Modifier = Modifier, followAccent: Boolean = true) {
    // DROPZ TV: the DROPZ drop — a square with three round corners and the top-right corner square —
    // in the accent (light purple on dark), whatever icon colour is applied.
    val color = accentTriangle().takeIf { followAccent } ?: DropzLilac
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val r = s / 2f
        drawCircle(color, radius = r, center = Offset(r, r))
        drawRect(color, topLeft = Offset(r, 0f), size = androidx.compose.ui.geometry.Size(r, r))
    }
}

/** DROPZ light purple, the drop's colour on dark surfaces. */
val DropzLilac = Color(0xFFB88AF0)

/**
 * "Pixel": the dot-matrix TV set (core's [CorePixelMark], the same grid as `owntv_mark_pixel`), the dots
 * of the play triangle in [triangle].
 */
@Composable
fun PixelMark(modifier: Modifier = Modifier, triangle: Color = stageAccent.accent) {
    Canvas(modifier) {
        val u = size.minDimension / 100f
        CorePixelMark.dots.forEach { d ->
            val color = when (d.kind) {
                CorePixelMark.Kind.BODY -> PixelBody
                CorePixelMark.Kind.TRIANGLE -> triangle
                CorePixelMark.Kind.SIDE -> PixelSide
            }
            drawCircle(color, CorePixelMark.DOT_RADIUS * u, Offset(d.x * u, d.y * u))
        }
        drawCircle(PixelPower, CorePixelMark.POWER_RADIUS * u, Offset(CorePixelMark.POWER_X * u, CorePixelMark.POWER_Y * u))
    }
}

private val PixelBody = Color(CorePixelMark.BODY)
private val PixelSide = Color(CorePixelMark.SIDE)
private val PixelPower = Color(CorePixelMark.POWER)

/**
 * DROPZ TV wordmark: "dropz" in DM Sans bold (the default font), letter-spacing -0.055 em, with the
 * descriptor "TV" under it, spaced 0.42 em, as in the DROPZ identity guide. [tv] tints the
 * descriptor, [own] the word. Sized from [width].
 */
@Composable
fun Wordmark(width: Dp, modifier: Modifier = Modifier, tv: Color = stageAccent.accent, own: Color = WordmarkCream) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val family = LocalMainFontFamily.current
    val wordSize = with(density) { (width / 2.8f).toSp() }
    val descSize = with(density) { (width / 2.8f * 0.27f).toSp() }
    Column(modifier.width(width)) {
        androidx.compose.foundation.text.BasicText(
            text = BRAND_WORD,
            maxLines = 1,
            softWrap = false,
            style = androidx.compose.ui.text.TextStyle(
                color = own,
                fontFamily = family,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontSize = wordSize,
                letterSpacing = (-0.055).em,
            ),
        )
        androidx.compose.foundation.text.BasicText(
            text = BRAND_DESCRIPTOR,
            maxLines = 1,
            softWrap = false,
            style = androidx.compose.ui.text.TextStyle(
                color = tv,
                fontFamily = family,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                fontSize = descSize,
                letterSpacing = 0.42.em,
            ),
        )
    }
}

private const val BRAND_WORD = "dropz"
private const val BRAND_DESCRIPTOR = "TV"

val WordmarkCream = Color(0xFFFFF6EE)

/**
 * The lockup (Brand tab, C column): the mark, then the wordmark at 2.75 × the mark's width (62 →
 * 170 in the design). Side by side the gap is 0.23 × the mark (14 / 62); [stacked] (the preview pane)
 * puts the mark above with a 0.2 × gap.
 */
@Composable
fun BrandLockup(
    modifier: Modifier = Modifier,
    markSize: Int = 36,
    stacked: Boolean = false,
) {
    val icon = rememberAppliedIcon()
    val parts = @Composable {
        BrandMark(icon, markSize.dp)
        // On a light theme "own" takes the text colour; the cream is for dark surfaces.
        Wordmark((markSize * 2.75f).dp, own = if (tv.own.owntv.ui.theme.LocalStagePalette.current.isLight) tv.own.owntv.ui.theme.StageColors.Text else WordmarkCream)
    }
    if (stacked) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((markSize * 0.2f).dp),
        ) { parts() }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy((markSize * 0.23f).dp),
        ) { parts() }
    }
}
