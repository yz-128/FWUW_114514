import com.flansmod.common.*;
import com.flansmod.common.network.GunModificationPayload;
import com.flansmod.common.types.*;
import com.flansmod.common.types.ModifiableType.DefaultAttachment;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import java.nio.file.Path;
import java.util.*;

/** Complete stored subassemblies, occupied positions, prerequisites and real standalone parts. */
public final class VerifyAttachmentAssemblies {
    static int checks,ids;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static void register(InfoType type){Registry.register(BuiltInRegistries.ITEM,ResourceLocation.fromNamespaceAndPath("flansmod","assembly_test_"+(ids++)),new ItemFlan(type,new Item.Properties()));}
    static AttachmentType part(String name,String...lines){var f=new TypeFile(EnumType.attachment,name,false);f.lines.add("ShortName "+name);f.lines.addAll(List.of(lines));var t=new AttachmentType(f);t.read(f);register(t);return t;}
    static GunType gun(String name){var f=new TypeFile(EnumType.gun,name,false);f.lines.addAll(List.of("ShortName "+name,"AttachPoints 0 0 0","AttachVectors 1 10","MaxAttachments 64"));var t=new GunType(f);t.read(f);register(t);return t;}
    static DefaultAttachment node(AttachmentType type,int slot,int step){var n=new DefaultAttachment();n.name=type.shortName;n.slot=slot;n.step=step;return n;}
    static List<List<DefaultAttachment>> empty(GunType gun){return GunAttachments.copy(gun,List.of());}
    public static void main(String[]args)throws Exception{
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        ((net.minecraft.core.MappedRegistry<?>)BuiltInRegistries.ITEM).unfreeze();
        var gun=gun("AssemblyHost");var rail=part("AssemblyRail","AttachmentType rail");
        var tree=empty(gun);
        for(int i=0;i<5;i++){check(GunAttachments.canInstall(gun,tree,new int[0],0,rail,i,0)==null,"distinct positions permitted");GunAttachments.add(tree,new int[0],node(rail,0,i));}
        check("full".equals(GunAttachments.canInstall(gun,tree,new int[0],0,rail,5,0)),"original global five-per-slot cap applies to omitted/local64 maximum");
        tree=empty(gun);GunAttachments.add(tree,new int[0],node(rail,0,2));
        check("duplicate".equals(GunAttachments.canInstall(gun,tree,new int[0],0,rail,2,0)),"same part at same physical mount cannot duplicate");
        check(GunAttachments.canInstall(gun,tree,new int[0],0,rail,3,0)==null,"same rail type at a different notch stays valid");
        var grip=part("AssemblyGrip","AttachmentType handle","ItsType grip");
        var parent=part("AssemblyParent","AttachmentType frame","AttachPoints 0 0 0","TypeWhitelist handle","MaxAttachments 1");
        var frame=new ItemStack(parent.item);var child=node(grip,0,0);GunAttachments.write(frame,List.of(List.of(child)));
        tree=empty(gun);
        check(GunAttachments.canInstallAssembly(gun,tree,new int[0],0,frame,0,0)==null,"valid saved parent and child install together");
        GunAttachments.add(tree,new int[0],node(grip,0,1));
        check("grip".equals(GunAttachments.canInstallAssembly(gun,tree,new int[0],0,frame,0,0)),"saved nested grip cannot bypass whole-gun unique grip rule");
        check(GunAttachments.count(tree)==1 && GunAttachments.count(GunAttachments.read(parent,frame))==1,"validation mutates neither gun nor component");
        var held=new ItemStack(gun.item);GunAttachments.write(held,tree);var inv=new Inventory(null);inv.selected=0;inv.setItem(0,held);inv.setItem(1,frame);
        int revision=GunAttachments.revision(held);
        check("grip".equals(GunAttachmentTransaction.apply(inv,0,revision,GunModificationPayload.INSTALL,new int[0],0,1,0,0,RegistryAccess.EMPTY)),"server rejects entire conflicting assembly");
        check(frame.getCount()==1 && GunAttachments.revision(held)==revision && GunAttachments.count(GunAttachments.read(gun,held))==1,"rejected install consumes and changes nothing");
        var wrong=part("AssemblyWrong","AttachmentType incompatible_child");
        GunAttachments.write(frame,List.of(List.of(node(wrong,0,0))));
        check("incompatible".equals(GunAttachments.canInstallAssembly(gun,empty(gun),new int[0],0,frame,0,0)),"saved children must fit their actual immediate parent");
        check("invalid".equals(GunAttachments.canInstallAssembly(gun,empty(gun),new int[]{0,0},0,new ItemStack(grip.item),0,0)),"missing prerequisite parent never exposes a usable child mount");
        var rear=part("AssemblyRear","ItsType reargrip");var alias=part("AssemblyRearAlias","ItsType rear_grip");
        tree=empty(gun);GunAttachments.add(tree,new int[0],node(rear,0,1));
        check("grip".equals(GunAttachments.canInstall(gun,tree,new int[0],0,alias,2,0)),"rear_grip and reargrip spellings share one constraint");
        tree=empty(gun);GunAttachments.add(tree,new int[0],node(rail,0,1));GunAttachments.add(tree,new int[0],node(rail,0,2));GunAttachments.write(held,tree);
        check("duplicate".equals(GunAttachmentTransaction.apply(inv,0,GunAttachments.revision(held),GunModificationPayload.MOVE,new int[]{0,1},0,-1,1,0,RegistryAccess.EMPTY)),"moving cannot duplicate an existing identical placement");
        check(GunAttachments.nodeAt(GunAttachments.read(gun,held),new int[]{0,1}).step==2,"failed movement keeps old placement");
        if(args.length>0){
            var pack=new OfflinePack(Path.of(args[0]));pack.readTypes();for(var t:pack.types)register(t);
            var mdr=AttachmentType.getAttachment("DTMDR762DefaultBarrel");
            var muzzle=AttachmentType.getAttachment("DTMDR762Muzzle");
            check(GunAttachments.allowed(mdr,0,muzzle),"declared EFT MDR default muzzle can be installed back on its original barrel");
            check(!GunAttachments.allowed(AttachmentType.getAttachment("16inchM1ABarrel"),0,muzzle),"specific MDR repair never grants unrelated barrel interfaces");
            var originalPack=mdr.contentPack;mdr.contentPack="CustomPack";
            check(!GunAttachments.allowed(mdr,0,muzzle),"other content packs keep their own declared rules");mdr.contentPack=originalPack;
            mdr.attachmentBlacklist.add(new ArrayList<>(List.of(muzzle.shortName)));
            check(!GunAttachments.allowed(mdr,0,muzzle),"explicit named blacklist still wins over known authored-pair repair");mdr.attachmentBlacklist.clear();
            int standalone=0;
            for(var t:pack.types)if(t instanceof AttachmentType at && GunAttachments.count(at.defaultAttachments)>0){
                String reason=GunAttachments.canInstallAssembly(gun,empty(gun),new int[0],0,new ItemStack(at.item),0,0);
                check(reason==null,"actual standalone default subassembly "+at.shortName+": "+reason);standalone++;
            }
            var barrel=part("ReturnBarrel","AttachmentType innerBarrel","ItsType barrel");
            var bolt=part("ReturnBolt","AttachmentType innerBolt","ItsType bolt");
            var carrier=part("ReturnCarrier","AttachPoints 0 0 0 8 0 0","TypeWhitelist innerBarrel next innerBolt","MaxAttachments 1 1");
            var assembly=node(carrier,0,0);assembly.children.add(node(barrel,0,0));assembly.children.add(node(bolt,1,0));
            var ammo=(BulletType)pack.types.stream().filter(t->t instanceof BulletType && t.shortName.equals("5.45x39mm7n39")).findFirst().orElseThrow();
            held=new ItemStack(gun.item);inv.setItem(0,held);for(int i=1;i<36;i++)inv.setItem(i,ItemStack.EMPTY);
            GunAttachments.write(held,List.of(List.of(assembly)));GunMagazine.setChamberedRound(held,new ItemStack(ammo.item));GunActions.boltCaught(held,true);
            check(GunAttachmentTransaction.apply(inv,0,GunAttachments.revision(held),GunModificationPayload.REMOVE,new int[]{0,0},0,-1,0,0,RegistryAccess.EMPTY)==null,"remove parent containing a barrel and bolt");
            check(GunMagazine.getChamberedRound(held).isEmpty() && !GunActions.boltCaught(held),"nested barrel ejects chamber and nested bolt clears catch");
            int rounds=0,components=0;
            for(int i=1;i<36;i++){var item=inv.getItem(i);if(InfoType.getType(item)==ammo)rounds+=item.getCount();if(InfoType.getType(item)==carrier){components++;check(GunAttachments.count(GunAttachments.read(carrier,item))==2,"returned carrier retains both children exactly once");}}
            check(rounds==1 && components==1,"removed assembly and live chambered round are both conserved");
            GunAttachments.write(held,List.of(List.of(assembly)));GunMagazine.setChamberedRound(held,new ItemStack(ammo.item));GunActions.boltCaught(held,true);
            for(int i=1;i<36;i++)inv.setItem(i,new ItemStack(Items.STONE,64));inv.setItem(1,ItemStack.EMPTY);
            int beforeRevision=GunAttachments.revision(held);
            check("inventory_full".equals(GunAttachmentTransaction.apply(inv,0,beforeRevision,GunModificationPayload.REMOVE,new int[]{0,0},0,-1,0,0,RegistryAccess.EMPTY)),"no drop context and only one free slot rejects whole two-item return");
            check(GunAttachments.revision(held)==beforeRevision && GunAttachments.count(GunAttachments.read(gun,held))==3,"failed return leaves exact assembly and revision intact");
            check(GunMagazine.getChamberedRound(held).getCount()==1 && GunActions.boltCaught(held) && inv.getItem(1).isEmpty(),"failed return preserves chamber, bolt catch and destination inventory");
            inv.setItem(2,new ItemStack(ammo.item,ammo.item.getDefaultMaxStackSize()-1));
            check(GunAttachmentTransaction.apply(inv,0,beforeRevision,GunModificationPayload.REMOVE,new int[]{0,0},0,-1,0,0,RegistryAccess.EMPTY)==null,"one empty component slot plus one matching partial ammo stack succeeds");
            check(inv.getItem(2).getCount()==ammo.item.getDefaultMaxStackSize() && InfoType.getType(inv.getItem(1))==carrier,"planned return merges the round and keeps full component");
            System.out.println("Actual standalone EFT subassemblies: "+standalone);
        }
        System.out.println("PASS VerifyAttachmentAssemblies: "+checks+" assertions");
    }
}
