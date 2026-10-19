import com.flansmod.client.model.loader.*;
import com.flansmod.common.types.*;
import java.nio.file.*;
import java.util.*;

/** Read-only diagnostics for the source pistol, attachment tree and recovered model groups. */
public final class InspectBocchiSlide {
    public static void main(String[] args)throws Exception {
        var pack=new OfflinePack(Path.of(args[0]));pack.readTypes();
        for(String name:List.of("BocchiTheGlock18C","Glock18CSlideBocchi","Glock18CSlide","Crossline")) {
            var type=InfoType.getType(name);System.out.println("TYPE "+name+" "+type);
            if(!(type instanceof ModifiableType owner))continue;
            System.out.println(" model="+type.modelString+" scale="+type.modelScale);
            for(int i=0;i<owner.slots.length;i++){var slot=owner.slots[i];System.out.printf(" slot %d xyz=%f,%f,%f rotation=%f%n",i,slot.x,slot.y,slot.z,slot.rotate);}
            for(var group:owner.defaultAttachments)for(var node:group)System.out.printf(" attached %s slot=%d pos=%f children=%d%n",node.name,node.slot,node.pos,node.children.size());
            var m=ModelCache.load(type);if(m==null)continue;
            System.out.printf(" model=%s flipped=%s translate=%f,%f,%f slide=%f scalars=%s%n",m.className,m.flipped,m.translateAllX,m.translateAllY,m.translateAllZ,m.gunSlideDistance,m.fmumScalars);
            for(var part:m.parts.values()){
                var rig=BakedRig.bake(m,type.modelScale);var quads=rig.quadsByPart.getOrDefault(part.name,List.of());
                float[] bounds={Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY,Float.NEGATIVE_INFINITY};
                for(var q:quads)for(var v:q.positions())for(int i=0;i<3;i++){bounds[i]=Math.min(bounds[i],v[i]);bounds[i+3]=Math.max(bounds[i+3],v[i]);}
                System.out.printf(" part=%s boxes=%d prisms=%d quads=%d bounds=%s%n",part.name,part.boxes.size(),part.prisms.size(),quads.size(),Arrays.toString(bounds));
            }
        }
    }
}
