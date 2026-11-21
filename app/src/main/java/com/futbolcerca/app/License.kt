package com.futbolcerca.app

import android.content.Context
import android.provider.Settings
import android.util.Base64
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Fin de la prueba gratis y fin de la licencia pagada (milisegundos). */
data class LicenseStatus(val trialEnd: Long, val licenseUntil: Long) {
    val until: Long get() = maxOf(trialEnd, licenseUntil)
}

/**
 * Licencias por celular. El código lo firma CodeMarket con su clave privada (generador-licencias.html)
 * y la app solo lleva la clave pública: sin la privada no se puede fabricar un código válido.
 * El código solo sirve para el ID del celular para el que se generó.
 */
object License {
    const val TRIAL_DAYS = 7
    private const val DAY = 24L * 3600_000L
    private const val PUBLIC_KEY = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAELTlxCOop2IQ+LkQZRZsexT3dcH7Coi52dIOeJlovZxmyWBgk1hJ4hn29Xu8jn6/vk7tk04CrUqW3mndQ1+wHbQ=="
    private val AR: TimeZone = TimeZone.getTimeZone("America/Argentina/Buenos_Aires")

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("licencia", Context.MODE_PRIVATE)

    /** ID corto y estable de este celular (ej. 3FA9-12BC-77D0). Se muestra al usuario para pedir la licencia. */
    fun deviceId(ctx: Context): String {
        val raw = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: "desconocido"
        val h = MessageDigest.getInstance("SHA-256").digest(("futbolcerca:" + raw).toByteArray()).joinToString("") { "%02X".format(it) }
        return h.substring(0, 4) + "-" + h.substring(4, 8) + "-" + h.substring(8, 12)
    }

    /** Hora "confiable": reloj del teléfono corregido con la hora del servidor, y nunca hacia atrás. */
    fun now(ctx: Context): Long {
        val p = prefs(ctx)
        val last = p.getLong("last", 0L)
        val t = System.currentTimeMillis() + p.getLong("offset", 0L)
        val v = maxOf(t, last)
        if (v != last) p.edit().putLong("last", v).apply()
        return v
    }

    fun setServerTime(ctx: Context, serverMs: Long) {
        prefs(ctx).edit().putLong("offset", serverMs - System.currentTimeMillis()).putLong("last", serverMs).apply()
    }

    fun cachedTrialStart(ctx: Context): Long = prefs(ctx).getLong("trialStart", 0L)
    fun saveTrialStart(ctx: Context, ms: Long) { prefs(ctx).edit().putLong("trialStart", ms).apply() }
    fun licenseUntil(ctx: Context): Long = prefs(ctx).getLong("until", 0L)
    fun status(ctx: Context, trialStart: Long) = LicenseStatus(trialStart + TRIAL_DAYS * DAY, licenseUntil(ctx))

    private class Activation(val plan: String, val issue: String, val days: Int)

    /** Verifica y activa un código. Devuelve (éxito, mensaje para mostrar). */
    fun activate(ctx: Context, token: String): Pair<Boolean, String> {
        val (a, err) = parse(token, deviceId(ctx))
        if (a == null) return false to err
        val now = now(ctx)
        val issueMs = try { SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = AR }.parse(a.issue)!!.time } catch (_: Exception) { return false to "El código tiene una fecha inválida." }
        if (now < issueMs - 2 * DAY) return false to "La fecha del código es futura. Revisá la fecha de tu celular."
        if (now > issueMs + 15 * DAY) return false to "Este código venció (se puede activar hasta 15 días después de generarlo). Pedí uno nuevo."
        val p = prefs(ctx)
        if (a.issue <= (p.getString("lastIssue", "") ?: "")) return false to "Este código ya fue usado."
        val start = maxOf(now, p.getLong("until", 0L))
        val until = start + a.days * DAY
        p.edit().putLong("until", until).putString("lastIssue", a.issue).apply()
        return true to "¡Licencia ${if (a.plan == "M") "mensual" else "trimestral"} activada! Vence el ${formatDate(until)}."
    }

    private fun parse(token: String, dev: String): Pair<Activation?, String> {
        val bad = "El código no es válido. Copialo completo, tal como te lo enviaron."
        val raw = try { Base64.decode(token.filter { !it.isWhitespace() }, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP) } catch (_: Exception) { return null to bad }
        if (raw.size < 70) return null to bad
        val len = raw[0].toInt() and 0xFF
        if (raw.size != 1 + len + 64) return null to bad
        val payload = raw.copyOfRange(1, 1 + len)
        val sig = raw.copyOfRange(1 + len, raw.size)
        val ok = try {
            val pub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.decode(PUBLIC_KEY, Base64.DEFAULT)))
            val s = Signature.getInstance("SHA256withECDSA")
            s.initVerify(pub); s.update(payload); s.verify(p1363ToDer(sig))
        } catch (_: Exception) { false }
        if (!ok) return null to bad
        val parts = String(payload, Charsets.US_ASCII).split("|")
        if (parts.size != 4 || parts[0] != "FC1") return null to bad
        if (parts[1] != dev) return null to "Este código es de otro celular. El ID de este celular es $dev."
        val days = when (parts[2]) { "M" -> 30; "T" -> 90; else -> return null to bad }
        return Activation(parts[2], parts[3], days) to ""
    }

    /** La firma viene como r||s (64 bytes); Java la espera en formato DER. */
    private fun p1363ToDer(sig: ByteArray): ByteArray {
        fun asInt(b: ByteArray): ByteArray {
            var i = 0
            while (i < b.size - 1 && b[i].toInt() == 0) i++
            var v = b.copyOfRange(i, b.size)
            if (v[0].toInt() and 0x80 != 0) v = byteArrayOf(0) + v
            return byteArrayOf(0x02, v.size.toByte()) + v
        }
        val r = asInt(sig.copyOfRange(0, 32)); val s = asInt(sig.copyOfRange(32, 64))
        return byteArrayOf(0x30, (r.size + s.size).toByte()) + r + s
    }

    fun remaining(ms: Long): String {
        if (ms <= 0) return "0 d 0 h"
        val d = ms / DAY; val h = (ms % DAY) / 3600_000L
        return "$d d $h h"
    }
    fun formatDate(ms: Long): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).apply { timeZone = AR }.format(Date(ms))
}
