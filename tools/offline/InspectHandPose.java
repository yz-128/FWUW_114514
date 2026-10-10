import com.flansmod.client.model.loader.*;
import com.flansmod.common.types.*;
import java.nio.file.*;
import java.util.*;
public final class InspectHandPose {
 public static void main(String[] args)throws Exception {
  var pack=new OfflinePack(Path.of(args[0]));pack.readTypes();
  for(String name:List.of("ColtM4A1","Model700","Mosin","TOZ106","AK74M")) {
   GunType gun=pack.types.stream().filter(t->t instanceof GunType && name.equals(t.shortName)).map(t->(GunType)t).findFirst().orElseThrow();
   var m=ModelCache.rigFor(gun,gun.modelScale).animation;
   System.out.println("\n"+name+" "+m.className+" parts="+m.parts.keySet());
   m.fmumPose.forEach((k,v)->{if(k.matches("(holding|aiming|leftArm|rightArm|precharge|arm).*"))System.out.println(k+" "+Arrays.toString(v));});
   walk(gun,gun.defaultAttachments,0);
  }
 }
 static void walk(ModifiableType owner,List<List<ModifiableType.DefaultAttachment>> groups,int depth)throws Exception {
  for(var group:groups)for(var n:group) {
   var a=AttachmentType.getAttachment(n.name);if(a==null)continue;
   var m=ModelCache.rigFor(a,a.modelScale).animation;
   System.out.printf("%s%s slot=%d pos=%s L=%s:%d R=%s:%d armT=%s armR=%s%n"," ".repeat(depth),a.shortName,n.slot,n.pos,a.leftHandPose,a.leftHandPriority,a.rightHandPose,a.rightHandPriority,Arrays.toString(m.fmumPose.get("armTranslate")),Arrays.toString(m.fmumPose.get("armRotate")));
   walk(a,List.of(n.children),depth+1);
  }
 }
}
