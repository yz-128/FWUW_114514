import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.TurborigItemRenderer;
import com.flansmod.client.render.SightFoldRenderer;
import com.flansmod.client.render.SightFoldGeometry;
import com.flansmod.common.types.AttachmentType;
import com.flansmod.common.network.GunSightAnimationPayload;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.lang.reflect.Method;
import java.util.*;
import org.joml.Vector3f;

/** Actual legacy-class material facts and production vertex emission, without game or GL. */
public final class VerifyLegacyEmission implements Opcodes {
    static int checks, glowModels, scanned;
    static void check(boolean value,String message) {checks++;if(!value)throw new AssertionError(message);}
    static final String ARRAY="[Lcom/flansmod/client/tmt/ModelRendererTurbo;";
    static final String OWNER="com/flansmod/client/model/EmissionFixture";
    static void array(MethodVisitor code,String field) {code.visitVarInsn(ALOAD,0);code.visitFieldInsn(GETFIELD,OWNER,field,ARRAY);}
    static void draw(MethodVisitor code,String field) {
        array(code,field);code.visitVarInsn(FLOAD,1);code.visitMethodInsn(INVOKESTATIC,OWNER,"render","("+ARRAY+"F)V",false);
    }
    static byte[] fixture(boolean unsupported) {
        var writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(V1_7,ACC_PUBLIC,OWNER,null,"com/flansmod/client/model/ModelAttachment",null);
        for(String field:List.of("attachmentModel","arbitraryPanel","lightModel","scopeGlassModel","laserModel"))
            writer.visitField(ACC_PUBLIC,field,ARRAY,null,null).visitEnd();
        var code=writer.visitMethod(ACC_PUBLIC,"render","(F)V",null,null);code.visitCode();
        draw(code,"attachmentModel");
        code.visitInsn(ICONST_3);code.visitMethodInsn(INVOKESTATIC,OWNER,"glowOn","(I)V",false);
        draw(code,"arbitraryPanel");code.visitMethodInsn(INVOKESTATIC,OWNER,"glowOff","()V",false);
        code.visitMethodInsn(INVOKESTATIC,OWNER,"glowOnStatic","()V",false);
        draw(code,"attachmentModel");code.visitMethodInsn(INVOKESTATIC,OWNER,"glowOff","()V",false);
        if(unsupported)code.visitMethodInsn(INVOKESTATIC,OWNER,"unknownStateDependentHelper","()V",false);
        code.visitInsn(RETURN);code.visitMaxs(0,0);code.visitEnd();writer.visitEnd();return writer.toByteArray();
    }
    static ModelGeometry.Model extract(byte[] bytes) throws Exception {return BytecodeModel.fromStream(new ByteArrayInputStream(bytes));}
    static void addBox(ModelGeometry.Model model,String field) {
        var part=new ModelGeometry.Part();part.name=field;
        var box=new ModelGeometry.Box();box.w=box.h=box.d=1;box.renderer=field+"#0";part.boxes.add(box);model.parts.put(field,part);
    }
    static Set<BakedRig.Quad> identities(Collection<BakedRig.Quad> quads) {
        var set=Collections.newSetFromMap(new IdentityHashMap<BakedRig.Quad,Boolean>());set.addAll(quads);return set;
    }
    static void synthetic() throws Exception {
        var geometry=extract(fixture(false));
        check(!geometry.emissiveRenderUnsupported && geometry.emissiveDrawsByMode.size()==256,"all signed-byte modes resolved");
        var draws=geometry.emissiveDrawsByMode.get(0);
        check(draws.size()==2,"only actual glow render sections become passes");
        check(draws.get(0).field().equals("arbitraryPanel"),"arbitrary mesh name is recognized from glow interval");
        check(draws.get(0).light().strength()==3 && !draws.get(0).light().absolute(),"explicit glowOn amount is retained");
        check(draws.get(1).light().absolute(),"glowOnStatic is independently retained");
        check(geometry.emissiveDrawsByMode.get(0)==geometry.emissiveDrawsByMode.get(255),"equal profiles share snapshots");
        for(String field:List.of("attachmentModel","arbitraryPanel","lightModel","scopeGlassModel","laserModel"))addBox(geometry,field);
        var rig=BakedRig.bake(geometry,1F);
        check(rig.emissivePasses(0).size()==2,"bake preserves pass split");
        check(rig.attachmentBody(0).isEmpty(),"a glow-drawn body is removed from the ordinary pass");
        check(rig.emissivePasses(0)==rig.emissivePasses(256),"fireMode follows legacy byte wrap");
        check(rig.emissivePasses(-1)==rig.emissivePasses(255),"negative fireMode byte maps safely");
        var emitted=identities(rig.emissivePasses(0).stream().flatMap(p->p.quads().stream()).toList());
        for(String field:List.of("lightModel","scopeGlassModel","laserModel"))
            for(var quad:rig.quadsByPart.get(field))check(!emitted.contains(quad),field+" is not guessed to be a glow material");
        var relative=draws.getFirst().light();var absolute=draws.getLast().light();
        check(relative.apply(0)==0x300030,"glowOn(3) adds three lightmap levels in darkness");
        check(relative.apply(0x500020)==0x800050,"block and sky channels brighten independently");
        check(relative.apply(0xE000D0)==0xF000F0,"relative glow clamps both channels at 240");
        check(absolute.apply(0)==0xF000F0 && absolute.apply(0x500020)==0xF000F0,"static glow ignores incoming light");
        check(new ModelGeometry.GlowLight(Integer.MAX_VALUE,false).apply(0)==0xF000F0,"extreme amount cannot overflow lightmap");
        check(new ModelGeometry.GlowLight(Integer.MIN_VALUE,false).apply(0xF000F0)==0,"negative amount cannot wrap lightmap");
        try{rig.emissivePasses(0).clear();throw new AssertionError("pass cache writable");}catch(UnsupportedOperationException expected){checks++;}
        try{rig.emissivePasses(0).getFirst().quads().clear();throw new AssertionError("quad cache writable");}catch(UnsupportedOperationException expected){checks++;}
        var fresh=BakedRig.bake(geometry,1F);
        check(fresh.emissivePasses(0)!=rig.emissivePasses(0),"new bake does not reuse a retired cache");
        var unknown=extract(fixture(true));
        check(unknown.emissiveRenderUnsupported && unknown.emissiveDrawsByMode.isEmpty(),"unknown state-dependent bytecode fails closed");
        var ordinary=new ModelGeometry.Model();addBox(ordinary,"attachmentModel");addBox(ordinary,"lightModel");
        check(BakedRig.bake(ordinary,1F).emissivePasses(0).isEmpty(),"lightModel name without proof stays ordinary/unselected");
        vertexEvidence(rig,0,0x500020,"synthetic relative/static",false);
        vertexEvidence(rig,0,0x500020,"synthetic mirrored",true);
    }
    static void production(Path root) throws Exception {
        try(var files=Files.walk(root)) {
            for(var file:files.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".class"))
                    .filter(p->!root.relativize(p).startsWith("_disabled")).sorted().toList()) {
                byte[] bytes=Files.readAllBytes(file);var cn=new ClassNode();new ClassReader(bytes).accept(cn,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
                boolean glow=false;
                for(var method:cn.methods)if(method.name.equals("render") && method.desc.equals("(F)V"))
                    for(var instruction:method.instructions)if(instruction instanceof MethodInsnNode call && call.name.equals("glowOn"))glow=true;
                var geometry=BytecodeModel.fromStream(new ByteArrayInputStream(bytes));scanned++;
                if(!glow) {check(geometry.emissiveDrawsByMode.isEmpty(),"ordinary original model gets no guessed emission");continue;}
                glowModels++;check(!geometry.emissiveRenderUnsupported,file+" original glow contract recognized");
                check(geometry.emissiveDrawsByMode.size()==256,file+" complete legacy byte-mode table");
                var rig=BakedRig.bake(geometry,1F);
                for(int mode:new int[]{-1,0,1,2,127,255,256}) {
                    var profile=geometry.emissiveDrawsByMode.get(mode&255);var passes=rig.emissivePasses(mode);
                    check(profile.size()==passes.size(),file+" every glow draw retains one mesh pass");
                    for(int pass=0;pass<profile.size();pass++) {
                        var draw=profile.get(pass);var actual=passes.get(pass);var expected=new ArrayList<BakedRig.Quad>();
                        if(draw.last()<0)expected.addAll(rig.quadsByPart.getOrDefault(draw.field(),List.of()));
                        else for(int i=draw.first();i<draw.last();i++)expected.addAll(rig.quadsByRenderer.getOrDefault(draw.field()+"#"+i,List.of()));
                        check(!expected.isEmpty() && actual.quads().size()==expected.size(),file+" exact actual renderer range");
                        for(int q=0;q<expected.size();q++)check(actual.quads().get(q)==expected.get(q),file+" geometry/UV identity retained");
                        check(actual.light().apply(0)==0xF000F0,file+" original glowOn() is full bright");
                    }
                }
                if(file.getFileName().toString().equals("ModelGlock18CSlideBocchi.class")) {
                    for(int mode=0;mode<3;mode++) {
                        var profile=geometry.emissiveDrawsByMode.get(mode);
                        var normal=geometry.normalDrawsByMode.get(mode);
                        check(profile.size()==2,"Bocchi has steady night sights and a fire-mode indicator");
                        check(normal.size()==2 && normal.get(1).field().equals("fireModeModel"),"Bocchi ordinary lever remains in the resolved render profile");
                        int first=mode==2?5:0,last=mode==2?6:5;
                        check(normal.get(1).first()==first && normal.get(1).last()==last,"Bocchi ordinary lever uses original mode-dependent range");
                        var lever=identities(rig.attachmentBody(mode));
                        for(int renderer=0;renderer<6;renderer++)for(var quad:rig.quadsByRenderer.get("fireModeModel#"+renderer))
                            check(lever.contains(quad)==(renderer>=first && renderer<last),"only current ordinary lever renderers are conserved");
                        check(rig.attachmentBody(mode).size()==rig.quadsByPart.get("attachmentModel").size()+(last-first)*6,
                                "Bocchi normal body plus exact lever range draw once");
                        check(profile.get(1).field().equals("fireModeLightModel") && profile.get(1).first()==mode && profile.get(1).last()==mode+1,
                                "Bocchi draws only one original indicator renderer per mode");
                        check(rig.emissivePasses(mode).get(1).quads().size()==6,"Bocchi mode indicator is one six-face box");
                    }
                    vertexEvidence(rig,2,0,"real Bocchi mode2",false);
                }
                if(file.getFileName().toString().equals("ModelBerettaM9RearSight.class")) {
                    check(geometry.emissiveDrawsByMode.getFirst().getFirst().field().equals("lightModel"),"real Beretta source calls glow around lightModel");
                    check(rig.emissivePasses(0).getFirst().quads().size()==36,"real reforged Beretta rear sight has six night-sight boxes");
                    vertexEvidence(rig,0,0,"real Beretta darkness",false);
                }
                if(file.getFileName().toString().equals("ModelMP443LightSight.class")) {
                    check(rig.emissivePasses(0).getFirst().quads().size()==54,"MP443 night sight has nine lightModel boxes");
                    vertexEvidence(rig,0,0,"real MP443 darkness",false);
                }
            }
        }
        check(glowModels>=45,"all current original glow class facts included");
    }
    static final class Recorder implements MultiBufferSource,VertexConsumer {
        int vertices,buffers,overlay;final List<Integer> lights=new ArrayList<>();
        final List<Vector3f> points=new ArrayList<>();boolean opaque=true;
        public VertexConsumer getBuffer(RenderType type){buffers++;return this;}
        public VertexConsumer addVertex(float x,float y,float z){check(Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(z),"actual vertex finite");points.add(new Vector3f(x,y,z));vertices++;return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){check(!opaque || a==255,"opaque material remains opaque");return this;}
        public VertexConsumer setUv(float u,float v){check(Float.isFinite(u)&&Float.isFinite(v),"actual UV finite");return this;}
        public VertexConsumer setUv1(int u,int v){overlay=u|v<<16;return this;}
        public VertexConsumer setUv2(int u,int v){lights.add(u|v<<16);return this;}
        public VertexConsumer setNormal(float x,float y,float z){return this;}
    }
    static List<Vector3f> points(List<BakedRig.Quad> quads,org.joml.Matrix4f matrix) {
        var result=new ArrayList<Vector3f>();for(var quad:quads)for(var point:quad.positions())result.add(matrix.transformPosition(new Vector3f(point)));return result;
    }
    static void folding(Path root) throws Exception {
        new OfflinePack(root.resolve("EscapeFromTarcraftReforged_1.5.3")).readTypes();
        new OfflinePack(root.resolve("OctagonWeaponsReforged_1.33")).readTypes();
        for(String[] pair:List.of(new String[]{"MP7Frontsight","MP7FrontsightFolded"},new String[]{"CICIS4FrontSight","CICIS4FrontSightFlipped"})) {
            var from=AttachmentType.getAttachment(pair[0]);var to=AttachmentType.getAttachment(pair[1]);
            var a=ModelCache.rigFor(from,from.modelScale);var b=ModelCache.rigFor(to,to.modelScale);
            check(a.hasEmissiveProfile() && b.hasEmissiveProfile(),"real folded endpoints have proven emission "+pair[0]);
            var event=new GunSightAnimationPayload.Part(new int[]{0,0},pair[0],pair[1]);
            var plan=SightFoldGeometry.plan(a,b);
            for(float phase:new float[]{0F,.5F,1F}) {
                var recorder=new Recorder();recorder.opaque=false;
                check(SightFoldRenderer.render(new PoseStack(),recorder,event,phase,0,0,false,false,0),"real fold renderer accepted "+pair[0]);
                var expected=new ArrayList<Vector3f>();
                if(phase>=1) for(var pass:b.emissivePasses(0))expected.addAll(points(pass.quads(),new org.joml.Matrix4f()));
                else for(var track:plan.tracks()) {
                    boolean source=!track.source().isEmpty() && a.attachmentVisible(track.source().getFirst(),0);
                    boolean target=!track.target().isEmpty() && b.attachmentVisible(track.target().getFirst(),0);
                    if(source && a.emissiveLight(track.source().getFirst(),0)!=null && (track.matched()&&target || phase<1))
                        expected.addAll(points(track.source(),track.pose(phase)));
                    if(target && phase>0 && b.emissiveLight(track.target().getFirst(),0)!=null)
                        expected.addAll(points(track.target(),track.targetPose(phase)));
                }
                var actual=new ArrayList<Vector3f>();
                for(int i=0;i<recorder.points.size();i++)if(recorder.lights.get(i)==0xF000F0)actual.add(recorder.points.get(i));
                check(!expected.isEmpty() && !actual.isEmpty(),"fold retains actual night-sight light vertices "+pair[0]);
                for(var point:actual)check(expected.stream().anyMatch(p->p.distanceSquared(point)<1E-8F),"light follows its same rigid folding pose");
                for(var point:expected)check(actual.stream().anyMatch(p->p.distanceSquared(point)<1E-8F),"no moving light endpoint geometry dropped");
                if(phase==0 || phase==1) {
                    var endpoint=phase==0?a:b;
                    int quads=endpoint.attachmentBody(0).size()+endpoint.emissivePasses(0).stream().mapToInt(p->p.quads().size()).sum();
                    check(recorder.vertices==quads*4,"fold endpoint draws every visible body/light quad exactly once");
                }
            }
        }
    }
    static void vertexEvidence(BakedRig rig,int mode,int light,String label,boolean mirrored) throws Exception {
        var method=Arrays.stream(TurborigItemRenderer.class.getDeclaredMethods()).filter(m->m.getName().equals("renderAttachmentSurface")).findFirst().orElseThrow();
        method.setAccessible(true);var recorder=new Recorder();
        method.invoke(null,new PoseStack(),recorder,rig,ResourceLocation.fromNamespaceAndPath("flansmod","textures/emission-fixture.png"),mode,light,0xA000B0,mirrored);
        check(recorder.buffers==1,label+" uses the same opaque cutout material, no duplicate special phase");
        int index=0;
        for(var ignored:rig.attachmentBody(mode))for(int vertex=0;vertex<4;vertex++)check(recorder.lights.get(index++)==light,label+" body keeps incoming light");
        for(var pass:rig.emissivePasses(mode))for(var ignored:pass.quads())for(int vertex=0;vertex<4;vertex++)
            check(recorder.lights.get(index++)==pass.light().apply(light),label+" emitted vertex has exact original lightmap operation");
        check(index==recorder.vertices && recorder.lights.size()==index,label+" exact vertex count, no missing/duplicate emission");
        check(recorder.overlay==0xA000B0,label+" overlay preserved");
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        Path root=Path.of(args.length==0?"run/flan":args[0]);
        synthetic();production(root);folding(root);
        System.out.println("PASS VerifyLegacyEmission: "+checks+" checks; "+scanned+" original classes / "+glowModels
                +" proven glow render models; exact fields/ranges, byte modes, relative/static light, immutable bake caches and actual held-surface vertex light. No game/GPU/pack writes.");
    }
}
