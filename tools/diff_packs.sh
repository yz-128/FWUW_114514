#!/bin/bash
# Full diff: legacy 1.7.10 packs vs ported mod content.
cd "E:/JAVAMCMODGJ/FLAN 移植i" || exit 1
OUT=/tmp/flan_diff

# ---------- port side ----------
cd flanmod-template-1.21.1/src/main/resources/data || exit 1
: > $OUT/port.txt
add() { # add <legacy-kind> <path-glob-dir>
  local kind="$1" dir="$2"
  [ -d "$dir" ] || return
  for f in "$dir"/*.json; do
    [ -f "$f" ] || continue
    local b="${f##*/}"; b="${b%.json}"
    echo "$kind/$b" >> $OUT/port.txt
  done
}
# World wars content (the pack in question targets this mod)
add guns        flansworldwars/guns
add vehicles    flansworldwars/vehicles
add grenades    flansworldwars/grenades
add attachments flansworldwars/attachments
add tools       flansworldwars/tools
add armorFiles  flansworldwars/armours/armour
# Bullets + parts live in the shared basic-parts mod
add bullets     flansbasicparts/bullets
add parts       flansbasicparts/parts
cd ../.. || exit 1
sort -u $OUT/port.txt > $OUT/port_s.txt

# ---------- normalize: strip the 44_ / W44_ prefix the legacy pack uses ----------
norm() { sed -E 's|^(guns\|vehicles\|bullets\|parts\|attachments\|armorFiles\|grenades\|tools\|planes\|boxes\|mechas)/(44_)?|\1/|' \
         | tr 'A-Z' 'a-z' | tr -d '_ -'; }

norm < $OUT/legacy.txt | sort -u > $OUT/legacy_n.txt
norm < $OUT/port_s.txt | sort -u > $OUT/port_n.txt

echo "legacy normalized : $(wc -l < $OUT/legacy_n.txt)"
echo "port   normalized : $(wc -l < $OUT/port_n.txt)"
echo
echo "===== 旧包有、移植版没有（可转换的增量）====="
comm -23 $OUT/legacy_n.txt $OUT/port_n.txt > $OUT/only_legacy.txt
awk -F/ '{print $1}' $OUT/only_legacy.txt | sort | uniq -c | sort -rn
echo "  合计: $(wc -l < $OUT/only_legacy.txt)"
echo
echo "===== 移植版有、旧包没有（新增内容）====="
comm -13 $OUT/legacy_n.txt $OUT/port_n.txt > $OUT/only_port.txt
awk -F/ '{print $1}' $OUT/only_port.txt | sort | uniq -c | sort -rn
echo "  合计: $(wc -l < $OUT/only_port.txt)"
echo
echo "===== 两边都有（已覆盖）====="
comm -12 $OUT/legacy_n.txt $OUT/port_n.txt > $OUT/both.txt
echo "  合计: $(wc -l < $OUT/both.txt)"
