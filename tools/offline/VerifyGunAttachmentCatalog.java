import com.flansmod.common.GunAttachmentCatalog;
import com.flansmod.common.GunAttachments;
import com.flansmod.common.types.*;
import com.flansmod.common.types.ModifiableType.DefaultAttachment;
import java.nio.file.Path;
import java.util.*;

/** Direct-fit catalogue and non-mutating replacement previews; no game, inventory or desktop. */
public final class VerifyGunAttachmentCatalog {
    static int checks;
    static void check(boolean value,String message) {checks++;if(!value)throw new AssertionError(message);}
    static AttachmentType part(String name,String... lines) {
        var f=new TypeFile(EnumType.attachment,name,false);f.lines.add("ShortName "+name);f.lines.addAll(List.of(lines));
        var type=new AttachmentType(f);type.read(f);return type;
    }
    static GunType gun(String name,String... lines) {
        var f=new TypeFile(EnumType.gun,name,false);f.lines.add("ShortName "+name);f.lines.addAll(List.of(lines));
        var type=new GunType(f);type.read(f);return type;
    }
    static DefaultAttachment node(String name,int slot) {var n=new DefaultAttachment();n.name=name;n.slot=slot;return n;}
    public static void main(String[] args) throws Exception {
        var optic=part("CatalogOptic","AttachmentType optic");
        var adapter=part("CatalogAdapter","AttachmentType adapter","AttachPoints 0 0 0","TypeWhitelist optic","MaxAttachments 1");
        var second=part("CatalogAdapter2","AttachmentType adapter");
        var unrelated=part("CatalogUnrelated","AttachmentType muzzle");
        var root=gun("CatalogGun","AttachPoints 0 0 0","TypeWhitelist adapter","MaxAttachments 1");
        var registry=new ArrayList<>(List.of(optic,adapter,second,unrelated));
        var catalog=new GunAttachmentCatalog(registry);
        registry.clear();
        var direct=catalog.compatible(root,0);
        check(direct.equals(List.of(adapter,second)),"only directly fitting parts, never an optic needing an adapter");
        check(catalog.compatible(adapter,0).equals(List.of(optic)),"nested owner queries use the adapter's own slot");
        check(direct==catalog.compatible(root,0),"same mount reuses the immutable cached catalogue");
        check(catalog.compatible(root,-1).isEmpty() && catalog.compatible(root,1).isEmpty() && catalog.compatible(null,0).isEmpty(),"invalid targets yield no catalogue");
        boolean immutable=false;try{direct.clear();}catch(UnsupportedOperationException expected){immutable=true;}
        check(immutable,"callers cannot mutate the cached catalogue");
        var mounted=node(adapter.shortName,0);mounted.children.add(node(optic.shortName,0));
        List<List<DefaultAttachment>> tree=new ArrayList<>();tree.add(new ArrayList<>(List.of(mounted)));
        check("full".equals(GunAttachments.canInstall(root,tree,new int[0],0,second,0,0)),"real slot remains full");
        check(catalog.compatible(root,0).contains(second),"full slots still list replacement candidates");
        var preview=GunAttachmentCatalog.previewBase(root,tree,new int[0],0,new int[0]);
        check(GunAttachments.count(preview)==0,"root replacement preview removes just the chosen existing assembly");
        check(GunAttachments.count(tree)==2 && tree.getFirst().getFirst()==mounted,"replacement never changes the real/default tree or child objects");
        check(GunAttachments.canInstall(root,preview,new int[0],0,second,0,0)==null,"replacement preview can fit the new part");
        check("full".equals(GunAttachments.canInstall(root,tree,new int[0],0,second,0,0)),"preview never authorizes real installation into a full mount");
        var nested=GunAttachmentCatalog.previewBase(root,tree,new int[]{0,0},0,new int[]{0,0,0,0});
        check(GunAttachments.count(nested)==1 && GunAttachments.nodeAt(nested,new int[]{0,0})!=null,"nested replacement retains its parent while removing its old child");
        check(GunAttachments.count(tree)==2,"nested replacement leaves source children untouched");
        root.slots[0].maxCanAttach=2;tree.getFirst().add(node(second.shortName,0));
        var preferred=GunAttachmentCatalog.previewBase(root,tree,new int[0],0,new int[]{0,1});
        check(preferred.getFirst().size()==1 && preferred.getFirst().getFirst().name.equals(adapter.shortName),"full multi-part mounts replace the explicitly selected sibling");
        root.slots[0].maxCanAttach=3;
        check(GunAttachments.count(GunAttachmentCatalog.previewBase(root,tree,new int[0],0,new int[]{0,1}))==3,"non-full mounts preserve existing parts in an additive preview");
        check(GunAttachments.count(tree)==3,"all previews leave source assembly intact");
        if(args.length>0) {
            var pack=new OfflinePack(Path.of(args[0]));pack.readTypes();
            var real=new GunAttachmentCatalog(AttachmentType.attachments);
            int mounts=0;
            for(var type:pack.types) if(type instanceof ModifiableType owner) for(int slot=0;slot<owner.slots.length;slot++) {
                var available=real.compatible(owner,slot);mounts++;
                for(var candidate:available) check(GunAttachments.allowed(owner,slot,candidate),"real catalogue matches exact mount rules");
                check(available==real.compatible(owner,slot),"real mount cache is reused");
            }
            System.out.println("Actual EFT catalogue mounts: "+mounts);
        }
        System.out.println("PASS VerifyGunAttachmentCatalog: "+checks+" checks");
    }
}
