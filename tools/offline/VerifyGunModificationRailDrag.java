import com.flansmod.client.gui.GunModificationRailDrag;
import com.flansmod.client.gui.GunModificationRailDrag.Axis;
import com.flansmod.client.gui.GunModificationView;
import com.flansmod.common.GunAttachments;
import com.flansmod.common.GunAttachmentCatalog;
import com.flansmod.common.types.*;
import com.flansmod.common.types.ModifiableType.DefaultAttachment;
import net.minecraft.nbt.CompoundTag;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Real EFT movable interfaces and independent gesture fixtures; no game or OpenGL is launched. */
public final class VerifyGunModificationRailDrag {
    private static int checks;
    private static void check(boolean condition,String message) {checks++;if(!condition)throw new AssertionError(message);}
    private static void near(float actual,float expected,String message) {
        check(Math.abs(actual-expected)<.0002F,message+": "+actual+" expected "+expected);
    }
    private static GunModificationView.Frame frame(Matrix4f matrix) {
        return new GunModificationView.Frame(matrix,1,new Vector3f(-1),new Vector3f(1));
    }
    private static ModifiableType.AttachableSlot slot(float x,float y,float z,float roll,float stepVector,int max) {
        var mount=new ModifiableType.AttachableSlot();
        mount.x=x;mount.y=y;mount.z=z;mount.rotate=roll;mount.stepVector=stepVector;mount.step=max;
        return mount;
    }
    private static DefaultAttachment node(AttachmentType type,int slot,int step,int offset) {
        var node=new DefaultAttachment();node.name=type.shortName;node.slot=slot;node.step=step;node.offset=offset;return node;
    }

    private static void realEft(OfflinePack pack) {
        int moved=0,nested=0;
        var identity=frame(new Matrix4f().scale(500,-500,500));
        for(var type:pack.types) if(type instanceof GunType gun && gun.itsType!=GunType.GunItsType.MAG) {
            var tree=gun.defaultAttachments;
            for(var row:GunAttachmentCatalog.treeEntries(gun,tree)) {
                if(row.node()==null || row.owner().slots[row.slot()].step<=0 || row.owner().slots[row.slot()].stepVector==0)continue;
                var path=row.nodePath();var node=GunAttachments.nodeAt(tree,path);
                var axis=GunModificationRailDrag.axis(identity,gun,tree,path);
                if(axis==null)continue;
                near(axis.dx(),row.owner().slots[row.slot()].stepVector*500,"real scaled EFT rail does not scale twice");
                near(axis.dy(),0,"real gun axis remains horizontal before viewer rotation");
                check(axis.initialStep()==node.step && axis.maxStep()==row.owner().slots[row.slot()].step,"real address reads its own step bounds");
                int target=node.step<axis.maxStep()?node.step+1:node.step-1;
                if(GunModificationRailDrag.error(gun,tree,path,target)!=null)continue;
                int delta=target-node.step;
                check(GunModificationRailDrag.stepFor(axis,axis.dx()*delta,axis.dy()*delta)==target,"one projected rail tick selects one real EFT tick");
                var preview=GunModificationRailDrag.preview(gun,tree,path,target);
                check(preview!=tree && GunAttachments.nodeAt(preview,path).step==target && node.step==axis.initialStep(),"EFT preview changes copied node only");
                float offset=node.offset<AttachmentType.getAttachment(node.name).attachOffsets.length
                        ?AttachmentType.getAttachment(node.name).attachOffsets[node.offset]:0;
                near(GunAttachments.nodeAt(preview,path).pos,row.owner().slots[row.slot()].stepVector*target+offset,"EFT preview retains selected offset");
                moved++;if(path.length>2)nested++;
            }
        }
        check(moved>0 && nested>0,"at least one real EFT installed nested rail was exercised");
        System.out.println("Real EFT: "+moved+" movable installed parts, including "+nested+" nested rails.");
    }

    private static void fixtures() {
        var gun=new GunType(new TypeFile(EnumType.gun,"rail-drag-fixture",false));
        var parentType=new AttachmentType(new TypeFile(EnumType.attachment,"rail-parent-fixture",false));
        var childType=new AttachmentType(new TypeFile(EnumType.attachment,"rail-child-fixture",false));
        var leafType=new AttachmentType(new TypeFile(EnumType.attachment,"rail-leaf-fixture",false));
        parentType.shortName="__rail_parent";childType.shortName="__rail_child";leafType.shortName="__rail_leaf";
        try {
            gun.modelScale=.2F;parentType.modelScale=4F;childType.modelScale=.07F;
            gun.slots=new ModifiableType.AttachableSlot[]{slot(.2F,.1F,-.05F,90,.025F,8),slot(.7F,0,0,0,.03F,4)};
            parentType.slots=new ModifiableType.AttachableSlot[]{slot(-.4F,.25F,.3F,-45,-.02F,12)};
            childType.slots=new ModifiableType.AttachableSlot[]{slot(.1F,-.12F,.07F,25,.015F,5)};
            parentType.attachOffsets=new float[]{0,.03125F};childType.attachOffsets=new float[]{0,.07F};
            var unrelated=node(parentType,0,1,0);var parent=node(parentType,0,4,1);
            var child=node(childType,0,3,1);var leaf=node(leafType,0,2,0);
            parent.pos=.13125F;unrelated.pos=-.8F;child.pos=.01F;leaf.pos=.03F;
            parent.children.add(child);child.children.add(leaf);
            child.zoomStep=7;child.dotFrame=9;child.paintjob=4;child.itemData=new CompoundTag();child.itemData.putString("retained","child metadata");
            leaf.itemData=new CompoundTag();leaf.itemData.putString("retained","leaf metadata");leaf.zoomStep=2;
            List<List<DefaultAttachment>> tree=new ArrayList<>();tree.add(new ArrayList<>(List.of(unrelated,parent)));tree.add(new ArrayList<>());
            int[] nestedPath={0,1,0,0};int[] rootPath={0,1};
            var originalPath=nestedPath.clone();
            float angle=.4F,yaw=.35F;
            var matrix=new Matrix4f().translate(120,80,0).rotateZ(angle).scale(300,-250,50).rotateY(yaw);
            var rotated=frame(matrix);var originalMatrix=new Matrix4f(matrix);
            var axis=GunModificationRailDrag.axis(rotated,gun,tree,nestedPath);
            check(axis!=null && axis.initialStep()==3 && axis.maxStep()==12,"nested sibling address selects the child owner and bounds");
            float horizontal=-.02F*(float)Math.cos(yaw)*300;
            near(axis.dx(),horizontal*(float)Math.cos(angle),"negative rail projects through independent scalar viewer rotation");
            near(axis.dy(),horizontal*(float)Math.sin(angle),"viewer roll creates the expected signed vertical drag direction");
            check(GunModificationRailDrag.stepFor(axis,axis.dx()*2,axis.dy()*2)==5,"negative world rail follows its on-screen direction");
            check(GunModificationRailDrag.stepFor(axis,-axis.dy()*3,axis.dx()*3)==3,"perpendicular motion cannot move the part");
            check(GunModificationRailDrag.stepFor(axis,axis.dx()*.49,axis.dy()*.49)==3,"less than half a projected step remains stationary");
            check(GunModificationRailDrag.stepFor(axis,axis.dx()*.51,axis.dy()*.51)==4,"more than half a projected step advances");
            check(GunModificationRailDrag.stepFor(axis,-axis.dx()*.51,-axis.dy()*.51)==2,"negative drag rounds to previous tick");
            check(GunModificationRailDrag.stepFor(axis,axis.dx()*100,axis.dy()*100)==12,"far drag clamps to upper endpoint");
            check(GunModificationRailDrag.stepFor(axis,-axis.dx()*100,-axis.dy()*100)==0,"reverse far drag clamps to zero");
            check(GunModificationRailDrag.stepFor(axis,Double.NaN,1)==3,"nonfinite input cannot jump the rail");
            check(GunModificationRailDrag.stepFor(new Axis(0,0,3,12),50,60)==3,"zero-length invalid gesture remains unchanged");
            var rootAxis=GunModificationRailDrag.axis(rotated,gun,tree,rootPath);
            near(rootAxis.dx(),.025F*(float)Math.cos(yaw)*300*(float)Math.cos(angle),"root rail uses root vector without attachment mesh scale");
            near(rootAxis.dy(),.025F*(float)Math.cos(yaw)*300*(float)Math.sin(angle),"root rail uses viewer roll once");
            var scaled=frame(new Matrix4f(matrix).scale(2));
            var larger=GunModificationRailDrag.axis(scaled,gun,tree,nestedPath);
            near(larger.dx(),axis.dx()*2,"preview zoom doubles GUI distance per rail tick");
            near(larger.dy(),axis.dy()*2,"zoom scales both drag components consistently");
            check(GunModificationRailDrag.axis(frame(new Matrix4f().rotateY((float)Math.PI/2)),gun,tree,nestedPath)==null,"end-on rail is not draggable");
            check(GunModificationRailDrag.axis(frame(new Matrix4f().scale(10)),gun,tree,nestedPath)==null,"less than four screen pixels of full rail is hidden");
            var childMount=parentType.slots[0];childMount.stepVector=-.0001F;childMount.step=1000;
            var fine=GunModificationRailDrag.axis(frame(new Matrix4f().scale(300)),gun,tree,nestedPath);
            check(fine!=null && Math.abs(fine.dx())<1,"subpixel ticks remain usable when whole rail is visible");
            check(GunModificationRailDrag.stepFor(fine,fine.dx()*200,0)==203,"fine long rails use full-range dot product");
            childMount.stepVector=-.02F;childMount.step=12;

            check("position".equals(GunModificationRailDrag.error(gun,tree,nestedPath,-1)),"negative tick rejected");
            check("position".equals(GunModificationRailDrag.error(gun,tree,nestedPath,13)),"tick beyond rail rejected");
            for(int[] invalid:List.of(new int[0],new int[]{0},new int[]{0,7},new int[]{0,1,1,0},new int[]{-1,0}))
                check("invalid".equals(GunModificationRailDrag.error(gun,tree,invalid,3)),"bad gesture address rejected");
            var same=node(childType,0,5,1);same.name=childType.shortName.toUpperCase(java.util.Locale.ROOT);parent.children.add(same);
            check("duplicate".equals(GunModificationRailDrag.error(gun,tree,nestedPath,5)),"same rail/type/tick/offset duplicate rejected case-insensitively");
            check(GunModificationRailDrag.preview(gun,tree,nestedPath,5)==tree,"invalid preview retains original object without changes");
            same.offset=0;
            check(GunModificationRailDrag.error(gun,tree,nestedPath,5)==null,"different authored offset is not a duplicate");
            var moved=GunModificationRailDrag.preview(gun,tree,nestedPath,5);var movedNode=GunAttachments.nodeAt(moved,nestedPath);
            check(moved!=tree && moved.getFirst()!=tree.getFirst() && movedNode!=child,"preview owns independent lists and node instances");
            near(movedNode.pos,-.02F*5+.07F,"negative movement preserves real scaled part offset");
            check(movedNode.step==5 && movedNode.offset==1 && movedNode.zoomStep==7 && movedNode.dotFrame==9 && movedNode.paintjob==4,"preview preserves optics and paintjob metadata");
            check(movedNode.itemData.equals(child.itemData) && movedNode.itemData!=child.itemData,"item component NBT is copied independently");
            check(movedNode.children.size()==1 && movedNode.children.getFirst()!=leaf && movedNode.children.getFirst().itemData.equals(leaf.itemData),"complete descendant metadata retained");
            near(movedNode.children.getFirst().pos,.03F,"child local position is unchanged when parent moves");
            near(moved.getFirst().getFirst().pos,-.8F,"unrelated sibling retains its exact position");
            near(moved.getFirst().get(1).pos,.13125F,"ancestor retains its exact position");
            movedNode.itemData.putString("retained","edited preview");movedNode.children.getFirst().itemData.putString("retained","edited leaf");
            check(child.itemData.getString("retained").equals("child metadata") && leaf.itemData.getString("retained").equals("leaf metadata"),"preview changes cannot leak into real component data");
            check(child.step==3 && child.pos==.01F && parent.children.size()==2,"real tree and source descendants remain unchanged");
            check(Arrays.equals(nestedPath,originalPath) && matrix.equals(originalMatrix),"gesture math preserves path and frame");
            child.offset=2;check("position".equals(GunModificationRailDrag.error(gun,tree,nestedPath,3)),"invalid saved offset rejected");child.offset=1;
            childMount.step=0;check(GunModificationRailDrag.axis(rotated,gun,tree,nestedPath)==null,"stationary socket has no drag axis");childMount.step=12;
            childMount.stepVector=Float.NaN;check(GunModificationRailDrag.axis(rotated,gun,tree,nestedPath)==null,"nonfinite authored vectors rejected");
        } finally {
            for(var type:List.of(parentType,childType,leafType)) {AttachmentType.attachments.remove(type);InfoType.infoTypes.remove(type);}
            InfoType.infoTypes.remove(gun);
        }
    }

    public static void main(String[] args)throws Exception {
        var pack=new OfflinePack(Path.of(args[0]));pack.readTypes();realEft(pack);fixtures();
        System.out.println("PASS: "+checks+" checks; real EFT movable rails, negative vectors, nested roll, zoom, end-on cutoff, fine ticks, bounds, duplicates and isolated metadata-preserving previews. No game launched.");
    }
}
