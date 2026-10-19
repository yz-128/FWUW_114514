import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import com.flansmod.common.GunSightState;
import com.flansmod.common.types.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import org.joml.*;
import java.lang.Math;
import java.nio.file.Path;
import java.util.*;

/** Every declared optic and texture frame, through production vertex emission; no Minecraft/GL. */
public final class VerifyAllSights {
    static int checks,optics,irons,frames,scopes,aliasModels,correctedAxes;
    static final List<OfflinePack> packs=new ArrayList<>();
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static void near(float a,float b,String message){check(Float.isFinite(a)&&Math.abs(a-b)<2E-4F,message+" actual="+a+" expected="+b);}
    static boolean exists(ResourceLocation id){return packs.stream().anyMatch(p->p.has(id));}
    static final class Recorder implements MultiBufferSource,VertexConsumer {
        int vertices;
        public VertexConsumer getBuffer(RenderType type){return this;}
        public VertexConsumer addVertex(float x,float y,float z){check(Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(z),"finite emitted vertex");vertices++;return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){return this;}
        public VertexConsumer setUv(float u,float v){check(Float.isFinite(u)&&Float.isFinite(v),"finite emitted UV");return this;}
        public VertexConsumer setUv1(int u,int v){return this;}
        public VertexConsumer setUv2(int u,int v){return this;}
        public VertexConsumer setNormal(float x,float y,float z){check(Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(z),"finite emitted normal");return this;}
    }
    static void aliases(ModelGeometry.Model model) {
        if(model.fmumVectorArrays.containsKey("sightDotBorder")) {
            check(model.fmumVectorArrays.get("reticleBorder")==model.fmumVectorArrays.get("sightDotBorder"),"legacy aperture normalization");aliasModels++;
            check(model.fmumVectorArrays.get("reticleAt")==model.fmumVectorArrays.get("dotAt"),"legacy rays normalization");
        }
        if(model.fmumPose.containsKey("scopeGlassCenter"))check(Arrays.equals(model.fmumPose.get("scopeGlassCenter"),model.fmumPose.get("pupilRefCenter")),"legacy ocular placement retained");
        if(model.fmumScalars.containsKey("divisionScale"))check(Objects.equals(model.fmumScalars.get("divisionScale"),model.fmumScalars.get("reticleScale")),"legacy dot size retained");
    }
    static GunType fixture(AttachmentType at) {
        var file=new TypeFile(EnumType.gun,"AllSights_"+at.shortName,false);
        file.lines.add("ShortName AllSights_"+at.shortName);
        var gun=new GunType(file);gun.read(file);
        gun.slots=new ModifiableType.AttachableSlot[]{new ModifiableType.AttachableSlot()};
        var node=new ModifiableType.DefaultAttachment();node.name=at.shortName;node.slot=0;
        gun.defaultAttachments=List.of(List.of(node));return gun;
    }
    static void optic(AttachmentType at) {
        var rig=ModelCache.rigFor(at,at.modelScale);
        check(rig!=null && !rig.quads.isEmpty(),at.shortName+" body geometry");
        aliases(rig.animation);
        boolean dot=at.lightStuffModel!=null && !at.lightStuffModel.equalsIgnoreCase("none");
        boolean scope=at.scopeGlassModel!=null && !at.scopeGlassModel.equalsIgnoreCase("none");
        if(!dot && !scope){irons++;return;}
        if(at.isFoldedVariant())return;
        optics++;
        var model=rig.animation;
        var center=FmumSightPicture.aimCenter(model,at.aimCenterY,at.aimCenterZ);
        if(Math.abs(center.y-at.aimCenterY)>1E-5 || Math.abs(center.z-at.aimCenterZ)>1E-5)correctedAxes++;
        if(dot) {
            check(model.fmumVectorArrays.getOrDefault("reticleBorder",new float[0][]).length>=3,at.shortName+" aperture");
            check(model.fmumVectorArrays.getOrDefault("reticleAt",new float[0][]).length>0,at.shortName+" rays");
        }
        var gun=fixture(at);
        var selected=GunSightState.scopeSight(gun);
        if(scope){check(selected!=null,at.shortName+" supplies selected scope");scopes++;}
        var glass=scope?ModelCache.rigForReference(at.scopeGlassModel,1F):null;
        if(scope)check(glass!=null && !glass.quads.isEmpty(),at.shortName+" glass geometry");
        for(int frame=0;frame<Math.max(1,at.lightTextureCount);frame++) {
            if(dot) {
                var skin=Textures.forReticle(at,frame,VerifyAllSights::exists);
                check(skin!=null && exists(skin),at.shortName+" real reticle skin frame "+frame+" "+skin);
                var dots=ModelCache.rigForReference(at.lightStuffModel,1F,skin);
                check(dots!=null && !dots.quads.isEmpty(),at.shortName+" reticle geometry");
            }
            gun.defaultAttachments.getFirst().getFirst().dotFrame=frame;
            for(int step=0;step<Math.max(1,at.fovZoomLevels.length);step++) {
                gun.defaultAttachments.getFirst().getFirst().zoomStep=step;
                for(float distance:new float[]{.5F,1F})for(boolean mirrored:new boolean[]{false,true}) {
                    var eye=new Vector3f(scope?glass.minX*at.modelScale+FmumSightPicture.vector(model,"pupilRefCenter",0,0,0).x*at.modelScale-distance:-distance,center.y,center.z);
                    var recorder=new Recorder();
                    if(scope) {
                        var toEye=new Matrix4f().rotationY((float)Math.PI/2).translate(new Vector3f(eye).negate());
                        var projection=new Matrix4f().perspective((float)Math.toRadians(70),16F/9F,.01F,100F);
                        double fov=70/at.zoomDivisor(step);
                        ScopeReticleRenderer.render(new PoseStack(),recorder,gun,selected,rig,glass,toEye,projection,fov,0,mirrored);
                    } else FmumSightRenderer.renderDot(new PoseStack(),recorder,at,gun.defaultAttachments.getFirst().getFirst(),rig,
                            eye,FmumSightPicture.pupilDistance(model,at.modelScale,eye),1,0,mirrored);
                    if(dot)check(recorder.vertices>0,at.contentPack+"/"+at.shortName+" frame="+frame+" step="+step+" distance="+distance+" emits reticle");
                    check(recorder.vertices%4==0,"complete reticle quads");frames++;
                }
            }
        }
        System.out.printf(Locale.ROOT,"CHECK %s/%s dot=%s scope=%s frames=%d steps=%d axis=(%.5f,%.5f)%n",at.contentPack,at.shortName,dot,scope,at.lightTextureCount,Math.max(1,at.fovZoomLevels.length),center.y,center.z);
    }
    static void projection() {
        var screen=new Matrix4f().perspective((float)Math.toRadians(70),16F/9F,.01F,100);
        var good=new float[][]{{-.1F,-.1F,-1},{.1F,-.1F,-1},{.1F,.1F,-1},{-.1F,.1F,-1}};
        check(FmumSightPicture.screenQuad(screen,good)!=null,"visible world-image quad emits all four corners");
        var crossing=good.clone();crossing[2]=new float[]{.1F,.1F,1};
        check(FmumSightPicture.screenQuad(screen,crossing)==null,"eye-crossing lens never emits an incomplete quad");
        var bad=good.clone();bad[1]=new float[]{Float.NaN,0,-1};
        check(FmumSightPicture.screenQuad(screen,bad)==null,"invalid lens vertex rejects whole primitive");
        var quad=new BakedRig.Quad(new float[][]{{0,1,-1},{0,1,1},{0,-1,1},{0,-1,-1}},new float[][]{{0,0},{1,0},{1,1},{0,1}},-1,0,0);
        for(float aspect:new float[]{4F/3,16F/9,21F/9})for(float fov:new float[]{50,70,100})for(float power:new float[]{1,2,7})for(float roll:new float[]{0,45,90}) {
            var eye=new Vector3f(-2,.04F,-.03F);
            var view=new Matrix4f().rotationY((float)Math.PI/2).rotateX((float)Math.toRadians(roll)).translate(new Vector3f(eye).negate());
            var projection=new Matrix4f().perspective((float)Math.toRadians(fov),aspect,.01F,100F);
            var inverse=new Matrix4f(projection).mul(view).invert();
            double worldFov=Math.toDegrees(2*Math.atan(Math.tan(Math.toRadians(fov)/2)/power));
            var target=new Vector3f(2,.1F,.2F);
            var ray=ScopeReticleRenderer.lensRay(target,view,projection,inverse,worldFov);
            check(ray!=null,"world ray unprojects");
            var uv=ScopeReticleRenderer.glassUv(quad,eye,ray);check(uv!=null,"live eye intersects actual glass");
            var actual=ScopeReticleRenderer.glassPosition(quad,uv[0],uv[1]);
            var actualClip=projection.transform(view.transform(new Vector4f(actual,1)));
            var expectedView=view.transform(new Vector4f(target,1));expectedView.x*=power;expectedView.y*=power;
            var expectedClip=projection.transform(expectedView);
            near(actualClip.x/actualClip.w,expectedClip.x/expectedClip.w,"reticle/world horizontal alignment at all aspect ratios, FOVs and rolls");
            near(actualClip.y/actualClip.w,expectedClip.y/expectedClip.w,"reticle/world vertical alignment at all aspect ratios, FOVs and rolls");
        }
        var type=new AttachmentType(new TypeFile(EnumType.attachment,"UnnumberedReticleFixture",false));
        type.lightStuffTexture="Custom/Single.png";type.lightTextureCount=3;
        var expected=ResourceLocation.parse("flansmod:skins/custom/single.png");
        check(expected.equals(Textures.forReticle(type,2,expected::equals)),"single unsuffixed PNG is a valid reticle fallback");
        var numbered=ResourceLocation.parse("flansmod:skins/custom/single2.png");
        check(numbered.equals(Textures.forReticle(type,2,id->id.equals(expected)||id.equals(numbered))),"requested numbered frame wins over unsuffixed fallback");
        var underscore=ResourceLocation.parse("flansmod:skins/custom/single_2.png");
        check(underscore.equals(Textures.forReticle(type,2,underscore::equals)),"older underscore-numbered textures resolve");
        type.lightStuffTexture="example:optics/red.png";
        var namespaced=ResourceLocation.parse("example:optics/red2.png");
        check(namespaced.equals(Textures.forReticle(type,2,namespaced::equals)),"namespaced numbered PNG retains extension");
    }
    public static void main(String[] args)throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        for(String arg:args){var pack=new OfflinePack(Path.of(arg));pack.readTypes();packs.add(pack);}
        var sights=new ArrayList<>(AttachmentType.attachments);projection();
        for(var at:sights)if("sight".equalsIgnoreCase(at.itsType))optic(at);
        System.out.printf(Locale.ROOT,"PASS: %d checks, %d optical definitions, %d mechanical sights, %d scopes, %d rendered variants, %d legacy alias models, %d invalid authored axes corrected. Offline only; no game/OpenGL.%n",checks,optics,irons,scopes,frames,aliasModels,correctedAxes);
    }
}
