---
title: "Examples"
order: 1
published: true
draft: false
---
# Examples

If you have not read the [syntax reference](Introduction) yet, it may be good to do first

## By feature

Looking for one specific operator or meta-key? Jump straight to the page that demonstrates it.

| Feature | What it does | Shown in |
|---|---|---|
| `+` append | add to the end of an array | [Bench-Category](Bench-Category), [Item-Category](Item-Category), [Droplists](Droplists) |
| `-` prepend | add to the front of an array | [Prepend-Array](Prepend-Array) |
| `?` fill-if-absent | set a key only if it is missing | [Fill-If-Absent](Fill-If-Absent) |
| `null` delete | remove a key, value, or recipe | [Removing-Values](Removing-Values) |
| `~` element merge | merge into one element of an array | [Match-Array-Element](Match-Array-Element) |
| `$Match` | locate which array element to merge | [Match-Array-Element](Match-Array-Element) |
| `$Requires` | apply only if certain packs are installed | [Parent-Override](Conditional-Override), [Droplists](Droplists) |
| `$Priority` | decide the winner when patches conflict | [Quality-Override](Quality-Override) |
| nested merge | change one field deep inside an object | [Recipe-Change](Recipe-Change), [Player-Stat](Player-Stat) |
| field replace | overwrite a single scalar value | [Quality-Override](Quality-Override) |
| forward reference | name an id from a later-loading pack | [Load-Order](Load-Order) |
| slot retarget | point one interaction slot at another handler | [Replace-Interaction](Replace-Interaction) |

## By scenario

| # | You want to... | Key technique |
|---|---|---|
| 1 | [Make one weapon work locally AND inside another mod](Conditional-Override) | reparent with `$Requires`, `null` the local interactions |
| 2 | [Add a category to a crafting bench](Bench-Category) | `Categories+` array append |
| 3 | [Give the player a new or bigger stat (like Mana)](Player-Stat) | patch an `Entity/Stats` definition |
| 4 | [Point an item's interaction at another mod's internal one](Replace-Interaction) | replace an `Interactions` slot value |
| 5 | [Change the quality of a block or armor piece](Quality-Override) | single-field replace |
| 6 | [Reference particles defined in a later-loading pack](Load-Order) | json reference + re-merge on register |
| 7 | [Remove a field, resistance, or recipe](Removing-Values) | `null` delete |
| 8 | [Add to a droplist or wire up a new one](Droplists) | `Containers+` append, repoint `DropList` |
| 9 | [Change a crafting recipe's ingredients or bench](Recipe-Change) | nested object merge |
| 10 | [Put an item into a creative or crafting category](Item-Category) | `Categories+` on the item |
| 11 | [Override one element inside an array](Match-Array-Element) | `~` element merge + `$Match` |
| 12 | [Add a stat only if the item doesn't already have it](Fill-If-Absent) | `?` fill-if-absent |
| 13 | [Put your entries at the front of an array](Prepend-Array) | `-` prepend |

## What Patchly can and cannot patch (for now)

Patchly only patches `.json` assets. The `.patch` path mirrors the target with `.json` swapped for `.patch`.

| Patchable (`.json`) | Not patchable |
|---|---|
| Items, blocks, recipes, droplists, stat definitions, interactions, entity configs | `.particlesystem`, `.particlespawner`, `.blockymodel`, `.blockyanim`, `.png`, `.lang` |

For a non-json asset, ship a normal full file in your pack instead of a `.patch`. You can still patch the `.json` that *references* it (see [Particles And Load Order](Load-Order)).
