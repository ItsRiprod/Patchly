---
title: "Droplists"
order: 8
published: true
draft: false
---
# Add to a Droplist or Wire a New One

Droplists live in `Server/Drops/`. A block points at one by id, and breaking the block rolls that list. You want a block to drop something extra, or to drop from a list you authored. There are two distinct tasks here, and only one of them is a patch each time.

## Goal

- Goal 1: gravel keeps its stock drop but also has a chance to roll a `PortalKey_Maze_Var1`, but only when `Icarus:EchoOfIcarus` is present.
- Goal 2: a block you do not own should drop from a brand-new droplist you wrote.

## The Original Asset

Without Patchly, changing one entry means your override file has to restate the whole asset, because Hytale does not deep-merge nested blocks. Here are the entire real files you would copy and maintain the old way, with the parts you actually wanted marked.

For Case 1, the stock gravel droplist at `Server/Drops/Soil_Gravel.json`:

```json
{
  "Container": {
    "Type": "Multiple",
    "Containers": [ // you want to ADD one more drop here, not replace what is here
      {
        "Type": "Single",
        "Item": {
          "ItemId": "Rubble_Stone",
          "QuantityMax": 2,
          "QuantityMin": 1
        }
      }
    ]
  }
}
```

For Case 2, a real block that references a droplist by id, the entire `Server/Item/Items/Soil/Gravel/Soil_Gravel_Sand.json`:

```json
{
  "Parent": "Soil_Gravel",
  "TranslationProperties": {
    "Name": "server.items.Soil_Gravel_Sand.name"
  },
  "Icon": "Icons/ItemsGenerated/Soil_Gravel_Sand.png",
  "Categories": [
    "Blocks.Rocks"
  ],
  "Set": "Rock_Sandstone",
  "BlockType": {
    "Textures": [
      {
        "All": "BlockTextures/Soil_Gravel_Sand.png",
        "Weight": 1
      }
    ],
    "Gathering": {
      "Breaking": {
        "GatherType": "Soils",
        "DropList": "Rubble_Sandstone" // you want to point this at your own list
      }
    },
    "ParticleColor": "#c69f4f",
    "TransitionTexture": "BlockTextures/Transition_Gravel_Sand.png"
  }
}
```

## The patch

### Case 1 - add a drop to an existing list

Patch at `Server/Drops/Soil_Gravel.patch`:

```json
{
  "$Requires": "Icarus:EchoOfIcarus",
  "Container": {
    "Containers+": [
      {
        "Type": "Single",
        "Item": {
          "ItemId": "PortalKey_Maze_Var1",
          "QuantityMax": 1,
          "QuantityMin": 0
        }
      }
    ]
  }
}
```

`PortalKey_Maze_Var1` is a real Icarus item, so `$Requires: "Icarus:EchoOfIcarus"` gates the whole patch: the bonus drop only appears when the Icarus pack is installed. The `+` appends; plain `"Containers"` would wipe the stock `Rubble_Stone` drop. See [Replace vs append](Introduction).

### Case 2 - point a block at a brand-new droplist

First create the new list. This is a normal asset, not a patch (there is nothing to merge with). Write `Server/Drops/Custom_Gravel_Drops.json` with its own `Container`. Then patch the block to use it. The block file is `Server/Item/Items/Soil/Gravel/Soil_Gravel_Sand.json`, so the patch is `Server/Item/Items/Soil/Gravel/Soil_Gravel_Sand.patch`:

```json
{
  "BlockType": {
    "Gathering": {
      "Breaking": {
        "DropList": "Custom_Gravel_Drops"
      }
    }
  }
}
```
Result
## After

- Case 1: gravel still drops `Rubble_Stone` (1-2) AND now also rolls `PortalKey_Maze_Var1`, but only when `icarus:legacy` is present. The original entry survives because of the `+`.
- Case 2: breaking `Soil_Gravel_Sand` now rolls `Custom_Gravel_Drops` instead of its old `Rubble_Sandstone` list.