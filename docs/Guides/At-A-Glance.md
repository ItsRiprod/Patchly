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

## Rules for a whole folder with `.batch`

A `.batch` has no target of its own. Its reserved keys gate every source at or below its directory, and its body merges once into every target those sources produce. Use it when a group of patches share a condition, a decoration, or both:

`Server/Item/Items/Patches/Hexcode/HexcodeGate.batch`
```json
{
  "$Requires": "Riprod:Hexcode",
  "Tags": { "Imbuement+": ["Imbuement Slot"] }
}
```

Without Hexcode, every `.patch` and `.put` in that folder is skipped. With Hexcode, they all apply and every asset they touch also gains the imbuement tag, without any of them naming it. Drop a new patch into the folder later and it inherits both, which is the point: the folder is the condition.

The body accepts everything a `.patch` does, including `+`, `-`, `~`, `?`, `null` deletes, `$Match` and `#` expressions.

| | Applies to |
|---|---|
| `.patch` / `.put` | its own target |
| `.batch` reserved keys | every source at or below its folder |
| `.batch` body | every target those sources produce, once each |

Name it anything you wish; Avoid naming it after a real asset, since that reads as if it targets one.

> Note: This does __not__ apply to .json files due to technical limitations of hytale and overhead

### Nesting and precedence

`.batch` files nest. Requirements combine, so an inner folder can narrow an outer one but never widen it. Bodies layer outer first, so an inner `.batch` overrides an outer one.

Within a folder, a sibling `.patch` overrides the `.batch` body at equal priority, because the folder default is meant to be overridable. Give the `.batch` a higher `$Priority` when you want it to win instead.

### Two boundaries

- A `.batch` decorates targets, it never creates one. If no `.patch` or `.put` produces the asset, the batch contributes nothing to it. To create assets conditionally, put a `.put` in the folder.
- `.vars` are exempt from batch gating. A gated folder still contributes its variable scopes, so a patch elsewhere referencing `$Scope.Name` cannot break just because an unrelated mod is missing.

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

> **Arrays.** `$Import` inside an array element needs a `$Match` alongside it. It looks up the same array in the imported asset, finds the element whose `$Match` field equals yours, and layers that in before your own keys. This works whether the element already exists in the base array or is being appended by `+`, so a new entry can pull the template too. If the imported asset has no matching element, nothing is imported and your own keys still apply. Without `$Match` there is nothing to select by, so the import is skipped.


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

### Feature flags

An entry with no colon is an expression over `.vars` variables, and passes when it evaluates greater than zero. Put `true`/`false` in `Globals.vars`:

`Server/Globals.vars`
```json
{ "AssetIsEnabled": true, "OldNumbers": false, "UseFallback": true }
```

and reference it bare or qualified by its file:

```json
{ "$Requires": ["$AssetIsEnabled", "-$OldNumbers", "Riprod:SomePack,$Globals.UseFallback"] }
```

Flags and packs combine under the same AND/OR/NOT rules. A leading `-` is boolean NOT, so `-$Flag` passes when the value is `0` or below. An unknown variable evaluates false and gates the source; `/patchly explain <target>` prints the reason. `Globals.vars` itself may only be gated by packs, a named `.vars` by packs and globals, and everything else by any scope. See [Feature-Flags](Feature-Flags).

## Resolving conflicts with another pack

If two packs patch the same field, both apply in load order and the last one wins. To guarantee yours wins regardless of load order, bump `$Priority`:

```json
{ "$Priority": 100, "Armor": { "StatModifiers": { "Mana": [{ "Amount": 9999, "CalculationType": "Additive" }] } } }
```

Higher `$Priority` applies last and wins on conflicting fields. Lower-priority `+` appends from other packs still stack onto fields you did not touch.

## Deriving values with `.vars` and `#`

When many assets share numbers, define them once in a `.vars` file and compute per-asset values with a `#` key instead of hardcoding. A `.vars` holds plain numbers and its filename is the scope:

`Armor.vars`
```json
{ "Spread_Head": 0.15, "Mult_Mana": 1.0 }
```

`Adamantite.vars`
```json
{ "Mana": 80 }
```

Suffix a numeric key with `#` to make its value an expression, and reference a variable as `$Scope.Name`:

```json
{ "Armor": { "StatModifiers": { "Mana?": [
  { "Amount#": "round($Adamantite.Mana * $Armor.Spread_Head * $Armor.Mult_Mana)", "CalculationType": "Additive" }
] } } }
```

Patchly evaluates it and writes `12` to `Amount`. Expressions support `+ - * / ( )` and `round floor ceil abs int min max clamp`. `Globals.vars` is referenced bare (`$Name`, or `$Globals.Name`); every other file is a named scope (`$Filename.Name`). A `.vars` value may also be `true` or `false`, stored as `1` or `0`. A named `.vars` value may reference globals but not another scope, so do cross-scope math in the patch. A bad expression is skipped with a log line, never fatal, and `.vars` files are never emitted as assets. `/patchly vars` prints every resolved value. See [Variables](Variables) for the full walkthrough.

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