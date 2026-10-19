package com.flansmod.common;

import com.flansmod.common.types.ArmourType;

/** Some one-sample proof families (notably Scav emptysound) have no numeric suffix. */
public final class ArmourSounds {
    private ArmourSounds() { }
    public static String hit(ArmourType type,int part,int sample) {
        if(part<0||part>=8||type.numHitSounds[part]<=0)return null;
        String family=type.hitSounds[part];if(FmumSounds.id(family)==null)return null;
        String numbered=family+"_"+Math.clamp(sample,0,type.numHitSounds[part]-1);
        if(exists(numbered))return numbered;
        return type.numHitSounds[part]==1&&exists(family)?family:numbered;
    }
    private static boolean exists(String name) {
        var id=FmumSounds.id(name);
        return id!=null&&ContentPackIndex.current().find("assets/"+id.getNamespace()+"/sounds/"+id.getPath()+".ogg",true).isPresent();
    }
}
