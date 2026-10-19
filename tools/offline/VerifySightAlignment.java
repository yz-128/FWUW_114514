import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import com.flansmod.common.GunSightState;
import com.flansmod.common.types.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Real AK74M ADS placement and authored side-rail adapters; no game or OpenGL. */
public final class VerifySightAlignment {
    private static int checks,failures;
    private static void check(boolean ok,String label){checks++;if(!ok){failures++;System.out.println("FAIL: "+label);}}
    private static ModifiableType.DefaultAttachment node(String name,int slot){
        var n=new ModifiableType.DefaultAttachment();n.name=name;n.slot=slot;return n;
    }
    private static GunType mounted(GunType ak,String optic){
        var tree=new ArrayList<List<ModifiableType.DefaultAttachment>>();
        for(var group:ak.defaultAttachments)tree.add(new ArrayList<>(group));
        while(tree.size()<ak.slots.length)tree.add(new ArrayList<>());
        if(optic.equals("PSO1"))tree.get(6).add(node(optic,6));
        else {
            var side=node("SVDLowSideMount",6);var lens=node(optic,0);
            if(optic.equals("NF7x")){var ring=node("NF34mm",0);ring.children.add(lens);side.children.add(ring);}
            else side.children.add(lens);
            tree.get(6).add(side);
        }
        return ak.withAttachments(tree);
    }
    private static Matrix4f gunPose(GunType view,ModelGeometry.Model body){
        Matrix4f pose=new Matrix4f().translate(.56F,-.52F,-.72F);
        FmumFirstPersonPose.itemFrame(pose);
        FmumFirstPersonPose.placement(pose,body,1,0,0);
        FmumFirstPersonPose.aim(pose,view,1);
        return pose;
    }
    private static void optic(GunType ak,ModelGeometry.Model body,String name){
        var view=mounted(ak,name);var sight=GunSightState.scopeSight(view);
        check(sight!=null && sight.type().shortName.equals(name),"real ADS selects "+name);
        var rig=ModelCache.rigFor(sight.type(),sight.type().modelScale);var model=rig.animation;
        Vector3f mount=ScopeReticleRenderer.mount(sight);
        Matrix4f toEye=gunPose(view,body).translate(mount).rotateX((float)Math.toRadians(sight.roll()-sight.type().aimCenterX));
        Vector3f objective=toEye.transformPosition(FmumSightPicture.vector(model,"scopeObjectiveLensCenter",0,0,0));
        float dy=objective.y/Math.max(.001F,-objective.z)*FmumActionPose.scalar(model,"scopeMaskMultTransY",.4F);
        float dz=objective.x/Math.max(.001F,-objective.z)*FmumActionPose.scalar(model,"scopeMaskMultTransZ",.4F);
        float[] divisors=model.fmumArrays.getOrDefault("scopeMaskAlphaDivisor",new float[]{.00003F});
        float divisor=divisors.length==0?.00003F:divisors[0];
        float brightness=Math.clamp(1-(dy*dy+dz*dz)/Math.max(1e-9F,divisor),0,1);
        float minimum=FmumActionPose.scalar(model,"minAlphaToRenderScopeTex",.05F);
        System.out.printf(Locale.ROOT,"%s actual mount=%s objectiveEye=(%.8f,%.8f,%.8f) mask=(%.8f,%.8f) divisor=%.8f brightness=%.8f threshold=%.8f%n",
                name,mount,objective.x,objective.y,objective.z,dy,dz,divisor,brightness,minimum);
        check(objective.isFinite() && objective.z<0,"actual objective projects in front of the eye "+name);
        check(brightness>=minimum && brightness>=.05F,"complete ADS optical picture remains visible "+name);
        Vector3f pupil=FmumSightPicture.vector(model,"pupilRefCenter",0,0,0).mul(sight.type().modelScale);
        Vector3f scale=FmumSightPicture.vector(model,"scopeGlassModelScale",1,1,1).mul(sight.type().modelScale);
        Matrix4f lens=new Matrix4f(toEye).translate(pupil).scale(scale);
        var glass=ModelCache.rigForReference(sight.type().scopeGlassModel,1F);
        var face=glass.quads.stream().filter(q->q.nx()<-.9F).findFirst().orElseThrow();
        float u=face.uvs()[0][0],v=face.uvs()[0][1];
        Vector3f p=lens.transformPosition(ScopeReticleRenderer.glassPosition(face,u,v));
        Vector3f right=lens.transformPosition(ScopeReticleRenderer.glassPosition(face,u+.01F,v)).sub(p);
        Vector3f down=lens.transformPosition(ScopeReticleRenderer.glassPosition(face,u,v+.01F)).sub(p);
        System.out.printf(Locale.ROOT,"%s real glass UV-to-eye basis dX/dU=%.8f dY/dV=%.8f%n",name,right.x/.01F,down.y/.01F);
        // How much of the screen the ocular covers decides whether magnifying the world pass needs
        // a surround at all: if the ocular's angular radius exceeds the magnified half-FOV, the
        // scope picture already fills the screen, exactly as a real scope does.
        float pupilDistance=FmumSightPicture.pupilDistance(model,sight.type().modelScale,new Vector3f());
        float ocularAngle=0F;
        for(var quad:glass.quads)for(int i=0;i<quad.positions().length;i++) {
            float[] pos=quad.positions()[i];
            Vector3f e=lens.transformPosition(new Vector3f(pos[0],pos[1],pos[2]));
            if(e.z>=0)continue;
            ocularAngle=Math.max(ocularAngle,(float)Math.toDegrees(Math.atan(Math.hypot(e.y,e.x)/-e.z)));
        }
        for(int step=0;step<Math.max(1,sight.type().fovZoomLevels.length);step++) {
            double zoom=ScopeMagnification.zoom(sight.type(),step);
            double half=Math.toDegrees(Math.atan(Math.tan(Math.toRadians(70)/2)/zoom));
            System.out.printf(Locale.ROOT,
                    "%s step=%d zoom=%.3f ocularHalfAngle=%.3f deg magnifiedHalfFov=%.3f deg coversScreen=%s%n",
                    name,step,zoom,ocularAngle,half,ocularAngle>=half);
        }
        // Regression evidence: the actual PSO1Glass has both raw axes reversed. The first run
        // caught all four scopes sampling a mirrored world; never hide that by inventing a mesh.
        check(right.x<0 && down.y>0,"real PSO1Glass retains authored double-reversed UV evidence "+name);
        float[] middle=ScopeReticleRenderer.authoredUv(glass,.5F,.5F);
        float[] imageRight=ScopeReticleRenderer.authoredUv(glass,.51F,.5F);
        float[] imageDown=ScopeReticleRenderer.authoredUv(glass,.5F,.51F);
        Vector3f imageCenter=lens.transformPosition(ScopeReticleRenderer.glassPosition(face,middle[0],middle[1]));
        Vector3f correctedRight=lens.transformPosition(ScopeReticleRenderer.glassPosition(face,imageRight[0],imageRight[1])).sub(imageCenter);
        Vector3f correctedDown=lens.transformPosition(ScopeReticleRenderer.glassPosition(face,imageDown[0],imageDown[1])).sub(imageCenter);
        check(correctedRight.x>0,"corrected optical image points screen-right "+name);
        check(correctedDown.y<0,"corrected optical image points screen-down "+name);
        float[] sceneRight=ScopeReticleRenderer.imageUv(glass,imageRight[0],imageRight[1]);
        check(sceneRight[0]>.5F,"same actual glass point samples right side of the world image "+name);
    }
    public static void main(String[]args)throws Exception{
        var pack=new OfflinePack(Path.of(args[0]));pack.readTypes();
        var ak=(GunType)InfoType.infoTypes.stream().filter(t->t instanceof GunType && "AK74M".equals(t.shortName)).findFirst().orElseThrow();
        var body=ModelCache.load(ak);check(body!=null && FmumFirstPersonPose.hasPose(body),"actual AK74M authored pose loaded");
        for(String optic:List.of("PSO1","NF7x","SeecterDR1x4x","G33Enabled"))optic(ak,body,optic);
        if(failures>0)throw new AssertionError(failures+" of "+checks+" optical alignment checks failed");
        System.out.println("PASS: "+checks+" assertions; real AK74M full ADS, real side-rail/ring mounts, scope brightness and actual glass/world-reticle UV handedness. Offline only; no game/OpenGL executed.");
    }
}
