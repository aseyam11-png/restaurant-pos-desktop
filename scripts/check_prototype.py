#!/usr/bin/env python3
"""Dependency-light structural and privacy checks for the HTML cashier prototype."""

from __future__ import annotations

import re
import subprocess
import tempfile
from html.parser import HTMLParser
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
HTML_FILE = ROOT / "prototype" / "index.html"


class PrototypeParser(HTMLParser):
    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.ids: list[str] = []
        self.inline_scripts: list[str] = []
        self._in_script = False
        self._script_parts: list[str] = []
        self.external_scripts = 0

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attributes = dict(attrs)
        element_id = attributes.get("id")
        if element_id:
            self.ids.append(element_id)
        if tag == "script":
            self._in_script = True
            self._script_parts = []
            if attributes.get("src"):
                self.external_scripts += 1

    def handle_endtag(self, tag: str) -> None:
        if tag == "script" and self._in_script:
            self.inline_scripts.append("".join(self._script_parts))
            self._in_script = False
            self._script_parts = []

    def handle_data(self, data: str) -> None:
        if self._in_script:
            self._script_parts.append(data)


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def main() -> None:
    if not HTML_FILE.exists():
        fail("prototype/index.html is missing")

    source = HTML_FILE.read_text(encoding="utf-8")
    parser = PrototypeParser()
    parser.feed(source)
    parser.close()

    duplicates = sorted({item for item in parser.ids if parser.ids.count(item) > 1})
    if duplicates:
        fail(f"duplicate HTML IDs: {', '.join(duplicates)}")

    if parser.external_scripts:
        fail("prototype must remain self-contained; external script tags are not allowed")

    if len(parser.inline_scripts) != 1 or not parser.inline_scripts[0].strip():
        fail("expected exactly one non-empty inline script")

    script = parser.inline_scripts[0]
    referenced_ids = set(re.findall(r"\$\(['\"]([^'\"]+)['\"]\)", script))
    missing_ids = sorted(referenced_ids - set(parser.ids))
    if missing_ids:
        fail(f"JavaScript references missing HTML IDs: {', '.join(missing_ids)}")

    if "function buildReceipt(order)" not in script:
        fail("customer receipt builder is missing")
    receipt_start = script.index("function buildReceipt(order)")
    receipt_end = script.index("function printOrder(order)", receipt_start)
    receipt_builder = script[receipt_start:receipt_end]
    if "walletSender" in receipt_builder or "wallet-sender" in receipt_builder:
        fail("wallet transfer sender phone must never be rendered on the customer receipt")

    if "walletSender" not in script or "function renderReports()" not in script:
        fail("internal wallet sender reporting path is missing")

    if "@media print" not in source or "@media(max-width:" not in source:
        fail("print and responsive layout rules are required")
    if 'lang="ar" dir="rtl"' not in source:
        fail("Arabic RTL document metadata is required")

    with tempfile.NamedTemporaryFile("w", suffix=".js", encoding="utf-8", delete=False) as temp:
        temp.write(script)
        temp_path = Path(temp.name)

    try:
        subprocess.run(["node", "--check", str(temp_path)], check=True, capture_output=True, text=True)
    except FileNotFoundError:
        fail("Node.js is required to syntax-check the inline prototype script")
    except subprocess.CalledProcessError as exc:
        fail(f"JavaScript syntax check failed:\n{exc.stderr}")
    finally:
        temp_path.unlink(missing_ok=True)

    print(
        "PASS: prototype HTML IDs, internal/customer data boundary, RTL, responsive/print CSS, "
        "and inline JavaScript syntax validated."
    )


if __name__ == "__main__":
    main()
