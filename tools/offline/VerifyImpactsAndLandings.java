import com.flansmod.client.model.*;
import com.flansmod.common.*;
import com.flansmod.common.entity.*;
import com.flansmod.common.network.*;
import com.flansmod.common.types.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.*;
import org.joml.Matrix4f;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import io.netty.buffer.Unpooled;
import java.nio.file.*;
import java.util.*;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.List;

/** Real native collision shapes/traversal and final-artifact geometry, codecs and landing physics. */
public final class VerifyImpactsAndLandings {
    static int checks,triangles,landings;
    static net.minecraft.world.entity.Entity projectile;
    static void check(boolean value,String detail){checks++;if(!value)throw new AssertionError(detail);}
    static void near(double a,double b,String detail){check(Double.isFinite(a)&&Math.abs(a-b)<.00001,detail+": "+a+" != "+b);}
    static class Grid implements BlockGetter {
        final Map<BlockPos,BlockState> blocks=new HashMap<>();
        Grid put(int x,int y,int z,Block b){blocks.put(new BlockPos(x,y,z),b.defaultBlockState());return this;}
        Grid put(int x,int y,int z,BlockState s){blocks.put(new BlockPos(x,y,z),s);return this;}
        public BlockState getBlockState(BlockPos p){return blocks.getOrDefault(p,Blocks.AIR.defaultBlockState());}
        public FluidState getFluidState(BlockPos p){return getBlockState(p).getFluidState();}
        public BlockEntity getBlockEntity(BlockPos p){return null;}
        public int getHeight(){return 384;}public int getMinBuildHeight(){return -64;}
    }
    static BlockImpactPayload packet(BlockPos block,Vec3 local,Direction face,long seed){return new BlockImpactPayload(block,local.add(block.getX(),block.getY(),block.getZ()),face,Block.getId(Blocks.STONE.defaultBlockState()),seed);}
    static Vec3 facePoint(Direction face){double normal=face.getAxisDirection()==Direction.AxisDirection.POSITIVE?1:0;return switch(face.getAxis()){case X->new Vec3(normal,.5,.5);case Y->new Vec3(.5,normal,.5);case Z->new Vec3(.5,.5,normal);};}
    static List<ImpactMark.Vertex> mesh(Direction face,long seed){return ImpactMark.mesh(packet(BlockPos.ZERO,facePoint(face),face,seed),List.of(new AABB(0,0,0,1,1,1)));}
    static void geometry()throws Exception {
        for(var face:Direction.values())for(int seed=0;seed<24;seed++) {
            var vertices=mesh(face,seed);check(!vertices.isEmpty()&&vertices.size()%3==0,"triangulated face "+face);triangles+=vertices.size()/3;
            for(var v:vertices){
                check(Double.isFinite(v.x())&&Double.isFinite(v.y())&&Double.isFinite(v.z()),"finite mark vertex");
                check(v.shade()>=0&&v.shade()<=255&&v.opacity()>0&&v.opacity()<=1,"valid vertex colour");
                double normal=switch(face.getAxis()){case X->v.x();case Y->v.y();case Z->v.z();};
                near(normal,(face.getAxisDirection()==Direction.AxisDirection.POSITIVE?1:0)+face.getAxisDirection().getStep()*ImpactMark.OFFSET,"outward surface offset");
                if(face.getAxis()!=Direction.Axis.X)check(v.x()>=0&&v.x()<=1,"X within surface");
                if(face.getAxis()!=Direction.Axis.Y)check(v.y()>=0&&v.y()<=1,"Y within surface");
                if(face.getAxis()!=Direction.Axis.Z)check(v.z()>=0&&v.z()<=1,"Z within surface");
            }
        }
        check(mesh(Direction.NORTH,42).equals(mesh(Direction.NORTH,42)),"shared seed reproduces mesh");
        check(!mesh(Direction.NORTH,42).equals(mesh(Direction.NORTH,43)),"different shots vary mark");
        var slab=Blocks.STONE_SLAB.defaultBlockState().getCollisionShape(new Grid(),BlockPos.ZERO).toAabbs();
        var edge=ImpactMark.mesh(packet(BlockPos.ZERO,new Vec3(.002,.499,0),Direction.NORTH,91),slab);
        check(!edge.isEmpty(),"slab corner remains visible");
        for(var v:edge)check(v.x()>=-1E-10&&v.y()>=-1E-10&&v.y()<=.5+1E-10,"slab edge clipping leaves no floating overhang");
        var thin=ImpactMark.mesh(packet(BlockPos.ZERO,new Vec3(.55,.001,.001),Direction.EAST,12),List.of(new AABB(.45,0,0,.55,1,1)));
        check(!thin.isEmpty(),"thin face mark");for(var v:thin){near(v.x(),.55+ImpactMark.OFFSET,"thin face plane");check(v.y()>=-1E-10&&v.z()>=-1E-10,"thin corner clipping");}
        check(ImpactMark.mesh(packet(BlockPos.ZERO,new Vec3(.5,.3,.5),Direction.UP,0),slab).isEmpty(),"not projected onto a different height surface");
        check(ImpactMark.mesh(packet(BlockPos.ZERO,new Vec3(.5,.5,.5),Direction.UP,0),List.of()).isEmpty(),"no mark on empty collision");
        near(ImpactMark.alpha(0,0),1,"full opacity at birth");near(ImpactMark.alpha(500,0),1,"retains opacity until fade");near(ImpactMark.alpha(550,0),.5,"late fade");near(ImpactMark.alpha(600,0),0,"expired mark transparent");
        var far=packet(new BlockPos(20000000,64,-20000000),new Vec3(.5,.5,0),Direction.NORTH,17);
        check(ImpactMark.mesh(far,List.of(new AABB(0,0,0,1,1,1))).equals(mesh(Direction.NORTH,17)),"far-world local mesh preserves precision");
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try{BlockImpactPayload.STREAM_CODEC.encode(buffer,far);check(far.equals(BlockImpactPayload.STREAM_CODEC.decode(buffer))&&!buffer.isReadable(),"exact point/face/state/seed codec");}finally{buffer.release();}
        check(!new BlockImpactPayload(BlockPos.ZERO,new Vec3(Double.NaN,.5,0),Direction.NORTH,1,0).valid(),"invalid point rejected");
        check(!new BlockImpactPayload(BlockPos.ZERO,new Vec3(10,.5,0),Direction.NORTH,1,0).valid(),"point outside block rejected");
        check(!new BlockImpactPayload(BlockPos.ZERO,new Vec3(.5,.5,0),Direction.NORTH,-1,0).valid(),"invalid state rejected");
    }
    static void passed()throws Exception {
        ((MappedRegistry<?>)BuiltInRegistries.REGISTRY).unfreeze();
        Registry.register((Registry)BuiltInRegistries.REGISTRY,net.neoforged.neoforge.registries.NeoForgeRegistries.FLUID_TYPES.key().location(),net.neoforged.neoforge.registries.NeoForgeRegistries.FLUID_TYPES);
        Registry.register(net.neoforged.neoforge.registries.NeoForgeRegistries.FLUID_TYPES,"minecraft:empty",new net.neoforged.neoforge.fluids.FluidType(net.neoforged.neoforge.fluids.FluidType.Properties.create()));
        projectile=new net.minecraft.world.entity.projectile.Snowball(net.minecraft.world.entity.EntityType.SNOWBALL,null);
        var bindings=new HashMap<net.minecraft.tags.TagKey<Block>,List<Holder<Block>>>();
        bindings.put(BulletBlockTrace.PASS_THROUGH,List.of(Blocks.OAK_PLANKS.builtInRegistryHolder(),Blocks.WHITE_WOOL.builtInRegistryHolder()));
        try(var zip=new java.util.zip.ZipFile("build/moddev/artifacts/neoforge-21.1.250-client-extra-aka-minecraft-resources.jar")) {
            for(var key:BuiltInRegistries.BLOCK.getTagNames().filter(t->!t.equals(BulletBlockTrace.PASS_THROUGH)).toList()) {
                var id=key.location();var entry=zip.getEntry("data/"+id.getNamespace()+"/tags/block/"+id.getPath()+".json");
                try(var reader=new java.io.InputStreamReader(zip.getInputStream(entry),java.nio.charset.StandardCharsets.UTF_8)) {
                    var holders=new ArrayList<Holder<Block>>();for(var block:VerifyBulletBlocks.expand(com.google.gson.JsonParser.parseReader(reader).getAsJsonObject(),zip,new HashSet<>()))holders.add(BuiltInRegistries.BLOCK.getHolder(block).orElseThrow());bindings.put(key,holders);
                }
            }
        }
        BuiltInRegistries.BLOCK.bindTags(bindings);
        var world=new Grid().put(1,0,0,Blocks.OAK_PLANKS).put(2,0,0,Blocks.WHITE_WOOL).put(3,0,0,Blocks.STONE).put(4,0,0,Blocks.OAK_PLANKS);
        var from=new Vec3(.1,.5,.5);var end=new Vec3(8,.5,.5);
        var stop=BulletBlockTrace.clip(world,from,end,projectile);check(stop.getBlockPos().equals(new BlockPos(3,0,0)),"hard wall still bounds travel");
        var hits=BulletImpacts.passedBlocks(world,from,stop.getLocation(),projectile);
        check(hits.size()==2,"two penetrated entry surfaces");
        check(hits.getFirst().getBlockPos().equals(new BlockPos(1,0,0))&&hits.getLast().getBlockPos().equals(new BlockPos(2,0,0)),"no effects behind hard cover");
        check(hits.stream().allMatch(h->h.getDirection()==Direction.WEST),"actual entry faces from native shape ray");
        check(BulletImpacts.passedBlocks(world,from,new Vec3(.8,.5,.5),projectile).isEmpty(),"no cover effects beyond nearer target");
        var split=BulletImpacts.passedBlocks(world,new Vec3(1.5,.5,.5),new Vec3(2.8,.5,.5),projectile);
        check(split.size()==1&&split.getFirst().getBlockPos().equals(new BlockPos(2,0,0)),"starting inside previous cover does not repeat its entry");
        var thick=new Grid();for(int i=1;i<=40;i++)thick.put(i,0,0,Blocks.OAK_PLANKS);
        check(BulletImpacts.passedBlocks(thick,from,new Vec3(41,.5,.5),projectile).size()==16,"bounded cosmetic events for extreme paths");
        check(BulletImpacts.passedBlocks(world,from,from,projectile).isEmpty(),"zero travel emits nothing");
        check(world.blocks.size()==4&&world.getBlockState(new BlockPos(1,0,0)).is(Blocks.OAK_PLANKS),"cosmetics never change blocks");
    }
    @SuppressWarnings("unchecked")
    static void bindWaterTag() {
        var fluids=(net.minecraft.core.MappedRegistry<net.minecraft.world.level.material.Fluid>)BuiltInRegistries.FLUID;
        ((net.minecraft.core.MappedRegistry<?>)BuiltInRegistries.REGISTRY).unfreeze();
        fluids.unfreeze();
        fluids.bindTags(java.util.Map.of(net.minecraft.tags.FluidTags.WATER,java.util.List.of(
                fluids.wrapAsHolder(net.minecraft.world.level.material.Fluids.WATER),
                fluids.wrapAsHolder(net.minecraft.world.level.material.Fluids.FLOWING_WATER))));
    }
    static void casings()throws Exception {
        // FMUM had no casing audio; the water line is judgement, so it gets its own cases.
        bindWaterTag();
        var pond=new Grid();for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){pond.put(x,0,z,Blocks.STONE);pond.put(x,1,z,Blocks.WATER);}
        check(FmumCasing.submerged(pond,new Vec3(.5,1.0,.5)),"contact at a pond bottom is under water");
        var deep=new Grid();for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){deep.put(x,0,z,Blocks.STONE);deep.put(x,1,z,Blocks.WATER);deep.put(x,2,z,Blocks.WATER);}
        check(FmumCasing.submerged(deep,new Vec3(.5,1.0,.5)),"deep water still mutes the floor contact");
        check(!FmumCasing.submerged(new Grid().put(0,0,0,Blocks.STONE),new Vec3(.5,1.0,.5)),"dry floor contact still rings");
        var wetSlab=new Grid().put(0,0,0,Blocks.OAK_SLAB.defaultBlockState()
                .setValue(net.minecraft.world.level.block.SlabBlock.TYPE,net.minecraft.world.level.block.state.properties.SlabType.BOTTOM)
                .setValue(net.minecraft.world.level.block.SlabBlock.WATERLOGGED,true));
        check(FmumCasing.submerged(wetSlab,new Vec3(.5,.5,.5)),"waterlogged bottom slab is under the water line");
        var topSlab=new Grid().put(0,0,0,Blocks.OAK_SLAB.defaultBlockState()
                .setValue(net.minecraft.world.level.block.SlabBlock.TYPE,net.minecraft.world.level.block.state.properties.SlabType.TOP)
                .setValue(net.minecraft.world.level.block.SlabBlock.WATERLOGGED,true));
        check(!FmumCasing.submerged(topSlab,new Vec3(.5,1.0,.5)),"waterlogged top slab clears the water line");
        var wetFence=new Grid();
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)wetFence.put(x,0,z,Blocks.OAK_FENCE.defaultBlockState()
                .setValue(net.minecraft.world.level.block.FenceBlock.WATERLOGGED,true));
        check(!FmumCasing.submerged(wetFence,new Vec3(.5,1.5,.5)),"waterlogged fence top still rings");
        var type=new BulletType(new TypeFile(EnumType.bullet,"landingFixture",false));type.bulletScale=.044F;type.isCase=true;
        var air=new FmumCasing(type,null,new Matrix4f(),false,7,0);for(int i=0;i<25;i++){air.tick(null,0,2,0);check(air.takeLanding()==null,"airborne has no impact sound event");}
        var floor=new Grid();for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)floor.put(x,0,z,Blocks.STONE);
        var casing=new FmumCasing(type,null,new Matrix4f(),false,8,0);casing.velocity.set(.01F,-.12F,.01F);
        casing.tick(floor,0,1.08,0);var first=casing.takeLanding();check(first!=null&&casing.grounded,"actual native floor collision emits landing");
        check(first.surface().is(Blocks.STONE)&&first.speed()>.04,"surface and incoming speed captured");
        check(casing.takeLanding()==null,"event consumed once");check(casing.audibleLanding(first),"first real landing audible");check(!casing.audibleLanding(first),"same contact cannot repeat sound");
        int sounds=1,contacts=1;for(int i=0;i<90;i++){casing.tick(floor,0,1.08,0);var hit=casing.takeLanding();if(hit!=null){contacts++;if(casing.audibleLanding(hit))sounds++;}}
        check(contacts>1,"fixture includes subsequent terrain contacts");check(sounds<=3,"bounce sounds bounded");check(casing.grounded,"case ends grounded");landings=contacts;
        var tiny=new FmumCasing.Landing(BlockPos.ZERO,Vec3.ZERO,Blocks.STONE.defaultBlockState(),.01F);
        casing.age+=10;check(!casing.audibleLanding(tiny),"resting gravity/small bounce is silent");
        var capped=new FmumCasing(type,null,new Matrix4f(),false,9,0);
        for(int i=0;i<4;i++){capped.age=i*10;check(capped.audibleLanding(first)==(i<3),"at most three audible landings");}
        var wall=new Grid().put(1,1,0,Blocks.STONE);var side=new FmumCasing(type,null,new Matrix4f(),false,10,0);side.velocity.set(.2F,0,0);
        side.tick(wall,.92,1.5,.5);check(side.velocity.x<0&&side.takeLanding()==null,"wall bounce is not a floor landing");
        var carpet=new Grid().put(0,0,0,Blocks.WHITE_CARPET);var fast=new FmumCasing(type,null,new Matrix4f(),false,11,0);fast.velocity.set(0,-1,0);
        fast.tick(carpet,.5,.9,.5);var thinHit=fast.takeLanding();
        check(thinHit!=null&&thinHit.surface().is(Blocks.WHITE_CARPET),"fast falling case cannot skip a thin landing surface");
        check(.9+fast.position.y>=.0625,"fast case remains above native carpet surface");
        var sound=ContentPackIndex.current().find("assets/flansmod/sounds/casing1.ogg",true).orElseThrow();var bytes=sound.readAllBytes();
        check(bytes.length>64&&bytes[0]=='O'&&bytes[1]=='g'&&bytes[2]=='g'&&bytes[3]=='S',"original casing1 Ogg available");
        near(FmumSounds.event("casing1",10).getRange(.5F),10,"original pack sound uses bounded hearing range");
    }
    static ClassNode node(String name)throws Exception {var n=new ClassNode();try(var in=VerifyImpactsAndLandings.class.getResourceAsStream("/"+name+".class")){check(in!=null,"final JAR class exists");new ClassReader(in).accept(n,0);}return n;}
    static boolean hasImpactProtocolVersion(Object value){return value instanceof String version
            &&version.matches("\\d+")&&Integer.parseInt(version)>=16;}
    static boolean calls(ClassNode n,String method,String owner,String called){for(var m:n.methods)if(m.name.equals(method))for(var i:m.instructions)if(i instanceof MethodInsnNode c&&c.owner.endsWith(owner)&&c.name.equals(called))return true;return false;}
    static void wiring()throws Exception {
        var bullet=node("com/flansmod/common/entity/EntityBullet");
        check(calls(bullet,"hitBlock","BulletImpacts","send"),"server solid-hit notification connected");
        check(calls(bullet,"tick","BulletImpacts","passedBlocks"),"penetration effects use bounded actual travel");
        var effects=node("com/flansmod/client/render/MuzzleEffectsRenderer");
        check(calls(effects,"tick","FmumCasing","takeLanding")&&calls(effects,"tick","CasingLandingSounds","play"),"audio driven by physics tick");
        check(!calls(effects,"render","CasingLandingSounds","play")&&!calls(effects,"renderCases","CasingLandingSounds","play"),"render frames do not duplicate landing sound");
        check(calls(effects,"tick","Minecraft","isPaused"),"paused physics does not advance sounds");
        var renderer=node("com/flansmod/client/render/BulletImpactRenderer");check(calls(renderer,"render","FmumWorldEffectsPose","worldStage"),"world view applied once");
        check(calls(renderer,"render","MultiBufferSource$BufferSource","endBatch"),"only decal render batch flushed");
        var network=node("com/flansmod/common/network/FlanNetwork");
        // Later features may legitimately bump the protocol again. Keep the
        // impact behaviour's compatibility floor rather than pinning its first version.
        check(!hasImpactProtocolVersion("15"),"protocol before the impact compatibility floor is rejected");
        check(hasImpactProtocolVersion("16"),"first impact-compatible protocol passes the feature floor");
        check(hasImpactProtocolVersion("19"),"later protocol passes the impact feature floor");
        check(network.fields.stream().anyMatch(f->f.name.equals("VERSION")&&hasImpactProtocolVersion(f.value)),
                "impact behaviour requires protocol 16 or newer");
        check(calls(node("com/flansmod/client/render/CasingLandingSounds"),"play","FmumCasing","submerged"),"water line decided before the landing-sound quota");
    }
    static void preview(Path output)throws Exception {
        var image=new BufferedImage(1120,480,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();g.setColor(new Color(16,25,34));g.fillRect(0,0,1120,480);
        g.setFont(new Font("SansSerif",Font.BOLD,22));g.setColor(Color.WHITE);g.drawString("Actual impact mesh / CPU surface preview",24,34);
        for(int column=0;column<4;column++){
            int x=24+column*276,y=72;g.setColor(new Color(105,110,116));g.fillRect(x,y,244,330);
            g.setColor(new Color(88,94,100));for(int row=0;row<7;row++){g.drawLine(x,y+row*50,x+244,y+row*50);for(int v=0;v<4;v++)g.drawLine(x+v*80+(row%2)*40,y+row*50,x+v*80+(row%2)*40,y+(row+1)*50);}
            var hit=packet(BlockPos.ZERO,column==3?new Vec3(.002,.499,0):facePoint(Direction.NORTH),Direction.NORTH,5+column*23);
            var boxes=column==3?List.of(new AABB(0,0,0,1,.5,1)):List.of(new AABB(0,0,0,1,1,1));
            var vertices=ImpactMark.mesh(hit,boxes);double originX=hit.point().x,originY=hit.point().y;double scale=column==3?1800:1350;
            for(int i=0;i<vertices.size();i+=3){var p=new Path2D.Double();for(int j=0;j<3;j++){var v=vertices.get(i+j);double px=x+122+(v.x()-originX)*scale,py=y+165-(v.y()-originY)*scale;if(j==0)p.moveTo(px,py);else p.lineTo(px,py);}p.closePath();var v=vertices.get(i);g.setColor(new Color(v.shade(),v.shade(),v.shade(),(int)(255*v.opacity())));g.fill(p);}
            g.setFont(new Font("SansSerif",Font.PLAIN,15));g.setColor(new Color(202,221,227));g.drawString(column==3?"Slab corner / clipped surface":"Seed "+(5+column*23),x,436);
        }
        g.dispose();Files.createDirectories(output.toAbsolutePath().getParent());ImageIO.write(image,"PNG",output.toFile());
    }
    public static void main(String[] args)throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();new OfflinePack(Path.of(args[0])).readTypes();
        geometry();passed();casings();wiring();preview(Path.of(args[1]));
        System.out.println("PASS VerifyImpactsAndLandings: "+checks+" assertions, "+triangles+" triangles, "+landings+" real floor contacts; six faces, clipping, far-world precision, codec, soft-cover entries, landing audio gates and final-JAR wiring. No game/GPU/input.");
    }
}
