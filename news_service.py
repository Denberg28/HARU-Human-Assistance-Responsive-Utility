from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from email.utils import parsedate_to_datetime
import html
import re
from urllib.parse import quote_plus
from urllib.request import Request, urlopen
import xml.etree.ElementTree as ET


@dataclass(frozen=True)
class NewsItem:
    title: str
    link: str
    source: str
    published: str
    published_ts: float
    category: str


TOPIC_KEYWORDS = {
    "technology": {"technology", "tech", "ai", "software", "computer", "android", "github", "python"},
    "aerospace": {"uav", "drone", "aircraft", "aviation", "aerospace", "ardupilot", "fpv", "rc plane"},
    "science": {"science", "research", "space", "engineering", "robotics", "energy"},
    "business": {"business", "market", "economy", "finance", "startup", "industry"},
    "weather_disaster": {"weather", "storm", "typhoon", "earthquake", "disaster", "flood", "volcano"},
    "sports": {"sports", "basketball", "football", "tennis", "boxing", "volleyball"},
    "entertainment": {"movie", "music", "game", "gaming", "entertainment", "anime"},
    "health": {"health", "medicine", "fitness", "wellness"},
}

# Keep politics out of inferred personalization. Political/civic news may still appear
# in the general feeds because HARU should not hide major public-interest stories.
PERSONALIZABLE_TOPICS = tuple(TOPIC_KEYWORDS.keys())


def infer_interests(history: list[tuple[str, str]], explicit: dict[str, int] | None = None) -> dict[str, int]:
    scores = {topic: 0 for topic in PERSONALIZABLE_TOPICS}
    explicit = explicit or {}

    for topic, value in explicit.items():
        if topic in scores:
            scores[topic] += max(0, int(value))

    for question, _answer in history[-50:]:
        text = question.lower()
        for topic, keywords in TOPIC_KEYWORDS.items():
            if any(keyword in text for keyword in keywords):
                scores[topic] += 1

    return scores


def top_interests(scores: dict[str, int], limit: int = 2) -> list[str]:
    ranked = sorted(
        ((topic, score) for topic, score in scores.items() if score > 0),
        key=lambda item: (-item[1], item[0]),
    )
    return [topic for topic, _ in ranked[:limit]]


def _published_timestamp(text: str) -> float:
    if not text:
        return 0.0
    try:
        dt = parsedate_to_datetime(text)
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return dt.timestamp()
    except Exception:
        return 0.0


def _clean_title(title: str) -> str:
    return re.sub(r"\s+", " ", html.unescape(title or "")).strip()


def _fetch_rss(url: str, category: str, timeout: int = 8, limit: int = 12) -> list[NewsItem]:
    request = Request(
        url,
        headers={
            "User-Agent": "HARU/0.2 (+https://github.com/Denberg28/HARU-Human-Assistance-Responsive-Utility)"
        },
    )
    with urlopen(request, timeout=timeout) as response:
        xml_bytes = response.read()

    root = ET.fromstring(xml_bytes)
    items: list[NewsItem] = []

    for node in root.findall(".//item")[:limit]:
        title = _clean_title(node.findtext("title", default=""))
        link = (node.findtext("link", default="") or "").strip()
        pub = (node.findtext("pubDate", default="") or "").strip()

        source_node = node.find("source")
        source = _clean_title(source_node.text if source_node is not None else "")
        if not source and " - " in title:
            maybe_title, maybe_source = title.rsplit(" - ", 1)
            if len(maybe_source) < 80:
                title, source = maybe_title, maybe_source

        if not title or not link:
            continue

        items.append(
            NewsItem(
                title=title,
                link=link,
                source=source or "News source",
                published=pub,
                published_ts=_published_timestamp(pub),
                category=category,
            )
        )

    return items


def philippines_headlines(limit: int = 10) -> list[NewsItem]:
    url = "https://news.google.com/rss?hl=en-PH&gl=PH&ceid=PH:en"
    return _fetch_rss(url, "Philippines", limit=limit)


def local_region_headlines(region: str, limit: int = 10) -> list[NewsItem]:
    region = (region or "Philippines").strip()
    if region.lower() == "philippines":
        return philippines_headlines(limit)
    query = quote_plus(f'"{region}" Philippines')
    url = f"https://news.google.com/rss/search?q={query}&hl=en-PH&gl=PH&ceid=PH:en"
    return _fetch_rss(url, f"Local · {region}", limit=limit)


def world_headlines(limit: int = 10) -> list[NewsItem]:
    url = "https://news.google.com/rss/headlines/section/topic/WORLD?hl=en-PH&gl=PH&ceid=PH:en"
    return _fetch_rss(url, "World", limit=limit)


def topic_headlines(topic: str, limit: int = 8) -> list[NewsItem]:
    if topic not in PERSONALIZABLE_TOPICS:
        return []

    query_map = {
        "technology": "technology AI software",
        "aerospace": "aviation aerospace UAV drone",
        "science": "science research engineering robotics",
        "business": "business economy industry",
        "weather_disaster": "Philippines weather typhoon disaster",
        "sports": "sports",
        "entertainment": "entertainment gaming movies",
        "health": "health medicine wellness",
    }
    query = quote_plus(query_map[topic])
    url = f"https://news.google.com/rss/search?q={query}&hl=en-PH&gl=PH&ceid=PH:en"
    return _fetch_rss(url, f"For you · {topic.replace('_', ' ').title()}", limit=limit)


def deduplicate(items: list[NewsItem]) -> list[NewsItem]:
    result: list[NewsItem] = []
    seen: set[str] = set()

    for item in sorted(items, key=lambda x: x.published_ts, reverse=True):
        key = re.sub(r"\W+", "", item.title.lower())[:100]
        if not key or key in seen:
            continue
        seen.add(key)
        result.append(item)

    return result


def friendly_time(item: NewsItem) -> str:
    if item.published_ts <= 0:
        return item.published
    seconds = max(0, datetime.now(tz=timezone.utc).timestamp() - item.published_ts)
    if seconds < 3600:
        return f"{max(1, int(seconds // 60))}m ago"
    if seconds < 86400:
        return f"{int(seconds // 3600)}h ago"
    return f"{int(seconds // 86400)}d ago"
