#!/usr/bin/env python3
"""
Strip <permission android:name="..."> declarations from a decoded (text) AndroidManifest.xml.

Used by the build pipeline to remove the signature-level permissions Messenger shares with
the Facebook app, so the re-signed Messenger installs alongside Facebook without
INSTALL_FAILED_DUPLICATE_PERMISSION. <uses-permission> references are left intact.

Usage: strip-perms.py <AndroidManifest.xml> <perm1> [perm2 ...]
Exits non-zero if nothing was stripped (so CI fails loudly rather than shipping a
still-conflicting APK).
"""
import re
import sys


def main() -> int:
    if len(sys.argv) < 3:
        print("usage: strip-perms.py <manifest.xml> <perm> [perm ...]", file=sys.stderr)
        return 1
    path = sys.argv[1]
    perms = sys.argv[2:]

    with open(path, encoding="utf-8") as f:
        text = f.read()

    total = 0
    for p in perms:
        esc = re.escape(p)
        # <permission ... android:name="P" ... /> or <permission ...>...</permission>
        pattern = re.compile(
            r'\s*<permission\b[^>]*?android:name="%s"[^>]*?(?:/>|>\s*</permission>)' % esc,
            re.DOTALL,
        )
        text, n = pattern.subn("", text)
        print(f"  stripped {n} block(s) for {p}")
        total += n

    with open(path, "w", encoding="utf-8") as f:
        f.write(text)

    if total == 0:
        print("ERROR: no <permission> declarations stripped; manifest format may have "
              "changed. Aborting.", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
