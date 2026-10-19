---
uid: c2e65f3a
id: flansmod.client.debug
parent: flansmod.client
state: planned
name: {zh: "自动射击", en: "Automated firing"}
description:
  zh: >
      无人值守时按脚本开火，用于回归与截图。
  en: >
      Fires on a script when nobody is at the keyboard, for regressions and screenshots.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 482c570c75987412ae8dbc4432cb3a201da791b73e88501b22ea864ba6dc21ee
source:
  - path: "src/main/java/com/flansmod/client/DebugAutoShot.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.DebugAutoShot.tick"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
