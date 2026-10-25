package tv.own.owntv.ui.theme

import androidx.compose.ui.graphics.Color
import tv.own.owntv.core.theme.AccentColor
import tv.own.owntv.core.theme.OwnTVPalette

/**
 * Material 3 tonal palette for OwnTV (teal-seeded). NEUTRAL + secondary/tertiary roles are
 * theme-only; the `primary` roles are seeded per [AccentColor] (default teal == these values).
 *
 * Dark uses a near-black background (#040e0b) so the panel colours pop against
 * the deep dark surface while keeping a subtle green undertone.
 *
 * **The values themselves live in core**, in [OwnTVPalette], because the mobile app renders the
 * same product and a second copy of the hex codes would drift. This file only wraps them in
 * Compose's [Color], which core cannot do — it carries the Compose runtime, not `compose-ui`.
 */

// Brand mark color (the OwnTV play logo) — constant.
val AccentCyan = Color(OwnTVPalette.AccentCyan)

// ---------------- DARK — DROPZ graphite (#161616) ----------------
val DarkBackground = Color(0xFF121212) // DROPZ
val DarkSurface = Color(0xFF161616) // DROPZ
val DarkSurfaceContainerLowest = Color(0xFF0E0E0E) // DROPZ
val DarkSurfaceContainerLow = Color(0xFF1A1A1C) // DROPZ
val DarkSurfaceContainer = Color(0xFF1F1E22) // DROPZ
val DarkSurfaceContainerHigh = Color(0xFF27252B) // DROPZ
val DarkSurfaceContainerHighest = Color(0xFF312E36) // DROPZ
val DarkOnSurface = Color(0xFFF5F3F7) // DROPZ
val DarkOnSurfaceVariant = Color(0xFFCAC5D1) // DROPZ
val DarkOutline = Color(0xFF8D8895) // DROPZ
val DarkOutlineVariant = Color(0xFF3B3741) // DROPZ
val DarkSecondary = Color(0xFFD2C1E8) // DROPZ
val DarkOnSecondary = Color(0xFF2F2442) // DROPZ
val DarkSecondaryContainer = Color(0xFF3E3254) // DROPZ
val DarkOnSecondaryContainer = Color(0xFFEEDDFF) // DROPZ
val DarkTertiary = Color(OwnTVPalette.DarkTertiary)
val DarkOnTertiary = Color(OwnTVPalette.DarkOnTertiary)
val DarkTertiaryContainer = Color(OwnTVPalette.DarkTertiaryContainer)
val DarkOnTertiaryContainer = Color(OwnTVPalette.DarkOnTertiaryContainer)
val DarkError = Color(OwnTVPalette.DarkError)

// ---------------- LIGHT (M3 light) ----------------
val LightBackground = Color(0xFFFAF8F4) // DROPZ
val LightSurface = Color(0xFFFFFFFF) // DROPZ
val LightSurfaceContainerLowest = Color(0xFFFFFFFF) // DROPZ
val LightSurfaceContainerLow = Color(0xFFF6F4EF) // DROPZ
val LightSurfaceContainer = Color(0xFFF1EEE8) // DROPZ
val LightSurfaceContainerHigh = Color(0xFFEBE8E1) // DROPZ
val LightSurfaceContainerHighest = Color(0xFFE5E1DA) // DROPZ
val LightOnSurface = Color(0xFF080808) // DROPZ
val LightOnSurfaceVariant = Color(0xFF5A5650) // DROPZ
val LightOutline = Color(OwnTVPalette.LightOutline)
val LightOutlineVariant = Color(OwnTVPalette.LightOutlineVariant)
val LightSecondary = Color(OwnTVPalette.LightSecondary)
val LightOnSecondary = Color(OwnTVPalette.LightOnSecondary)
val LightSecondaryContainer = Color(OwnTVPalette.LightSecondaryContainer)
val LightOnSecondaryContainer = Color(OwnTVPalette.LightOnSecondaryContainer)
val LightTertiary = Color(OwnTVPalette.LightTertiary)
val LightOnTertiary = Color(OwnTVPalette.LightOnTertiary)
val LightTertiaryContainer = Color(OwnTVPalette.LightTertiaryContainer)
val LightOnTertiaryContainer = Color(OwnTVPalette.LightOnTertiaryContainer)
val LightError = Color(OwnTVPalette.LightError)
