---
uid: 83a585ea
id: flansmod.model.dev.bake-test
parent: flansmod.model.dev
state: planned
name: {zh: "烘焙自检", en: "Bake self-test"}
description:
  zh: >
      对真实内容包执行字节码提取与烘焙的断言式自检。
  en: >
      Assertion-based self-test of bytecode extraction and baking against real content packs.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 236b4986b324d82e3417a41616bfa0d81a6e3388164d89d87193d01fe7e83b34
source:
  - path: "src/main/java/com/flansmod/client/model/loader/BakeTest.java"
  - path: "src/main/java/com/flansmod/client/model/loader/BytecodeModelTest.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.model.loader.BakeTest.run"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.client.model.loader.BytecodeModelTest.run"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
