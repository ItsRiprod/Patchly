# v3.5.0

Added `.batch`, a folder-scoped patch whose reserved keys gate every source at or below its directory and whose body merges once into every target those sources produce.

Added `/patchly explain <target>`, which lists every source that built one asset in merge order.

Gated-source log lines moved from INFO to FINE.