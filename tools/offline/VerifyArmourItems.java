import com.flansmod.common.ArmourProtection;
import com.flansmod.common.ContentRegistry;
import com.flansmod.common.ItemAssets;
import com.flansmod.common.types.*;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.nio.file.Path;
import java.util.*;

/** Real EFT armour registration and equipment contracts, without a world or game client. */
public final class VerifyArmourItems {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static void near(double actual,double expected,String message){check(Double.isFinite(actual)&&Math.abs(actual-expected)<2E-5,message+": "+actual+" != "+expected);}
    static ResourceLocation id(String name){return ResourceLocation.fromNamespaceAndPath("flansmod",name);}
    static ItemFlanArmour item(String name){var type=ArmourType.getArmourType(name);check(type!=null,"real definition "+name);check(type.item instanceof ItemFlanArmour,"registered equipment "+name);return (ItemFlanArmour)type.item;}
    static EquipmentSlot slot(int legacy){return switch(legacy){case 0->EquipmentSlot.HEAD;case 2->EquipmentSlot.LEGS;case 3->EquipmentSlot.FEET;default->EquipmentSlot.CHEST;};}

    static void realItems()throws Exception {
        // Exercise the production registration factory, including its existing duplicate policy.
        var constructor=RegisterEvent.class.getDeclaredConstructor(ResourceKey.class,Registry.class);
        constructor.setAccessible(true);
        ContentRegistry.onRegister(constructor.newInstance(Registries.ITEM,BuiltInRegistries.ITEM));
        int parsed=0,registered=0,duplicates=0,damageable=0,cosmetic=0;
        var slots=new EnumMap<EquipmentSlot,Integer>(EquipmentSlot.class);
        for(var definition:InfoType.infoTypes)if(definition instanceof ArmourType armour){
            parsed++;
            String registry=ItemAssets.registryPathOf(armour);
            if(ContentRegistry.registered.get(registry)!=armour){
                duplicates++;
                check(armour.item==null,"later duplicate never steals the original item: "+registry);
                continue;
            }
            registered++;
            check(armour.item instanceof ItemFlanArmour,"factory creates an ArmorItem for "+armour.shortName);
            var item=(ItemFlanArmour)armour.item;
            var stack=new ItemStack(item);
            check(BuiltInRegistries.ITEM.get(id(registry))==item,"stable registry ID: "+registry);
            check(item.armour==armour&&InfoType.getType(stack)==armour,"definition lookup works through the new subclass");
            check(Equipable.get(stack)==item,"vanilla equipment discovery: "+armour.shortName);
            check(item.getEquipmentSlot()==slot(armour.type),"original equipment slot: "+armour.shortName);
            check(stack.getMaxStackSize()==1,"equipment is not stackable");
            check(stack.getMaxDamage()==Math.max(0,armour.durability),"exact pack durability: "+armour.shortName);
            check(stack.isDamageableItem()==(armour.durability>0),"cosmetic zero-durability equipment remains usable");
            if(armour.durability>0){
                damageable++;
                stack.setDamageValue(armour.durability/2);
                check(stack.copy().getDamageValue()==armour.durability/2,"wear stays on copied equipment");
            }else cosmetic++;
            near(item.getDefense(),0,"fractional Defence is not converted into armour points");
            near(item.getToughness(),0,"proof level is not invented toughness");
            check(item.getDescriptionId().equals(ItemAssets.packTranslationKey(armour,registry)),"original pack name key");
            check(!item.getName(stack).getString().isBlank(),"equipment keeps its display name");
            var layer=item.getMaterial().value().layers().getFirst();
            check(ItemFlanArmour.layerId(armour).equals(id("legacy/"+registry)),"shared generated material layer API");
            check(layer.texture(armour.type==2).equals(id("textures/models/armor/legacy/"+registry+"_layer_"+(armour.type==2?2:1)+".png")),"correct vanilla inner/outer texture alias");
            var attrs=item.getDefaultAttributeModifiers(stack);
            double[] movement={0},knockback={0};int[] held={0};
            attrs.forEach(item.getEquipmentSlot(),(attribute,modifier)->{
                if(attribute.equals(Attributes.MOVEMENT_SPEED)){
                    check(modifier.operation()==AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,"movement is a multiplier");movement[0]+=modifier.amount();
                }
                if(attribute.equals(Attributes.KNOCKBACK_RESISTANCE)){
                    check(modifier.operation()==AttributeModifier.Operation.ADD_VALUE,"knockback matches original additive attribute");knockback[0]+=modifier.amount();
                }
                if(attribute.equals(Attributes.ARMOR)||attribute.equals(Attributes.ARMOR_TOUGHNESS))near(modifier.amount(),0,"no second armour-point reduction");
            });
            attrs.forEach(EquipmentSlot.MAINHAND,(attribute,modifier)->held[0]++);
            near(movement[0],armour.moveSpeedModifier-1,"real pack movement modifier");
            near(knockback[0],armour.knockbackModifier,"real pack knockback modifier");
            check(held[0]==0,"holding armour never applies worn attributes");
            slots.merge(item.getEquipmentSlot(),1,Integer::sum);
        }
        check(parsed==80,"both complete real packs parsed, 53 base plus 27 Scav");
        check(registered+duplicates==parsed,"all source definitions accounted for");
        check(slots.size()==4,"all four legacy equipment slots are represented");
        check(cosmetic>0&&damageable>0,"both real protective and cosmetic equipment covered");
        check(item("6B43").getEquipmentSlot()==EquipmentSlot.CHEST,"6B43 goes into chest slot");
        near(new ItemStack(item("6B43")).getMaxDamage(),85,"6B43 original 85 durability");
        check(item("ComTac2HeadsetFDE").getEquipmentSlot()==EquipmentSlot.LEGS,"legacy headset keeps Pants slot");
        check(item("FastMT").getEquipmentSlot()==EquipmentSlot.HEAD,"helmet goes into head slot");
        near(ArmourProtection.displayPoints(item("6B43").armour.defence),7,"real parsed .35 retains exact legacy display precision");
        near(ArmourProtection.displayPoints(item("FastMT").armour.defence),6,"real parsed .3 displays six armour points");
        System.out.printf(Locale.ROOT,"Real armour: parsed=%d registered=%d first-wins duplicates=%d damageable=%d cosmetic=%d slots=%s%n",parsed,registered,duplicates,damageable,cosmetic,slots);
    }

    static void reduction(){
        near(ArmourProtection.displayPoints(.35D),7,"legacy Defence displays seven points without adding actual armour");
        near(ArmourProtection.displayPoints(.3D),6,"helmet visual armour matches original getArmorDisplay");
        near(ArmourProtection.displayPoints(Double.NaN),0,"invalid armour display stays finite");
        near(ArmourProtection.displayPoints(2),20,"visual armour is bounded");
        near(ArmourProtection.reduction(100,0,.35),35,"6B43 provides its declared 35 percent reduction");
        near(ArmourProtection.reduction(100,0,.35+.3),65,"6B43 and FastMT add their original fractions");
        near(ArmourProtection.reduction(100,20,.35),48,"other armour reduction is preserved before pack percentage");
        near(ArmourProtection.reduction(100,0,1.2),100,"sum above one cannot heal");
        near(ArmourProtection.reduction(100,0,-1),0,"negative pack Defence cannot amplify damage");
        near(ArmourProtection.reduction(100,0,Double.NaN),0,"invalid fraction does not poison damage");
        near(ArmourProtection.reduction(100,Float.NaN,.35),35,"invalid prior reduction remains finite");
        near(ArmourProtection.reduction(0,100,.35),0,"zero hit does not create damage");
        // Execute NeoForge's actual deferred ARMOR stage, then effects and absorption.
        var sequence=new DamageContainer(null,100);
        sequence.addModifier(DamageContainer.Reduction.ARMOR,(container,old)->ArmourProtection.reduction(container.getNewDamage(),old,.35));
        sequence.setReduction(DamageContainer.Reduction.ARMOR,20);
        near(sequence.getNewDamage(),52,"armour-stage callback really reduces incoming damage");
        sequence.setReduction(DamageContainer.Reduction.MOB_EFFECTS,sequence.getNewDamage()*.2F);
        near(sequence.getNewDamage(),41.6,"resistance applies after percentage armour");
        sequence.setReduction(DamageContainer.Reduction.ABSORPTION,10);
        near(sequence.getNewDamage(),31.6,"absorption consumes only remaining damage");
    }

    static void proofSounds(){
        var file=new TypeFile(EnumType.armour,"proofSoundFixture");
        file.lines.addAll(List.of("ShortName proofSoundFixture","Type Shoes",
            "HeadProofLevel 10 head 2 3 headPen 4 5","BodyProofLevel 20 body 6 7 bodyPen 8 9",
            "LeftArmProofLevel 30 left 10 11 leftPen 12 13","RightArmProofLevel 40 right 14 15 rightPen 16 17"));
        var armour=new ArmourType(file);armour.read(file);
        check(armour.type==3,"original Shoes spelling accepted");
        check(armour.hitSounds.length==8,"independent blocked and penetrating body-part sounds");
        String[] sounds={"head","body","left","right","headPen","bodyPen","leftPen","rightPen"};
        int[] counts={2,6,10,14,4,8,12,16};float[] ranges={3,7,11,15,5,9,13,17};
        for(int i=0;i<8;i++){
            check(sounds[i].equals(armour.hitSounds[i]),"sound index never overwrites adjacent body part: "+i);
            check(counts[i]==armour.numHitSounds[i],"sound count "+i);
            near(armour.hitSoundRanges[i],ranges[i],"sound range "+i);
        }
    }

    public static void main(String[] args)throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        ((MappedRegistry<?>)BuiltInRegistries.ITEM).unfreeze();
        for(String path:args)new OfflinePack(Path.of(path)).readTypes();
        realItems();reduction();proofSounds();
        System.out.println("PASS: "+checks+" assertions; production registration, 80 actual EFT/Scav definitions, vanilla equipment discovery and slots, durability, worn-only attributes, texture layer contracts, real NeoForge reduction stages and independent proof sounds. Offline only; no game/client/world launched.");
    }
}
