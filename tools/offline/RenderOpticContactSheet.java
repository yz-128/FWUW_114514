import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import com.flansmod.common.GunSightState;
import com.flansmod.common.types.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.client.renderer.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/** CPU contact sheet of production-emitted reticles with the original pack PNGs. Not a game screenshot. */
public final class RenderOpticContactSheet {
    static class Point {float x,y,z,u,v;Point(float x,float y,float z){this.x=x;this.y=y;this.z=z;}}
    static class Capture implements MultiBufferSource,VertexConsumer {
        List<Point> points=new ArrayList<>();
        public VertexConsumer getBuffer(RenderType type){return this;}
        public VertexConsumer addVertex(float x,float y,float z){points.add(new Point(x,y,z));return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){return this;}
        public VertexConsumer setUv(float u,float v){var p=points.getLast();p.u=u;p.v=v;return this;}
        public VertexConsumer setUv1(int u,int v){return this;}public VertexConsumer setUv2(int u,int v){return this;}
        public VertexConsumer setNormal(float x,float y,float z){return this;}
    }
    static void textured(Graphics2D g,BufferedImage image,List<Point> p,float cx,float cy,float scale,float my,float mz) {
        for(int n=0;n+3<p.size();n+=4)for(int[] triangle:new int[][]{{0,1,2},{0,2,3}}) {
            var a=p.get(n+triangle[0]);var b=p.get(n+triangle[1]);var c=p.get(n+triangle[2]);
            double ax=cx+(a.z-mz)*scale,ay=cy-(a.y-my)*scale;
            double bx=cx+(b.z-mz)*scale,by=cy-(b.y-my)*scale;
            double dx=cx+(c.z-mz)*scale,dy=cy-(c.y-my)*scale;
            if(Math.abs((bx-ax)*(dy-ay)-(by-ay)*(dx-ax))<1E-5)continue;
            var source=new AffineTransform((b.u-a.u)*image.getWidth(),(b.v-a.v)*image.getHeight(),
                    (c.u-a.u)*image.getWidth(),(c.v-a.v)*image.getHeight(),a.u*image.getWidth(),a.v*image.getHeight());
            var destination=new AffineTransform(bx-ax,by-ay,dx-ax,dy-ay,ax,ay);
            try{destination.concatenate(source.createInverse());}catch(NoninvertibleTransformException failure){continue;}
            var path=new Path2D.Double();path.moveTo(ax,ay);path.lineTo(bx,by);path.lineTo(dx,dy);path.closePath();
            var previous=g.getClip();g.clip(path);g.drawImage(image,destination,null);g.setClip(previous);
        }
    }
    public static void main(String[] args)throws Exception {
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        List<OfflinePack> packs=new ArrayList<>();for(int i=1;i<args.length;i++){var pack=new OfflinePack(Path.of(args[i]));pack.readTypes();packs.add(pack);}
        var optics=AttachmentType.attachments.stream().filter(at->"sight".equalsIgnoreCase(at.itsType) && !at.isFoldedVariant()
                && (at.lightStuffModel!=null || at.scopeGlassModel!=null)).toList();
        int width=1280,cardW=320,cardH=270,header=72,height=header+((optics.size()+3)/4)*cardH;
        var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        g.setColor(new Color(10,17,24));g.fillRect(0,0,width,height);g.setFont(new Font("SansSerif",Font.BOLD,23));g.setColor(Color.WHITE);
        g.drawString("OPTIC RETICLES / original meshes and textures",22,31);g.setFont(new Font("SansSerif",Font.PLAIN,14));
        g.setColor(new Color(150,176,190));g.drawString("CPU vertex capture - frame 0 / first zoom step / no Minecraft or GPU",22,56);
        for(int k=0;k<optics.size();k++) {
            var at=optics.get(k);var rig=ModelCache.rigFor(at,at.modelScale);var m=rig.animation;
            int x=(k%4)*cardW,y=header+(k/4)*cardH;g.setColor(new Color(22,35,46));g.fillRoundRect(x+8,y+8,cardW-16,cardH-16,12,12);
            g.setColor(new Color(200,220,225));g.setFont(new Font("SansSerif",Font.PLAIN,13));g.drawString(at.shortName,x+19,y+30);
            boolean scope=at.scopeGlassModel!=null;float my,mz,extent;
            var center=FmumSightPicture.aimCenter(m,at.aimCenterY,at.aimCenterZ);var capture=new Capture();
            if(scope) {
                var glass=ModelCache.rigForReference(at.scopeGlassModel,1F);my=(glass.minY+glass.maxY)/2;mz=(glass.minZ+glass.maxZ)/2;
                extent=Math.max(glass.height(),glass.depth());
                var file=new TypeFile(EnumType.gun,"contact_"+k,false);file.lines.add("ShortName contact_"+k);var gun=new GunType(file);gun.read(file);
                gun.slots=new ModifiableType.AttachableSlot[]{new ModifiableType.AttachableSlot()};
                var node=new ModifiableType.DefaultAttachment();node.name=at.shortName;gun.defaultAttachments=List.of(List.of(node));
                var eye=new Vector3f(FmumSightPicture.vector(m,"pupilRefCenter",0,0,0).x*at.modelScale-.5F,center.y,center.z);
                var toEye=new Matrix4f().rotationY((float)Math.PI/2).translate(new Vector3f(eye).negate());
                var projection=new Matrix4f().perspective((float)Math.toRadians(70),16F/9F,.01F,100F);
                ScopeReticleRenderer.render(new PoseStack(),capture,gun,GunSightState.scopeSight(gun),rig,glass,toEye,projection,70/at.zoomDivisor(0),0,false);
            } else {
                var border=m.fmumVectorArrays.get("reticleBorder");
                float minY=Float.POSITIVE_INFINITY,maxY=-minY,minZ=minY,maxZ=-minY;
                for(var p:border){minY=Math.min(minY,p[1]);maxY=Math.max(maxY,p[1]);minZ=Math.min(minZ,p[2]);maxZ=Math.max(maxZ,p[2]);}
                my=(minY+maxY)/2;mz=(minZ+maxZ)/2;extent=Math.max(maxY-minY,maxZ-minZ);
                var eye=new Vector3f(-.5F,center.y,center.z);var node=new ModifiableType.DefaultAttachment();node.name=at.shortName;
                FmumSightRenderer.renderDot(new PoseStack(),capture,at,node,rig,eye,FmumSightPicture.pupilDistance(m,at.modelScale,eye),1,0,false);
            }
            float scale=165/Math.max(.001F,extent),cx=x+160,cy=y+142;
            g.setColor(new Color(154,181,190));g.fillOval(x+67,y+50,186,186);
            var cardClip=g.getClip();g.clip(new Ellipse2D.Float(x+67,y+50,186,186));
            g.setColor(new Color(121,150,130));g.fillRect(x+65,y+145,190,92);
            g.setColor(new Color(132,160,169));
            for(int line=-3;line<=3;line++){g.drawLine(x+67,y+143+line*24,x+253,y+143+line*24);g.drawLine(x+160+line*24,y+50,x+160+line*24,y+236);}
            g.setClip(cardClip);g.setColor(new Color(64,88,102));g.drawOval(x+67,y+50,186,186);
            var skin=Textures.forReticle(at,0,id->packs.stream().anyMatch(p->p.has(id)));
            Path skinPath=null;for(var pack:packs){var p=pack.path(skin);if(p!=null){skinPath=p;break;}}
            if(skinPath!=null)textured(g,ImageIO.read(skinPath.toFile()),capture.points,cx,cy,scale,my,mz);
            g.setFont(new Font("SansSerif",Font.PLAIN,12));g.setColor(new Color(117,166,172));
            g.drawString(at.lightStuffModel==null?"MAGNIFIER - external reticle required":scope?String.format(java.util.Locale.ROOT,"%.2fx / %d reticle frames",at.zoomDivisor(0),at.lightTextureCount):"COLLIMATED / "+at.lightTextureCount+" frames",x+19,y+251);
        }
        g.dispose();Path output=Path.of(args[0]);Files.createDirectories(output.toAbsolutePath().getParent());ImageIO.write(image,"PNG",output.toFile());
        System.out.println("Rendered "+optics.size()+" original optic definitions to "+output);
    }
}
