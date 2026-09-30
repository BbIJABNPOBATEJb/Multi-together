# Multi Together

A **Paper 1.21.11** plugin (Java 21) that links players with a chain, shared health, shared hunger
and a shared inventory. Built for videos and challenges: drop the jar into `plugins`, get two or more
players online, run `/link Player1 Player2` — done.

Links live in memory until the server stops. No files, no database.

## Features

| Type | How it works |
|---|---|
| ⛓ **CHAIN** | A real chain made of display entities between players: it sags, lies on the ground and pulls tight. You can't get farther apart than its length: a runner drags their partner, a player who falls off a ledge hangs on the chain. A portal, a world change, `/tp` or an ender pearl far away brings the whole chain along. |
| ❤ **HP** | One health bar for everyone. Hit one player — everyone flinches and hears the hurt sound. One dies — everyone dies ("Bob died together with Alice"). |
| ☕ **FOOD** | Shared hunger and saturation: one eats — everyone is fed, one sprints — everyone gets hungry. |
| ⚒ **INVENTORY** | One inventory, armor and offhand included. Players are handed the very same item list, so nothing is copied and nothing can be duplicated. The main hand stays personal (each player keeps their own selected hotbar slot). |

Health, hunger and inventory are shared across the whole group: `A—B` plus `B—C` means one health bar
for all three. The chain is pairwise: `A—B—C` is two chains, and the one in the middle holds both.

Death and switching worlds do not break links.

### Chain physics

It is a length constraint, not a spring along the whole chain: while the chain sags it does not touch
anyone. Once it is taut, both players get an impulse towards each other every tick — a share of the
overstretch plus damping of the speed at which they separate. The impulse is split by "mass":

- a player standing on the ground is heavier than one in the air;
- **a player sneaking on the ground braces** and is pulled several times weaker — that is how you hold
  a partner hanging over a cliff;
- speed is taken from the player's real movement with the same friction the client applies, so players
  never accelerate as if on ice.

When the chain catches a falling player, their fall distance is reset — no fall damage. A player hanging
on the chain is not kicked for flying, even with `allow-flight=false`.

### Visuals

Every link is a `BlockDisplay` of a chain block, rotated and stretched along its own segment. Position is
smoothed with teleport duration, rotation and length with transformation interpolation, so the chain moves
smoothly even though it updates once per tick. The sag follows a parabola based on the chain length, and
where it would dip into a block, the link rests on top of it instead. The chain can be iron or copper
(all four oxidation stages).

## Commands

Every command requires `multitogether.admin` (operators by default).

| Command | What it does |
|---|---|
| `/multitogether` | Menu (54-slot chest) |
| `/link <player1> <player2> [type]` | Link two players |
| `/link all [type]` | Link everyone online into one chain, alphabetically |
| `/link list` | Show all links |
| `/link length <blocks>` | Chain length, 1.5–32 |
| `/link settings` | Settings menu |
| `/unlink <player1> <player2> [type]` | Unlink a pair |
| `/unlink <player> [type]` | Unlink a player from everyone |
| `/unlink all [type]` | Remove all links |

**Type:** `ALL` (default), `CHAIN`, `HP`, `FOOD`, `INVENTORY`, or several separated by commas —
`HP,FOOD`. Russian names work too: `цепь`, `здоровье`, `голод`, `инвентарь`, `все`.

**Aliases.** `/multitogether` = `/together` = `/link` = `/mt` = `/связать`, and the same typed with a
Russian keyboard layout: `/ьгдешещпуерук`, `/ещпуерук`, `/дштл`, `/ье`. `/unlink` = `/отвязать` =
`/развязать` = `/гтдштл`. Subcommands too: `/дштл фдд` = `/link all`.

In-game messages are in Russian.

## Menu

`/link` with no arguments opens a 54-slot chest:

- **heads of online players** — left click to select (the number on the head is the position in the
  chain), right click to unlink them from everyone, Shift + left click to teleport to them. The head's
  tooltip lists who they are linked with;
- **type toggles** — chain, health, "everything", hunger, inventory;
- **buttons** — select all, clear selection, link selected, current links, unlink selected,
  remove all links (Shift), settings.

**Settings** apply immediately and last until restart: chain length, stiffness, chain style, visibility,
sneak bracing, respawn next to a partner, teleport the whole chain, chain sounds.

## Edge cases

- **Linking players with different inventories** — items are merged into one inventory, whatever does
  not fit drops at the owner's feet. Nothing is lost.
- **Unlinking** — every player keeps a copy of what was in the shared inventory.
- **Death without keepInventory** — the shared inventory drops once, where the dead player was.
- **Leaving and rejoining** — the player gets the shared inventory back: the snapshot saved on quit is
  outdated.
- **Respawn** — next to a living chain partner (can be disabled in settings). Leaving the End through
  the exit portal works the other way round: partners are pulled along.
- **Creative and spectator** players do not take part in shared health and hunger; spectators are also
  excluded from chain physics.
- **On linking**, a partner who is in another world or far away is brought to the first player.

## Installation

1. Download `MultiTogether-<version>.jar` from the releases or build it.
2. Put it into the `plugins` folder of a **Paper 1.21.11** server running Java 21.
3. Restart the server.

No dependencies. LiteCommands is relocated inside the jar
(`me.bbijabnpobatejb.multitogether.libs`), so it does not clash with other plugins that use LiteCommands.

The shared inventory relies on server internals (Mojang mappings), which is why the plugin targets
Paper 1.21.11 specifically. If those fields are not found on another version, the plugin still starts —
without the shared inventory, with an error in the log.

## Building

```bash
./gradlew shadowJar
```

The jar ends up in `build/libs/MultiTogether-1.0.0.jar`.

## Tested

On Paper 1.21.11-132 in Docker with mineflayer bots:

- the chain pulls (a runner drags a standing player at ~5.2 blocks with length 5), a chain of three
  pulls link by link;
- hanging over a cliff and in open air for 10 seconds — no damage, no flying kick;
- `/tp` far away, a nether portal, the End and back — the partner is brought along;
- shared health (averaged on link, damage to one hits everyone), dying together with a message,
  respawning together;
- shared hunger and eating, shared inventory with armor and offhand, lossless merge, copies on unlink,
  dropping without duplication, reattaching after rejoin;
- menu and settings via bot clicks; commands and Russian-layout aliases;
- link geometry: segment ends meet with no gaps from waist to waist.
