# pack-core (vendored)

These seven files are the format-neutral content-pack engine from the PackArms
project (`PackArms-1.21.1/pack-core`). They are pure JDK code with no Minecraft
or NeoForge dependency, which is why they can be compiled straight into this mod.

## Why they are copied rather than referenced

`build.gradle` used to add `../PackArms-1.21.1/pack-core/src/main/java` as an
extra source directory. That works on a machine where both projects sit side by
side, but the published repository contains only this project, so CI checked out
a tree without that directory and every build failed on the missing
`dev.packarms.core` package. Copying the sources in makes the repository
self-contained.

## Keeping them in sync

This is a snapshot. If PackArms changes the engine, copy the files over again:

```
cp ../PackArms-1.21.1/pack-core/src/main/java/dev/packarms/core/*.java \
   src/main/java/dev/packarms/core/
```

There is no automatic sync, so the two copies can drift. The mod only uses the
pack reading surface (`PackIndex`, `PackEntry`, `IndexedPack`, `LazyAssetCache`,
`ScanPolicy`, `ScanProblem`, `PackPaths`); a change that keeps those signatures
stable is safe to bring over.

## Licence

MIT, Copyright (c) 2026 PackArms contributors. The full text is in
`LICENSE-PACKCORE-MIT.txt` and applies to the files in this directory only; the
rest of this repository stays under CC BY-NC-SA 3.0.
