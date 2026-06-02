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

A `.patch` is plain JSON. Every key you write is merged onto the matching key in the resolved base asset. Here is every rule at a glance, then each one explained.

| Feature | Example | What it does |
|---|---|---|
| Deep merge (objects) | `{ "A": { "B": 1 } }` | Only leaf `B` changes; sibling keys survive. |
| Replace array (default) | `"Categories": [...]` | Discards the parent's array, uses yours. |
| Append to array | `"Categories+": [...]` | Keeps the parent's entries, adds yours at the end. |
| Prepend to array | `"Categories-": [...]` | Keeps the parent's entries, adds yours at the front. |
| Fill if absent | `"Mana?": [...]` | Writes the value only if the key is missing; otherwise the base wins. |
| Extend by index | `"Recipes~": [ {}, {...} ]` | Merges into the base element at each position; `{}` changes nothing. |
| Extend by field | `"Children+": [ { "$Match": "Id", "Id": "Tools", ... } ]` | Finds the element whose field matches and merges into it. |
| Delete a key | `"DamageResistance": null` | Removes that key from the merged asset. |
| Gate on packs | `"$Requires": "Group:Name"` or `"Group:Name:>=1.2.0"` or `[...]` | Patch applies only if all named packs are installed (and satisfy the optional version range); otherwise skipped with a log line. |
| Win on conflicts | `"$Priority": 100` | Integer, default 0. Higher applies last and wins on conflicting fields. |

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
