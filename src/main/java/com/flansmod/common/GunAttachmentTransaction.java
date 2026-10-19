package com.flansmod.common;

import com.flansmod.common.network.GunModificationPayload;
import com.flansmod.common.types.*;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.Arrays;

/** Validates a complete edit before consuming or returning any inventory item. */
public final class GunAttachmentTransaction {
    private GunAttachmentTransaction() { }
    public static String apply(Inventory inventory,int gunSlot,int expectedRevision,int operation,int[] path,
                               int slot,int inventorySlot,int step,int offset,RegistryAccess registries) {
        if(gunSlot<0 || gunSlot>8 || inventory.selected!=gunSlot || !GunAttachments.validPath(path)) return "invalid";
        var stack=inventory.getItem(gunSlot);var gun=GunActions.firearm(stack);
        if(gun==null) return "invalid";
        if(GunAttachments.revision(stack)!=expectedRevision) return "stale";
        var tree=GunAttachments.copy(gun,GunAttachments.read(gun,stack));
        if(operation==GunModificationPayload.INSTALL) {
            if(inventorySlot<0 || inventorySlot>=36 || inventorySlot==gunSlot) return "invalid";
            var source=inventory.getItem(inventorySlot);
            if(!(InfoType.getType(source) instanceof AttachmentType part)) return "invalid";
            var reason=GunAttachments.canInstallAssembly(gun,tree,path,slot,source,step,offset);
            if(reason!=null) return reason;
            var node=GunAttachments.mounted(GunAttachments.ownerAt(gun,tree,path),slot,source,step,offset,registries);
            if(GunAttachments.count(tree)+1+node.children.stream().mapToInt(n->1+descendants(n)).sum()>GunAttachments.MAX_NODES
                    || path.length/2+GunAttachments.depth(node)>GunAttachments.MAX_DEPTH) return "limit";
            GunAttachments.add(tree,path,node);
            GunAttachments.write(stack,tree);
            source.shrink(1);
        } else if(operation==GunModificationPayload.REMOVE || operation==GunModificationPayload.MOVE) {
            var node=GunAttachments.nodeAt(tree,path);
            if(node==null) return "invalid";
            var part=AttachmentType.getAttachment(node.name);
            if(operation==GunModificationPayload.REMOVE) {
                var returned=GunAttachments.item(node,registries);
                if(returned.isEmpty()) return "invalid";
                var planned=new ItemStack[36];
                for(int i=0;i<planned.length;i++) planned[i]=inventory.getItem(i).copy();
                if(!place(planned,returned.copy(),gunSlot).isEmpty()) return "inventory_full";
                boolean barrelRemoved=containsKind(node,"barrel"),boltRemoved=containsKind(node,"bolt");
                var round=barrelRemoved?GunMagazine.getChamberedRound(stack):ItemStack.EMPTY;
                var remainder=place(planned,round.copy(),gunSlot);
                // Original FMUM drops a round that will not fit. An unavailable/cancelled drop
                // rejects the whole edit, retaining both the assembly and its chambered round.
                if(!remainder.isEmpty() && (inventory.player==null || inventory.player.drop(remainder,false)==null))
                    return "inventory_full";
                GunAttachments.remove(tree,path);
                GunAttachments.write(stack,tree);
                if(barrelRemoved) GunMagazine.setChamberedRound(stack,ItemStack.EMPTY);
                if(boltRemoved) GunActions.boltCaught(stack,false);
                for(int i=0;i<planned.length;i++) if(i!=gunSlot && !ItemStack.matches(inventory.getItem(i),planned[i]))
                    inventory.setItem(i,planned[i]);
            } else {
                int[] parent=Arrays.copyOf(path,path.length-2);
                var owner=GunAttachments.ownerAt(gun,tree,parent);
                if(step<0 || step>Math.max(0,owner.slots[node.slot].step) || offset<0 || offset>=Math.max(1,part.attachOffsets.length)) return "position";
                if(GunAttachments.children(tree,parent).stream().anyMatch(other->other!=node && other.slot==node.slot
                        && other.name.equalsIgnoreCase(node.name) && other.step==step && other.offset==offset)) return "duplicate";
                node.step=step;node.offset=offset;GunAttachments.position(owner,node,part);
                GunAttachments.write(stack,tree);
            }
        } else return "invalid";
        inventory.setChanged();return null;
    }
    private static int descendants(ModifiableType.DefaultAttachment node) {
        return node.children.stream().mapToInt(n->1+descendants(n)).sum();
    }

    private static boolean containsKind(ModifiableType.DefaultAttachment node,String kind) {
        var part=AttachmentType.getAttachment(node.name);
        return part!=null && kind.equalsIgnoreCase(part.itsType)
                || node.children.stream().anyMatch(child->containsKind(child,kind));
    }

    /** Mutates only a local inventory plan, filling existing stacks before using empty slots. */
    private static ItemStack place(ItemStack[] plan,ItemStack remaining,int gunSlot) {
        if(remaining.isEmpty()) return ItemStack.EMPTY;
        for(int pass=0;pass<2;pass++) for(int i=0;i<plan.length && !remaining.isEmpty();i++) {
            if(i==gunSlot) continue;
            var current=plan[i];
            if(pass==0 && !current.isEmpty() && ItemStack.isSameItemSameComponents(current,remaining)) {
                int amount=Math.min(remaining.getCount(),Math.max(0,current.getMaxStackSize()-current.getCount()));
                current.grow(amount);remaining.shrink(amount);
            } else if(pass==1 && current.isEmpty()) {
                int amount=Math.min(remaining.getCount(),remaining.getMaxStackSize());
                plan[i]=remaining.copyWithCount(amount);remaining.shrink(amount);
            }
        }
        return remaining;
    }
}
