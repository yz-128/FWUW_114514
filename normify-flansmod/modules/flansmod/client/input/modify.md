---
uid: 82bc7137
id: flansmod.client.input.modify
parent: flansmod.client.input
state: planned
name: {zh: "改枪键", en: "Gunsmith key"}
description:
  zh: >
      I 打开权威改枪界面。
      
  en: >
      I opens the authoritative gunsmith screen.
      
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:25:02.980Z"
fingerprint: cfb21ab8486e853568bee9bf3bdd2b61d15a8e8ea3c3b2351f216f31b3fe7b8c
source:
  - path: "src/main/java/com/flansmod/client/ModifyKeyHandler.java"
apis:
  - protocol: rpc
    path: "rpc:modifykeys:ClientTickEvent.Post"
    description:
      zh: >
          每次客户端 tick 读取改枪按键。
      en: >
          Reads the gunsmith key every client tick.
  - protocol: rpc
    path: "rpc:com.flansmod.client.ModifyKeyHandler.tick"
    description:
      zh: >
          打开权威改枪界面。
      en: >
          Opens the authoritative gunsmith screen.
---
