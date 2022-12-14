package me.mars;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.math.geom.QuadTree;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.layout.Table;
import arc.struct.IntIntMap;
import arc.struct.Seq;
import arc.util.*;
import mindustry.Vars;
import mindustry.game.EventType.*;
import mindustry.gen.Groups;
import mindustry.gen.Icon;
import mindustry.mod.Mod;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.distribution.ItemBridge;
import mindustry.world.blocks.distribution.ItemBridge.ItemBridgeBuild;

import static mindustry.Vars.*;
//import arc.util.noise.Noise;


public class ModMain2 extends Mod {
	public static boolean debugMode;
	public static int lineOpacity;
	public static boolean fixedColor;
	public static int ticksPerUpdate = 10;

	static Rect bounds = new Rect(0, 0, 0, 0);
	public static QuadTree<Segment> vertSeg = new QuadTree<>(bounds);
	public static QuadTree<Segment> horiSeg = new QuadTree<>(bounds);
	public static Seq<Segment> allSegments = new Seq<>(false);

	public static Seq<ItemBridge> bridgeBlocks = new Seq<>(); // All bridges on the current world
	public static ItemBridge currentSelection = null;
	private static Seq<Segment> hoverSelected = new Seq<>(false, 8, Segment.class);

	private static float time;
	private static Seq<Runnable> queue = new Seq<>();
	public static IntIntMap lastConfigs = new IntIntMap();
	@Override
	public void init() {
		if (Vars.headless) return;

		if (!Vars.mobile)ConfigHandler.init();
		for (Block block : Vars.content.blocks()) {
			if (/*Vars.indexer.isBlockPresent(block) && */block instanceof ItemBridge bridge) {
				bridgeBlocks.add(bridge);
			}
		}

		Vars.ui.settings.addCategory(Core.bundle.get("setting.bridging.name"), settingsTable -> {
			// TODO: Bundles
			settingsTable.sliderPref("bridging.line-opacity", 70, 10, 100, i -> i+"%");
			settingsTable.checkPref("bridging.fixed-highlight-color", false);
			settingsTable.checkPref("bridging.debug-mode", false);
		});
		Events.on(ClientLoadEvent.class, event -> {
			lineOpacity = Core.settings.getInt("bridging.line-opacity");
			fixedColor = Core.settings.getBool("bridging.fixed-highlight-color");
			debugMode = Core.settings.getBool("bridging.debug-mode");

			Table table = new DragTable();
			table.setSize(50f);
			table.setPosition(0, Core.graphics.getHeight()/2);
			TextureRegionDrawable blockRegion = new TextureRegionDrawable(Icon.none);
			table.button(blockRegion, () -> {
				int index = bridgeBlocks.indexOf(currentSelection);
				if (index == bridgeBlocks.size-1) {
					currentSelection = null;
					blockRegion.set(Icon.none.getRegion());
					return;
				}
				currentSelection = bridgeBlocks.get(index+1);
				blockRegion.set(currentSelection.uiIcon);
			});
			Vars.ui.hudGroup.addChild(table);
		});

		eventInit();

		Events.run(Trigger.uiDrawBegin, () -> {
			if (Vars.state.isGame() && currentSelection != null) {
				Rect camBounds = Tmp.r1;
				camBounds.setSize(Core.camera.width/tilesize, Core.camera.height/tilesize);
				camBounds.setCenter(Core.camera.position.x/tilesize, Core.camera.position.y/tilesize);
				Rect hitbox = Tmp.r2;

				Vec2 mouseCoords = Core.input.mouseWorld();
				int mouseX = Math.round(mouseCoords.x/tilesize), mouseY = Math.round(mouseCoords.y/tilesize);
				hoverSelected.clear();
				for (Segment segment : allSegments) {
					if (segment.block != currentSelection) continue;
					segment.hitbox(hitbox);
					if (camBounds.overlaps(hitbox)) {
						segment.draw();
						if (segment.passing.contains(Point2.pack(mouseX, mouseY))) hoverSelected.add(segment);
					}
				}
				if (hoverSelected.size == 1) {
					hoverSelected.get(0).drawHighlight();
				} else if (hoverSelected.size > 1) {
					int index = (int)(Time.time/7.5f % (hoverSelected.size*Mathf.PI*4) /(Mathf.PI*4));
					// For Select, index starts at 1
					Select.instance().select(hoverSelected.items, Structs.comparingFloat(segment -> segment.start.pos()),
							index+1, hoverSelected.size).drawHighlight();
				}
			}
		});
	}

	public static void eventInit() {
		Events.on(WorldLoadEvent.class, worldLoadEvent -> {
			bounds.set(0, 0, world.width(), world.height());
			Log.info("Bounds: @", bounds);
			reloadSegments();
		});

		// These work on the assumption that bridges are 1x1
		Events.on(TilePreChangeEvent.class, tilePreChangeEvent -> {
			if (tilePreChangeEvent.tile.build instanceof ItemBridgeBuild bridge) bridgeRemoved(bridge);
		});

		Events.on(TileChangeEvent.class, tileChangeEvent -> {
			if (tileChangeEvent.tile.build instanceof ItemBridgeBuild bridge) bridgeBuilt(bridge);
		});

		Events.on(ConfigEvent.class, configEvent -> {
			if (!(configEvent.tile instanceof ItemBridge.ItemBridgeBuild bridge)) return;

			// Form for disconnected
			int lastConfig = lastConfigs.get(bridge.pos(), -1); //
//			if (lastConfig != -1) formSegment(lastConfig);
			if (Vars.world.build(lastConfig) instanceof ItemBridgeBuild oldLink) {
//				if (oldLink.incoming.contains(bridge.pos())) Log.info("Removing");
				oldLink.incoming.removeValue(bridge.pos());
				formSegment(oldLink);
			}
			// Form for new connection
			if (Vars.world.build((int) configEvent.value) instanceof ItemBridgeBuild link) {
				bridge.updateTile();
				link.incoming.add(bridge.pos());
				if (!segHead(link)) {
					Segment linkSeg = findSeg(link.tileX(), link.tileY(), 0);
					if (linkSeg != null) {
						allSegments.remove(linkSeg);
						getTree(linkSeg.linkDir()).remove(linkSeg);
					}
				}
				// Remove to prevent duplicates
				link.incoming.removeValue(bridge.pos());
				formSegment((ItemBridgeBuild) configEvent.tile);
			}
			formSegment(bridge);
			// Update those passing
			both(tree -> tree.intersect(bridge.tileX(), bridge.tileY(), 1, 1, segment -> {
				ItemBridgeBuild oldEnd = segment.end;
				updateEnd(segment);
				if (!segment.valid()) {
					segment.end = oldEnd; // Needed as QuadTree#remove needs the Segment hitbox
					tree.remove(segment);
					allSegments.remove(segment);
				}
			}));
			lastConfigs.put(bridge.pos(), (int) configEvent.value);
		});

		// Wait 1 tick for incoming to be updated
		Events.run(Trigger.update, () -> {
			time+=Time.delta;
			if (time >= ticksPerUpdate) {
				// Settings
				lineOpacity = Core.settings.getInt("bridging.line-opacity");
				fixedColor = Core.settings.getBool("bridging.fixed-highlight-color");
				debugMode = Core.settings.getBool("bridging.debug-mode");

				if (debugMode) Time.mark();
				update();
				if (debugMode) Vars.ui.showInfoToast("Took " + Time.elapsed() + " ms to update", 1);
				time-=ticksPerUpdate;
			}
			queue.each(Runnable::run);
			queue.clear();
		});
	}

	public static void bridgeBuilt(ItemBridgeBuild bridge) {
		queue.add(() -> {
			lastConfigs.put(bridge.pos(), bridge.link);
			// Form for link
			Segment segment = findSegStrict(Point2.x(bridge.link), Point2.y(bridge.link), Segment.linkDir(bridge));
			if (segment == null) {
				formSegment(bridge);
			} else {
				segment.start = bridge;
				updateEnd(segment);
			}
			// Form for incoming
			for (int i = 0; i < bridge.incoming.size; i++) {
				int pos = bridge.incoming.items[i];
				both(tree -> tree.intersect(Point2.x(pos), Point2.y(pos), 1, 1, ModMain2::updateEnd));
				formSegment(pos);
			}
		});
	}

	public static void bridgeRemoved(ItemBridgeBuild bridge) {
		lastConfigs.remove(bridge.pos());
		// Remove itself
		Segment self = findSeg(bridge.tileX(), bridge.tileY(), Segment.linkDir(bridge));
		if (self != null) {
			getTree(self.linkDir()).remove(self);
			allSegments.remove(self);
		}
		// Make new Segment for link if possible
		if (Vars.world.build(bridge.link) instanceof ItemBridgeBuild link) {
			link.incoming.removeValue(bridge.pos());
			formSegment(link);
		}
		// Update those linked to the bridge.
		both(tree -> tree.intersect(bridge.tileX(), bridge.tileY(), 1, 1, segment -> {
			ItemBridgeBuild oldEnd = segment.end;
			// Jank
			int removeIndex = segment.passing.indexOf(bridge.pos());
			if (removeIndex > 0) {
				// Segment hitbox changed, update tree
				tree.remove(segment);
				segment.end = (ItemBridgeBuild) Vars.world.build(segment.passing.items[removeIndex-1]);
				segment.passing.setSize(removeIndex);
				tree.insert(segment);
			}
			if (!segment.valid()) {
				// REMOVEME
				segment.end = oldEnd; // Needed as QuadTree#remove needs the Segment hitbox
				tree.remove(segment);
				allSegments.remove(segment);
			}}));
	}

	public static void update() {
		if (!Vars.state.isPlaying()) return;

		allSegments.each(segment -> {
			segment.selfIndex = 0;
			segment.currentSize = 4;
			segment.occupied.clear();
		});
		allSegments.each(Segment::update);
		allSegments.each(Segment::postUpdate);
	}

	static void updateEnd(Segment segment) {
		QuadTree<Segment> tree = getTree(segment.linkDir());
		tree.remove(segment);
		segment.updateEnd();
		tree.insert(segment);
	}

	public static void reloadSegments() {
		Time.mark();
		both(QuadTree::clear);
		allSegments.clear();
		Groups.build.each(building -> {
			if (!(building instanceof ItemBridgeBuild b)) return;
			formSegment(b);

		});
		Log.info("Segments reloaded in @ ms, @/@ segments total",
				Time.elapsed(), allSegments.count(segment -> segment.start.team == Vars.player.team()), allSegments.size);
	}

	public static boolean linkValid(ItemBridgeBuild build) {
		return ((ItemBridge)build.block).linkValid(build.tile, Vars.world.tile(build.link));
	}

	public static boolean segHead(ItemBridgeBuild build) {
		if (build.incoming.isEmpty()) return true;
		for (int i = 0; i < build.incoming.size; i++) {
			int pos = build.incoming.items[i];
			int incomingDir = Tile.relativeTo(Point2.x(pos), Point2.y(pos), (float) build.tileX(), (float) build.tileY());
			if (incomingDir == Segment.linkDir(build)) return false;
		}
		return true;
	}

	public static Segment findSeg(int sx, int sy, int firstAxis) {
		Seq<Segment> out = new Seq<>();
		getTree((byte) (1-firstAxis)).intersect(sx, sy, 1, 1, out);
		Segment res = out.find(segment -> segment.start.tileX() == sx && segment.start.tileY() == sy);
		if (res != null) return res;
		out.clear();
		getTree(firstAxis).intersect(sx, sy, 1, 1, out);
		return out.find(segment -> segment.start.tileX() == sx && segment.start.tileY() == sy);
	}

	public static Segment findSegStrict(int sx, int sy, int axis) {
		Seq<Segment> out = new Seq<>();
		getTree(axis).intersect(sx, sy, 1, 1, out);
		return out.find(segment -> segment.start.tileX() == sx && segment.start.tileY() == sy);
	}

	public static void formSegment(int pos) {
		if (!(Vars.world.build(pos) instanceof ItemBridgeBuild bridge)) return;
		formSegment(bridge);
	}

	public static void formSegment(ItemBridgeBuild bridge) {
		if (segHead(bridge) && linkValid(bridge)) {
			Segment found = findSeg(bridge.tileX(), bridge.tileY(), Segment.linkDir(bridge));
			if (found != null) {
				found.update();
			} else {
				Segment seg = new Segment(bridge);
				allSegments.add(seg);
				getTree(seg.linkDir()).insert(seg);
			}
		}
	}

	static public QuadTree<Segment> getTree(int dir) {
		return dir % 2 == 1 ? vertSeg : horiSeg;
	}

	public static void both(Cons<QuadTree<Segment>> cons) {
		cons.get(horiSeg);
		cons.get(vertSeg);
	}

}