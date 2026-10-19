---
uid: 944a500a
id: flansmod.defs.vehicle
parent: flansmod.defs
state: planned
name: {zh: "载具定义", en: "Vehicle definitions"}
description:
  zh: >
      载具、飞机与可驾驶物的定义；这一版只做读取与物品化，驾驶逻辑尚未移植。
  en: >
      Definitions for vehicles, planes and driveables; this port only reads and itemises them, driving logic is not ported yet.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 609176ecf1621302dc2db0c9115bb2d0ce35aaf71dca1f63bd58906ca7fae6e0
source:
  - path: "src/main/java/com/flansmod/common/types/VehicleType.java"
  - path: "src/main/java/com/flansmod/common/types/PlaneType.java"
  - path: "src/main/java/com/flansmod/common/types/DriveableType.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.VehicleType.read"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.PlaneType.read"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.types.DriveableType.read"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
