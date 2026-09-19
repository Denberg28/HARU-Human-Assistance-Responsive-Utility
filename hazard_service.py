from __future__ import annotations

from dataclasses import dataclass
from html import unescape
from html.parser import HTMLParser
import re
from urllib.request import Request, urlopen

MAX_HAZARD_BYTES = 2_000_000
USER_AGENT = (
    "HARU/0.3 (+https://github.com/Denberg28/"
    "HARU-Human-Assistance-Responsive-Utility)"
)

PAGASA_WEATHER_URL = "https://bagong.pagasa.dost.gov.ph/weather"
PAGASA_ADVISORY_URL = "https://www.pagasa.dost.gov.ph/weather/weather-advisory"
PAGASA_TC_URL = "https://bagong.pagasa.dost.gov.ph/tropical-cyclone-bulletin-iframe"
PHIVOLCS_EQ_URL = "https://earthquake.phivolcs.dost.gov.ph/"
NOAH_RAIN_URL = "https://noah.up.edu.ph/weather-updates/rainfall-contour"
NOAH_TYPHOON_URL = "https://noah.up.edu.ph/weather-updates/typhoon-track"
NOAH_HAZARD_URL = "https://noah.up.edu.ph/know-your-hazards"


@dataclass(frozen=True)
class HazardEvent:
    source: str
    title: str
    summary: str
    url: str
    issued: str = ""
    severity: str = "info"


class _TextExtractor(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.parts: list[str] = []

    def handle_data(self, data: str) -> None:
        clean = re.sub(r"\s+", " ", unescape(data or "")).strip()
        if clean:
            self.parts.append(clean)

    def text(self) -> str:
        return " ".join(self.parts)


class _TableRowParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__()
        self.rows: list[list[str]] = []
        self._row: list[str] | None = None
        self._cell: list[str] | None = None

    def handle_starttag(self, tag: str, attrs) -> None:
        low = tag.lower()
        if low == "tr":
            self._row = []
        elif low in {"td", "th"} and self._row is not None:
            self._cell = []

    def handle_data(self, data: str) -> None:
        if self._cell is not None:
            clean = re.sub(r"\s+", " ", unescape(data or "")).strip()
            if clean:
                self._cell.append(clean)

    def handle_endtag(self, tag: str) -> None:
        low = tag.lower()
        if low in {"td", "th"} and self._cell is not None:
            if self._row is not None:
                self._row.append(" ".join(self._cell).strip())
            self._cell = None
        elif low == "tr" and self._row is not None:
            if self._row:
                self.rows.append(self._row)
            self._row = None


def _fetch_html(url: str, timeout: int = 8) -> str:
    request = Request(url, headers={"User-Agent": USER_AGENT})
    with urlopen(request, timeout=timeout) as response:
        raw = response.read(MAX_HAZARD_BYTES + 1)
        if len(raw) > MAX_HAZARD_BYTES:
            raise ValueError("Official hazard source returned an unexpectedly large page.")
    return raw.decode("utf-8", errors="replace")


def _page_text(html_text: str) -> str:
    parser = _TextExtractor()
    parser.feed(html_text)
    return re.sub(r"\s+", " ", parser.text()).strip()


def _between(text: str, start: str, stops: tuple[str, ...], limit: int = 520) -> str:
    match = re.search(re.escape(start), text, flags=re.IGNORECASE)
    if not match:
        return ""
    tail = text[match.end():]
    stop_positions = []
    for stop in stops:
        stop_match = re.search(re.escape(stop), tail, flags=re.IGNORECASE)
        if stop_match:
            stop_positions.append(stop_match.start())
    if stop_positions:
        tail = tail[: min(stop_positions)]
    return re.sub(r"\s+", " ", tail).strip(" :-")[:limit]


def pagasa_events() -> list[HazardEvent]:
    events: list[HazardEvent] = []

    weather_html = _fetch_html(PAGASA_WEATHER_URL)
    weather_text = _page_text(weather_html)

    issued_match = re.search(
        r"Issued at:\s*([^S]{3,80}?)(?=\s+Synopsis\b)",
        weather_text,
        flags=re.IGNORECASE,
    )
    issued = issued_match.group(1).strip() if issued_match else ""

    synopsis = _between(
        weather_text,
        "Synopsis",
        ("TC Information", "Forecast Weather Conditions"),
        360,
    )
    tc_info = _between(
        weather_text,
        "TC Information",
        ("Forecast Weather Conditions", "Forecast Wind"),
        520,
    )

    summary_parts = []
    if synopsis:
        summary_parts.append(synopsis)
    if tc_info and "no active tropical cyclone" not in tc_info.lower():
        summary_parts.append(tc_info)

    events.append(
        HazardEvent(
            source="DOST-PAGASA",
            title="Daily weather and hazard outlook",
            summary=" ".join(summary_parts)[:760]
            or "Open the official PAGASA daily weather page for the latest nationwide outlook.",
            issued=issued,
            url=PAGASA_WEATHER_URL,
            severity="watch",
        )
    )

    try:
        advisory_text = _page_text(_fetch_html(PAGASA_ADVISORY_URL))
        advisory = _between(
            advisory_text,
            "Weather Advisory",
            ("We always find ways", "Feedback"),
            620,
        )
        if advisory:
            events.append(
                HazardEvent(
                    source="DOST-PAGASA",
                    title="Weather advisory",
                    summary=advisory,
                    url=PAGASA_ADVISORY_URL,
                    severity=(
                        "info"
                        if "no weather advisory issued" in advisory.lower()
                        else "warning"
                    ),
                )
            )
    except Exception:
        pass

    try:
        tc_text = _page_text(_fetch_html(PAGASA_TC_URL))
        tc_summary = _between(
            tc_text,
            "Tropical Cyclone Bulletin",
            ("We always find ways", "Feedback"),
            620,
        )
        if tc_summary:
            events.append(
                HazardEvent(
                    source="DOST-PAGASA",
                    title="Tropical cyclone bulletin",
                    summary=tc_summary,
                    url=PAGASA_TC_URL,
                    severity=(
                        "info"
                        if "no active tropical cyclone" in tc_summary.lower()
                        else "warning"
                    ),
                )
            )
    except Exception:
        pass

    return events


def phivolcs_earthquakes(limit: int = 6) -> list[HazardEvent]:
    html_text = _fetch_html(PHIVOLCS_EQ_URL)
    parser = _TableRowParser()
    parser.feed(html_text)

    events: list[HazardEvent] = []
    date_pattern = re.compile(
        r"\b\d{1,2}\s+[A-Za-z]+\s+\d{4}\s*-\s*\d{1,2}:\d{2}\s*(?:AM|PM)\b",
        re.IGNORECASE,
    )

    for row in parser.rows:
        if len(row) < 6:
            continue
        if not date_pattern.search(row[0]):
            continue

        date_time = row[0]
        latitude = row[1] if len(row) > 1 else ""
        longitude = row[2] if len(row) > 2 else ""
        depth = row[3] if len(row) > 3 else ""
        magnitude = row[4] if len(row) > 4 else ""
        location = row[5] if len(row) > 5 else ""

        try:
            mag_value = float(re.sub(r"[^0-9.]", "", magnitude))
        except ValueError:
            mag_value = 0.0

        severity = "warning" if mag_value >= 5 else "watch" if mag_value >= 4 else "info"
        events.append(
            HazardEvent(
                source="DOST-PHIVOLCS",
                title=f"M{magnitude} earthquake · {location}",
                summary=(
                    f"Depth {depth} km · Coordinates {latitude}°N, {longitude}°E. "
                    "Check the official bulletin for reported intensities and updates."
                ),
                issued=date_time,
                url=PHIVOLCS_EQ_URL,
                severity=severity,
            )
        )
        if len(events) >= limit:
            break

    if not events:
        events.append(
            HazardEvent(
                source="DOST-PHIVOLCS",
                title="Latest earthquake information",
                summary=(
                    "HARU could not parse the current earthquake table. "
                    "Open the official PHIVOLCS earthquake page for the latest bulletins."
                ),
                url=PHIVOLCS_EQ_URL,
            )
        )

    return events


def noah_resources() -> list[HazardEvent]:
    return [
        HazardEvent(
            source="UP NOAH",
            title="Current rainfall and typhoon context",
            summary=(
                "NOAH combines current accumulated rainfall and typhoon-track layers "
                "with hazard context for situational awareness."
            ),
            url=NOAH_RAIN_URL,
            severity="watch",
        ),
        HazardEvent(
            source="UP NOAH",
            title="Typhoon track layer",
            summary=(
                "Open the NOAH typhoon-track view for current track context sourced "
                "from operational weather providers."
            ),
            url=NOAH_TYPHOON_URL,
            severity="watch",
        ),
        HazardEvent(
            source="UP NOAH",
            title="Know Your Hazards",
            summary=(
                "Point-based flood, landslide, and storm-surge hazard assessment. "
                "Use this as hazard-map context, not as an emergency warning bulletin."
            ),
            url=NOAH_HAZARD_URL,
            severity="info",
        ),
    ]


def fetch_hazard_bundle() -> tuple[
    list[HazardEvent],
    list[HazardEvent],
    list[HazardEvent],
    list[str],
]:
    errors: list[str] = []

    try:
        pagasa = pagasa_events()
    except Exception as exc:
        pagasa = []
        errors.append(f"PAGASA: {exc}")

    try:
        phivolcs = phivolcs_earthquakes()
    except Exception as exc:
        phivolcs = []
        errors.append(f"PHIVOLCS: {exc}")

    noah = noah_resources()
    return pagasa, phivolcs, noah, errors
