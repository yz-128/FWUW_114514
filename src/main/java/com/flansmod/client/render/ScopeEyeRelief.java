package com.flansmod.client.render;

import com.flansmod.client.model.loader.ModelGeometry;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** FMUM's authored off-axis fade and eye-distance limits, evaluated from the live sight pose. */
public final class ScopeEyeRelief {
    private ScopeEyeRelief() { }
    public record Offset(float y,float z) { }
    public static Offset maskOffset(ModelGeometry.Model model,Matrix4f toEye) {
        if(model==null || toEye==null || !toEye.isFinite())return null;
        // RenderGun:780-792 measures objective angles in its tracked gun frame, before
        // the vanilla/Forge item parent matrix. Its fixed eye is (-81.5/160,.4,0).
        // scopeObjectiveLensCenter is already in optical blocks; unlike pupilRefCenter,
        // it must not be multiplied by the attachment's mesh scale.
        var objective=LegacyScopeProjection.physicalToLegacy(toEye).transformPosition(
                FmumSightPicture.vector(model,"scopeObjectiveLensCenter",0,0,0));
        if(!objective.isFinite())return null;
        objective.sub(FmumFirstPersonPose.DEFAULT_EYE);
        float horizontal=(float)Math.hypot(objective.x,objective.z);
        if(objective.x<=1E-5F || horizontal<=1E-5F)return null;
        // These are tan(signed angle(projectXZ(ray),ray)) and
        // tan(signed angle(projectXZ(ray),+X)), without acos/tan round-off.
        // The old rotation multipliers are only added for explicit barrel recoil
        // (RenderGun:878-879); subtracting the sight axis again double-counted its pose.
        return new Offset(-objective.y/horizontal*FmumActionPose.scalar(model,"scopeMaskMultTransY",.4F),
                -objective.z/objective.x*FmumActionPose.scalar(model,"scopeMaskMultTransZ",.4F));
    }
    public record Picture(float alpha,float shiftY,float shiftZ,float radius,float minimum) {
        public boolean visible() {return alpha>=minimum && alpha>0 && radius>0;}
        /** Historical CPU diagnostic; live rendering uses the original ScopeMaskRenderer mesh. */
        public float shadow(float y,float z) {
            if(!visible() || !Float.isFinite(y) || !Float.isFinite(z))return 1;
            float distance=(float)Math.hypot(y-shiftY,z-shiftZ);
            return Math.clamp((distance-radius*.88F)/Math.max(.001F,radius*.12F),0,1);
        }
    }
    public static Picture picture(ModelGeometry.Model model,float modelScale,Matrix4f toEye,int step) {
        var closed=new Picture(0,0,0,0,.05F);
        if(model==null || toEye==null || !toEye.isFinite())return closed;
        var offset=maskOffset(model,toEye);if(offset==null)return closed;
        float dy=offset.y,dz=offset.z;
        float[] divisors=model.fmumArrays.getOrDefault("scopeMaskAlphaDivisor",new float[]{.00003F});
        float divisor=divisors.length==0?.00003F:divisors[Math.clamp(step,0,divisors.length-1)];
        if(!Float.isFinite(divisor) || divisor<=0 || !Float.isFinite(dy) || !Float.isFinite(dz))return closed;
        float alpha=Math.clamp(1-(dy*dy+dz*dz)/divisor,0,1);
        Vector3f eye=new Matrix4f(toEye).invert().transformPosition(new Vector3f());
        if(!eye.isFinite())return closed;
        float distance=FmumSightPicture.pupilDistance(model,modelScale,eye);
        float minimum=FmumActionPose.scalar(model,"minPupilDistance",.3F);
        float power=FmumActionPose.scalar(model,"pupilDistancePower",3F);
        float radius=(float)Math.clamp(minimum/Math.pow(distance,power),0,1);
        float threshold=FmumActionPose.scalar(model,"minAlphaToRenderScopeTex",.05F);
        if(!Float.isFinite(radius) || !Float.isFinite(threshold))return closed;
        // Rotate the camera-space shadow drift into the actual lens axes (including canted sights).
        Vector3f drift=new Matrix4f(toEye).invert().transformDirection(new Vector3f(dz,dy,0));
        float divisorRoot=(float)Math.sqrt(divisor);
        return new Picture(alpha,Math.clamp(drift.y/divisorRoot,-2,2)*.35F,
                Math.clamp(drift.z/divisorRoot,-2,2)*.35F,radius,Math.clamp(threshold,0,1));
    }
}
