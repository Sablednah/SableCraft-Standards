#!/usr/bin/env python3
"""Generate NODES.md from StandardsPermissions.java.

Generated rather than hand-written, because a hand-kept list of a hundred permission nodes is
exactly the document this repo keeps catching itself shipping stale — and unlike a status line,
nobody would ever notice: a missing node reads as a node that does not exist.

    python3 scripts/nodes.py

Run it after adding, removing or re-defaulting a node. The source of truth stays the Java.
"""
import re
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "src/main/java/com/sablednah/standards/neoforge/StandardsPermissions.java"
OUT = ROOT / "NODES.md"

MOD_ID = "standards"

DEFAULT_TEXT = {
    "EVERYONE": ("everyone", "Every player has it unless a permissions mod takes it away."),
    "OPS": ("ops", "Operators have it; anybody else needs it granted."),
    "NOBODY": ("nobody", "**Nobody has it until it is granted**, operators included."),
}


# Nodes whose meaning is not simply "may use the command of the same name". Anything not listed
# here and undocumented in the source is described from its own path, which is accurate: that is
# precisely what those nodes gate.
NOT_A_COMMAND = {
    "home.limit.unlimited": "Any number of homes, beating every numbered `home.limit.<n>`.",
    "teleport.instant": "Skip the teleport warmup — arrive immediately.",
    "teleport.nocooldown": "Skip the wait between teleports.",
    "admin": "`/standards` — administering the mod itself, not using it.",
    "afk.exempt": "Never kicked for idling.",
    "combat.bypass": "Teleport out of a fight anyway.",
    "back.ondeath": "`/back` returns you to where you died, not just where you teleported from.",
    "msg.override": "Message somebody who has `/msgtoggle` on.",
    "tpa.override": "Send a teleport request to somebody who has `/tptoggle` on.",
    "nick.color": "Colour codes in a nickname.",
    "nick.others": "Set or clear somebody else's nickname.",
    "gc": "`/gc`, `/tps`, `/lag`, `/mem` — server health.",
}


def describe_from_path(path):
    """A truthful description for an undocumented, command-shaped node."""
    if path in NOT_A_COMMAND:
        return NOT_A_COMMAND[path]
    if path.endswith(".others"):
        base = path[: -len(".others")]
        return f"Use `/{base}` on another player."
    if path.endswith(".see"):
        return f"See through `/{path[: -len('.see')]}`."
    return f"Use `/{path}`."


def clean_doc(raw):
    """Turn a javadoc block into one plain sentence-ish line."""
    text = re.sub(r"/\*\*|\*/", " ", raw)
    text = re.sub(r"(?m)^\s*\*\s?", " ", text)
    text = re.sub(r"</?p>|</?b>|</?em>|</?ul>|</?li>", " ", text)
    text = re.sub(r"\{@code\s+([^}]*)\}", r"`\1`", text)
    text = re.sub(r"\{@link\s+#?([^}]*)\}", r"`\1`", text)
    text = re.sub(r"\s+", " ", text).strip()
    # Only the first sentence-or-two; the full reasoning belongs in the source.
    parts = re.split(r"(?<=\.)\s+", text)
    out = " ".join(parts[:2]).strip()
    return out


def parse():
    source = SRC.read_text(encoding="utf-8")
    sections = []
    current = ("General", [])
    # Walk line by line so a node keeps the '// --- section ---' heading above it and whichever
    # comment block immediately precedes it.
    lines = source.splitlines()
    pending = []
    i = 0
    while i < len(lines):
        line = lines[i]
        heading = re.match(r"\s*//\s*---\s*(.+?)\s*---", line)
        if heading:
            if current[1]:
                sections.append(current)
            current = (heading.group(1).strip().capitalize(), [])
            pending = []
            i += 1
            continue
        if line.strip().startswith("/**"):
            block = [line]
            while "*/" not in lines[i] and i + 1 < len(lines):
                i += 1
                block.append(lines[i])
            pending = [clean_doc("\n".join(block))]
            i += 1
            continue
        decl = re.search(r'node\("([^"]+)",\s*Default\.(\w+)\)', line)
        if decl:
            path = decl.group(1)
            doc = pending[0] if pending and pending[0] else describe_from_path(path)
            current[1].append((path, decl.group(2), doc))
            pending = []
            i += 1
            continue
        if line.strip() and not line.strip().startswith("//"):
            pending = []
        i += 1
    if current[1]:
        sections.append(current)
    return sections


def render(sections):
    total = sum(len(nodes) for _, nodes in sections)
    out = []
    out.append("# Permission nodes")
    out.append("")
    out.append("**Generated from the source — do not edit by hand.** "
               "`python3 scripts/nodes.py` rebuilds it from "
               "`StandardsPermissions.java`, which is the only place a node is really declared.")
    out.append("")
    out.append(f"{total} declared nodes, plus the runtime ones described at the bottom.")
    out.append("")
    out.append("Standards asks NeoForge's `PermissionAPI` for every one of these, so they work "
               "with LuckPerms, with Standards' own handler (`/rank`, see "
               "[`PERMISSIONS.md`](PERMISSIONS.md)), or with nothing installed at all — in which "
               "case the **Default** column is the whole answer.")
    out.append("")
    out.append("| Default | Means |")
    out.append("|---|---|")
    for key in ("EVERYONE", "OPS", "NOBODY"):
        label, meaning = DEFAULT_TEXT[key]
        out.append(f"| `{label}` | {meaning} |")
    out.append("")
    for title, nodes in sections:
        if not nodes:
            continue
        out.append(f"## {title}")
        out.append("")
        out.append("| Node | Default | What it allows |")
        out.append("|---|---|---|")
        for path, default, doc in sorted(nodes):
            label = DEFAULT_TEXT[default][0]
            out.append(f"| `{MOD_ID}.{path}` | `{label}` | {doc} |")
        out.append("")
    out.append("## Built at runtime")
    out.append("")
    out.append("These are not declared in source — the server builds them from what it actually "
               "holds, so they are not in the table above and a permissions mod will only see "
               "them after a restart.")
    out.append("")
    out.append("| Node | What it allows |")
    out.append("|---|---|")
    out.append(f"| `{MOD_ID}.home.limit.<n>` | That many homes. The highest granted number wins, "
               "and `home.limit.unlimited` beats them all. Numbered nodes rather than one integer "
               "node, because every server admin alive already knows the idiom. |")
    out.append(f"| `{MOD_ID}.kit.<name>` | One particular kit. Its default follows the kit's own "
               "access — see `/kitaccess`. A kit created since the last restart has no node, and "
               "is answered from that access directly. |")
    out.append("")
    out.append("Ask a running server what it really has with `/standards nodes`, which lists "
               "every node actually registered, runtime ones included.")
    out.append("")
    return "\n".join(out)


if __name__ == "__main__":
    if not SRC.exists():
        sys.exit(f"!! {SRC} not found — run this from the repo, or fix the path.")
    sections = parse()
    OUT.write_text(render(sections), encoding="utf-8")
    count = sum(len(n) for _, n in sections)
    print(f"Wrote {OUT.relative_to(ROOT)} — {count} declared nodes in {len(sections)} sections.")
