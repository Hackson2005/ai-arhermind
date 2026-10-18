"""Playwright-powered extraction for pages that require JavaScript."""

from __future__ import annotations

from urllib.parse import urlparse

from playwright.sync_api import Error as PlaywrightError
from playwright.sync_api import TimeoutError as PlaywrightTimeoutError
from playwright.sync_api import sync_playwright


class PlaywrightBrowseError(RuntimeError):
    """Raised when a page cannot be loaded or extracted with Playwright."""


def browse_playwright(url: str, timeout_ms: int = 30_000) -> str:
    """Load a JavaScript-rendered page and return its visible text content."""
    if not isinstance(url, str) or not url.strip():
        raise ValueError("url must be a non-empty string")
    target = url.strip()
    parsed = urlparse(target)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        raise ValueError("url must be an absolute http:// or https:// URL")
    if isinstance(timeout_ms, bool) or not isinstance(timeout_ms, int) or timeout_ms <= 0:
        raise ValueError("timeout_ms must be a positive integer")

    try:
        with sync_playwright() as playwright:
            browser = playwright.chromium.launch(headless=True)
            try:
                page = browser.new_page(
                    user_agent="AetherMind/1.0 (JavaScript page reader)"
                )
                page.goto(
                    target,
                    wait_until="domcontentloaded",
                    timeout=timeout_ms,
                )
                page.locator("body").wait_for(state="visible", timeout=timeout_ms)
                try:
                    page.wait_for_load_state("networkidle", timeout=min(timeout_ms, 5_000))
                except PlaywrightTimeoutError:
                    pass
                content = page.locator("body").inner_text(timeout=timeout_ms).strip()
                if not content:
                    raise PlaywrightBrowseError(f"No visible page text found at {target}")
                return content
            finally:
                browser.close()
    except PlaywrightBrowseError:
        raise
    except PlaywrightError as exc:
        raise PlaywrightBrowseError(f"Playwright failed to read {target}: {exc}") from exc
