---
uid: 903ccc0d
id: flansmod.render.item.reload
parent: flansmod.render.item
state: planned
name: {zh: "换弹姿态", en: "Reload pose"}
description:
  zh: >
      整枪换弹姿态与弹夹式装填的逐发动画。
  en: >
      Whole-gun reload pose plus the per-round animation used by clip-loading guns.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 6799b20c4df10252a11d879d70cc1dcf06e974805a4b9bb50925a270634795a4
source:
  - path: "src/main/java/com/flansmod/client/render/TurborigItemRenderer.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.TurborigItemRenderer.applyReloadGunPose"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.TurborigItemRenderer.applyClipReload"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
