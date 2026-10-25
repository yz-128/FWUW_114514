import com.flansmod.client.gui.GunModificationLayout;
import com.flansmod.client.gui.GunModificationLayout.Anchor;
import com.flansmod.client.gui.GunModificationLayout.Rect;
import com.flansmod.client.gui.GunModificationView;
import com.flansmod.client.model.loader.ModelCache;
import com.flansmod.common.GunAttachments;
import com.flansmod.common.types.*;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.InputStreamReader;
import java.util.*;
import java.util.List;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Offline layout evidence, not a game/GuiGraphics screenshot. Uses production layout, actual pack
 * geometry, production framing/light and projected real root sockets. Inventory and stats are
 * labelled samples; AWT text rasterization and model thumbnails approximate the live GUI renderer.
 */
public final class RenderTarkovGunsmithLayout {
    private static final Color BG = color(0x151614), PANEL = color(0x1D1F1B), BORDER = color(0x45463D);
    private static final Color TEXT = color(0xDAD5C5), MUTED = color(0x939184), ACCENT = color(0xB7AD8D);
    private static final Color SELECT = color(0x3A3B30), DIM = color(0x282C24);
    private static int selectedSlot;
    private static final Font FONT = new Font("Microsoft YaHei UI", Font.PLAIN, 9);
    private static Path packRoot;
    private static GunType gun;
    private static List<FPPreview.Part> parts;
    private static final Map<AttachmentType, BufferedImage> thumbnails = new HashMap<>();
    private static List<AttachmentType> sampleInventory;
    private static JsonObject language;

    public static void main(String[] args) throws Exception {
        packRoot = Paths.get(args.length > 0 ? args[0] : "run/flan/EscapeFromTarcraftReforged_1.5.3");
        Path output = Paths.get(args.length > 1 ? args[1] : "build/tarkov-gunsmith-layout");
        String weapon = args.length > 2 ? args[2] : "AK74M";
        Files.createDirectories(output);
        var pack = new OfflinePack(packRoot);
        pack.readTypes();
        try(var input=GunModificationLayout.class.getResourceAsStream("/assets/flansmod/lang/zh_cn.json")) {
            if(input==null) throw new IllegalStateException("Production zh_cn.json is missing from the preview classpath");
            language=JsonParser.parseReader(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
        }
        gun = (GunType)pack.types.stream().filter(type -> type instanceof GunType && weapon.equals(type.shortName)).findFirst().orElseThrow();
        selectedSlot=Math.min(3,gun.slots.length-1);
        parts = new ArrayList<>();
        parts.add(new FPPreview.Part(new Matrix4f(), ModelCache.rigFor(gun, gun.modelScale), FPPreview.skin(packRoot, gun.texture), -1));
        for(var group : gun.defaultAttachments) FPPreview.addNodes(parts, packRoot, gun, group, new Matrix4f(), 0);
        sampleInventory = AttachmentType.attachments.stream().filter(type -> ModelCache.rigFor(type, type.modelScale) != null)
                .filter(type -> !ModelCache.rigFor(type, type.modelScale).quads.isEmpty())
                .filter(type -> selectedSlot>=0 && GunAttachments.allowed(gun,selectedSlot,type)).limit(12).toList();
        for(int[] size : new int[][] {{320,240},{480,270},{640,360},{960,540},{1280,720}}) {
            for(boolean inventoryTab : size[0] < 600 ? new boolean[] {false,true} : new boolean[] {false}) {
                Path target = output.resolve(weapon + "-" + size[0] + "x" + size[1] + (inventoryTab ? "-inventory" : "") + ".png");
                ImageIO.write(render(size[0], size[1], inventoryTab), "png", target.toFile());
                System.out.println("OFFLINE LAYOUT, NOT GAME SCREENSHOT: " + target);
            }
        }
    }

    private static BufferedImage render(int width, int height, boolean inventoryTab) {
        var layout = GunModificationLayout.of(width, height);
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setFont(FONT);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        fill(g, new Rect(0,0,width,height), BG);
        fill(g,new Rect(0,0,width,35),color(0x10110F));
        text(g,"军械库 /",12,8,width-80,MUTED);
        text(g,"武器改装",24+g.getFontMetrics().stringWidth("军械库 /"),8,width-110,ACCENT);
        text(g,gun.displayName(),12,26,width-92,TEXT);
        button(g,new Rect(width-62,9,50,20),"完成",false);
        fill(g,new Rect(10,35,width-20,1),BORDER);
        Rect side = layout.structure();
        if(!layout.compact() || !inventoryTab) {
            panel(g, side);
            if(layout.compact()) tabs(g, side, false); else text(g,"配件结构",side.x()+10,side.y()+11,side.w()-20,TEXT);
            Shape clip = g.getClip(); Rect area = layout.treeArea(); g.clipRect(area.x(),area.y(),area.w(),area.h());
            int y = area.y();
            for(int slot = 0; slot < gun.slots.length; slot++) {
                if(slot==selectedSlot) {
                    fill(g,new Rect(area.x()+2,y,area.w()-6,22),SELECT);
                    fill(g,new Rect(area.x()+2,y+3,2,16),ACCENT);
                }
                text(g,"+",area.x()+9,y+6,10,ACCENT);
                text(g,slotName(slot)+"  "+installedCount(slot)+"/"+GunAttachments.slotCapacity(gun,slot),area.x()+20,y+6,area.w()-26,MUTED);
                y+=24;
                for(var group : gun.defaultAttachments) for(var node : group) if(node.slot == slot) {
                    var type = AttachmentType.getAttachment(node.name);
                    if(type != null) {
                        g.drawImage(thumbnail(type),area.x()+16,y+2,16,16,null);
                        text(g,type.displayName(),area.x()+35,y+6,area.w()-41,TEXT);
                        y+=24;
                    }
                }
            }
            g.setClip(clip);
        }
        if(!layout.compact() || inventoryTab) {
            Rect inventory = layout.inventory();panel(g,inventory);
            if(layout.compact()) tabs(g, inventory, true); else text(g,"当前挂点："+gun.displayName()+" / "+slotName(selectedSlot),inventory.x()+10,inventory.y()+11,inventory.w()-20,TEXT);
            int toggleY=inventory.y()+(layout.compact()?31:27);
            g.setColor(MUTED);g.drawRect(inventory.x()+7,toggleY+1,10,10);
            text(g,"显示此枪可用配件",inventory.x()+23,toggleY+3,inventory.w()-31,TEXT);
            Rect search=layout.search();
            fill(g,new Rect(search.x()-2,search.y()-3,search.w()+4,search.h()+5),color(0x10120F));
            fill(g,new Rect(search.x()-2,search.bottom()+2,search.w()+4,1),BORDER);
            text(g,"搜索配件…",search.x(),search.y(),search.w(),MUTED);
            Rect area=layout.candidateArea();Shape old=g.getClip();g.clipRect(area.x(),area.y(),area.w(),area.h());
            int cols=Math.max(1,(area.w()+5)/52),cardW=(area.w()-(cols-1)*5)/cols;
            for(int i=0;i<sampleInventory.size();i++) {
                var type=sampleInventory.get(i);Rect card=new Rect(area.x()+i%cols*(cardW+5),area.y()+i/cols*52,cardW,48);
                fill(g,card,color(0x242820));border(g,card,BORDER);
                fill(g,new Rect(card.x()+1,card.y()+34,card.w()-2,1),color(0x3B4033));
                g.drawImage(thumbnail(type),card.x()+(card.w()-22)/2,card.y()+5,22,22,null);
                text(g,type.displayName(),card.x()+4,card.y()+36,card.w()-8,TEXT);
                text(g,"1",card.right()-3-g.getFontMetrics().stringWidth("1"),card.y()+3,10,color(0xA5B18A));
            }
            g.setClip(old);
        }
        Rect viewport=layout.viewport();
        panel(g,viewport);
        g.setPaint(new GradientPaint(viewport.x(),viewport.y()+1,color(0x292B25),viewport.x(),viewport.bottom()-1,color(0x131511)));
        g.fillRect(viewport.x()+1,viewport.y()+1,viewport.w()-2,viewport.h()-2);
        for(int y=viewport.y()+26;y<viewport.bottom()-16;y+=4)fill(g,new Rect(viewport.x()+1,y,viewport.w()-2,1),new Color(0x03202218,true));
        fill(g,new Rect(viewport.x()+12,viewport.y()+viewport.h()/2,viewport.w()-24,1),new Color(0x154F5144,true));
        int resetWidth=viewport.w()>280?48:35,mountsWidth=viewport.w()>280?50:35;
        text(g,"当前装配",viewport.x()+9,viewport.y()+9,viewport.w()-resetWidth-mountsWidth-22,ACCENT);
        button(g,new Rect(viewport.right()-resetWidth-5,viewport.y()+3,resetWidth,18),"复位",false);
        button(g,new Rect(viewport.right()-resetWidth-mountsWidth-8,viewport.y()+3,mountsWidth,18),"挂点",true);
        var corners=new ArrayList<Vector3f>();
        for(var part:parts) VerifyGunModificationView.collect(corners,part.rig(),part.m());
        Rect model=layout.previewModel();
        var frame=GunModificationView.frame(corners,-24,0,1F,model.x(),model.y(),model.w(),model.h());
        g.dispose();
        drawModel(image,frame);
        g=image.createGraphics();g.setFont(FONT);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        var anchors=new ArrayList<Anchor>();
        for(int pass=0;pass<2;pass++) for(int i=0;i<gun.slots.length;i++) {
            if((i==selectedSlot)!=(pass==0))continue;
            var slot=gun.slots[i];var installed=installed(i);float rail=installed.size()==1?installed.getFirst().pos:0;
            var projected=frame.matrix().transformPosition(new Vector3f(slot.x+rail,slot.y,slot.z));
            anchors.add(new Anchor(i,projected.x,projected.y));
        }
        for(var pin:GunModificationLayout.pins(viewport,anchors)) {
            boolean selected=pin.id()==selectedSlot;
            var installed=installed(pin.id());
            var part=installed.isEmpty()?null:AttachmentType.getAttachment(installed.getFirst().name);
            g.setColor(selected?ACCENT:color(0x6F7560));
            int elbowY=(Math.round(pin.anchorY())+Math.round(pin.connectorY()))/2;
            g.drawLine(Math.round(pin.anchorX()),Math.round(pin.anchorY()),Math.round(pin.anchorX()),elbowY);
            g.drawLine(Math.round(pin.anchorX()),elbowY,Math.round(pin.connectorX()),elbowY);
            g.drawLine(Math.round(pin.connectorX()),elbowY,Math.round(pin.connectorX()),Math.round(pin.connectorY()));
            g.fillRect(Math.round(pin.anchorX())-2,Math.round(pin.anchorY())-2,5,5);
            fill(g,pin.card(),new Color(selected?0xF14A4D3C:0xF120241D,true));
            border(g,pin.card(),selected?ACCENT:color(0x6F7560));
            if(part!=null)g.drawImage(thumbnail(part),pin.card().x()+4,pin.card().y()+6,16,16,null);
            else text(g,"+",pin.card().x()+8,pin.card().y()+10,12,MUTED);
            text(g,slotName(pin.id()),pin.card().x()+24,pin.card().y()+4,pin.card().w()-27,selected?ACCENT:TEXT);
            String content=installed.size()>1?installed.size()+" 件配件":part==null?"空挂点":part.displayName();
            text(g,content,pin.card().x()+24,pin.card().y()+15,pin.card().w()-27,MUTED);
        }
        text(g,"拖动旋转 · 滚轮缩放",viewport.x()+9,viewport.bottom()-13,viewport.w()-18,MUTED);
        var selectedPart=installed(selectedSlot).isEmpty()?null:AttachmentType.getAttachment(installed(selectedSlot).getFirst().name);
        text(g,"当前挂点 · "+slotName(selectedSlot)+(selectedPart==null?"":" / "+selectedPart.displayName()),layout.context().x()+2,layout.context().y()+1,layout.context().w()-4,TEXT);
        Rect action=layout.actions();int buttonWidth=layout.compact()?59:Math.min(100,width/7);
        button(g,new Rect(10,action.y(),buttonWidth,21),"安装",false,false);
        button(g,new Rect(16+buttonWidth,action.y(),buttonWidth,21),"拆卸",false,true);
        int controlX=22+2*buttonWidth,controlW=Math.max(50,(width-controlX-16)/2);
        for(int i=0;i<2;i++) {
            int x=controlX+i*(controlW+4),sideWidth=Math.min(18,controlW/4);
            button(g,new Rect(x,action.y(),sideWidth,21),"−",false,false);
            button(g,new Rect(x+controlW-sideWidth,action.y(),sideWidth,21),"+",false,false);
            centered(g,i==0?"导轨":"偏移",x+controlW/2,action.y()+1,controlW-2*sideWidth-4,MUTED);
            centered(g,"0/0",x+controlW/2,action.y()+12,controlW-2*sideWidth-4,TEXT);
        }
        Rect stats=layout.stats();int statW=(width-28)/3;
        for(int i=0;i<3;i++) {
            Rect cell=new Rect(10+i*(statW+4),stats.y(),statW,27);fill(g,cell,color(0x1D211A));
            text(g,new String[]{"伤害倍率","垂直后坐力","腰射散布"}[i],cell.x()+7,cell.y()+4,cell.w()/2,MUTED);
            text(g,new String[]{"1.00","0.69","2.00"}[i],cell.x()+7,cell.y()+16,cell.w()-14,TEXT);
            fill(g,new Rect(cell.x()+7,cell.y()+25,cell.w()-14,1),BORDER);
            fill(g,new Rect(cell.x()+7,cell.y()+25,(cell.w()-14)*5/6,1),color(0x73745C));
        }
        text(g,"离线布局 · 实际枪模 / 库存及数值样例",layout.status().x(),layout.status().y(),layout.status().w(),MUTED);
        g.dispose();return image;
    }

    private static void drawModel(BufferedImage image,GunModificationView.Frame frame) {
        float[] depth=new float[image.getWidth()*image.getHeight()];Arrays.fill(depth,-Float.MAX_VALUE);
        // The fire-mode selector is one array of every position; draw only the current one, the
        // way ModelGun.renderSwitch does and the way the production preview now does.
        int fireMode=gun==null?0:gun.fireModeIndex(null);
        for(var part:parts) {
            var hidden=part.rig().switchModelHidden(fireMode);
            for(var quad:part.rig().quads) {
                if(!hidden.isEmpty() && hidden.contains(quad)) continue;
                var matrix=new Matrix4f(frame.matrix()).mul(part.m());var normals=new Matrix3f();matrix.normal(normals);
                float light=GunModificationView.shade(normals.transform(new Vector3f(quad.nx(),quad.ny(),quad.nz())));
                Vector3f[] p=new Vector3f[4];for(int i=0;i<4;i++)p[i]=matrix.transformPosition(new Vector3f(quad.positions()[i]));
                RenderGunModificationLayout.triangle(image,depth,p[0],p[1],p[2],quad.uvs()[0],quad.uvs()[1],quad.uvs()[2],part.tex(),light);
                RenderGunModificationLayout.triangle(image,depth,p[0],p[2],p[3],quad.uvs()[0],quad.uvs()[2],quad.uvs()[3],part.tex(),light);
            }
        }
    }
    private static BufferedImage thumbnail(AttachmentType type) {
        return thumbnails.computeIfAbsent(type,part -> {
            try {
                return RenderGunModificationLayout.renderMesh(List.of(new FPPreview.Part(new Matrix4f(),
                        ModelCache.rigFor(part,part.modelScale),FPPreview.skin(packRoot,part.texture),-1)),96,54);
            } catch(Exception error) { throw new IllegalStateException("Cannot preview " + part.shortName,error); }
        });
    }
    private static List<ModifiableType.DefaultAttachment> installed(int slot) {
        return gun.defaultAttachments.stream().flatMap(List::stream).filter(node->node.slot==slot).toList();
    }
    private static long installedCount(int slot) {return installed(slot).size();}
    private static String prettyKind(String category) {
        String text = category.replace('_', ' ');
        text = text.replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ").replaceAll("(?<=[A-Z])(?=[A-Z][a-z])", " ");
        StringBuilder out = new StringBuilder(text.length());
        boolean start = true;
        for(int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.append(start ? Character.toUpperCase(c) : c);
            start = c == ' ';
        }
        return out.toString();
    }

    private static String slotName(int slot) {
        if(slot>=0 && slot<gun.typeWhitelist.size() && !gun.typeWhitelist.get(slot).isEmpty()) {
            var labels=new ArrayList<String>();
            for(String category:gun.typeWhitelist.get(slot)) {
                String key="gunsmith.flansmod.kind."+category.toLowerCase(Locale.ROOT);
                // Same fallback as the production slot label: 1.7.10 printed the raw kind, and
                // splitting it into words keeps the preview readable.
                labels.add(language.has(key)?language.get(key).getAsString():prettyKind(category));
            }
            return (slot+1)+" · "+String.join(" / ",labels);
        }
        return "挂点 "+(slot+1);
    }
    private static void tabs(Graphics2D g,Rect area,boolean inventory) {
        int w=(area.w()-12)/2;
        button(g,new Rect(area.x()+6,area.y()+5,w,19),"结构",!inventory);
        button(g,new Rect(area.x()+6+w,area.y()+5,w,19),"背包",inventory);
    }
    private static void panel(Graphics2D g,Rect r) {
        fill(g,r,PANEL);border(g,r,BORDER);
        fill(g,new Rect(r.x()+1,r.y()+23,r.w()-2,1),color(0x33362D));
    }
    private static void button(Graphics2D g,Rect r,String text,boolean selected) {
        button(g,r,text,selected,true);
    }
    private static void button(Graphics2D g,Rect r,String text,boolean selected,boolean enabled) {
        fill(g,r,selected&&enabled?ACCENT:DIM);border(g,r,selected?ACCENT:BORDER);
        centered(g,text,r.x()+r.w()/2,r.y()+(r.h()-8)/2,r.w()-6,enabled?selected?color(0x181B15):TEXT:color(0x666B5A));
    }
    private static void centered(Graphics2D g,String text,int cx,int y,int maxWidth,Color color) {
        String clipped=clip(g,text,maxWidth);text(g,clipped,cx-g.getFontMetrics().stringWidth(clipped)/2,y,maxWidth,color);
    }
    private static void fill(Graphics2D g,Rect r,Color color) {g.setColor(color);g.fillRect(r.x(),r.y(),r.w(),r.h());}
    private static void border(Graphics2D g,Rect r,Color color) {g.setColor(color);g.drawRect(r.x(),r.y(),r.w()-1,r.h()-1);}
    private static void text(Graphics2D g,String value,int x,int y,int width,Color color) {
        g.setColor(color);g.drawString(clip(g,value,width),x,y+8);
    }
    private static String clip(Graphics2D g,String value,int width) {
        if(width<1)return "";
        if(g.getFontMetrics().stringWidth(value)<=width)return value;
        while(!value.isEmpty() && g.getFontMetrics().stringWidth(value+"…")>width)value=value.substring(0,value.length()-1);
        return value+"…";
    }
    private static Color color(int rgb) {return new Color(rgb);}
}
