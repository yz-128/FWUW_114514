---
uid: 6bea356d
id: flansmod.render.textures
parent: flansmod.render
state: planned
name: {zh: "贴图解析", en: "Texture resolution"}
description:
  zh: >
      把定义里的贴图名解析成真实资源位置，处理大小写、扩展名与命名变化。
  en: >
      Resolves definition texture names into real resource locations, handling case, extension and legacy naming.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 1149b1c266dc91f48620722ecfe576ebc0cf5a3c5d65e24eb7ac0db03b946dd0
source:
  - path: "src/main/java/com/flansmod/client/render/Textures.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.Textures.of"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.Textures.forType"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
