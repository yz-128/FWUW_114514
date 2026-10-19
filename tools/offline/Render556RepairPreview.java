import com.flansmod.common.types.*;
import com.flansmod.client.model.loader.*;
import com.flansmod.client.render.*;
import com.mojang.blaze3d.vertex.PoseStack;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Orthographic preview of actual emitted magazine contents plus original body; no GPU or game. */
public final class Render556RepairPreview {
    record Face(List<Verify556Series.Vertex> points,BufferedImage skin,float z) { }
    static final Matrix4f CAMERA=new Matrix4f().rotateX(.46F).rotateY(.68F);
    static BufferedImage skin(String name)throws Exception{
        var location=Textures.of(name,false,Audit556Series::has);
        for(var pack:Audit556Series.packs){var p=pack.path(location);if(p!=null)return ImageIO.read(p.toFile());}
        throw new IllegalStateException("Missing original skin "+name);
    }
    static void append(List<Face> faces,List<Verify556Series.Vertex> vertices,BufferedImage image){
        for(int i=0;i+3<vertices.size();i+=4){
            var points=new ArrayList<Verify556Series.Vertex>();float depth=0;
            for(int j=0;j<4;j++){var a=vertices.get(i+j);var p=CAMERA.transformPosition(new Vector3f(a.x,a.y,a.z));
                var b=new Verify556Series.Vertex(p.x,p.y,p.z);b.u=a.u;b.v=a.v;points.add(b);depth+=p.z;}
            faces.add(new Face(points,image,depth/4));
        }
    }
    static List<Face> faces(GunType mag,int count)throws Exception{
        var rig=ModelCache.rigFor(mag,mag.modelScale);var faces=new ArrayList<Face>();
        if(mag.magFollowerModel!=null){
            var bullet=BulletType.bullets.stream().filter(b->b.item!=null&&!b.isCase&&mag.ammoNames.stream().anyMatch(n->Audit556Series.matches(b,n))).findFirst().orElseThrow();
            var order=new ArrayList<BulletType>();for(int i=0;i<count;i++)order.add(bullet);
            var capture=Verify556Series.contents(mag,Verify556Series.load(mag,order),rig);
            for(int i=0;i<capture.batches.size();i++)append(faces,capture.batches.get(i),skin(i==0?mag.magFollowerTexture==null?mag.texture:mag.magFollowerTexture:bullet.bulletTexture));
        }
        var body=new Verify556Series.Capture();body.getBuffer(null);
        RigRenderer.render(new PoseStack(),body,rig,0xF000F0,0,1,1,1,1);append(faces,body.active,skin(mag.texture));
        faces.sort(Comparator.comparingDouble(Face::z));return faces;
    }
    static float[] bounds(List<Face> faces){
        float minX=Float.POSITIVE_INFINITY,minY=minX,maxX=-minX,maxY=-minX;
        for(var face:faces)for(var p:face.points){minX=Math.min(minX,p.x);minY=Math.min(minY,p.y);maxX=Math.max(maxX,p.x);maxY=Math.max(maxY,p.y);}
        return new float[]{minX,minY,maxX,maxY};
    }
    static void draw(Graphics2D g,List<Face> faces,int x,int y,int w,int h,float[] box){
        float scale=Math.min(w/(box[2]-box[0]),h/(box[3]-box[1])),cx=w/2F-(box[0]+box[2])*scale/2,cy=h/2F+(box[1]+box[3])*scale/2;
        var tile=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);float[] depth=new float[w*h];java.util.Arrays.fill(depth,Float.NEGATIVE_INFINITY);
        for(var face:faces)for(int[] tri:new int[][]{{0,1,2},{0,2,3}}){
            var a=face.points.get(tri[0]);var b=face.points.get(tri[1]);var c=face.points.get(tri[2]);
            double ax=cx+a.x*scale,ay=cy-a.y*scale,bx=cx+b.x*scale,by=cy-b.y*scale,dx=cx+c.x*scale,dy=cy-c.y*scale;
            double determinant=(by-dy)*(ax-dx)+(dx-bx)*(ay-dy);if(Math.abs(determinant)<1E-8)continue;
            int minX=Math.max(0,(int)Math.floor(Math.min(ax,Math.min(bx,dx)))),maxX=Math.min(w-1,(int)Math.ceil(Math.max(ax,Math.max(bx,dx))));
            int minY=Math.max(0,(int)Math.floor(Math.min(ay,Math.min(by,dy)))),maxY=Math.min(h-1,(int)Math.ceil(Math.max(ay,Math.max(by,dy))));
            var image=face.skin;
            for(int py=minY;py<=maxY;py++)for(int px=minX;px<=maxX;px++){
                double wa=((by-dy)*(px+.5-dx)+(dx-bx)*(py+.5-dy))/determinant;
                double wb=((dy-ay)*(px+.5-dx)+(ax-dx)*(py+.5-dy))/determinant,wc=1-wa-wb;
                if(wa<-1E-6||wb<-1E-6||wc<-1E-6)continue;
                float z=(float)(wa*a.z+wb*b.z+wc*c.z);int index=py*w+px;if(z<depth[index])continue;
                int u=Math.clamp((int)Math.floor((wa*a.u+wb*b.u+wc*c.u)*image.getWidth()),0,image.getWidth()-1);
                int v=Math.clamp((int)Math.floor((wa*a.v+wb*b.v+wc*c.v)*image.getHeight()),0,image.getHeight()-1);
                int color=image.getRGB(u,v);if((color>>>24)<26)continue;depth[index]=z;tile.setRGB(px,py,color|0xFF000000);
            }
        }
        g.drawImage(tile,x,y,null);
    }
    public static void main(String[] args)throws Exception{
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();Audit556Series.load(Path.of(args[0]));
        var names=List.of("556x45mm20RoundMagazine","556x45mm30RoundMagazine","556x45mm30RoundMagazineMagazinePull","XProductsX15556x45mm50RoundDrum");
        var output=new BufferedImage(1020,1120,BufferedImage.TYPE_INT_RGB);var g=output.createGraphics();g.setColor(new Color(11,20,28));g.fillRect(0,0,output.getWidth(),output.getHeight());
        g.setColor(Color.WHITE);g.setFont(new Font("SansSerif",Font.BOLD,19));g.drawString("5.56 magazines / actual contents renderer",20,28);g.setFont(new Font("SansSerif",Font.PLAIN,13));g.drawString("Original meshes and skins / CPU preview / empty - one round - twelve rounds",20,51);
        for(int row=0;row<names.size();row++){
            var mag=Verify556Series.gun(names.get(row));g.setColor(new Color(163,196,208));g.drawString(mag.shortName,20,83+row*260);
            var full=faces(mag,12);var box=bounds(full);
            for(int col=0;col<3;col++){int x=col*340+12,y=92+row*260,count=col==0?0:col==1?1:12;
                g.setColor(new Color(23,38,48));g.fillRect(x,y,320,225);draw(g,faces(mag,count),x+35,y+12,250,180,box);
                g.setColor(new Color(146,195,188));g.drawString(count+" round(s)",x+12,y+211);}
        }
        g.dispose();Path preview=Path.of(args[1]);Files.createDirectories(preview.toAbsolutePath().getParent());ImageIO.write(output,"PNG",preview.toFile());
        if(args.length>2){var mag=Verify556Series.type("EscapeFromTarcraftReforged","TESTmag",GunType.class);var mesh=faces(mag,0);
            var icon=new BufferedImage(64,64,BufferedImage.TYPE_INT_ARGB);var ig=icon.createGraphics();draw(ig,mesh,3,3,58,58,bounds(mesh));ig.dispose();
            Path path=Path.of(args[2]);Files.createDirectories(path.toAbsolutePath().getParent());ImageIO.write(icon,"PNG",path.toFile());}
        System.out.println("Rendered production contents preview: "+preview+"; optional TESTmag icon uses its original mesh/skin.");
    }
}
