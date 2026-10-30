package app.nova.vpn.core

object NetUtil {
    private fun ip(a: Int, b: Int, c: Int, d: Int): Long =
        (a.toLong() shl 24) or (b.toLong() shl 16) or (c.toLong() shl 8) or d.toLong()

    /** (start, prefix) of the ranges that stay OUT of the tunnel when "Bypass LAN" is on */
    private val LAN = listOf(
        ip(10, 0, 0, 0) to 8,
        ip(127, 0, 0, 0) to 8,
        ip(169, 254, 0, 0) to 16,
        ip(172, 16, 0, 0) to 12,
        ip(192, 168, 0, 0) to 16,
        ip(224, 0, 0, 0) to 3,
    )

    private fun toIp(a: Long) = "${(a shr 24) and 255}.${(a shr 16) and 255}.${(a shr 8) and 255}.${a and 255}"

    /** 0.0.0.0/0 minus the LAN ranges, as a minimal list of CIDR routes (works on every Android version). */
    fun routesBypassingLan(): List<Pair<String, Int>> {
        val ranges = LAN.map { (s, p) -> s to (s + (1L shl (32 - p)) - 1) }.sortedBy { it.first }
        val out = ArrayList<Pair<String, Int>>()
        var cur = 0L
        for ((s, e) in ranges) {
            if (s > cur) addRange(cur, s - 1, out)
            cur = e + 1
        }
        if (cur <= 0xFFFFFFFFL) addRange(cur, 0xFFFFFFFFL, out)
        return out
    }

    private fun addRange(from: Long, to: Long, out: MutableList<Pair<String, Int>>) {
        var a = from
        while (a <= to) {
            var size = if (a == 0L) 1L shl 32 else (a and -a)
            while (size > to - a + 1) size = size shr 1
            out.add(toIp(a) to (32 - java.lang.Long.numberOfTrailingZeros(size)))
            a += size
        }
    }

    private val V4 = Regex("^(25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}$")

    fun isIpv4(s: String) = V4.matches(s)
    fun isIpLiteral(s: String) = isIpv4(s) || (s.contains(':') && Regex("^[0-9a-fA-F:.]+$").matches(s))
}
