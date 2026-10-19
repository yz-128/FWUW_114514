---
uid: 78e2e862
id: flansmod.render.shader.smoke
parent: flansmod.render.shader
state: planned
name: {zh: "光影烟雾", en: "Shader smoke"}
description:
  zh: >
      光影下的烟雾绘制通道。
  en: >
      The smoke draw pass used when a shader pack is active.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 71bd61b35559f9b3389b5144d9fc41e0c83d1bfcf0e051c3e51861cfe46f6097
source:
  - path: "src/main/java/com/flansmod/client/render/ShaderSmokeRenderer.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.ShaderSmokeRenderer.render"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
