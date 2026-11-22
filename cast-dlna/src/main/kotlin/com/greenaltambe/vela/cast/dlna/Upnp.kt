package com.greenaltambe.vela.cast.dlna

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** A UPnP media renderer found on the network, with the address of its AVTransport control endpoint. */
data class DlnaRenderer(val udn: String, val name: String, val avTransportControlUrl: String)

/** HTTP as the UPnP code needs it; a seam so tests need no network. */
interface UpnpHttp {
    fun get(url: String): String
    /** POSTs a SOAP body; returns the response body, or throws [UpnpException] with the SOAP fault / HTTP status. */
    fun postSoap(url: String, soapAction: String, body: String): String
}

class UpnpException(message: String) : Exception(message)

object JdkUpnpHttp : UpnpHttp {
    override fun get(url: String): String = open(url).run {
        connectTimeout = 3000; readTimeout = 4000
        try { inputStream.use { it.readBytes().toString(Charsets.UTF_8) } } finally { disconnect() }
    }

    override fun postSoap(url: String, soapAction: String, body: String): String = open(url).run {
        connectTimeout = 3000; readTimeout = 6000
        requestMethod = "POST"; doOutput = true
        setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
        setRequestProperty("SOAPACTION", "\"$soapAction\"")
        try {
            outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            if (responseCode in 200..299) inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            else throw UpnpException("HTTP $responseCode ${(errorStream?.use { it.readBytes().toString(Charsets.UTF_8) } ?: "").take(200)}")
        } finally { disconnect() }
    }

    private fun open(url: String) = URL(url).openConnection() as HttpURLConnection
}

internal fun parseXml(xml: String): org.w3c.dom.Document =
    DocumentBuilderFactory.newInstance().apply {
        // The description comes from the network: no DTDs, no external entities.
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        isNamespaceAware = false
    }.newDocumentBuilder().parse(xml.byteInputStream())

private fun Element.text(tag: String): String? = getElementsByTagName(tag).item(0)?.textContent?.trim()

/** Reads a device description document; null when the device has no AVTransport service (not something to cast to). */
fun parseDeviceDescription(xml: String, location: String): DlnaRenderer? {
    val doc = parseXml(xml).documentElement
    val device = doc.getElementsByTagName("device").item(0) as? Element ?: return null
    val name = device.text("friendlyName") ?: return null
    val udn = device.text("UDN") ?: location
    val base = doc.text("URLBase") ?: location
    val services = doc.getElementsByTagName("service")
    for (i in 0 until services.length) {
        val s = services.item(i) as? Element ?: continue
        if (s.text("serviceType")?.contains("AVTransport", ignoreCase = true) == true) {
            val control = s.text("controlURL") ?: continue
            return DlnaRenderer(udn, name, URI(base).resolve(control).toString())
        }
    }
    return null
}

internal fun xmlEscape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")

private const val AVT = "urn:schemas-upnp-org:service:AVTransport:1"

/** The AVTransport actions Vela uses. Every call is blocking; callers run it off the main thread. */
class AvTransport(private val controlUrl: String, private val http: UpnpHttp) {
    private fun call(action: String, vararg args: Pair<String, String>): org.w3c.dom.Document {
        val params = (listOf("InstanceID" to "0") + args).joinToString("") { (k, v) -> "<$k>${xmlEscape(v)}</$k>" }
        val body = "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
            "<s:Body><u:$action xmlns:u=\"$AVT\">$params</u:$action></s:Body></s:Envelope>"
        return parseXml(http.postSoap(controlUrl, "$AVT#$action", body))
    }

    fun setUri(url: String, title: String, mime: String, isVideo: Boolean) {
        val klass = if (isVideo) "object.item.videoItem" else "object.item.audioItem.musicTrack"
        val didl = "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
            "<item id=\"0\" parentID=\"-1\" restricted=\"1\"><dc:title>${xmlEscape(title)}</dc:title><upnp:class>$klass</upnp:class>" +
            "<res protocolInfo=\"http-get:*:$mime:*\">${xmlEscape(url)}</res></item></DIDL-Lite>"
        call("SetAVTransportURI", "CurrentURI" to url, "CurrentURIMetaData" to didl)
    }

    fun play() { call("Play", "Speed" to "1") }
    fun pause() { call("Pause") }
    fun stop() { call("Stop") }
    fun seek(positionMs: Long) { call("Seek", "Unit" to "REL_TIME", "Target" to formatTime(positionMs)) }

    /** Transport state ("PLAYING", "PAUSED_PLAYBACK", "STOPPED", "TRANSITIONING", "NO_MEDIA_PRESENT") */
    fun state(): String = call("GetTransportInfo").documentElement.text("CurrentTransportState") ?: "UNKNOWN"

    /** Position and duration in milliseconds; null parts when the device reports none ("NOT_IMPLEMENTED"). */
    fun position(): Pair<Long?, Long?> = call("GetPositionInfo").documentElement.let { parseTime(it.text("RelTime")) to parseTime(it.text("TrackDuration")) }
}

fun formatTime(ms: Long): String { val s = ms.coerceAtLeast(0) / 1000; return "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) }

fun parseTime(t: String?): Long? {
    val parts = t?.substringBefore('.')?.split(':')?.mapNotNull { it.toLongOrNull() } ?: return null
    if (parts.size != 3) return null
    return (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
}
