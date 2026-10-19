---
uid: 3a999453
id: flansmod.render.armour
parent: flansmod.render
state: planned
name: {zh: "护甲渲染", en: "Armour rendering"}
description:
  zh: >
      把护甲模型贴合到人形骨架并应用包贴图。
  en: >
      Fits armour models onto the humanoid skeleton with pack textures.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: fbddf40be2f625d0540ae939a5a5b63501b34eb099abba5e34cc8327ffa652ca
source:
  - path: "src/main/java/com/flansmod/client/render/LegacyArmourModel.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.LegacyArmourModel.render"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
