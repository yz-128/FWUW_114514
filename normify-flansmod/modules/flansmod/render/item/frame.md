---
uid: 752d20c2
id: flansmod.render.item.frame
parent: flansmod.render.item
state: planned
name: {zh: "历史变换", en: "Legacy transform"}
description:
  zh: >
      重建 1.7.10 的整条变换链（物品框、手部偏移、第三人称手臂），左手镜像也在这里。
  en: >
      Rebuilds the whole 1.7.10 transform chain — item frame, hand offset, third-person arm — including the left-hand mirror.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 6799b20c4df10252a11d879d70cc1dcf06e974805a4b9bb50925a270634795a4
source:
  - path: "src/main/java/com/flansmod/client/render/TurborigItemRenderer.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.TurborigItemRenderer.applyLegacyTransform"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
