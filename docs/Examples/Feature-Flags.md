---
title: "Feature Flags"
order: 16
published: true
draft: false
---
# Turn Content On And Off With Flags


## Goal

- Ship a heavier armor rebalance that is off by default.
- Let a server (or a companion mod) enable it by changing one value.
- Keep a legacy fallback that disappears the moment the rebalance is on.

## The flags

`Server/Globals.vars`
```json
{
  "HeavyArmor": false,
  "OldNumbers": false,
  "ArmorMult": 1.5,
  "AllowWithoutHexcode": true
}
```

Booleans are stored as numbers, `true` as `1` and `false` as `0`. Any number works as a flag: it is
on when greater than zero and off otherwise.

## Gating a patch

`Server/Item/Items/Armor/Adamantite/Armor_Adamantite_Head.patch`
```json
{
  "$Requires": "$HeavyArmor",
  "Armor": { "StatModifiers": { "Mana?": [ { "Amount#": "round(12 * $ArmorMult)", "CalculationType": "Additive" } ] } }
}
```

`Server/Item/Items/Armor/Adamantite/Armor_Adamantite_Head_Legacy.patch`
```json
{
  "$Requires": "-$HeavyArmor",
  "Armor": { "StatModifiers": { "Mana?": [ { "Amount": 12, "CalculationType": "Additive" } ] } }
}
```

With `HeavyArmor` false the legacy patch applies. Set it to `true` and the two swap places.

## Gating a folder

A `.batch` accepts the same clauses, so an entire folder can sit behind one flag:

`Server/Item/Items/Armor/Heavy/_.batch`
```json
{ "$Requires": "$HeavyArmor" }
```

## Mixing flags with packs and math

An entry without a colon is an expression; one with a colon is a pack id. They combine under the
usual rules: list entries are ANDed, commas inside an entry are ORed, and a leading `-` negates.

```json
{
  "$Requires": [
    "$HeavyArmor",
    "-$Globals.OldNumbers",
    "abs($ArmorMult - 1) * 10",
    "Riprod:Hexcode,$AllowWithoutHexcode"
  ]
}
```

`$Globals.Name` is the same as the bare `$Name`, qualified by its file like any other scope. Named
scopes work too: `"$Adamantite.Mana - 50"` passes when that material has more than 50 Mana.

## Overriding a flag from another pack

Same-named `.vars` files merge across packs in load order, last wins. A companion mod that ships

`Server/Globals.vars`
```json
{ "HeavyArmor": true }
```

turns the rebalance on for everyone without touching a single patch.

## Notes

- **`-` is boolean NOT, not arithmetic.** `-$Flag` passes when the flag is `0` or below, so
  `-$Zero` is true even though `-0 > 0` would be false. Arithmetic negation still works past the
  first character (`abs(-$X)`, `0 - $X`).
- **Unknown variables evaluate false.** A typo gates the source, and `/patchly explain <target>`
  shows the failing entry and the reason, so a typo looks different from a flag that is
  deliberately off.
- **What may gate what.** `Globals.vars` may only be gated by packs. A named `.vars` may be gated by
  packs and globals. `.patch`, `.put`, and `.batch` may use every scope. A gated-out named scope is
  absent, so a patch referencing `$Scope.Name` records an unresolved expression, and `explain`
  points at the `.vars` that was gated and why.
- **Java mods can read flags.** `PatchlyVars.getFlag("HeavyArmor")` works from any Patchly copy in
  the JVM. See [For Mod Developers](Mod-Developers).
- **`/patchly vars`** prints every resolved scope and value.
