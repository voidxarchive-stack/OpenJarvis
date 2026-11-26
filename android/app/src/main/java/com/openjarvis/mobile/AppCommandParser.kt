package com.openjarvis.mobile

import java.util.Locale

/** Returns an app name only for an explicit local launch command. */
internal fun extractOpenAppTarget(input: String): String? {
    val cleanedInput = input.trim().replace(
        Regex("""^(?:hey\\s+)?jarvis[,:]?\\s+""", RegexOption.IGNORE_CASE),
        "",
    )
    val prefix = Regex(
        """^\s*(?:hey\s+)?(?:jarvis[,:]?\s+)?(?:please\s+)?(?:open|launch|start)\s+(?:the\s+)?(.+?)\s*$""",
        RegexOption.IGNORE_CASE,
    ).find(cleanedInput)
    val suffix = Regex(
        """^\s*(.+?)\s+(?:app\s+)?(?:kholo|khol(?:\s+do)?|open\s+karo|chalao)\s*$""",
        RegexOption.IGNORE_CASE,
    ).find(cleanedInput)

    val target = (prefix?.groupValues?.getOrNull(1) ?: suffix?.groupValues?.getOrNull(1))
        ?.trim()
        ?.replace(Regex("""\s+app$""", RegexOption.IGNORE_CASE), "")
        ?.trim()
        ?: return null
    if (target.isBlank() || target.equals("app", ignoreCase = true)) return null
    return target
}

internal fun normalizeAppSearchValue(value: String): String =
    value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")

/** Matches common spoken aliases (for example, GPay) or a visible launcher label. */
internal fun appLabelMatchesTarget(label: String, packageName: String, requested: String): Boolean {
    val target = normalizeAppSearchValue(requested)
    val normalizedLabel = normalizeAppSearchValue(label)
    val normalizedPackage = normalizeAppSearchValue(packageName)
    if (target.isBlank()) return false

    val aliases = when (target) {
        "gpay", "googlepay" -> setOf("gpay", "googlepay", "nbupaisa")
        "phonepe" -> setOf("phonepe")
        "paytm" -> setOf("paytm")
        "maps", "googlemaps" -> setOf("maps", "googlemaps")
        "youtube", "yt" -> setOf("youtube")
        "whatsapp", "whatsap" -> setOf("whatsapp")
        "chrome" -> setOf("chrome")
        "gmail" -> setOf("gmail")
        else -> setOf(target)
    }
    return aliases.any { alias ->
        normalizedLabel == alias ||
            normalizedLabel.contains(alias) ||
            normalizedPackage.contains(alias)
    }
}
