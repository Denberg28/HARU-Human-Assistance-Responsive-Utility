import unittest
from unittest.mock import patch
from ai_runtime import AiConfig, AiRuntimeError, ask_ai, test_ai
import ai_usage


class AiUsageTests(unittest.TestCase):
    def setUp(self):
        ai_usage._STATES.clear()
        self.config = AiConfig(mode="Cloud", provider="Google Gemini API",
                               model="antigravity-preview-09-2026", api_key="synthetic-test-key")

    @patch("ai_runtime._json_request")
    def test_simple_news_uses_one_grounded_bounded_generation(self, request):
        request.return_value = {"candidates": [{"content": {"parts": [
            {"text": "private thinking", "thought": True}, {"text": "News"}
        ]}, "groundingMetadata": {"groundingChunks": [{"web": {"uri": "https://example.com/news", "title": "Publisher"}}]}}]}
        self.assertEqual(ask_ai(self.config, "Search the web for latest news", enable_native_tools=True), "News\n\nLive sources:\n- Publisher: https://example.com/news")
        self.assertEqual(request.call_count, 1)
        url = request.call_args.args[0]
        payload = request.call_args.kwargs["payload"]
        self.assertIn("generateContent", url)
        self.assertEqual(payload["generationConfig"]["maxOutputTokens"], 1200)
        self.assertEqual(payload["tools"], [{"google_search": {}}])

    @patch("ai_runtime._json_request")
    def test_agent_polls_same_id_and_has_bounded_budget(self, request):
        request.side_effect = [
            {"id": "v1_test", "status": "in_progress"},
            {"id": "v1_test", "status": "completed", "output_text": "Findings"}
        ]
        with patch("ai_runtime.time.sleep"):
            self.assertEqual(ask_ai(self.config, "Deep research this topic", enable_native_tools=True), "Findings")
        self.assertEqual(request.call_count, 2)
        self.assertTrue(request.call_args_list[0].kwargs["payload"]["background"])
        self.assertEqual(request.call_args_list[0].kwargs["payload"]["agent_config"]["max_total_tokens"], 4096)
        self.assertIn("/v1_test?include_input=false", request.call_args_list[1].args[0])

    @patch("ai_runtime._json_request")
    def test_quota_is_not_retried_and_blocks_retest(self, request):
        request.side_effect = AiRuntimeError("Quota", status_code=429, cooldown_s=90)
        with self.assertRaises(AiRuntimeError):
            ask_ai(self.config, "Hello")
        with self.assertRaisesRegex(AiRuntimeError, "cooldown"):
            test_ai(self.config)
        self.assertEqual(request.call_count, 1)

    @patch("ai_runtime._json_request")
    def test_failed_poll_requests_cancel_once(self, request):
        request.side_effect = [
            {"id": "v1_test", "status": "in_progress"},
            AiRuntimeError("Unavailable", status_code=503),
            {"status": "cancelled"},
        ]
        with patch("ai_runtime.time.sleep"):
            with self.assertRaises(AiRuntimeError):
                ask_ai(self.config, "Deep research this topic")
        self.assertEqual(request.call_count, 3)
        self.assertTrue(request.call_args_list[-1].args[0].endswith("/v1_test/cancel"))

    @patch("ai_runtime._json_request")
    def test_incomplete_agent_does_not_continue(self, request):
        request.return_value = {"id": "v1_test", "status": "incomplete", "output_text": "Partial"}
        with self.assertRaisesRegex(AiRuntimeError, "token budget"):
            ask_ai(self.config, "Deep research this topic")
        self.assertEqual(request.call_count, 1)

    @patch("ai_runtime._json_request")
    def test_diagnostics_use_small_agent_budget_and_empty_tools(self, request):
        request.return_value = {"id": "v1_test", "status": "completed", "output_text": "HARU OK"}
        self.assertEqual(test_ai(self.config), "HARU OK")
        payload = request.call_args.kwargs["payload"]
        self.assertEqual(payload["agent_config"]["max_total_tokens"], 512)
        self.assertEqual(payload["tools"], [])

    def test_retry_metadata_is_bounded(self):
        self.assertEqual(ai_usage.cooldown_seconds({}, "NaN"), 60)
        self.assertEqual(ai_usage.cooldown_seconds({}, "99999999"), 86400)
        self.assertEqual(ai_usage.cooldown_seconds({"details": [
            {"@type": "type.googleapis.com/google.rpc.RetryInfo", "retryDelay": "90s"}]}), 90)

    def test_query_coverage_and_cost_routing(self):
        ordinary = ["Translate hello to French", "Explain photosynthesis", "What is electric current?", "Define price elasticity", "Audit this code: a + b", "Solve x squared = 4", "Translate this: latest news", "Write a fictional news report", "Latest news without search"]
        for query in ordinary:
            with self.subTest(query=query):
                self.assertFalse(ai_usage.needs_agent(query))
                self.assertFalse(ai_usage.needs_web_search(query))
        for query in ["Explain news about Saudi fuel", "Latest Saudi news", "What time is flight 5J 325 today?", "Who is the president of France?", "PCAR requirements", "Weather in Cebu"]:
            with self.subTest(query=query):
                self.assertTrue(ai_usage.needs_web_search(query))
        self.assertTrue(ai_usage.needs_web_search("Tell me more", ["Latest news", "What about Cebu?"]))
        self.assertFalse(ai_usage.needs_web_search("Tell me more", ["Explain photosynthesis"]))
        self.assertFalse(ai_usage.needs_agent("Recent HARU conversation:\nUser: Deep research a topic\n\nCurrent user request:\nTranslate hello"))

    @patch("ai_runtime._json_request")
    def test_ordinary_review_is_direct_without_tools_with_reasoning_budget(self, request):
        request.return_value = {"candidates": [{"content": {"parts": [{"text": "Review"}]}}]}
        self.assertEqual(ask_ai(self.config, "Audit this code: a - b"), "Review")
        self.assertEqual(request.call_count, 1)
        payload = request.call_args.kwargs["payload"]
        self.assertNotIn("tools", payload)
        self.assertEqual(payload["generationConfig"]["maxOutputTokens"], 2400)
        self.assertEqual(payload["generationConfig"]["thinkingConfig"]["thinkingLevel"], "low")

    @patch("ai_runtime._json_request")
    def test_supplied_page_uses_retrieval_without_separate_search(self, request):
        request.return_value = {"candidates": [{"content": {"parts": [{"text": "Summary"}]}, "urlContextMetadata": {"urlMetadata": [{"retrievedUrl": "https://example.com/article", "urlRetrievalStatus": "URL_RETRIEVAL_STATUS_SUCCESS"}]}}]}
        reply = ask_ai(self.config, "Summarize https://example.com/article", enable_native_tools=True)
        self.assertIn("Retrieved pages", reply)
        self.assertEqual(request.call_count, 1)
        self.assertEqual(request.call_args.kwargs["payload"]["tools"], [{"url_context": {}}])

    @patch("ai_runtime._json_request")
    def test_missing_sources_show_labeled_response_once(self, request):
        request.return_value = {"candidates": [{"content": {"parts": [{"text": "Unverified"}]}}]}
        reply = ask_ai(self.config, "Latest news", enable_native_tools=True)
        self.assertTrue(reply.startswith("Current facts unverified:"))
        self.assertTrue(reply.endswith("Unverified"))
        self.assertEqual(request.call_count, 1)

    @patch("ai_runtime._json_request")
    def test_unsafe_source_urls_do_not_make_response_appear_grounded(self, request):
        request.return_value = {"candidates": [{"content": {"parts": [{"text": "Useful background"}]},
            "groundingMetadata": {"groundingChunks": [{"web": {"uri": url}} for url in
                ["javascript:alert(1)", "http://example.com/news", "https://user:password@example.com/news"]]}}]}
        reply = ask_ai(self.config, "Latest news", enable_native_tools=True)
        self.assertTrue(reply.startswith("Current facts unverified:"))
        self.assertNotIn("Live sources:", reply)
        self.assertEqual(request.call_count, 1)

    @patch("ai_runtime._json_request")
    def test_other_backends_have_output_caps(self, request):
        cases = [
            ("Ollama", {"message": {"content": "Hello"}}, "options", "num_predict"),
            ("Local OpenAI-compatible", {"choices": [{"message": {"content": "Hello"}}]}, None, "max_tokens"),
            ("OpenAI API", {"output": [{"type": "message", "content": [{"type": "output_text", "text": "Hello"}]}]}, None, "max_output_tokens"),
            ("Anthropic Claude API", {"content": [{"type": "text", "text": "Hello"}]}, None, "max_tokens"),
        ]
        for provider, response, parent, key in cases:
            with self.subTest(provider=provider):
                request.return_value = response
                ask_ai(AiConfig(mode="Cloud", provider=provider, model="synthetic-model", api_key="synthetic-key"), "Hello")
                payload = request.call_args.kwargs["payload"]
                self.assertEqual((payload[parent] if parent else payload)[key], 640)
                self.assertNotIn("tools", payload)

    def test_local_utility_returns_without_ai_call(self):
        import ast
        from pathlib import Path
        parsed = ast.parse(Path("streamlit_app.py").read_text())
        function = next(node for node in parsed.body if isinstance(node, ast.FunctionDef) and node.name == "route_command")
        def must_not_call():
            raise AssertionError("Local utility must not check or call AI")
        scope = {"local_tool_result": lambda _: ("HAPPY", "confirmed local result"), "ai_is_active": must_not_call}
        exec(compile(ast.Module(body=[function], type_ignores=[]), "streamlit_app.py", "exec"), scope)
        for query in ["what time is it", "1 / 0", "add task buy milk", "show reminders"]:
            self.assertEqual(scope["route_command"](query), ("HAPPY", "confirmed local result"))


if __name__ == "__main__":
    unittest.main()
