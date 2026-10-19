---
uid: e406fdf5
id: flansmod.render.muzzle.glow
parent: flansmod.render.muzzle
state: planned
name: {zh: "枪口辉光", en: "Muzzle glow"}
description:
  zh: >
      枪口焰周围的自发光面片，避免整体提亮场景。
  en: >
      Emissive quads around the flash so the scene is not brightened wholesale.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 3182eea348814cc0b00beb1c87d123b4345441b32dd4181341e53d924357f4e6
source:
  - path: "src/main/java/com/flansmod/client/render/FmumMuzzleGlow.java"
  - path: "src/main/java/com/flansmod/client/render/MuzzleGlowRenderer.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.FmumMuzzleGlow.emit"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
