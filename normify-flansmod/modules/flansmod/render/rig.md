---
uid: 731e9c77
id: flansmod.render.rig
parent: flansmod.render
state: planned
name: {zh: "图元提交", en: "Rig submission"}
description:
  zh: >
      把烘焙后的四边形提交给缓冲区，处理法线、光照、叠加层与镜像绕序。
  en: >
      Submits baked quads to the buffer, handling normals, light, overlay and mirrored winding.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 76e1ef0f7ab3031a01b08037678310fcf14d6a3dfe58901f8fd58f7dd8ca95cb
source:
  - path: "src/main/java/com/flansmod/client/render/RigRenderer.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.RigRenderer.render"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.client.render.RigRenderer.renderPart"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
