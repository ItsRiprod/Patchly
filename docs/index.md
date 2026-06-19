---
title: "Introduction"
order: 1
published: true
draft: false
---
# Patchly - The Perfect Pure Patching Plugin

**Patch JSON assets instead of rewriting them**

Patchly lets one pack reach into another pack's asset and change only the fields it cares about, leaving everything else untouched. You drop a `.patch` file next to the asset you want to change, and Patchly merges it onto the resolved original.

## TL;DR

In your pack, create a file at the same path as the asset you want to change, swapping `.json` for `.patch`.

To add the mana stat to

`Server/Item/Items/Armor/Iron/Armor_Iron_Head.json`

make this in your pack

`Server/Item/Items/Armor/Iron/Armor_Iron_Head.patch`

and set it to

```json
{
  "Armor": {
    "StatModifiers": {
      "Mana": [{ "Amount": 126, "CalculationType": "Additive" }]
    }
  }
}
```

That's it. You've now patched some armor!

# Patch syntax

A `.patch` is plain JSON. Every key you write is merged onto the matching key in the resolved base asset.

| Feature | Example | What it does |
|---|---|---|
| Deep merge (objects) | `{ "A": { "B": 1 } }` | Only leaf `B` changes; sibling keys survive. |
| Replace array (default) | `"Categories": [...]` | Discards the parent's array, uses yours. |
| Append to array | `"Categories+": [...]` | Keeps the parent's entries, adds yours at the end, skipping any element already present. |
| Prepend to array | `"Categories-": [...]` | Keeps the parent's entries, adds yours at the front, skipping any element already present. |
| Append allowing duplicates | `"Categories++": [...]` | Like `+`, but never de-dups; appends even if an identical element already exists. |
| Prepend allowing duplicates | `"Categories--": [...]` | Like `-`, but never de-dups; prepends even if an identical element already exists. |
| Fill if absent | `"Mana?": [...]` | Writes the value only if the key is missing; otherwise the base wins. |
| Extend by index | `"Recipes~": [ {}, {...} ]` | Merges into the base element at each position; `{}` changes nothing. |
| Extend by field | `"Children+": [ { "$Match": "Id", "Id": "Tools", ... } ]` | Finds the element whose field matches and merges into it. |
| Delete a key | `"DamageResistance": null` | Removes that key from the merged asset. |
| Gate on packs | `"$Requires": "Group:Name"` or `"Group:Name:>=1.2.0"` or `[...]` | Patch applies only if all named packs are installed (and satisfy the optional version range); otherwise skipped with a log line. |
| Win on conflicts | `"$Priority": 100` | Integer, default 0. Higher applies last and wins on conflicting fields. |
| Inherit another asset | `"$Import": "Template_Base_Item"` or `[ ... ]` | Pulls in another same-type asset as a base layer beneath your keys. |

## Inheriting from another asset with `$Import`

`$Import` lets a patch inherit from another asset instead of copying its fields. The named asset is pulled in as a base layer; your own keys then override it.

```json
{
  "$Import": "Template_Base_Item",
  "Quality": "Legendary",
  "ItemLevel": 100,
  "PlayerAnimationsId?": "DynamicAnimation"
}
```

If `Template_Base_Item` defines `Quality`, `ItemLevel`, `PlayerAnimationsId` and `Interactions`, the result keeps the original asset's own fields, layers the template on top, then applies your overrides. `Quality`/`ItemLevel` become your values; `PlayerAnimationsId?` is only filled if neither the original nor the template already set it.

- **By id, type-scoped.** `"Template_Base_Item"` is resolved against assets of the **same type** as the file you are patching (an item only imports items, a block only blocks), so two unrelated assets sharing an id never collide. Use a value containing `/` to point at an exact asset path instead.
- **Base + `.put`, never `.patch`.** An import sees the target asset's base plus any `.put` that creates or seeds it, so you can ship a template with a `.put` and `$Import` it everywhere. It never sees other packs' `.patch` output, which keeps imports cycle-free.
- **Precedence**, highest first: your own non-`?` keys, then imports (a deeper `$Import` wins over a shallower one), then the original asset, then `?` fill fields.
- **Arrays and chains.** `"$Import": [ "A", "B" ]` layers several assets in order (later wins). An imported template may itself `$Import` another. A missing import is skipped with a log line.
- **Nesting.** A `$Import` inside a child object contributes only that asset's matching sub-tree (an `$Import` inside `Interactions` brings in only the imported asset's `Interactions`).

`.put` will add the file if it does not exist

`.patch` will only patch into the file if the base file exists

An overview of their implementation is found [here](Examples)

# Questions and Answers

| Question | Answer |
|---|---|
| Where do I put the `.patch`? | Same path as what you want to patch into. `.json` swapped for `.patch`. |
| Which packs can be patched? | Every registered pack: `folder`, `.zip`, and `.jar`. |
| Does it hot-reload? | Folder packs re-merge live on `.patch` edit. Zip/jar packs apply once at |

---

# Next steps

- **[For Pack Developers](Pack-Developers)** - ship `.patch` files in an asset-only pack and require Patchly as a dependency.
- **[For Mod Developers](Mod-Developers)** - bundle Patchly directly into your Java plugin with Gradle Shadow.
