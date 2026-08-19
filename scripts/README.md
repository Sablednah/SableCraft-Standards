# scripts/

Test tooling for the dev server. Neither is needed to build or run the mod.

## `rcon.py` — a console you can actually drive

Gradle cannot pipe stdin to `runServer`, so there is no way to type into the dev server's console.
This talks to it over RCON instead, which turns every "op yourself and restart" into one command.

Enable it once in `run/server.properties`:

```properties
enable-rcon=true
rcon.port=25575
rcon.password=standards-dev
```

```bash
python3 scripts/rcon.py "list" "gamerule keepInventory true"
```

⚠ **LuckPerms' output never comes back over RCON.** Its commands run, but it replies to the sender
and swallows it for RCON clients. Check the effect, not the reply.

## `battery.py` — assertions, not smoke

Runs ~70 Standards commands and checks their **return values**. The trick that makes that possible:

```
execute store result score <holder> <objective> run <command>
scoreboard players get <holder> <objective>
```

Our commands return something meaningful — a count, a balance, 1 or 0 for worked/refused — so the
scoreboard turns "it did not error" into a real assertion. Every check has a negative twin: a
missing home must refuse, an over-cap speed must refuse, a second kit claim must hit the cooldown.

Needs the dev server up **and one player connected** (most commands need a player; the script drives
them with `execute as`):

```bash
./gradlew runServer            # WSL
.\TestClient.cmd               # Windows, connects TestBuddy
python3 scripts/battery.py
```

⚠ **`execute as <player>` does not test that player's permissions.** `requires()` is evaluated at
parse time against whoever typed the command, so everything here runs with console authority. That
makes the battery a test of *behaviour*, never of *permission boundaries* — those need the player
to type it themselves. See `TESTING.md`.
