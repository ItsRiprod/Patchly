---
title: "At A Glance"
order: 3
published: true
draft: false
---
# At A Glance

A brief overview of several functions

## Patching an existing asset with `.patch`

Mirror the path of the asset you are patching, swapping `.json` for `.patch`. To patch the iron helmet:

```
MyPack/
  manifest.json
  Server/
    Item/
      Items/
        Armor/
          Iron/
            Armor_Iron_Head.patch
```

The `.patch` contains only the fields you want to change:

```json
{
  "Armor": {
    "StatModifiers": {
      "Mana": [{ "Amount": 126, "CalculationType": "Additive" }]
    }
  }
}
```

The path is the only link to the source. Patchly resolves the target from whichever pack actually owns it; you never reference the source pack by name in the file. For the full merge rules (`+` append, `-` prepend, `?` fill-if-absent, `null` removal, `$Requires`, `$Priority`), see the [syntax reference](Introduction).

## Adding a whole new asset with `.put`

A `.patch` requires the target asset to already exist; if it does not, the patch is skipped. To **create** an asset, use a `.put` file instead. It carries the same syntax (operators, `$Requires`, `$Priority`, `$Match`, `$Import`) and resolves its target the same way (`.put` swapped for the asset extension, `Sword.put` and `Sword.json.put` both target `Sword.json`). The difference is the base:

| | Target missing | Target already exists |
|---|---|---|
| `.patch` | skipped, logged | merges onto it |
| `.put` | created from the file body | merges onto it |

The headline case is an asset that only makes sense alongside another mod. Pair `.put` with `$Requires` and the asset is created only when that mod is present:

```json
{
  "$Requires": "Riprod:Hexcode:^1.0.0",
  "Id": "Sword",
  "Damage": 12
}
```

If the asset already exists, a `.put` merges onto it rather than skipping. To create-but-never-clobber, tag the fields you only want to seed with `?` (fill-if-absent), e.g. `"Damage?": 12` leaves an existing `Damage` untouched.

## Reusing a template with `$Import`

When several assets share a chunk of configuration, define it once and `$Import` it instead of copy-pasting. The imported asset is layered in as a base; your own keys override it:

```json
{
  "$Import": "Template_Base_Item",
  "Quality": "Legendary",
  "ItemLevel": 100
}
```

The reference is an asset **id**, resolved only against assets of the **same type** as the file you are patching, so an item never imports a block that happens to share the id. Use a value containing `/` to point at an exact asset path. An import sees the target's base plus any `.put` that creates it (never other packs' `.patch`), so a template you ship as a `.put` can be `$Import`ed everywhere. Use `"$Import": [ "A", "B" ]` to layer several (later wins); a missing import is skipped with a log line.
> **Nesting.** A `$Import` inside a child object contributes only that asset's matching sub-tree (an `$Import` inside `Interactions` brings in only the imported asset's `Interactions`).

> **Arrays** Having `$Import` in an array will only work if you also have `$Match` present. It will import from the parent an array element that matches the Id of the Match into that element of the array


## Cross-mod patches with `$Requires`

A manifest dependency is all-or-nothing for the whole pack. To apply **one specific patch** only when some other mod is present, use `$Requires` inside that `.patch` file instead:

```json
{
  "$Requires": "Riprod:Hexcode",
  "Armor": {
    "StatModifiers": {
      "Mana": [{ "Amount": 126, "CalculationType": "Additive" }]
    }
  }
}
```

This patch is skipped (with a log line) unless `Riprod:Hexcode` is loaded. A single pack can carry optional compatibility patches for several mods, each activating only when its target is present. The pack itself still only hard-depends on `Riprod:Patchly`.

### Combining requirements

When `$Requires` is a list, each entry is a clause and **all** clauses must pass (AND). Inside a clause you have two more tools:

- **Require absent**: prefix a pack with `-`. The clause passes only if that pack is **not** loaded. Useful for a fallback that should disappear once a richer mod is installed.
- **Require any of**: comma-join packs in one clause. The clause passes if **at least one** is loaded.

```json
{ "$Requires": ["Author:A", "-Author:B", "Author:C,Author:D"] }
```

reads as `A && !B && (C or D)`: A must be present, B must be absent, and at least one of C/D must be present. Each pack still accepts an optional `:range`, e.g. `"Author:A:>=1.2.0"` or `"-Author:B:>=2.0.0"` (absent, or present below 2.0.0). Avoid comma-style version ranges, since the comma is read as the OR separator.

## Resolving conflicts with another pack

If two packs patch the same field, both apply in load order and the last one wins. To guarantee yours wins regardless of load order, bump `$Priority`:

```json
{ "$Priority": 100, "Armor": { "StatModifiers": { "Mana": [{ "Amount": 9999, "CalculationType": "Additive" }] } } }
```

Higher `$Priority` applies last and wins on conflicting fields. Lower-priority `+` appends from other packs still stack onto fields you did not touch.

# Array Operations

When working with arrays, there are 4 primary things you can do.

| Key | Operation | Description |
|---|---|---|
| `"array": []` | Replace | Replaces the array |
| `"array+": []` | Append | Adds to the end of the array |
| `"array-": []` | Prepend | Adds to the beginning of the array |
| `"array~": []` | Inject | Replaces elements at the corrosponding indexes |
| `"array~": [null]` | Inject | Deletes elements at the index |

### Overriding specific elements

Say you have the array
```json
{
    "array": [
        {
            "Id": "SomeId1",
            "Value": 100
        },
        {
            "Id": "SomeId2",
            "Value": 103
        },
        {
            "Id": "SomeId3",
            "Value": 152
        }
    ]
}
```
and you want to override `SomeId2` specifically, you can use `$Match`
```json
{ "array+": [
        {
            "$Match": "Id",
            "Id": "SomeId2",
            "Value": 900
        }
] }
```

Resulting in
```json
{ "array": [
        {
            "Id": "SomeId1",
            "Value": 100
        },
        {
            "Id": "SomeId2",
            "Value": 900
        },
        {
            "Id": "SomeId3",
            "Value": 152
        }
] }
```

### Edge Cases
For very edge cases, there is also 

| Key | Operation | Description |
|---|---|---|
| `"array++": []` | Always Append | Always adds the elements to the end |
| `"array--": []` | Always Prepend | Always adds the elements to the beginning |

By default, `+` and `-` will always dedupe before adding. So if you have
```json
{ "array": ["A", "B"] }
```
and you patch in
```json
{ "array+": ["B", "C"] }
```
the result is
```json
{ "array": ["A", "B", "C"] } // dedupes the "B"
```
but with
```json
{ "array++": ["B", "C"] }
```
the result is
```json
{ "array": ["A", "B", "B", "C"] } // keeps the extra "B"
```