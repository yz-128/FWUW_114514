import com.flansmod.common.*;
import com.flansmod.common.types.*;
import com.flansmod.common.network.ArmourMaskPayload;
import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.packs.PackType;
import net.neoforged.neoforge.event.entity.living.ArmorHurtEvent;
import java.nio.file.*;
import java.util.*;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;
import io.netty.buffer.Unpooled;

/** Actual all-pack item/resource contracts plus deterministic plate, mask and wear regression. */
public final class VerifyAllArmour {
    static int checks,parts,masks,sounds,fixtures;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static void near(double a,double b,String m){check(Double.isFinite(a)&&Math.abs(a-b)<.0001,m+": "+a+" != "+b);}
    static ArmourType find(String name){return ArmourType.armours.stream().filter(a->name.equals(a.shortName)).findFirst().orElseThrow();}
    static ItemStack fixture(String...lines){
        String name="armour_fixture_"+(fixtures++);var f=new TypeFile(EnumType.armour,name,false);
        f.lines.add("ShortName "+name);f.lines.addAll(List.of(lines));var a=new ArmourType(f);a.read(f);
        var item=new ItemFlanArmour(a,new Item.Properties());Registry.register(BuiltInRegistries.ITEM,ResourceLocation.parse("flansmod:"+name),item);
        return new ItemStack(item);
    }
    static BulletType bullet(String...lines){var f=new TypeFile(EnumType.bullet,"plateBullet",false);f.lines.add("ShortName plateBullet");f.lines.addAll(List.of(lines));var b=new BulletType(f);b.read(f);return b;}

    static void actual()throws Exception {
        check(ArmourType.armours.size()==118,"all four armour packs, including duplicate definitions");
        var generated=GeneratedAssets.build();int registered=0,unbreakable=0,icons=0,duplicates=0;
        Set<ResourceLocation> aliases=new HashSet<>();
        for(var a:ArmourType.armours){
            var skin=ArmourResources.source(a).orElseThrow();var pixels=skin.entry().readAllBytes();
            var image=ImageIO.read(new ByteArrayInputStream(pixels));check(image!=null&&image.getWidth()>0&&image.getHeight()>0,"decodable skin: "+a.shortName);
            var geometry=ModelCache.load(a);check(geometry!=null&&!geometry.parts.isEmpty(),"model exists: "+a.shortName);
            var uv=ModelCache.uvScaleForDimensions(geometry,image.getWidth(),image.getHeight());
            var rig=ArmourGeometry.bake(geometry,a.modelScale,uv[0],uv[1]);check(!rig.quads.isEmpty(),"nonempty worn mesh: "+a.shortName);
            var pose=VerifyArmourRendering.body();pose.young=false;
            for(var group:rig.quadsByPart.keySet())check(LegacyArmourModel.joint(pose,group)!=null,"supported bone: "+group);
            var sink=new VerifyArmourRendering.Recorder();new LegacyArmourModel(rig,pose).renderToBuffer(new com.mojang.blaze3d.vertex.PoseStack(),sink,0xF000F0,0,-1);
            check(!sink.positions.isEmpty(),"real worn vertices: "+a.shortName);
            if(a.item==null){duplicates++;continue;}
            registered++;check(a.item instanceof ItemFlanArmour,"native ArmorItem: "+a.shortName);var item=(ItemFlanArmour)a.item;var stack=new ItemStack(item);
            check(Equipable.get(stack)==item,"equipable discovery: "+a.shortName);
            check(item.getEquipmentSlot()==VerifyArmourItems.slot(a.type),"authored slot: "+a.shortName);
            check(stack.getMaxStackSize()==1,"unstackable equipment");check(stack.getMaxDamage()==a.durability,"exact maximum durability");
            check(stack.isDamageableItem()==(a.breakable&&a.durability>0),"breakable/cosmetic contract: "+a.shortName);
            if(!a.breakable){unbreakable++;check(stack.has(DataComponents.UNBREAKABLE),"unbreakable vanilla component");}
            check(InfoType.getType(stack)==a,"registered subclass resolves definition");
            var texture=Textures.forArmour(a);check(texture.equals(item.getMaterial().value().layers().getFirst().texture(a.type==2)),"baker and native layer bind identical alias");
            check(aliases.add(texture),"independent per-item material");
            String registry=ItemAssets.registryPathOf(a);
            for(int layer=1;layer<=2;layer++){
                var id=ResourceLocation.parse("flansmod:textures/models/armor/legacy/"+registry+"_layer_"+layer+".png");
                var resource=generated.getResource(PackType.CLIENT_RESOURCES,id);check(resource!=null,"generated skin alias");
                try(var in=resource.get()){check(Arrays.equals(in.readAllBytes(),pixels),"alias matches selected source bytes: "+a.shortName);}
            }
            var icon=generated.getResource(PackType.CLIENT_RESOURCES,ResourceLocation.parse("flansmod:textures/item/"+registry+".png"));
            check(icon!=null,"all inventory icons exist: "+a.shortName);try(var in=icon.get()){check(ImageIO.read(in)!=null,"decodable inventory icon");}icons++;
            for(int part=0;part<4;part++){
                stack.setDamageValue(0);near(ArmourState.proof(a,stack,part),a.proofLevel[part],"fresh proof: "+a.shortName);parts++;
                if(a.durability>0){stack.setDamageValue(a.durability);near(ArmourState.proof(a,stack,part),0,"exhausted proof");stack.setDamageValue(a.durability/2);near(ArmourState.proof(a,stack,part),a.proofLevel[part]*(a.durability-a.durability/2)/(double)a.durability,"worn proof");}
            }
            stack.setDamageValue(0);
            for(int i=0;i<8;i++)if(a.numHitSounds[i]>0){
                check(a.hitSounds[i]!=null,"declared sound family");
                for(int n=0;n<a.numHitSounds[i];n++){
                    var id=FmumSounds.id(ArmourSounds.hit(a,i,n));
                    check(ContentPackIndex.current().find("assets/"+id.getNamespace()+"/sounds/"+id.getPath()+".ogg",true).isPresent(),"impact audio exists: "+a.shortName+" / "+id);sounds++;
                }
            }
            if(a.hasMask){masks++;mask(a,stack);}
        }
        check(registered==99&&duplicates==19,"99 real items, 19 first-wins duplicates");check(unbreakable==4,"all four nonbreakable clothes");check(icons==99,"no missing registered icons");check(masks==2,"two registered toggle helmets");
        var reforged=find("6B13M");var scav=find("6B13MScav");
        var r=ArmourResources.source(reforged).orElseThrow().entry();var s=ArmourResources.source(scav).orElseThrow().entry();
        check(r.source().getFileName().toString().contains("Reforged"),"Reforged keeps its own skin");
        check(s.source().getFileName().toString().equals("[FMUM]EscapeFromTarCraft-Armor"),"Scav follows borrowed old EFT model skin");
        check(ImageIO.read(new ByteArrayInputStream(r.readAllBytes())).getWidth()==128,"Reforged 128 atlas preserved");
        check(ImageIO.read(new ByteArrayInputStream(s.readAllBytes())).getWidth()==1024,"legacy 1024 atlas preserved");
        check(!Arrays.equals(r.readAllBytes(),s.readAllBytes()),"colliding names do not share incompatible pixels");
        var commander=ArmourResources.source(find("CommanderHat")).orElseThrow();
        check(commander.entry().path().toLowerCase(Locale.ROOT).contains("skins/armours/"),"CommanderHat alternate armour skin directory");
        System.out.println("All armour: 118 definitions / 99 items / 19 duplicates / "+parts+" proof parts / "+sounds+" impact sound variants");
    }
    static void mask(ArmourType a,ItemStack stack)throws Exception {
        CustomData.update(DataComponents.CUSTOM_DATA,stack,t->t.putString("unrelated","preserved"));
        check(!ArmourState.switched(stack),"legacy mask default false");var original=com.flansmod.client.ArmourControls.overlay(a,stack);
        if(original!=null)check(ContentPackIndex.current().find("assets/"+original.getNamespace()+"/"+original.getPath(),true).isPresent(),"real helmet overlay exists");
        check(!ArmourState.toggle(stack,"wrong helmet",false),"reject different helmet");
        check(ArmourState.toggle(stack,a.shortName,false),"actual mask toggles");check(!ArmourState.toggle(stack,a.shortName,false),"stale mode rejected");
        near(ArmourState.proof(a,stack,0),a.proofLevel[4],"switched head uses legacy mask proof index");
        check(ArmourState.switched(stack.copy()),"copy retains mask mode");check("preserved".equals(stack.get(DataComponents.CUSTOM_DATA).copyTag().getString("unrelated")),"other metadata preserved");
        var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);try{
            var request=new ArmourMaskPayload(a.shortName,true);ArmourMaskPayload.STREAM_CODEC.encode(buf,request);
            check(request.equals(ArmourMaskPayload.STREAM_CODEC.decode(buf)),"mask network codec roundtrip");check(buf.readableBytes()==0,"packet consumed exactly");
        }finally{buf.release();}
        check(ArmourState.toggle(stack,a.shortName,true),"mask returns to original mode");check(Objects.equals(original,com.flansmod.client.ArmourControls.overlay(a,stack)),"overlay restored");
    }
    static void plates(){
        check(!ArmourBallistics.blocks(0,0,0,0),"no protection never blocks even zero-penetration rounds");
        check(!ArmourBallistics.blocks(10,10,30,0),"minimum boundary penetrates");check(ArmourBallistics.blocks(30,10,30,.999),"maximum boundary blocks");
        check(ArmourBallistics.blocks(20,10,30,.49)&&!ArmourBallistics.blocks(20,10,30,.5),"intermediate proof probability boundary");
        near(ArmourBallistics.wear(2.3F,.2),3,"fractional wear rounds up");near(ArmourBallistics.wear(2.3F,.4),2,"fractional wear rounds down");near(ArmourBallistics.wear(Float.NaN,0),0,"invalid wear ignored");
        check(ArmourBallistics.part(new Vec3(0,1.7,0),0,1.62F,.6F)==0,"head region");
        check(ArmourBallistics.part(new Vec3(0,1.2,0),0,1.62F,.6F)==1,"body region");
        check(ArmourBallistics.part(new Vec3(.3,1.2,0),0,1.62F,.6F)==2,"left arm region");
        check(ArmourBallistics.part(new Vec3(-.3,1.2,0),0,1.62F,.6F)==3,"right arm region");
        check(ArmourBallistics.part(new Vec3(0,1.2,.3),90,1.62F,.6F)==2,"yaw rotates arm regions");
        var stack=fixture("Type Body","Durability 100","BodyProofLevel 40","BluntDamageMult .1 .5","PenetratedDamageMult .8 2");
        var round=bullet("PenetrationLevel 10 30","ArmorBreakPower 10","BluntDamageMult .5");
        near(round.armorBreakPower,10,"bullet parsed wear");near(round.minPenetrationLevel,10,"bullet penetration parsed");
        var blocked=ArmourBallistics.resolve(List.of(stack),round,1,100,true,()->.5);
        check(blocked.stopped()&&blocked.impacts().size()==1,"plate blocks round");near(blocked.damage(),1,"actual EFT player blunt damage");near(blocked.impacts().getFirst().wear(),5,"blocked wear multiplier");
        check(blocked.impacts().getFirst().soundPart()==1,"blocked body sound");ArmourBallistics.applyWear(blocked.impacts().getFirst());near(stack.getDamageValue(),5,"manual plate wear applied once");
        var mob=ArmourBallistics.resolve(List.of(stack),round,1,100,false,()->.5);near(mob.damage(),5,"mob blunt authored multipliers");
        round.minPenetrationLevel=100;round.maxPenetrationLevel=200;
        var pierced=ArmourBallistics.resolve(List.of(stack),round,1,100,true,()->.5);
        check(!pierced.stopped()&&pierced.impacts().size()==1,"high penetration passes");near(pierced.damage(),80,"penetrated damage multiplier");near(pierced.impacts().getFirst().wear(),20,"penetrated wear multiplier");check(pierced.impacts().getFirst().soundPart()==5,"penetrated body sound");
        var layered=ArmourBallistics.resolve(List.of(stack,stack.copy()),round,1,100,true,()->.5);near(layered.damage(),64,"ordered multiple plate layers");check(layered.impacts().size()==2,"both penetrated layers wear");
        stack.setDamageValue(100);var exhausted=ArmourBallistics.resolve(List.of(stack),round,1,100,true,()->.5);near(exhausted.damage(),100,"exhausted plate does not protect");check(exhausted.impacts().isEmpty(),"exhausted plate no sound/wear");
        stack.setDamageValue(98);ArmourBallistics.applyWear(new ArmourBallistics.Impact(stack,10,1,false));check(!stack.isEmpty()&&stack.getDamageValue()==100,"exhaustion retains equipment at zero condition");
        var fixed=fixture("Type Body","Durability 100","BreakableArmor false","BodyProofLevel 40");round.minPenetrationLevel=0;round.maxPenetrationLevel=10;
        var noWear=ArmourBallistics.resolve(List.of(fixed),round,1,10,true,()->.5);check(noWear.stopped()&&noWear.impacts().getFirst().wear()==0,"nonbreakable plate protects without wear");
        var map=new EnumMap<EquipmentSlot,ArmorHurtEvent.ArmorEntry>(EquipmentSlot.class);
        map.put(EquipmentSlot.CHEST,new ArmorHurtEvent.ArmorEntry(fixed,8));map.put(EquipmentSlot.HEAD,new ArmorHurtEvent.ArmorEntry(new ItemStack(Items.IRON_HELMET),8));map.put(EquipmentSlot.FEET,new ArmorHurtEvent.ArmorEntry(stack,8));
        var damage=new net.minecraft.world.damagesource.DamageSource(Holder.direct(new net.minecraft.world.damagesource.DamageType("generic",0F)));
        var event=new ArmorHurtEvent(map,null,damage);ArmourProtection.armourWear(event);
        near(event.getNewDamage(EquipmentSlot.CHEST),0,"NeoForge per-slot unbreakable wear suppressed");near(event.getNewDamage(EquipmentSlot.HEAD),8,"other armour wear preserved");near(event.getNewDamage(EquipmentSlot.FEET),8,"ordinary non-projectile wear preserved");
        VerifyArmourItems.reduction();VerifyArmourItems.proofSounds();
        check(ArmourBallistics.resolve(List.of(fixed),null,1,10,true,()->0).damage()==10,"missing bullet bypass");
        check(!ArmourState.toggle(stack,"x",false),"non-mask equipment cannot toggle");
    }
    public static void main(String[] args)throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();AuditAllArmour.load(Path.of(args[0]));
        actual();plates();System.out.println("PASS VerifyAllArmour: "+checks+" assertions; plus production vertex/reduction assertions; no game/world launched.");
    }
}
