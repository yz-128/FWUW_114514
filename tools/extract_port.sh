#!/bin/bash
# Build the port's inventory and diff it against the legacy packs.
cd "E:/JAVAMCMODGJ/FLAN 移植i/flanmod-template-1.21.1/src/main/resources/data/flansworldwars" || exit 1

OUT=/tmp/flan_diff
: > $OUT/port.txt

# Port folders -> legacy folder names.  The port renamed/split several kinds.
declare -A MAP=(
  [guns]=guns
  [bullets]=bullets
  [parts]=parts
  [attachments]=attachments
  [armour]=armorFiles      # legacy calls it armorFiles
  [vehicles]=vehicles
  [grenades]=grenades
  [tools]=tools
)

for d in "${!MAP[@]}"; do
  [ -d "$d" ] || continue
  for f in "$d"/*.json; do
    [ -f "$f" ] || continue
    b="${f##*/}"; b="${b%.json}"
    echo "${MAP[$d]}/$b" >> $OUT/port.txt
  done
done

sort -u $OUT/port.txt > $OUT/port_sorted.txt
echo "port unique definitions: $(wc -l < $OUT/port_sorted.txt)"
echo
echo "=== port by kind ==="
awk -F/ '{print $1}' $OUT/port_sorted.txt | sort | uniq -c | sort -rn
echo
echo "=== port data root (all kinds) ==="
ls -d */ 2>/dev/null | tr -d '/'
