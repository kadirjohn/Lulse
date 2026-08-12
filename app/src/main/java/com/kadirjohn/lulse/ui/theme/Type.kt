package com.kadirjohn.lulse.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Lulse tipografi — modern sans, büyük, sakin, az (spec §5).
 *
 * V1'de sistem sans-serif (cihazda genelde Roboto / Sans-serif) kullanılır;
 * Inter gibi özel font asset'i ileride eklenebilir (TODO: font asset).
 * Premium his, geniş lineHeight + düşük letterSpacing + nazik weight'ten gelir.
 */
val LulseTypography = Typography(
    // Ana yönlendirme metni ("Yatar pozisyona geçin", "Hazır")
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.5).sp,
    ),
    // İkincil metin ("Telefonu kalbinizin üzerine koyun")
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.1.sp,
    ),
    // Üçüncül/yardımcı küçük metin ("Hareket azalınca ölçüm başlayacak")
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.3.sp,
    ),
    // Büyük BPM sayısı
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Thin,
        fontSize = 88.sp,
        lineHeight = 96.sp,
        letterSpacing = (-1).sp,
    ),
    // BPM alt etiketi ("Tahmini nabız")
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 1.5.sp,
    ),
    // Sinyal kalitesi / güven etiketi
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.8.sp,
    ),
    // Debug panel metni
    bodySmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.sp,
    ),
)