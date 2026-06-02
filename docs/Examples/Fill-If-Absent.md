---
title: "Add a Stat Only If It Is Missing"
order: 12
published: true
draft: false
---
# Add a Stat Only If It Is Missing

Deep merge and `+` always win: whatever your patch writes, lands. Sometimes you want the
opposite. You are shipping a default and you only want it to apply when the target does NOT
already define it, so an item (or another mod) that already set its own value keeps it. That is
what the `?` fill-if-absent suffix does. The real case: hexcode wants every armor piece to grant
some Mana and Volatility, but armor that already carries its own Mana from another mod must be
left exactly as the author tuned it.

## Goal

- Give `Armor_Bronze_Hands` a `Volatility` modifier, because it has none.
- Do NOT overwrite its `Mana` modifier, because the item already defines one.
- Touch nothing else.

The intention here is to have a "soft add" - so that if "Mana" is NOT defined, it can be added here. But if it is, to NOT override it.

## The Asset

`Server/Item/Items/Armor/Bronze/Armor_Bronze_Hands.json`
```json
{
  "TranslationProperties": {
    "Name": "server.items.Armor_Bronze_Hands.name"
  },
  "Quality": "Common",
  "ItemLevel": 8,
  "PlayerAnimationsId": "Block",
  "ItemSoundSetId": "ISS_Armor_Light",
  "Categories": [
    "Items.Armors"
  ],
  "Icon": "Icons/ItemsGenerated/Armor_Bronze_Hands.png",
  "Model": "Items/Armors/Bronze/Hands.blockymodel",
  "Texture": "Items/Armors/Bronze/Hands_Texture.png",
  "Armor": {
    "ArmorSlot": "Hands",
    "BaseDamageResistance": 0,
    "StatModifiers": {
      "Mana": [  // already present
        {
          "Amount": 50,
          "CalculationType": "Additive"
        }
      ]
    }
  },
  "MaxDurability": 80,
  "DurabilityLossOnHit": 0.5,
  "Tags": {
    "Type": [
      "Armor"
    ],
    "Family": [
      "Bronze"
    ]
  }
}
```

## The patch
`Server/Item/Items/Armor/Bronze/Armor_Bronze_Hands.patch`

```json
{
  "Armor": {
    "StatModifiers": {
      "Mana?": [
        { "Amount": 200, "CalculationType": "Additive" }
      ],
      "Volatility?": [
        { "Amount": 26, "CalculationType": "Additive" }
      ]
    }
  }
}
```

`Mana?` and `Volatility?` each resolve independently against the base. `StatModifiers` already
has `Mana`, so the base wins and the patch's `200` is dropped. It has no `Volatility`, so that
one is written.

## Resulting Asset

- `Mana` is still the item's own `50`. The patch's `200` never landed, because the key was
  already there.
- `Volatility` is now `26`, added because the item had none.

```json
{
  "Armor": {
    "ArmorSlot": "Hands",
    "BaseDamageResistance": 0,
    "StatModifiers": {
      "Mana": [
        {
          "Amount": 50,
          "CalculationType": "Additive"
        }
      ],
      "Volatility": [
        {
          "Amount": 26,
          "CalculationType": "Additive"
        }
      ]
    }
  }
}
```

## Notes

- `?` checks **key presence**, not value type. It works the same whether the value is an array,
  a scalar, or an object: present means base wins and the patch value is dropped; absent means the
  patch value is written.
- It is **per key**, so you opt each field in independently. Drop the `?` from a key (e.g. plain
  `Mana`) and that key reverts to normal deep-merge / replace and overwrites the base.
- Contrast with `$Priority`: priority only orders patches against EACH OTHER, never against the
  base. A low `$Priority` still overwrites the base. `?` is the only way to let the base win.
- Ship the same `?` patch across a whole armor set: pieces that lack the stat get your default,
  pieces that already tune it keep theirs. See the array rules in the [syntax reference](index).
