---
uid: e61f2d41
id: flansmod.platform.mod
parent: flansmod.platform
state: planned
name: {zh: "模组入口", en: "Mod entry"}
description:
  zh: >
      声明 mod id、注册表事件、客户端与服务端初始化、配置项与公共日志器；内容包目录也在启动期被扫描。
  en: >
      Declares the mod id, registry hooks, client and server initialisation, config values and the shared logger; also scans the pack directory during startup.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: e1f5ca95b11c783896672fb1e805369a110c05c7825420be313a22c27b2aca03
source:
  - path: "src/main/java/com/flansmod/common/FlansMod.java"
apis:
  - protocol: rpc
    path: "event:RegisterEvent(NeoForge)"
    description:
      zh: >
          监听该事件。
      en: >
          Subscribes to this event.
  - protocol: rpc
    path: "rpc:com.flansmod.common.FlansMod.logger"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
