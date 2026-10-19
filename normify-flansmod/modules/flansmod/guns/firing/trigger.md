---
uid: 1eba7bd6
id: flansmod.guns.firing.trigger
parent: flansmod.guns.firing
state: planned
name: {zh: "扳机与输入", en: "Trigger and input"}
description:
  zh: >
      把按下/保持/松开映射成扳机状态，并在不可射击时把请求排队而不是丢弃。
  en: >
      Maps press, hold and release into trigger state and queues a request instead of dropping it when the gun cannot fire yet.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 4dad223a0b67028ba9fba34308ccc348e872ac7152826785ef9303a22760650f
source:
  - path: "src/main/java/com/flansmod/common/GunFiring.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunFiring.onTriggerPull"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunFiring.serverTrigger"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunFiring.onUse"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunFiring.tickHeld"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
