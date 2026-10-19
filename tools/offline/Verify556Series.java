import com.flansmod.common.*;
import com.flansmod.common.types.*;
import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.client.renderer.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import java.nio.file.Path;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Real pack parsing, mixed magazine/chamber transactions and production vertex capture, no world. */
public final class Verify556Series {
    static int checks,draws,vertices;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static void near(float actual,float expected,String message){check(Math.abs(actual-expected)<.00003F,message+": "+actual+" / "+expected);}
    static <T extends InfoType>T type(String pack,String name,Class<T> kind){return kind.cast(InfoType.infoTypes.stream().filter(t->t.contentPack.startsWith(pack)&&name.equals(t.shortName)).findFirst().orElseThrow());}
    static GunType gun(String name){return type("Octagon",name,GunType.class);}
    static BulletType bullet(String name){return type("Octagon",name,BulletType.class);}
    static TypeFile file(EnumType kind,String name,String...lines){var f=new TypeFile(kind,name,false);f.contentPack="verification";f.lines.add("ShortName "+name);f.lines.addAll(List.of(lines));return f;}
    static GunType fixture(String name,String...lines){var f=file(EnumType.gun,name,lines);var g=new GunType(f);g.read(f);return g;}
    static ItemStack save(ItemStack stack){return ItemStack.parseOptional(RegistryAccess.EMPTY,(CompoundTag)stack.save(RegistryAccess.EMPTY));}
    static ItemStack load(GunType mag,List<BulletType> order){
        var stack=new ItemStack(mag.item);var inventory=new Inventory(null);
        for(var b:order){inventory.setItem(0,new ItemStack(b.item));check(GunMagazine.loadOneBullet(stack,mag,inventory),"declared round compresses: "+b.shortName);check(inventory.getItem(0).isEmpty(),"one round consumed");}
        return stack;
    }
    static void parsing(){
        var test=type("EscapeFromTarcraftReforged","TESTmag",GunType.class);
        check(test.ammoNames.size()==14&&test.ammoNames.contains("556x45")&&test.ammoNames.contains("338"),"all fourteen TESTmag calibre tags retained");
        var f=fixture("multi","Ammo One next Two NEXT Three // words are comments","Ammo Four");
        check(f.ammoNames.equals(List.of("One","Two","Three","Four")),"multiple Ammo values/lines and separator/comments");
        for(String name:List.of("556x45mm","556x45mmAP","556x45mmJHP","9x19mm")){
            var b=bullet(name);check(b.shapeless&&b.recipeLine.length==2,"inline shapeless ingredients "+name);
            check(b.recipeOutput==30,"recipe output retained "+name);
            check(name.equals(b.iconPath)&&b.bulletModel!=null&&b.bulletTexture!=null,"settings following shapeless recipe retained "+name);
            near(b.bulletScale,.1F,"original bullet scale "+name);
            var rig=ModelCache.rigForReference(b.bulletModel,b.bulletScale);
            check(rig!=null&&!rig.quads.isEmpty(),"original round mesh resolves "+name);
        }
        var sf=file(EnumType.part,"shaped","Recipe X iron","XXX"," X ","XXX","RecipeOutput 3","Icon shaped","Name After Grid");
        var shaped=new PartType(sf);shaped.read(sf);
        check(!shaped.shapeless&&shaped.recipeLine[0].equals("Recipe")&&shaped.recipeOutput==3,"shaped recipe consumes exactly three rows");
        check(shaped.recipeGrid[0][0]=='X'&&shaped.recipeGrid[2][2]=='X'&&shaped.iconPath.equals("shaped")&&shaped.name.equals("After Grid"),"shaped recipe leaves subsequent settings intact");
        for(String name:List.of("556x45mm20RoundMagazine","556x45mm30RoundMagazine","556x45mm30RoundMagazineMagazinePull","TEST5.56x45mmMag","XProductsX15556x45mm50RoundDrum")){
            var mag=gun(name);check(mag.ammoNames.size()==6,"all six magazine ammo declarations "+name);
            for(String ammo:mag.ammoNames){var b=bullet(ammo);var stack=load(mag,List.of(b));check(GunMagazine.roundsRemaining(save(stack))==1,"each declared live/case item survives NBT "+ammo);}
        }
        for(var b:BulletType.bullets)if(b.item!=null&&"556x45".equals(b.ammoType))check(GunMagazine.roundsRemaining(load(test,List.of(b)))==1,"universal magazine accepts EFT family "+b.shortName);
        var inventory=new Inventory(null);inventory.setItem(0,new ItemStack(bullet("9x19mm").item));
        check(!GunMagazine.loadOneBullet(new ItemStack(gun("556x45mm30RoundMagazine").item),gun("556x45mm30RoundMagazine"),inventory),"incompatible 9mm stays rejected");
        check(inventory.getItem(0).getCount()==1,"rejected round stays in inventory");
        var drum=ModelCache.rigFor(gun("XProductsX15556x45mm50RoundDrum"),.1F);
        check(Arrays.deepEquals(drum.animation.fmumVectorArrays.get("bulletAttachPoint1"),drum.animation.fmumVectorArrays.get("bulletAttachPoint2")),"X-15 literal element references preserve even-round seating array");
        check(FmumBulletPose.seats(drum.animation,12).size()==3&&FmumBulletPose.seats(drum.animation,11).size()==3,"drum shows original three seated rounds at either parity");
    }
    static void cases()throws Exception{
        var mag=gun("556x45mm30RoundMagazine");var g=gun("HK416ND10RS");
        BulletType normal=bullet("556x45mm"),ap=bullet("556x45mmAP"),jhp=bullet("556x45mmJHP"),casing=bullet("556x45mmCase");
        var order=List.of(normal,ap,casing,jhp);var stock=save(load(mag,order));
        for(int i=0;i<order.size();i++)check(InfoType.getType(GunMagazine.magazineRoundAt(stock,mag,i))==order.get(i),"mixed bottom-to-mouth order survives save "+i);
        var stack=new ItemStack(g.item);var inventory=new Inventory(null);inventory.setItem(0,stock);
        var returned=new ArrayList<ItemStack>();check(GunReloading.install(stack,g,inventory,0,stock.copy(),returned::add),"mixed magazine actual install");
        check(GunMagazine.getChamberedRound(stack).isEmpty(),"advanced install leaves chamber empty");
        GunReloading.charge(stack,g,returned::add);check(GunFiring.liveRound(stack,g,0)==jhp,"last compressed JHP feeds first");
        check(InfoType.getType(GunMagazine.spendChamberedRound(stack,g))==jhp,"JHP spent once");
        stack=save(stack);check(InfoType.getType(GunMagazine.getChamberedRound(stack))==casing,"case remains independent in saved chamber");
        check(GunFiring.liveRound(stack,g,0)==null,"case cannot become a projectile");
        var before=stack.copy();rejectFire(stack,g);check(ItemStack.matches(before,stack),"actual fire entry preserves case/chamber/magazine before accessing world");
        GunReloading.charge(stack,g,returned::add);
        check(returned.size()==1&&InfoType.getType(returned.getFirst())==casing,"manual charge ejects exactly one stored case");
        check(GunFiring.liveRound(stack,g,0)==ap,"AP behind case can feed after manual extraction");
        GunMagazine.spendChamberedRound(stack,g);check(GunFiring.liveRound(stack,g,0)==normal,"mixed order continues to ordinary round");
        GunMagazine.spendChamberedRound(stack,g);check(GunMagazine.isEmpty(stack,g),"three live rounds plus one extracted case exhaust original four");
        check(GunMagazine.magazineType(stack,g)==mag,"empty installed magazine survives");
        for(String name:List.of("556x45mmCase","556x45mmCaseAP","556x45mmCaseJHP")){
            var direct=new ItemStack(g.item);GunMagazine.setChamberedRound(direct,new ItemStack(bullet(name).item));
            check(GunFiring.liveRound(direct,g,0)==null,"all 5.56 cases rejected "+name);rejectFire(direct,g);
            var legacy=new ItemStack(g.item);GunMagazine.set(legacy,g,0,new ItemStack(bullet(name).item));
            rejectFire(legacy,g);check(!GunMagazine.get(legacy,g,0).isEmpty(),"legacy direct case not consumed "+name);
        }
        rejectFire(new ItemStack(g.item),g);
        check(GunFiring.liveRound(new ItemStack(mag.item),mag,0)==null,"magazine items cannot fire");
    }
    static void rejectFire(ItemStack stack,GunType gun)throws Exception{
        var method=GunFiring.class.getDeclaredMethod("fireRound",net.minecraft.world.level.Level.class,net.minecraft.world.entity.player.Player.class,ItemStack.class,GunType.class,PlayerData.class,boolean.class,int.class);
        method.setAccessible(true);method.invoke(null,null,null,stack,gun,null,false,0);checks++;
    }
    static final class Vertex {float x,y,z,u,v;Vertex(float x,float y,float z){this.x=x;this.y=y;this.z=z;}}
    static final class Capture implements MultiBufferSource,VertexConsumer {
        final List<List<Vertex>> batches=new ArrayList<>();List<Vertex> active;
        public VertexConsumer getBuffer(RenderType type){active=new ArrayList<>();batches.add(active);return this;}
        public VertexConsumer addVertex(float x,float y,float z){active.add(new Vertex(x,y,z));return this;}
        public VertexConsumer setUv(float u,float v){var p=active.getLast();p.u=u;p.v=v;return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){return this;}
        public VertexConsumer setUv1(int u,int v){return this;}public VertexConsumer setUv2(int u,int v){return this;}
        public VertexConsumer setNormal(float x,float y,float z){return this;}
    }
    static Capture contents(GunType mag,ItemStack stack,BakedRig rig)throws Exception{
        var method=TurborigItemRenderer.class.getDeclaredMethod("renderSeatedRounds",PoseStack.class,MultiBufferSource.class,GunType.class,ItemStack.class,BakedRig.class,boolean.class,int.class,int.class);
        method.setAccessible(true);var result=new Capture();var pose=new PoseStack();
        method.invoke(null,pose,result,mag,stack,rig,false,0xF000F0,0);
        check(pose.last().pose().equals(new Matrix4f(),1E-6F),"contents rendering restores caller pose");return result;
    }
    static void followers()throws Exception{
        int models=0;
        for(var t:InfoType.infoTypes)if(t instanceof GunType mag&&mag.item!=null&&mag.magFollowerModel!=null){
            models++;near(mag.magFollowerScale,.1F,"authored follower scale "+mag.shortName);
            check(mag.magFollowerTexture==null,"None remains skin inheritance "+mag.shortName);
            var skin=Textures.forMagazineFollower(mag,Audit556Series::has);
            check(skin.equals(Textures.forType(mag,Audit556Series::has))&&Audit556Series.has(skin),"follower inherits actual magazine skin");
            var rig=ModelCache.rigFor(mag,mag.modelScale);var follower=ModelCache.rigForReference(mag.magFollowerModel,mag.magFollowerScale);
            check(follower!=null&&!follower.quads.isEmpty(),"real follower mesh");
            var positions=rig.animation.fmumVectorArrays.get("magFollowerAttachPos");
            for(int count:new int[]{0,1,2,3,8,9,mag.ammoCapacity}){
                var pose=FmumBulletPose.follower(rig.animation,count);check(pose!=null&&pose.isFinite(),"follower valid at count "+count);
                var expected=positions[Math.min(count,positions.length-1)];var origin=pose.transformPosition(new Vector3f());
                near(origin.x,expected[0],"follower position X");near(origin.y,expected[1],"follower position Y");near(origin.z,expected[2],"follower position Z");
            }
            var ammo=BulletType.bullets.stream().filter(b->b.item!=null&&!b.isCase&&mag.ammoNames.stream().anyMatch(n->Audit556Series.matches(b,n))).findFirst().orElseThrow();
            for(int count:new int[]{0,1,Math.min(12,mag.ammoCapacity)}){
                var order=new ArrayList<BulletType>();for(int i=0;i<count;i++)order.add(ammo);
                var stack=load(mag,order);var capture=contents(mag,stack,rig);
                check(capture.batches.size()==1+FmumBulletPose.seats(rig.animation,count).size(),"live renderer emits follower even when empty, plus visible actual rounds");
                check(capture.batches.getFirst().size()==follower.quads.size()*4,"production follower vertex count");
                var first=capture.batches.getFirst().getFirst();var expected=FmumBulletPose.follower(rig.animation,count).transformPosition(new Vector3f(follower.quads.getFirst().positions()[0]));
                near(first.x,expected.x,"rendered follower X uses original scale/position");near(first.y,expected.y,"rendered follower Y");near(first.z,expected.z,"rendered follower Z");
                draws++;for(var batch:capture.batches)for(var v:batch){check(Float.isFinite(v.x)&&Float.isFinite(v.y)&&Float.isFinite(v.z)&&Float.isFinite(v.u)&&Float.isFinite(v.v),"finite production content vertex");vertices++;}
            }
        }
        check(models==7,"five 5.56 and two 9mm followers covered");
        var fixture=new ModelGeometry.Model();check(FmumBulletPose.follower(fixture,0)==null,"models without follower positions draw none");
        fixture.fmumVectorArrays.put("magFollowerAttachPos",new float[][]{{1,2,3}});fixture.fmumVectorArrays.put("magFollowerAttachRot",new float[][]{{0,0,90}});
        var rotated=FmumBulletPose.follower(fixture,99).transformPosition(new Vector3f(1,0,0));near(rotated.x,1,"follower rotation about own origin X");near(rotated.y,3,"rotation degrees after translation");near(rotated.z,3,"follower rotation Z");
    }
    static void icons()throws Exception {
        var id=net.minecraft.resources.ResourceLocation.parse("flansmod:textures/item/testmag.png");
        try(var generated=GeneratedAssets.build()) {
            var supplier=generated.getResource(net.minecraft.server.packs.PackType.CLIENT_RESOURCES,id);
            check(supplier!=null,"TESTmag icon is available through the actual generated resource pack");
            try(var stream=supplier.get()) {
                var image=javax.imageio.ImageIO.read(stream);check(image!=null&&image.getWidth()==64&&image.getHeight()==64,"bundled original-model icon is a real PNG");
                int opaque=0;for(int y=0;y<64;y++)for(int x=0;x<64;x++)if((image.getRGB(x,y)>>>24)>0)opaque++;
                check(opaque>30&&opaque<64*64,"inventory icon is visible with transparent background");
            }
        }
        // A private temporary pack tests override precedence without writing the user's packs.
        var previous=new LinkedHashMap<>(FlansMod.contentPacks);
        var directory=java.nio.file.Files.createTempDirectory("flans-556-icon-override-");
        var sample=Audit556Series.packs.stream().map(p->p.path(net.minecraft.resources.ResourceLocation.parse("flansmod:textures/items/stanag_556x45_30rounds.png"))).filter(Objects::nonNull).findFirst().orElseThrow();
        var png=java.nio.file.Files.readAllBytes(sample);var file=directory.resolve("assets/flansmod/textures/items/testmag.png");
        java.nio.file.Files.createDirectories(file.getParent());java.nio.file.Files.write(file,png);
        try {
            FlansMod.contentPacks.clear();FlansMod.contentPacks.put("icon-override-fixture",directory);FlansMod.contentPacks.putAll(previous);
            var find=GeneratedAssets.class.getDeclaredMethod("findIcon",String.class);find.setAccessible(true);
            check(Arrays.equals(png,(byte[])find.invoke(null,"items/testmag")),"a pack-supplied icon always takes precedence over bundled fallback");
        } finally { FlansMod.contentPacks.clear();FlansMod.contentPacks.putAll(previous); }
    }
    public static void main(String[] args)throws Exception{
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();Audit556Series.load(Path.of(args[0]));
        parsing();cases();followers();icons();
        System.out.println("PASS: "+checks+" checks; multi-calibre and six-value Ammo, shaped/shapeless definitions, AP/JHP/case storage, mixed chamber/manual case extraction, "+draws+" production content draws / "+vertices+" vertices; no world, GPU or input.");
    }
}
