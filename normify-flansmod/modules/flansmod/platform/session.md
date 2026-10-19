---
uid: c1961039
id: flansmod.platform.session
parent: flansmod.platform
state: planned
name: {zh: "玩家会话状态", en: "Player session state"}
description:
  zh: >
      每名玩家一份的可变状态：当前枪、瞄准进度、换弹进度、动作状态机、瞄具选择与缓存射线。
  en: >
      One mutable record per player: current gun, aim progress, reload progress, action state machine, sight selection and cached wall ray.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 0633f0971abde92244b46e02ad8f8e8dfdccf5444f57f6a5b89f87613a5c22d5
source:
  - path: "src/main/java/com/flansmod/common/PlayerHandler.java"
  - path: "src/main/java/com/flansmod/common/PlayerData.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.PlayerHandler.getPlayerData"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.PlayerData.tick"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
