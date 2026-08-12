package com.kadirjohn.lulse.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Lulse her zaman karanlık bir "ambient interface"dir (spec §5).
 * dynamicColor kapatılır — Material You paleti bu ürünün niyetine uymaz;
 * sabit, kontrollü siyah/kırmızı/glow paleti kullanılır.
 *
 * Status/nav bar renkleri Activity seviyesinde (edge-to-edge) yönetilir.
 */
private val LulseColorScheme = darkColorScheme(
    primary = ReadyGlow,
    onPrimary = Black,
    secondary = MotionEmber,
    onSecondary = Black,
    tertiary = Amber,
    onTertiary = Black,
    background = Black,
    onBackground = GlowWhite,
    surface = NearBlack,
    onSurface = DimWhite,
    surfaceVariant = DeepCharcoal,
    onSurfaceVariant = MutedWhite,
    error = MotionRed,
    onError = Black,
)

@Composable
fun LulseTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = LulseColorScheme,
        typography = LulseTypography,
        content = content,
    )
}