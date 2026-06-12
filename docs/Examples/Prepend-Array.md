---
title: "Prepend Array"
order: 13
published: true
draft: false
---
# Prepend to an Array

`+` adds your entries to the END of an array. When order is meaningful - menu tabs, priority
lists, anything rendered top-to-bottom - you sometimes need your entries at the FRONT instead.
The `-` suffix prepends: it inserts before the existing elements, preserving the order you wrote
them in. The real case: you want hexcode's Arcane category to be the FIRST tab in the Creative
Library, not the last.

## Goal

- Put a new `Hexcode` category at the front of the Creative Library's top-level `Children`.
- Keep `Tools`, `Weapons`, `Armors` and the rest exactly as they are, just after yours.

## The Asset
`Server/Item/Category/CreativeLibrary/Items.json`

The `Children` array is the tab order, top to bottom; you want to land before the first entry.

```json
{
  "Icon": "Icons/ItemCategories/Items.png",
  "Order": 2,
  "Children": [
    {
      "Id": "Tools",  // currently first; you want to sit before it
      "Name": "server.ui.itemcategory.tools",
      "Icon": "Icons/ItemCategories/Items-Tools.png"
    },
    {
      "Id": "Weapons",
      "Name": "server.ui.itemcategory.weapons",
      "Icon": "Icons/ItemCategories/Items-Weapons.png"
    },
    {
      "Id": "Armors",
      "Name": "server.ui.itemcategory.armors",
      "Icon": "Icons/ItemCategories/Items-Armor.png"
    }
  ]
}
```

## The patch

Path: `Server/Item/Category/CreativeLibrary/Items.patch`

```json
{
  "Children-": [
    {
      "Id": "Hexcode",
      "Name": "hexcode.itemcategory.hexcode.name",
      "Icon": "Icons/ItemCategories/Hexcode.png"
    }
  ]
}
```

The key is `Children-`, not `Children` or `Children+`. The `-` prepends to the existing array. A
plain `Children` would REPLACE all three stock tabs; `Children+` would put `Hexcode` last.

## After

`Hexcode` is now the first element; the three stock categories follow in their original order.

```json
{
  "Children": [
    {
      "Id": "Hexcode",
      "Name": "hexcode.itemcategory.hexcode.name",
      "Icon": "Icons/ItemCategories/Hexcode.png"
    },
    {
      "Id": "Tools",
      "Name": "server.ui.itemcategory.tools",
      "Icon": "Icons/ItemCategories/Items-Tools.png"
    },
    {
      "Id": "Weapons",
      "Name": "server.ui.itemcategory.weapons",
      "Icon": "Icons/ItemCategories/Items-Weapons.png"
    },
    {
      "Id": "Armors",
      "Name": "server.ui.itemcategory.armors",
      "Icon": "Icons/ItemCategories/Items-Armor.png"
    }
  ]
}
```

## Multiple elements keep their order

Prepend preserves the order of the elements you write. `"Children-": [ A, B ]` against a base of
`[ X ]` yields `[ A, B, X ]`, not `[ B, A, X ]`. Front insertion does not reverse them.

## Prepending with `$Match`

`-` is fully symmetric with `+` under `$Match`. On a **hit** it deep-merges into the matched base
element in place (it does not move it); on a **miss** it prepends the new element. So you can
upsert-to-front: merge if the element already exists, otherwise add it at the front.

```json
{
  "Children-": [
    { "$Match": "Id", "Id": "Tools", "Order": 5 },
    { "Id": "Hexcode", "Name": "hexcode.itemcategory.hexcode.name", "Icon": "Icons/ItemCategories/Hexcode.png" }
  ]
}
```

`Tools` already exists, so it is merged in place (gains `Order: 5`, stays where it is). `Hexcode`
matches nothing, so it is prepended. `$Match` is stripped from the output.

## Notes

- `-` creates the array if the key is absent, exactly like `+`. Against no base, `"Children-": [A]`
  just yields `[A]`.
- If the value is not an array, `-` does nothing (same guard as `+`).
- `-` means **prepend** in Patchly. It is NOT a remove operator. To remove array elements you
  restate the array with only the survivors (see [Remove a Field or Recipe](Removing-Values)).
- Prepend and append are independent: `Children-` and `Children+` in the same patch both apply,
  yielding `[ prepended..., base..., appended... ]`. See the array rules in the
  [syntax reference](Introduction).
