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

/** Packet, accepted-state matching, emitted meshes and a real supporting-hand gesture; no client. */
public final class VerifySightFoldPlayback {
    static int checks,ids;
    static void check(boolean v,String message){checks++;if(!v)throw new AssertionError(message);}
    static void near(float a,float b,String message){check(Float.isFinite(a)&&Math.abs(a-b)<.00005F,message+" "+a+" != "+b);}
    static void matrix(Matrix4f a,Matrix4f b,String m){float[] aa=a.get(new float[16]),bb=b.get(new float[16]);for(int i=0;i<16;i++)near(aa[i],bb[i],m);}
    static GunSightAnimationPayload packet(int revision,List<GunSightAnimationPayload.Part> parts){return new GunSightAnimationPayload(42,0,"FoldPlayback",0,revision,0,101,parts);}
    static class Recorder implements net.minecraft.client.renderer.MultiBufferSource,VertexConsumer {
        final Set<String> points=new HashSet<>();int vertices;float alpha;
        public VertexConsumer getBuffer(net.minecraft.client.renderer.RenderType type){return this;}
        public VertexConsumer addVertex(float x,float y,float z){check(Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(z),"finite emitted fold vertex");points.add(key(x,y,z));vertices++;return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){alpha=a/255F;check(a>0&&a<=255,"visible fold vertex alpha");return this;}
        public VertexConsumer setUv(float u,float v){check(Float.isFinite(u)&&Float.isFinite(v),"finite fold texture UV");return this;}
        public VertexConsumer setUv1(int u,int v){return this;}
        public VertexConsumer setUv2(int u,int v){return this;}
        public VertexConsumer setNormal(float x,float y,float z){check(Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(z),"finite fold normal");return this;}
    }
    static String key(float x,float y,float z){return Math.round(x*10000)+":"+Math.round(y*10000)+":"+Math.round(z*10000);}
    static Set<String> points(BakedRig rig){var points=new HashSet<String>();for(var q:rig.quadsByPart.get("attachmentModel"))for(var p:q.positions())points.add(key(p[0],p[1],p[2]));return points;}
    static void geometry(GunSightAnimationPayload.Part part) {
        var from=AttachmentType.getAttachment(part.from());var to=AttachmentType.getAttachment(part.to());
        var a=ModelCache.rigFor(from,from.modelScale);var b=ModelCache.rigFor(to,to.modelScale);
        var start=new Recorder();check(SightFoldRenderer.render(new PoseStack(),start,part,0,100,0,false,false),"actual fold renderer handles source");
        check(start.points.equals(points(a)),"emitted start exactly equals actual visible source body");
        var end=new Recorder();SightFoldRenderer.render(new PoseStack(),end,part,1,100,0,false,false);
        if(!end.points.equals(points(b))) {
            var extra=new HashSet<>(end.points);extra.removeAll(points(b));var missing=new HashSet<>(points(b));missing.removeAll(end.points);
            System.out.println(part.from()+" endpoint diff extra="+extra.stream().limit(8).toList()+" missing="+missing.stream().limit(8).toList());
        }
        check(end.points.equals(points(b)),"emitted end equals actual visible target body "+part.from());
        for(float p:new float[]{.1F,.5F,.9F}) {
            var mid=new Recorder();SightFoldRenderer.render(new PoseStack(),mid,part,p,100,0,false,false);
            check(mid.vertices>0&&mid.vertices%4==0,"intermediate body emits complete quads");
            var mirror=new Recorder();SightFoldRenderer.render(new PoseStack(),mirror,part,p,100,0,true,false);
            check(mid.vertices==mirror.vertices && mid.points.equals(mirror.points),"left-hand winding retains intermediate geometry");
        }
    }
    public static void main(String[] args)throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();((net.minecraft.core.MappedRegistry<?>)BuiltInRegistries.ITEM).unfreeze();
        var pack=new OfflinePack(Path.of(args[0]));pack.readTypes();for(var type:pack.types)
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
