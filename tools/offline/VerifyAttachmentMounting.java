import com.flansmod.common.*;
import com.flansmod.common.types.*;
import com.flansmod.common.types.ModifiableType.DefaultAttachment;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.nio.file.Path;
import java.util.*;

/** Real M14/SASS interfaces, unambiguous GUI selection and complete default-part round trips. */
public final class VerifyAttachmentMounting {
    static int checks,ids,roundTrips,attachmentRoundTrips;
    static final RegistryAccess REGISTRIES=RegistryAccess.EMPTY;
    static void check(boolean value,String message) {checks++;if(!value)throw new AssertionError(message);}
    static AttachmentType at(String name) {return Objects.requireNonNull(AttachmentType.getAttachment(name),name);}
    static GunType gun(OfflinePack pack,String name) {return (GunType)pack.types.stream().filter(t->t instanceof GunType && name.equals(t.shortName)).findFirst().orElseThrow();}
    static void register(InfoType type) {Registry.register(BuiltInRegistries.ITEM,ResourceLocation.fromNamespaceAndPath("flansmod","mount_fixture_"+(ids++)),new ItemFlan(type,new Item.Properties()));}
    static AttachmentType fixture(String name,String... lines) {
        var f=new TypeFile(EnumType.attachment,name,false);f.lines.add("ShortName "+name);f.lines.addAll(List.of(lines));
        var type=new AttachmentType(f);type.read(f);register(type);return type;
    }
    static DefaultAttachment node(AttachmentType type,int slot) {var n=new DefaultAttachment();n.name=type.shortName;n.slot=slot;return n;}
    static boolean tag(List<String> tags,String expected) {return tags.stream().anyMatch(t->t.equalsIgnoreCase(expected));}
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();((net.minecraft.core.MappedRegistry<?>)BuiltInRegistries.ITEM).unfreeze();
        var pack=new OfflinePack(Path.of(args[0]));pack.readTypes();
        // Also verifies that finalization is idempotent after the real loader hook.
        for(var type:pack.types) {if(type instanceof ModifiableType owner)owner.resolveDefaultAttachments();if(type instanceof GunType || type instanceof AttachmentType)register(type);}
        var m1a=gun(pack,"M1A");var frame=at("M14LCS");var stock=at("MK14LCSStock");var grip=at("MK14LCSGrip");var sass=at("M1ASASS");
        check(GunAttachments.allowed(m1a,0,frame),"M1AStock accepts M14LCS frame");
        check(!GunAttachments.allowed(m1a,0,stock) && !GunAttachments.allowed(m1a,0,grip),"LCS stock and grip cannot bypass their prerequisite frame");
        check(GunAttachments.allowed(frame,0,stock) && !GunAttachments.allowed(frame,0,grip),"M14LCS accepts the stock, not its grip directly");
        check(GunAttachments.allowed(stock,0,grip),"MK14LCSStock provides the actual MK14LCSGrip interface");
        check(tag(sass.typeWhitelist.get(0),"ARPistolGrip") && tag(sass.typeWhitelist.get(3),"BufferTube"),"SASS exposes AR grip and buffer tube interfaces");
        check(!GunAttachments.allowed(sass,0,grip) && !GunAttachments.allowed(sass,3,stock),"SASS never silently accepts incompatible LCS parts");
        var catalog=new GunAttachmentCatalog(AttachmentType.attachments);
        check(catalog.parents(stock).stream().anyMatch(p->p.parent()==frame && p.slot()==0),"stock parent hint names the real M14LCS mount");
        check(catalog.parents(grip).getFirst().parent()==stock && catalog.parents(grip).getFirst().slot()==0,"grip parent hint prioritizes the explicit MK14LCSStock interface");
        check(GunAttachmentCatalog.directParents(grip).stream().noneMatch(p->p.parent()==sass),"shared server-side tooltip query does not imply SASS compatibility");

        var tree=GunAttachments.copy(m1a,m1a.defaultAttachments);tree.get(0).clear();
        check(GunAttachmentCatalog.treeEntries(m1a,tree).stream().noneMatch(row->row.owner()==frame || row.owner()==stock),"uninstalled prerequisites expose no virtual child slots");
        var chassis=node(frame,0);tree.get(0).add(chassis);
        check(GunAttachmentCatalog.treeEntries(m1a,tree).stream().anyMatch(row->row.owner()==frame && row.slot()==0 && row.node()==null),"mounting the actual frame opens its stock mount");
        check(GunAttachmentCatalog.treeEntries(m1a,tree).stream().noneMatch(row->row.owner()==stock),"uninstalled stock still exposes no virtual grip slot");
        var mountedStock=node(stock,0);chassis.children.add(mountedStock);mountedStock.children.add(node(grip,0));
        var rows=GunAttachmentCatalog.treeEntries(m1a,tree);
        check(rows.stream().anyMatch(row->row.owner()==stock && row.slot()==0 && row.node()==null),"mounted stock opens its nested grip slot");
        var keys=new HashSet<String>();for(var row:rows)if(row.node()==null)check(keys.add(Arrays.toString(row.parentPath())+"#"+row.slot()),"actual mount address appears only once");
        check(Arrays.equals(GunAttachmentCatalog.selectedRemovalPath(tree,new int[0],0,null),new int[]{0,0}),"selecting a single occupied root mount resolves its unique assembly");
        check(Arrays.equals(GunAttachmentCatalog.selectedRemovalPath(tree,new int[]{0,0,0,0},0,null),new int[]{0,0,0,0,0,0}),"nested unique selection uses per-slot indices");
        var preview=GunAttachmentCatalog.previewBase(m1a,tree,new int[0],0,null);
        check(preview.get(0).isEmpty() && chassis.children.size()==1 && mountedStock.children.size()==1,"replacement preview retains the entire original subtree");
        check("full".equals(GunAttachments.canInstall(m1a,tree,new int[0],0,sass,0,0)),"real replacement remains blocked until the old assembly is removed");
        check(GunAttachments.canInstall(m1a,preview,new int[0],0,sass,0,0)==null,"empty preview target accepts the compatible alternative");
        check(catalog.compatible(m1a,0).contains(sass),"full mount alternatives remain in the direct-fit catalogue");
        var removed=GunAttachments.remove(tree,new int[]{0,0});var detached=GunAttachments.item(removed,REGISTRIES);
        check(GunAttachments.count(GunAttachments.read(frame,detached))==2,"real detach serializes stock and grip together in the frame item");
        var restored=GunAttachments.mounted(m1a,0,detached,0,0,REGISTRIES);GunAttachments.add(tree,new int[0],restored);
        check(GunAttachments.count(List.of(List.of(restored)))==3,"reattaching the frame retains exactly one stock and one grip");
        GunAttachments.remove(tree,new int[]{0,0});
        check(GunAttachmentCatalog.treeEntries(m1a,tree).stream().noneMatch(row->row.owner()==frame || row.owner()==stock),"removing a parent rebuilds GUI rows without stale child mounts");
        var sassNode=node(sass,0);tree.get(0).add(sassNode);
        var sassRows=GunAttachmentCatalog.treeEntries(m1a,tree).stream().filter(row->row.owner()==sass && row.node()==null).toList();
        check(sassRows.size()==sass.slots.length && sassRows.size()==7,"all seven real SASS rails remain independent");
        check(tag(sass.typeWhitelist.get(4),"left") && tag(sass.typeWhitelist.get(5),"right") && tag(sass.typeWhitelist.get(2),"under")
                && tag(sass.typeWhitelist.get(1),"upper"),"full slot categories retain left/right/lower/upper labels");
        check(sassRows.stream().filter(row->tag(sass.typeWhitelist.get(row.slot()),"right")).count()==2,"two physically separate right rails are preserved, not merged as duplicates");
        var arbitrary=at("Crossline");sassNode.children.add(node(arbitrary,1));sassNode.children.add(node(arbitrary,2));sassNode.children.add(node(arbitrary,1));
        check(GunAttachmentCatalog.selectedRemovalPath(tree,new int[]{0,0},1,null).length==0,"multiple parts in one slot never choose a removal target implicitly");
        check(Arrays.equals(GunAttachmentCatalog.selectedRemovalPath(tree,new int[]{0,0},1,new int[]{0,0,1,1}),new int[]{0,0,1,1}),"explicit sibling uses index within its own slot, not flattened child order");
        check(GunAttachmentCatalog.selectedRemovalPath(tree,new int[]{0,0},1,new int[]{0,0,2,0}).length==0,"explicit target from another slot cannot be substituted");
        var fullMany=GunAttachments.copy(m1a,tree);sass.slots[1].maxCanAttach=2;
        var ambiguous=GunAttachmentCatalog.previewBase(m1a,fullMany,new int[]{0,0},1,null);
        check(GunAttachments.nodeAt(ambiguous,new int[]{0,0}).children.size()==3,"ambiguous multi-part replacement preview never hides an arbitrary part");
        sass.slots[1].maxCanAttach=5;

        var forward=fixture("MountForwardParent","AttachPoints 0 0 0","AttachVectors 2 5","Model none none .2",
                "DefaultAttachments MountForwardChild 2 1");
        check(GunAttachments.count(forward.defaultAttachments)==0,"fixture demonstrates unresolved forward reference before finalization");
        var child=fixture("MountForwardChild","AttachOffsets 0 8","Model none none .2");
        float vector=forward.slots[0].stepVector;forward.resolveDefaultAttachments();
        check(forward.defaultAttachments.getFirst().getFirst().name.equals(child.shortName),"late attachment reference resolves after every definition is registered");
        float position=forward.defaultAttachments.getFirst().getFirst().pos;
        check(Math.abs(position-(vector*2+child.attachOffsets[1]))<.000001F,"resolved default uses already scaled owner rail and child offset");
        forward.resolveDefaultAttachments();check(GunAttachments.count(forward.defaultAttachments)==1 && forward.slots[0].stepVector==vector
                && forward.defaultAttachments.getFirst().getFirst().pos==position,"finalization neither duplicates defaults nor rescales geometry");
        var bare=new ItemStack(forward.item);GunAttachments.write(bare,List.of(new ArrayList<>()));forward.resolveDefaultAttachments();
        check(GunAttachments.count(GunAttachments.read(forward,bare))==0,"saved explicit empty trees never grow defaults back");
        var cycleA=fixture("MountCycleA","AttachPoints 0 0 0","DefaultAttachments MountCycleB");
        var cycleB=fixture("MountCycleB","AttachPoints 0 0 0","DefaultAttachments MountCycleA");
        cycleA.resolveDefaultAttachments();cycleB.resolveDefaultAttachments();
        check(GunAttachments.count(cycleA.defaultAttachments)==1 && GunAttachments.count(cycleB.defaultAttachments)==1,"cyclic names do not recursively expand implicit default dependencies");
        var huge=fixture("MountBounded","AttachPoints 0 0 0","DefaultAttachments "+String.join(" | ",Collections.nCopies(300,child.shortName)));
        huge.resolveDefaultAttachments();check(GunAttachments.count(huge.defaultAttachments)==GunAttachments.MAX_NODES,"explicit defaults obey global node budget");
        String deep=child.shortName;for(int i=1;i<16;i++)deep=child.shortName+" [ "+deep+" ]";
        var boundedDepth=fixture("MountDepthBounded","AttachPoints 0 0 0","DefaultAttachments "+deep);
        boundedDepth.resolveDefaultAttachments();
        check(GunAttachments.count(boundedDepth.defaultAttachments)==GunAttachments.MAX_DEPTH,"explicit nested default brackets obey the eight-node depth budget");
        var deepPart=fixture("MountDeepPart","AttachPoints 0 0 0");var deepRoot=node(deepPart,0);var cursor=deepRoot;
        for(int i=1;i<GunAttachments.MAX_DEPTH;i++){var next=node(deepPart,0);cursor.children.add(next);cursor=next;}
        var deepGun=new GunType(new TypeFile(EnumType.gun,"MountDeepGun",false));deepGun.slots=deepPart.slots;
        check(GunAttachmentCatalog.treeEntries(deepGun,List.of(List.of(deepRoot))).stream().filter(row->row.node()!=null).count()==8,
                "actual GUI rows retain every installed node through all eight legal levels, independent of indentation depth");
        var groups=fixture("MountSlotGroups","AttachPoints 0 0 0 0 0 0 0 0 0","TypeWhitelist next next MountGroupTag","MaxAttachments 1 2");
        check(groups.typeWhitelist.size()==3 && groups.typeWhitelist.get(0).isEmpty() && groups.typeWhitelist.get(1).isEmpty()
                && groups.typeWhitelist.get(2).equals(List.of("MountGroupTag")),"empty next groups preserve exact subsequent slot indices");
        check(GunAttachments.allowed(groups,0,child) && GunAttachments.allowed(groups,1,child) && !GunAttachments.allowed(groups,2,child),"empty groups keep original unconstrained semantics without shifting the constrained slot");
        check(GunAttachments.slotCapacity(groups,0)==1 && GunAttachments.slotCapacity(groups,1)==2
                && GunAttachments.slotCapacity(groups,2)==GunAttachments.MAX_PER_SLOT,"per-slot capacities combine explicit limits with the original global cap");
        check(GunAttachments.count(at("16inchM1ABarrel").defaultAttachments)==1,"real early-read M1A barrel retains its later-defined default muzzle brake");
        var blocked=new ArrayList<String>();
        for(var type:pack.types)if(type instanceof GunType g)audit(g,g.defaultAttachments,new int[0],blocked);
        System.out.println("Audited actual gun default mounts: "+roundTrips+", rejected reinstalls: "+blocked.size());
        int gunRoundTrips=roundTrips;
        for(var type:pack.types)if(type instanceof AttachmentType part && GunAttachments.count(part.defaultAttachments)>0) {
            // A standalone receiver with the exact owner's slots/rules isolates each real part's
            // authored defaults; nested owners still resolve to their actual registered definitions.
            var owner=new GunType(new TypeFile(EnumType.gun,"mount_owner_"+part.shortName,false));owner.shortName=part.shortName;
            owner.slots=part.slots;owner.contentPack=part.contentPack;
            owner.typeWhitelist.addAll(part.typeWhitelist);owner.typeBlacklist.addAll(part.typeBlacklist);
            owner.attachmentWhitelist.addAll(part.attachmentWhitelist);owner.attachmentBlacklist.addAll(part.attachmentBlacklist);
            audit(owner,GunAttachments.copy(part,part.defaultAttachments),new int[0],blocked);
        }
        attachmentRoundTrips=roundTrips-gunRoundTrips;
        System.out.println("Audited actual attachment default mounts: "+attachmentRoundTrips+", total rejected reinstalls: "+blocked.size());
        for(String rejected:blocked)System.out.println("DEFAULT REINSTALL REJECTED "+rejected);
        check(blocked.isEmpty(),"all actual parsed gun and attachment defaults can be removed and reinstalled under the precise compatibility rules");
        System.out.println("PASS VerifyAttachmentMounting: "+checks+" checks");
    }
    static void audit(GunType gun,List<List<DefaultAttachment>> tree,int[] parent,List<String> blocked) {
        var owner=GunAttachments.ownerAt(gun,tree,parent);if(owner==null)return;
        for(int slot=0;slot<owner.slots.length;slot++) {
            int index=0;for(var original:GunAttachments.children(tree,parent))if(original.slot==slot) {
                int[] path=Arrays.copyOf(parent,parent.length+2);path[parent.length]=slot;path[parent.length+1]=index++;
                var copy=GunAttachments.copy(gun,tree);var removed=GunAttachments.remove(copy,path);var part=at(removed.name);
                String reason=GunAttachments.canInstall(gun,copy,parent,slot,part,removed.step,removed.offset);
                if(reason!=null)blocked.add(gun.shortName+" "+Arrays.toString(path)+" "+part.shortName+" "+reason
                        +" [ownerPack="+owner.contentPack+", partPack="+part.contentPack+", interface="+part.attachmentType+"]");
                var item=GunAttachments.item(removed,REGISTRIES);check(!item.isEmpty(),"default node serializes to an actual registered part");
                if(reason==null) {
                    var assemblyError=GunAttachments.canInstallAssembly(gun,copy,parent,slot,item,removed.step,removed.offset);
                    if(assemblyError!=null)blocked.add(gun.shortName+" "+Arrays.toString(path)+" "+part.shortName+" assembly."+assemblyError);
                }
                var restored=GunAttachments.mounted(owner,slot,item,removed.step,removed.offset,REGISTRIES);
                check(restored.name.equals(removed.name) && GunAttachments.count(List.of(List.of(restored)))==GunAttachments.count(List.of(List.of(removed))),"default part round trip retains its entire child assembly exactly once");
                check(restored.step==removed.step && restored.offset==removed.offset && Math.abs(restored.pos-removed.pos)<.000001F,"default part retains rail placement");
                roundTrips++;audit(gun,tree,path,blocked);
            }
        }
    }
}
