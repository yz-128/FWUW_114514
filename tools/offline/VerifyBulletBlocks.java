import com.flansmod.common.entity.BulletBlockTrace;
import com.flansmod.common.entity.SoftBlockBallistics;
import com.flansmod.common.types.*;
import com.google.gson.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;

/** Native block shapes and native voxel traversal; only the world's block storage is a fixture. */
public final class VerifyBulletBlocks {
 static int sweeps;
 static int damageSamples;
 static Entity projectile;
 static void check(boolean value,String detail){if(!value)throw new AssertionError(detail);}
 static final class Grid implements BlockGetter {
  final Map<BlockPos,BlockState> blocks=new HashMap<>();
  Grid put(int x,int y,int z,Block block){blocks.put(new BlockPos(x,y,z),block.defaultBlockState());return this;}
  public BlockState getBlockState(BlockPos pos){return blocks.getOrDefault(pos,Blocks.AIR.defaultBlockState());}
  public FluidState getFluidState(BlockPos pos){return getBlockState(pos).getFluidState();}
  public BlockEntity getBlockEntity(BlockPos pos){return null;}
  public int getHeight(){return 384;}
  public int getMinBuildHeight(){return -64;}
 }
 static Set<ResourceLocation> expand(JsonObject tag,ZipFile vanilla,Set<String> visiting)throws Exception {
  var result=new LinkedHashSet<ResourceLocation>();
  for(var entry:tag.getAsJsonArray("values")) {
   String id=entry.isJsonPrimitive()?entry.getAsString():entry.getAsJsonObject().get("id").getAsString();
   if(id.startsWith("#")) {
    check(visiting.add(id),"no recursive tag cycle: "+id);
    var key=ResourceLocation.parse(id.substring(1));
    var resource=vanilla.getEntry("data/"+key.getNamespace()+"/tags/block/"+key.getPath()+".json");
    check(resource!=null,"tag exists in actual 1.21.1 resources: "+id);
    try(var reader=new java.io.InputStreamReader(vanilla.getInputStream(resource),java.nio.charset.StandardCharsets.UTF_8)){
     result.addAll(expand(JsonParser.parseReader(reader).getAsJsonObject(),vanilla,visiting));
    }
    visiting.remove(id);
   } else result.add(ResourceLocation.parse(id));
  }
  return result;
 }
 static BlockHitResult trace(Grid world,Vec3 start,Vec3 end){sweeps++;projectile.setPos(start);return BulletBlockTrace.clip(world,start,end,projectile);}
 static void hit(Grid world,Vec3 start,Vec3 end,BlockPos expected,String detail) {
  var result=trace(world,start,end);
  check(result.getType()==HitResult.Type.BLOCK && result.getBlockPos().equals(expected),detail+": "+result.getType()+" "+result.getBlockPos());
 }
 static void miss(Grid world,Vec3 start,Vec3 end,String detail){check(trace(world,start,end).getType()==HitResult.Type.MISS,detail);}
 static float damage(Grid world,Vec3 from,Vec3 to,BulletType ammo,float initial) {
  damageSamples++;projectile.setPos(from);
  return SoftBlockBallistics.remainingDamage(world,from,to,projectile,ammo,initial);
 }
 static void close(float a,float b,String detail){check(Math.abs(a-b)<.00003F,detail+": "+a+" vs "+b);}
 public static void main(String[] args)throws Exception {
  SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
  // The isolated bootstrap does not dispatch NeoForge registry events. Supply its empty
  // fluid holder so a native projectile can provide the real CollisionContext.
  ((MappedRegistry<?>)BuiltInRegistries.REGISTRY).unfreeze();
  Registry.register((Registry)BuiltInRegistries.REGISTRY,net.neoforged.neoforge.registries.NeoForgeRegistries.FLUID_TYPES.key().location(),
    net.neoforged.neoforge.registries.NeoForgeRegistries.FLUID_TYPES);
  Registry.register(net.neoforged.neoforge.registries.NeoForgeRegistries.FLUID_TYPES,"minecraft:empty",
    new net.neoforged.neoforge.fluids.FluidType(net.neoforged.neoforge.fluids.FluidType.Properties.create()));
  projectile=new net.minecraft.world.entity.projectile.Snowball(net.minecraft.world.entity.EntityType.SNOWBALL,null);
  Set<ResourceLocation> ids;
  try(var vanilla=new ZipFile("build/moddev/artifacts/neoforge-21.1.250-client-extra-aka-minecraft-resources.jar")){
   ids=expand(JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/flansmod/tags/block/bullet_pass_through.json"))).getAsJsonObject(),vanilla,new HashSet<>());
  }
  var holders=new ArrayList<Holder<Block>>();
  for(var id:ids)holders.add(BuiltInRegistries.BLOCK.getHolder(id).orElseThrow(()->new AssertionError("unknown block: "+id)));
  var bindings=new HashMap<net.minecraft.tags.TagKey<Block>,List<Holder<Block>>>();
  bindings.put(BulletBlockTrace.PASS_THROUGH,holders);
  // Populate already-requested native tags as well, so the fixture does not erase leaves/wool
  // or tool tags merely because it skips a full resource-server reload.
  try(var vanilla=new ZipFile("build/moddev/artifacts/neoforge-21.1.250-client-extra-aka-minecraft-resources.jar")){
   var nativeTags=new HashSet<>(BuiltInRegistries.BLOCK.getTagNames().toList());
   nativeTags.addAll(List.of(net.minecraft.tags.BlockTags.LEAVES,net.minecraft.tags.BlockTags.WOOL,
     net.minecraft.tags.BlockTags.WOOL_CARPETS,net.minecraft.tags.BlockTags.BEDS));
   for(var key:nativeTags) {
    var id=key.location();var file=vanilla.getEntry("data/"+id.getNamespace()+"/tags/block/"+id.getPath()+".json");
    check(file!=null,"native bootstrap tag has backing resource: "+id);
    try(var reader=new java.io.InputStreamReader(vanilla.getInputStream(file),java.nio.charset.StandardCharsets.UTF_8)) {
     var values=new ArrayList<Holder<Block>>();
     for(var block:expand(JsonParser.parseReader(reader).getAsJsonObject(),vanilla,new HashSet<>()))values.add(BuiltInRegistries.BLOCK.getHolder(block).orElseThrow());
     bindings.put(key,values);
    }
   }
  }
  BuiltInRegistries.BLOCK.bindTags(bindings);
  check(ids.size()>200,"native tag covers wood variants and all wool colours");
  for(var block:List.of(Blocks.OAK_LEAVES,Blocks.CHERRY_PLANKS,Blocks.STRIPPED_SPRUCE_LOG,Blocks.BAMBOO_MOSAIC,
    Blocks.CRIMSON_PLANKS,Blocks.WHITE_WOOL,Blocks.RED_WOOL,Blocks.BLUE_CARPET,Blocks.OAK_DOOR,Blocks.CHEST))
   check(block.defaultBlockState().is(BulletBlockTrace.PASS_THROUGH),"requested soft material: "+block);
  for(var block:List.of(Blocks.STONE,Blocks.IRON_BLOCK,Blocks.GLASS,Blocks.TINTED_GLASS,Blocks.IRON_DOOR,
    Blocks.IRON_TRAPDOOR,Blocks.BRICKS,Blocks.WHITE_CONCRETE_POWDER,Blocks.DIRT,Blocks.NETHER_BRICKS))
   check(!block.defaultBlockState().is(BulletBlockTrace.PASS_THROUGH),"hard/other material excluded: "+block);
  // All declared native states: horizontal shots at three heights/three offsets also cover
  // thin slabs, carpets, fence posts, closed doors, chests and ladders.
  var collidingBlocks=new HashSet<Block>();
  for(var holder:holders) {
   var world=new Grid().put(1,0,0,holder.value());boolean nativeHit=false;
   for(double y:new double[]{.02,.3,.75})for(double z:new double[]{.02,.5,.95}){
    var start=new Vec3(0,y,z);var end=new Vec3(3,y,z);
    projectile.setPos(start);
    var original=world.clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,projectile));
    nativeHit|=original.getType()==HitResult.Type.BLOCK;
    miss(world,start,end,"passes declared block at every sampled height: "+holder.value());
   }
   if(nativeHit)collidingBlocks.add(holder.value());
  }
  check(collidingBlocks.containsAll(List.of(Blocks.OAK_LEAVES,Blocks.OAK_PLANKS,Blocks.OAK_LOG,Blocks.RED_WOOL,
    Blocks.OAK_STAIRS,Blocks.OAK_SLAB,Blocks.OAK_FENCE,Blocks.OAK_FENCE_GATE,Blocks.OAK_DOOR,Blocks.OAK_TRAPDOOR,
    Blocks.BLUE_CARPET,Blocks.CHEST)),"native control rays hit leaves/wood/wool and thin wood shapes before filtering");
  var chain=new Grid().put(1,0,0,Blocks.OAK_LEAVES).put(2,0,0,Blocks.OAK_PLANKS).put(3,0,0,Blocks.RED_WOOL).put(5,0,0,Blocks.STONE);
  var start=new Vec3(0,.5,.5);var end=new Vec3(8,.5,.5);
  hit(chain,start,end,new BlockPos(5,0,0),"mixed soft blocks do not hide next stone wall");
  var clipped=trace(chain,start,end).getLocation();
  check(new AABB(4,.1,.1,4.8,.9,.9).clip(start,clipped).isPresent(),"target behind wood and before stone remains reachable in same sweep");
  check(new AABB(6,.1,.1,6.8,.9,.9).clip(start,clipped).isEmpty(),"target behind stone cannot be reached");
  hit(chain,new Vec3(1.5,.5,.5),end,new BlockPos(5,0,0),"starting inside leaves still finds stone");
  for(var hard:List.of(Blocks.STONE,Blocks.IRON_BLOCK,Blocks.GLASS,Blocks.TINTED_GLASS,Blocks.BRICKS))
   hit(new Grid().put(1,0,0,Blocks.WHITE_WOOL).put(2,0,0,hard),start,end,new BlockPos(2,0,0),"hard barrier behind wool: "+hard);
  var reversed=new Grid().put(-1,0,0,Blocks.OAK_LOG).put(-2,0,0,Blocks.BLACK_WOOL).put(-4,0,0,Blocks.IRON_BLOCK);
  hit(reversed,new Vec3(.5,.5,.5),new Vec3(-8,.5,.5),new BlockPos(-4,0,0),"negative direction and coordinates");
  var diagonal=new Grid().put(1,1,1,Blocks.BIRCH_LEAVES).put(2,2,2,Blocks.SPRUCE_PLANKS).put(4,4,4,Blocks.STONE);
  hit(diagonal,new Vec3(.5,.5,.5),new Vec3(8.5,8.5,8.5),new BlockPos(4,4,4),"diagonal voxel boundary traversal");
  var thick=new Grid();for(int x=1;x<=34;x++)thick.put(x,0,0,x%2==0?Blocks.WARPED_PLANKS:Blocks.GREEN_WOOL);
  thick.put(35,0,0,Blocks.STONE);hit(thick,start,new Vec3(50,.5,.5),new BlockPos(35,0,0),"no repeated-ray skip cap on thick wood wall");
  var distant=new Grid().put(20000001,64,-20000000,Blocks.OAK_PLANKS).put(20000003,64,-20000000,Blocks.STONE);
  hit(distant,new Vec3(20000000,64.5,-19999999.5),new Vec3(20000008,64.5,-19999999.5),new BlockPos(20000003,64,-20000000),"double-precision far-world sweep");
  var empty=new Grid().put(1,0,0,Blocks.WATER);miss(empty,start,end,"water remains ignored by collider ray policy");
  check(chain.blocks.size()==4 && chain.getBlockState(new BlockPos(2,0,0)).is(Blocks.OAK_PLANKS),"sweeps never remove or mutate blocks");
  var compiled=new ClassNode();try(var in=VerifyBulletBlocks.class.getResourceAsStream("/com/flansmod/common/entity/EntityBullet.class")){new ClassReader(in).accept(compiled,0);}
  var calls=new ArrayList<String>();boolean resolves=false;
  for(var method:compiled.methods)if(method.name.equals("tick"))for(var ins:method.instructions)if(ins instanceof MethodInsnNode call){
   if(call.name.equals("bulletType"))resolves=true;
   if(call.owner.endsWith("BulletBlockTrace") && call.name.equals("clip"))calls.add("clip");
   if(call.name.equals("findEntityHit"))calls.add("entity");
   if(call.owner.endsWith("SoftBlockBallistics") && call.name.equals("remainingDamage"))calls.add("damage");
   if(call.name.equals("hitEntity"))calls.add("hurt");
  }
  check(calls.equals(List.of("clip","entity","damage","hurt")) && resolves,"actual server resolves ammo, bounds travel, attenuates then hurts target");
  attenuation(args[0]);
  System.out.printf("PASS: %d declared soft blocks (%d native colliders), %d native sweeps, %d damage samples; mixed/thick/inside/negative/diagonal/far-world rays, same-tick targets, hard-wall occlusion, penetration-ranked/material/thickness loss, tick-subdivision invariance, target-bounded loss, real ammo/NBT/cache contracts, no block mutation, production server wiring.%n",ids.size(),collidingBlocks.size(),sweeps,damageSamples);
 }
 static void attenuation(String packPath)throws Exception {
  var pack=new OfflinePack(Path.of(packPath));pack.readTypes();
  var ammo=pack.types.stream().filter(t->t instanceof BulletType b&&!b.isCase).map(t->(BulletType)t)
    .sorted(Comparator.comparingDouble(SoftBlockBallistics::penetrationLevel)).toList();
  check(ammo.size()==73,"73 live EFT ammo definitions");
  var wood=new Grid().put(1,0,0,Blocks.OAK_PLANKS);var wool=new Grid().put(1,0,0,Blocks.WHITE_WOOL);var leaf=new Grid().put(1,0,0,Blocks.OAK_LEAVES);
  var from=new Vec3(0,.5,.5);var to=new Vec3(3,.5,.5);float previous=0;
  for(var round:ammo) {
   float retained=damage(wood,from,to,round,100)/100;
   check(retained>0 && retained<1 && retained+1E-6F>=previous,"higher penetration retains more damage: "+round.shortName);
   previous=retained;
   float leafDamage=damage(leaf,from,to,round,100),woolDamage=damage(wool,from,to,round,100);
   check(leafDamage>woolDamage && woolDamage>retained*100,"leaves lose less than wool, wool less than wood");
   close(damage(new Grid(),from,to,round,12.75F),12.75F,"air preserves exact float damage");
   close(damage(wood,from,to,round,25.5F),25.5F*retained,"loss is a fraction of original gun/ammo damage");
   float split=100;
   for(int i=0;i<17;i++)split=damage(wood,from.lerp(to,i/17D),from.lerp(to,(i+1)/17D),round,split);
   close(split,retained*100,"crossing one block over 17 ticks cannot charge 17 full blocks");
   close(damage(wood,to,from,round,100),retained*100,"reverse path loses same damage");
   var two=new Grid().put(1,0,0,Blocks.OAK_PLANKS).put(2,0,0,Blocks.OAK_PLANKS);
   close(damage(two,from,new Vec3(4,.5,.5),round,100),retained*retained*100,"two layers accumulate");
   float half=damage(wood,from,new Vec3(1.5,.5,.5),round,100);
   close(half,(float)Math.sqrt(retained)*100,"target halfway through wood charges only half thickness");
   close(damage(wood,new Vec3(1.5,.5,.5),to,round,100),half,"starting inside wood charges only remaining half");
   close(damage(wood,from,new Vec3(.9,.5,.5),round,100),100,"wood after first target cannot reduce its damage");
   close(damage(wood,from,from,round,100),100,"zero movement has zero loss");
  }
  var low=BulletType.getBullet("5.45x39mmSP");var high=BulletType.getBullet("5.45x39mm7n39");
  check(low.penetratingPower==high.penetratingPower,"real EFT control pair has identical old penetration budget");
  check(damage(wood,from,to,high,100)>damage(wood,from,to,low,100),"actual PenetrationLevel differentiates soft point and high-penetration ammo");
  var slab=new Grid().put(1,0,0,Blocks.OAK_SLAB);var carpet=new Grid().put(1,0,0,Blocks.WHITE_CARPET);
  var below=new Vec3(1.5,-1,.5);var above=new Vec3(1.5,2,.5);
  float full=damage(wood,below,above,high,100)/100;
  close(damage(slab,below,above,high,100),(float)Math.sqrt(full)*100,"native half-slab is half thickness");
  float fullWool=damage(wool,below,above,high,100)/100;
  close(damage(carpet,below,above,high,100),(float)Math.pow(fullWool,1D/16D)*100,"native carpet is one-sixteenth thickness");
  var diagonal=new Vec3(.5,-.5,-.5);var diagonalEnd=new Vec3(2.5,1.5,1.5);
  close(damage(wood,diagonal,diagonalEnd,high,100),(float)Math.pow(full,Math.sqrt(3))*100,"diagonal traversal is physically thicker");
  var stone=new Grid().put(2,0,0,Blocks.STONE);
  close(damage(stone,from,trace(stone,from,to).getLocation(),high,100),100,"hard stop does not get treated as soft attenuation");
  // Native EntityBullet constructor and save data, no world ticking or game client.
  var bullet=new com.flansmod.common.entity.EntityBullet((net.minecraft.world.entity.EntityType)net.minecraft.world.entity.EntityType.SNOWBALL,null);
  var read=bullet.getClass().getDeclaredMethod("readAdditionalSaveData",net.minecraft.nbt.CompoundTag.class);read.setAccessible(true);
  var write=bullet.getClass().getDeclaredMethod("addAdditionalSaveData",net.minecraft.nbt.CompoundTag.class);write.setAccessible(true);
  var tag=new net.minecraft.nbt.CompoundTag();tag.putString("bulletType",high.shortName);tag.putFloat("damage",damage(wood,from,to,high,12.75F));tag.putFloat("penetratingPower",high.penetratingPower);
  read.invoke(bullet,tag);check(bullet.bulletType()==high,"server lazy cache resolves ammo after synced-name/NBT invalidation");
  var saved=new net.minecraft.nbt.CompoundTag();write.invoke(bullet,saved);
  close(saved.getFloat("damage"),tag.getFloat("damage"),"remaining damage persists across save/reload");
  var cache=bullet.getClass().getDeclaredField("type");cache.setAccessible(true);cache.set(bullet,low);
  var name=bullet.getClass().getDeclaredField("DATA_TYPE_NAME");name.setAccessible(true);
  bullet.getEntityData().set((net.minecraft.network.syncher.EntityDataAccessor<String>)name.get(null),high.shortName+" ");
  check(cache.get(bullet)==null,"native synced update really invalidates definition cache");
  bullet.getEntityData().set((net.minecraft.network.syncher.EntityDataAccessor<String>)name.get(null),high.shortName);
  check(bullet.bulletType()==high,"subsequent server resolve restores real ammo fields");
  for(var round:List.of(low,high))System.out.printf("%s PenetrationLevel=%.1f, retained after 1 block: leaves=%.2f%% wool=%.2f%% wood=%.2f%%%n",
    round.shortName,SoftBlockBallistics.penetrationLevel(round),damage(leaf,from,to,round,100),damage(wool,from,to,round,100),damage(wood,from,to,round,100));
 }
}
