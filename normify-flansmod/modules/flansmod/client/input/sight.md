---
uid: 8308109c
id: flansmod.client.input.sight
parent: flansmod.client.input
state: planned
name: {zh: "瞄具键", en: "Sight keys"}
description:
  zh: >
      N 切组、Z 切倍率、C 切准点、V 折叠，全部经服务端校验。
      
  en: >
      N cycles groups, Z steps zoom, C steps reticles and V folds, all validated server-side.
      
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:49.811Z"
fingerprint: 1fd53a5f433c44295b6ea4e2f1902fbed9fb162bafa64d8335045f727b9c9f1c
source:
  - path: "src/main/java/com/flansmod/client/SightKeyHandler.java"
apis:
  - protocol: rpc
    path: "rpc:sightkeys:ClientTickEvent.Post"
    description:
      zh: >
          每次客户端 tick 读取瞄具按键。
      en: >
          Reads sight key presses every client tick.
  - protocol: rpc
    path: "rpc:com.flansmod.client.SightKeyHandler.tick"
    description:
      zh: >
          把按键转成瞄具请求。
      en: >
          Turns key presses into sight requests.
---
