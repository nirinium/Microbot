# Grotesque Guardians inventory recovery

Under **Loot**, **Manage inventory space** handles game inventory-full messages during fights and makes room before picking up drops that need a new slot. Existing stackable loot needs no extra slot. Emergency movement and normal healing retain priority; inventory actions run off the client thread.

- **Eat for space** uses spare edible food, even at full health. **Keep food slots** defaults to one; ordinary low-health eating is unaffected. Multi-bite food may take multiple actions before a slot opens.
- **Drop listed items for space** only drops exact comma-separated names in **Discardable item names**, defaulting to `Vial, Jug, Bowl, Bucket`. Add unwanted cheap loot explicitly; arbitrary low-price inventory items are not candidates.
- **Maximum discard stack value** defaults to 1,000 gp for the entire stack, not each item. Unknown or zero prices are protected. The cheapest eligible stack is selected first.
- Gear, selected switch items, finishers, food, potions, runes, coins, untradeables and items exposing teleport/safety actions are protected even if listed. Items requested for dropping are excluded from subsequent looting for the run to prevent drop/pickup loops.
- Recovery waits for observed free slots rather than treating an interaction as success. Retries are limited and spaced by game ticks; repeated chat does not restart the budget. A blocked request is reconsidered when inventory or policy changes.
- If nothing safe can free a slot, the status explains the shortage. Loot requiring space is skipped for that kill rather than holding up combat indefinitely. Banking is not implemented.

Disable **Manage inventory space** to turn recovery off, or disable only dropping to retain food-only recovery. No live drops are performed as part of development tests.
