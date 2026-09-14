#!/usr/bin/env python3
"""Refresh model data from official public catalogs without API credentials."""

import argparse
import copy
from contextlib import contextmanager, nullcontext
import fcntl
from html.parser import HTMLParser
import json
import os
from pathlib import Path
import re
import sys
import tempfile
from urllib.request import Request, urlopen

SOURCES = {
    "DEEP_SEEK": "https://api-docs.deepseek.com/quick_start/pricing",
    "OPEN_AI": "https://developers.openai.com/api/docs/models",
    "QWEN": "https://help.aliyun.com/zh/model-studio/models",
    "GOOGLE_GEMINI": "https://ai.google.dev/gemini-api/docs/models",
}
MAX_CATALOG_BYTES = 1024 * 1024
MAX_PAGE_BYTES = 8 * 1024 * 1024
MODEL_ID = re.compile(r"[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}")
SEED = Path(__file__).resolve().parents[1] / "src/main/resources/ai/models.json"


class CatalogPage(HTMLParser):
    def __init__(self, html):
        super().__init__()
        self.text = []
        self.links = []
        self.tables = []
        self.table = None
        self.hidden = 0
        self.feed(html)

    def handle_starttag(self, tag, attrs):
        if tag in ("script", "style"):
            self.hidden += 1
        if self.hidden:
            return
        if tag == "table":
            self.table = []
        if tag == "a":
            self.links.extend(value for key, value in attrs if key == "href" and value)

    def handle_endtag(self, tag):
        if tag in ("script", "style"):
            self.hidden = max(0, self.hidden - 1)
        if tag == "table" and self.table is not None:
            self.tables.append(self.table)
            self.table = None

    def handle_data(self, data):
        if not self.hidden and data.strip():
            self.text.append(data.strip())
            if self.table is not None:
                self.table.append(data.strip())


def extract_models(provider, html):
    page = CatalogPage(html)
    candidates = []
    if provider == "OPEN_AI":
        # Canonical model links supply IDs even where the visible label is a marketing name.
        candidates = [link.rsplit("/", 1)[-1] for link in page.links
                      if link.startswith("/api/docs/models/")]
        text_pattern = r"gpt-\d+(?:\.\d+)?(?:-(?:astra|sol|terra|luna|mini|nano))?"
        image_pattern = r"gpt-image-\d+(?:\.\d+)?(?:-(?:sunburst|flare|mini))?"
    elif provider == "DEEP_SEEK":
        for table in page.tables:
            if table and table[0] == "MODEL":
                candidates = table[1:next(i for i, value in enumerate(table) if value.startswith("BASE URL"))]
                break
        text_pattern = r"deepseek-(?:flash|chat|reasoner|v\d+(?:\.\d+)?-(?:pro|flash))"
        image_pattern = r"(?!)"
    elif provider == "QWEN":
        # Only the text-generation section; vision input does not imply image output.
        start = page.text.index("文本生成") + 1
        end = page.text.index("图像与视频", start)
        candidates = page.text[start:end]
        text_pattern = r"qwen(?:\d+(?:\.\d+)?)?-(?:max|plus|flash)(?:-latest)?"
        image_pattern = r"(?!)"
    elif provider == "GOOGLE_GEMINI":
        for table in page.tables:
            if "Endpoint" in table and not any("Shut down" in value or "Deprecated" in value for value in table):
                candidates.extend(table)
        text_pattern = r"gemini-\d+(?:\.\d+)?-(?:flash|pro)(?:-lite)?(?:-preview)?"
        image_pattern = r"gemini-\d+(?:\.\d+)?-(?:flash|pro)(?:-lite)?-image(?:-preview)?"
    else:
        raise ValueError("Unknown provider")

    models = {}
    for name in candidates:
        capability = ("TEXT" if re.fullmatch(text_pattern, name) else
                      "IMAGE_GENERATION" if re.fullmatch(image_pattern, name) else None)
        if capability:
            models.setdefault(name, {"name": name, "capabilities": [capability]})
    if not any("TEXT" in model["capabilities"] for model in models.values()) or len(models) > 100:
        raise ValueError("Official catalog layout changed or returned no supported text models")
    if provider in ("OPEN_AI", "GOOGLE_GEMINI") and not any(
            "IMAGE_GENERATION" in model["capabilities"] for model in models.values()):
        raise ValueError("Official catalog returned no image models")
    return list(models.values())


def validate_catalog(catalog):
    if not isinstance(catalog, dict) or type(catalog.get("schemaVersion")) is not int or catalog["schemaVersion"] != 1:
        raise ValueError("Unsupported catalog schema")
    providers = catalog.get("providers")
    if not isinstance(providers, list) or len(providers) != len(SOURCES):
        raise ValueError("Catalog must include all four providers")
    seen = set()
    for provider in providers:
        if not isinstance(provider, dict) or provider.get("name") not in SOURCES or provider["name"] in seen:
            raise ValueError("Unknown or duplicate provider")
        seen.add(provider["name"])
        models = provider.get("models")
        if not isinstance(models, list) or not 1 <= len(models) <= 512:
            raise ValueError("Invalid model list")
        names = set()
        has_text = False
        for model in models:
            if not isinstance(model, dict) or not isinstance(model.get("name"), str) or not MODEL_ID.fullmatch(model["name"]):
                raise ValueError("Invalid model ID")
            capabilities = model.get("capabilities")
            if (not isinstance(capabilities, list) or not capabilities
                    or any(cap not in ("TEXT", "IMAGE_GENERATION") for cap in capabilities)
                    or len(set(capabilities)) != len(capabilities) or model["name"] in names):
                raise ValueError("Duplicate model or invalid capabilities")
            if "IMAGE_GENERATION" in capabilities and provider["name"] not in ("OPEN_AI", "GOOGLE_GEMINI"):
                raise ValueError("Provider has no supported image protocol")
            names.add(model["name"])
            has_text |= "TEXT" in capabilities
        if not has_text:
            raise ValueError("Provider has no text models")


def fetch_page(url):
    request = Request(url, headers={"User-Agent": "ZrLog-Model-Catalog/1.0", "Accept-Encoding": "identity"})
    with urlopen(request, timeout=30) as response:
        if response.status != 200 or not response.url.startswith("https://"):
            raise ValueError("Unexpected official catalog response")
        body = response.read(MAX_PAGE_BYTES + 1)
        if len(body) > MAX_PAGE_BYTES:
            raise ValueError("Official catalog exceeds size limit")
        return body.decode("utf-8")


def refresh(catalog, fetcher=fetch_page):
    validate_catalog(catalog)
    result = copy.deepcopy(catalog)
    errors = []
    for provider in result["providers"]:
        name = provider["name"]
        try:
            discovered = extract_models(name, fetcher(SOURCES[name]))
            previous = {model["name"]: model for model in provider["models"]}
            if any(model["name"] in previous and model["capabilities"] != previous[model["name"]]["capabilities"]
                   for model in discovered):
                raise ValueError("Model capability changed; manual review required")
            merged = {model["name"]: model for model in discovered}
            for model in provider["models"]:
                merged.setdefault(model["name"], model)
            if len(merged) > 512:
                raise ValueError("Merged model list exceeds size limit")
            provider["models"] = list(merged.values())
            provider["source"] = SOURCES[name]
        except Exception as error:
            errors.append(f"{name}: {error}")
    validate_catalog(result)
    return result, errors


def read_catalog(path):
    with path.open("rb") as stream:
        data = stream.read(MAX_CATALOG_BYTES + 1)
    if len(data) > MAX_CATALOG_BYTES:
        raise ValueError("Catalog exceeds size limit")
    result = json.loads(data)
    validate_catalog(result)
    return result


def atomic_write(path, catalog):
    data = (json.dumps(catalog, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    if len(data) > MAX_CATALOG_BYTES:
        raise ValueError("Catalog exceeds size limit")
    temporary = None
    try:
        with tempfile.NamedTemporaryFile(dir=path.parent, prefix=path.name + ".", delete=False) as stream:
            temporary = Path(stream.name)
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        # Preserve existing access mode; new catalogs contain only public model metadata.
        os.chmod(temporary, path.stat().st_mode & 0o777 if path.exists() else 0o644)
        os.replace(temporary, path)
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


@contextmanager
def output_lock(path):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.with_name(path.name + ".lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        yield


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True, help="Runtime conf/ai-models.json or bundled snapshot")
    parser.add_argument("--seed", type=Path, default=SEED, help="Initial catalog when output does not exist")
    parser.add_argument("--check", action="store_true", help="Report differences without writing (exit 1 if changed)")
    args = parser.parse_args()
    try:
        with nullcontext() if args.check else output_lock(args.output):
            existing = read_catalog(args.output if args.output.exists() else args.seed)
            updated, errors = refresh(existing)
            for error in errors:
                print(error, file=sys.stderr)
            changed = updated != existing
            if not args.check and (changed or not args.output.exists()) and len(errors) < len(SOURCES):
                atomic_write(args.output, updated)
            print(f"AI model catalog: {'changed' if changed else 'unchanged'}, {len(errors)} provider failure(s)")
            return 2 if errors else 1 if args.check and changed else 0
    except Exception as error:
        print(f"AI model catalog sync failed: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
