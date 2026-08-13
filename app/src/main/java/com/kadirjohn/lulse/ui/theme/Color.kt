package com.kadirjohn.lulse.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Lulse renk paleti — gerçek siyah tabanlı, premium/karanlık (spec §5).
 *
 * Hareket durumuna göre arka plan gradient bu tonlarla harmanlanır:
 *  - HIGH_MOTION : kırmızı/bordo sıcak gradient
 *  - SETTLING    : kırmızı → siyah yumuşak geçiş
 *  - STILL       : neredeyse siyah + hafif beyaz glow
 *  - NO_PULSE    : siyah + nötr glow
 *  - LOW_CONF    : hafif amber uyarı
 */

// Temel
val Black = Color(0xFF000000)
val NearBlack = Color(0xFF0A0A0C)
val DeepCharcoal = Color(0xFF141417)

// Glow / metin
val GlowWhite = Color(0xFFEDEDED)
val DimWhite = Color(0xFFB8B8BE)
val MutedWhite = Color(0xFF7A7A82)
val FaintWhite = Color(0xFF4A4A52)

// Hareketli durum (sıcak / kırmızı-bordo)
val MotionRed = Color(0xFFD32F2F)
val MotionCrimson = Color(0xFF8E1B1B)
val MotionDeepRed = Color(0xFF5A1212)
val MotionEmber = Color(0xFFB33A2A)

// Sakin / hazır
val StillBlue = Color(0xFF3E4A6B)   // çok hafif soğuk vurgu
val ReadyGlow = Color(0xFFE8EDF2)

// Nabız bulundu (sıcak beyaz kalp vurgusu)
val PulseWarmWhite = Color(0xFFFFF6F0)
val PulseSoftRed = Color(0xFFE0554E)

// Uyarı / düşük güven
val Amber = Color(0xFFE0A23A)
val AmberDim = Color(0xFF8A6A24)

// LOCKED — güven yüksek, yeşil arka plan (kullanıcı UI isteği)
val LockedGreen = Color(0xFF2E6B4A)
val LockedGreenDim = Color(0xFF1A3D2A)
val LockedGreenGlow = Color(0xFF4A9D72)

// No pulse (nötr)
val NeutralGray = Color(0xFF6A6A72)