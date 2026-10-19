---
uid: b4239601
id: flansmod.guns.attachments.catalog
parent: flansmod.guns.attachments
state: planned
name: {zh: "配件目录", en: "Attachment catalogue"}
description:
  zh: >
      列出某把枪可用的配件、拥有的数量与缺少状态，供改枪界面右侧栏使用。
  en: >
      Lists the attachments a gun accepts with owned counts and missing state for the gunsmith sidebar.
revision: 93e14b015c7dd5b0817eb937da3cedbc05a18347
updated_at: "2026-10-19T14:24:07.699Z"
fingerprint: 0efc1de76a17d407edd66af95fcd47f4d6719b269772bf1ae4db0788d74b7789
source:
  - path: "src/main/java/com/flansmod/common/GunAttachmentCatalog.java"
apis:
  - protocol: rpc
    path: "rpc:com.flansmod.common.GunAttachmentCatalog.candidates"
    description:
      zh: >
          模组内部调用入口。
      en: >
          Internal call entry point.
---
