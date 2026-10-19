---
uid: 26a4778f
id: flansmod.platform.debug
parent: flansmod.platform
state: planned
name: {zh: "调试采集", en: "Debug capture"}
description:
  zh: >
      无人值守自检：给玩家发枪弹、经由模组自身 API 开火并保存截图，便于在没有人工操作的条件下验证移植。
  en: >
      Unattended self-check: hands the player a gun and ammunition, fires through the mod API and saves screenshots so the port can be verified without manual input.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: f798bd763caea607ea27785f16ffe9d120ba2d41dfb520ae0eeff9bc12f6c2be
source:
  - path: "src/main/java/com/flansmod/common/DebugCapture.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.DebugCapture.tick"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:flansmod.debugCapture"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
