---
uid: 7ee12047
id: flansmod.pack.layout
parent: flansmod.pack
state: planned
name: {zh: "包目录布局", en: "Pack layout"}
description:
  zh: >
      识别包内各类型定义所在的子目录（guns/、attachments/、bullets/ 等），并处理大小写与旧命名差异。
  en: >
      Recognises the subdirectories holding each definition type (guns/, attachments/, bullets/ and so on) and tolerates case and legacy naming differences.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 932ea74681de8fc490666e17fe02be4df5b726056f3a117e916484e346bf657d
source:
  - path: "src/main/java/com/flansmod/common/PackLayout.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.PackLayout.folderFor"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
