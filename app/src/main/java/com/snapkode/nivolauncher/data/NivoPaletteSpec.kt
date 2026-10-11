package com.snapkode.nivolauncher.data

/** Raw ARGB values for the Nivo palettes. Kept free of Compose types so contrast can be unit-tested. */
data class NivoPaletteSpec(
    val canvas: Long,
    val surface: Long,
    val elevated: Long,
    val highest: Long,
    val textPrimary: Long,
    val textSecondary: Long,
    val hairline: Long,
    val dark: Boolean,
    val outline: Long = textSecondary,
)

object NivoPalettes {
    // Values come from the Nivo design-system tokens sheet (dark: blue seed, light: coral seed).
    val Light = NivoPaletteSpec(
        canvas = 0xFFFFF8F6, surface = 0xFFFFF1ED, elevated = 0xFFFCEAE5, highest = 0xFFF7E4DF,
        textPrimary = 0xFF231917, textSecondary = 0xFF53433F, hairline = 0xFFD8C2BC, dark = false, outline = 0xFF85736E,
    )
    val Dark = NivoPaletteSpec(
        canvas = 0xFF111318, surface = 0xFF191C20, elevated = 0xFF1D2024, highest = 0xFF282A2F,
        textPrimary = 0xFFE2E2E9, textSecondary = 0xFFC4C6D0, hairline = 0xFF2E3036, dark = true, outline = 0xFF8E9099,
    )
    val Amoled = NivoPaletteSpec(
        canvas = 0xFF000000, surface = 0xFF0C0E11, elevated = 0xFF14161A, highest = 0xFF1F2125,
        textPrimary = 0xFFE2E2E9, textSecondary = 0xFFC4C6D0, hairline = 0xFF22252A, dark = true, outline = 0xFF8E9099,
    )

    /** WCAG relative luminance of an ARGB colour. */
    fun luminance(argb: Long): Double {
        fun channel(shift: Int): Double {
            val c = ((argb shr shift) and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }

    /** WCAG contrast ratio between two colours (1..21). */
    fun contrast(a: Long, b: Long): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}
