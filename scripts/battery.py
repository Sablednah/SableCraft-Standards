#!/usr/bin/env python3
"""Drive Standards commands over RCON and assert on their return values.

Our commands return meaningful ints (a count, a balance, 1/0 for did-it-work), and
`execute store result score` captures that into a scoreboard we can read back. That turns
"the command did not error" into a real assertion.
"""
import socket, struct, sys, time

HOST, PORT, PASSWORD = "127.0.0.1", 25575, "standards-dev"
OBJ = "stdtest"
HOLDER = "T"

passed, failed = [], []


class Rcon:
    def __init__(self):
        self.sock = socket.create_connection((HOST, PORT), timeout=15)
        self._send(3, PASSWORD)
        if self._read()[0] == -1:
            raise SystemExit("RCON auth failed")
        self.n = 1

    def _send(self, kind, body):
        payload = struct.pack("<ii", 1, kind) + body.encode("utf8") + b"\x00\x00"
        self.sock.sendall(struct.pack("<i", len(payload)) + payload)

    def _read(self):
        raw = self.sock.recv(4)
        if len(raw) < 4:
            return -1, ""
        size = struct.unpack("<i", raw)[0]
        data = b""
        while len(data) < size:
            data += self.sock.recv(size - len(data))
        rid, _ = struct.unpack("<ii", data[:8])
        return rid, data[8:-2].decode("utf8", "replace")

    def run(self, cmd):
        self._send(2, cmd)
        return self._read()[1].strip()

    def value(self, player, cmd):
        """Run a command as a player and return its integer result, or None if it refused."""
        self.run(f"scoreboard players reset {HOLDER} {OBJ}")
        self.run(f"execute as {player} at {player} store result score {HOLDER} {OBJ} run {cmd}")
        out = self.run(f"scoreboard players get {HOLDER} {OBJ}")
        for token in out.split():
            if token.lstrip("-").isdigit():
                return int(token)
        return None


def check(label, ok, detail=""):
    (passed if ok else failed).append(label + (f"  [{detail}]" if detail and not ok else ""))
    print(("  PASS  " if ok else "  FAIL  ") + label + (f"   {detail}" if detail else ""))


def main():
    r = Rcon()
    who = "TestBuddy"
    r.run(f"scoreboard objectives add {OBJ} dummy")
    r.run(f"gamemode creative {who}")

    print("\n--- economy ---")
    start = r.value(who, "balance")
    check("/balance returns a number", start is not None, f"got {start}")
    r.run(f"eco set {who} 100")
    check("/eco set then /balance reads 100", r.value(who, "balance") == 100)
    r.run(f"eco give {who} 500")
    check("/eco give adds", r.value(who, "balance") == 600)
    r.run(f"eco take {who} 250")
    check("/eco take subtracts", r.value(who, "balance") == 350)
    check("/baltop lists at least one account", (r.value(who, "baltop") or 0) >= 1)

    print("\n--- homes ---")
    r.value(who, "delhome home")
    r.value(who, "delhome base")
    check("/sethome works", r.value(who, "sethome") == 1)
    check("/homes counts 1", r.value(who, "homes") == 1)
    check("/sethome base works", r.value(who, "sethome base") == 1)
    check("/homes counts 2", r.value(who, "homes") == 2)
    check("/delhome removes", r.value(who, "delhome base") == 1)
    check("/homes back to 1", r.value(who, "homes") == 1)
    check("/home to a missing name refuses", r.value(who, "home nonsense") == 0)

    print("\n--- warps ---")
    r.run("delwarp shop")
    check("/setwarp works", r.value(who, "setwarp shop") == 1)
    check("/warps counts 1", r.value(who, "warps") == 1)
    check("/warp to a missing name refuses", r.value(who, "warp nope") == 0)
    check("/delwarp removes", r.value(who, "delwarp shop") == 1)

    print("\n--- kits ---")
    r.run(f"clear {who}")
    r.run(f"give {who} diamond 5")
    r.run(f"give {who} iron_ingot 3")
    time.sleep(0.5)
    made = r.value(who, "setkit probe all")
    check("/setkit captures items", made is not None and made >= 2, f"captured {made}")
    check("/kits lists it", (r.value(who, "kits") or 0) >= 1)
    check("/showkit lists contents", (r.value(who, "showkit probe") or 0) >= 2)
    r.run(f"clear {who}")
    check("/kit hands it back", r.value(who, "kit probe") == 1)
    check("/kit for a missing kit refuses", r.value(who, "kit nosuchkit") == 0)
    check("/delkit removes", r.value(who, "delkit probe") == 1)

    # --- the chat pipeline, driven through a REAL ServerChatEvent ---
    #
    # This is the gap the self-test cannot close. SelfTest runs on ServerStartedEvent with nobody
    # connected, so it can prove a function computes the right answer but never that anything
    # calls it — and "a path nothing has ever called" is the shape of most of this mod's bugs.
    # /standards testchat posts a genuine event on the real bus, so the mute gate, the router
    # offer, decoration and delivery all run as they would for a typed line.
    #
    # Harness borrowed from the LegendQuest session, which built the same thing to test the
    # router seam from RCON.
    print("\n--- chat pipeline (real events) ---")
    r.run(f"unmute {who}")
    time.sleep(0.3)
    check("an ordinary line goes through",
          r.value(who, "standards testchat hello world") == 1)

    r.run(f"mute {who} 10m battery test")
    time.sleep(0.4)
    # The one that was actually broken: a mute must stop chat at the gate, not merely at the
    # public channel. Yesterday a channel mod could step around this entirely.
    check("a muted player's chat is stopped",
          r.value(who, "standards testchat i am muted") == 0)
    check("and their /msg is stopped too, not just chat",
          r.value(who, f"msg {who} sneaking through") == 0)
    r.run(f"unmute {who}")
    time.sleep(0.4)
    check("unmuting lets chat through again",
          r.value(who, "standards testchat back again") == 1)

    # Colour codes: player text must never become formatting. The self-test proves the function;
    # this proves the function is actually on the path a message travels.
    check("a line with colour codes still delivers",
          r.value(who, "standards testchat &c&lnot red not bold") == 1)
    check("an ampersand in ordinary text still delivers",
          r.value(who, "standards testchat Tom & Jerry") == 1)

    print("\n--- mail ---")
    r.value(who, "mail clear")
    check("/mail send works", r.value(who, f"mail send {who} hello") == 1)
    check("/mail read shows it", r.value(who, "mail read") == 1)
    check("/mail clear empties", r.value(who, "mail clear") == 1)
    check("/mail read on empty refuses", r.value(who, "mail read") == 0)

    print("\n--- movement ---")
    r.run(f"tp {who} 100 200 100")
    time.sleep(0.4)
    check("/top from midair refuses or moves", r.value(who, "top") in (0, 1))
    r.run(f"tp {who} 100 100 100")
    time.sleep(0.4)
    check("/back returns somewhere", r.value(who, "back") in (0, 1))
    check("/spawn works", r.value(who, "spawn") == 1)

    # /setspawn is destructive and server-wide: whatever it leaves behind is the spawn every
    # later session uses. Left mid-air once, and the next morning "/spawn says nowhere safe to
    # land there" took a while to trace back to this line. spreadplayers puts them on actual
    # ground first, so the spawn this test leaves behind is one a walking player can arrive at.
    r.run(f"spreadplayers 100 100 1 1 false {who}")
    time.sleep(0.4)
    check("/setspawn works", r.value(who, "setspawn") == 1)
    check("and the spawn it left is reachable", r.value(who, "spawn") == 1)

    print("\n--- self care ---")
    r.run(f"effect give {who} minecraft:instant_damage 1 0")
    time.sleep(0.3)
    check("/heal works", r.value(who, "heal") == 1)
    check("/feed works", r.value(who, "feed") == 1)
    check("/rest works", r.value(who, "rest") == 1)
    check("/speed 2 works", r.value(who, "speed 2") == 1)
    check("/speed above the cap refuses", r.value(who, "speed 999") == 0)
    check("/speed reset works", r.value(who, "speed reset") == 1)

    print("\n--- switches ---")
    check("/fly on", r.value(who, "fly on") == 1)
    check("/fly on again reports no change", r.value(who, "fly on") == 0)
    check("/fly off", r.value(who, "fly off") == 1)
    check("/god on", r.value(who, "god on") == 1)
    check("/god off", r.value(who, "god off") == 1)
    check("/vanish on", r.value(who, "vanish on") == 1)
    check("/vanish off", r.value(who, "vanish off") == 1)
    check("/tptoggle off", r.value(who, "tptoggle off") == 1)
    check("/tptoggle on", r.value(who, "tptoggle on") == 1)

    print("\n--- moderation and server ---")
    check("/gc reports a TPS", (r.value(who, "gc") or 0) > 0)
    check("/mute then /unmute", r.value(who, f"mute {who} 30m testing") == 1
          and r.value(who, f"unmute {who}") == 1)
    check("/tempban with a bad duration refuses",
          r.value(who, f"tempban {who} bananas") == 0)
    check("/standards economy names a provider", (r.value(who, "standards economy") or 0) >= 1)
    check("/smite works", r.value(who, f"smite {who}") == 1)

    r.run(f"scoreboard objectives remove {OBJ}")
    print(f"\n=== {len(passed)} passed, {len(failed)} failed ===")
    for f in failed:
        print("  FAILED: " + f)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
