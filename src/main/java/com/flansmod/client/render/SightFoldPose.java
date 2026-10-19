package com.flansmod.client.render;

import com.flansmod.common.types.*;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Reach, operate and release in gun coordinates; the original pack has no fold-hand keyframes. */
public final class SightFoldPose {
    private SightFoldPose() { }
    private static float smooth(float p) {p=Math.clamp(p,0,1);return p*p*(3-2*p);}
    public static float turn(float progress) {return smooth((progress-.2F)/.52F);}
    public static float reach(float progress) {return smooth(progress/.2F)*(1-smooth((progress-.72F)/.28F));}
    /** Make room between the eye and a rear-mounted magnifier before the support hand reaches it. */
    public static Matrix4f weapon(Matrix4f pose,float progress) {
        float weight=reach(progress);return pose.translate(.45F*weight,-.12F*weight,0);
    }
    /** Recover the actual attachment origin through the nested, possibly rolled mount tree. */
    public static Matrix4f mount(GunType view,int[] path) {
        if(!com.flansmod.common.GunAttachments.validPath(path) || path.length==0)return null;
        ModifiableType owner=view;List<ModifiableType.DefaultAttachment> nodes=view.defaultAttachments.stream().flatMap(List::stream).toList();
        var result=new Matrix4f();
        for(int i=0;i<path.length;i+=2) {
            int slot=path[i],index=path[i+1];ModifiableType.DefaultAttachment selected=null;
            for(var candidate:nodes)if(candidate.slot==slot && index--==0) {selected=candidate;break;}
            if(selected==null || slot>=owner.slots.length)return null;
            var at=AttachmentType.getAttachment(selected.name);if(at==null)return null;
            var position=owner.slots[slot];
            result.translate(position.x+selected.pos,position.y,position.z).rotateX(FmumActionPose.rad(position.rotate));
            nodes=selected.children;owner=at;
        }
        return result;
    }
    /** Keeps authored hand scale and returns exactly to the holding pose at both ends. */
    public static Matrix4f hand(Matrix4f holding,Vector3f contact,float progress,boolean folding) {
        float weight=reach(progress);if(weight<=0 || contact==null)return new Matrix4f(holding);
        var position=holding.getTranslation(new Vector3f()).lerp(contact,weight);
        var rotation=holding.getUnnormalizedRotation(new Quaternionf());
        // EFT's forearm extends along local -X. Keep it pointing back/down, away from the eye.
        var target=new Quaternionf().rotationTo(new Vector3f(1,0,0),new Vector3f(.55F,.82F,.12F).normalize())
                .rotateX((folding?1:-1)*FmumActionPose.rad(32)*turn(progress));
        rotation.slerp(target,weight);
        return new Matrix4f().translationRotateScale(position,rotation,holding.getScale(new Vector3f()));
    }
}
