---
uid: 0a302aee
id: flansmod.platform.sound
parent: flansmod.platform
state: planned
name: {zh: "音效播放", en: "Sound playback"}
description:
  zh: >
      按内容包声明的名字与距离播放音效，并处理失真标记与本地/广播两种投递。
  en: >
      Plays sounds by the pack-declared name and range, handling the distort flag and both local and broadcast delivery.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 6d9af7b4e5e552e2b91224b64eb76f98d42a74576b6d3080c3307c4284b3885a
source:
  - path: "src/main/java/com/flansmod/common/FmumSounds.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.FmumSounds.play"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
