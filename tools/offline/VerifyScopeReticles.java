import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.FmumSightPicture;
import com.flansmod.client.render.ScopeReticleRenderer;
import com.flansmod.client.render.ScopeReticleRenderer.Projected;
import com.flansmod.common.GunSightState;
import com.flansmod.common.types.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Actual scope-reticle vertex emission plus independent clipping/projection examples; no GL/game. */
public final class VerifyScopeReticles {
    private static int checks;
    private static void check(boolean ok, String label) { checks++; if (!ok) throw new AssertionError(label); }
    private static void near(float actual, float expected, String label) {
        check(Float.isFinite(actual) && Math.abs(actual - expected) < 2E-5F, label + " actual=" + actual + " expected=" + expected);
    }
    private static void point(Vector3f p, Vector3f expected, String label) {
        check(p != null, label + " exists");near(p.x, expected.x, label + " X");near(p.y, expected.y, label + " Y");near(p.z, expected.z, label + " Z");
    }
    private static Projected vertex(float u, float v) { return new Projected(u, v, u * 2 + 3, v * 3 - 2); }
    private static final float[][] WINDOW = {{0,0},{1,0},{1,1},{0,1}};

    private static double area(List<Projected> polygon) {
        double value=0;for(int i=0;i<polygon.size();i++){var a=polygon.get(i);var b=polygon.get((i+1)%polygon.size());value+=a.u()*b.v()-b.u()*a.v();}return Math.abs(value)/2;
    }
    private static void geometry() {
        float[] center=ScopeReticleRenderer.project(new Vector3f(1,0,0),new Vector3f(),90);
        near(center[0],.5F,"forward optical axis U");near(center[1],.5F,"forward optical axis V");
        float[] corner=ScopeReticleRenderer.project(new Vector3f(1,1,1),new Vector3f(),90);
        near(corner[0],1F,"positive Z is framebuffer right");near(corner[1],0F,"positive Y is framebuffer top");
        check(ScopeReticleRenderer.project(new Vector3f(-1,0,0),new Vector3f(),90)==null,"behind-scope point cannot project");
        check(ScopeReticleRenderer.project(new Vector3f(),new Vector3f(),90)==null,"point at pupil cannot divide by zero");
        var large=List.of(vertex(-1,-1),vertex(2,-1),vertex(2,2),vertex(-1,2));
        var clipped=ScopeReticleRenderer.clip(large,WINDOW);
        near((float)area(clipped),1F,"large reticle clips to exact square lens area");
        for(var p:clipped){
            check(p.u()>=-1E-5 && p.u()<=1.00001 && p.v()>=-1E-5 && p.v()<=1.00001,"clipped vertex inside lens");
            near(p.textureU(),p.u()*2+3,"reticle U interpolates at newly introduced edge");
            near(p.textureV(),p.v()*3-2,"reticle V interpolates at newly introduced edge");
        }
        float[][] reverse={{0,1},{1,1},{1,0},{0,0}};
        near((float)area(ScopeReticleRenderer.clip(large,reverse)),1F,"lens polygon may use either winding");
        var outside=List.of(vertex(2,2),vertex(3,2),vertex(3,3),vertex(2,3));
        check(ScopeReticleRenderer.clip(outside,WINDOW).isEmpty(),"reticle entirely outside lens emits nothing");
        check(ScopeReticleRenderer.clip(large,new float[][]{{0,0},{.5F,.5F},{1,1}}).isEmpty(),"degenerate UV aperture is rejected");
        var split=List.of(vertex(.25F,-1),vertex(.75F,-1),vertex(.75F,2),vertex(.25F,2));
        near((float)area(ScopeReticleRenderer.clip(split,WINDOW)),.5F,"partial reticle clipping preserves its correct area");
        var quad=new BakedRig.Quad(new float[][]{{0,2,7},{0,2,3},{0,6,3},{0,6,7}},
                new float[][]{{0,0},{1,0},{1,1},{0,1}},-1,0,0);
        point(ScopeReticleRenderer.glassPosition(quad,.25F,.75F),new Vector3f(0,5,6),"UV maps affinely into rolled/flipped mesh positions");
        for(int i=0;i<4;i++)point(ScopeReticleRenderer.glassPosition(quad,quad.uvs()[i][0],quad.uvs()[i][1]),
                new Vector3f(quad.positions()[i]),"authored lens corner "+i);
    }

    private static ModifiableType.DefaultAttachment node(AttachmentType type,int slot) {
        var n=new ModifiableType.DefaultAttachment();n.name=type.shortName;n.slot=slot;return n;
    }
    private static GunType gun(String name,int slots) {
        var file=new TypeFile(EnumType.gun,name,false);file.lines.add("ShortName "+name);
        var gun=new GunType(file);gun.read(file);gun.slots=new ModifiableType.AttachableSlot[slots];
        for(int i=0;i<slots;i++)gun.slots[i]=new ModifiableType.AttachableSlot();return gun;
    }
    private static void mounts() {
        var file=new TypeFile(EnumType.attachment,"ScopePoseFixture",false);
        var type=new AttachmentType(file);type.aimCenterX=17;type.aimCenterY=2;type.aimCenterZ=3;
        var n=node(type,0);
        // A physical roll of 90 degrees maps the aim offset (Y=2,Z=3) to (Y=-3,Z=2).
        var sight=new GunSightState.Sight(new int[]{0,0},type,n,1,1,7,107);
        point(ScopeReticleRenderer.mount(sight),new Vector3f(1,4,5),"AimCenter and its extra roll do not move physical mount");
        point(ScopeReticleRenderer.intoScope(sight,sight).transformPosition(new Vector3f(4,5,6)),
                new Vector3f(4,5,6),"self-to-scope transform is identity despite AimCenter");
    }

    private static void imageOrientation() {
        float[][] corners={{0,0},{1,0},{1,1},{0,1}};
        float[][] positions={{0,1,-1},{0,1,1},{0,-1,1},{0,-1,-1}};
        for(float[][] uv:new float[][][]{corners,{{1,1},{0,1},{0,0},{1,0}},
                {{1,0},{1,1},{0,1},{0,0}},{{1,0},{0,0},{0,1},{1,1}}}) {
            var rig=new BakedRig();rig.quads.add(new BakedRig.Quad(positions,uv,-1,0,0));
            for(int i=0;i<4;i++) {
                float[] image=ScopeReticleRenderer.imageUv(rig,uv[i][0],uv[i][1]);
                near(image[0],corners[i][0],"normal/mirrored/rotated lens samples image-right consistently");
                near(image[1],corners[i][1],"normal/mirrored/rotated lens samples image-down consistently");
                float[] authored=ScopeReticleRenderer.authoredUv(rig,image[0],image[1]);
                near(authored[0],uv[i][0],"lens image/authored U round trip");near(authored[1],uv[i][1],"lens image/authored V round trip");
            }
            float[] authored=ScopeReticleRenderer.authoredUv(rig,.26F,.73F);
            float[] image=ScopeReticleRenderer.imageUv(rig,authored[0],authored[1]);
            near(image[0],.26F,"non-corner optical U round trip");near(image[1],.73F,"non-corner optical V round trip");
        }
    }

    private static final class Recorder implements MultiBufferSource, VertexConsumer {
        final List<Vector3f> points=new ArrayList<>();final List<float[]> uvs=new ArrayList<>();final List<String> materials=new ArrayList<>();
        int fullBright;
        public VertexConsumer getBuffer(RenderType renderType){materials.add(renderType.toString());return this;}
        public VertexConsumer addVertex(float x,float y,float z){check(Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(z),"actual emitted vertex finite");points.add(new Vector3f(x,y,z));return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){check(a==255,"reticle preserves opaque vertex alpha for texture blending");return this;}
        public VertexConsumer setUv(float u,float v){check(Float.isFinite(u)&&Float.isFinite(v),"interpolated texture UV finite");uvs.add(new float[]{u,v});return this;}
        public VertexConsumer setUv1(int u,int v){return this;}
        public VertexConsumer setUv2(int u,int v){if((u|(v<<16))==LightTexture.FULL_BRIGHT)fullBright++;return this;}
        public VertexConsumer setNormal(float x,float y,float z){check(Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(z),"actual normal finite");return this;}
    }

    private static Recorder render(GunType view,GunSightState.Sight scope,boolean mirrored) {
        var rig=ModelCache.rigFor(scope.type(),scope.type().modelScale);
        var glass=ModelCache.rigForReference(scope.type().scopeGlassModel,1F);
        check(rig!=null && glass!=null,"real scope body and glass loaded");
        var eye=FmumSightPicture.vector(rig.animation,"pupilRefCenter",0,0,0).mul(scope.type().modelScale).add(-.5F,0,0);
        var toEye=new Matrix4f().rotationY((float)Math.PI/2).translate(new Vector3f(eye).negate());
        var projection=new Matrix4f().perspective((float)Math.toRadians(70),16F/9F,.01F,100F);
        var recorder=new Recorder();ScopeReticleRenderer.render(new PoseStack(),recorder,view,scope,rig,glass,toEye,projection,35,0,mirrored);
        check(!recorder.points.isEmpty() && recorder.points.size()%4==0,"actual renderer emits complete clipped reticle quads");
        check(recorder.fullBright==recorder.points.size(),"every actual reticle vertex is emissive full-bright");
        for(var p:recorder.points){
            near(p.x,glass.minX-.001F,"reticle lies just in front of actual glass");
            check(p.y>=glass.minY-1E-4 && p.y<=glass.maxY+1E-4 && p.z>=glass.minZ-1E-4 && p.z<=glass.maxZ+1E-4,
                    "actual emitted geometry stays within real strip bounds");
        }
        return recorder;
    }

    private static void realOptics() {
        var pso=AttachmentType.getAttachment("PSO1");var solo=gun("ScopeSoloFixture",1);var psoNode=node(pso,0);psoNode.dotFrame=2;
        solo.defaultAttachments=List.of(List.of(psoNode));var scope=GunSightState.scopeSight(solo);
        check(scope!=null,"PSO1 is its own active scope");var normal=render(solo,scope,false);
        check(normal.materials.stream().anyMatch(s->s.contains(com.flansmod.client.render.Textures.forReticle(pso,2).toString())),"actual PSO1 reticle uses per-node frame 2");
        var mirrored=render(solo,scope,true);check(mirrored.points.size()==normal.points.size(),"mirrored hand retains all clipped geometry");
        for(int i=0;i<normal.points.size();i+=4){
            point(mirrored.points.get(i),normal.points.get(i+2),"mirrored reticle triangle reverses first corner");
            point(mirrored.points.get(i+2),normal.points.get(i),"mirrored reticle triangle reverses last corner");
        }
        var magnifier=AttachmentType.getAttachment("G33Enabled");var cobra=AttachmentType.getAttachment("CobraEKPrefiexSightPic");
        check(magnifier!=null && cobra!=null,"actual G33 and Cobra definitions present");
        var combo=gun("ScopeCoaxialFixture",2);combo.slots[0].x=.5F;combo.slots[0].y=magnifier.aimCenterY-cobra.aimCenterY;
        var redNode=node(cobra,0);redNode.dotFrame=4;combo.defaultAttachments=List.of(List.of(redNode),List.of(node(magnifier,1)));
        check(GunSightState.selectedGroup(combo).sights().size()==2,"G33 shares the Cobra optical line");
        var g33=GunSightState.scopeSight(combo);check(g33!=null && g33.type()==magnifier,"G33 supplies glass behind forward red dot");
        var composite=render(combo,g33,false);
        check(composite.materials.stream().anyMatch(s->s.contains(com.flansmod.client.render.Textures.forReticle(cobra,4).toString())),"G33 carries the coaxial red dot's frame 4 into its glass");
        check(composite.materials.stream().noneMatch(s->s.contains(com.flansmod.client.render.ReticleResources.texture(magnifier,0).toString())),"G33 DotModel None never invents a separate dot");
        var offAxis=gun("ScopeSeparateLineFixture",3);offAxis.slots[0].x=combo.slots[0].x;offAxis.slots[0].y=combo.slots[0].y;
        offAxis.slots[2].y=1;offAxis.selectedSightGroup=1;
        offAxis.defaultAttachments=List.of(List.of(redNode),List.of(node(magnifier,1)),List.of(node(pso,2)));
        check(GunSightState.groups(offAxis).size()==2,"off-axis PSO1 occupies a separate selectable line");
        var selected=GunSightState.scopeSight(offAxis);check(selected!=null && selected.type()==magnifier,"G33 line stays active with another mounted scope");
        var isolated=render(offAxis,selected,false);
        check(isolated.materials.stream().noneMatch(s->s.contains(com.flansmod.client.render.ReticleResources.texture(pso,0).toString())),"unselected optical line contributes no reticle to G33");
        check(isolated.points.size()==composite.points.size(),"unselected scope cannot add vertices to the selected image");
    }

    public static void main(String[] args)throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        var pack=new OfflinePack(Path.of(args[0]));pack.readTypes();geometry();mounts();imageOrientation();realOptics();
        System.out.println("PASS: "+checks+" assertions; scope projection, polygon/texture UV clipping, actual PSO1 glass emission, mirrored hand and real G33/Cobra coaxial reticle composition. Offline only; no game/OpenGL executed.");
    }
}
