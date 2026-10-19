---
uid: eee0f155
id: flansmod.guns.actions.rest
parent: flansmod.guns.actions
state: planned
name: {zh: "收枪状态机", en: "Rest state machine"}
description:
  zh: >
      手动收枪与顶墙自动收枪的进入、退出与进度；顶墙判定使用碰撞箱而非轮廓箱，因此草与花不会触发。
  en: >
      Entering, leaving and progressing manual and wall-triggered rest; the wall test uses collision shapes, so grass and flowers do not trigger it.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: f28a518953915b7b026a85349c768b624e6c523ea00da8dfd1dda929dbf95ccc
source:
  - path: "src/main/java/com/flansmod/common/GunActionState.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunActionState.toggleRest"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunActionState.tick"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunActions.nearWall"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
