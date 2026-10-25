import com.flansmod.client.model.FmumCasing;
import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import com.flansmod.common.types.*;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;

/** Real pack origins and native level/hand view contracts, including moving front/back cameras. */
public final class VerifyWorldEffects {
 static void check(boolean value,String detail){if(!value)throw new AssertionError(detail);}
 static void close(Vector3f a,Vector3f b,String detail){check(a.distance(b)<.0002F,detail+": "+a+" vs "+b);}
 static Quaternionf camera(float yaw,float pitch){return new Quaternionf().rotateY((float)Math.toRadians(yaw)).rotateX((float)Math.toRadians(pitch));}
 static Matrix4f view(Quaternionf rotation){return new Matrix4f().rotate(new Quaternionf(rotation).conjugate());}
 public static void main(String[] args)throws Exception {
  var pack=new OfflinePack(Path.of(args[0]));pack.readTypes();int samples=0,guns=0;
  var casing=BulletType.getBullet("556x45mmCasing");
  for(var type:pack.types)if(type instanceof GunType gun && gun.itsType!=GunType.GunItsType.MAG && !gun.magazineNames.isEmpty()){
   guns++;var model=ModelCache.rigFor(gun,gun.modelScale).animation;
   // Fixed actor pose in world space. Camera-relative translation is supplied by LevelRenderer.
   var gunWorld=new Matrix4f().translate(4.25F,1.4F,-7.5F).rotateY(.37F).rotateX(-.23F).rotateZ(.1F).scale(.625F);
   var origins=List.of(new Vector3f(FmumActionPose.vector(model,"caseOrigin")),
    FmumMuzzlePose.smoke(gun,model,false).getTranslation(new Vector3f()),
    FmumMuzzlePose.smoke(gun,model,true).getTranslation(new Vector3f()));
   var originalCase=new FmumCasing(casing,model,gunWorld,false,31,0);
   for(float yaw:new float[]{-179,-90,-17,0,61,90,179})for(float pitch:new float[]{-70,-15,0,38,70}){
    var rotation=camera(yaw,pitch);var stage=view(rotation);
    var cameraPosition=new Vector3f(2.5F,-.7F,3.2F);
    var relative=new Matrix4f().translate(new Vector3f(cameraPosition).negate()).mul(gunWorld);
    var converted=FmumWorldEffectsPose.gunToCameraRelativeWorld(relative,rotation,stage);
    for(var point:origins){
     var expected=gunWorld.transformPosition(new Vector3f(point));
     close(converted.transformPosition(new Vector3f(point)).add(cameraPosition),expected,"birth stays on gun with third-person camera rotation: "+gun.shortName);
     // Rotate and move the camera after emission. Stored world points must remain fixed.
     for(float laterYaw:new float[]{yaw,yaw+180F,yaw-47F}){
      var laterView=view(camera(laterYaw,pitch+8F));var laterCamera=new Vector3f(-1.3F,2.4F,6.1F);
      var renderPoint=new Vector3f(expected).sub(laterCamera);
      for(var shader:List.of(new Matrix4f(laterView),new Matrix4f(),new Matrix4f().rotateY(.2F).mul(laterView))){
       var correction=FmumWorldEffectsPose.worldStage(laterView,shader);
       var actual=new Matrix4f(shader).mul(correction).transformPosition(new Vector3f(renderPoint));
       close(actual,laterView.transformPosition(new Vector3f(renderPoint)),"view applied once, including alternate shader frame");samples++;
      }
     }
    }
    var sameCase=new FmumCasing(casing,model,converted,false,31,0);
    close(sameCase.velocity,originalCase.velocity,"third-person camera never changes ejection direction");
    // In first person the hand pose starts with inverse view and the shader restores it.
    var hand=new Matrix4f(stage).invert().translate(.56F,-.52F,-.72F).rotateY(.6F).scale(.4F);
    var fromHand=FmumWorldEffectsPose.gunToCameraRelativeWorld(hand,rotation,stage);
    var expectedHand=new Matrix4f().rotate(rotation).translate(.56F,-.52F,-.72F).rotateY(.6F).scale(.4F);
    check(fromHand.equals(expectedHand,.0002F),"first-person emission frame remains correct");
   }
  }
  var v=view(camera(53,31));var fixture=new Vector3f(2,1,-5);
  check(new Matrix4f(v).mul(v).transformPosition(new Vector3f(fixture)).distance(v.transformPosition(new Vector3f(fixture)))>1F,"regression fixture detects the previous double rotation");
  var node=new ClassNode();try(var in=VerifyWorldEffects.class.getResourceAsStream("/com/flansmod/client/render/MuzzleEffectsRenderer.class")){new ClassReader(in).accept(node,0);}
  int birth=0,draw=0;for(var method:node.methods)for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call && call.owner.endsWith("FmumWorldEffectsPose")){
   if(call.name.equals("gunToCameraRelativeWorld"))birth++;if(call.name.equals("worldStage"))draw++;
  }
  check(birth==2&&draw==1,"actual renderer uses tested conversions for both births and world draw");check(guns==31,"31 EFT guns");
  System.out.printf("PASS: %d view samples, 31 real firearm smoke/case origins, front/back/pitched/moving cameras, shader view applied once, camera-independent ejection velocity, unchanged first-person conversion and production renderer wiring.%n",samples);
 }
}
