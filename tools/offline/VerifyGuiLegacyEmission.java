import com.flansmod.client.gui.GunModificationPreview;
import com.flansmod.client.gui.GunModificationView;
import com.flansmod.client.model.loader.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.joml.Vector3f;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;

/** The final variant's actual GUI vertices, including selected fire-mode light groups. No GL. */
public final class VerifyGuiLegacyEmission {
    static int checks,models;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static final class Capture implements VertexConsumer {
        final List<float[]> positions=new ArrayList<>();final List<Integer> colors=new ArrayList<>();
        public VertexConsumer addVertex(float x,float y,float z){positions.add(new float[]{x,y,z});return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){check(r==g&&g==b,"unselected surface keeps its tint neutral");colors.add(r);return this;}
        public VertexConsumer setUv(float u,float v){return this;}
        public VertexConsumer setUv1(int u,int v){return this;}
        public VertexConsumer setUv2(int u,int v){return this;}
        public VertexConsumer setNormal(float x,float y,float z){return this;}
    }
    public static void main(String[] args)throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        Method render=GunModificationPreview.class.getDeclaredMethod("renderMesh",PoseStack.class,VertexConsumer.class,BakedRig.class,boolean.class,int.class);
        render.setAccessible(true);
        for(String arg:args)try(var files=Files.walk(Path.of(arg))){
            for(var file:files.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".class"))
                    .filter(p->!Path.of(arg).relativize(p).startsWith("_disabled")).sorted().toList()){
                ModelGeometry.Model model;try(var in=Files.newInputStream(file)){model=BytecodeModel.fromStream(in);}
                var rig=BakedRig.bake(model,1F);if(!rig.hasEmissiveProfile())continue;models++;
                for(int mode:new int[]{0,1,2}){
                    var pose=new PoseStack();pose.scale(3,-3,3);pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(31));
                    var capture=new Capture();render.invoke(null,pose,capture,rig,false,mode);
                    var quads=new ArrayList<BakedRig.Quad>(rig.attachmentBody(mode));
                    for(var pass:rig.emissivePasses(mode))quads.addAll(pass.quads());
                    check(capture.positions.size()==quads.size()*4,file+" exact body+active light topology; no all-mode light leakage");
                    int vertex=0;
                    for(var quad:quads){
                        var emission=rig.emissiveLight(quad,mode);
                        var normal=pose.last().normal().transform(new Vector3f(quad.nx(),quad.ny(),quad.nz()));
                        float shade=GunModificationView.shade(normal);
                        int expected=emission==null?(int)(shade*255):255;
                        check(emission==null||emission.strength()==15||emission.absolute(),"real source default glow contract");
                        for(int i=3;i>=0;i--){
                            float[] actual=capture.positions.get(vertex);var p=pose.last().pose().transformPosition(new Vector3f(quad.positions()[i]));
                            check(Math.abs(actual[0]-p.x)<.00001F&&Math.abs(actual[1]-p.y)<.00001F&&Math.abs(actual[2]-p.z)<.00001F,"preview draws exactly the selected source geometry");
                            check(Math.abs(capture.colors.get(vertex)-expected)<=1,"glow remains bright, ordinary surface retains directional studio shade");vertex++;
                        }
                    }
                }
            }
        }
        check(models==45,"all 45 authored glow models checked in final GUI variant");
        System.out.println("PASS VerifyGuiLegacyEmission: "+checks+" checks; "+models+" original models, 3 modes, real preview colors/vertices; no game/GPU.");
    }
}
