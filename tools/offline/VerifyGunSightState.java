import com.flansmod.common.*;
import com.flansmod.common.types.*;
import com.flansmod.common.types.ModifiableType.DefaultAttachment;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import java.nio.file.Path;
import java.util.*;

/** Per-stack optics, real EFT folding pairs and metadata conservation; isolated JVM, no game. */
public final class VerifyGunSightState {
    static int checks,ids;
    static final RegistryAccess REGISTRIES=RegistryAccess.EMPTY;
    static void check(boolean value,String message) {checks++;if(!value)throw new AssertionError(message);}
    static void close(float actual,float expected,String message) {check(Math.abs(actual-expected)<.00001F,message+" actual="+actual+" expected="+expected);}
    static void register(InfoType type) {Registry.register(BuiltInRegistries.ITEM,ResourceLocation.fromNamespaceAndPath("flansmod","sight_fixture_"+(ids++)),new ItemFlan(type,new Item.Properties()));}
    static AttachmentType part(String name,String... lines) {
        var f=new TypeFile(EnumType.attachment,name,false);f.lines.add("ShortName "+name);f.lines.addAll(List.of(lines));
        var type=new AttachmentType(f);type.read(f);register(type);return type;
    }
    static GunType fixture(String name,String... lines) {
        var f=new TypeFile(EnumType.gun,name,false);f.lines.add("ShortName "+name);f.lines.addAll(List.of(lines));
        var type=new GunType(f);type.read(f);register(type);return type;
    }
    static DefaultAttachment node(AttachmentType type,int slot) {var n=new DefaultAttachment();n.name=type.shortName;n.slot=slot;return n;}
    static GunType gun(String name) {return (GunType)InfoType.infoTypes.stream().filter(t->t instanceof GunType && name.equals(t.shortName)).findFirst().orElseThrow();}
    static CompoundTag tag(ItemStack stack) {var data=stack.get(DataComponents.CUSTOM_DATA);return data==null?new CompoundTag():data.copyTag();}
    static void apply(ItemStack stack,GunType type,int operation) {check(GunSightState.apply(stack,type,operation,REGISTRIES),"accepted sight operation "+operation);}
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();((net.minecraft.core.MappedRegistry<?>)BuiltInRegistries.ITEM).unfreeze();
        var pack=new OfflinePack(Path.of(args[0]));pack.readTypes();for(var type:pack.types)register(type);
        var ak=gun("AK74M");var original=GunSightState.selectedSight(ak);
        check(original!=null && original.type().shortName.equals("6P1Sb2AK74Rearsight"),"AK74M retains its original default rear-sight group");
        close(original.x(),.5125F,"AK74M rear-sight authored X");close(original.y(),.4F,"AK74M rear-sight authored Y");
        var scalar=part("SightLegacyScalar","ItsType sight","ZoomLevel 4");close(scalar.zoomDivisor(0),4F,"old numeric ZoomLevel still magnifies");
        var scoped=part("SightVariable","ItsType sight","ZoomLevel Glass.Ref Mask.Ref 20 .7 40 .4","DotModel Dot.Ref DotTexture 3");
        close(scoped.zoomDivisor(0),2F,"first zoom step");close(scoped.zoomDivisor(1),4F,"second zoom step");close(scoped.sensitivityMultiplier(1),.4F,"step-specific sensitivity");
        var sightA=part("SightForward","ItsType sight");var sightB=part("SightRear","ItsType sight");
        var line=fixture("SightLine","AttachPoints 32 0 0 0 0 0","MaxAttachments 2 2");
        line.slots[0].y=.1F;line.slots[1].y=.10000002F;
        var lineTree=new ArrayList<List<DefaultAttachment>>();lineTree.add(new ArrayList<>(List.of(node(sightA,0))));lineTree.add(new ArrayList<>(List.of(node(sightB,1))));
        var lineView=line.withAttachments(lineTree);
        check(GunSightState.groups(lineView).size()==1,"floating-point roundoff does not split a collinear group");
        check(GunSightState.selectedSight(lineView).type()==sightA,"frontmost X sight aims regardless of reverse traversal order");
        var opticTree=new ArrayList<List<DefaultAttachment>>();opticTree.add(new ArrayList<>(List.of(node(sightA,0))));opticTree.add(new ArrayList<>(List.of(node(scoped,1))));
        var optical=line.withAttachments(opticTree);check(GunSightState.selectedSight(optical).type()==sightA,"forward iron sight still selects the aiming position");
        check(GunSightState.scopeSight(optical).type()==scoped,"zoom comes from the actual glass-bearing sight in that same group");
        var rotatedParent=part("SightRotatedParent","AttachPoints 0 16 0","AttachRotates 90");
        var nestedGun=fixture("SightNestedGun","AttachPoints 0 16 0","AttachRotates 90");
        var nestedNode=node(rotatedParent,0);nestedNode.children.add(node(sightA,0));
        var nestedView=nestedGun.withAttachments(List.of(List.of(nestedNode)));
        var nestedSight=GunSightState.selectedSight(nestedView);close(nestedSight.y(),1F,"nested mount Y rotation");close(nestedSight.z(),1F,"nested mount Z rotation");close(nestedSight.roll(),180F,"nested inherited roll");

        var multi=fixture("SightMultiGun","AttachPoints 0 0 0 0 8 0","MaxAttachments 1 1");
        var tree=new ArrayList<List<DefaultAttachment>>();tree.add(new ArrayList<>(List.of(node(scoped,0))));tree.add(new ArrayList<>(List.of(node(sightA,1))));
        var stack=new ItemStack(multi.item);var twin=new ItemStack(multi.item);GunAttachments.write(stack,tree);GunAttachments.write(twin,tree);
        CustomData.update(DataComponents.CUSTOM_DATA,stack,t->t.putString("unrelated","kept"));
        int structural=GunAttachments.revision(stack),stateRev=GunAttachments.sightRevision(stack);
        var oldView=GunAttachments.view(multi,stack);check(GunSightState.scopeSight(oldView)==null,"different sight line does not magnify selected group");
        apply(stack,multi,GunSightState.NEXT_GROUP);var view=GunAttachments.view(multi,stack);
        check(view!=oldView && view.selectedSightGroup==1,"stack state invalidates the view and selects its own group");
        check(GunAttachments.view(multi,twin).selectedSightGroup==0 && multi.selectedSightGroup==0,"same model in another stack and registered definition keep group zero");
        check(GunAttachments.revision(stack)==structural && GunAttachments.sightRevision(stack)==stateRev+1,"group switch changes optical revision, never structural/equip revision");
        apply(stack,multi,GunSightState.ZOOM);apply(stack,multi,GunSightState.DOT);
        view=GunAttachments.view(multi,stack);var active=GunSightState.scopeSight(view);
        check(active.node().zoomStep==1 && active.node().dotFrame==1,"zoom and reticle frame live on the mounted node");
        check(GunAttachments.nodeAt(GunAttachments.read(multi,twin),new int[]{0,0}).zoomStep==0,"same attachment model in another gun retains step zero");
        var roundTrip=ItemStack.parseOptional(REGISTRIES,(CompoundTag)stack.save(REGISTRIES));var saved=GunAttachments.view(multi,roundTrip);
        check(saved.selectedSightGroup==1 && GunSightState.scopeSight(saved).node().zoomStep==1 && GunSightState.scopeSight(saved).node().dotFrame==1,"group, zoom and dot frame survive item serialization");
        check(tag(roundTrip).getString("unrelated").equals("kept"),"sight state preserves unrelated custom components");
        var detached=GunAttachments.item(active.node(),REGISTRIES);
        detached.set(DataComponents.CUSTOM_NAME,Component.literal("custom scope"));
        var remounted=GunAttachments.mounted(multi,0,detached,0,0,REGISTRIES);
        check(remounted.zoomStep==1 && remounted.dotFrame==1,"zoom and dot survive detaching and reattaching the physical part");
        check(!GunSightState.apply(stack,multi,-1,REGISTRIES) && !GunSightState.apply(stack,multi,9,REGISTRIES),"unsupported operations cannot mutate a gun");
        check(!GunSightState.apply(ItemStack.EMPTY,multi,GunSightState.ZOOM,REGISTRIES)
                && !GunSightState.apply(stack,line,GunSightState.ZOOM,REGISTRIES),"empty or mismatched gun definitions cannot accept sight state");
        var sameTypeGun=fixture("SightSameModelTwice","AttachPoints 0 0 0 0 8 0","MaxAttachments 1 1");
        var sameTypeStack=new ItemStack(sameTypeGun.item);
        GunAttachments.write(sameTypeStack,List.of(List.of(node(scoped,0)),List.of(node(scoped,1))));
        apply(sameTypeStack,sameTypeGun,GunSightState.ZOOM);
        var isolated=GunAttachments.read(sameTypeGun,sameTypeStack);
        check(isolated.get(0).getFirst().zoomStep==0 && isolated.get(1).getFirst().zoomStep==1,
                "two instances of the same attachment model on one gun keep separate zoom state");

        var red=part("SightRedDot","ItsType sight","Model none none 0.2","AimCenter 6.25 0","DotModel Dot.Ref Red 2");
        var enabled=AttachmentType.getAttachment("G33Enabled");var disabled=AttachmentType.getAttachment("G33Disabled");
        check(enabled.isCoSight() && disabled.isCoSight() && !enabled.hideInCreativeTab && disabled.hideInCreativeTab,
                "real ShadowType parses fourth cosight token and original hide/update bool order");
        check(!enabled.isFoldedVariant() && disabled.isFoldedVariant(),"real G33 shadow pair identifies folded variant");
        var combo=fixture("SightG33Gun","AttachPoints 32 0 0 0 0 0","MaxAttachments 1 1");
        var source=new ItemStack(enabled.item);source.set(DataComponents.CUSTOM_NAME,Component.literal("my magnifier"));
        CustomData.update(DataComponents.CUSTOM_DATA,source,t->t.putString("part_metadata","retained"));
        var mounted=GunAttachments.mounted(combo,1,source,0,0,REGISTRIES);
        var combination=List.of(List.of(node(red,0)),List.of(mounted));var comboStack=new ItemStack(combo.item);GunAttachments.write(comboStack,combination);
        check(GunSightState.groups(GunAttachments.view(combo,comboStack)).size()==1,"real enabled G33 aligns with the red-dot group");
        check(GunSightState.scopeSight(GunAttachments.view(combo,comboStack)).type()==enabled,"collinear G33 glass supplies zoom behind the forward red dot");
        int rev=GunAttachments.revision(comboStack);apply(comboStack,combo,GunSightState.TOGGLE);
        var folded=GunAttachments.view(combo,comboStack);check(GunSightState.groups(folded).size()==2,"folded G33 retains legacy raw group ordering");
        check(GunSightState.selectedSight(folded).type()==red && GunSightState.scopeSight(folded)==null,"folding preserves the active red-dot line and cancels off-axis magnification");
        var foldedNode=GunAttachments.nodeAt(GunAttachments.read(combo,comboStack),new int[]{1,0});
        check(foldedNode.name.equals(disabled.shortName) && foldedNode.itemData.getString("id").equals(BuiltInRegistries.ITEM.getKey(disabled.item).toString()),"folding changes node definition AND serialized item id");
        var foldedItem=GunAttachments.item(foldedNode,REGISTRIES);
        check(InfoType.getType(foldedItem)==disabled && foldedItem.getHoverName().getString().equals("my magnifier") && tag(foldedItem).getString("part_metadata").equals("retained"),"folded removal retains exact custom name and original custom metadata");
        int foldedGroup=-1;
        var savedGroups=GunSightState.groups(folded);
        for(int i=0;i<savedGroups.size();i++)if(savedGroups.get(i).aiming().type()==disabled)foldedGroup=i;
        GunAttachments.writeSightState(comboStack,GunAttachments.read(combo,comboStack),foldedGroup);
        check(GunSightState.selectedSight(GunAttachments.view(combo,comboStack)).type()==red
                && GunSightState.scopeSight(GunAttachments.view(combo,comboStack))==null,"old folded legacy selection resolves to the forward red-dot camera");
        check(!GunSightState.canChange(GunAttachments.view(combo,comboStack),GunSightState.NEXT_GROUP),"a stowed magnifier adds no selectable camera line");
        check(!GunSightState.canChange(GunAttachments.view(combo,comboStack),GunSightState.ZOOM),"folded scope has no active zoom control");
        apply(comboStack,combo,GunSightState.TOGGLE);check(GunSightState.scopeSight(GunAttachments.view(combo,comboStack)).type()==enabled,"folded legacy group can deploy magnifier again");
        check(GunAttachments.revision(comboStack)==rev,"fold/unfold never triggers structural take-out revision");

        // A folded variant may have no glass, no reticle, and ItsType=generic. Preserve its
        // deployed settings and nested physical components until it is unfolded again.
        var child=part("SightPhysicalChild","AttachmentType child");
        var deployed=part("SightPhysicalDeployed","ItsType sight","ZoomLevel Glass.Ref Mask.Ref 20 .7 40 .4",
                "DotModel Dot.Ref DotTexture 3","ShadowType SightPhysicalFolded false false cosight",
                "AttachPoints 0 0 0","AttachOffsets 0 8");
        var hidden=part("SightPhysicalFolded","ShadowType SightPhysicalDeployed true false cosight",
                "AttachPoints 0 0 0","AttachOffsets 0 8");
        var physical=fixture("SightPhysicalGun","AttachPoints 0 0 0","AttachVectors 2 5","MaxAttachments 1");
        var physicalPart=new ItemStack(deployed.item);GunAttachments.writeSightState(physicalPart,List.of(List.of(node(child,0))),0);
        physicalPart.set(DataComponents.CUSTOM_NAME,Component.literal("physical optic"));
        var physicalNode=GunAttachments.mounted(physical,0,physicalPart,3,1,REGISTRIES);physicalNode.zoomStep=1;physicalNode.dotFrame=2;
        var physicalStack=new ItemStack(physical.item);GunAttachments.write(physicalStack,List.of(List.of(physicalNode)));
        apply(physicalStack,physical,GunSightState.TOGGLE);
        var hiddenNode=GunAttachments.read(physical,physicalStack).getFirst().getFirst();
        check(hiddenNode.name.equals(hidden.shortName) && hiddenNode.zoomStep==1 && hiddenNode.dotFrame==2,"folded non-optical variant retains deployed zoom and reticle settings");
        check(hiddenNode.children.size()==1 && hiddenNode.children.getFirst().name.equals(child.shortName)
                && hiddenNode.step==3 && hiddenNode.offset==1,"folding preserves nested physical children and rail/offset indices");
        close(hiddenNode.pos,physicalNode.pos,"folding preserves authored rail placement");
        check(GunSightState.groups(GunAttachments.view(physical,physicalStack)).isEmpty(),"generic folded variant has no active aiming group");
        apply(physicalStack,physical,GunSightState.TOGGLE);
        var redeployed=GunAttachments.read(physical,physicalStack).getFirst().getFirst();
        check(redeployed.name.equals(deployed.shortName) && redeployed.zoomStep==1 && redeployed.dotFrame==2
                && redeployed.children.size()==1,"a generic folded co-sight can redeploy with all settings and children intact");

        var front=AttachmentType.getAttachment("MP7Frontsight");var rear=AttachmentType.getAttachment("MP7Rearsight");
        var irons=fixture("SightMP7IronGun","AttachPoints 32 0 0 0 0 0","MaxAttachments 1 1");
        var ironStack=new ItemStack(irons.item);GunAttachments.write(ironStack,List.of(List.of(node(front,0)),List.of(node(rear,1))));
        apply(ironStack,irons,GunSightState.TOGGLE);
        var ironTree=GunAttachments.read(irons,ironStack);
        check(ironTree.get(0).getFirst().name.equals("MP7FrontsightFolded") && ironTree.get(1).getFirst().name.equals("MP7RearsightFolded"),"MP7 generic rear sight folds with the optical front sight");
        check(GunSightState.scopeSight(GunAttachments.view(irons,ironStack))==null,"folded MP7 iron sights never magnify");
        apply(ironStack,irons,GunSightState.TOGGLE);ironTree=GunAttachments.read(irons,ironStack);
        check(ironTree.get(0).getFirst().name.equals(front.shortName) && ironTree.get(1).getFirst().name.equals(rear.shortName),"MP7 front/rear fold pair restores exactly");
        if(args.length>1) {
            var octagon=new OfflinePack(Path.of(args[1]));octagon.readTypes();for(var type:octagon.types)register(type);
            var rear1=AttachmentType.getAttachment("CICIS4RearSight");
            var rear2=AttachmentType.getAttachment("CICIS4RearSight2");
            var rear3=AttachmentType.getAttachment("CICIS4RearSightFlipped");
            check(!rear1.isFoldedVariant() && !rear2.isFoldedVariant() && rear3.isFoldedVariant(),"three-state rear sight distinguishes second aperture from generic folded endpoint");
            check(rear1.isShadowTransitionTo(rear2) && rear2.isShadowTransitionTo(rear3)
                    && rear3.isShadowTransitionTo(rear1),"every directed edge of actual three-state cycle is valid");
            check(!rear1.isShadowTransitionTo(rear3),"undeclared cycle shortcut stays rejected");
            var cycle=fixture("SightActualOctagonCycle","AttachPoints 0 0 0","MaxAttachments 1");
            var cycleStack=new ItemStack(cycle.item);GunAttachments.write(cycleStack,List.of(List.of(node(rear1,0))));
            for(var target:List.of(rear2,rear3,rear1)) {
                var before=GunAttachments.view(cycle,cycleStack);
                apply(cycleStack,cycle,GunSightState.TOGGLE);
                var after=GunAttachments.view(cycle,cycleStack);
                var changes=GunSightState.foldChanges(before,after);
                check(changes.size()==1 && changes.getFirst().to().equals(target.shortName),"actual cycle chooses declared next state "+target.shortName);
                var event=new com.flansmod.common.network.GunSightAnimationPayload(1,0,cycle.shortName,
                        GunAttachments.revision(cycleStack),GunAttachments.sightRevision(cycleStack),0,1,changes);
                check(com.flansmod.client.SightFoldAnimations.matches(event,cycleStack),"client accepts declared three-state animation "+target.shortName);
            }
        }
        // Real user assembly: AK74M + SVD side rail with EOTech552 and folded G33 in ONE rail slot.
        // The pack declares them a cosight pair, but the closed magnifier sits ~0.1 blocks off the
        // holo's line on this mount, far past the 1e-5 display tolerance.
        {
            var eotech=AttachmentType.getAttachment("EOTech552");
            var rail=AttachmentType.getAttachment("SVDLowSideMount");
            var realAk=gun("AK74M");
            var railTree=new ArrayList<List<DefaultAttachment>>();
            for(var row:realAk.defaultAttachments)railTree.add(new ArrayList<>(row));
            while(railTree.size()<realAk.slots.length)railTree.add(new ArrayList<>());
            var side=node(rail,6);
            side.children.add(node(eotech,0));
            side.children.add(node(disabled,0));
            railTree.get(6).add(side);
            var railStack=new ItemStack(realAk.item);GunAttachments.write(railStack,railTree);
            var railView=GunAttachments.view(realAk,railStack);
            var railGroups=GunSightState.groups(railView);
            int holo=-1;
            for(int i=0;i<railGroups.size();i++)if(railGroups.get(i).aiming().type()==eotech)holo=i;
            check(holo>=0,"real SVD-rail assembly exposes the holographic sight group");
            GunAttachments.writeSightState(railStack,GunAttachments.read(realAk,railStack),holo);
            railView=GunAttachments.view(realAk,railStack);
            check(railView.selectedSightGroup==holo,"holographic group can be selected on the real rail");
            check(GunSightState.canChange(railView,GunSightState.TOGGLE),
                    "folded G33 is togglable from the holographic group (declared cosight pair, not raw collinearity)");
            apply(railStack,realAk,GunSightState.TOGGLE);
            var railAfter=GunAttachments.view(realAk,railStack);
            check(GunAttachments.nodeAt(GunAttachments.read(realAk,railStack),new int[]{6,0,0,1}).name.equals(enabled.shortName),
                    "toggling from the holographic group deploys the real G33Enabled definition");
            check(GunSightState.groups(railAfter).stream().anyMatch(g->g.sights().stream().anyMatch(s->s.type()==enabled)),
                    "deployed magnifier stays enumerated after the toggle");
            check(GunSightState.scopeSight(railAfter)!=null && GunSightState.scopeSight(railAfter).type()==enabled,
                    "flipped-up magnifier becomes the active scope, so its lens renders instead of the black unbound glass");
            // Deployed in its own group (the hip-fire state in the report): folding it BACK must
            // still be offered from the holographic group, not only from the magnifier's own group.
            var upTree=new ArrayList<List<DefaultAttachment>>();
            for(var row:realAk.defaultAttachments)upTree.add(new ArrayList<>(row));
            while(upTree.size()<realAk.slots.length)upTree.add(new ArrayList<>());
            var upSide=node(rail,6);
            upSide.children.add(node(eotech,0));
            upSide.children.add(node(enabled,0));
            upTree.get(6).add(upSide);
            var upStack=new ItemStack(realAk.item);GunAttachments.write(upStack,upTree);
            GunAttachments.writeSightState(upStack,GunAttachments.read(realAk,upStack),holo);
            var upView=GunAttachments.view(realAk,upStack);
            check(GunSightState.selectedGroup(upView).sights().stream().anyMatch(s->s.type()==eotech),"holographic reticle shares the deployed magnifier line");
            check(GunSightState.canChange(upView,GunSightState.TOGGLE),
                    "deployed G33 can be folded back from the holographic group");
            apply(upStack,realAk,GunSightState.TOGGLE);
            check(GunAttachments.nodeAt(GunAttachments.read(realAk,upStack),new int[]{6,0,0,1}).name.equals(disabled.shortName),
                    "folding back restores the real G33Disabled definition");
            check(GunSightState.scopeSight(GunAttachments.view(realAk,upStack))==null,
                    "folding back returns the aim to the holographic group, not the stowed optic");
        }
        System.out.println("PASS VerifyGunSightState: "+checks+" assertions");
    }
}
