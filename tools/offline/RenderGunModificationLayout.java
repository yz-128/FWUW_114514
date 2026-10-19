import com.flansmod.client.model.loader.BakedRig;
import com.flansmod.client.model.loader.ModelCache;
import com.flansmod.common.GunAttachments;
import com.flansmod.common.types.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/**
 * Headless layout evidence, NOT a Minecraft screenshot. Layout rectangles mirror the live
 * GunModificationScreen; artwork is CPU-rendered from the actual complete EFT assembly.
 * Inventory cards are labelled sample inventory, never observations of the player's inventory.
 * Uses the existing OfflinePack / FPPreview helpers; run under the same JDK as those helpers.
 */
public final class RenderGunModificationLayout {
    static final Color BG = color(0x0B111A), PANEL = color(0x111D2B), BORDER = color(0x273849);
    static final Color TEXT = color(0xE4EDF5), MUTED = color(0x849BAE), ACCENT = color(0x5DE0C2);
    static final Font FONT = new Font("Microsoft YaHei UI", Font.PLAIN, 9);
    static final Font SMALL = FONT.deriveFont(8F);
    record R(int x, int y, int w, int h) { }
    static BufferedImage gunImage;
    static List<BufferedImage> partImages = new ArrayList<>();
    static List<String> partNames = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length > 0 ? args[0] : "run/flan/EscapeFromTarcraftReforged_1.5.3");
        Path out = Paths.get(args.length > 1 ? args[1] : "reports/eft/gun-modification");
        Files.createDirectories(out);
        var pack = new OfflinePack(root); pack.readTypes();
        var gun = (GunType)pack.types.stream().filter(t -> t instanceof GunType && "BocchiTheGlock18C".equals(t.shortName)).findFirst().orElseThrow();
        List<FPPreview.Part> parts = new ArrayList<>();
        parts.add(new FPPreview.Part(new Matrix4f(), ModelCache.rigFor(gun, gun.modelScale), FPPreview.skin(root, gun.texture), -1));
        for(var group : gun.defaultAttachments) FPPreview.addNodes(parts, root, gun, group, new Matrix4f(), 0);
        gunImage = renderMesh(parts, 600, 400);
        for(var type : AttachmentType.attachments) {
            if(!GunAttachments.allowed(gun, 2, type)) continue;
            var rig = ModelCache.rigFor(type, type.modelScale);
            if(rig == null || rig.quads.isEmpty()) continue;
            partImages.add(renderMesh(List.of(new FPPreview.Part(new Matrix4f(), rig, FPPreview.skin(root, type.texture), -1)), 80, 44));
            partNames.add(type.displayName());
            if(partImages.size() == 8) break;
        }
        for(int[] size : new int[][] {{320,240},{640,360},{960,540}}) {
            var image = layout(size[0],size[1]);
            Path target = out.resolve("ui-layout-" + size[0] + ".png");
            ImageIO.write(image,"png",target.toFile());
            System.out.println("LAYOUT ONLY " + size[0] + "x" + size[1] + " -> " + target);
        }
    }

    static BufferedImage layout(int width,int height) {
        var image = new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);
        var g=image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(FONT); fill(g,new R(0,0,width,height),BG); fill(g,new R(0,0,width,1),ACCENT);
        text(g,"武器工坊",12,10,width-80,ACCENT); text(g,"孤独 · 全自动手枪 / GLOCK 18C",12,26,width-92,TEXT);
        button(g,new R(width-62,9,50,20),"关闭",false,true);
        boolean compact=width<560;
        int panelHeight=Math.max(40,height-138);
        R tree,inv,view;
        if(compact) {
            int side=Math.max(96,Math.min(134,width*35/100));
            tree=inv=new R(8,44,side,panelHeight); view=new R(side+16,44,Math.max(20,width-side-24),panelHeight);
        } else {
            int left=Math.max(132,Math.min(190,width*23/100)),right=Math.max(148,Math.min(224,width*27/100));
            tree=new R(10,44,left,panelHeight);inv=new R(width-right-10,44,right,panelHeight);
            view=new R(left+18,44,width-left-right-36,panelHeight);
        }
        check(tree,width,height,"tree");check(inv,width,height,"inventory");check(view,width,height,"preview");
        panel(g,tree);
        if(compact) {
            int half=(tree.w-12)/2;
            button(g,new R(tree.x+6,tree.y+5,half,19),"结构",true,true);
            button(g,new R(tree.x+6+half,tree.y+5,half,19),"背包",false,true);
        } else text(g,"配件结构",tree.x+10,tree.y+11,tree.w-20,TEXT);
        var treeArea=new R(tree.x+4,tree.y+(compact?30:28),tree.w-8,tree.h-(compact?35:33));
        Shape old=g.getClip();g.clipRect(treeArea.x,treeArea.y,treeArea.w,treeArea.h);
        String[] labels={"+  1 · 滑套","孤独 GLOCK 18C 滑套","+  2 · 机械瞄具","准星定位","+  3 · 下导轨"};
        for(int i=0;i<labels.length;i++) {
            int y=treeArea.y+i*22; boolean node=i==1||i==3,selected=i==4;
            if(selected) {fill(g,new R(treeArea.x+2,y,treeArea.w-6,20),color(0x20443F));fill(g,new R(treeArea.x+2,y+3,2,14),ACCENT);}
            int x=treeArea.x+9+(node?7:0);
            if(node) {g.drawImage(i==1?gunImage:partImages.isEmpty()?gunImage:partImages.getFirst(),x,y+2,16,16,null);x+=19;}
            text(g,labels[i],x,y+6,treeArea.x+treeArea.w-x-5,node?TEXT:selected?ACCENT:MUTED);
        }
        g.setClip(old);
        if(compact) {fill(g,new R(treeArea.x+treeArea.w-2,treeArea.y,2,treeArea.h),BORDER);fill(g,new R(treeArea.x+treeArea.w-2,treeArea.y,2,18),color(0x5B9B9B));}
        if(!compact) {
            panel(g,inv);text(g,"背包配件 · 样例",inv.x+10,inv.y+11,inv.w-20,TEXT);
            fill(g,new R(inv.x+7,inv.y+28,inv.w-14,20),BG);text(g,"搜索配件…",inv.x+9,inv.y+31,inv.w-18,MUTED);
            var area=new R(inv.x+7,inv.y+56,inv.w-14,inv.h-61);
            int cols=Math.max(1,(area.w+5)/58),cardW=(area.w-5*(cols-1))/cols;
            old=g.getClip();g.clipRect(area.x,area.y,area.w,area.h);
            for(int i=0;i<partImages.size();i++) {
                int x=area.x+i%cols*(cardW+5),y=area.y+i/cols*45;
                fill(g,new R(x,y,cardW,40),color(i==0?0x21473F:0x192A3B));border(g,new R(x,y,cardW,40),i==0?ACCENT:BORDER);
                g.drawImage(partImages.get(i),x+(cardW-40)/2,y+2,40,22,null);
                text(g,partNames.get(i),x+4,y+27,cardW-8,i==0?ACCENT:TEXT);
            }
            g.setClip(old);
        }
        panel(g,view);
        g.setPaint(new GradientPaint(view.x,view.y,color(0x15283A),view.x,view.y+view.h,color(0x101B27)));
        g.fillRect(view.x+1,view.y+1,view.w-2,view.h-2);
        g.setColor(color(0x1B3548));
        for(int x=view.x+12;x<view.x+view.w;x+=24)g.drawLine(x,view.y+27,x,view.y+view.h-15);
        for(int y=view.y+28;y<view.y+view.h-13;y+=24)g.drawLine(view.x+10,y,view.x+view.w-10,y);
        text(g,"枪械总览",view.x+9,view.y+10,view.w-55,ACCENT);button(g,new R(view.x+view.w-44,view.y+5,37,18),"重置",false,true);
        int meshH=Math.max(4,view.h-44),meshW=view.w-4;
        old=g.getClip();g.clipRect(view.x+2,view.y+27,meshW,meshH);
        // CPU mesh fits the same assembly-space bounding sphere as the in-game preview.
        double ratio=Math.min(meshW*.88/600D,meshH*.83/400D);
        int dw=(int)(600*ratio),dh=(int)(400*ratio);
        g.drawImage(gunImage,view.x+view.w/2-dw/2,view.y+27+meshH/2-dh/2,dw,dh,null);g.setClip(old);
        text(g,"拖动旋转 · 滚轮缩放",view.x+9,view.y+view.h-13,view.w-18,MUTED);
        text(g,"3 · 下导轨 / 配件装配",12,height-88,width-24,TEXT);
        int bw=compact?59:Math.min(100,width/7),y=height-74;
        button(g,new R(10,y,bw,21),"安装",false,!compact);button(g,new R(16+bw,y,bw,21),"拆卸",false,false);
        int controlsX=22+2*bw,cw=Math.max(50,(width-controlsX-16)/2);
        position(g,new R(controlsX,y,cw,21),"导轨",0,3);position(g,new R(controlsX+cw+4,y,cw,21),"偏移",0,2);
        int w=(width-28)/3;
        for(int i=0;i<3;i++) {
            int x=10+i*(w+4);fill(g,new R(x,height-47,w,29),color(0x142331));
            text(g,new String[]{"伤害","后坐力","散布"}[i],x+7,height-43,w/2,MUTED);
            text(g,new String[]{"1.00","1.00 → 0.85","2.00 → 1.80"}[i],x+7,height-31,w-14,i==0?TEXT:ACCENT);
            fill(g,new R(x+7,height-20,w-14,1),BORDER);fill(g,new R(x+7,height-20,(w-14)*3/4,1),ACCENT);
        }
        g.setFont(SMALL);text(g,"布局示意 · 实际 EFT 模型 / 列表样例",12,height-12,width-24,MUTED);
        g.dispose();return image;
    }

    static void position(Graphics2D g,R r,String name,int value,int max) {
        int side=Math.min(18,r.w/4);button(g,new R(r.x,r.y,side,r.h),"−",false,value>0);
        button(g,new R(r.x+r.w-side,r.y,side,r.h),"+",false,value<max);
        center(g,name,r.x+r.w/2,r.y+1,r.w-side*2-4,MUTED);center(g,value+"/"+max,r.x+r.w/2,r.y+12,r.w-side*2-4,TEXT);
    }
    static void button(Graphics2D g,R r,String label,boolean selected,boolean active) {
        fill(g,r,color(selected?0x234C44:0x1A2C3D));border(g,r,selected?ACCENT:BORDER);
        center(g,label,r.x+r.w/2,r.y+(r.h-8)/2,r.w-6,active?selected?ACCENT:TEXT:color(0x4C6375));
    }
    static void panel(Graphics2D g,R r) {fill(g,r,PANEL);border(g,r,BORDER);}
    static void fill(Graphics2D g,R r,Color c) {g.setColor(c);g.fillRect(r.x,r.y,r.w,r.h);}
    static void border(Graphics2D g,R r,Color c) {g.setColor(c);g.drawRect(r.x,r.y,r.w-1,r.h-1);}
    static void center(Graphics2D g,String text,int cx,int y,int width,Color color) {String s=clip(g,text,width);text(g,s,cx-g.getFontMetrics().stringWidth(s)/2,y,width,color);}
    static String clip(Graphics2D g,String text,int width) {
        if(g.getFontMetrics().stringWidth(text)<=width)return text;
        while(!text.isEmpty()&&g.getFontMetrics().stringWidth(text+"…")>width)text=text.substring(0,text.length()-1);
        return text+"…";
    }
    static void text(Graphics2D g,String value,int x,int y,int max,Color color) {g.setColor(color);g.drawString(clip(g,value,max),x,y+8);}
    static Color color(int hex) {return new Color(hex);}
    static void check(R r,int w,int h,String name) {if(r.x<0||r.y<0||r.w<=0||r.h<=0||r.x+r.w>w||r.y+r.h>h)throw new AssertionError(name+" out of bounds "+r);}

    /** Textured orthographic mesh render, excluding player arms and effects. */
    static BufferedImage renderMesh(List<FPPreview.Part> parts,int width,int height) {
        var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        float[] depth=new float[width*height];Arrays.fill(depth,-Float.MAX_VALUE);
        var rotation=new Matrix4f().rotateZ((float)Math.toRadians(12)).rotateY((float)Math.toRadians(-24));
        Vector3f min=new Vector3f(Float.POSITIVE_INFINITY),max=new Vector3f(Float.NEGATIVE_INFINITY);
        for(var p:parts)for(var q:p.rig().quads)for(var v:q.positions()) {
            var point=rotation.transformPosition(p.m().transformPosition(new Vector3f(v)));min.min(point);max.max(point);
        }
        Vector3f center=new Vector3f(min).add(max).mul(.5F);
        float scale=Math.min(width*.94F/Math.max(.01F,max.x-min.x),height*.94F/Math.max(.01F,max.y-min.y));
        for(var part:parts)for(var quad:part.rig().quads) {
            var transform=new Matrix4f(rotation).mul(part.m());Vector3f[] p=new Vector3f[4];
            for(int i=0;i<4;i++) {p[i]=transform.transformPosition(new Vector3f(quad.positions()[i])).sub(center);p[i].x=width/2F+p[i].x*scale;p[i].y=height/2F-p[i].y*scale;}
            var normal=transform.transformDirection(new Vector3f(quad.nx(),quad.ny(),quad.nz())).normalize();
            float shade=.58F+.42F*Math.abs(normal.dot(new Vector3f(.3F,.6F,.74F).normalize()));
            triangle(image,depth,p[0],p[1],p[2],quad.uvs()[0],quad.uvs()[1],quad.uvs()[2],part.tex(),shade);
            triangle(image,depth,p[0],p[2],p[3],quad.uvs()[0],quad.uvs()[2],quad.uvs()[3],part.tex(),shade);
        }
        return image;
    }
    static void triangle(BufferedImage target,float[] depth,Vector3f a,Vector3f b,Vector3f c,float[] ua,float[] ub,float[] uc,BufferedImage tex,float shade) {
        int w=target.getWidth(),h=target.getHeight();float area=edge(a,b,c.x,c.y);if(Math.abs(area)<.00001F)return;
        int left=Math.max(0,(int)Math.floor(Math.min(a.x,Math.min(b.x,c.x)))),right=Math.min(w-1,(int)Math.ceil(Math.max(a.x,Math.max(b.x,c.x))));
        int top=Math.max(0,(int)Math.floor(Math.min(a.y,Math.min(b.y,c.y)))),bottom=Math.min(h-1,(int)Math.ceil(Math.max(a.y,Math.max(b.y,c.y))));
        for(int y=top;y<=bottom;y++)for(int x=left;x<=right;x++) {
            float wa=edge(b,c,x+.5F,y+.5F)/area,wb=edge(c,a,x+.5F,y+.5F)/area,wc=1-wa-wb;
            if(wa<0||wb<0||wc<0)continue;float z=wa*a.z+wb*b.z+wc*c.z;if(z<=depth[y*w+x])continue;
            float u=wa*ua[0]+wb*ub[0]+wc*uc[0],v=wa*ua[1]+wb*ub[1]+wc*uc[1];
            int argb=tex==null?0xFFD0D0D0:tex.getRGB(Math.max(0,Math.min(tex.getWidth()-1,(int)(u*tex.getWidth()))),Math.max(0,Math.min(tex.getHeight()-1,(int)(v*tex.getHeight()))));
            if((argb>>>24)<100)continue;
            int r=(int)(((argb>>>16)&255)*shade),g=(int)(((argb>>>8)&255)*shade),bl=(int)((argb&255)*shade);
            target.setRGB(x,y,0xFF000000|(r<<16)|(g<<8)|bl);depth[y*w+x]=z;
        }
    }
    static float edge(Vector3f a,Vector3f b,float x,float y) {return (x-a.x)*(b.y-a.y)-(y-a.y)*(b.x-a.x);}
}
