import os
import tempfile
import unittest
from datetime import datetime, timedelta, timezone

os.environ["HARU_LOCATION_SHARE_SECRET"] = "haru-test-location-secret"

from ai_runtime import _interaction_output_text
from companion_store import (
    CompanionStore,
    due_reminders,
    new_reminder,
    parse_relative_reminder,
    upcoming_reminders,
)
from hazard_service import _page_text
from location_share import (
    decode_location_share,
    encode_location_share,
    purge_expired_locations,
)
from news_service import NewsItem, deduplicate


class RuntimeTests(unittest.TestCase):
    def test_interaction_output_text_prefers_direct_output(self):
        self.assertEqual(
            _interaction_output_text({"output_text": "  HARU OK  "}),
            "HARU OK",
        )

    def test_interaction_output_text_falls_back_to_steps(self):
        payload = {
            "steps": [
                {
                    "type": "model_output",
                    "content": [
                        {"type": "text", "text": "hello"},
                        {"type": "text", "text": "world"},
                    ],
                }
            ]
        }
        self.assertEqual(_interaction_output_text(payload), "hello\nworld")

    def test_news_deduplicate_keeps_newest(self):
        older = NewsItem("Same story", "https://a.example/1", "A", "", 1.0, "x")
        newer = NewsItem("Same story", "https://b.example/2", "B", "", 2.0, "x")
        result = deduplicate([older, newer])
        self.assertEqual(len(result), 1)
        self.assertEqual(result[0].link, "https://b.example/2")

    def test_companion_relative_reminder_parser(self):
        base = 1_700_000_000
        parsed = parse_relative_reminder(
            "remind me in 30 minutes to charge batteries",
            now_ts=base,
        )
        self.assertIsNotNone(parsed)
        text, due_at = parsed
        self.assertEqual(text, "charge batteries")
        self.assertEqual(due_at, base + 30 * 60)

    def test_companion_relative_reminder_rejects_over_30_days(self):
        self.assertIsNone(
            parse_relative_reminder(
                "remind me in 31 days to test",
                now_ts=1_700_000_000,
            )
        )

    def test_companion_due_and_upcoming_reminders(self):
        now = 1_700_000_000
        past = new_reminder("past", now - 10)
        future = new_reminder("future", now + 60)
        self.assertEqual(
            [item["text"] for item in due_reminders([past, future], now_ts=now)],
            ["past"],
        )
        self.assertEqual(
            [item["text"] for item in upcoming_reminders([past, future], now_ts=now)],
            ["future"],
        )

    def test_companion_store_round_trip(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            path = os.path.join(temp_dir, "companion.json")
            store = CompanionStore(path)
            state = {
                "notes": ["  inspect   battery  "],
                "tasks": [{"text": "check props", "done": False}],
                "reminders": [new_reminder("charge radio", 1_800_000_000)],
            }
            store.save(state)
            loaded = store.load()
            self.assertEqual(loaded["notes"], ["inspect battery"])
            self.assertEqual(loaded["tasks"][0]["text"], "check props")
            self.assertEqual(loaded["reminders"][0]["text"], "charge radio")

    def test_hazard_text_extractor_ignores_style_script_and_svg(self):
        raw = """
        <html>
          <style>.secret { display:none; }</style>
          <script>window.bad = true;</script>
          <svg><text>hidden-map-label</text></svg>
          <body>Southwest Monsoon affecting western Luzon.</body>
        </html>
        """
        text = _page_text(raw)
        self.assertEqual(text, "Southwest Monsoon affecting western Luzon.")
        self.assertNotIn("display:none", text)
        self.assertNotIn("window.bad", text)
        self.assertNotIn("hidden-map-label", text)

    def test_location_share_round_trip_and_expiry(self):
        now = datetime(2026, 9, 19, 4, 0, tzinfo=timezone.utc)
        code = encode_location_share(
            "Mom",
            14.5995,
            120.9842,
            12.4,
            1.0,
            now=now,
        )
        decoded = decode_location_share(code, now=now + timedelta(minutes=10))
        self.assertEqual(decoded["name"], "Mom")
        self.assertAlmostEqual(decoded["lat"], 14.5995, places=5)
        self.assertAlmostEqual(decoded["lon"], 120.9842, places=5)
        self.assertAlmostEqual(decoded["accuracy_m"], 12.4, places=1)

        with self.assertRaisesRegex(ValueError, "expired"):
            decode_location_share(code, now=now + timedelta(hours=2))

    def test_location_share_rejects_tampering(self):
        now = datetime(2026, 9, 19, 4, 0, tzinfo=timezone.utc)
        code = encode_location_share("Dad", 14.6, 121.0, 20, 1, now=now)
        payload, signature = code.split(".", 1)
        altered = ("A" if payload[0] != "A" else "B") + payload[1:] + "." + signature
        with self.assertRaises(ValueError):
            decode_location_share(altered, now=now)

    def test_location_share_rejects_invalid_coordinates(self):
        now = datetime(2026, 9, 19, 4, 0, tzinfo=timezone.utc)
        with self.assertRaisesRegex(ValueError, "coordinates"):
            encode_location_share("X", 999, 121, 10, 1, now=now)

    def test_purge_expired_locations_is_defensive(self):
        now = datetime(2026, 9, 19, 4, 0, tzinfo=timezone.utc)
        ts = int(now.timestamp())
        items = [
            {"name": "Valid", "lat": 14.5, "lon": 121.0, "expires": ts + 100},
            {"name": "Expired", "lat": 14.5, "lon": 121.0, "expires": ts - 1},
            {"name": "Bad", "lat": "oops", "lon": 121.0, "expires": ts + 100},
        ]
        result = purge_expired_locations(items, now=now)
        self.assertEqual([item["name"] for item in result], ["Valid"])


if __name__ == "__main__":
    unittest.main()
