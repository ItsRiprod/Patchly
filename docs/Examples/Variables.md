---
title: "Variables"
order: 14
published: true
draft: false
---
# Compute Values From Shared Variables

Hardcoding the same numbers across dozens of assets means a balance change is dozens of edits.
`.vars` files let you define numbers once and reference them from any patch, and `#` expression
keys do the math. The real case: hexcode gives all 25 armor materials Mana that splits across the
four pieces; instead of hand-tuning 99 `Amount` values, each piece derives its number from one
per-material total and a shared piece spread, so a rebalance edits a single line.

## Goal

- Give every Adamantite piece a Mana modifier whose value is `total * piece-spread`.
- Keep the spread (how Mana splits across Head/Chest/Legs/Hands) in one shared place, so changing
  it reshapes every set at once.
- Rebalance a whole material by editing one number.

## The variables

A `.vars` file holds plain numbers. Its filename is the scope, and it can live anywhere under the
same pack. Two files here: one shared, one per-material.

`Server/Item/Items/Armor/Armor.vars`
```json
{
  "Spread_Head": 0.15,
  "Spread_Chest": 0.2,
  "Spread_Legs": 0.275,
  "Spread_Hands": 0.375,
  "Mult_Mana": 1.0
}
```

`Server/Item/Items/Armor/Adamantite/Adamantite.vars`
```json
{
  "Mana": 80
}
```
> *Note: The file location of `.vars` files __does not matter__ - only the name does. You can put this anywhere. For convenience, I always place it close to where it is used*

## The patch

Suffix a numeric key with `#` and its value becomes an expression. Reference a variable as
`$Scope.Name`.

`Server/Item/Items/Armor/Adamantite/Armor_Adamantite_Head.patch`
```json
{
  "Armor": {
    "StatModifiers": {
      "Mana?": [
        {
          "Amount#": "$Adamantite.Mana * $Armor.Spread_Head * $Armor.Mult_Mana", 
          "CalculationType": "Additive"
        }
      ]
    }
  }
}
```

Patchly evaluates the expression, writes the result to `Amount` (dropping the `#`), then merges as
normal. `80 * 0.15 * 1.0` is `12`, and a whole result is written as an integer.

## Resulting Asset

```json
{
  // ... rest of Adamantite Armor
  "Armor": {
    "StatModifiers": {
      "Mana": [
        {
          "Amount": 12,
          "CalculationType": "Additive"
        }
      ]
    }
  }
}
```

The Chest piece reads `$Armor.Spread_Chest` and lands `16`; Legs `22`; Hands `30`. Bump
`Adamantite.vars` `Mana` to `100` and all four recompute. Bump `Armor.vars` `Mult_Mana` to `2.0`
and every material's Mana doubles at once.

## Notes

- **Two kinds of scope.** `Globals.vars` (exact spelling) is the one file you reference bare, as
  `$Name`, or as `$Globals.Name` like any other scope. Every other file is a named scope you
  reference as `$Filename.Name` — the filename is the scope and folders are ignored, so `Armor.vars`
  is reached as `$Armor.Spread_Head` wherever it sits.
- **Booleans are numbers.** A value of `true` is stored as `1` and `false` as `0`, so a flag can be
  multiplied like any other variable and used in `$Requires`. See [Feature-Flags](Feature-Flags).
- **`#` goes on the key, the expression is the value.** `"Amount#": "..."`. It resolves anywhere a
  number lives, including inside an array element like the `Mana?` one above. On success the `#`
  key is replaced by the plain key; a whole result writes as an integer (`12`), a fractional one as
  a decimal (`4.5`).
- **Math you can use:** `+ - * / ( )` and `round floor ceil abs int min max clamp`. Wrap in
  `round(...)` when you want clean integers, since `total * spread` rarely lands whole.
- **Named vars may reference globals, not each other.** A value inside `Adamantite.vars` can use
  `$Some_Global`, but not `$Armor.X`. That keeps resolution simple and cycle-free; do the
  cross-scope math in the patch, where any scope is fair game.
- **A broken expression is skipped, not fatal.** An undefined variable or a typo leaves the `#` key
  in place and logs `[patcher] unresolved expression ...`; the field falls back to whatever the
  base had, and the rest of the asset is untouched.
- **`.vars` files are never emitted as assets.** They exist only to feed expressions; nothing is
  written for them into the patched output. Same-named `.vars` across packs merge (and honour
  `$Priority`), so another mod can rebalance your numbers without touching your patches.
