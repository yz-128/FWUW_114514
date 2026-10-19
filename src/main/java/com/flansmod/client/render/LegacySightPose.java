package com.flansmod.client.render;

import com.flansmod.common.GunSightState;
import com.flansmod.common.types.AttachmentType;
import com.flansmod.common.types.GunType;

/** One per-stack sight selection drives the aiming pose and optical picture. */
public final class LegacySightPose {
    private LegacySightPose() { }
    public record Center(String source,float roll,float x,float y,float z) { }
    public static Center forGun(GunType gun) {
        var sight=GunSightState.selectedSight(gun);
        if(sight==null)return new Center(gun.shortName,gun.aimCenterX,0F,gun.aimCenterY,gun.aimCenterZ);
        var type=sight.type();
        var rig=com.flansmod.client.model.loader.ModelCache.rigFor(type,type.modelScale);
        var center=FmumSightPicture.aimCenter(rig==null?null:rig.animation,type.aimCenterY,type.aimCenterZ);
        float dy=center.y-type.aimCenterY,dz=center.z-type.aimCenterZ;
        double roll=Math.toRadians(sight.roll()-type.aimCenterX);
        return new Center(type.shortName,sight.roll(),sight.x(),
                sight.y()+dy*(float)Math.cos(roll)-dz*(float)Math.sin(roll),
                sight.z()+dy*(float)Math.sin(roll)+dz*(float)Math.cos(roll));
    }
    public static AttachmentType selectedType(GunType gun) {
        var sight=GunSightState.selectedSight(gun);
        return sight==null?null:sight.type();
    }
}
