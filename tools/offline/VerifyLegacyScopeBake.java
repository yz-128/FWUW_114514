import com.flansmod.client.model.loader.BakedRig;
import com.flansmod.client.model.loader.ModelCache;
import com.flansmod.client.model.loader.ModelGeometry;
import com.flansmod.client.render.*;
import com.flansmod.common.GunSightState;
import com.flansmod.common.types.*;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.server.Bootstrap;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.file.Path;
import java.util.*;

/** Exercises production bake(), including cases the earlier glass-only tests never called. */
public final class VerifyLegacyScopeBake {
    private static int checks;
    private static void check(boolean value,String message) { checks++; if(!value)throw new AssertionError(message); }
    private static void near(float actual,float expected,String message) {
        check(Float.isFinite(actual)&&Math.abs(actual-expected)<3E-5F,message+": "+actual+" != "+expected);
    }
    private static void xyz(Vector3f p,Vector3f expected,String message) {
        near(p.x,expected.x,message+" X");near(p.y,expected.y,message+" Y");near(p.z,expected.z,message+" Z");
    }
    private static void coordinates() {
        // Independently evaluated original static matrix, from the MC/Forge/RenderGun factors.
        var eye=LegacyScopeProjection.base().invert().transformPosition(new Vector3f());
        xyz(eye,new Vector3f(-.50879327F,.39998328F,.00007573F),"original matrix inverse eye");
        var fixed=LegacyScopeProjection.base().transformPosition(new Vector3f(-81.5F/160F,.4F,0));
        xyz(fixed,new Vector3f(-.00004558F,.00000997F,.00034902F),"CPU tuned eye differs slightly from GL origin");
        var objective=new Vector3f(.3F,.2F,-.1F);
        for(float ads:new float[]{0,.25F,.5F,.75F,1}) {
            var placed=LegacyScopeProjection.scopePlacement(objective,90F,ads).transformPosition(new Vector3f(objective));
            xyz(placed,new Vector3f(-69.5F/160F,.4F,0),"objective pivot remains fixed at ADS "+ads);
            near(LegacyScopeProjection.dotRotation(90,90,ads),90*(1-ads),"runtime own roll cancellation");
        }
        near(LegacyScopeProjection.dotRotation(135,90,.5F),90,"coaxial part retains relative roll");
        var oldWrong=new Matrix4f().translate(-69.5F/160F-objective.x,.4F-objective.y,-objective.z)
                .rotateX((float)Math.PI/2).transformDirection(new Vector3f(0,1,0));
        var correct=LegacyScopeProjection.scopePlacement(objective,90,1).rotateX((float)Math.PI/2)
                .transformDirection(new Vector3f(0,1,0));
        check(oldWrong.distance(correct)>1F,"90-degree mount exposes omitted scope-roll counter-rotation");
        xyz(correct,new Vector3f(0,1,0),"full ADS cancels physical roll");
        var crossing=List.of(new LegacyScopeProjection.Vertex(-.1F,-.1F,-1,0,0),
                new LegacyScopeProjection.Vertex(.1F,-.1F,-1,1,0),
                new LegacyScopeProjection.Vertex(.1F,.1F,.1F,1,1),
                new LegacyScopeProjection.Vertex(-.1F,.1F,.1F,0,1));
        var clipped=LegacyScopeProjection.clipCameraPolygon(crossing,90,.05F);
        check(clipped.size()>=3,"face crossing near plane is clipped, not discarded");
        for(var v:clipped) {
            var p=LegacyScopeProjection.project(v.position(),90);
            check(p!=null&&p[0]>=-1E-5F&&p[0]<=1.00001F&&p[1]>=-1E-5F&&p[1]<=1.00001F,"clipped vertices lie inside square");
            check(v.textureU()>=0&&v.textureU()<=1&&v.textureV()>=0&&v.textureV()<=1,"clipping retains bounded atlas UV");
            check(v.z()<=-.04999F,"clipped vertex retains near distance");
        }
    }

    private static final class Recorder implements MultiBufferSource,VertexConsumer {
        final List<Vector3f> points=new ArrayList<>();final List<float[]> uv=new ArrayList<>();
        public VertexConsumer getBuffer(RenderType type){return this;}
        public VertexConsumer addVertex(float x,float y,float z){check(Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(z),"production bake vertex finite");points.add(new Vector3f(x,y,z));return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){check(a==255,"authored reticle keeps opaque vertex alpha");return this;}
        public VertexConsumer setUv(float u,float v){check(Float.isFinite(u)&&Float.isFinite(v),"production bake UV finite");uv.add(new float[]{u,v});return this;}
        public VertexConsumer setUv1(int u,int v){return this;}
        public VertexConsumer setUv2(int u,int v){return this;}
        public VertexConsumer setNormal(float x,float y,float z){return this;}
    }

    private static void actualBake() {
        var type=AttachmentType.getAttachment("SeecterDR1x4x");check(type!=null,"real scope definition available");
        var file=new TypeFile(EnumType.gun,"ScopeBakeRegressionFixture",false);file.lines.add("ShortName ScopeBakeRegressionFixture");
        var gun=new GunType(file);gun.read(file);gun.slots=new ModifiableType.AttachableSlot[]{new ModifiableType.AttachableSlot()};
        var node=new ModifiableType.DefaultAttachment();node.name=type.shortName;node.slot=0;
        gun.defaultAttachments=List.of(List.of(node));
        var scope=GunSightState.scopeSight(gun);check(scope!=null,"scope selected for real bake");
        var rig=new BakedRig();rig.animation=new ModelGeometry.Model();
        rig.animation.fmumVectorArrays.put("reticleBorder",new float[][]{{.1F,-.1F,-.1F},{.1F,.1F,-.1F},{.1F,.1F,.1F},{.1F,-.1F,.1F}});
        rig.animation.fmumVectorArrays.put("reticleAt",new float[][]{{1,0,0},{1,.1F,0}});
        rig.animation.fmumScalars.put("reticleScale",1F);
        var skin=Textures.forReticle(type,0);var dot=ModelCache.rigForReference(type.lightStuffModel,1F,skin);
        check(dot!=null,"real reticle cache available");
        var originalQuads=new ArrayList<>(dot.quads);var originalParts=new LinkedHashMap<>(dot.quadsByRenderer);
        // Only these in-memory cache entries are replaced in this isolated verifier process.
        // No pack, source model or resource file is edited.
        try {
            var q=new BakedRig.Quad(new float[][]{{0,-.002F,-.004F},{0,-.002F,.004F},{0,.002F,.004F},{0,.002F,-.004F}},
                    new float[][]{{0,0},{1,0},{1,1},{0,1}},-1,0,0);
            dot.quads.clear();dot.quads.add(q);dot.quadsByRenderer.clear();
            dot.quadsByRenderer.put("defaultModel#0",List.of(q));dot.quadsByRenderer.put("defaultModel#1",List.of(q));
            var placement=LegacyScopeProjection.scopePlacement(new Vector3f(),0,1);
            var toEye=LegacyScopeProjection.base().mul(placement);
            var capture=new Recorder();ScopeReticleRenderer.bake(capture,gun,scope,rig,toEye,90,1,1);
            check(capture.points.size()==12,"two visible reticle parts emit two complete triangle pairs");
            for(int i=0;i<6;i++)xyz(capture.points.get(i+6),capture.points.get(i),"multi-ray parts share target-zero anchor "+i);
            // A non-zero offset for target 1 is a counterexample to the former per-target hit placement.
            float plane=LegacyScopeProjection.SCOPE_X+.1F;
            float nativeHit1=(plane-LegacyScopeProjection.EYE_X)/(1+LegacyScopeProjection.SCOPE_X-LegacyScopeProjection.EYE_X)*.1F;
            check(nativeHit1>.01F,"independent second-ray intersection would visibly move the second part");
            rig.animation.fmumVectorArrays.put("reticleAt",new float[][]{{1,0,0}});
            var rolled=new GunSightState.Sight(scope.path(),type,node,scope.x(),-type.aimCenterZ,type.aimCenterY,90+type.aimCenterX);
            gun.slots[0].rotate=90;
            var before=new Recorder();ScopeReticleRenderer.bake(before,gun,rolled,rig,toEye,90,1,1);
            check(before.points.size()==6,"rolled scope bake survives full-ADS counter-rotation");
            for(int i=0;i<6;i++)xyz(before.points.get(i),capture.points.get(i),"full-ADS rolled reticle keeps upright orientation "+i);
        } finally {dot.quads.clear();dot.quads.addAll(originalQuads);dot.quadsByRenderer.clear();dot.quadsByRenderer.putAll(originalParts);}
    }

    public static void main(String[] args)throws Exception {
        coordinates();SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        new OfflinePack(Path.of(args[0])).readTypes();actualBake();
        System.out.println("PASS: "+checks+" assertions; original matrix constants, ADS scope pivot/roll, pre-divide clipping and actual production bake multi-ray/shared-anchor emission. No game/OpenGL started.");
    }
}
