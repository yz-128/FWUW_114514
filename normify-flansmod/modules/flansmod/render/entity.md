---
uid: a7b27456
id: flansmod.render.entity
parent: flansmod.render
state: planned
name: {zh: "弹丸渲染", en: "Projectile rendering"}
description:
  zh: >
      飞行中的子弹/曳光弹绘制。
  en: >
      Draws bullets and tracers in flight.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: aa9c3a37de799226d0e6dc604801b6b27c1930eebe518fe8c8bc3207c2fd37b8
source:
  - path: "src/main/java/com/flansmod/client/render/EntityBulletRenderer.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.EntityBulletRenderer.render"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
