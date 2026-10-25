import com.flansmod.common.*;
import com.flansmod.common.types.*;
import com.flansmod.client.model.loader.ModelCache;
import com.flansmod.client.render.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.nio.file.Path;
import java.util.*;

/** Reproduces the user's saved AK74M, including a selected stowed G33 group and rail step 4. */
public final class VerifyG33SightLine {
    static int checks, ids;
    static void check(boolean condition,String message) {checks++;if(!condition)throw new AssertionError(message);}
    static ModifiableType.DefaultAttachment part(String name,int slot,int step) {
        var n=new ModifiableType.DefaultAttachment();n.name=name;n.slot=slot;n.step=step;return n;
    }
    static ItemStack stack(GunType gun,boolean reverse,int step) {
        var rows=new ArrayList<List<ModifiableType.DefaultAttachment>>();
        for(var row:gun.defaultAttachments)rows.add(new ArrayList<>(row));
        while(rows.size()<gun.slots.length)rows.add(new ArrayList<>());
        var side=part("SVDLowSideMount",6,0);
        var magnifier=part("G33Disabled",0,step);var holo=part("EOTech552",0,step);
        side.children.add(reverse?holo:magnifier);side.children.add(reverse?magnifier:holo);
        rows.get(6).add(side);var stack=new ItemStack(gun.item);GunAttachments.write(stack,rows);return stack;
    }
    static void near(float value,float expected,String label) {check(Math.abs(value-expected)<1E-5F,label+" actual="+value+" expected="+expected);}
    static void verify(GunType gun,boolean reverse,int step) {
        var stack=stack(gun,reverse,step);var view=GunAttachments.view(gun,stack);
        var raw=GunSightState.groups(view);
        int folded=-1;
        for(int i=0;i<raw.size();i++)if(raw.get(i).aiming().type().shortName.equals("G33Disabled"))folded=i;
        check(folded>=0,"raw saved folded group can still be decoded without renumbering");
        GunAttachments.writeSightState(stack,GunAttachments.read(gun,stack),folded);
        view=GunAttachments.view(gun,stack);
        var selected=GunSightState.selectedSight(view);
        System.out.printf(Locale.ROOT,"order=%s railStep=%d savedGroup=%d selected=%s camera=%s%n",
                reverse?"holo-first":"G33-first",step,folded,selected.type().shortName,LegacySightPose.forGun(view));
        check(selected.type().shortName.equals("EOTech552"),"stowed G33 cannot take the EOTech aiming line");
        check(GunSightState.scopeSight(view)==null,"stowed magnifier does not enable a scope camera");
        var center=LegacySightPose.forGun(view);
        check(center.source().equals("EOTech552"),"camera pivot belongs to EOTech while G33 is stowed");
        near(center.z(),selected.z(),"stowed lateral offset never enters the camera pivot");
        int revision=GunAttachments.revision(stack);
        var encoded=stack.save(RegistryAccess.EMPTY);
        var restored=ItemStack.parseOptional(RegistryAccess.EMPTY,(net.minecraft.nbt.CompoundTag)encoded);
        check(GunSightState.selectedSight(GunAttachments.view(gun,restored)).type().shortName.equals("EOTech552"),
                "old saved group is corrected after serialization too");
        check(GunSightState.apply(stack,gun,GunSightState.TOGGLE,RegistryAccess.EMPTY),"stowed G33 can deploy from the corrected EOTech line");
        view=GunAttachments.view(gun,stack);
        var scope=GunSightState.scopeSight(view);
        check(scope!=null && scope.type().shortName.equals("G33Enabled"),"deployed G33 supplies the scope picture");
        check(GunSightState.selectedGroup(view).sights().stream().anyMatch(s->s.type().shortName.equals("EOTech552")),
                "deployed magnifier includes the forward holographic reticle");
        var pose=new Matrix4f().translate(.56F,-.52F,-.72F);
        FmumFirstPersonPose.itemFrame(pose);FmumFirstPersonPose.placement(pose,ModelCache.load(gun),1,0,0);
        FmumFirstPersonPose.aim(pose,view,1,FmumFirstPersonPose.eyeInGunSpace(pose));
        var toEye=pose.translate(ScopeReticleRenderer.mount(scope)).rotateX((float)Math.toRadians(scope.roll()-scope.type().aimCenterX));
        var rig=ModelCache.rigFor(scope.type(),scope.type().modelScale);
        check(ScopeEyeRelief.picture(rig.animation,scope.type().modelScale,toEye,scope.node().zoomStep).visible(),
                "complete deployed gun pose sees the magnifier aperture");
        for(int i=0;i<8;i++) {
            check(GunSightState.selectedSight(view)!=null && !GunSightState.selectedSight(view).type().isFoldedVariant(),
                    "sight cycling never aims through the stowed magnifier");
            check(GunSightState.apply(stack,gun,GunSightState.NEXT_GROUP,RegistryAccess.EMPTY),"real gun can cycle its sight lines");
            view=GunAttachments.view(gun,stack);
        }
        // Return to the magnifier group before checking the reverse operation.
        var all=GunSightState.groups(view);int deployed=-1;
        for(int i=0;i<all.size();i++)if(all.get(i).sights().stream().anyMatch(s->s.type().shortName.equals("G33Enabled")))deployed=i;
        GunAttachments.writeSightState(stack,GunAttachments.read(gun,stack),deployed);
        check(GunSightState.apply(stack,gun,GunSightState.TOGGLE,RegistryAccess.EMPTY),"deployed G33 can stow again");
        view=GunAttachments.view(gun,stack);
        check(GunSightState.selectedSight(view).type().shortName.equals("EOTech552"),"stowing restores EOTech camera pivot");
        check(GunSightState.scopeSight(view)==null,"stowing restores the unmagnified holographic line");
        for(int i=0;i<8;i++) {
            check(!GunSightState.selectedSight(view).type().isFoldedVariant(),"stowed group stays out of the sight-key cycle");
            check(GunSightState.apply(stack,gun,GunSightState.NEXT_GROUP,RegistryAccess.EMPTY),"stowed gun can cycle its valid sights");
            view=GunAttachments.view(gun,stack);
        }
        check(GunAttachments.revision(stack)==revision,"folding and corrected selection never alter attachment structure");
        check(gun.defaultAttachments.size()<=6 || gun.defaultAttachments.get(6).isEmpty(),"registered default gun remains unchanged");
    }
    public static void main(String[] args)throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();((net.minecraft.core.MappedRegistry<?>)BuiltInRegistries.ITEM).unfreeze();
        var pack=new OfflinePack(Path.of(args[0]));pack.readTypes();
        for(var type:pack.types)Registry.register(BuiltInRegistries.ITEM,ResourceLocation.fromNamespaceAndPath("flansmod","g33_line_"+ids++),new ItemFlan(type,new Item.Properties()));
        var gun=(GunType)InfoType.infoTypes.stream().filter(t->t instanceof GunType&&"AK74M".equals(t.shortName)).findFirst().orElseThrow();
        for(String name:List.of("G33Enabled","G33Disabled")) {
            var type=AttachmentType.getAttachment(name);var model=ModelCache.load(type);
            System.out.printf(Locale.ROOT,"%s folded=%s aim=(%.6f,%.6f) pupil=%s%n",name,type.isFoldedVariant(),
                    type.aimCenterY,type.aimCenterZ,FmumSightPicture.vector(model,"pupilRefCenter",0,0,0).mul(type.modelScale));
        }
        for(boolean reverse:new boolean[]{false,true})for(int step:new int[]{0,4,8})verify(gun,reverse,step);
        System.out.println("PASS VerifyG33SightLine: "+checks+" checks; real AK74M/EOTech552, both G33 states, saved folded selection, both sibling orders, rail steps 0/4/8, camera pivot and forward reticle. Offline only; no game.");
    }
}
