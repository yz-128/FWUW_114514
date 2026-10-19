---
uid: b6f54012
id: flansmod.platform.network.reload
parent: flansmod.platform.network
state: planned
name: {zh: "换弹载荷", en: "Reload payloads"}
description:
  zh: >
      换弹请求与换弹动画同步；换弹是服务端权威的，客户端只负责表现。
  en: >
      Reload requests and reload animation sync; reloading is server-authoritative while the client only presents it.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 20ffc480d81ee5bce26bafd47040989d33ab6e876f846057b05eaba6fc582254
source:
  - path: "src/main/java/com/flansmod/common/network/ReloadPayload.java"
  - path: "src/main/java/com/flansmod/common/network/ReloadAnimationPayload.java"
apis:
  - protocol: rpc
    path: "rpc:flansmod:reload"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:flansmod:reload_animation"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
