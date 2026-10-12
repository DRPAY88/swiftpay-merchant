package com.example.myapplication.util

import android.nfc.tech.IsoDep
import android.util.Log

/**
 * Robust EMV Parser for contactless cards.
 * Implements standard EMV Tag-Length-Value (TLV) parsing and card reading flow.
 */
object EmvParser {
    private const val TAG = "EmvParser"

    private val PPSE_AID = byteArrayOf(
        0x00, 0xA4.toByte(), 0x04, 0x00, 0x0E,
        0x32, 0x50, 0x41, 0x59, 0x2E, 0x53, 0x59, 0x53, 0x2E, 0x44, 0x44, 0x46, 0x30, 0x31, 0x00
    )

    private val KNOWN_AIDS = listOf(
        "A0000000031010", // Visa
        "A0000000041010", // Mastercard
        "A0000003330101", // UnionPay
        "A0000000032010", // Visa Electron
        "A0000000043060", // Maestro
        "A0000000250101", // Amex
        "A0000000651010"  // JCB
    )

    fun readCardData(isoDep: IsoDep, onProgress: ((String) -> Unit)? = null): Triple<String, String, String>? {
        try {
            onProgress?.invoke("Initializing NFC...")
            
            val ppseRes = try { isoDep.transceive(PPSE_AID) } catch (e: Exception) { null }
            val discoveredAids = if (ppseRes != null) parseAidsFromPpse(ppseRes) else emptyList()
            
            val aidsToTry = (discoveredAids + KNOWN_AIDS).distinct()

            for (aidHex in aidsToTry) {
                onProgress?.invoke("Selecting Card Application...")
                val aidBytes = hexToBytes(aidHex)
                val selectCommand = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aidBytes.size.toByte()) + aidBytes + byteArrayOf(0x00)
                val aidRes = try { isoDep.transceive(selectCommand) } catch (e: Exception) { continue }

                if (isSuccess(aidRes)) {
                    val aidResHex = bytesToHex(aidRes)
                    var pan = ""
                    var expiry = ""
                    val label = extractTag(aidResHex, "50")?.let { hexToAscii(it) } ?: "Payment Card"

                    onProgress?.invoke("Establishing Secure Channel...")
                    
                    val gpoAttempts = listOf(
                        byteArrayOf(0x80.toByte(), 0xA8.toByte(), 0x00, 0x00, 0x02, 0x83.toByte(), 0x00, 0x00),
                        byteArrayOf(0x80.toByte(), 0xA8.toByte(), 0x00, 0x00, 0x0B, 0x83.toByte(), 0x09, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
                    )

                    var aflHex = ""
                    for (gpoCmd in gpoAttempts) {
                        val gpoRes = try { isoDep.transceive(gpoCmd) } catch (e: Exception) { null }
                        if (isSuccess(gpoRes)) {
                            val gpoHex = bytesToHex(gpoRes!!)
                            extractTrack2FromTlv(gpoHex)?.let {
                                pan = it.first
                                expiry = it.second
                            }
                            aflHex = extractTag(gpoHex, "94") ?: extractTag(gpoHex, "80")?.let { if (it.length > 4) it.substring(4) else null } ?: ""
                            if (pan.isNotEmpty() || aflHex.isNotEmpty()) break
                        }
                    }

                    if (aflHex.isNotEmpty()) {
                        val records = parseAfl(aflHex)
                        for (rec in records) {
                            onProgress?.invoke("Reading Card Records...")
                            val readRecCmd = byteArrayOf(0x00, 0xB2.toByte(), rec.second.toByte(), ((rec.first shl 3) or 4).toByte(), 0x00)
                            val recRes = try { isoDep.transceive(readRecCmd) } catch (e: Exception) { null }
                            
                            if (isSuccess(recRes)) {
                                val recHex = bytesToHex(recRes!!)
                                extractTrack2FromTlv(recHex)?.let {
                                    if (pan.isEmpty()) pan = it.first
                                    if (expiry.isEmpty()) expiry = it.second
                                }
                                if (pan.isEmpty()) pan = extractTag(recHex, "5A")?.replace("F", "") ?: ""
                                if (expiry.isEmpty()) extractTag(recHex, "5F24")?.let { 
                                    if (it.length >= 4) expiry = "${it.substring(2, 4)}/${it.substring(0, 2)}"
                                }
                            }
                            if (pan.isNotEmpty() && expiry.isNotEmpty()) break
                        }
                    }
                    
                    if (pan.isEmpty()) {
                        for (sfi in 1..2) {
                            for (r in 1..2) {
                                val cmd = byteArrayOf(0x00, 0xB2.toByte(), r.toByte(), ((sfi shl 3) or 4).toByte(), 0x00)
                                val res = try { isoDep.transceive(cmd) } catch (e: Exception) { null }
                                if (isSuccess(res)) {
                                    val h = bytesToHex(res!!)
                                    extractTrack2FromTlv(h)?.let { pan = it.first; expiry = it.second }
                                }
                                if (pan.isNotEmpty()) break
                            }
                            if (pan.isNotEmpty()) break
                        }
                    }

                    if (pan.isNotEmpty()) return Triple(pan, expiry, label)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "EMV Error", e)
        }
        return null
    }

    private fun extractTag(hex: String, target: String): String? {
        var i = 0
        while (i <= hex.length - 2) {
            val startIdx = i
            val firstByte = hex.substring(i, i + 2).toInt(16)
            i += 2
            
            if ((firstByte and 0x1F) == 0x1F) {
                while (i <= hex.length - 2 && (hex.substring(i, i + 2).toInt(16) and 0x80) != 0) i += 2
                i += 2
            }
            if (i > hex.length) break
            val currentTag = hex.substring(startIdx, i)

            if (i > hex.length - 2) break
            var len = hex.substring(i, i + 2).toInt(16)
            i += 2
            if (len >= 0x80) {
                val numBytes = len and 0x7F
                if (i + numBytes * 2 > hex.length) break
                len = hex.substring(i, i + numBytes * 2).toInt(16)
                i += numBytes * 2
            }

            if (i + len * 2 > hex.length) break
            val value = hex.substring(i, i + len * 2)

            if (currentTag.equals(target, ignoreCase = true)) return value

            if ((firstByte and 0x20) != 0) {
                extractTag(value, target)?.let { return it }
            }
            
            i += len * 2
        }
        return null
    }

    private fun extractTrack2FromTlv(hex: String): Pair<String, String>? {
        val t2 = extractTag(hex, "57") ?: return null
        val dIdx = t2.indexOf('D')
        if (dIdx < 13 || dIdx + 4 >= t2.length) return null
        val pan = t2.substring(0, dIdx).replace("F", "")
        val exp = "${t2.substring(dIdx + 3, dIdx + 5)}/${t2.substring(dIdx + 1, dIdx + 3)}"
        return Pair(pan, exp)
    }

    private fun parseAfl(aflHex: String): List<Pair<Int, Int>> {
        val list = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until aflHex.length step 8) {
            if (i + 8 > aflHex.length) break
            val entry = aflHex.substring(i, i + 8)
            val sfi = entry.substring(0, 2).toInt(16) shr 3
            val start = entry.substring(2, 4).toInt(16)
            val end = entry.substring(4, 6).toInt(16)
            for (r in start..end) list.add(Pair(sfi, r))
        }
        return list
    }

    private fun parseAidsFromPpse(ppseRes: ByteArray): List<String> {
        val hex = bytesToHex(ppseRes)
        val aids = mutableListOf<String>()
        var i = 0
        while (i < hex.length - 4) {
            if (hex.substring(i, i + 2) == "4F") {
                val len = hex.substring(i + 2, i + 4).toInt(16)
                if (i + 4 + len * 2 <= hex.length) aids.add(hex.substring(i + 4, i + 4 + len * 2))
                i += 4 + len * 2
            } else i += 2
        }
        return aids
    }

    private fun isSuccess(res: ByteArray?) = res != null && res.size >= 2 && res[res.size - 2] == 0x90.toByte()
    private fun hexToBytes(s: String): ByteArray {
        val clean = if (s.length % 2 != 0) "0$s" else s
        return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
    private fun bytesToHex(b: ByteArray) = b.joinToString("") { "%02X".format(it) }
    private fun hexToAscii(h: String): String {
        return try { 
            val clean = if (h.length % 2 != 0) "0$h" else h
            val sb = StringBuilder()
            for (i in 0 until clean.length step 2) sb.append(clean.substring(i, i + 2).toInt(16).toChar())
            sb.toString().trim()
        } catch (e: Exception) { "Payment Card" }
    }
}
