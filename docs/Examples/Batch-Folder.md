---
title: "Batch A Folder"
order: 15
published: true
draft: false
---
# Gating And Decorating A Whole Folder

You ship twenty armor patches that only make sense when Hexcode is installed, and every one of them needs the same imbuement tag. Writing `$Requires` and the tag into all twenty files means twenty places to edit when either changes.

## Goal

- One folder holds every Hexcode-only armor patch.
- Nothing in it applies unless `Riprod:Hexcode` is loaded.
- Every asset it touches gains an imbuement slot, without any patch naming it.
- Adding a twenty-first patch means dropping in a file and nothing else.

## Before

Every file repeats both facts:

`Server/Item/Items/Armor/Armor_Iron_Head.patch`
```json
{
  "$Requires": "Riprod:Hexcode",
  "Tags": { "Imbuement+": ["Imbuement Slot"] },
  "Armor": {
    "StatModifiers": {
      "Mana": [{ "Amount": 126, "CalculationType": "Additive" }]
    }
  }
}
```

`Server/Item/Items/Armor/Armor_Iron_Chest.patch`
```json
{
  "$Requires": "Riprod:Hexcode",
  "Tags": { "Imbuement+": ["Imbuement Slot"] },
  "Armor": {
    "StatModifiers": {
      "Mana": [{ "Amount": 210, "CalculationType": "Additive" }]
    }
  }
}
```

## After

Move the patches into a folder and put the shared parts in one `.batch`:

```
Server/Item/Items/Patches/Hexcode/
  Imbuements.batch
  Armor_Iron_Head.patch
  Armor_Iron_Chest.patch
```

`Server/Item/Items/Patches/Hexcode/_.batch`
```json
{
  "$Requires": "Riprod:Hexcode",
  "Tags": { "Imbuement+": ["Imbuement Slot"] }
}
```

`Server/Item/Items/Patches/Hexcode/Armor_Iron_Head.patch`
```json
{
  "Armor": {
    "StatModifiers": {
      "Mana": [{ "Amount": 126, "CalculationType": "Additive" }]
    }
  }
}
```

`Server/Item/Items/Patches/Hexcode/Armor_Iron_Chest.patch`
```json
{
  "Armor": {
    "StatModifiers": {
      "Mana": [{ "Amount": 210, "CalculationType": "Additive" }]
    }
  }
}
```

Two outcomes, decided entirely by whether Hexcode is loaded.

Hexcode absent:

- The `.batch` requirement is not satisfied, so every source at or below its folder is skipped.
- Both armor pieces are untouched. No override is written.

Hexcode present:

- Both patches apply, and both outputs also carry `Imbuement Slot` in `Tags.Imbuement`.
- The tag is appended once per asset, not once per patch, so two patches targeting one asset still produce a single slot.

## Moving the files is safe

Assets are keyed by filename within their store root, and the store directory is scanned recursively, so `Armor_Iron_Head.patch` still binds to `Armor_Iron_Head.json` from inside `Patches/Hexcode/`. The folder is free organization; only the condition is new.

## Narrowing a subfolder

Nest another `.batch` to add a requirement or a decoration for part of the tree:

```
Server/Item/Items/Patches/Hexcode/
  _.batch                     -> requires Riprod:Hexcode
  Heavy/
    _.batch                   -> also requires Riprod:Icarus
    Armor_Adamantite_Chest.patch
```

`Server/Item/Items/Patches/Hexcode/Heavy/_.batch`
```json
{
  "$Requires": "Riprod:Icarus",
  "Quality": "Legendary"
}
```

Requirements combine, so `Heavy/` needs both mods while the rest of the folder needs only Hexcode. An inner `.batch` can narrow an outer one but never widen it.

## Notes

- A `.batch` decorates targets, it never creates one. If no `.patch` or `.put` produces the asset, the batch contributes nothing to it.
- A sibling `.patch` overrides the `.batch` body at equal priority, so the batch is a folder default. Set `$Priority` on the `.batch` to make it win instead. Bodies layer outer folder first, so an inner `.batch` overrides an outer one.
- `.vars` are exempt from batch gating. A gated folder still contributes its variable scopes, so a patch elsewhere using `$Scope.Name` cannot break because an unrelated mod is missing. See [Variables](Variables).
- Because a `.batch` can change an asset that never names it, use `/patchly explain <asset>` to see every source that built one target in merge order.
