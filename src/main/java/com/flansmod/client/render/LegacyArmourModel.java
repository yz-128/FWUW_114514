package com.flansmod.client.render;

import com.flansmod.client.model.loader.BakedRig;
import com.flansmod.client.model.loader.ModelCache;
import com.flansmod.common.types.ArmourType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FastColor;

/** A per-render wrapper: live humanoid joints drive the pack's head, torso and limb arrays. */
public final class LegacyArmourModel extends Model {
    private final BakedRig rig;
    private final HumanoidModel<?> pose;

    public LegacyArmourModel(BakedRig rig, HumanoidModel<?> pose) {
        super(RenderType::armorCutoutNoCull);
        this.rig = rig; this.pose = pose;
    }

    public static Model forItem(ArmourType armour, HumanoidModel<?> original) {
        var texture = Textures.forArmour(armour);
        var rig = ModelCache.rigForArmour(armour, texture);
        // Loading a model is asynchronous in a running world; never block the render thread.
        return new LegacyArmourModel(rig, original);
    }

    @Override public void renderToBuffer(PoseStack matrices, VertexConsumer vertices,
                                         int light, int overlay, int color) {
        if(rig == null) return;
        for(var part : rig.quadsByPart.entrySet()) {
            ModelPart joint = joint(pose, part.getKey());
            if(joint == null) continue;
            matrices.pushPose();
            if(pose.young) {
                boolean head = part.getKey().equals("headModel");
                float scale = head ? .75F : .5F;
                matrices.scale(scale, scale, scale);
                matrices.translate(0, head ? 1F : 1.5F, 0);
            }
            joint.translateAndRotate(matrices);
            emit(matrices, vertices, part.getValue(), light, overlay, color);
            matrices.popPose();
        }
    }

    /** Slot and mesh are separate: EFT's leggings-slot masks/headsets still follow the head. */
    public static ModelPart joint(HumanoidModel<?> pose, String part) {
        return switch(part) {
            case "headModel" -> pose.head;
            case "bodyModel" -> pose.body;
            case "leftArmModel" -> pose.leftArm;
            case "rightArmModel" -> pose.rightArm;
            case "leftLegModel" -> pose.leftLeg;
            case "rightLegModel" -> pose.rightLeg;
            case "skirtFrontModel", "skirtRearModel" -> skirt(pose, part.equals("skirtFrontModel"));
            default -> null;
        };
    }

    private static ModelPart skirt(HumanoidModel<?> pose, boolean front) {
        var part = new ModelPart(java.util.List.of(), java.util.Map.of());
        part.x = (pose.leftLeg.x + pose.rightLeg.x) * .5F;
        part.y = (pose.leftLeg.y + pose.rightLeg.y) * .5F;
        part.z = (pose.leftLeg.z + pose.rightLeg.z) * .5F;
        part.xRot = front ? Math.min(pose.leftLeg.xRot, pose.rightLeg.xRot)
                : Math.max(pose.leftLeg.xRot, pose.rightLeg.xRot);
        part.yRot = pose.leftLeg.yRot; part.zRot = pose.leftLeg.zRot;
        return part;
    }

    private static void emit(PoseStack matrices, VertexConsumer vertices, java.util.List<BakedRig.Quad> quads,
                             int light, int overlay, int color) {
        float r = FastColor.ARGB32.red(color) / 255F, g = FastColor.ARGB32.green(color) / 255F;
        float b = FastColor.ARGB32.blue(color) / 255F, a = FastColor.ARGB32.alpha(color) / 255F;
        for(var quad : quads) {
            var p = quad.positions(); var uv = quad.uvs();
            var normal = new org.joml.Vector3f(p[1][0]-p[0][0],p[1][1]-p[0][1],p[1][2]-p[0][2])
                    .cross(p[2][0]-p[0][0],p[2][1]-p[0][1],p[2][2]-p[0][2]);
            if(normal.lengthSquared() < 1e-12F) continue;
            normal.normalize().mul(matrices.last().normal()).normalize();
            for(int i=0;i<4;i++) vertices.addVertex(matrices.last().pose(),p[i][0],p[i][1],p[i][2])
                    .setColor(r,g,b,a).setUv(uv[i][0],uv[i][1]).setOverlay(overlay).setLight(light)
                    .setNormal(normal.x,normal.y,normal.z);
        }
    }
}
