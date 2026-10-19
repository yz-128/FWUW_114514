---
uid: fa2f5c78
id: flansmod.model.cache
parent: flansmod.model
state: planned
name: {zh: "模型缓存", en: "Model cache"}
description:
  zh: >
      按引用+缩放+贴图缓存烘焙结果，后台线程预热，资源重载时整代失效。
  en: >
      Caches baked rigs by reference, scale and texture, prewarms on worker threads and drops the whole generation on resource reload.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 1dcde64ca3346d6245b28775bdcddf303aba87a252e4959eb45eeec67e9cd523
source:
  - path: "src/main/java/com/flansmod/client/model/loader/ModelCache.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.model.loader.ModelCache.rigFor"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.client.model.loader.ModelCache.rigForReference"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
  - protocol: rpc
    path: "rpc:com.flansmod.client.model.loader.ModelCache.prewarm"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
