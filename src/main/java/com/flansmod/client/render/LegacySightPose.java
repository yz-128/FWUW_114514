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
        return sight==null ? new Center(gun.shortName,gun.aimCenterX,0F,gun.aimCenterY,gun.aimCenterZ)
                : new Center(sight.type().shortName,sight.roll(),sight.x(),sight.y(),sight.z());
    }
    public static AttachmentType selectedType(GunType gun) {
        var sight=GunSightState.selectedSight(gun);
        return sight==null?null:sight.type();
    }
}
