import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.LegacyThirdPersonPose;
import com.flansmod.common.types.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.*;

/** Observation-free static third-person preview: classic player proportions + real gun assembly. */
public final class TPPreview {
    public static void main(String[] args)throws Exception {
        OfflinePack pack=new OfflinePack(Paths.get(args[0]));pack.readTypes();
        GunType gun=(GunType)pack.types.stream().filter(t->t instanceof GunType&&t.shortName.equals(args[1])).findFirst().orElseThrow();
        BakedRig rig=ModelCache.rigFor(gun,gun.modelScale);
        float yaw=Float.parseFloat(System.getProperty("tp.viewYaw","180"));
        float pitch=Float.parseFloat(System.getProperty("tp.headPitch","0"));
        float headYaw=Float.parseFloat(System.getProperty("tp.headYaw","0"));
        System.setProperty("fp.fov",System.getProperty("fp.fov","40"));
        Matrix4f view=new Matrix4f().translate(0,.65F,-4.5F).rotateY(rad(yaw)).scale(1,-1,1);
        var skin=ImageIO.read(TPPreview.class.getResourceAsStream("/assets/minecraft/textures/entity/player/wide/steve.png"));
        List<FPPreview.Part> parts=new ArrayList<>();
        parts.add(new FPPreview.Part(new Matrix4f(view),box(-4,0,-2,8,12,4,16,16),skin,0xFFFFFFFF));
        parts.add(new FPPreview.Part(new Matrix4f(view).rotateY(rad(headYaw)).rotateX(rad(pitch)),box(-4,-8,-4,8,8,8,0,0),skin,0xFFFFFFFF));
        parts.add(new FPPreview.Part(new Matrix4f(view).translate(-2F/16,12F/16,0),box(-2,0,-2,4,12,4,0,16),skin,0xFFFFFFFF));
        parts.add(new FPPreview.Part(new Matrix4f(view).translate(2F/16,12F/16,0),box(-2,0,-2,4,12,4,16,48),skin,0xFFFFFFFF));
        // ModelBiped's aimedBow, also used by modern BOW_AND_ARROW. Static tick (no sway).
        Matrix4f right=new Matrix4f(view).translate(-5F/16,2F/16,0).rotateY(-.1F+rad(headYaw)).rotateX(-(float)Math.PI/2+rad(pitch));
        Matrix4f left=new Matrix4f(view).translate(5F/16,2F/16,0).rotateY(.5F+rad(headYaw)).rotateX(-(float)Math.PI/2+rad(pitch));
        parts.add(new FPPreview.Part(new Matrix4f(right),box(-3,-2,-2,4,12,4,40,16),skin,0xFFFFFFFF));
        parts.add(new FPPreview.Part(new Matrix4f(left),box(-1,-2,-2,4,12,4,32,48),skin,0xFFFFFFFF));
        Matrix4f held=new Matrix4f(right).rotateX(rad(-90)).rotateY(rad(180)).translate(1F/16,.125F,-.625F);
        if(Boolean.getBoolean("tp.oldPort")) held.rotateY(rad(90)).translate(-.08F,-.12F,0)
            .translate(rig.thirdPersonOffsetX,rig.thirdPersonOffsetY,rig.thirdPersonOffsetZ);
        else LegacyThirdPersonPose.handFrame(held,false,"block".equalsIgnoreCase(gun.itemUseAction));
        LegacyThirdPersonPose.placement(held,rig.thirdPersonOffsetX,rig.thirdPersonOffsetY,rig.thirdPersonOffsetZ);
        parts.add(new FPPreview.Part(new Matrix4f(held),rig,FPPreview.skin(pack.root,gun.texture),0xFFFFFFFF));
        for(var group:gun.defaultAttachments)FPPreview.addNodes(parts,pack.root,gun,group,new Matrix4f(held),0);
        Vector3f muzzle=held.transformPosition(new Vector3f(1.375F,.2875F,0));
        System.out.printf("third-person %s head=(%.1f %.1f), view=%.1f, muzzle view=%s%n",gun.shortName,headYaw,pitch,yaw,muzzle);
        FPPreview.render(parts,Paths.get(args[2]).toFile());
    }
    static BakedRig box(float x,float y,float z,int w,int h,int d,int u,int v) {
        var model=new ModelGeometry.Model();model.sheetX=model.sheetY=64;
        var part=new ModelGeometry.Part();part.name="player";part.sheetWidth=part.sheetHeight=64;part.textureOffsetX=u;part.textureOffsetY=v;
        var box=new ModelGeometry.Box();box.x=x;box.y=y;box.z=z;box.w=w;box.h=h;box.d=d;
        part.boxes.add(box);model.parts.put(part.name,part);return BakedRig.bake(model,1);
    }
    static float rad(float degrees){return(float)Math.toRadians(degrees);}
}
