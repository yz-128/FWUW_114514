---
uid: f5082bc4
id: flansmod.render.shader.muzzle
parent: flansmod.render.shader
state: planned
name: {zh: "光影枪口焰", en: "Shader muzzle flash"}
description:
  zh: >
      把枪口焰捕获到帧末再用独立着色器绘制，使其在光影下仍可见。
  en: >
      Captures the flash and draws it at frame end with its own shader so it stays visible under shader packs.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 7d2e2b51c75c95f47ea44defbf3f1fa1a91f6fb29c8a757b7cf04e6f2ac097e3
source:
  - path: "src/main/java/com/flansmod/client/render/ShaderMuzzleRenderer.java"
  - path: "src/main/java/com/flansmod/client/render/FmumShaderFlash.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.ShaderMuzzleRenderer.capture"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.ShaderMuzzleRenderer.shadersEnabled"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
