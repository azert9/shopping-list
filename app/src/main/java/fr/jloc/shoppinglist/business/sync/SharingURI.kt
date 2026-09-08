package fr.jloc.shoppinglist.business.sync

import fr.jloc.shoppinglist.business.PadSyncParams
import kotlin.io.encoding.Base64
import java.net.URLDecoder
import kotlin.math.min

private val BASE64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

data class SharingURI(val padName: String, val syncParams: PadSyncParams) {

    companion object {

        /** @throws IllegalArgumentException if the input is not a valid sharing URI. */
        fun decode(uri: String): SharingURI = try {

            val prefix = "https://shopping-list.jloc.fr/shared#"
            require(uri.startsWith(prefix, ignoreCase = true))
            val fragment = uri.substring(prefix.length)

            val fields = decodeUrlFragment(fragment)

            require(fields.size == 3)

            SharingURI(
                padName = fields[2],
                syncParams = PadSyncParams(
                    url = fields[0],
                    key = PadKey.fromBytes(BASE64.decode(fields[1])),
                ),
            )
        } catch (e: Exception) {
            throw IllegalArgumentException("failed to decode sharing URI", e)
        }
    }

    fun encode(): String = run {
        val fragment = encodeUrlFragment(
            listOf(
                syncParams.url,
                BASE64.encode(syncParams.key.toBytes()),
                padName.substring(0, min(padName.length, 32)),
            )
        )
        "https://shopping-list.jloc.fr/shared#$fragment"
    }

    override fun toString(): String = encode()
}

private fun encodeUrlFragment(fields: List<String>): String = run {

    val out = StringBuilder()

    val allowed = "-._~!$&'()+,;=:@/?".encodeToByteArray() // RFC 3986, '*' reserved as a separator

    var firstField = true
    for (field in fields) {
        if (firstField) {
            firstField = false
        } else {
            out.append('*')
        }
        for (b in field.encodeToByteArray()) {
            if (b in 0x61..0x7a || b in 0x41..0x5A || b in 0x30..0x39 || allowed.contains(b)) {
                out.append(b.toInt().toChar())
            } else {
                out.append("%%%02X".format(b))
            }
        }
    }

    out.toString()
}

private fun decodeUrlFragment(fragment: String): List<String> =
    fragment.split('*').map { encodedField ->
        URLDecoder.decode(encodedField.replace("+", "%2B"), "utf-8")
    }
