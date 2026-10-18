"""Tool registry for AetherMind's OpenAI-compatible agent integrations."""

from __future__ import annotations

from typing import Any

from .browse import BrowseError, browse, web_search_and_browse
from .browser_playwright import PlaywrightBrowseError, browse_playwright

TOOL_DEFINITIONS: list[dict[str, Any]] = [
    {
        "type": "function",
        "function": {
            "name": "browse",
            "description": "Fetch a webpage and return clean Markdown using Jina Reader.",
            "parameters": {
                "type": "object",
                "properties": {
                    "url": {
                        "type": "string",
                        "description": "Absolute HTTP or HTTPS URL of the webpage.",
                    }
                },
                "required": ["url"],
                "additionalProperties": False,
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "web_search_and_browse",
            "description": (
                "Search the web using Tavily or DuckDuckGo fallback, then browse "
                "the top results and return their Markdown content."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {
                        "type": "string",
                        "description": "The web search query.",
                    },
                    "max_results": {
                        "type": "integer",
                        "description": "Maximum number of results to search and browse.",
                        "default": 3,
                        "minimum": 1,
                    },
                },
                "required": ["query"],
                "additionalProperties": False,
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "browse_playwright",
            "description": (
                "Load a JavaScript-heavy webpage with Chromium and return its "
                "visible text."
            ),
            "parameters": {
                "type": "object",
                "properties": {
                    "url": {
                        "type": "string",
                        "description": "Absolute HTTP or HTTPS URL of the webpage.",
                    },
                    "timeout_ms": {
                        "type": "integer",
                        "description": "Navigation timeout in milliseconds.",
                        "default": 30000,
                        "minimum": 1,
                    },
                },
                "required": ["url"],
                "additionalProperties": False,
            },
        },
    },
]

OPENAI_TOOLS = TOOL_DEFINITIONS


def execute_tool(name: str, args: dict[str, Any]) -> str | list[dict[str, str]]:
    """Dispatch a tool call by name, rejecting unknown tools or invalid args."""
    if not isinstance(args, dict):
        raise TypeError("tool arguments must be a JSON object")
    if name == "browse":
        url = args.get("url")
        if not isinstance(url, str):
            raise ValueError("browse requires a string 'url' argument")
        return browse(url)
    if name == "web_search_and_browse":
        query = args.get("query")
        max_results = args.get("max_results", 3)
        if not isinstance(query, str):
            raise ValueError("web_search_and_browse requires a string 'query' argument")
        return web_search_and_browse(query, max_results=max_results)
    if name == "browse_playwright":
        url = args.get("url")
        timeout_ms = args.get("timeout_ms", 30_000)
        if not isinstance(url, str):
            raise ValueError("browse_playwright requires a string 'url' argument")
        return browse_playwright(url, timeout_ms=timeout_ms)
    raise ValueError(f"Unknown tool: {name}")


__all__ = [
    "BrowseError",
    "OPENAI_TOOLS",
    "PlaywrightBrowseError",
    "TOOL_DEFINITIONS",
    "browse",
    "browse_playwright",
    "execute_tool",
    "web_search_and_browse",
]
