package com.flansmod.common;

import com.flansmod.common.types.*;
import com.flansmod.common.types.ModifiableType.DefaultAttachment;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import java.util.*;

/** Per-item assemblies. Pack definitions remain shared, read-only defaults. */
public final class GunAttachments {
    public static final String KEY = "flansmodAttachments", REVISION = "flansmodAttachmentRevision";
    public static final String SIGHT_REVISION = "flansmodSightRevision", SIGHT_GROUP = "flansmodSelectedSightGroup";
    /** Tactical device switches, stored on the gun stack like the selected sight group. */
    public static final String LIGHT_ON = "flansmodLightOn", LASER_ON = "flansmodLaserOn";
    private static final String PART_ZOOM = "flansmodSightZoomStep", PART_DOT = "flansmodSightDotFrame";
    public static final int MAX_DEPTH = 8, MAX_NODES = 256;
    /** FMUM's global MaxAttachments caps EACH slot/rail, even if the pack omits its local cap. */
    public static final int MAX_PER_SLOT = 5;
    private record Cached(ModifiableType owner, CustomData data, List<List<DefaultAttachment>> tree, GunType view) { }
    private static final Map<ItemStack,Cached> CACHE = Collections.synchronizedMap(new WeakHashMap<>());
    private GunAttachments() { }

    public static int revision(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : data.copyTag().getInt(REVISION)&0x7fff;
    }
    public static int sightRevision(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? 0 : data.copyTag().getInt(SIGHT_REVISION)&0x7fff;
    }
    public static boolean isLightOn(ItemStack stack) { return flag(stack, LIGHT_ON); }
    public static boolean isLaserOn(ItemStack stack) { return flag(stack, LASER_ON); }
    private static boolean flag(ItemStack stack, String key) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().getBoolean(key);
    }
    public static void setTacticalState(ItemStack stack, boolean light, boolean laser) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        var tag = data == null ? new CompoundTag() : data.copyTag();
        tag.putBoolean(LIGHT_ON, light);
        tag.putBoolean(LASER_ON, laser);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
    public static List<List<DefaultAttachment>> read(ModifiableType owner, ItemStack stack) {
        if(stack == null || stack.isEmpty()) return owner.defaultAttachments;
        if(owner instanceof GunType gun) owner = gun.definition();
        var data = stack.get(DataComponents.CUSTOM_DATA);
        var cached = CACHE.get(stack);
        if(cached != null && cached.owner == owner && cached.data == data) return cached.tree;
        var tag = data == null ? null : data.copyTag();
        var tree = tag != null && tag.contains(KEY,Tag.TAG_LIST)
                ? decode(owner,tag.getList(KEY,Tag.TAG_COMPOUND),new int[]{MAX_NODES},0)
                : owner.defaultAttachments;
        GunType view = owner instanceof GunType gun ? gun.withAttachments(tree) : null;
        if(view != null) view.selectedSightGroup = tag == null ? 0 : Math.max(0,tag.getInt(SIGHT_GROUP));
        CACHE.put(stack,new Cached(owner,data,tree,view));
        return tree;
    }
    public static GunType view(GunType gun, ItemStack stack) {
        if(stack == null || stack.isEmpty()) return gun;
        read(gun,stack);
        return CACHE.get(stack).view;
    }
    private static List<List<DefaultAttachment>> groups(ModifiableType owner) {
        var groups = new ArrayList<List<DefaultAttachment>>();
        for(int i=0;i<owner.slots.length;i++) groups.add(new ArrayList<>());
        return groups;
    }
    private static List<List<DefaultAttachment>> decode(ModifiableType owner,ListTag list,int[] budget,int depth) {
        var groups = groups(owner);
        if(depth > MAX_DEPTH) return groups;
        for(int i=0;i<list.size() && budget[0]>0;i++) {
            budget[0]--;
            var tag = list.getCompound(i);
            var type = AttachmentType.getAttachment(tag.getString("name"));
            int slot=tag.getInt("slot");
            if(type == null || slot < 0 || slot >= owner.slots.length) continue;
            var node = new DefaultAttachment();
            node.name=type.shortName;node.slot=slot;node.step=tag.getInt("step");node.offset=tag.getInt("offset");
            // A folded shadow can have no optical model; retain its physical part's settings so
            // unfolding does not erase them. The optical consumer clamps to the active model.
            node.zoomStep=Math.clamp(tag.getInt("zoomStep"),0,65535);
            node.dotFrame=Math.clamp(tag.getInt("dotFrame"),0,65535);
            if(tag.contains("paintjob")) node.paintjob=tag.getInt("paintjob");
            if(tag.contains("item",Tag.TAG_COMPOUND)) node.itemData=tag.getCompound("item").copy();
            position(owner,node,type);
            for(var children:decode(type,tag.getList("children",Tag.TAG_COMPOUND),budget,depth+1)) node.children.addAll(children);
            groups.get(slot).add(node);
        }
        return groups;
    }
    private static ListTag encode(List<List<DefaultAttachment>> groups) {
        var list = new ListTag();
        for(var group:groups) for(var node:group) list.add(encode(node));
        return list;
    }
    private static CompoundTag encode(DefaultAttachment node) {
        var tag = new CompoundTag();
        tag.putString("name",node.name);tag.putInt("slot",node.slot);tag.putInt("step",node.step);tag.putInt("offset",node.offset);
        tag.putInt("zoomStep",node.zoomStep);tag.putInt("dotFrame",node.dotFrame);
        if(node.paintjob != null) tag.putInt("paintjob",node.paintjob);
        if(node.itemData != null) tag.put("item",node.itemData.copy());
        var children = new ListTag();
        for(var child:node.children) children.add(encode(child));
        tag.put("children",children);
        return tag;
    }
    public static void write(ItemStack stack,List<List<DefaultAttachment>> tree) {
        var data=stack.get(DataComponents.CUSTOM_DATA);
        var tag=data==null?new CompoundTag():data.copyTag();
        tag.put(KEY,encode(tree));tag.putInt(REVISION,(revision(stack)+1)&0x7fff);
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
    }
    /** Optical changes invalidate stack views without triggering the gun's structural equip clock. */
    public static void writeSightState(ItemStack stack,List<List<DefaultAttachment>> tree,int selectedGroup) {
        var data=stack.get(DataComponents.CUSTOM_DATA);
        var tag=data==null?new CompoundTag():data.copyTag();
        tag.put(KEY,encode(tree));tag.putInt(SIGHT_GROUP,Math.clamp(selectedGroup,0,MAX_NODES));
        tag.putInt(SIGHT_REVISION,(sightRevision(stack)+1)&0x7fff);
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));
    }
    public static List<List<DefaultAttachment>> copy(ModifiableType owner,List<List<DefaultAttachment>> tree) {
        return decode(owner,encode(tree),new int[]{MAX_NODES},0);
    }
    public static boolean validPath(int[] path) {
        if(path == null || (path.length&1)!=0 || path.length>MAX_DEPTH*2) return false;
        for(int n:path) if(n<0 || n>=MAX_NODES) return false;
        return true;
    }
    public static DefaultAttachment nodeAt(List<List<DefaultAttachment>> tree,int[] path) {
        if(!validPath(path) || path.length==0) return null;
        List<DefaultAttachment> nodes=flatten(tree);
        DefaultAttachment node=null;
        for(int i=0;i<path.length;i+=2) {
            int index=path[i+1],slot=path[i];
            node=null;
            for(var candidate:nodes) if(candidate.slot==slot && index--==0) {node=candidate;break;}
            if(node==null) return null;
            nodes=node.children;
        }
        return node;
    }
    public static ModifiableType ownerAt(GunType gun,List<List<DefaultAttachment>> tree,int[] path) {
        if(!validPath(path)) return null;
        if(path.length==0) return gun;
        var node=nodeAt(tree,path);
        return node==null?null:AttachmentType.getAttachment(node.name);
    }
    private static List<DefaultAttachment> flatten(List<List<DefaultAttachment>> groups) {
        var nodes=new ArrayList<DefaultAttachment>();for(var g:groups)nodes.addAll(g);return nodes;
    }
    public static List<DefaultAttachment> children(List<List<DefaultAttachment>> tree,int[] parent) {
        if(parent.length==0) return flatten(tree);
        var node=nodeAt(tree,parent);
        return node==null?List.of():node.children;
    }
    private static List<String> slotList(List<List<String>> lists,int slot) {
        return slot>=0 && slot<lists.size()?lists.get(slot):List.of();
    }
    private static boolean contains(List<String> list,String value) {
        return list.stream().anyMatch(s->s.equalsIgnoreCase(value));
    }
    /** Original FMUM priority: named allow, named deny, type allow, type deny. */
    public static boolean allowed(ModifiableType owner,int slot,AttachmentType part) {
        if(slot<0 || slot>=owner.slots.length) return false;
        if(contains(slotList(owner.attachmentWhitelist,slot),part.shortName)) return true;
        if(contains(slotList(owner.attachmentBlacklist,slot),part.shortName)) return false;
        // Empty groups separated by "next" are absent constraints in the original reader.
        if(!slotList(owner.typeWhitelist,slot).isEmpty()) return contains(owner.typeWhitelist.get(slot),part.attachmentType)
                || originalEftMdrMuzzle(owner,slot,part);
        return !contains(slotList(owner.typeBlacklist,slot),part.attachmentType);
    }

    /**
     * The guns that can take this attachment, for the item tooltip.
     *
     * <p>1.7.10 did not compute this: it printed the definition's {@code Description} line, which
     * the packs use to spell the compatible weapons out ("Need:AK74 AK74N ..."). The port keeps the
     * description as well, but a real computed list is what the "fits onto" line was reaching for —
     * and unlike the attachment-parent list it previously used, it names weapons, not parts.
     */
    public static java.util.List<com.flansmod.common.types.GunType> gunsAccepting(
            com.flansmod.common.types.AttachmentType part) {
        var result = new java.util.ArrayList<com.flansmod.common.types.GunType>();
        if (part == null) return result;
        for (var type : com.flansmod.common.types.InfoType.infoTypes) {
            if (!(type instanceof com.flansmod.common.types.GunType gun)) continue;
            for (int slot = 0; slot < gun.slots.length; slot++)
                if (allowed(gun, slot, part)) { result.add(gun); break; }
        }
        return result;
    }

    /** EFT 1.5.3 explicitly ships this default barrel/muzzle pair, but names its interface twice.
     * Restore only that authored connection; unrelated NATO and 7.62x51 interfaces stay distinct. */
    private static boolean originalEftMdrMuzzle(ModifiableType owner,int slot,AttachmentType part) {
        return slot==0 && "EscapeFromTarcraftReforged_1.5.3".equals(owner.contentPack)
                && Objects.equals(owner.contentPack,part.contentPack)
                && "DTMDR762DefaultBarrel".equals(owner.shortName) && "DTMDR762Muzzle".equals(part.shortName)
                && "762NATOMuzzle".equalsIgnoreCase(part.attachmentType)
                && contains(slotList(owner.typeWhitelist,slot),"762x51Muzzle")
                && !contains(slotList(owner.typeBlacklist,slot),part.attachmentType);
    }
    public static String canInstall(GunType gun,List<List<DefaultAttachment>> tree,int[] parent,int slot,
                                   AttachmentType part,int step,int offset) {
        var owner=ownerAt(gun,tree,parent);
        if(owner==null || slot<0 || slot>=owner.slots.length) return "invalid";
        if(!allowed(owner,slot,part)) return "incompatible";
        var mount=owner.slots[slot];
        if(step<0 || step>Math.max(0,mount.step) || offset<0 || offset>=Math.max(1,part.attachOffsets.length)) return "position";
        var siblings=children(tree,parent).stream().filter(n->n.slot==slot).toList();
        if(siblings.size()>=slotCapacity(owner,slot)) return "full";
        if(siblings.stream().anyMatch(n->n.name.equalsIgnoreCase(part.shortName) && n.step==step && n.offset==offset)) return "duplicate";
        if(parent.length/2>=MAX_DEPTH || count(tree)>=MAX_NODES) return "limit";
        if(("grip".equals(part.itsType) || "rear_grip".equals(part.itsType) || "reargrip".equals(part.itsType))
                && hasKind(tree,part.itsType)) return "grip";
        return null;
    }

    public static int slotCapacity(ModifiableType owner,int slot) {
        return owner==null || slot<0 || slot>=owner.slots.length ? 0
                : Math.max(0,Math.min(MAX_PER_SLOT,owner.slots[slot].maxCanAttach));
    }

    /** Validate every part of a stored/default subassembly before consuming its source item. */
    public static String canInstallAssembly(GunType gun,List<List<DefaultAttachment>> tree,int[] parent,int slot,
                                           ItemStack source,int step,int offset) {
        if(source==null || source.isEmpty() || !(InfoType.getType(source) instanceof AttachmentType part)) return "invalid";
        var reason=canInstall(gun,tree,parent,slot,part,step,offset);
        if(reason!=null) return reason;
        var descendants=new ArrayList<DefaultAttachment>();
        for(var group:read(part,source)) descendants.addAll(group);
        if(descendants.isEmpty()) return null;
        var proposed=copy(gun,tree);
        var root=new DefaultAttachment();root.name=part.shortName;root.slot=slot;root.step=step;root.offset=offset;
        position(ownerAt(gun,proposed,parent),root,part);
        int index=(int)children(proposed,parent).stream().filter(n->n.slot==slot).count();
        add(proposed,parent,root);
        var rootPath=Arrays.copyOf(parent,parent.length+2);rootPath[parent.length]=slot;rootPath[parent.length+1]=index;
        return validateChildren(gun,proposed,rootPath,descendants);
    }

    private static String validateChildren(GunType gun,List<List<DefaultAttachment>> proposed,int[] parent,
                                           List<DefaultAttachment> nodes) {
        for(var child:nodes) {
            var part=AttachmentType.getAttachment(child.name);
            if(part==null) return "invalid";
            var reason=canInstall(gun,proposed,parent,child.slot,part,child.step,child.offset);
            if(reason!=null) return reason;
            int index=(int)children(proposed,parent).stream().filter(n->n.slot==child.slot).count();
            var node=new DefaultAttachment();node.name=child.name;node.slot=child.slot;node.step=child.step;node.offset=child.offset;
            position(ownerAt(gun,proposed,parent),node,part);add(proposed,parent,node);
            var path=Arrays.copyOf(parent,parent.length+2);path[parent.length]=child.slot;path[parent.length+1]=index;
            reason=validateChildren(gun,proposed,path,child.children);
            if(reason!=null) return reason;
        }
        return null;
    }
    public static void position(ModifiableType owner,DefaultAttachment node,AttachmentType part) {
        node.pos=owner.slots[node.slot].stepVector*node.step;
        if(node.offset>=0 && node.offset<part.attachOffsets.length) node.pos+=part.attachOffsets[node.offset];
    }
    public static void add(List<List<DefaultAttachment>> tree,int[] parent,DefaultAttachment node) {
        if(parent.length==0) tree.get(node.slot).add(node);
        else nodeAt(tree,parent).children.add(node);
    }
    public static DefaultAttachment remove(List<List<DefaultAttachment>> tree,int[] path) {
        var node=nodeAt(tree,path);
        if(node==null) return null;
        if(path.length==2) tree.get(node.slot).remove(node);
        else nodeAt(tree,Arrays.copyOf(path,path.length-2)).children.remove(node);
        return node;
    }
    public static ItemStack item(DefaultAttachment node,RegistryAccess registries) {
        var type=AttachmentType.getAttachment(node.name);
        if(type==null || type.item==null) return ItemStack.EMPTY;
        var stack=node.itemData==null?new ItemStack(type.item):ItemStack.parseOptional(registries,node.itemData);
        if(stack.isEmpty() || InfoType.getType(stack)!=type) stack=new ItemStack(type.item);
        stack.setCount(1);
        var children=groups(type);for(var child:node.children) if(child.slot<children.size())children.get(child.slot).add(child);
        write(stack,children);
        CustomData.update(DataComponents.CUSTOM_DATA,stack,tag->{tag.putInt(PART_ZOOM,node.zoomStep);tag.putInt(PART_DOT,node.dotFrame);});
        if(node.paintjob!=null) CustomData.update(DataComponents.CUSTOM_DATA,stack,tag->tag.putInt("flansmodAttachmentPaintjob",node.paintjob));
        return stack;
    }
    public static DefaultAttachment mounted(ModifiableType owner,int slot,ItemStack source,int step,int offset,RegistryAccess registries) {
        var part=(AttachmentType)InfoType.getType(source);
        var node=new DefaultAttachment();node.name=part.shortName;node.slot=slot;node.step=step;node.offset=offset;
        var original=source.get(DataComponents.CUSTOM_DATA);
        if(original!=null) {node.zoomStep=Math.clamp(original.copyTag().getInt(PART_ZOOM),0,65535);node.dotFrame=Math.clamp(original.copyTag().getInt(PART_DOT),0,65535);}
        if(original!=null && original.copyTag().contains("flansmodAttachmentPaintjob")) node.paintjob=original.copyTag().getInt("flansmodAttachmentPaintjob");
        var item=source.copyWithCount(1);
        // Child assemblies are stored once, outside the original item snapshot.
        var data=item.get(DataComponents.CUSTOM_DATA);
        if(data!=null) {var tag=data.copyTag();tag.remove(KEY);tag.remove(REVISION);item.set(DataComponents.CUSTOM_DATA,CustomData.of(tag));}
        node.itemData=(CompoundTag)item.save(registries);
        for(var group:copy(part,read(part,source)))node.children.addAll(group);
        position(owner,node,part);return node;
    }
    public static int count(List<List<DefaultAttachment>> tree) {return flatten(tree).stream().mapToInt(GunAttachments::count).sum();}
    private static int count(DefaultAttachment node) {return 1+node.children.stream().mapToInt(GunAttachments::count).sum();}
    public static int depth(DefaultAttachment node) {return 1+node.children.stream().mapToInt(GunAttachments::depth).max().orElse(0);}
    private static boolean hasKind(List<List<DefaultAttachment>> tree,String kind) {
        return flatten(tree).stream().anyMatch(n->has(n,normalizeKind(kind),false));
    }
    private static String normalizeKind(String kind) { return kind.toLowerCase(Locale.ROOT).replace("_", ""); }
    private static boolean has(DefaultAttachment node,String tag,boolean typeTag) {
        var part=AttachmentType.getAttachment(node.name);
        return part!=null && (tag.equalsIgnoreCase(typeTag?part.attachmentType:normalizeKind(part.itsType))
                || node.children.stream().anyMatch(n->has(n,tag,typeTag)));
    }
    public static boolean requirements(GunType gun,ItemStack stack,int operation) {
        return requirements(gun,flatten(read(gun,stack)),operation);
    }
    private static boolean requirements(ModifiableType owner,List<DefaultAttachment> nodes,int operation) {
        var remaining=new ArrayList<>(operation==0?owner.neededToShoot:operation==1?owner.neededToAuto:owner.neededToCharge);
        for(var node:nodes) {
            var part=AttachmentType.getAttachment(node.name);
            if(part==null) continue;
            for(int i=0;i<remaining.size();i++) if(remaining.get(i).equalsIgnoreCase(part.attachmentType)) {remaining.remove(i);break;}
            if(!requirements(part,node.children,operation)) return false;
        }
        return remaining.isEmpty();
    }
}
