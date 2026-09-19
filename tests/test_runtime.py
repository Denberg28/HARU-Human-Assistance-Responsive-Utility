import unittest

from ai_runtime import _interaction_output_text
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


if __name__ == "__main__":
    unittest.main()
