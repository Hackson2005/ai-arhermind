"""Periodically search selected topics and save browsed material as Markdown."""

from __future__ import annotations

import argparse
import logging
import re
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Sequence

import schedule

from tools import web_search_and_browse

LOGGER = logging.getLogger("continuous_sucker")
DEFAULT_TOPICS = [
    "artificial intelligence research",
    "cybersecurity news",
    "software engineering best practices",
]


def _slugify(value: str) -> str:
    slug = re.sub(r"[^a-z0-9]+", "-", value.lower()).strip("-")
    return slug or "topic"


def collect_topic(topic: str, output_dir: Path, max_results: int = 3) -> Path:
    """Search and browse one topic, then write a timestamped Markdown report."""
    results = web_search_and_browse(topic, max_results=max_results)
    output_dir.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    target = output_dir / f"{_slugify(topic)}-{timestamp}.md"

    sections = [
        f"# Knowledge collection: {topic}",
        "",
        f"Collected: {datetime.now(timezone.utc).isoformat()}",
        "",
    ]
    if not results:
        sections.extend(["No search results were returned.", ""])
    for result in results:
        sections.extend(
            [
                f"## {result['title']}",
                "",
                f"Source: {result['url']}",
                "",
                result["content"],
                "",
                "---",
                "",
            ]
        )
    target.write_text("\n".join(sections), encoding="utf-8")
    LOGGER.info("Saved %d result(s) for %r to %s", len(results), topic, target)
    return target


def collect_all(topics: Sequence[str], output_dir: Path, max_results: int) -> None:
    """Collect all topics, logging failures and continuing with remaining topics."""
    for topic in topics:
        try:
            collect_topic(topic, output_dir, max_results=max_results)
        except Exception:
            LOGGER.exception("Could not collect topic %r", topic)


def _parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Periodically search topics and save readable results as Markdown."
    )
    parser.add_argument(
        "--topic",
        action="append",
        dest="topics",
        help="Topic to collect; repeat this option to add more topics.",
    )
    parser.add_argument(
        "--interval-minutes",
        type=int,
        default=60,
        help="How often to repeat the collection cycle (default: 60 minutes).",
    )
    parser.add_argument(
        "--max-results",
        type=int,
        default=3,
        help="Maximum results to browse per topic (default: 3).",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("knowledge"),
        help="Directory for collected Markdown files (default: ./knowledge).",
    )
    return parser.parse_args()


def main() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s %(levelname)s %(name)s: %(message)s",
    )
    args = _parse_args()
    if args.interval_minutes <= 0:
        raise SystemExit("--interval-minutes must be a positive integer")
    if args.max_results <= 0:
        raise SystemExit("--max-results must be a positive integer")

    topics = args.topics or DEFAULT_TOPICS
    collect_all(topics, args.output_dir, args.max_results)
    schedule.every(args.interval_minutes).minutes.do(
        collect_all,
        topics=topics,
        output_dir=args.output_dir,
        max_results=args.max_results,
    )
    LOGGER.info(
        "Scheduled %d topic(s) every %d minute(s); press Ctrl+C to stop",
        len(topics),
        args.interval_minutes,
    )
    try:
        while True:
            schedule.run_pending()
            time.sleep(1)
    except KeyboardInterrupt:
        LOGGER.info("Stopping knowledge collection.")


if __name__ == "__main__":
    main()
