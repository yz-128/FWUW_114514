---
uid: 215d6924
id: flansmod.client.animation.sprint
parent: flansmod.client.animation
state: planned
name: {zh: "冲刺姿态", en: "Sprint stance"}
description:
  zh: >
      持枪奔跑时的让位姿态与恢复。
  en: >
      The carry pose and recovery while sprinting with a gun.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 939504c516e8b63642a56d7cb4d0becf739342a0020dbf2e78c779fb5728dc59
source:
  - path: "src/main/java/com/flansmod/client/SprintStance.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.client.SprintStance.tick"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
