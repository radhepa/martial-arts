# Martial Arts

Hand-to-hand combat for Minecraft, built for combos: punches and kicks that chain, cancel, launch and juggle.
Fabric, Minecraft 26.3, Fabric API. It stands alone; the Shinobi Modpack uses it for taijutsu.

## Controls

- **Punch**: attack button with an empty main hand, aimed at the air or a creature (blocks still mine).
- **Kick**: `V` (Options > Controls > Martial Arts).

## Strings

| Input | Ground | Air |
|---|---|---|
| P | jab | air jab |
| P P | cross | air cross |
| P P P | hook | air hook |
| P P P P | uppercut (launches) | |
| K | front kick (pushes) | axe kick (spikes down) |
| P K | low kick | |
| P P K | roundhouse | |
| P P P K | spinning kick | |
| K K | side kick | |
| K P | knee (launches) | |

A press made during a move is buffered and comes out in the last ticks of its recovery, so mashing in rhythm flows.
Pause too long and the string starts over. Launch with the uppercut or knee, jump, and the air punches keep the target
(and you) up; finish with the axe kick. Hits within 1.5 s of each other count as one combo; each hit after the first
deals 8% less (never under 40%).

## Build

JDK 25: `./gradlew build` (the jar is in `build/libs/`), `./gradlew runClient` to play.

For other mods: `MartialArts.HIT` fires on the server after each strike lands, with the move and the combo's length.
