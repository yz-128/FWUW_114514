import com.flansmod.client.gui.GunModificationPreview;
import com.flansmod.client.gui.GunModificationPreview.PickQuad;
import com.flansmod.client.gui.GunModificationView;
import com.flansmod.client.model.loader.BakedRig;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.lang.reflect.Field;
import java.util.*;

/** Pure GUI geometry and the actual preview entry point; no game or OpenGL. */
public final class VerifyGunModificationPicking {
    private static int checks;
    private static void check(boolean ok,String message) { checks++; if(!ok) throw new AssertionError(message); }
    private static PickQuad square(float x,float y,float size,float z,int... path) {
        return new PickQuad(new Vector3f(x,y,z),new Vector3f(x+size,y,z),
                new Vector3f(x+size,y+size,z),new Vector3f(x,y+size,z),path);
    }
    private static int[] pick(List<PickQuad> faces,double x,double y) {
        return GunModificationPreview.pickProjected(faces,x,y,0,0,100,100);
    }
    private static void field(Object object,String name,Object value)throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);
    }
    private static Object field(Object object,String name)throws Exception {
        Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);
    }
    @SuppressWarnings("unchecked")
    private static void actualPreview()throws Exception {
        var preview=new GunModificationPreview();
        var frame=new GunModificationView.Frame(new Matrix4f().translate(20,30,250).scale(2,-2,2),2,
                new Vector3f(),new Vector3f(10));
        field(preview,"renderedFrame",frame);
        field(preview,"renderedX",20);field(preview,"renderedY",15);
        field(preview,"renderedWidth",40);field(preview,"renderedHeight",40);
        var rig=new BakedRig();
        rig.quads.add(new BakedRig.Quad(new float[][]{{0,0,0},{10,0,0},{10,10,0},{0,10,0}},
                new float[][]{{0,0},{1,0},{1,1},{0,1}},0,0,1));
        var piece=Class.forName("com.flansmod.client.gui.GunModificationPreview$Piece");
        var constructor=piece.getDeclaredConstructor(BakedRig.class,ResourceLocation.class,Matrix4f.class,int[].class);
        constructor.setAccessible(true);
        int[] path={3,1,2,0};
        var pieces=(List<Object>)field(preview,"pieces");
        pieces.add(constructor.newInstance(rig,null,new Matrix4f().translate(2,0,1),path));
        check(Arrays.equals(preview.pickAttachment(30,20),path),"live entry applies both model-piece and last-rendered GUI transforms");
        check(preview.pickAttachment(22,20).length==0,"live entry rejects point outside translated attachment");
        check(preview.pickAttachment(30,12).length==0,"live entry applies the model scissor even where clipped geometry exists");
        int[] picked=preview.pickAttachment(30,20);picked[0]=99;
        check(path[0]==3 && preview.pickAttachment(30,20)[0]==3,"live entry never exposes mutable assembly identity");
        // The renderer emits 3,2,1,0. Its centre diagonal joins vertices 1 and 3 (both z=0),
        // not vertices 0 and 2; the latter would wrongly lift this face above the z=2 blocker.
        rig.quads.clear();
        rig.quads.add(new BakedRig.Quad(new float[][]{{0,0,0},{10,0,0},{10,10,10},{0,10,0}},
                new float[][]{{0,0},{1,0},{1,1},{0,1}},0,0,1));
        var blocker=new BakedRig();
        blocker.quads.add(new BakedRig.Quad(new float[][]{{0,0,2},{10,0,2},{10,10,2},{0,10,2}},
                new float[][]{{0,0},{1,0},{1,1},{0,1}},0,0,1));
        pieces.add(constructor.newInstance(blocker,null,new Matrix4f().translate(2,0,0),new int[0]));
        check(preview.pickAttachment(34,20).length==0,"live nonplanar shape follows the rendered quad diagonal when resolving occlusion");
        check(preview.frame()==frame,"frame accessor returns the frame actually used for drawing");
        preview.lockFrame(true);
        field(preview,"renderedFrame",null);
        check(field(preview,"frozenFrame")==frame,"clearing transient frame does not discard locked rail-drag camera");
        preview.lockFrame(false);
        check(field(preview,"frozenFrame")==null,"unlock releases the frozen framing matrix");
        check(preview.pickAttachment(30,20).length==0,"no rendered frame means no stale selectable geometry");
    }
    public static void main(String[] args)throws Exception {
        var rear=square(10,10,60,20,1,0);
        var front=square(25,25,30,80,2,1);
        check(Arrays.equals(pick(List.of(rear,front),40,40),new int[]{2,1}),"nearer attachment wins even when pieces overlap");
        check(Arrays.equals(pick(List.of(front,rear),40,40),new int[]{2,1}),"selection is independent of noncoplanar draw order");
        check(Arrays.equals(pick(List.of(rear,front),15,15),new int[]{1,0}),"uncovered rear attachment stays selectable");
        check(pick(List.of(rear,square(25,25,30,90)),40,40).length==0,"receiver blocks the attachment behind it");
        check(pick(List.of(rear,square(25,25,30,90,-1,-1)),40,40).length==0,"seated magazine blocks without becoming an attachment");
        var reversed=new PickQuad(front.d(),front.c(),front.b(),front.a(),front.path());
        check(Arrays.equals(pick(List.of(rear,reversed),40,40),front.path()),"negative-Y GUI winding does not affect hit testing");
        var slope=new PickQuad(new Vector3f(10,10,0),new Vector3f(70,10,120),
                new Vector3f(70,70,120),new Vector3f(10,70,0),new int[]{4,0});
        var flat=square(10,10,60,50,5,0);
        check(Arrays.equals(pick(List.of(slope,flat),20,40),flat.path()),"sloped face is behind at its low-depth end");
        check(Arrays.equals(pick(List.of(slope,flat),60,40),slope.path()),"sloped face is ahead at its high-depth end; centre sorting is insufficient");
        var triangle=new PickQuad(new Vector3f(10,10,1),new Vector3f(50,10,1),new Vector3f(10,50,1),new Vector3f(10,50,1),new int[]{6,0});
        check(pick(List.of(triangle),45,45).length==0,"bounding-box-only hits outside the actual triangle are rejected");
        check(pick(List.of(front),99,99).length==0,"outside geometry has no hit");
        check(pick(List.of(square(95,10,20,1,1,0)),100,20).length==0,"right scissor edge is excluded");
        check(pick(List.of(square(10,95,20,1,1,0)),20,100).length==0,"bottom scissor edge is excluded");
        check(pick(List.of(front),Double.NaN,40).length==0,"nonfinite mouse coordinates are rejected");
        var degenerate=new PickQuad(new Vector3f(10,10,500),new Vector3f(20,20,500),
                new Vector3f(30,30,500),new Vector3f(40,40,500),new int[]{7,0});
        check(Arrays.equals(pick(List.of(rear,degenerate),20,20),rear.path()),"zero-area face cannot hide a genuine hit");
        int[] result=pick(List.of(front),40,40);result[0]=88;
        check(front.path()[0]==2,"pure picking returns a copied path");
        actualPreview();
        System.out.println("PASS VerifyGunModificationPicking: "+checks+" checks; projected polygons, interpolated front depth, receiver/magazine blockers, mirrored winding, scissor, degenerate faces, immutable paths and live preview frame integration. No game or OpenGL.");
    }
}
