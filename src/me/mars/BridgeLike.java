package me.mars;

import arc.math.geom.Geometry;
import arc.math.geom.Point2;
import arc.struct.IntSeq;
import mindustry.Vars;
import static mindustry.Vars.world;
import mindustry.gen.Building;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.distribution.DirectionBridge;
import mindustry.world.blocks.distribution.DirectionBridge.DirectionBridgeBuild;
import mindustry.world.blocks.distribution.ItemBridge;
import mindustry.world.blocks.distribution.ItemBridge.ItemBridgeBuild;


public class BridgeLike {
    public static int getRange(Block bridge) {
        if (bridge instanceof ItemBridge) {
            return ((ItemBridge) bridge).range;
        } else if (bridge instanceof DirectionBridge) {
            return ((DirectionBridge) bridge).range;
        }
        return -1;
    }

    public static Building getEnd(Building start, IntSeq bridges) {
        Building next = start;
        byte startDir = linkDir(start);


        Building nextLink;
        while ((nextLink = world.build(getLink(next))) != null && linkValid(next, nextLink) && next != nextLink && linkDir(next) == startDir) {
            bridges.add(next.pos());
            next = nextLink;
        }
        bridges.add(next.pos());
        return next;
    }

    public static byte linkDir(Building build) {
        int linkPos = getLink(build);
        return build.relativeTo(Point2.x(linkPos), Point2.y(linkPos));
    }

    // TODO: Do we need to verify if the link exists?
    public static int getLink(Building build) {
        if (build instanceof ItemBridgeBuild) {
            return ((ItemBridgeBuild) build).link;
        } else if (build instanceof DirectionBridgeBuild) {
            // TODO Yeah above todo is somewhat important. For now we return -1 if none found
            DirectionBridgeBuild link = ((DirectionBridgeBuild) build).findLink();
            return link != null ? link.pos() : -1;
        }
        return -1;
    }

    public static IntSeq allIncoming(Building build) {
        Block block = build.block;
        IntSeq ret = new IntSeq();
        int x = build.tileX(), y = build.tileY();
        int range = getRange(block);
        for (int d = 0; d < 4; d++) {
            for (int i = 1; i-1 < range; i++) {
                int cx = x + Geometry.d4x(d)*i, cy = y + Geometry.d4y(d)*i;
                Building inc = world.build(cx, cy);
                if (getLink(inc) == build.pos() && linkValid(world.build(cx, cy), build)) {
                    ret.add(Point2.pack(cx, cy));
                }
            }
        }
        return ret;
    }

    public static boolean linkValid(Building build, Building link) {
        if (link == null) {
            return false;
        }
        if (build instanceof ItemBridgeBuild) {
            return ((ItemBridge)(build.block)).linkValid(build.tile, link.tile);
        } else if (build instanceof DirectionBridgeBuild) {
            return ((DirectionBridgeBuild) build).findLink() == link;
        }
        return false;
    }

    public static boolean linkValid(Building build) {
        if (build instanceof ItemBridgeBuild) {
            return ((ItemBridge)(build.block)).linkValid(build.tile, Vars.world.tile(((ItemBridgeBuild) build).link));
        } else if (build instanceof DirectionBridgeBuild) {
            return ((DirectionBridgeBuild) build).findLink() != null;
        }
        return false;
    }

    public static boolean segHead(Building build) {
        if (build instanceof ItemBridgeBuild itemBridge) {
            if (itemBridge.incoming.isEmpty()) return true;
            for (int i = 0; i < itemBridge.incoming.size; i++) {
                int pos = itemBridge.incoming.get(i);
                int incomingDir = Tile.relativeTo(Point2.x(pos), Point2.y(pos), (float) itemBridge.tileX(), (float) itemBridge.tileY());
                if (incomingDir == linkDir(itemBridge)) return false;
            }
            return true;
        } else if (build instanceof DirectionBridgeBuild dirBridge) {
            int linkDir = linkDir(dirBridge);
            if (linkDir == -1) return true;

            return dirBridge.occupied[linkDir%4] == null;
        }
        return false;
    }
}

