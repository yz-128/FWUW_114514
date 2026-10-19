package com.flansmod.common;

import com.flansmod.common.types.AttachmentType;
import com.flansmod.common.types.GunType;
import com.flansmod.common.types.ModifiableType;
import com.flansmod.common.types.ModifiableType.DefaultAttachment;
import java.util.*;

/** A per-screen, cached catalogue of DIRECT mount compatibility, independent of ownership. */
public final class GunAttachmentCatalog {
    private record Mount(ModifiableType owner, int slot) { }
    /** A DIRECT interface on a parent part, not an inferred gun-level compatibility chain. */
    public record ParentMount(AttachmentType parent, int slot) { }
    /** A slot header or its installed part, in the same depth-first order used by the GUI. */
    public record TreeEntry(ModifiableType owner, int[] parentPath, int slot, int[] nodePath, DefaultAttachment node, int depth) { }
    private final List<AttachmentType> types;
    private final Map<Mount, List<AttachmentType>> compatible = new HashMap<>();
    private final Map<AttachmentType, List<ParentMount>> parents = new HashMap<>();
    private static GunAttachmentCatalog shared;
    private static int sharedSize = -1;
    public GunAttachmentCatalog(Collection<AttachmentType> registry) { types = List.copyOf(registry); }
    public List<AttachmentType> compatible(ModifiableType owner, int slot) {
        if(owner == null || slot < 0 || slot >= owner.slots.length || GunAttachments.slotCapacity(owner,slot) <= 0) return List.of();
        return compatible.computeIfAbsent(new Mount(owner, slot), mount -> types.stream()
                .filter(type -> GunAttachments.allowed(owner, slot, type)).toList());
    }

    /** Explicit matching interfaces sort before unrestricted mounts. Safe for server-side tooltips. */
    public List<ParentMount> parents(AttachmentType child) {
        if(child == null) return List.of();
        return parents.computeIfAbsent(child, part -> {
            var result = new ArrayList<ParentMount>();
            for(var parent : types) for(int slot = 0; slot < parent.slots.length; slot++)
                if(GunAttachments.slotCapacity(parent,slot) > 0 && GunAttachments.allowed(parent, slot, child))
                    result.add(new ParentMount(parent, slot));
            result.sort(Comparator.comparingInt((ParentMount mount) -> specificity(mount, child))
                    .thenComparing(mount -> mount.parent.shortName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                    .thenComparingInt(ParentMount::slot));
            return List.copyOf(result);
        });
    }
    private static int specificity(ParentMount mount, AttachmentType child) {
        if(mount.slot < mount.parent.attachmentWhitelist.size()
                && mount.parent.attachmentWhitelist.get(mount.slot).stream().anyMatch(name -> name.equalsIgnoreCase(child.shortName))) return 0;
        if(mount.slot < mount.parent.typeWhitelist.size()
                && mount.parent.typeWhitelist.get(mount.slot).stream().anyMatch(tag -> tag.equalsIgnoreCase(child.attachmentType))) return 1;
        return 2;
    }
    /** Cached registry snapshot, refreshed as definitions register; never constructs ItemStacks. */
    public static synchronized List<ParentMount> directParents(AttachmentType child) {
        if(shared == null || sharedSize != AttachmentType.attachments.size()) {
            shared = new GunAttachmentCatalog(AttachmentType.attachments); sharedSize = AttachmentType.attachments.size();
        }
        return shared.parents(child);
    }

    /** Returns a selected node, or the sole child of a selected mount; never guesses between siblings. */
    public static int[] selectedRemovalPath(List<List<DefaultAttachment>> tree, int[] parent, int slot, int[] explicitPath) {
        if(!GunAttachments.validPath(parent) || slot < 0) return new int[0];
        if(explicitPath != null && explicitPath.length > 0) {
            boolean matches = explicitPath.length == parent.length + 2 && GunAttachments.validPath(explicitPath)
                    && Arrays.equals(parent, Arrays.copyOf(explicitPath, parent.length)) && explicitPath[parent.length] == slot;
            return matches && GunAttachments.nodeAt(tree, explicitPath) != null ? Arrays.copyOf(explicitPath, explicitPath.length) : new int[0];
        }
        if(GunAttachments.children(tree, parent).stream().filter(node -> node.slot == slot).count() != 1) return new int[0];
        var result = Arrays.copyOf(parent, parent.length + 2); result[parent.length] = slot; result[parent.length + 1] = 0;
        return result;
    }

    /** Only slots on the receiver or actually mounted parents exist; no virtual prerequisite slots. */
    public static List<TreeEntry> treeEntries(GunType gun, List<List<DefaultAttachment>> tree) {
        if(gun == null) return List.of();
        var rows = new ArrayList<TreeEntry>(); treeEntries(gun, tree, new int[0], 0, rows); return List.copyOf(rows);
    }
    private static void treeEntries(ModifiableType owner, List<List<DefaultAttachment>> tree, int[] parent, int depth, List<TreeEntry> rows) {
        if(parent.length / 2 >= GunAttachments.MAX_DEPTH || rows.size() >= 4096) return;
        var children = GunAttachments.children(tree, parent);
        for(int slot = 0; slot < owner.slots.length && rows.size() < 4096; slot++) {
            rows.add(new TreeEntry(owner, parent, slot, null, null, depth)); int index = 0;
            for(var node : children) if(node.slot == slot) {
                int[] path = Arrays.copyOf(parent, parent.length + 2); path[parent.length] = slot; path[parent.length + 1] = index++;
                rows.add(new TreeEntry(owner, parent, slot, path, node, depth + 1));
                var part = AttachmentType.getAttachment(node.name);
                if(part != null) treeEntries(part, tree, path, depth + 2, rows);
            }
        }
    }

    /**
     * A full mount may show a replacement preview. This copies the tree and removes ONE current
     * part only from that preview; real installation still requires a separate server removal.
     */
    public static List<List<DefaultAttachment>> previewBase(GunType gun, List<List<DefaultAttachment>> tree,
                                                           int[] parent, int slot, int[] preferredPath) {
        var copy = GunAttachments.copy(gun, tree);
        var owner = GunAttachments.ownerAt(gun, copy, parent);
        if(owner == null || slot < 0 || slot >= owner.slots.length) return copy;
        var nodes = GunAttachments.children(copy, parent).stream().filter(node -> node.slot == slot).toList();
        int capacity = GunAttachments.slotCapacity(owner,slot);
        if(capacity <= 0 || nodes.size() < capacity || nodes.isEmpty()) return copy;
        var removal = selectedRemovalPath(copy, parent, slot, preferredPath);
        if(removal.length > 0) GunAttachments.remove(copy, removal);
        return copy;
    }
}
