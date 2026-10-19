---
uid: 5c310689
id: flansmod.pack.lang
parent: flansmod.pack
state: planned
name: {zh: "包语言文件", en: "Pack language files"}
description:
  zh: >
      解析包自带的 en_US.lang / zh_CN.lang，让物品名与提示随游戏语言切换，而不是回落到短名。
  en: >
      Parses the pack-shipped en_US.lang / zh_CN.lang so item names and tooltips follow the game language instead of falling back to short names.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 2ad9fed407fe6ed7804831fa6a504fcd297316c0e4a5ad00fe3d89b6340fb536
source:
  - path: "src/main/java/com/flansmod/common/PackLang.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.PackLang.load"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.PackLang.lookup"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
