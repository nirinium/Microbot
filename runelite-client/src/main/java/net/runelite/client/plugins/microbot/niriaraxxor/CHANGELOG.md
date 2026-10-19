# Niri Araxxor Changelog

Notable user-visible changes to the Niri Araxxor plugin are recorded here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/). Versions match
`AraxxorPlugin.VERSION`.

## [Unreleased]

Add new entries here under `Added`, `Changed`, or `Fixed`, then move them into a dated version
section when the plugin version is bumped.

## [1.0.71] - 2026-10-18

### Changed

- Banking now withdraws the complete armor and weapon setup before equipping it, then loads the
  remaining inventory supplies. This removes the repetitive withdraw-one, equip-one pattern.

## [1.0.70] - 2026-10-18

### Fixed

- Added a Grand Exchange coordinate fallback to bank detection so a transient object-cache miss
  cannot trigger a redundant POH and jewellery-box trip while already at the Exchange.
- Banking is now transaction-locked from the first setup load until inventory and equipment are
  verified. It cannot switch to POH travel after depositing items or a failed bank-cache refresh.
- Setup loading now uses progressive retry delays and stops with a concise bank-supplies message
  after three failures instead of retrying and logging every missing slot indefinitely.

### Changed

- Consolidated Inventory Setup matching and removed obsolete POH-pool state.
- Removed the duplicate boss-entry starting-tile pass and only repeats prayer management when an
  intervening equipment or attack-style change marked prayer state dirty.

## [1.0.69] - 2026-10-18

### Fixed

- Banking now checks for a nearby bank before selecting the POH route. Starting at the Grand
  Exchange or another bank goes directly to that bank instead of teleporting home and using the
  jewellery box to return to a bank.
- POH restoration now skips the rejuvenation pool when hitpoints and prayer are already fully
  restored.

## [1.0.68] - 2026-10-18

### Changed

- Startup now checks the complete selected Inventory Setup before beginning a banking trip. When
  both inventory and equipment already match, banking is skipped and travel to Araxxor begins
  immediately.

### Fixed

- A bank interface that was already open is closed before the setup-ready shortcut hands control
  to travel.

## [1.0.67] - 2026-10-18

### Added

- Added an explicit return-route setting with `Spider cave teleport` and `POH fairy ring (ALQ)`
  choices. The selected route is now followed consistently after banking and when starting inside
  the POH.

### Changed

- Updated the POH fairy-ring route from BKR to the closer ALQ destination.

## [1.0.66] - 2026-10-18

### Changed

- Return travel now prefers a `Spider cave teleport` from the selected Inventory Setup, landing
  directly beside the cave entrance. The POH fairy ring to BKR is retained as a fallback only.

### Fixed

- POH tablets and Spider Cave scrolls now require a confirmed-closed bank interface before their
  inventory interaction is attempted.

## [1.0.65] - 2026-10-18

### Changed

- Run is now kept enabled throughout the Araxxor encounter, with Acid Drip remaining the only
  forced-walk mechanic. Run is restored unconditionally as soon as Acid Drip ends.
- Enrage no longer sleeps for 600 ms after attack decisions. A ready ranged attack is queued in
  the same script iteration that confirms a successful cleave dodge when post-dodge delay is zero.

### Fixed

- A configured zero-tick enrage post-dodge delay no longer suppresses attacks until the next game
  tick; positive delay values still retain their intended safety window.

## [1.0.64] - 2026-10-18

### Fixed

- Preserved Araxxor-lair awareness between kills. The temporary no-boss window after looting now
  waits at the configured starting tile instead of launching an overworld web-walk from the
  instance and blocking detection of the next spawn.
- Added a second lair-location guard directly to the entrance router so an active Araxxor arena
  can never be passed to the synchronous web-walker.

## [1.0.63] - 2026-10-18

### Fixed

- Lair entry now remains latched across the instance-loading transition. Once the arena loads,
  the player walks to the configured starting tile before Araxxor is visible and before combat
  actions can take ownership of the loop.
- Prevented the entrance router from starting a new surface web-walk from inside Araxxor's lair.

## [1.0.62] - 2026-10-18

### Fixed

- POH detection now accepts a loaded fairy ring or spirit ring as direct house evidence instead
  of requiring the exit portal to be present in the active scene cache.
- A latched banking trip now retains control while crossing an instance boundary. It can no
  longer fall into Araxxor web-walking inside the POH and select a minigame teleport.

## [1.0.61] - 2026-10-18

### Fixed

- Latched a failed startup supply check into a required banking trip so POH loading transitions
  cannot make the script abandon banking before inventory and equipment are restored.
- Limited automatic setup validation to once per trip. Consuming the return POH tablet after
  banking no longer immediately starts another POH → bank → BKR loop.

## [1.0.60] - 2026-10-18

### Fixed

- Prevented a fully supplied script started inside a POH from handing the Araxxor surface
  destination to the overworld web walker. It now leaves through the configured BKR fairy ring.
- Separated equipment loading from inventory retries during banking. A failed inventory load no
  longer restarts equipment loading and repeatedly destabilizes weapon slots.

## [1.0.59] - 2026-10-18

### Fixed

- Fixed POH fairy-ring travel attempting to web-walk to the ring's instance-template coordinates.
  Loaded POH objects now use scene-local approach movement after a failed interaction, then retry
  normally without creating an impossible overworld route.
- Audited the return route: after BKR, the plugin web-walks to the Morytania Spider Cave entrance,
  enters it directly when loaded, then web-walks inside the cave to the Araxxor tunnel.

## [1.0.58] - 2026-10-18

### Added

- Added automatic death recovery. The plugin returns to the gravestone outside Araxxor's cavern,
  reclaims the lost items, restores equipment from the selected Inventory Setup, and then either
  resupplies through the existing banking flow or returns to the fight when banking is disabled.
- Added a `Recover after death` safety setting, enabled by default.

## [1.0.57] - 2026-10-18

### Changed

- Delayed the normal ruptura escape threshold from three tiles to two, allowing the araxyte one
  additional game tick to commit to its trigger tile before the player flees.
- Delayed the enraged ruptura escape threshold from four tiles to three while retaining an extra
  tile of safety compared with normal combat.
- Unified the urgent and fallback ruptura handlers so both use the same phase-aware thresholds.

### Fixed

- The player now walks to the configured starting tile as soon as the Araxxor instance loads,
  instead of waiting for Araxxor to become visible first.
- Protect from Melee remains active while the player moves from the lair entrance to the configured
  starting tile.

[Unreleased]: https://github.com/nirinium/Microbot/compare/v1.0.62...HEAD
[1.0.62]: https://github.com/nirinium/Microbot/compare/v1.0.61...v1.0.62
[1.0.61]: https://github.com/nirinium/Microbot/compare/v1.0.60...v1.0.61
[1.0.60]: https://github.com/nirinium/Microbot/compare/v1.0.59...v1.0.60
[1.0.59]: https://github.com/nirinium/Microbot/compare/v1.0.58...v1.0.59
[1.0.58]: https://github.com/nirinium/Microbot/compare/v1.0.57...v1.0.58
[1.0.57]: https://github.com/nirinium/Microbot/releases/tag/v1.0.57
