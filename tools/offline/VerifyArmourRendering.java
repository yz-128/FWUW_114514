import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import com.flansmod.common.*;
import com.flansmod.common.types.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import java.nio.file.*;
import java.util.*;
import org.joml.*;

/** Real EFT armour geometry, resource aliases, joint transforms and cache isolation. No game. */
public final class VerifyArmourRendering {
    static int checks;
    static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    static void near(float a,float b,String message){check(Float.isFinite(a)&&java.lang.Math.abs(a-b)<.00005F,message+": "+a+" != "+b);}
    static final class Recorder implements VertexConsumer {
        final List<Vector3f> positions=new ArrayList<>();
        public VertexConsumer addVertex(float x,float y,float z){check(Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(z),"finite vertex");positions.add(new Vector3f(x,y,z));return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){check(a==255,"opaque armour");return this;}
        public VertexConsumer setUv(float u,float v){check(Float.isFinite(u)&&Float.isFinite(v),"finite armour UV");return this;}
        public VertexConsumer setUv1(int u,int v){return this;}
        public VertexConsumer setUv2(int u,int v){return this;}
        public VertexConsumer setNormal(float x,float y,float z){near(x*x+y*y+z*z,1,"unit transformed normal");return this;}
    }
    static HumanoidModel<?> body(){return new HumanoidModel<>(HumanoidModel.createMesh(CubeDeformation.NONE,0).getRoot().bake(64,32));}
    static BakedRig one(ModelGeometry.Model model){return ArmourGeometry.bake(model,1,1,1);}
    public static void main(String[] args)throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        // Known renderer pivot: legacy armour overwrites it with the active joint every frame.
        var model=new ModelGeometry.Model();var part=new ModelGeometry.Part();part.name="leftArmModel";
        var box=new ModelGeometry.Box();box.w=2;box.h=3;box.d=4;box.x=-1;box.y=2;box.z=-3;
        box.renderer="leftArmModel#0";box.hasPivot=true;box.pivotX=100;box.pivotY=-200;box.rotationZ=90;
        part.boxes.add(box);model.parts.put(part.name,part);
        var fixed=one(model);check(fixed.maxX<1 && fixed.minX>-.2,"constructor pivots are replaced, never added twice");
        check(box.pivotX==100 && box.rotationZ==90,"bake leaves cached source geometry intact");
        var pose=body();pose.young=false;pose.leftArm.setPos(7,11,-5);pose.leftArm.setRotation(.4F,-.3F,.7F);
        var rigModel=new LegacyArmourModel(fixed,pose);var result=new Recorder();
        rigModel.renderToBuffer(new PoseStack(),result,123,0,-1);
        var expected=new PoseStack();pose.leftArm.translateAndRotate(expected);
        int index=0;
        for(var quad:fixed.quads)for(var p:quad.positions()) {
            var point=expected.last().pose().transformPosition(new Vector3f(p));var actual=result.positions.get(index++);
            near(actual.x,point.x,"correct arm joint x");near(actual.y,point.y,"correct arm joint y");near(actual.z,point.z,"correct arm joint z");
        }
        check(index==result.positions.size(),"no extra armour vertices");
        check(LegacyArmourModel.joint(pose,"headModel")==pose.head,"masks follow head even in leggings slot");
        check(LegacyArmourModel.joint(pose,"leftLegModel")==pose.leftLeg,"boots follow leg joint");
        pose.leftLeg.xRot=-.7F;pose.rightLeg.xRot=.4F;
        near(LegacyArmourModel.joint(pose,"skirtFrontModel").xRot,-.7F,"front skirt min leg pitch");
        near(LegacyArmourModel.joint(pose,"skirtRearModel").xRot,.4F,"rear skirt max leg pitch");
        int armours=0,faces=0,missing=0;
        for(String path:args){var pack=new OfflinePack(Path.of(path));pack.readTypes();}
        java.util.function.Predicate<ResourceLocation> exists=id->ContentPackIndex.current().find("assets/"+id.getNamespace()+"/"+id.getPath(),true).isPresent();
        var generated=GeneratedAssets.build();
        for(var type:InfoType.infoTypes)if(type instanceof ArmourType armour){
            var texture=Textures.forArmour(armour,exists);
            if(texture==null || !exists.test(texture)){System.out.println("MISSING TEXTURE: "+armour.shortName+" -> "+texture);missing++;continue;}
            check(true,"original armour texture: "+armour.shortName);
            var rig=ModelCache.rigForArmour(armour,texture);check(rig!=null && !rig.quads.isEmpty(),"real worn mesh: "+armour.shortName);
            check(rig==ModelCache.rigForArmour(armour,texture),"worn cache reused");
            var generic=ModelCache.rigForReference(armour.modelString,armour.modelScale,texture);
            check(generic!=rig,"armour bake isolated from generic rig cache");
            for(String group:rig.quadsByPart.keySet())check(LegacyArmourModel.joint(pose,group)!=null,"supported armour group: "+group);
            var sink=new Recorder();new LegacyArmourModel(rig,pose).renderToBuffer(new PoseStack(),sink,123,0,-1);
            check(!sink.positions.isEmpty(),"wear renderer emits visible mesh: "+armour.shortName);
            String registry=ItemAssets.registryPathOf(armour);
            for(int layer=1;layer<=2;layer++){
                var alias=ResourceLocation.fromNamespaceAndPath("flansmod","textures/models/armor/legacy/"+registry+"_layer_"+layer+".png");
                var supplier=generated.getResource(PackType.CLIENT_RESOURCES,alias);check(supplier!=null,"modern armour texture alias exists");
                byte[] wanted=ContentPackIndex.current().find("assets/"+texture.getNamespace()+"/"+texture.getPath(),true).orElseThrow().readAllBytes();
                try(var input=supplier.get()){check(Arrays.equals(input.readAllBytes(),wanted),"alias preserves original pixels and layer selection");}
            }
            faces+=rig.quads.size();armours++;
        }
        check(armours>0,"loaded actual armour packs");
        check(missing==0,"all original armour textures resolve: missing "+missing);
        System.out.println("PASS VerifyArmourRendering: "+checks+" checks, "+armours+" armours, "+faces+" faces");
    }
}
