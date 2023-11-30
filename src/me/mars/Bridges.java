package me.mars;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.graphics.g2d.Draw;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.math.geom.Geometry;
import arc.math.geom.Point2;
import arc.math.geom.Rect;
import arc.math.geom.Vec2;
import arc.scene.event.ChangeListener;
import arc.scene.event.ClickListener;
import arc.scene.event.InputEvent;
import arc.scene.style.TextureRegionDrawable;
import arc.scene.ui.ImageButton;
import arc.struct.IntIntMap;
import arc.struct.IntSeq;
import arc.struct.Seq;
import arc.util.*;
import me.mars.blocks.BlockHandler;
import mindustry.Vars;
import mindustry.game.EventType.*;
import mindustry.gen.Groups;
import mindustry.gen.Icon;
import mindustry.input.Binding;
import mindustry.mod.Mod;
import mindustry.world.Block;
import mindustry.world.Tile;
import mindustry.world.blocks.distribution.ItemBridge;
import mindustry.world.blocks.distribution.ItemBridge.ItemBridgeBuild;

import static mindustry.Vars.*;


public class Bridges extends Mod {
	private final static String internalName = "bridging";

	public static boolean debugMode;
	public static int lineOpacity;
	public static boolean fixedColor;
	private static final int ticksPerUpdate = 10;

	static Rect bounds = new Rect(0, 0, 0, 0);
	public static QuadTree<Segment> vertSeg;
	public static QuadTree<Segment> horiSeg;
	public static Seq<Segment> allSegments = new Seq<>(false);

	private static DragTable table;
	public static Seq<ItemBridge> bridgeBlocks = new Seq<>(); // All bridges of the current world
	public static ItemBridge currentSelection = null;
	private static Seq<Segment> hoverSelected = new Seq<>(false, 8, Segment.class);

	private static int scroll = 0;

	private static float time;
	static Seq<Runnable> queue = new Seq<>();
	static Seq<Runnable> queue2 = new Seq<>();
	public static IntIntMap lastConfigs = new IntIntMap();

	public Bridges() {
		Events.on(ContentInitEvent.class, contentInitEvent -> {
			for (Block block : Vars.content.blocks()) {
				if (/*Vars.indexer.isBlockPresent(block) && */block instanceof ItemBridge bridge) {
					bridgeBlocks.add(bridge);
				}
			}
			bridgeBlocks.add((ItemBridge) null);
		});

		BlockHandler.init();
	}

	@Override
	public void init() {
		if (Vars.headless) return;
		Log.info("@ running version [red]@",internalName, mods.getMod(internalName).meta.version);
		if (!Vars.mobile)ConfigHandler.init();

		Vars.ui.settings.addCategory(Core.bundle.get("setting.bridging.name"), settingsTable -> {
			// TODO: Bundles
			settingsTable.sliderPref("bridging.line-opacity", 70, 10, 100, i -> i+"%");
			settingsTable.checkPref("bridging.fixed-highlight-color", false);
			settingsTable.checkPref("bridging.custom-blocks", false);
			settingsTable.checkPref("bridging.debug-mode", false);
		});

		Events.on(ClientLoadEvent.class, event -> {
			lineOpacity = Core.settings.getInt("bridging.line-opacity");
			fixedColor = Core.settings.getBool("bridging.fixed-highlight-color");
			debugMode = Core.settings.getBool("bridging.debug-mode");

			table = new DragTable();
			table.setSize(50f);
			table.setPosition(0, Core.graphics.getHeight()/2f);
			TextureRegionDrawable blockRegion = new TextureRegionDrawable(Icon.none);
			ImageButton selector = new ImageButton(blockRegion) {{
					this.addListener(new ClickListener(null) {
						@Override
						public void clicked(InputEvent event, float x, float y) {
							// Very cursed
							ChangeListener.ChangeEvent changeEvent = new ChangeListener.ChangeEvent();
							fire(changeEvent);
							if (changeEvent.stopped) return;
							if (event.keyCode != KeyCode.mouseLeft && event.keyCode != KeyCode.mouseRight) return;
							int index = bridgeBlocks.indexOf(currentSelection);
							index = Mathf.mod(index + Mathf.sign(event.keyCode == KeyCode.mouseLeft), bridgeBlocks.size);
							currentSelection = bridgeBlocks.get(index);
							blockRegion.set(currentSelection == null ? Icon.none.getRegion() : currentSelection.uiIcon);
						}
					});
				}
			};
			table.add(selector);
			Vars.ui.hudGroup.addChild(table);
		});

		listenerInit();

		Events.on(ResizeEvent.class, resizeEvent -> table.clampPos());

		Events.run(Trigger.drawOver, () -> {
			if (Vars.state.isGame() && currentSelection != null) {
				Rect camBounds = Tmp.r1;
				camBounds.setSize(Core.camera.width/tilesize, Core.camera.height/tilesize);
				camBounds.setCenter(Core.camera.position.x/tilesize, Core.camera.position.y/tilesize);
				Rect hitbox = Tmp.r2;

				Vec2 mouseCoords = Core.input.mouseWorld();
				int mouseX = Math.round(mouseCoords.x/tilesize), mouseY = Math.round(mouseCoords.y/tilesize);
				hoverSelected.clear();
				float prevZ = Draw.z();
				for (Segment segment : allSegments) {
					if (segment.block != currentSelection) continue;
					segment.hitbox(hitbox);
					if (camBounds.overlaps(hitbox)) {
						boolean hovered = segment.passing.contains(Point2.pack(mouseX, mouseY));
						if (hovered) hoverSelected.add(segment);
						segment.draw(!fixedColor && hovered);
					}
				}
				if (hoverSelected.size < 2) {
					scroll = 0;
				}
				if (hoverSelected.size == 1) {
					hoverSelected.items[0].drawHighlight();
				} else if (hoverSelected.size > 1) {
					int index = scroll == 0 ? (int)(Time.time/7.5f % (hoverSelected.size*Mathf.PI*4) /(Mathf.PI*4)) :
							Mathf.mod(scroll, hoverSelected.size);
					// For Select, index starts at 1
					Select.instance().select(hoverSelected.items, Structs.comparingFloat(segment -> segment.start.pos()),
							index+1, hoverSelected.size).drawHighlight();
				}
				Draw.z(prevZ);
				Draw.reset();
			}
		});

		// Wait 2(?) ticks for incoming to be updated
		Events.run(Trigger.update, () -> {
			// Poll input stuff
			if (state.isPlaying() && Core.input.keyDown(Binding.rotateplaced) && Math.abs(Core.input.axisTap(Binding.rotate)) > 0) {
				scroll+= (int)Core.input.axisTap(Binding.rotate);
			}
			// Run Segment logic
			queue2.each(Runnable::run);
			queue2.clear();
			queue2.addAll(queue);
			queue.clear();
			time+=Time.delta;
			if (time >= ticksPerUpdate && state.isPlaying()) {
				// Settings
				lineOpacity = Core.settings.getInt("bridging.line-opacity");
				fixedColor = Core.settings.getBool("bridging.fixed-highlight-color");
				debugMode = Core.settings.getBool("bridging.debug-mode");

				if (debugMode) Time.mark();
				update();
				if (debugMode) {
					int seqInvalid = allSegments.count(segment -> !segment.valid());
					Seq<Segment> treeSegs = new Seq<>();
					both(tree -> tree.getObjects(treeSegs));
					int treeInvalid = treeSegs.count(segment -> !segment.valid());
					Vars.ui.showInfoToast("Took " + Time.elapsed() + " ms to update\nInvalid: "
							+ seqInvalid + ":" + treeInvalid, ticksPerUpdate/60f);
				}
				time-=ticksPerUpdate;
			}
		});
	}

	public static void listenerInit() {
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

		Events.on(BlockBuildEndEvent.class, blockBuildEndEvent -> {
			if (!(blockBuildEndEvent.tile.build instanceof ItemBridgeBuild bridge)) return;
			// TODO: This may or may not fire late/early. It is an issue can't solve for now. Probably a source of bugs
			// TODO: Figure out what the second part of my comment meant
			if (blockBuildEndEvent.config == null) {
				lastConfigs.remove(bridge.pos());
			} else if (blockBuildEndEvent.config instanceof Integer pos) {
				lastConfigs.put(bridge.pos(), pos);
			} else if (blockBuildEndEvent.config instanceof Point2 point && (point.x != 0 || point.y != 0)) {
				lastConfigs.put(bridge.pos(), Point2.pack(point.x + bridge.tileX(), point.y + bridge.tileY()));
			}
		});

		Events.on(ConfigEvent.class, configEvent -> {
			if (!(configEvent.tile instanceof ItemBridge.ItemBridgeBuild bridge)) return;
			// Update those passing
			Seq<Segment> intersected = new Seq<>();
			both(tree -> {
				intersected.clear();
				tree.intersect(bridge.tileX(), bridge.tileY(), 1, 1, intersected);
				intersected.each(segment -> {
					updateEnd(segment);
					if (!segment.valid()) {
						getTree(segment.linkDir()).remove(segment);
						allSegments.remove(segment);
					}
				});
			});
			// Form for disconnected
			int lastConfig = lastConfigs.get(bridge.pos(), -1); //
			if (Vars.world.build(lastConfig) instanceof ItemBridgeBuild oldLink) {
				oldLink.incoming.removeValue(bridge.pos());
				formSegment(oldLink);
			}
			// Form for new connection: Remove potential leftover Segment in new link
			int linkVal = -1;
			if (configEvent.value instanceof Integer) {
				linkVal = (int) configEvent.value;
			} else if (configEvent.value instanceof Point2 point && point.x != 0 && point.y != 0) {
				linkVal = Point2.pack(point.x + bridge.tileX(), point.y + bridge.tileY());
			}
			if (Vars.world.build(linkVal) instanceof ItemBridgeBuild link) {
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
			}
			// Form new segment
			formSegment(bridge);
			lastConfigs.put(bridge.pos(), linkVal);
		});
	}

	static void bridgeBuilt(ItemBridgeBuild bridge) {
		// TODO: Unknown if this is properly updated.
//		queue2.add(() -> lastConfigs.put(bridge.pos(), bridge.link));
		queue.add(() -> {
			// Form for incoming
			Seq<Segment> intersected = new Seq<>();
			IntSeq incoming = allIncoming(bridge);
			for (int i = 0; i < incoming.size; i++) {
				intersected.clear();
				int pos = incoming.items[i];
				both(tree -> tree.intersect(Point2.x(pos), Point2.y(pos), 1, 1, intersected));
				intersected.each(Bridges::updateEnd);
				formSegment(pos);
			}
			// Form for link
			Segment segment = findSegStrict(Point2.x(bridge.link), Point2.y(bridge.link), Segment.linkDir(bridge));
			if (segment == null || segment.block != bridge.block) {
				formSegment(bridge);
			} else {
				QuadTree<Segment> tree = getTree(segment.linkDir());
				tree.remove(segment);
				segment.start = bridge;
				if (!segHead(bridge)) {
					allSegments.remove(segment);
					return;
				}
				segment.updateEnd();
				tree.insert(segment);
			}
		});
	}

	static void bridgeRemoved(ItemBridgeBuild bridge) {
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
		Seq<Segment> intersected = new Seq<>();
		both(tree -> {
			intersected.clear();
			tree.intersect(bridge.tileX(), bridge.tileY(), 1, 1, intersected);
			intersected.each(segment -> {
				// Jank
				int removeIndex = segment.passing.indexOf(bridge.pos());
				if (removeIndex == -1) return; // Segment isn't involved, skip it
				tree.remove(segment);
				if (removeIndex > 1) {
					// Segment hitbox changed, update tree
					segment.end = (ItemBridgeBuild) Vars.world.build(segment.passing.items[removeIndex-1]);
					segment.passing.setSize(removeIndex);
					tree.insert(segment);
				} else {
					allSegments.remove(segment);
				}
			});
		});
	}

	public static void update() {
		allSegments.each(segment -> {
			segment.selfIndex = 0;
			segment.currentSize = 4;
			segment.occupied.clear();
		});
		allSegments.each(Segment::update);
		allSegments.each(Segment::postUpdate);
	}

	static void updateEnd(Segment segment) {
		// REMOVEME: Some day.
		if (!getTree(segment.linkDir()).remove(segment)) {
			Log.err("Failed to remove segment: @", segment);
			return;
		}
		segment.updateEnd();
		getTree(segment.linkDir()).insert(segment);
	}

	public static void reloadSegments() {
		Time.mark();
		queue.clear();
		queue2.clear();
		horiSeg = new QuadTree<>(bounds);
		vertSeg = new QuadTree<>(bounds);
		allSegments.clear();
		Groups.build.each(building -> {
			if (!(building instanceof ItemBridgeBuild b)) return;
			if (b.link != -1) lastConfigs.put(b.pos(), b.link);
			formSegment(b);

		});
		Log.info("Segments reloaded in @ ms, @/@ segments total",
				Time.elapsed(), allSegments.count(segment -> segment.start.team == Vars.player.team()), allSegments.size);
	}
	private static IntSeq allIncoming(ItemBridgeBuild bridge) {
		ItemBridge block = (ItemBridge)bridge.block;
		IntSeq ret = new IntSeq();
		int x = bridge.tileX(), y = bridge.tileY();
		int range = block.range;
		for (int d = 0; d < 4; d++) {
			for (int i = 1; i-1 < range; i++) {
				int cx = x + Geometry.d4x(d)*i, cy = y + Geometry.d4y(d)*i;
				if (block.linkValid(bridge.tile, world.tile(cx, cy), false)) {
					ItemBridgeBuild inc = (ItemBridgeBuild) world.build(cx, cy);
					if (inc.link == bridge.pos()) ret.add(Point2.pack(cx, cy));
				}
			}
		}
		return ret;
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

	public static Segment findSegStrict(int sx, int sy, int dir) {
		Seq<Segment> out = new Seq<>();
		getTree(dir).intersect(sx, sy, 1, 1, out);
		return out.find(segment -> segment.start.tileX() == sx && segment.start.tileY() == sy && segment.linkDir() == dir);
	}

	public static void formSegment(int pos) {
		if (!(Vars.world.build(pos) instanceof ItemBridgeBuild bridge)) return;
		formSegment(bridge);
	}

	public static void formSegment(ItemBridgeBuild bridge) {
		if (segHead(bridge) && linkValid(bridge)) {
			Segment found = findSeg(bridge.tileX(), bridge.tileY(), Segment.linkDir(bridge));
			if (found != null) {
				updateEnd(found);
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