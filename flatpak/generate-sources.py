#!/usr/bin/env python3
"""Lists every Maven file the desktop build needs, for the Flatpak to fetch.

The Flatpak builds offline: flatpak-builder downloads each file listed here,
checks its SHA-256, and lays it out as a Maven repository that the build reads
instead of the network (see offline.init.gradle.kts).

Regenerate whenever a dependency changes:

    G=$(mktemp -d)
    GRADLE_USER_HOME=$G ./gradlew --no-daemon -Panonpdf.desktopOnly=true \\
        :desktop:createReleaseDistributable
    flatpak/generate-sources.py $G > flatpak/maven-sources.json

The Gradle home must start empty, so the list holds what this build resolves
and nothing else.
"""
import hashlib
import json
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

# The repositories settings.gradle.kts names, in the order Gradle tries them.
REPOSITORIES = [
    "https://dl.google.com/dl/android/maven2/",
    "https://repo.maven.apache.org/maven2/",
    "https://plugins.gradle.org/m2/",
]


def exists(url: str) -> bool:
    for attempt in range(5):
        try:
            request = urllib.request.Request(url, method="HEAD")
            with urllib.request.urlopen(request, timeout=30) as response:
                return response.status == 200
        except urllib.error.HTTPError:
            return False
        except (urllib.error.URLError, TimeoutError):
            time.sleep(2 ** attempt)  # a dropped connection, not an answer: retry
    raise SystemExit(f"could not reach {url}")


def served_from(path: str) -> str:
    for repository in REPOSITORIES:
        if exists(repository + path):
            return repository + path
    raise SystemExit(f"no repository serves {path}")


def entry(file: Path, cache: Path) -> dict:
    # Gradle keeps files as <group>/<artifact>/<version>/<sha1>/<name>.
    group, artifact, version, _, name = file.relative_to(cache).parts
    directory = f"{group.replace('.', '/')}/{artifact}/{version}"
    return {
        "type": "file",
        "url": served_from(f"{directory}/{name}"),
        "sha256": hashlib.sha256(file.read_bytes()).hexdigest(),
        "dest": f"offline-repository/{directory}",
        "dest-filename": name,
    }


def main() -> None:
    cache = Path(sys.argv[1]) / "caches/modules-2/files-2.1"
    files = sorted(p for p in cache.rglob("*") if p.is_file())
    with ThreadPoolExecutor(max_workers=8) as pool:
        entries = list(pool.map(lambda f: entry(f, cache), files))
    json.dump(entries, sys.stdout, indent=2)
    sys.stdout.write("\n")


if __name__ == "__main__":
    main()
