import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import com.flansmod.common.GunSightState;
import com.flansmod.common.types.*;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.client.renderer.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;

/** Exercises the actual framebuffer bake (not the obsolete glass-overlay entry point).
 * A second raster draws the original meshes through the 1.7.10 parent matrix and GPU projection.
 * World is synthetic; this is an invisible GPU test, not an in-game/shader-pack acceptance run. */
public final class VerifyScopeBakedPipeline {
    static final int SIZE=512;
    static int checks,cases,scene;
    static Path output;
    static final Map<RenderType,Integer> textures=new HashMap<>();
    static RenderType layer(net.minecraft.resources.ResourceLocation texture){
        try{var method=ScopeFramebufferOverlays.class.getDeclaredMethod("type",net.minecraft.resources.ResourceLocation.class);
            method.setAccessible(true);return (RenderType)method.invoke(null,texture);}
        catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
    }
    static void imageViewState()throws Exception{
        var constructor=Class.forName("com.flansmod.client.render.ScopeFramebufferOverlays$ImageView").getDeclaredConstructor(int.class,int.class,double.class);
        constructor.setAccessible(true);var stack=RenderSystem.getModelViewStack();stack.pushMatrix();stack.translate(.2F,.3F,.4F);RenderSystem.applyModelViewMatrix();
        var modelView=new Matrix4f(RenderSystem.getModelViewMatrix());var original=new Matrix4f().scaling(2,3,4);
        RenderSystem.setProjectionMatrix(original,com.mojang.blaze3d.vertex.VertexSorting.DISTANCE_TO_ORIGIN);RenderSystem.setShaderColor(.2F,.3F,.4F,.5F);
        RenderSystem.viewport(7,9,31,27);RenderSystem.depthFunc(GL11.GL_GREATER);RenderSystem.depthMask(true);RenderSystem.enableDepthTest();RenderSystem.enableCull();
        try{for(int[] size:new int[][]{{854,480},{480,854},{512,512}}){
            try(var state=(AutoCloseable)constructor.newInstance(size[0],size[1],30D)){
                int square=Math.min(size[0],size[1]);int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
                check(Arrays.equals(viewport,new int[]{(size[0]-square)/2,(size[1]-square)/2,square,square}),"actual ImageView uses the central square viewport");
                var p=RenderSystem.getProjectionMatrix();float focal=(float)(1/Math.tan(Math.toRadians(15)));
                check(Math.abs(p.m00()-focal)<1E-5&&Math.abs(p.m11()-focal)<1E-5,"actual ImageView applies square image perspective exactly once");
                check(RenderSystem.getModelViewMatrix().equals(new Matrix4f(),1E-6F),"actual ImageView removes world/hand model-view");
                check(!GL11.glIsEnabled(GL11.GL_DEPTH_TEST)&&!GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),"image bake ignores world depth");
            }
            int[] viewport=new int[4];GL11.glGetIntegerv(GL11.GL_VIEWPORT,viewport);
            check(Arrays.equals(viewport,new int[]{7,9,31,27}),"actual ImageView restores viewport");
            check(RenderSystem.getProjectionMatrix().equals(original,1E-6F)&&RenderSystem.getModelViewMatrix().equals(modelView,1E-6F),"actual ImageView restores both caller matrices");
            check(GL11.glIsEnabled(GL11.GL_DEPTH_TEST)&&GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)&&GL11.glGetInteger(GL11.GL_DEPTH_FUNC)==GL11.GL_GREATER,"actual ImageView restores depth state");
            check(GL11.glIsEnabled(GL11.GL_CULL_FACE),"actual ImageView restores cull state");
            check(Math.abs(RenderSystem.getShaderColor()[3]-.5F)<1E-6F,"actual ImageView restores caller color");
        }}finally{stack.popMatrix();RenderSystem.applyModelViewMatrix();RenderSystem.viewport(0,0,SIZE,SIZE);RenderSystem.depthFunc(GL11.GL_LEQUAL);RenderSystem.disableCull();RenderSystem.setShaderColor(1,1,1,1);}
    }
    static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
    static Matrix4f base(){float d=(float)(Math.PI/180);
        return new Matrix4f().translate(.56F,-.52F,-.72F).rotateY(45*d).scale(.4F)
            .translate(0,-.3F,0).scale(1.5F).rotateY(50*d).rotateZ(335*d).translate(-.9375F,-.0625F,0)
            .rotateZ(20*d).rotateY(-5*d).translate(.15F,.375F,-1.005F).rotateX(.5F*d).rotateZ(5*d)
            .translate(0,.7F/160-.03F,-.35F/160);}
    static Vector3f mount(GunSightState.Sight s){float a=(float)Math.toRadians(s.roll()-s.type().aimCenterX);
        return new Vector3f(s.x(),s.y()-s.type().aimCenterY*(float)Math.cos(a)+s.type().aimCenterZ*(float)Math.sin(a),
                s.z()-s.type().aimCenterY*(float)Math.sin(a)-s.type().aimCenterZ*(float)Math.cos(a));}
    static Matrix4f physical(GunSightState.Sight s){return new Matrix4f().translate(mount(s)).rotateX((float)Math.toRadians(s.roll()-s.type().aimCenterX));}
    static Vector3f vector(ModelGeometry.Model m,String name,float x,float y,float z){return FmumSightPicture.vector(m,name,x,y,z);}
    static Vector3f intersect(Vector3f far,float[][] border,Vector3f normal,boolean bounded){
        var eye=new Vector3f(-81.5F/160,.4F,0);var direction=new Vector3f(far).sub(eye);
        float denominator=direction.dot(normal);if(Math.abs(denominator)<1E-7F)return null;
        float t=new Vector3f(border[0]).sub(eye).dot(normal)/denominator;if(t<0)return null;
        var hit=direction.mul(t).add(eye);if(!bounded)return hit;
        float sign=0;
        for(int i=0;i<border.length;i++){var a=new Vector3f(border[i]);var b=new Vector3f(border[(i+1)%border.length]);
            float side=b.sub(a).cross(new Vector3f(hit).sub(a)).dot(normal);
            if(Math.abs(side)<1E-6F)continue;if(sign==0)sign=Math.signum(side);else if(sign*side<0)return null;}
        return hit;
    }
    static final class Batches implements MultiBufferSource {
        final double fov; Batches(double fov){this.fov=fov;}
        final List<RenderType> materials=new ArrayList<>();final List<VerifyScopeComposition.Capture> batches=new ArrayList<>();
        public VertexConsumer getBuffer(RenderType type){materials.add(type);var c=new VerifyScopeComposition.Capture();batches.add(c);return c;}
        void draw(){for(int b=0;b<batches.size();b++){
            var triangles=batches.get(b);check(triangles.vertices.size()%3==0,"bake emits triangles");
            var quads=new VerifyScopeComposition.Capture();
            for(int i=0;i<triangles.vertices.size();i+=3){quads.vertices.add(triangles.vertices.get(i));quads.vertices.add(triangles.vertices.get(i+1));
                quads.vertices.add(triangles.vertices.get(i+2));quads.vertices.add(triangles.vertices.get(i+2));}
            Integer texture=textures.get(materials.get(b));check(texture!=null,"known authored atlas "+materials.get(b));
            VerifyScopeComposition.draw(quads,texture,new Matrix4f().perspective((float)Math.toRadians(fov),1,.05F,1000));}}
    }
    static void fill(){RenderSystem.disableScissor();RenderSystem.colorMask(true,true,true,true);RenderSystem.disableDepthTest();RenderSystem.depthMask(false);
        var c=new VerifyScopeComposition.Capture();for(float[] p:new float[][]{{-1,-1},{1,-1},{1,1},{-1,1}})
            c.addVertex(p[0],p[1],0).setUv((p[0]+1)/2,(p[1]+1)/2).setColor(1F,1F,1F,1F);
        VerifyScopeComposition.draw(c,scene,new Matrix4f());}
    static BufferedImage pixels(){var data=BufferUtils.createByteBuffer(SIZE*SIZE*4);GL11.glReadPixels(0,0,SIZE,SIZE,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,data);
        var img=new BufferedImage(SIZE,SIZE,BufferedImage.TYPE_INT_RGB);for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){int i=(y*SIZE+x)*4;
            img.setRGB(x,SIZE-1-y,(data.get(i)&255)<<16|(data.get(i+1)&255)<<8|(data.get(i+2)&255));}return img;}
    static void nativeReticle(GunType gun,GunSightState.Sight scope,Matrix4f toEye,double fov,float ads){
        var scopeModel=ModelCache.load(scope.type());var objective=physical(scope).transformPosition(vector(scopeModel,"scopeObjectiveLensCenter",0,0,0));
        float scopeRoll=scope.roll()-scope.type().aimCenterX;
        var placement=new Matrix4f().translate(-objective.x-69.5F/160,.4F,0).rotateX((float)Math.toRadians(-scopeRoll*ads)).translate(0,-objective.y,-objective.z);
        var projection=new Matrix4f().perspective((float)Math.toRadians(fov),1,.05F,1000);
        var scopeInverse=physical(scope).invert();var live=new Matrix4f(base()).invert().mul(toEye);
        for(var sight:GunSightState.selectedGroup(gun).sights()){
            var at=sight.type();if(at.isFoldedVariant()||at.lightStuffModel==null||at.lightStuffModel.equalsIgnoreCase("none"))continue;
            var m=ModelCache.load(at);var border=m.fmumVectorArrays.get("reticleBorder");if(border==null||border.length<3)continue;
            var virtual=new Matrix4f(placement).mul(physical(sight));float[][] plane=new float[border.length][];
            for(int i=0;i<border.length;i++){var p=virtual.transformPosition(new Vector3f(border[i]));plane[i]=new float[]{p.x,p.y,p.z};}
            var n=virtual.transformDirection(vector(m,"normalVecOfDotBorder",1,0,0));
            var rays=m.fmumVectorArrays.getOrDefault("reticleAt",new float[][]{{0,0,0}});if(rays.length==0)continue;
            var rayMatrix=new Matrix4f(live).mul(scopeInverse).mul(physical(sight));
            var anchor=intersect(rayMatrix.transformPosition(new Vector3f(rays[0])),plane,n,false);if(anchor==null)continue;
            float scale=FmumActionPose.scalar(m,"reticleScale",1);
            if(FmumActionPose.scalar(m,"relativeReticleSize",0)!=0&&at.fovZoomLevels.length>0)
                scale*=at.fovZoomLevels[0]/at.fovZoomLevels[Math.clamp(sight.node().zoomStep,0,at.fovZoomLevels.length-1)];
            var rig=ModelCache.rigForReference(at.lightStuffModel,1,Textures.forReticle(at,sight.node().dotFrame));
            var transform=new Matrix4f(base()).translate(anchor).rotateX((float)Math.toRadians(sight.roll()-at.aimCenterX-scopeRoll*ads)).scale(1,scale,scale);
            for(int i=0;i<rays.length;i++){if(intersect(rayMatrix.transformPosition(new Vector3f(rays[i])),plane,n,true)==null)continue;
                var parts=rays.length==1?rig.quads:rig.quadsByRenderer.get("defaultModel#"+i);if(parts==null)continue;
                var pose=new PoseStack();pose.mulPose(transform);var capture=new VerifyScopeComposition.Capture();
                RigRenderer.renderPart(pose,capture,parts,15728880,0,1,1,1,1,false);
                VerifyScopeComposition.draw(capture,textures.get(layer(Textures.forReticle(at,sight.node().dotFrame))),projection);}
        }
    }
    static void nativeMask(GunSightState.Sight scope,Matrix4f toEye,double fov){
        var type=scope.type();var model=ModelCache.load(type);
        var skin=Textures.forReticle(type,scope.node().dotFrame);
        var mask=ModelCache.rigForReference(type.blackMaskModel,1,skin);if(mask==null)return;
        var objective=new Matrix4f(base()).invert().mul(toEye).transformPosition(vector(model,"scopeObjectiveLensCenter",0,0,0));
        objective.sub(-81.5F/160,.4F,0);if(objective.x<=0)return;
        float y=-objective.y/(float)Math.hypot(objective.x,objective.z)*FmumActionPose.scalar(model,"scopeMaskMultTransY",.4F);
        float z=-objective.z/objective.x*FmumActionPose.scalar(model,"scopeMaskMultTransZ",.4F);
        float distance=vector(model,"pupilRefCenter",0,0,0).mul(type.modelScale).distance(new Matrix4f(toEye).invert().transformPosition(new Vector3f()));
        int step=Math.clamp(scope.node().zoomStep,0,Math.max(0,type.fovZoomLevels.length-1));
        float raw=type.fovZoomLevels.length==0?type.FOVZoomLevel:type.fovZoomLevels[step];
        float first=type.fovZoomLevels.length==0?raw:type.fovZoomLevels[0];
        float scale=FmumActionPose.scalar(model,"scopeMaskScale",.02F)*first/raw/distance
            *(float)Math.min(1,FmumActionPose.scalar(model,"minPupilDistance",.3F)/Math.pow(distance,FmumActionPose.scalar(model,"pupilDistancePower",3)));
        var pose=new PoseStack();pose.mulPose(base().translate(FmumActionPose.scalar(model,"scopeMaskXTrans",-.409375F),.4F+y,z).scale(1,scale,scale));
        var capture=new VerifyScopeComposition.Capture();RigRenderer.renderPart(pose,capture,mask.quads,15728880,0,1,1,1,1,false);
        var projection=new Matrix4f().perspective((float)Math.toRadians(fov),1,.05F,1000);
        VerifyScopeComposition.draw(capture,textures.get(layer(skin)),projection);
    }
    static void lensContract(GunSightState.Sight sight){
        var glass=ModelCache.rigForReference(sight.type().scopeGlassModel,1);var capture=new VerifyScopeComposition.Capture();
        FmumSightRenderer.renderLensSurface(new PoseStack(),capture,glass,new Matrix4f(),1,false);
        check(capture.vertices.size()==glass.quads.size()*4,"all original glass faces retained "+sight.type().shortName);
        int k=0;for(var q:glass.quads)for(int i=0;i<q.positions().length;i++){
            var v=capture.vertices.get(k++);var uv=FmumSightRenderer.framebufferUv(glass,q.uvs()[i][0],q.uvs()[i][1]);
            check(Math.abs(v[3]-uv[0])<1E-5F&&Math.abs(v[4]-uv[1])<1E-5F,"authored glass UV extent with framebuffer axis conversion");}
    }
    static void mountedImage(GunType gun,GunSightState.Sight scope,Matrix4f toEye,String label)throws Exception{
        int previous=GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);int image=GL11.glGenTextures();
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);RenderSystem.bindTexture(image);
        GL11.glCopyTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGB,0,0,SIZE,SIZE,0);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL11.GL_REPEAT);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL11.GL_REPEAT);
        var main=new TextureTarget(VerifyScopeComposition.W,VerifyScopeComposition.H,true,false);
        try{main.bindWrite(true);RenderSystem.disableScissor();RenderSystem.depthMask(true);RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.clearColor(.15F,.22F,.28F,1);RenderSystem.clearDepth(1);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,false);
            var projection=new Matrix4f().perspective((float)Math.toRadians(70),VerifyScopeComposition.W/(float)VerifyScopeComposition.H,.005F,1000);
            var weapon=VerifyMountedScopes.gunPose(gun);VerifyMountedScopes.body(gun,weapon,projection);
            VerifyMountedScopes.children(gun,gun.defaultAttachments.stream().flatMap(List::stream).toList(),weapon,projection);
            var before=VerifyScopeComposition.pixels();var rig=ModelCache.load(scope.type());var glass=ModelCache.rigForReference(scope.type().scopeGlassModel,1);
            var lens=new PoseStack();lens.mulPose(new Matrix4f(toEye).translate(vector(rig,"pupilRefCenter",0,0,0).mul(scope.type().modelScale))
                .scale(vector(rig,"scopeGlassModelScale",1,1,1).mul(scope.type().modelScale)));
            var mesh=new VerifyScopeComposition.Capture();FmumSightRenderer.renderLensSurface(lens,mesh,glass,new Matrix4f(projection).mul(lens.last().pose()),1,false);
            ScopeRenderLayers.composite(()->VerifyScopeComposition.draw(mesh,image,projection));var old=VerifyScopeComposition.pixels();
            ImageIO.write(old,"png",output.resolve(label+"-old-glass-no-depth-write.png").toFile());
            RenderSystem.depthMask(true);RenderSystem.clearColor(.15F,.22F,.28F,1);RenderSystem.clearDepth(1);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,false);
            VerifyMountedScopes.body(gun,weapon,projection);VerifyMountedScopes.children(gun,gun.defaultAttachments.stream().flatMap(List::stream).toList(),weapon,projection);
            ScopeRenderLayers.glass(()->VerifyScopeComposition.draw(mesh,image,projection,true));var after=VerifyScopeComposition.pixels();
            ImageIO.write(after,"png",output.resolve(label+"-mounted-glass.png").toFile());int changed=0;
            RenderSystem.depthMask(true);RenderSystem.clearColor(.15F,.22F,.28F,1);RenderSystem.clearDepth(1);RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT,false);
            VerifyMountedScopes.body(gun,weapon,projection);VerifyMountedScopes.children(gun,gun.defaultAttachments.stream().flatMap(List::stream).toList(),weapon,projection);
            // Native item drawing leaves depth writes enabled while the complete glass model
            // samples IMAGE. This is independent of the production glass state guard.
            RenderSystem.enableDepthTest();RenderSystem.depthFunc(GL11.GL_LEQUAL);RenderSystem.depthMask(true);
            VerifyScopeComposition.draw(mesh,image,projection,true);var nativeGlass=VerifyScopeComposition.pixels();int selfMismatch=0,oldDifference=0;
            for(int y=0;y<VerifyScopeComposition.H;y++)for(int x=0;x<VerifyScopeComposition.W;x++)if(before.getRGB(x,y)!=after.getRGB(x,y))changed++;
            for(int y=0;y<VerifyScopeComposition.H;y++)for(int x=0;x<VerifyScopeComposition.W;x++){
                if(nativeGlass.getRGB(x,y)!=after.getRGB(x,y))selfMismatch++;if(old.getRGB(x,y)!=after.getRGB(x,y))oldDifference++;}
            check(selfMismatch==0,"native opaque glass self-occlusion "+label);
            if(scope.type().shortName.equals("G33Enabled"))check(oldDifference>20,"G33 reproduces color-only glass self-overdraw "+label);
            boolean depthWrite=GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);RenderSystem.depthMask(false);
            try{ScopeRenderLayers.glass(()->{throw new IllegalStateException("glass cleanup fixture");});}
            catch(IllegalStateException expected){check(!GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),"glass restores caller depth write on exception");}
            RenderSystem.depthMask(depthWrite);
            System.out.println(label+" oldGlassSelfOverdrawPixels="+oldDifference+" nativeGlassMismatch="+selfMismatch);
            check(changed>100,"actual recessed glass shows baked image through complete housing "+label);System.out.println(label+" finalLensPixels="+changed);
        }finally{main.destroyBuffers();GL11.glDeleteTextures(image);GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,previous);RenderSystem.viewport(0,0,SIZE,SIZE);}
    }
    static void sample(String name,int step,float rail)throws Exception{
        var gun=VerifyMountedScopes.mounted(name,rail);
        if(name.equals("G33Enabled")){
            var side=gun.defaultAttachments.get(6).getLast();var holo=VerifyMountedScopes.node("EOTech552",0);holo.pos=rail+.1F;side.children.add(holo);
            gun=gun.withAttachments(gun.defaultAttachments);
        }
        var scope=GunSightState.scopeSight(gun);check(scope!=null,"mounted "+name);scope.node().zoomStep=step;
        if(name.equals("G33Enabled"))check(GunSightState.selectedGroup(gun).sights().stream().anyMatch(s->s.type().shortName.equals("EOTech552")),"G33 samples real forward EOTech reticle");
        var rig=ModelCache.rigFor(scope.type(),scope.type().modelScale);var toEye=VerifyMountedScopes.gunPose(gun).mul(physical(scope));
        float distance=FmumSightPicture.pupilDistance(rig.animation,scope.type().modelScale,new Matrix4f(toEye).invert().transformPosition(new Vector3f()));
        double fov=ScopeMagnification.nativeFov(70,scope.type(),step,distance);String label=name+"-z"+step+"-rail"+String.format(Locale.ROOT,"%.2f",rail);
        for(var sight:GunSightState.selectedGroup(gun).sights()){var at=sight.type();var skin=Textures.forReticle(at,sight.node().dotFrame);if(skin!=null)
            textures.computeIfAbsent(layer(skin),k->{try{return VerifyMountedScopes.reticleTexture(at,sight.node().dotFrame);}catch(Exception ex){throw new IllegalStateException(ex);}});}
        lensContract(scope);fill();var actual=new Batches(fov);ScopeReticleRenderer.bake(actual,gun,scope,rig,toEye,fov,1,1);actual.draw();var reticle=pixels();
        if(name.equals("G33Enabled"))check(actual.batches.stream().mapToInt(c->c.vertices.size()).sum()>0,"forward EOTech is baked through G33");
        ImageIO.write(reticle,"png",output.resolve(label+"-baked-reticle.png").toFile());fill();nativeReticle(gun,scope,toEye,fov,1);var expected=pixels();
        ImageIO.write(expected,"png",output.resolve(label+"-native-reticle.png").toFile());
        int different=0,totalError=0;for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){
            int a=reticle.getRGB(x,y),b=expected.getRGB(x,y);int delta=Math.abs((a>>16&255)-(b>>16&255))+Math.abs((a>>8&255)-(b>>8&255))+Math.abs((a&255)-(b&255));
            totalError+=delta;if(delta>60)different++;}
        System.out.printf(Locale.ROOT,"%s fov=%.5f vertices=%d nativeRasterMismatch=%d meanChannelError=%.4f%n",label,fov,actual.batches.stream().mapToInt(c->c.vertices.size()).sum(),different,totalError/(double)(SIZE*SIZE*3));
        check(different<=16,"baked reticle agrees with original 3D projection "+label+" mismatch="+different);
        fill();var all=new Batches(fov);ScopeFramebufferOverlays.bake(all,gun,scope.type(),rig,toEye,fov,1,1);all.draw();
        var complete=pixels();ImageIO.write(complete,"png",output.resolve(label+"-baked-complete.png").toFile());
        fill();nativeReticle(gun,scope,toEye,fov,1);nativeMask(scope,toEye,fov);var nativeComplete=pixels();
        ImageIO.write(nativeComplete,"png",output.resolve(label+"-native-complete.png").toFile());
        int maskMismatch=0;long maskError=0;for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){
            int a=complete.getRGB(x,y),b=nativeComplete.getRGB(x,y);
            int delta=Math.abs((a>>16&255)-(b>>16&255))+Math.abs((a>>8&255)-(b>>8&255))+Math.abs((a&255)-(b&255));maskError+=delta;if(delta>60)maskMismatch++;}
        System.out.printf(Locale.ROOT,"%s completeMaskMismatch=%d meanChannelError=%.5f%n",label,maskMismatch,maskError/(double)(SIZE*SIZE*3));
        check(maskMismatch<=32&&maskError/(double)(SIZE*SIZE*3)<.1,"complete native mask raster "+label);
        if(rail>.1F){fill();all.draw();mountedImage(gun,scope,toEye,label);}
        check(GL11.glGetError()==GL11.GL_NO_ERROR,"GPU error "+label);cases++;
    }
    public static void main(String[] args)throws Exception{
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();output=Path.of(args[0]);Files.createDirectories(output);
        for(int i=1;i<args.length;i++){var p=new OfflinePack(Path.of(args[i]));p.readTypes();VerifyMountedScopes.packs.add(p);}
        RenderSystem.initRenderThread();check(GLFW.glfwInit(),"GLFW");GLFW.glfwDefaultWindowHints();GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,2);GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window=GLFW.glfwCreateWindow(SIZE,SIZE,"scope bake verification",0,0);check(window!=0,"hidden window");GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();VerifyScopeComposition.initShader();imageViewState();
        var target=new TextureTarget(SIZE,SIZE,true,false);target.bindWrite(true);
        try{var image=new BufferedImage(128,128,BufferedImage.TYPE_INT_RGB);for(int y=0;y<128;y++)for(int x=0;x<128;x++)image.setRGB(x,y,(x<64?0xE0C090:0x90C0E0)^(y<64?0:0x182818));
            scene=VerifyScopeComposition.texture(image);var white=new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB);white.setRGB(0,0,0xFFFFFF);int whiteTexture=VerifyScopeComposition.texture(white);
            RenderSystem.activeTexture(GL13.GL_TEXTURE1);RenderSystem.bindTexture(whiteTexture);RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            try{for(String name:List.of("SeecterDR1x4x","ELCANSpecterDR1x4x","PSO1","NF7x","G33Enabled")){var type=AttachmentType.getAttachment(name);if(type==null)continue;
                for(int step=0;step<Math.max(1,type.fovZoomLevels.length);step++)for(float rail:new float[]{0,.15F})sample(name,step,rail);}}
            finally{GL11.glDeleteTextures(whiteTexture);}
        }finally{target.destroyBuffers();GL11.glDeleteTextures(scene);for(int id:new HashSet<>(textures.values()))GL11.glDeleteTextures(id);for(int id:VerifyMountedScopes.textures.values())GL11.glDeleteTextures(id);
            GL15.glDeleteBuffers(VerifyScopeComposition.vbo);GL30.glDeleteVertexArrays(VerifyScopeComposition.vao);GL20.glDeleteProgram(VerifyScopeComposition.program);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
        System.out.println("PASS: "+checks+" checks; "+cases+" actual mounted scope bake GPU cases; authored glass UVs and independent 1.7.10 3D reticle raster. Synthetic world, no game.");
    }
}
