package io.haru.assistant.content

import android.text.Html
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

data class AndroidHazardItem(
    val source: String,
    val title: String,
    val summary: String,
    val url: String,
    val issued: String = "",
    val severity: String = "info",
)

data class AndroidHazardBundle(
    val pagasa: List<AndroidHazardItem> = emptyList(),
    val phivolcs: List<AndroidHazardItem> = emptyList(),
    val noah: List<AndroidHazardItem> = emptyList(),
    val error: String = "",
)

class AndroidHazardService {
    suspend fun fetch(): AndroidHazardBundle = withContext(Dispatchers.IO) {
        var error = ""

        val pagasa = runCatching { fetchPagasaSummaries() }.getOrElse {
            error = "PAGASA feed unavailable."
            listOf(
                AndroidHazardItem(
                    source = "DOST-PAGASA",
                    title = "PAGASA advisory unavailable",
                    summary = "Open PAGASA for the latest official weather and warning information.",
                    url = PAGASA_WEATHER,
                )
            )
        }

        val phivolcs = runCatching { fetchPhivolcs() }.getOrElse {
            error = listOf(error, "PHIVOLCS feed unavailable.")
                .filter { it.isNotBlank() }
                .joinToString(" ")
            listOf(
                AndroidHazardItem(
                    source = "DOST-PHIVOLCS",
                    title = "PHIVOLCS feed unavailable",
                    summary = "Open PHIVOLCS for the latest official earthquake bulletins.",
                    url = PHIVOLCS_EQ,
                )
            )
        }

        AndroidHazardBundle(
            pagasa = pagasa.take(1),
            phivolcs = phivolcs.take(3),
            noah = listOf(
                AndroidHazardItem(
                    source = "UP NOAH",
                    title = "Local hazard map",
                    summary = "Check flood, landslide, and storm-surge susceptibility for a location.",
                    url = NOAH_HAZARD,
                )
            ),
            error = error,
        )
    }

    private fun fetchPagasaSummaries(): List<AndroidHazardItem> {
        val current = fetchPagasaCurrentOutlook()
        val weekly = runCatching { fetchPagasaWeeklyOutlook() }.getOrNull()
        return buildList {
            add(current)
            if (weekly != null) add(weekly)
        }
    }

    private fun fetchPagasaCurrentOutlook(): AndroidHazardItem {
        val text = pageText(fetchText(PAGASA_WEATHER))
        val synopsis = between(
            text,
            "Synopsis",
            listOf("TC Information", "Forecast Weather Conditions"),
            420,
        )
        val tcInfo = between(
            text,
            "TC Information",
            listOf("Forecast Weather Conditions", "Forecast Wind"),
            380,
        )
        val issued = Regex(
            "Issued at:\\s*(.{3,80}?)(?=\\s+Synopsis\\b)",
            RegexOption.IGNORE_CASE,
        ).find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()

        val activeTc =
            tcInfo.isNotBlank() &&
                !tcInfo.contains(
                    "no active tropical cyclone",
                    ignoreCase = true,
                )

        val summary = listOf(
            synopsis,
            if (activeTc) tcInfo else "",
        )
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" ")
            .take(720)
            .ifBlank {
                "Open PAGASA for the latest nationwide weather outlook."
            }

        return AndroidHazardItem(
            source = "DOST-PAGASA",
            title = if (activeTc) {
                "Active weather advisory"
            } else {
                "Current weather outlook"
            },
            summary = summary,
            issued = issued,
            url = PAGASA_WEATHER,
            severity = if (activeTc) "warning" else "watch",
        )
    }

    private fun fetchPagasaWeeklyOutlook(): AndroidHazardItem {
        val text = pageText(fetchText(PAGASA_WEEKLY))
        val issued = Regex(
            "(?:Issued at|Valid from|Forecast issued)[:\\s]+(.{3,100}?)(?=\\s{2,}|\\.)",
            RegexOption.IGNORE_CASE,
        ).find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()

        val cleaned = text
            .replace(
                Regex(
                    "(Home|Weather|Climate|Astronomical|Hydrometeorology|" +
                        "Research|About PAGASA|Skip to content)",
                    RegexOption.IGNORE_CASE,
                ),
                " ",
            )
            .replace(Regex("\\s+"), " ")
            .trim()

        val startMarkers = listOf(
            "Weekly Weather Outlook",
            "Weather Outlook",
            "Forecast",
        )
        var body = ""
        for (marker in startMarkers) {
            val index = cleaned.indexOf(marker, ignoreCase = true)
            if (index >= 0) {
                body = cleaned.substring(index + marker.length)
                break
            }
        }

        val summary = body
            .replace(Regex("\\s+"), " ")
            .trim(' ', ':', '-')
            .take(900)
            .ifBlank {
                "Open PAGASA for the latest weekly weather outlook."
            }

        return AndroidHazardItem(
            source = "DOST-PAGASA",
            title = "Weekly weather outlook",
            summary = summary,
            issued = issued,
            url = PAGASA_WEEKLY,
            severity = "info",
        )
    }

    private fun fetchPhivolcs(): List<AndroidHazardItem> {
        val html = fetchText(PHIVOLCS_EQ)
        val rowRegex = Regex(
            "<tr[^>]*>(.*?)</tr>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val cellRegex = Regex(
            "<t[dh][^>]*>(.*?)</t[dh]>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )

        val events = mutableListOf<AndroidHazardItem>()
        for (row in rowRegex.findAll(html)) {
            val cells = cellRegex.findAll(row.groupValues[1])
                .map { pageText(it.groupValues[1]) }
                .toList()

            if (cells.size < 6) continue
            if (!Regex("\\d{1,2}\\s+[A-Za-z]+\\s+\\d{4}").containsMatchIn(cells[0])) {
                continue
            }

            val magnitude = cells[4].replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 0.0
            if (magnitude < 3.0) continue

            events += AndroidHazardItem(
                source = "DOST-PHIVOLCS",
                title = ("M" + cells[4] + " earthquake · " + cells[5]).take(220),
                summary = "Depth " + cells[3] + " km. Check PHIVOLCS for intensities and updates.",
                issued = cells[0].take(90),
                url = PHIVOLCS_EQ,
                severity = when {
                    magnitude >= 5 -> "warning"
                    magnitude >= 4 -> "watch"
                    else -> "info"
                },
            )
            if (events.size >= 3) break
        }

        return if (events.isNotEmpty()) events else listOf(
            AndroidHazardItem(
                source = "DOST-PHIVOLCS",
                title = "Latest earthquake information",
                summary = "Open PHIVOLCS for the latest Philippine earthquake bulletins.",
                url = PHIVOLCS_EQ,
            )
        )
    }

    private fun fetchText(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("User-Agent", "HARU-Android/0.1")

        try {
            require(connection.responseCode in 200..299)
            val output = ByteArrayOutputStreamLimited(2_000_000)
            connection.inputStream.use { input ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                }
            }
            return output.toByteArray().toString(Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    private fun pageText(value: String): String {
        val withoutNoise = value
            .replace(
                Regex(
                    "<(script|style|noscript|svg)[^>]*>.*?</\\1>",
                    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
                ),
                " ",
            )
        return Html.fromHtml(withoutNoise, Html.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun between(
        text: String,
        start: String,
        stops: List<String>,
        limit: Int,
    ): String {
        val startIndex = text.indexOf(start, ignoreCase = true)
        if (startIndex < 0) return ""
        var tail = text.substring(startIndex + start.length)
        val positions = stops.mapNotNull { stop ->
            tail.indexOf(stop, ignoreCase = true).takeIf { it >= 0 }
        }
        if (positions.isNotEmpty()) {
            tail = tail.substring(0, positions.min())
        }
        return tail.replace(Regex("\\s+"), " ").trim(' ', ':', '-').take(limit)
    }

    private class ByteArrayOutputStreamLimited(
        private val maxBytes: Int,
    ) : java.io.ByteArrayOutputStream() {
        override fun write(b: ByteArray, off: Int, len: Int) {
            require(count + len <= maxBytes) { "Response too large." }
            super.write(b, off, len)
        }
    }

    companion object {
        const val PAGASA_WEATHER = "https://bagong.pagasa.dost.gov.ph/weather"
        const val PAGASA_WEEKLY =
            "https://bagong.pagasa.dost.gov.ph/weather/weekly-weather-outlook"
        const val PHIVOLCS_EQ = "https://earthquake.phivolcs.dost.gov.ph/"
        const val NOAH_HAZARD = "https://noah.up.edu.ph/know-your-hazards"
    }
}
