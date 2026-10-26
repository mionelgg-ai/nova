package app.nova.vpn.data

import java.time.LocalDate

/** Gregorian -> Jalali (Persian) calendar, no library needed. */
object Jalali {
    fun fromGregorian(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gdm[gm - 1]
        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + days / 31
            jd = 1 + days % 31
        } else {
            jm = 7 + (days - 186) / 30
            jd = 1 + (days - 186) % 30
        }
        return Triple(jy, jm, jd)
    }

    fun toPersianDigits(s: String): String =
        s.map { if (it in '0'..'9') ('۰' + (it - '0')) else it }.joinToString("")

    /** "2026-12-31" -> "۱۴۰۵/۱۰/۱۰" when [fa], otherwise unchanged. */
    fun display(iso: String, fa: Boolean): String {
        if (!fa) return iso
        val d = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
        val (y, m, day) = fromGregorian(d.year, d.monthValue, d.dayOfMonth)
        return toPersianDigits("%04d/%02d/%02d".format(java.util.Locale.ENGLISH, y, m, day))
    }
}
