package com.flansmod.client.model.loader;

/** ModelCustomArmour replaces each renderer's pivot and rotation with its animated body part. */
public final class ArmourGeometry {
    private ArmourGeometry() { }

    public static BakedRig bake(ModelGeometry.Model source, float scale, float uvU, float uvV) {
        // A separate model keeps the shared constructor geometry immutable. Guns may still use
        // the ordinary bake of the same class, and resource reloads can run concurrently.
        var model = new ModelGeometry.Model();
        model.className = source.className; model.simpleName = source.simpleName;
        model.superName = source.superName; model.sheetX = source.sheetX; model.sheetY = source.sheetY;
        model.flipped = source.flipped; model.renderScale = source.renderScale;
        for(var original : source.parts.values()) {
            var part = new ModelGeometry.Part();
            part.name = original.name; part.sheetWidth = original.sheetWidth; part.sheetHeight = original.sheetHeight;
            part.textureOffsetX = original.textureOffsetX; part.textureOffsetY = original.textureOffsetY;
            for(var old : original.boxes) {
                var box = new ModelGeometry.Box();
                box.x = old.x; box.y = old.y; box.z = old.z;
                box.w = old.w; box.h = old.h; box.d = old.d; box.scale = old.scale;
                System.arraycopy(old.offsets, 0, box.offsets, 0, old.offsets.length);
                box.renderer = old.renderer; box.hasTexture = old.hasTexture;
                box.textureOffsetX = old.textureOffsetX; box.textureOffsetY = old.textureOffsetY;
                box.sheetWidth = old.sheetWidth; box.sheetHeight = old.sheetHeight;
                part.boxes.add(box);
            }
            for(var old : original.prisms) {
                var prism = new ModelGeometry.Prism();
                prism.x = old.x; prism.y = old.y; prism.z = old.z; prism.depth = old.depth;
                prism.shapeTexW = old.shapeTexW; prism.shapeTexH = old.shapeTexH;
                prism.sideTexW = old.sideTexW; prism.sideTexH = old.sideTexH; prism.direction = old.direction;
                prism.renderer = old.renderer;
                for(var point : old.coords) prism.coords.add(point.clone());
                part.prisms.add(prism);
            }
            model.parts.put(part.name, part);
        }
        return BakedRig.bake(model, scale, uvU, uvV);
    }
}
