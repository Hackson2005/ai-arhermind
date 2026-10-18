"""Web search and readable-page extraction helpers."""

from __future__ import annotations

import html
from html.parser import HTMLParser
import logging
import os
from typing import Any
from urllib.parse import parse_qs, quote, urlparse

import requests

JINA_READER_BASE_URL = "https://r.jina.ai/"
REQUEST_TIMEOUT = (10, 45)
USER_AGENT = "AetherMind/1.0 (web research tool)"
LOGGER = logging.getLogger(__name__)


class BrowseError(RuntimeError):
    """Raised when a web page or search results cannot be retrieved."""


def _validate_http_url(url: str) -> str:
    if not isinstance(url, str) or not url.strip():
        raise ValueError("url must be a non-empty string")
    value = url.strip()
    parsed = urlparse(value)
    if parsed.scheme not in {"http", "https"} or not parsed.netloc:
        raise ValueError("url must be an absolute http:// or https:// URL")
    return value


def browse(url: str) -> str:
    """Return clean Markdown for a webpage using the Jina Reader service."""
    target = _validate_http_url(url)
    reader_url = JINA_READER_BASE_URL + quote(target, safe=":/?&=#%")
    try:
        response = requests.get(
            reader_url,
            headers={"Accept": "text/plain", "User-Agent": USER_AGENT},
            timeout=REQUEST_TIMEOUT,
        )
        response.raise_for_status()
    except requests.RequestException as exc:
        raise BrowseError(f"Jina Reader failed to retrieve {target}: {exc}") from exc
    if not response.text.strip():
        raise BrowseError(f"Jina Reader returned empty content for {target}")
    return response.text.strip()


class _DuckDuckGoResultsParser(HTMLParser):
    """Extract result links and snippets from DuckDuckGo's HTML endpoint."""

    _VOID_TAGS = {
        "area", "base", "br", "col", "embed", "hr", "img", "input",
        "link", "meta", "param", "source", "track", "wbr",
    }

    def __init__(self) -> None:
        super().__init__(convert_charrefs=True)
        self.results: list[dict[str, str]] = []
        self._capture: str | None = None
        self._depth = 0

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        attributes = dict(attrs)
        classes = set((attributes.get("class") or "").split())
        if self._capture is not None:
            if tag not in self._VOID_TAGS:
                self._depth += 1
            return

        if tag == "a" and "result__a" in classes:
            self.results.append(
                {
                    "title": "",
                    "url": html.unescape(attributes.get("href") or ""),
                    "content": "",
                }
            )
            self._capture = "title"
            self._depth = 1
        elif self.results and "result__snippet" in classes:
            self._capture = "content"
            self._depth = 1

    def handle_data(self, data: str) -> None:
        if self._capture is not None and self.results:
            self.results[-1][self._capture] += data

    def handle_endtag(self, tag: str) -> None:
        if self._capture is None:
            return
        self._depth -= 1
        if self._depth <= 0:
            self._capture = None
            self._depth = 0


def _duckduckgo_search(query: str, max_results: int) -> list[dict[str, str]]:
    try:
        response = requests.get(
            "https://html.duckduckgo.com/html/",
            params={"q": query},
            headers={"User-Agent": USER_AGENT},
            timeout=REQUEST_TIMEOUT,
        )
        response.raise_for_status()
    except requests.RequestException as exc:
        raise BrowseError(f"DuckDuckGo search failed: {exc}") from exc

    parser = _DuckDuckGoResultsParser()
    parser.feed(response.text)
    results: list[dict[str, str]] = []
    for result in parser.results:
        url = result["url"].strip()
        parsed_url = urlparse(url)
        if parsed_url.netloc.endswith("duckduckgo.com"):
            redirect_target = parse_qs(parsed_url.query).get("uddg", [""])[0]
            if redirect_target:
                url = redirect_target
        if not url.startswith(("http://", "https://")):
            continue
        results.append(
            {
                **{key: value.strip() for key, value in result.items() if key != "url"},
                "url": url,
            }
        )
        if len(results) >= max_results:
            break
    return results


def _tavily_search(query: str, max_results: int) -> list[dict[str, str]]:
    from tavily import TavilyClient

    api_key = os.environ.get("TAVILY_API_KEY", "").strip()
    if not api_key:
        raise ValueError("TAVILY_API_KEY is not set")

    response: dict[str, Any] = TavilyClient(api_key=api_key).search(
        query=query,
        max_results=max_results,
        search_depth="basic",
    )
    results = response.get("results", [])
    if not isinstance(results, list):
        raise BrowseError("Tavily returned results in an unexpected format")

    normalized: list[dict[str, str]] = []
    for item in results:
        if not isinstance(item, dict):
            continue
        url = item.get("url")
        if not isinstance(url, str) or not url.startswith(("http://", "https://")):
            continue
        normalized.append(
            {
                "title": str(item.get("title") or url),
                "url": url,
                "content": str(item.get("content") or ""),
            }
        )
    return normalized


def web_search_and_browse(query: str, max_results: int = 3) -> list[dict[str, str]]:
    """Search with Tavily (or DuckDuckGo without a key) and browse each result.

    Each returned item contains ``title``, ``url``, and Markdown ``content``.
    If Tavily is unavailable or fails, DuckDuckGo is used as a fallback.
    """
    if not isinstance(query, str) or not query.strip():
        raise ValueError("query must be a non-empty string")
    if isinstance(max_results, bool) or not isinstance(max_results, int) or max_results < 1:
        raise ValueError("max_results must be a positive integer")

    try:
        results = _tavily_search(query.strip(), max_results)
        if not results:
            LOGGER.info("Tavily returned no results; trying DuckDuckGo fallback")
            results = _duckduckgo_search(query.strip(), max_results)
    except Exception as exc:
        LOGGER.warning("Tavily search failed; trying DuckDuckGo fallback: %s", exc)
        results = _duckduckgo_search(query.strip(), max_results)

    browsed: list[dict[str, str]] = []
    for result in results[:max_results]:
        try:
            content = browse(result["url"])
        except (BrowseError, ValueError) as exc:
            LOGGER.warning("Could not browse search result %s: %s", result["url"], exc)
            snippet = result.get("content", "").strip()
            if snippet:
                content = f"> Page extraction failed: {exc}\n\n{snippet}"
            else:
                content = f"Could not browse this result: {exc}"
        browsed.append(
            {
                "title": result["title"],
                "url": result["url"],
                "content": content,
            }
        )
    return browsed
