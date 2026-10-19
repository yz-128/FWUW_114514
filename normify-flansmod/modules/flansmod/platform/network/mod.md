---
uid: 22602f6f
id: flansmod.platform.network.mod
parent: flansmod.platform.network
state: planned
name: {zh: "改枪载荷", en: "Modification payload"}
description:
  zh: >
      改枪界面的请求与响应：操作类型、挂点路径、槽位与修订号一起提交，服务端据此原子执行。
  en: >
      Gunsmith request and response: operation, mount path, slot and revision travel together so the server can apply them atomically.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 2717180bfaeba128942c3ce85c39cc172ca15c6e952d3410862e89e89e667b53
source:
  - path: "src/main/java/com/flansmod/common/network/GunModificationPayload.java"
apis:
  - protocol: rpc
    path: "rpc:flansmod:gun_modification"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
