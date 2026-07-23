# v3.4.2

Added Variables
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
