import copy
from html import escape
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location("sync_ai_models", Path(__file__).with_name("sync-ai-models.py"))
sync = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(sync)
FIXTURES = Path(__file__).with_name("fixtures") / "ai-models"


def table(*values):
    return "<table><tr>" + "".join(f"<td>{value}</td>" for value in values) + "</tr></table>"


PAGES = {
    "DEEP_SEEK": table("MODEL", "deepseek-flash", "deepseek-v4-pro", "BASE URL", "https://api.deepseek.com")
                 + "<p>Legacy name: deepseek-v4-flash</p>",
    "OPEN_AI": "".join(f'<a href="/api/docs/models/{name}">{name}</a>' for name in
                       ["gpt-6-astra", "gpt-5.6-sol", "gpt-image-2.5-sunburst", "gpt-image-2.5-flare",
                        "gpt-5.6-cyber", "gpt-realtime-2", "gpt-5-pro", "gpt-5-codex", "gpt-4o-mini-tts"]),
    "QWEN": "<h2>文本生成</h2><code>qwen3.8-max</code><code>qwen3.7-plus</code><code>qwen3.8-flash</code>"
            "<h2>图像与视频</h2><code>qwen-image-3.0-pro</code><code>qwen3.7-text-embedding</code>",
    "GOOGLE_GEMINI": table("Model", "Endpoint", "gemini-3.8-flash", "gemini-3.1-flash-image",
                           "gemini-3.1-flash-lite-image", "gemini-3.5-transcribe", "gemini-embedding-001",
                           "gemini-omni-1.1-flash", "gemini-3.1-flash-live-preview")
                     + table("Model", "Endpoint", "(Shut down)", "gemini-3.1-flash-lite-preview"),
}


def fetch_fixture(url):
    return PAGES[next(name for name, source in sync.SOURCES.items() if source == url)]


class SyncAiModelsTest(unittest.TestCase):
    def setUp(self):
        self.seed = sync.read_catalog(sync.SEED)

    def test_only_extracts_supported_capabilities_from_active_sections(self):
        expected = {
            "DEEP_SEEK": ["deepseek-flash", "deepseek-v4-pro"],
            "OPEN_AI": ["gpt-6-astra", "gpt-5.6-sol", "gpt-image-2.5-sunburst", "gpt-image-2.5-flare"],
            "QWEN": ["qwen3.8-max", "qwen3.7-plus", "qwen3.8-flash"],
            "GOOGLE_GEMINI": ["gemini-3.8-flash", "gemini-3.1-flash-image", "gemini-3.1-flash-lite-image"],
        }
        for provider in sync.SOURCES:
            with self.subTest(provider=provider):
                models = sync.extract_models(provider, PAGES[provider])
                self.assertEqual(expected[provider], [model["name"] for model in models])
                for model in models:
                    self.assertEqual(["IMAGE_GENERATION" if "image" in model["name"] else "TEXT"], model["capabilities"])

    def test_rejects_empty_or_changed_pages(self):
        for provider in sync.SOURCES:
            with self.subTest(provider=provider), self.assertRaises((ValueError, StopIteration)):
                sync.extract_models(provider, "<html><script>gpt-6-astra</script>Access denied</html>")

    def test_current_qwen_cards_only_supply_supported_text_models(self):
        html = (FIXTURES / "qwen-models.html").read_text(encoding="utf-8")
        models = sync.extract_models("QWEN", html)
        self.assertEqual([
            {"name": "qwen3.7-plus", "capabilities": ["TEXT"]},
            {"name": "qwen3.8-flash", "capabilities": ["TEXT"]},
        ], models)

    def test_qwen_section_uses_heading_levels_and_ignores_navigation_and_hidden_text(self):
        html = ('<nav>文本生成<code>qwen99-max</code></nav>'
                '<h3><span>文本</span>生成</h3><h4>推荐模型</h4><code>qwen3.8-flash</code>'
                '<script>qwen98-max</script><style>qwen97-max</style>')
        for boundary in ('<h3>其他模型</h3>', '<h2>视觉</h2>', '<h1>更多</h1>'):
            with self.subTest(boundary=boundary):
                models = sync.extract_models("QWEN", html + boundary + '<code>qwen96-max</code>')
                self.assertEqual([{"name": "qwen3.8-flash", "capabilities": ["TEXT"]}], models)

    def test_current_gemini_markdoc_and_html_wrapper_supply_active_models(self):
        document = (FIXTURES / "gemini-models.markdoc").read_text(encoding="utf-8")
        expected = [
            {"name": "gemini-3.8-flash", "capabilities": ["TEXT"]},
            {"name": "gemini-3.1-flash-image", "capabilities": ["IMAGE_GENERATION"]},
            {"name": "gemini-3.1-flash-lite-image", "capabilities": ["IMAGE_GENERATION"]},
        ]
        for wrapped in (False, True):
            with self.subTest(wrapped=wrapped):
                page = '<pre>' + escape(document) + '</pre>' if wrapped else document
                self.assertEqual(expected, sync.extract_models("GOOGLE_GEMINI", page))

    def test_gemini_mixed_tables_filter_retired_rows_without_discarding_active_rows(self):
        for status in ("Shut down", "SHUT DOWN", "Deprecated", "deprecated"):
            with self.subTest(status=status):
                html = ('<table><tr><th>Model</th><th>Endpoint</th></tr>'
                        '<tr><td>Flash</td><td><code>gemini-3.8-flash</code></td></tr>'
                        f'<tr><td>Old <b>{status}</b></td><td>gemini-2.0-flash</td></tr>'
                        '<tr><td>Image</td><td>gemini-3.1-flash-image</td></tr></table>')
                document = ('{% table variant="model" %}\n* Model\n* Endpoint\n---\n'
                            '* Flash\n* `gemini-3.8-flash`\n---\n'
                            f'* Old ({status})\n* `gemini-2.0-flash`\n---\n'
                            '* Image\n* `gemini-3.1-flash-image`\n{% /table %}')
                expected = [
                    {"name": "gemini-3.8-flash", "capabilities": ["TEXT"]},
                    {"name": "gemini-3.1-flash-image", "capabilities": ["IMAGE_GENERATION"]},
                ]
                self.assertEqual(expected, sync.extract_models("GOOGLE_GEMINI", html))
                self.assertEqual(expected, sync.extract_models("GOOGLE_GEMINI", document))

    def test_gemini_ignores_model_mentions_outside_endpoint_tables(self):
        document = (FIXTURES / "gemini-models.markdoc").read_text(encoding="utf-8")
        for invalid in (document.replace('* Endpoint', '* Example'),
                        document.replace('{% /table %}', ''),
                        '<script>' + document + '</script>',
                        '<p>`gemini-3.8-flash` and `gemini-3.1-flash-image`</p>'):
            with self.subTest(page=invalid), self.assertRaises(ValueError):
                sync.extract_models("GOOGLE_GEMINI", invalid)

    def test_current_catalog_formats_refresh_successfully_and_preserve_previous_models(self):
        pages = dict(PAGES,
                     QWEN=(FIXTURES / "qwen-models.html").read_text(encoding="utf-8"),
                     GOOGLE_GEMINI='<pre>' + escape(
                         (FIXTURES / "gemini-models.markdoc").read_text(encoding="utf-8")) + '</pre>')
        updated, errors = sync.refresh(
            self.seed, lambda url: pages[next(name for name, source in sync.SOURCES.items() if source == url)])
        self.assertEqual([], errors)
        for before, after in zip(self.seed["providers"], updated["providers"]):
            self.assertTrue(set(m["name"] for m in before["models"]).issubset(m["name"] for m in after["models"]))

    def test_merges_new_models_preserving_old_entries_and_failed_providers(self):
        original = copy.deepcopy(self.seed)

        def fetch(url):
            if url == sync.SOURCES["QWEN"]:
                raise TimeoutError("timeout")
            return fetch_fixture(url).replace("gpt-6-astra", "gpt-99-astra")

        updated, errors = sync.refresh(self.seed, fetch)
        self.assertEqual(original, self.seed)
        self.assertEqual(1, len(errors))
        self.assertIn("QWEN", errors[0])
        for before, after in zip(original["providers"], updated["providers"]):
            if before["name"] == "QWEN":
                self.assertEqual(before, after)
            else:
                self.assertTrue(set(m["name"] for m in before["models"]).issubset(m["name"] for m in after["models"]))
            if before["name"] == "OPEN_AI":
                self.assertEqual("gpt-99-astra", after["models"][0]["name"])

    def test_all_failures_leave_data_unchanged(self):
        updated, errors = sync.refresh(self.seed, lambda _: "<html>unavailable</html>")
        self.assertEqual(self.seed, updated)
        self.assertEqual(4, len(errors))

    def test_sync_preserves_retired_status_when_model_is_listed_again(self):
        provider = next(p for p in self.seed["providers"] if p["name"] == "OPEN_AI")
        model = next(m for m in provider["models"] if m["name"] == "gpt-6-astra")
        model.update(retired=True, retirementSource="https://developers.openai.com/api/docs/deprecations")
        updated, errors = sync.refresh(self.seed, fetch_fixture)
        self.assertEqual([], errors)
        models = next(p for p in updated["providers"] if p["name"] == "OPEN_AI")["models"]
        self.assertEqual(model, next(m for m in models if m["name"] == "gpt-6-astra"))
        deepseek = next(p for p in updated["providers"] if p["name"] == "DEEP_SEEK")["models"]
        self.assertTrue(next(m for m in deepseek if m["name"] == "deepseek-chat")["retired"])

    def test_capability_conflict_preserves_provider(self):
        provider = next(p for p in self.seed["providers"] if p["name"] == "OPEN_AI")
        provider["models"].insert(0, {"name": "gpt-99-astra", "capabilities": ["IMAGE_GENERATION"]})
        updated, errors = sync.refresh(self.seed, lambda url: fetch_fixture(url).replace("gpt-6-astra", "gpt-99-astra"))
        self.assertEqual(provider, next(p for p in updated["providers"] if p["name"] == "OPEN_AI"))
        self.assertEqual(1, len(errors))

    def test_validates_duplicate_unknown_and_incomplete_data(self):
        invalid = []
        for change in [lambda c: c.update(schemaVersion=2),
                       lambda c: c["providers"].pop(),
                       lambda c: c["providers"].append(c["providers"][0]),
                       lambda c: c["providers"][0].update(name="UNKNOWN"),
                       lambda c: c["providers"][0].update(models=[]),
                       lambda c: c["providers"][0]["models"].append(c["providers"][0]["models"][0]),
                       lambda c: c["providers"][0]["models"][0].update(name="bad model"),
                       lambda c: c["providers"][0]["models"][0].update(capabilities=["VIDEO"]),
                       lambda c: c["providers"][0]["models"][0].update(retired="false"),
                       lambda c: c["providers"][0]["models"][0].update(retired=True),
                       lambda c: c["providers"][0]["models"][0].update(retired=True, retirementSource=" "),
                       lambda c: c["providers"][0]["models"][0].update(capabilities=["IMAGE_GENERATION"])]:
            catalog = copy.deepcopy(self.seed)
            change(catalog)
            invalid.append(catalog)
        for catalog in invalid:
            with self.subTest(catalog=catalog), self.assertRaises(ValueError):
                sync.validate_catalog(catalog)

    def test_atomic_write_and_replace_failure_keep_previous_file(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "models.json"
            sync.atomic_write(path, self.seed)
            self.assertEqual(self.seed, json.loads(path.read_text()))
            old = path.read_bytes()
            with patch.object(sync.os, "replace", side_effect=OSError("disk failure")), self.assertRaises(OSError):
                sync.atomic_write(path, {"new": True})
            self.assertEqual(old, path.read_bytes())
            self.assertEqual([path], list(path.parent.iterdir()))

    def test_check_does_not_write_and_failed_run_does_not_create_output(self):
        updated = copy.deepcopy(self.seed)
        updated["providers"][0]["models"].insert(0, {"name": "future-model", "capabilities": ["TEXT"]})
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "models.json"
            with patch.object(sync, "refresh", return_value=(updated, [])), \
                    patch.object(sync.sys, "argv", ["sync", "--output", str(path), "--check"]):
                self.assertEqual(1, sync.main())
                self.assertEqual([], list(path.parent.iterdir()))
            with patch.object(sync, "refresh", return_value=(self.seed, ["failed"] * 4)), \
                    patch.object(sync.sys, "argv", ["sync", "--output", str(path)]):
                self.assertEqual(2, sync.main())
                self.assertFalse(path.exists())

    def test_output_lock_rejects_overlapping_jobs(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "models.json"
            with sync.output_lock(path), self.assertRaises(BlockingIOError), sync.output_lock(path):
                self.fail("Second job should not acquire the same lock")

    def test_repeated_sync_does_not_rewrite_the_snapshot(self):
        original, errors = sync.refresh(self.seed, fetch_fixture)
        self.assertEqual([], errors)
        updated, errors = sync.refresh(original, fetch_fixture)
        self.assertEqual([], errors)
        self.assertEqual(original, updated)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "models.json"
            sync.atomic_write(path, original)
            original_bytes = path.read_bytes()
            for extra in ([], ["--check"]):
                with patch.object(sync, "refresh", return_value=(updated, [])), \
                        patch.object(sync, "atomic_write") as write, \
                        patch.object(sync.sys, "argv", ["sync", "--output", str(path), *extra]):
                    self.assertEqual(0, sync.main())
                    write.assert_not_called()
                    self.assertEqual(original_bytes, path.read_bytes())

    def test_repository_sync_writes_model_changes_and_reports_partial_failures(self):
        updated = copy.deepcopy(self.seed)
        updated["providers"][0]["models"].insert(0, {"name": "future-model", "capabilities": ["TEXT"]})
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "models.json"
            sync.atomic_write(path, self.seed)
            with patch.object(sync, "refresh", return_value=(updated, ["QWEN: timeout"])), \
                    patch.object(sync.sys, "argv", ["sync", "--output", str(path)]):
                self.assertEqual(2, sync.main())
                self.assertEqual(updated, sync.read_catalog(path))


if __name__ == "__main__":
    unittest.main()
