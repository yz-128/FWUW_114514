package com.flansmod.common;

import com.flansmod.common.network.GunSightPayload;
import com.flansmod.common.types.*;
import com.flansmod.common.types.ModifiableType.DefaultAttachment;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Per-stack sight settings and FMUM's ordered, collinear sight groups. */
public final class GunSightState {
    public static final int NEXT_GROUP=0, ZOOM=1, DOT=2, TOGGLE=3;
    public static final int FOLD_TICKS=12;
    private static final float LINE_EPSILON=0.00001F, ROLL_EPSILON=.001F;
    // G33Enabled and EOTech552 differ by 0.000625 gun-local blocks after their authored
    // model scales. Only a deployed cosight sharing this rail may bridge that subpixel gap.
    private static final float COSIGHT_LINE_EPSILON=.001F;
    public record Sight(int[] path,AttachmentType type,DefaultAttachment node,float x,float y,float z,float roll) { }
    public record Group(List<Sight> sights,Sight aiming) { }
    private static final Map<GunType,List<Group>> GROUPS=Collections.synchronizedMap(new WeakHashMap<>());
    private GunSightState() { }

    /** Stack views are immutable. Definitions may still be populated by readers or fixtures. */
    public static List<Group> groups(GunType view) {
        if(view==null)return List.of();
        boolean cacheable=view.definition()!=view;
        var cached=cacheable?GROUPS.get(view):null;if(cached!=null)return cached;
        var remaining=new ArrayList<Sight>();
        collect(view,view.defaultAttachments.stream().flatMap(List::stream).toList(),new int[0],0,0,0,0,remaining,false);
        var result=new ArrayList<Group>();
        while(!remaining.isEmpty()) {
            var first=remaining.removeFirst();var group=new ArrayList<Sight>();group.add(first);
            // Original setSightsAtForGun appends matching sights from the end, then sorts X descending.
            for(int i=remaining.size();--i>=0;)if(sameLine(first,remaining.get(i)))group.add(remaining.remove(i));
            group.sort(Comparator.comparingDouble(Sight::x).reversed());
            result.add(new Group(List.copyOf(group),group.stream().filter(s->!s.type.isFoldedVariant()).findFirst().orElse(group.getFirst())));
        }
        var value=List.copyOf(result);if(cacheable)GROUPS.put(view,value);return value;
    }
    public static Group selectedGroup(GunType view) {
        var groups=groups(view);int index=selectedIndex(view,groups);if(index<0)return null;
        var selected=groups.get(index);var combined=new ArrayList<>(selected.sights);
        for(int i=0;i<groups.size();i++)if(i!=index && sameCoSightLine(selected.aiming,groups.get(i).aiming))combined.addAll(groups.get(i).sights);
        if(combined.size()==selected.sights.size())return selected;
        combined.sort(Comparator.comparingDouble(Sight::x).reversed());
        // Raw group indices stay stable for existing saves. Optically, a deployed magnifier
        // and the forward holographic sight form one line, aimed through the rear glass.
        var aiming=combined.stream().filter(s->s.type.isCoSight() && usableScope(s)).findFirst().orElse(selected.aiming);
        return new Group(List.copyOf(combined),aiming);
    }
    /** Keep raw indices for existing stacks, but never move the eye onto a stowed optic. */
    private static int selectedIndex(GunType view,List<Group> groups) {
        if(groups.isEmpty())return -1;
        int index=Math.clamp(view.selectedSightGroup,0,groups.size()-1);
        var aiming=groups.get(index).aiming;
        if(!aiming.type.isFoldedVariant())return index;
        var shadow=AttachmentType.getAttachment(aiming.type.shadowType);
        if(shadow!=null) {
            var deployed=shadowPoint(aiming,shadow);
            for(int i=0;i<groups.size();i++) {
                var candidate=groups.get(i).aiming;
                if(!candidate.type.isFoldedVariant() && (sameLine(deployed,candidate) || sameCoSightLine(deployed,candidate)))return i;
            }
        }
        for(int i=0;i<groups.size();i++)if(!groups.get(i).aiming.type.isFoldedVariant())return i;
        return -1;
    }
    public static Sight selectedSight(GunType view) {var group=selectedGroup(view);return group==null?null:group.aiming;}
    /** A folded co-sight is still enumerated for legacy ordering, but never contributes zoom. */
    public static Sight scopeSight(GunType view) {
        var group=selectedGroup(view);if(group==null)return null;
        for(var sight:group.sights)if(usableScope(sight))return sight;
        return null;
    }
    public static boolean usableScope(Sight sight) {return !sight.type.isFoldedVariant() && hasGlass(sight.type);}
    /** A definition that actually declares a glass model is an optic you can look through. */
    static boolean hasGlass(AttachmentType type) {
        var glass=type==null?null:type.scopeGlassModel;
        return glass!=null && !glass.isBlank() && !glass.equalsIgnoreCase("none") && !glass.equalsIgnoreCase("null");
    }
    private static boolean sameLine(Sight a,Sight b) {
        float roll=(a.roll-b.roll)%360F;if(roll>180)roll-=360;if(roll< -180)roll+=360;
        return Math.abs(a.y-b.y)<=LINE_EPSILON && Math.abs(a.z-b.z)<=LINE_EPSILON && Math.abs(roll)<=ROLL_EPSILON;
    }
    private static boolean sameCoSightLine(Sight a,Sight b) {
        if(a.type.isFoldedVariant() || b.type.isFoldedVariant() || !(a.type.isCoSight() || b.type.isCoSight()) || !sameMount(a,b))return false;
        float roll=(a.roll-b.roll)%360F;if(roll>180)roll-=360;if(roll< -180)roll+=360;
        return Math.abs(a.y-b.y)<=COSIGHT_LINE_EPSILON && Math.abs(a.z-b.z)<=COSIGHT_LINE_EPSILON && Math.abs(roll)<=ROLL_EPSILON;
    }
    private static int aimingLineCount(List<Group> groups) {
        var lines=new ArrayList<Sight>();
        for(var group:groups)if(!group.aiming.type.isFoldedVariant()
                && lines.stream().noneMatch(s->sameLine(s,group.aiming) || sameCoSightLine(s,group.aiming)))lines.add(group.aiming);
        return lines.size();
    }
    /** Two sights sharing a parent and a slot are the same physical position on one mount. */
    private static boolean sameMount(Sight a,Sight b) {
        if(a.path.length!=b.path.length || a.path.length<2)return false;
        for(int i=0;i<a.path.length-2;i++)if(a.path[i]!=b.path[i])return false;
        return a.path[a.path.length-2]==b.path[b.path.length-2];
    }
    private static void collect(ModifiableType owner,List<DefaultAttachment> nodes,int[] parent,float x,float y,float z,
                                float roll,List<Sight> out,boolean allNodes) {
        if(parent.length/2>=GunAttachments.MAX_DEPTH)return;
        float sin=(float)Math.sin(Math.toRadians(roll)),cos=(float)Math.cos(Math.toRadians(roll));
        // Like recordSights: reverse SLOT order, then reverse per-slot sibling order, parent before child.
        for(int slot=owner.slots.length;--slot>=0;) {
            var group=new ArrayList<DefaultAttachment>();for(var node:nodes)if(node.slot==slot)group.add(node);
            for(int index=group.size();--index>=0;) {
                var node=group.get(index);var type=AttachmentType.getAttachment(node.name);if(type==null)continue;
                var mount=owner.slots[slot];float nx=x+mount.x+node.pos;
                float ny=y+mount.y*cos-mount.z*sin,nz=z+mount.y*sin+mount.z*cos,nr=roll+mount.rotate;
                var path=Arrays.copyOf(parent,parent.length+2);path[parent.length]=slot;path[parent.length+1]=index;
                if(allNodes || type.isAimingSight()) {
                    float rs=(float)Math.sin(Math.toRadians(nr)),rc=(float)Math.cos(Math.toRadians(nr));
                    out.add(new Sight(path,type,node,nx,ny+type.aimCenterY*rc-type.aimCenterZ*rs,
                            nz+type.aimCenterY*rs+type.aimCenterZ*rc,nr+type.aimCenterX));
                }
                collect(type,node.children,path,nx,ny,nz,nr,out,allNodes);
            }
        }
    }
    private static List<Sight> allNodes(GunType view) {
        var result=new ArrayList<Sight>();collect(view,view.defaultAttachments.stream().flatMap(List::stream).toList(),
                new int[0],0,0,0,0,result,true);return result;
    }
    private static Sight shadowPoint(Sight current,AttachmentType shadow) {
        float mountRoll=current.roll-current.type.aimCenterX,rad=(float)Math.toRadians(mountRoll),sin=(float)Math.sin(rad),cos=(float)Math.cos(rad);
        float dy=shadow.aimCenterY-current.type.aimCenterY,dz=shadow.aimCenterZ-current.type.aimCenterZ;
        return new Sight(current.path,shadow,current.node,current.x,current.y+dy*cos-dz*sin,current.z+dy*sin+dz*cos,mountRoll+shadow.aimCenterX);
    }
    private static List<Sight> toggleTargets(GunType view) {
        var group=selectedGroup(view);var all=allNodes(view);var result=new ArrayList<Sight>();
        if(group!=null) {
            for(var sight:group.sights)if(sight.type.isCoSight() && AttachmentType.getAttachment(sight.type.shadowType)!=null)result.add(sight);
            if(!result.isEmpty())return result;
            // A magnifier in EITHER state can sit off the aimed line when its centre is not
            // pixel-collinear with the partner. The pack's declared cosight pair on the same mount
            // is the target; exact collinearity is only the precise fast path.
            for(var sight:all)if(sight.type.isCoSight()) {
                var shadow=AttachmentType.getAttachment(sight.type.shadowType);
                if(shadow==null)continue;
                if(sight.type.isFoldedVariant() && sameLine(group.aiming,shadowPoint(sight,shadow))) {result.add(sight);continue;}
                if(sameMount(group.aiming,sight))result.add(sight);
            }
            if(!result.isEmpty())return result;
        }
        else {
            for(var sight:all)if(sight.type.isCoSight() && sight.type.isFoldedVariant()
                    && AttachmentType.getAttachment(sight.type.shadowType)!=null)result.add(sight);
            if(!result.isEmpty())return result;
        }
        // FMUM toggle sights include generic rear-sight nodes, so do not restrict these to ItsType=sight.
        var toggles=all.stream().filter(s->s.type.isToggle() && AttachmentType.getAttachment(s.type.shadowType)!=null).toList();
        boolean folding=toggles.stream().anyMatch(s->!s.type.isFoldedVariant());
        for(var sight:toggles)if(sight.type.isFoldedVariant()!=folding)result.add(sight);
        return result;
    }
    public static boolean canChange(GunType view,int operation) {
        if(view==null)return false;
        return switch(operation) {
            case NEXT_GROUP -> aimingLineCount(groups(view))>1;
            case ZOOM -> {var sight=scopeSight(view);yield sight!=null && sight.type.fovZoomLevels.length>1;}
            case DOT -> {var group=selectedGroup(view);yield group!=null && group.sights.stream().anyMatch(s->!s.type.isFoldedVariant() && s.type.lightTextureCount>1);}
            case TOGGLE -> !toggleTargets(view).isEmpty();
            default -> false;
        };
    }

    /** Only a fresh copy is changed; structure revision and the registered definitions remain intact. */
    public static boolean apply(ItemStack stack,GunType gun,int operation,RegistryAccess registries) {
        if(stack==null || stack.isEmpty() || gun==null || GunActions.firearm(stack)!=gun.definition())return false;
        if(operation<NEXT_GROUP || operation>TOGGLE)return false;
        var current=GunAttachments.view(gun,stack);if(!canChange(current,operation))return false;
        var tree=GunAttachments.copy(gun,GunAttachments.read(gun,stack));var view=gun.withAttachments(tree);
        view.selectedSightGroup=current.selectedSightGroup;
        int selected=view.selectedSightGroup;
        if(operation==NEXT_GROUP) {
            var groups=groups(view);selected=selectedIndex(view,groups);
            var currentSight=groups.get(selected).aiming;
            for(int i=1;i<groups.size();i++) {
                int next=(selected+i)%groups.size();var aiming=groups.get(next).aiming;
                if(!aiming.type.isFoldedVariant() && !sameLine(currentSight,aiming) && !sameCoSightLine(currentSight,aiming)) {selected=next;break;}
            }
        }
        else if(operation==ZOOM) {var sight=scopeSight(view);sight.node.zoomStep=(sight.node.zoomStep+1)%sight.type.fovZoomLevels.length;}
        else if(operation==DOT) {
            for(var sight:selectedGroup(view).sights)if(!sight.type.isFoldedVariant() && sight.type.lightTextureCount>1)
                sight.node.dotFrame=(sight.node.dotFrame+1)%sight.type.lightTextureCount;
        } else {
            var targets=toggleTargets(view);var oldGroup=selectedGroup(view);Sight reference=null;
            if(oldGroup!=null)for(var sight:oldGroup.sights)if(targets.stream().noneMatch(t->Arrays.equals(t.path,sight.path))){reference=sight;break;}
            // Validate the whole transition before touching any copied node; preserve every child and component.
            for(var sight:targets) {
                var shadow=AttachmentType.getAttachment(sight.type.shadowType);
                if(shadow==null || shadow.item==null || !sight.type.isShadowTransitionTo(shadow))return false;
                for(var child:sight.node.children)if(child.slot<0 || child.slot>=shadow.slots.length)return false;
            }
            for(var sight:targets) {
                var shadow=AttachmentType.getAttachment(sight.type.shadowType);var node=sight.node;
                node.name=shadow.shortName;
                if(node.itemData!=null) {node.itemData=node.itemData.copy();node.itemData.putString("id",BuiltInRegistries.ITEM.getKey(shadow.item).toString());}
                // Node coordinates, paintjob, zoom/dot state and complete child tree stay with this physical part.
            }
            var updated=gun.withAttachments(tree);var newGroups=groups(updated);selected=0;
            int[] referencePath=reference!=null?reference.path:targets.getFirst().path;
            // Flipping an optic UP means the eye now looks through it. Staying on the partner group
            // that has no glass left the magnifier rendering its black "unbound scope" lens.
            int[] scopePath=null;
            for(var sight:targets) {
                var shadow=AttachmentType.getAttachment(sight.type.shadowType);
                if(hasGlass(shadow) && !shadow.isFoldedVariant()) {scopePath=sight.path;break;}
            }
            int[] wanted=scopePath!=null?scopePath:referencePath;
            for(int i=0;i<newGroups.size();i++)if(newGroups.get(i).sights.stream().anyMatch(s->Arrays.equals(s.path,wanted))){selected=i;break;}
        }
        GunAttachments.writeSightState(stack,tree,selected);return true;
    }

    /** Only declared shadow-pair changes at the same physical mount become animation tracks. */
    public static List<com.flansmod.common.network.GunSightAnimationPayload.Part> foldChanges(GunType before,GunType after) {
        var parts=new ArrayList<com.flansmod.common.network.GunSightAnimationPayload.Part>();
        for(var sight:allNodes(before)) {
            var node=GunAttachments.nodeAt(after.defaultAttachments,sight.path);
            if(node!=null && !sight.node.name.equals(node.name) && sight.type.shadowType.equalsIgnoreCase(node.name))
                parts.add(new com.flansmod.common.network.GunSightAnimationPayload.Part(sight.path,sight.node.name,node.name));
        }
        return List.copyOf(parts);
    }

    /** Menu, hotbar slot, structure AND optical revision all belong to the held server stack. */
    public static boolean request(Player player,GunSightPayload payload) {
        if(!(player instanceof ServerPlayer server) || !player.isAlive() || player.isSpectator()
                || player.containerMenu!=player.inventoryMenu || payload.slot()<0 || payload.slot()>8
                || payload.slot()!=player.getInventory().selected)return false;
        var stack=player.getMainHandItem();var gun=GunActions.firearm(stack);if(gun==null)return false;
        if(payload.attachmentRevision()!=GunAttachments.revision(stack) || payload.sightRevision()!=GunAttachments.sightRevision(stack))return false;
        var data=PlayerHandler.getPlayerData(player);
        GunActions.ensureHeld(player,data);
        if(data.sightFolding(player) || data.isReloading() || data.gunReload.active() || data.compressionHeld || !data.gunAction.ready()
                || data.gunAction.queued!=GunActionState.Operation.NONE || data.pendingGunReload || data.pendingGunTrigger)return false;
        if(payload.operation()==TOGGLE && (data.isShootingRight || player.isSprinting()))return false;
        var before=GunAttachments.view(gun,stack);
        if(!apply(stack,gun,payload.operation(),player.registryAccess()))return false;
        player.getInventory().setChanged();server.inventoryMenu.broadcastChanges();
        if(payload.operation()==TOGGLE) {
            var parts=foldChanges(before,GunAttachments.view(gun,stack));
            if(!parts.isEmpty()) {
                data.sightFoldUntilTick=player.tickCount+FOLD_TICKS;
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(server,
                        new com.flansmod.common.network.GunSightAnimationPayload(player.getId(),payload.slot(),gun.shortName,
                                GunAttachments.revision(stack),GunAttachments.sightRevision(stack),before.selectedSightGroup,++data.sightFoldSequence,parts));
            }
        }
        return true;
    }
}
