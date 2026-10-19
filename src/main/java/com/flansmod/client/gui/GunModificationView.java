package com.flansmod.client.gui;

import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** GUI-only orthographic framing and stable studio lighting, independent of world light. */
public final class GunModificationView {
    private GunModificationView() { }
    public record Frame(Matrix4f matrix,float scale,Vector3f projectedMin,Vector3f projectedMax) { }
    public static Matrix4f rotation(float yaw,float pitch) {
        return new Matrix4f().rotateZ((float)Math.toRadians(pitch)).rotateY((float)Math.toRadians(yaw));
    }
    /** Fit the rotated silhouette, rather than squeezing a long rifle into a height-sized sphere. */
    public static Frame frame(List<Vector3f> corners,float yaw,float pitch,float zoom,int x,int y,int width,int height) {
        if(corners.isEmpty() || width<1 || height<1) return null;
        var rotation=rotation(yaw,pitch);
        var min=new Vector3f(Float.POSITIVE_INFINITY);var max=new Vector3f(Float.NEGATIVE_INFINITY);
        for(var corner:corners) {var point=rotation.transformPosition(new Vector3f(corner));min.min(point);max.max(point);}
        if(!Float.isFinite(min.x+min.y+min.z+max.x+max.y+max.z)) return null;
        var center=new Vector3f(min).add(max).mul(.5F);
        float scale=Math.min(width*.88F/Math.max(.001F,max.x-min.x),height*.83F/Math.max(.001F,max.y-min.y));
        // Extremely edge-on views must remain inside the GUI depth range as well.
        scale=Math.min(scale,350F/Math.max(.001F,max.z-min.z));
        scale*=Math.max(.5F,Math.min(2.8F,zoom));
        var matrix=new Matrix4f().translate(x+width/2F,y+height/2F,250F).scale(scale,-scale,scale)
                .translate(-center.x,-center.y,-center.z).mul(rotation);
        return new Frame(matrix,scale,min,max);
    }
    /** Soft two-sided lighting preserves authored colours even with dark world/shader lighting. */
    public static float shade(Vector3f normal) {
        if(!Float.isFinite(normal.lengthSquared()) || normal.lengthSquared()<.000001F) return 1F;
        return .70F+.30F*Math.abs(new Vector3f(normal).normalize().dot(new Vector3f(-.3F,-.6F,1F).normalize()));
    }
}
