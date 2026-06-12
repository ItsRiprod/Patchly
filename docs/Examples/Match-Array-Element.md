---
title: "Match Array Element"
order: 11
published: true
draft: false
---
# Override One Array Element

`+` and replace treat an array as a whole. Sometimes you need to reach INTO one existing
element and add fields to it. The real case: the Creative Library's `Items.json` has a
`Children` array of categories, and you want to add `SubCategories` to the stock `Tools`
category without disturbing the other six categories.

## Goal

- Add a `SubCategories` list to the existing `{ "Id": "Tools" }` element.
- Leave `Weapons`, `Armors`, `Foods`, `Potions`, `Recipes`, `Ingredients` and the `Tools`
  element's own `Name`/`Icon` exactly as they are.

## The Asset
`Server/Item/Category/CreativeLibrary/Items.json`

Without an element-targeting operator your only options are replace (restate every element) or append (a new sibling category in its own tab, not under `Tools`).

```json
{
  "Icon": "Icons/ItemCategories/Items.png",
  "Order": 2,
  "Children": [
    {
      "Id": "Tools",                                  // the ONE element you want to extend
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
    },
    {
      "Id": "Foods",
      "Name": "server.ui.itemcategory.foods",
      "Icon": "Icons/ItemCategories/Items-Food.png"
    },
    {
      "Id": "Potions",
      "Name": "server.ui.itemcategory.potions",
      "Icon": "Icons/ItemCategories/Items-Potion.png"
    },
    {
      "Id": "Recipes",
      "Name": "server.ui.itemcategory.recipes",
      "Icon": "Icons/ItemCategories/Items-Recipe.png"
    },
    {
      "Id": "Ingredients",
      "Name": "server.ui.itemcategory.ingredients",
      "Icon": "Icons/ItemCategories/Items-Ingredients.png"
    }
  ]
}
```

## The patch

Path: `Server/Item/Category/CreativeLibrary/Items.patch`

```json
{
  "Children+": [
    {
      "$Match": "Id",
      "Id": "Tools",
      "SubCategories+": [
        { "Id": "Staffs", "Name": "server.ui.itemcategory.subcategory.staffs.name", "Order": 100 }
      ]
    }
  ]
}
```

`Children~` opts the array into element merging. `$Match: "Id"` finds the element whose `Id`
is `Tools`. The rest of the object merges into it, and the nested `SubCategories+` appends
(creating the array, since `Tools` had none).

## After

- The `Tools` element now has a `SubCategories` array with your `Staffs` entry.
- `Tools` keeps its `Name` and `Icon`. Every sibling category is untouched.
- `$Match` is stripped and never appears in the merged asset.

```json
{
  "Id": "Tools",
  "Name": "server.ui.itemcategory.tools",
  "Icon": "Icons/ItemCategories/Items-Tools.png",
  "SubCategories": [
    { "Id": "Staffs", "Name": "server.ui.itemcategory.subcategory.staffs.name", "Order": 100 }
  ]
}
```

## Targeting by position instead

Not every array is keyed by a field. When there is nothing to match on, drop `$Match` and let
`~` align by position. The patch element at each index merges into the base element at the
same index, and an empty `{}` merges nothing, so it skips a slot:

```json
{ "SomeList~": [ {}, { "Enabled": false } ] }
```

This leaves index 0 alone and sets `Enabled` on index 1.

## Notes

- `~` is the element-merge operator; `$Match` is an optional locator inside it (and inside `+`).
  There is no implicit `Id` matching: matching happens only where you write `$Match`.
- On a `$Match` miss the suffix decides the fallback: `~` merges at the index, `+` appends
  (upsert), a bare key does nothing. Pair `$Match` with `~` or `+` when you want a defined
  fallback.
- `SubCategories+` inside the matched element shows that operators compose to any depth. See
  the [syntax reference](Introduction) for the full ruleset.
- A plain array with no `$Match` and no `~`/`+` still replaces wholesale, exactly as before.
