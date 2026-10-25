import com.flansmod.client.SightFoldAnimations;
import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import com.flansmod.common.*;
import com.flansmod.common.network.GunSightAnimationPayload;
import com.flansmod.common.types.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.nio.file.Path;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Packet, accepted-state matching, emitted meshes and a real supporting-hand gesture; no client. */
public final class VerifySightFoldPlayback {
    static int checks,ids;
    static void check(boolean v,String message){checks++;if(!v)throw new AssertionError(message);}
    static void near(float a,float b,String message){check(Float.isFinite(a)&&Math.abs(a-b)<.00005F,message+" "+a+" != "+b);}
    static void matrix(Matrix4f a,Matrix4f b,String m){float[] aa=a.get(new float[16]),bb=b.get(new float[16]);for(int i=0;i<16;i++)near(aa[i],bb[i],m);}
    static GunSightAnimationPayload packet(int revision,List<GunSightAnimationPayload.Part> parts){return new GunSightAnimationPayload(42,0,"FoldPlayback",0,revision,0,101,parts);}
    static Path packRoot;
    static class Recorder implements net.minecraft.client.renderer.MultiBufferSource,VertexConsumer {
        final Set<String> points=new HashSet<>();int vertices;float alpha;
        final List<Vector3f> ordered=new ArrayList<>();final List<Integer> lights=new ArrayList<>(),alphas=new ArrayList<>();
        public VertexConsumer getBuffer(net.minecraft.client.renderer.RenderType type){return this;}
        public VertexConsumer addVertex(float x,float y,float z){check(Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(z),"finite emitted fold vertex");points.add(key(x,y,z));ordered.add(new Vector3f(x,y,z));vertices++;return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){alpha=a/255F;alphas.add(a);check(a>0&&a<=255,"visible fold vertex alpha");return this;}
        public VertexConsumer setUv(float u,float v){check(Float.isFinite(u)&&Float.isFinite(v),"finite fold texture UV");return this;}
        public VertexConsumer setUv1(int u,int v){return this;}
        public VertexConsumer setUv2(int u,int v){lights.add(u|v<<16);return this;}
        public VertexConsumer setNormal(float x,float y,float z){check(Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(z),"finite fold normal");return this;}
    }
    static String key(float x,float y,float z){return Math.round(x*10000)+":"+Math.round(y*10000)+":"+Math.round(z*10000);}
    static void addPoints(Set<String> points,List<BakedRig.Quad> quads){for(var q:quads)for(var p:q.positions())points.add(key(p[0],p[1],p[2]));}
    /** Narrow oracle for these original folding classes. It reads the authored draw calls,
     * never attachmentBody/emissivePasses/attachmentVisible or the port's material metadata. */
    static IdentityHashMap<BakedRig.Quad,Integer> authoredSurface(BakedRig rig)throws Exception {
        var cn=new ClassNode();try(var in=java.nio.file.Files.newInputStream(packRoot.resolve(rig.animation.className.replace('.','/')+".class"))){
            new ClassReader(in).accept(cn,ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        }
        var fields=new LinkedHashMap<String,Integer>();MethodNode draw=null;
        for(var method:cn.methods)if(method.name.equals("render")&&method.desc.equals("(F)V"))draw=method;
        if(draw==null){
            check(cn.superName.endsWith("/ModelAtSight")||cn.superName.endsWith("/ModelAttachment"),"known core default render contract");
            fields.put("attachmentModel",100);
        }else{
            String field=null;boolean glow=false;
            for(var instruction:draw.instructions){
                check(!(instruction instanceof JumpInsnNode),"oracle requires the actual folding class's straight-line render");
                if(instruction instanceof FieldInsnNode get&&get.getOpcode()==Opcodes.GETFIELD&&get.desc.contains("ModelRendererTurbo"))field=get.name;
                if(instruction instanceof MethodInsnNode call){
                    if(call.name.equals("glowOn")){check(call.desc.equals("()V"),"authored default core glow strength is 15");glow=true;}
                    else if(call.name.equals("glowOff"))glow=false;
                    else if(call.name.equals("render")&&call.desc.startsWith("([Lcom/flansmod/client/tmt/ModelRendererTurbo;")){
                        check(field!=null,"authored array draw has an explicit field");check(!fields.containsKey(field),"authored folding endpoint draws each array once");
                        fields.put(field,glow?0xF000F0:100);field=null;
                    }else throw new AssertionError("Extend independent oracle for original call "+call.name+call.desc);
                }
            }
            check(!glow,"authored source restores its lightmap");
        }
        var result=new IdentityHashMap<BakedRig.Quad,Integer>();
        for(var field:fields.entrySet()){
            var quads=rig.quadsByPart.get(field.getKey());check(quads!=null&&!quads.isEmpty(),"authored field has actual original geometry "+field.getKey());
            for(var quad:quads)check(result.put(quad,field.getValue())==null,"original surface identity appears once");
        }
        return result;
    }
    static String vertexKey(Vector3f point,int light,int alpha){return key(point.x,point.y,point.z)+"/"+light+"/"+alpha;}
    static Map<String,Integer> actualVertices(Recorder recorder){
        check(recorder.vertices==recorder.lights.size()&&recorder.vertices==recorder.alphas.size(),"every actual vertex carries light and alpha");
        var result=new HashMap<String,Integer>();for(int i=0;i<recorder.vertices;i++)result.merge(vertexKey(recorder.ordered.get(i),recorder.lights.get(i),recorder.alphas.get(i)),1,Integer::sum);return result;
    }
    static void expectedVertices(Map<String,Integer> result,List<BakedRig.Quad> quads,Matrix4f pose,int light,float alpha){
        if(alpha<=0)return;for(var quad:quads)for(var point:quad.positions())
            result.merge(vertexKey(pose.transformPosition(new Vector3f(point)),light,(int)(alpha*255)),1,Integer::sum);
    }
    static Map<String,Integer> endpointVertices(IdentityHashMap<BakedRig.Quad,Integer> surface){
        var result=new HashMap<String,Integer>();surface.forEach((quad,light)->expectedVertices(result,List.of(quad),new Matrix4f(),light,1));return result;
    }
    static boolean uvDifferent(List<BakedRig.Quad> a,List<BakedRig.Quad> b){
        if(a.size()!=b.size())return true;for(int q=0;q<a.size();q++)for(int i=0;i<4;i++)for(int axis=0;axis<2;axis++)
            if(Math.abs(a.get(q).uvs()[i][axis]-b.get(q).uvs()[i][axis])>2E-6F)return true;return false;
    }
    static void geometry(GunSightAnimationPayload.Part part)throws Exception {
        var from=AttachmentType.getAttachment(part.from());var to=AttachmentType.getAttachment(part.to());
        var a=ModelCache.rigFor(from,from.modelScale);var b=ModelCache.rigFor(to,to.modelScale);
        var start=new Recorder();check(SightFoldRenderer.render(new PoseStack(),start,part,0,100,0,false,false),"actual fold renderer handles source");
        var sourceSurface=authoredSurface(a);var targetSurface=authoredSurface(b);
        check(actualVertices(start).equals(endpointVertices(sourceSurface)),"source equals independently authored body+light vertices/light/alpha exactly once "+part.from());
        var end=new Recorder();SightFoldRenderer.render(new PoseStack(),end,part,1,100,0,false,false);
        check(actualVertices(end).equals(endpointVertices(targetSurface)),"target equals independently authored body+light vertices/light/alpha exactly once "+part.from());
        check(start.vertices==sourceSurface.size()*4&&end.vertices==targetSurface.size()*4,"both endpoints conserve original visible face multiplicity");
        for(float p:new float[]{.1F,.5F,.9F}) {
            var mid=new Recorder();SightFoldRenderer.render(new PoseStack(),mid,part,p,100,0,false,false);
            check(mid.vertices>0&&mid.vertices%4==0,"intermediate body emits complete quads");
            var expected=new HashMap<String,Integer>();
            for(var track:SightFoldGeometry.plan(a,b).tracks()){
                Integer source=track.source().isEmpty()?null:sourceSurface.get(track.source().getFirst());
                Integer target=track.target().isEmpty()?null:targetSurface.get(track.target().getFirst());
                if(track.matched()){
                    Matrix4f rigid=track.pose(p);near(rigid.determinant3x3(),1,"intermediate motion is a proper rigid rotation");
                    var positions=track.source().getFirst().positions();
                    for(int i=1;i<4;i++)near(rigid.transformPosition(new Vector3f(positions[0])).distance(rigid.transformPosition(new Vector3f(positions[i]))),
                            new Vector3f(positions[0]).distance(new Vector3f(positions[i])),"rigid playback keeps each source face edge length");
                }
                if(track.matched()&&source!=null&&target!=null){
                    expectedVertices(expected,track.source(),track.pose(p),source,1);
                    if(uvDifferent(track.source(),track.target())||!Objects.equals(from.texture,to.texture)||!source.equals(target))
                        expectedVertices(expected,track.source(),track.pose(p),target,p);
                }else{
                    if(source!=null)expectedVertices(expected,track.source(),track.pose(p),source,1-p);
                    if(target!=null)expectedVertices(expected,track.target(),track.targetPose(p),target,p);
                }
            }
            check(actualVertices(mid).equals(expected),"intermediate conserves authored visible faces under rigid poses, including UV/material crossfade "+part.from()+" phase="+p);
            var mirror=new Recorder();SightFoldRenderer.render(new PoseStack(),mirror,part,p,100,0,true,false);
            check(actualVertices(mid).equals(actualVertices(mirror)),"left-hand winding retains full intermediate vertex/light/alpha multiplicity");
        }
    }
    public static void main(String[] args)throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();((net.minecraft.core.MappedRegistry<?>)BuiltInRegistries.ITEM).unfreeze();
        packRoot=Path.of(args[0]);var pack=new OfflinePack(packRoot);pack.readTypes();for(var type:pack.types)
            Registry.register(BuiltInRegistries.ITEM,ResourceLocation.fromNamespaceAndPath("flansmod","fold_playback_"+ids++),new ItemFlan(type,new Item.Properties()));
        var f=new TypeFile(EnumType.gun,"FoldPlayback",false);f.lines.addAll(List.of("ShortName FoldPlayback","AttachPoints 0 0 0","MaxAttachments 1"));
        var gun=new GunType(f);gun.read(f);Registry.register(BuiltInRegistries.ITEM,ResourceLocation.fromNamespaceAndPath("flansmod","fold_gun"),new ItemFlan(gun,new Item.Properties()));
        var n=new ModifiableType.DefaultAttachment();n.name="G33Enabled";n.slot=0;gun.defaultAttachments=List.of(List.of(n));
        var stack=new ItemStack(gun.item);var before=GunAttachments.view(gun,stack);
        var anticipated=packet(1,List.of(new GunSightAnimationPayload.Part(new int[]{0,0},"G33Enabled","G33Disabled")));
        check(SightFoldAnimations.matches(anticipated,stack),"animation accepts equipment immediately before authoritative update");
        check(GunSightState.apply(stack,gun,GunSightState.TOGGLE,RegistryAccess.EMPTY),"real G33 server operation toggles");
        var after=GunAttachments.view(gun,stack);var changes=GunSightState.foldChanges(before,after);
        check(changes.size()==1&&changes.getFirst().from().equals("G33Enabled")&&changes.getFirst().to().equals("G33Disabled"),"server emits actual changed path and direction");
        var event=packet(GunAttachments.sightRevision(stack),changes);
        check(SightFoldAnimations.matches(event,stack),"animation accepts equipment immediately after authoritative update");
        check(!SightFoldAnimations.matches(packet(8,changes),stack),"stale/future unrelated optical revision cannot animate gun");
        check(!SightFoldAnimations.matches(packet(1,List.of(new GunSightAnimationPayload.Part(new int[]{0,2},"G33Enabled","G33Disabled"))),stack),"absent path cannot animate");
        check(!SightFoldAnimations.matches(packet(1,List.of(new GunSightAnimationPayload.Part(new int[]{0,0},"G33Enabled","PSO1"))),stack),"undeclared model transition cannot animate");
        var buffer=new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            GunSightAnimationPayload.STREAM_CODEC.encode(buffer,event);var decoded=GunSightAnimationPayload.STREAM_CODEC.decode(buffer);
            check(decoded.entity()==42&&decoded.slot()==0&&decoded.fromGroup()==0&&decoded.sightRevision()==1&&decoded.sequence()==101&&decoded.parts().size()==1,"wire fields roundtrip");
            check(SightFoldAnimations.sameEvent(event,decoded),"duplicate accepted event is recognized");
            check(!SightFoldAnimations.sameEvent(event,new GunSightAnimationPayload(42,0,"FoldPlayback",0,1,0,102,changes)),"a different same-type gun can fold with the same item revisions");
            check(Arrays.equals(decoded.parts().getFirst().path(),new int[]{0,0})&&decoded.parts().getFirst().to().equals("G33Disabled"),"wire track direction/path roundtrip");
            for(int count:new int[]{-1,GunAttachments.MAX_NODES+1}) {
                buffer.clear();buffer.writeVarInt(42);buffer.writeVarInt(0);buffer.writeUtf("FoldPlayback");
                buffer.writeVarInt(0);buffer.writeVarInt(1);buffer.writeVarInt(0);buffer.writeVarLong(101);buffer.writeVarInt(count);
                boolean rejected=false;try{GunSightAnimationPayload.STREAM_CODEC.decode(buffer);}catch(io.netty.handler.codec.DecoderException expected){rejected=true;}
                check(rejected,"bounded wire part count");
            }
        } finally {buffer.release();}
        GunAttachments.write(stack,after.defaultAttachments);check(!SightFoldAnimations.matches(event,stack),"structural edit cancels visual transition");
        near(SightFoldPose.turn(0),0,"no turn before hand reaches");near(SightFoldPose.turn(.2F),0,"contact precedes turn");near(SightFoldPose.turn(.72F),1,"turn completes before hand release");
        near(SightFoldPose.reach(0),0,"holding at start");near(SightFoldPose.reach(1),0,"holding at end");near(SightFoldPose.reach(.45F),1,"hand operates at contact");
        matrix(SightFoldPose.weapon(new Matrix4f(),0),new Matrix4f(),"weapon start has no offset");
        matrix(SightFoldPose.weapon(new Matrix4f(),1),new Matrix4f(),"weapon returns without residual offset");
        check(SightFoldPose.weapon(new Matrix4f(),.4F).getTranslation(new Vector3f()).x>.4F,"weapon moves away from eye before hand operates");
        var holding=new Matrix4f().translate(.3F,-.2F,0).rotateY(.2F).scale(.6F,.7F,.8F);var contact=new Vector3f(.1F,.25F,.1F);
        matrix(SightFoldPose.hand(holding,contact,0,true),holding,"exact holding at initial frame");
        matrix(SightFoldPose.hand(holding,contact,1,true),holding,"exact holding after return");
        var hand=SightFoldPose.hand(holding,contact,.45F,true);near(hand.getTranslation(new Vector3f()).distance(contact),0,"operating hand reaches actual contact");
        near(hand.determinant3x3(),holding.determinant3x3(),"gesture keeps hand dimensions");
        var forearm=hand.transformDirection(new Vector3f(-1,0,0)).normalize();
        check(forearm.x<0 && forearm.y<-.5F,"forearm points back/down away from optic and eye");
        var nested=new TypeFile(EnumType.attachment,"FoldMount",false);nested.lines.addAll(List.of("ShortName FoldMount","AttachPoints 16 32 48","AttachRotates 90"));
        var mountType=new AttachmentType(nested);mountType.read(nested);
        var parent=new ModifiableType.DefaultAttachment();parent.name="FoldMount";parent.slot=0;parent.children.add(n);
        gun.defaultAttachments=List.of(List.of(parent));
        var position=SightFoldPose.mount(gun,new int[]{0,0,0,0});check(position!=null,"nested mount resolves");
        matrix(position,new Matrix4f().translate(1,2,3).rotateX((float)Math.PI/2),"nested rolled mount follows real slot");
        for(String[] pair:new String[][]{{"G33Enabled","G33Disabled"},{"G33Disabled","G33Enabled"},{"MP7Frontsight","MP7FrontsightFolded"},{"QBZ191RearSight","QBZ191RearSightFolded"}})
            geometry(new GunSightAnimationPayload.Part(new int[]{0,0},pair[0],pair[1]));
        System.out.println("PASS: "+checks+" assertions; accepted event codec/state bounds, real rendered endpoints and mirrored playback, reach/turn/return and nested mounts. Offline only; no Minecraft client started.");
    }
}
