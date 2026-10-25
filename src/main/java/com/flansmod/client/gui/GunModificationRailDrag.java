package com.flansmod.client.gui;

import com.flansmod.common.GunAttachments;
import com.flansmod.common.types.AttachmentType;
import com.flansmod.common.types.GunType;
import com.flansmod.common.types.ModifiableType.DefaultAttachment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Pure GUI rail gesture math. Committing a move remains the existing server-owned transaction. */
public final class GunModificationRailDrag {
    private GunModificationRailDrag() { }

    /** GUI pixels per one authored rail step, relative to the node's step at gesture start. */
    public record Axis(float dx, float dy, int initialStep, int maxStep) { }

    public static Axis axis(GunModificationView.Frame frame, GunType gun,
                            List<List<DefaultAttachment>> realTree, int[] nodePath) {
        if(gun == null || realTree == null || !GunAttachments.validPath(nodePath) || nodePath.length == 0)
            return null;
        var node = GunAttachments.nodeAt(realTree, nodePath);
        if(node == null || error(gun, realTree, nodePath, node.step) != null) return null;
        int[] parent = Arrays.copyOf(nodePath, nodePath.length - 2);
        var owner = GunAttachments.ownerAt(gun, realTree, parent);
        var mount = owner.slots[node.slot];
        if(mount.step <= 0 || !Float.isFinite(mount.stepVector) || mount.stepVector == 0) return null;
        var part = AttachmentType.getAttachment(node.name);
        float offset = node.offset < part.attachOffsets.length ? part.attachOffsets[node.offset] : 0;
        // Both slot vectors and attachment offsets were scaled by the pack reader already.
        float position = mount.stepVector * node.step + offset;
        var start = GunModificationView.projectMount(frame, gun, realTree, parent, node.slot, position);
        var next = GunModificationView.projectMount(frame, gun, realTree, parent, node.slot, position + mount.stepVector);
        if(start == null || next == null) return null;
        float dx = next.x - start.x, dy = next.y - start.y;
        double totalLength = Math.hypot(dx, dy) * mount.step;
        if(!Double.isFinite(totalLength) || totalLength < 4) return null;
        return new Axis(dx, dy, node.step, mount.step);
    }

    /** Uses cumulative mouse motion from press, allowing perpendicular motion without rail drift. */
    public static int stepFor(Axis axis, double mouseDeltaX, double mouseDeltaY) {
        if(axis == null) throw new IllegalArgumentException("A draggable rail axis is required");
        int maxStep = Math.max(0, axis.maxStep);
        int initial = Math.clamp(axis.initialStep, 0, maxStep);
        double lengthSquared = (double)axis.dx * axis.dx + (double)axis.dy * axis.dy;
        if(!Double.isFinite(mouseDeltaX) || !Double.isFinite(mouseDeltaY)
                || !Double.isFinite(lengthSquared) || lengthSquared <= 0) return initial;
        double movement = (mouseDeltaX * axis.dx + mouseDeltaY * axis.dy) / lengthSquared;
        double requested = initial + movement;
        if(Double.isNaN(requested)) return initial;
        if(requested <= 0) return 0;
        if(requested >= maxStep) return maxStep;
        return (int)Math.round(requested);
    }

    /** Same invalid/position/duplicate tokens as the server MOVE operation; offset stays unchanged. */
    public static String error(GunType gun, List<List<DefaultAttachment>> tree, int[] nodePath, int step) {
        if(gun == null || tree == null || !GunAttachments.validPath(nodePath) || nodePath.length == 0) return "invalid";
        var node = GunAttachments.nodeAt(tree, nodePath);
        if(node == null || node.name == null) return "invalid";
        var part = AttachmentType.getAttachment(node.name);
        int[] parent = Arrays.copyOf(nodePath, nodePath.length - 2);
        var owner = GunAttachments.ownerAt(gun, tree, parent);
        if(part == null || owner == null || owner.slots == null || node.slot < 0
                || node.slot >= owner.slots.length || owner.slots[node.slot] == null) return "invalid";
        var mount = owner.slots[node.slot];
        if(step < 0 || step > Math.max(0, mount.step) || node.offset < 0
                || node.offset >= Math.max(1, part.attachOffsets.length)) return "position";
        for(var other : GunAttachments.children(tree, parent))
            if(other != node && other.slot == node.slot && other.name != null
                    && other.name.equalsIgnoreCase(node.name) && other.step == step && other.offset == node.offset)
                return "duplicate";
        return null;
    }

    /** Invalid requests return the unchanged real tree; valid previews are independent deep copies. */
    public static List<List<DefaultAttachment>> preview(GunType gun, List<List<DefaultAttachment>> tree,
                                                       int[] nodePath, int step) {
        if(error(gun, tree, nodePath, step) != null) return tree;
        var copy = new ArrayList<List<DefaultAttachment>>(tree.size());
        for(var group : tree) {
            var copiedGroup = new ArrayList<DefaultAttachment>(group.size());
            for(var node : group) copiedGroup.add(copyNode(node));
            copy.add(copiedGroup);
        }
        var node = GunAttachments.nodeAt(copy, nodePath);
        node.step = step;
        GunAttachments.position(GunAttachments.ownerAt(gun, copy, Arrays.copyOf(nodePath, nodePath.length - 2)),
                node, AttachmentType.getAttachment(node.name));
        return copy;
    }

    private static DefaultAttachment copyNode(DefaultAttachment source) {
        var copy = new DefaultAttachment();
        copy.name = source.name; copy.slot = source.slot; copy.pos = source.pos;
        copy.step = source.step; copy.offset = source.offset; copy.paintjob = source.paintjob;
        copy.zoomStep = source.zoomStep; copy.dotFrame = source.dotFrame;
        copy.itemData = source.itemData == null ? null : source.itemData.copy();
        for(var child : source.children) copy.children.add(copyNode(child));
        return copy;
    }
}
