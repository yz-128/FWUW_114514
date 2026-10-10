#!/bin/bash
# Extract normalized content inventories from the three legacy 1.7.10 packs
# and from the ported mod, then diff them.
cd "E:/JAVAMCMODGJ/FLAN 移植i" || exit 1

OUT=/tmp/flan_diff
rm -rf $OUT && mkdir -p $OUT

PACKS=(
  "Flan/Warfare44ContentPack- Version 3.2.jar"
  "Flan/Warfare-44SupplementalPack.zip"
  "Flan/Warfare-44 CoHM Expansion Pack.zip"
)

# ---- 1. legacy: list every definition, keyed by <kind>/<basename> ----
: > $OUT/legacy_all.txt
for p in "${PACKS[@]}"; do
  unzip -l "$p" 2>/dev/null | awk '{print $4}' | grep -E "\.txt$" | while read -r path; do
    kind="${path%%/*}"
    base="${path##*/}"; base="${base%.txt}"
    echo "$kind/$base" >> $OUT/legacy_all.txt
  done
done
sort -u $OUT/legacy_all.txt > $OUT/legacy.txt

echo "legacy unique definitions: $(wc -l < $OUT/legacy.txt)"
echo
echo "=== legacy by kind ==="
awk -F/ '{print $1}' $OUT/legacy.txt | sort | uniq -c | sort -rn
