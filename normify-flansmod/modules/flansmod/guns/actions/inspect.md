---
uid: e8e3e428
id: flansmod.guns.actions.inspect
parent: flansmod.guns.actions
state: planned
name: {zh: "检视动作", en: "Inspection"}
description:
  zh: >
      检视弹药与弹膛：推进进度、在中间点输出结果、再退回原位。
  en: >
      Inspects ammunition and chamber: advances progress, emits the result at the midpoint and returns to rest.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 1ec5056f2e69dc011d2202dbefbb867b97c143fc2112f8c1570ea3cf19e8784e
source:
  - path: "src/main/java/com/flansmod/common/GunActions.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunActions.request"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunActions.tick"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunActions.ready"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
