---
uid: 538c96e2
id: flansmod.guns.input
parent: flansmod.guns
state: planned
name: {zh: "换弹输入识别", en: "Reload input detection"}
description:
  zh: >
      把点击、双击、长按等原始输入识别成普通/进阶/压缩换弹命令。
  en: >
      Turns tap, double-tap and hold into normal, advanced and compressed reload commands.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 4a877c1f17e18177d079ec8c99a30d0c1884b76b3b9a8c56008cdeae946abf87
source:
  - path: "src/main/java/com/flansmod/common/ReloadKeyState.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.ReloadKeyState.update"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
