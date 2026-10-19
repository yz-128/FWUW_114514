import com.flansmod.common.*;
import com.flansmod.common.network.GunModificationPayload;
import com.flansmod.common.types.*;
import com.flansmod.common.types.ModifiableType.DefaultAttachment;
import com.flansmod.client.render.LegacySightPose;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import java.nio.file.Path;
import java.util.*;

/** Real item transactions and save/reload, in an isolated JVM; no game or desktop input. */
public final class VerifyGunModification {
    private static int checks;
    private static void check(boolean condition,String text) {checks++;if(!condition)throw new AssertionError(text);}
    private static final RegistryAccess REGISTRIES=RegistryAccess.EMPTY;
    private static void edit(Inventory inv,int op,int[] path,int slot,int source,int step,int offset) {
        var result=GunAttachmentTransaction.apply(inv,0,GunAttachments.revision(inv.getItem(0)),op,path,slot,source,step,offset,REGISTRIES);
        check(result==null,"accepted edit: "+result);
    }
    private static CompoundTag data(ItemStack stack) {var d=stack.get(DataComponents.CUSTOM_DATA);return d==null?new CompoundTag():d.copyTag();}
    private static GunType gun(String name) {return (GunType)InfoType.infoTypes.stream().filter(g->g instanceof GunType && name.equals(g.shortName)).findFirst().orElseThrow();}
    private static ItemStack stack(InfoType type) {return new ItemStack(type.item);}
    private static DefaultAttachment node(String name,int slot) {var node=new DefaultAttachment();node.name=name;node.slot=slot;return node;}
    private static AttachmentType part(String name,String... lines) {
        var file=new TypeFile(EnumType.attachment,name,false);file.lines.add("ShortName "+name);file.lines.addAll(List.of(lines));
        var type=new AttachmentType(file);type.read(file);register(type);return type;
    }
    private static int ids;
    private static void register(InfoType type) {Registry.register(BuiltInRegistries.ITEM,ResourceLocation.fromNamespaceAndPath("flansmod","modify_fixture_"+(ids++)),new ItemFlan(type,new Item.Properties()));}
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        ((net.minecraft.core.MappedRegistry<?>)BuiltInRegistries.ITEM).unfreeze();
        var pack=new OfflinePack(Path.of(args[0]));pack.readTypes();
        for(var type:pack.types)register(type);
        var bocchi=gun("BocchiTheGlock18C");var held=stack(bocchi);var twin=stack(bocchi);
        var inv=new Inventory(null);inv.selected=0;inv.setItem(0,held);
        int initial=GunAttachments.count(GunAttachments.read(bocchi,held));
        check(initial==2,"real Bocchi starts with its slide and Crossline");
        check(!data(held).contains(GunAttachments.KEY),"reads do not initialize or mutate item data");
        var custom=new CompoundTag();custom.putString("unrelated_fixture","retained");custom.putString(GunType.MODE_KEY,"SEMIAUTO");
        held.set(DataComponents.CUSTOM_DATA,CustomData.of(custom));
        var sightBefore=LegacySightPose.forGun(GunAttachments.view(bocchi,held));
        edit(inv,GunModificationPayload.REMOVE,new int[]{1,0},0,0,0,0);
        check(GunAttachments.count(GunAttachments.read(bocchi,held))==1,"remove one mounted real sight");
        check(GunAttachments.count(GunAttachments.read(bocchi,twin))==initial && bocchi.defaultAttachments.get(1).size()==1,"same gun definition and another gun remain untouched");
        check(LegacySightPose.forGun(GunAttachments.view(bocchi,held)).source().equals(bocchi.shortName),"ADS falls back to receiver after actual sight removal");
        check(data(held).getString("unrelated_fixture").equals("retained") && data(held).getString(GunType.MODE_KEY).equals("SEMIAUTO"),"edits preserve independent ammo/mode/custom keys");
        var returned=inv.getItem(1);check(InfoType.getType(returned)==AttachmentType.getAttachment("Crossline"),"the actual removed sight is returned");
        int revision=GunAttachments.revision(held);var before=data(held);
        check("stale".equals(GunAttachmentTransaction.apply(inv,0,revision-1,GunModificationPayload.REMOVE,new int[]{0,0},0,0,0,0,REGISTRIES)),"replayed stale request rejected");
        check(before.equals(data(held)) && inv.getItem(1)==returned,"stale request changes neither gun nor inventory");
        edit(inv,GunModificationPayload.INSTALL,new int[0],1,1,0,0);
        check(inv.getItem(1).isEmpty() && GunAttachments.count(GunAttachments.read(bocchi,held))==initial,"install consumes exactly its real source");
        check(LegacySightPose.forGun(GunAttachments.view(bocchi,held)).equals(sightBefore),"reinstalled actual sight restores ADS");
        var saved=ItemStack.parseOptional(REGISTRIES,(CompoundTag)held.save(REGISTRIES));
        check(GunAttachments.count(GunAttachments.read(bocchi,saved))==initial && data(saved).equals(data(held)),"item serialization retains exact loadout and other keys");
        for(int[] invalid:List.of(new int[]{-1,0},new int[]{0},new int[]{99,0},new int[18])) {
            var snapshot=data(held);
            check("invalid".equals(GunAttachmentTransaction.apply(inv,0,GunAttachments.revision(held),GunModificationPayload.REMOVE,invalid,0,0,0,0,REGISTRIES)),"invalid path rejected");
            check(snapshot.equals(data(held)),"invalid request is side-effect-free");
        }
        inv.selected=1;check("invalid".equals(GunAttachmentTransaction.apply(inv,0,GunAttachments.revision(held),GunModificationPayload.REMOVE,new int[]{0,0},0,0,0,0,REGISTRIES)),"changing selected slot rejects edits");inv.selected=0;
        for(int i=1;i<36;i++)inv.setItem(i,new ItemStack(Items.STONE,64));
        before=data(held);
        check("inventory_full".equals(GunAttachmentTransaction.apply(inv,0,GunAttachments.revision(held),GunModificationPayload.REMOVE,new int[]{0,0},0,0,0,0,REGISTRIES)),"full inventory does not lose detached parts");
        check(before.equals(data(held)),"failed removal leaves entire assembly intact");
        for(int i=1;i<36;i++)inv.setItem(i,ItemStack.EMPTY);
        edit(inv,GunModificationPayload.REMOVE,new int[]{0,0},0,0,0,0);
        edit(inv,GunModificationPayload.REMOVE,new int[]{1,0},0,0,0,0);
        check(GunAttachments.count(GunAttachments.read(bocchi,held))==0,"explicitly bare gun does not re-grow defaults");
        check(GunAttachments.count(GunAttachments.read(bocchi,ItemStack.parseOptional(REGISTRIES,(CompoundTag)held.save(REGISTRIES))))==0,"bare gun remains bare after save/load");

        var ak=gun("AK74M");var akStack=stack(ak);inv.setItem(0,akStack);var original=GunStats.effective(ak,akStack);
        edit(inv,GunModificationPayload.REMOVE,new int[]{2,0},0,0,0,0);
        check(GunStats.effective(ak,akStack).pitch()>original.pitch(),"removing real AK74M stock changes effective recoil");
        check(GunStats.effective(ak).equals(original),"base default recoil remains unchanged");

        // Nested parts round-trip as a single subassembly, including item components and placement.
        var child=part("ModifyChild","AttachmentType child","DamageMultiplier 2");
        var parent=part("ModifyParent","AttachmentType parent","AttachPoints 0 0 0","AttachVectors 2 4","TypeWhitelist child","MaxAttachments 2","AttachOffsets 0 8","Model none none 0.2");
        var fixtureFile=new TypeFile(EnumType.gun,"ModifyGun",false);
        fixtureFile.lines.addAll(List.of("ShortName ModifyGun","AttachPoints 0 0 0","AttachVectors 2 5","TypeWhitelist parent","MaxAttachments 1"));
        var fixture=new GunType(fixtureFile);fixture.read(fixtureFile);register(fixture);
        inv=new Inventory(null);inv.selected=0;held=stack(fixture);inv.setItem(0,held);
        var source=stack(parent);source.setCount(4);source.set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("kept name"));inv.setItem(1,source);
        inv.setItem(2,stack(child));
        check(Math.abs(parent.attachOffsets[1]-.1F)<.000001F,"AttachOffsets includes attachment model scale");
        edit(inv,GunModificationPayload.INSTALL,new int[0],0,1,3,1);
        check(source.getCount()==3,"stacked parts consume exactly one");
        edit(inv,GunModificationPayload.INSTALL,new int[]{0,0},0,2,2,0);
        check(GunAttachments.count(GunAttachments.read(fixture,held))==2 && GunStats.effective(fixture,held).damage()==fixture.damage*2F,"nested part contributes its multiplier");
        edit(inv,GunModificationPayload.MOVE,new int[]{0,0,0,0},0,0,4,0);
        var installed=GunAttachments.nodeAt(GunAttachments.read(fixture,held),new int[]{0,0,0,0});
        check(installed.step==4 && Math.abs(installed.pos-parent.slots[0].stepVector*4)<.000001F,"nested rail movement uses the parent's scaled rail");
        before=data(held);
        check("position".equals(GunAttachmentTransaction.apply(inv,0,GunAttachments.revision(held),GunModificationPayload.MOVE,new int[]{0,0,0,0},0,0,5,0,REGISTRIES)),"out-of-range rail move rejected");
        check(before.equals(data(held)),"bad rail move never changes data");
        edit(inv,GunModificationPayload.REMOVE,new int[]{0,0},0,0,0,0);
        var subassembly=inv.getItem(2);
        check(InfoType.getType(subassembly)==parent && GunAttachments.count(GunAttachments.read(parent,subassembly))==1,"detached parent retains exactly one mounted child");
        check(subassembly.getHoverName().getString().equals("kept name"),"detached part retains original item components");
        edit(inv,GunModificationPayload.INSTALL,new int[0],0,2,1,0);
        check(GunAttachments.count(GunAttachments.read(fixture,held))==2 && source.getCount()==3 && inv.getItem(2).isEmpty(),"reinstalling parent neither duplicates nor loses its child");
        check(GunAttachments.nodeAt(GunAttachments.read(fixture,held),new int[]{0,0,0,0}).step==4,"nested rail position survives detach/reinstall");
        check(GunAttachments.view(fixture,held).definition()==fixture,"assembly view retains stable registered identity");

        fixture.typeWhitelist.getFirst().clear();check(GunAttachments.allowed(fixture,0,child),"empty next-separated whitelist is unconstrained");
        fixture.typeBlacklist.add(new ArrayList<>(List.of("child")));check(!GunAttachments.allowed(fixture,0,child),"type blacklist uses AttachmentType");
        fixture.attachmentWhitelist.add(new ArrayList<>(List.of(child.shortName)));check(GunAttachments.allowed(fixture,0,child),"named whitelist precedes type blacklist");
        fixture.attachmentBlacklist.add(new ArrayList<>(List.of(child.shortName)));check(GunAttachments.allowed(fixture,0,child),"named whitelist precedes named blacklist");
        fixture.attachmentWhitelist.clear();check(!GunAttachments.allowed(fixture,0,child),"named blacklist precedes type allowance");
        fixture.attachmentBlacklist.clear();fixture.typeWhitelist.getFirst().add("parent");check(!GunAttachments.allowed(fixture,0,child),"unlisted type rejected regardless of ItsType");

        fixture.neededToShoot.add("child");check(!GunAttachments.requirements(fixture,held,0),"nested child cannot satisfy the gun's direct child requirement");
        fixture.neededToShoot.clear();parent.neededToShoot.add("child");check(GunAttachments.requirements(fixture,held,0),"part's direct child satisfies its own requirement");
        parent.neededToShoot.add("child");check(!GunAttachments.requirements(fixture,held,0),"repeated requirements need distinct parts");parent.neededToShoot.clear();
        fixture.itsType=GunType.GunItsType.AUTO_GUN;
        var roundType=(BulletType)pack.types.stream().filter(t->"5.45x39mm7n39".equals(t.shortName)).findFirst().orElseThrow();
        var magType=gun("6L20_545x39_30Rounds");var magazine=stack(magType);
        GunMagazine.set(magazine,magType,0,new ItemStack(roundType.item,2));GunMagazine.set(held,fixture,0,magazine);
        check(GunMagazine.ensureChamber(held,fixture),"fixture chambers one actual EFT round");
        fixture.neededToAuto.add("absent_cycle_part");
        var fired=GunMagazine.spendChamberedRound(held,fixture);
        check(!fired.isEmpty() && GunMagazine.getChamberedRound(held).isEmpty() && GunCasings.retained(held)!=null,"missing automatic-cycle part retains case and does not auto-feed");
        check(GunMagazine.roundsRemaining(GunMagazine.get(held,fixture,0))==1,"missing automatic cycling never consumes another round");
        fixture.neededToAuto.clear();GunReloading.charge(held,fixture,ignored->{});
        check(!GunMagazine.getChamberedRound(held).isEmpty() && GunCasings.retained(held)==null,"manual charge clears retained case and feeds actual remaining round");
        var codecBuffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),REGISTRIES);
        var request=new GunModificationPayload(7,123,GunModificationPayload.MOVE,new int[]{0,0,0,0},0,-1,3,1);
        GunModificationPayload.STREAM_CODEC.encode(codecBuffer,request);var decoded=GunModificationPayload.STREAM_CODEC.decode(codecBuffer);
        check(decoded.containerId()==7 && decoded.revision()==123 && Arrays.equals(decoded.path(),request.path()) && decoded.step()==3 && decoded.offset()==1,"nested edit network payload round-trip");codecBuffer.release();
        codecBuffer=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),REGISTRIES);
        GunModificationPayload.STREAM_CODEC.encode(codecBuffer,new GunModificationPayload(7,0,2,new int[18],0,0,0,0));
        boolean rejected=false;try{GunModificationPayload.STREAM_CODEC.decode(codecBuffer);}catch(io.netty.handler.codec.DecoderException expected){rejected=true;}
        check(rejected,"oversized network paths are bounded during decode");codecBuffer.release();
        var wrap=data(held);wrap.putInt(GunAttachments.REVISION,32767);held.set(DataComponents.CUSTOM_DATA,CustomData.of(wrap));
        GunAttachments.write(held,GunAttachments.read(fixture,held));check(GunAttachments.revision(held)==0,"revision stays representable in vanilla menu data shorts");
        System.out.println("PASS: "+checks+" assertions; actual EFT detach/install/save, same-type isolation, recoil/ADS, nested subassemblies and metadata, quantity conservation, stale/invalid/full-inventory rejection, rail/offset bounds, FMUM whitelist priority and per-layer requirements.");
    }
}
