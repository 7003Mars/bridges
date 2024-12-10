package me.mars;

import arc.Core;
import arc.Events;
import arc.func.Cons;
import arc.graphics.g2d.Draw;
import arc.input.KeyCode;
import arc.math.Mathf;
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
import mindustry.content.Blocks;
import mindustry.game.EventType.*;
import mindustry.gen.Building;
import mindustry.gen.Groups;
import mindustry.gen.Icon;
import mindustry.input.Binding;
import mindustry.mod.Mod;
import mindustry.world.Block;
import mindustry.world.blocks.distribution.DirectionBridge.DirectionBridgeBuild;
import mindustry.world.blocks.distribution.ItemBridge;
import mindustry.world.blocks.distribution.ItemBridge.ItemBridgeBuild;

import static me.mars.BridgeLike.*;
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
	public static Seq<Block> bridgeBlocks = new Seq<>(); // All bridges of the current world
	public static Block currentSelection = null;
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
			bridgeBlocks.add(Blocks.ductBridge);
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
			if (!Vars.state.isGame() || currentSelection == null) return;
			Rect camBounds = Tmp.r1;
			camBounds.setSize(Core.camera.width/tilesize, Core.camera.height/tilesize);
			camBounds.setCenter(Core.camera.position.x/tilesize, Core.camera.position.y/tilesize);
			Rect hitbox = Tmp.r2;

			Vec2 mouseCoords = Core.input.mouseWorld();
			int mouseX = Math.round(mouseCoords.x/tilesize), mouseY = Math.round(mouseCoords.y/tilesize);
			hoverSelected.clear();
			float prevZ = Draw.z();
			for (Segment segment : allSegments) {
				if (segment.bridgeType != currentSelection) continue;
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
			horiSeg = new QuadTree<>(bounds);
			vertSeg = new QuadTree<>(bounds);
			queue.add(Bridges::reloadSegments);
		});

		// These work on the assumption that bridges are 1x1
		Events.on(TilePreChangeEvent.class, tilePreChangeEvent -> {
			Building building = tilePreChangeEvent.tile.build;
			if (building instanceof ItemBridgeBuild || building instanceof DirectionBridgeBuild) {
				bridgeRemoved(building);
			}
		});

		Events.on(TileChangeEvent.class, tileChangeEvent -> {
			Building building = tileChangeEvent.tile.build;
			if (building instanceof ItemBridgeBuild || building instanceof DirectionBridgeBuild) {
				bridgeBuilt(building);
			}
		});

		Events.on(BlockBuildEndEvent.class, blockBuildEndEvent -> {
			if (!(blockBuildEndEvent.tile.build instanceof ItemBridgeBuild bridge)) return;
			// TODO: This may or may not fire late/early. It is an issue I can't solve for now. Probably a source of bugs
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

		Events.on(BuildRotateEvent.class, buildRotateEvent -> {
			if (!(buildRotateEvent.build instanceof DirectionBridgeBuild bridge)) return;
			int rotation = bridge.rotation;
			bridge.rotation = buildRotateEvent.previous;
			DirectionBridgeBuild oldLink = bridge.findLink();
			bridge.rotation = rotation;
			// Try forming a segment for the disconnected bridge
			if (oldLink != null) {
				oldLink.occupied[buildRotateEvent.previous%4] = null;
				formSegment(oldLink);
			}
			if (segHead(bridge)) {
				// Try forming a segment if the bridge is a seghead
				formSegment(bridge);
			} else {
				// Remove leftover segment if this bridge is no longer a seghead
				Segment oldSeg = findSegStrict(bridge.tileX(), bridge.tileY(), buildRotateEvent.previous);
				if (oldSeg != null) {
					getTree(oldSeg.linkDir()).remove(oldSeg);
					allSegments.remove(oldSeg);
				}
			}
			// Update any segments that pass through this bridge
			Seq<Segment> intersected = new Seq<>();
			both(tree -> {
				intersected.clear();
				tree.intersect(bridge.tileX(), bridge.tileY(), 1f, 1f, intersected);
				for (Segment segment : intersected) {
					if (segment.passing.contains(bridge.pos())) updateEnd(segment);
				}
			});
			// Remove leftover segment in new link if it is no longer a seghead
			DirectionBridgeBuild link = bridge.findLink();
			if (link != null) {
				link.occupied[bridge.rotation%4] = bridge;
				if (segHead(link)) {
					formSegment(link);
				} else {
					Segment linkSeg = findSeg(link.tileX(), link.tileY(), rotation);
					if (linkSeg != null) {
						getTree(linkSeg.linkDir()).remove(linkSeg);
						allSegments.remove(linkSeg);
					}
				}

			}
		});
	}


	static void bridgeBuilt(Building bridge) {
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
			int linkPos = getLink(bridge);
			// Form for dirBridge which might have lost its connection
			if (bridge instanceof DirectionBridgeBuild dirBridge) {
				// Revert to before this bridge existed
				dirBridge.tile.build = null;
				for (int i = 0; i < incoming.size; i++) {
					DirectionBridgeBuild incomingBridge = (DirectionBridgeBuild) world.build(incoming.items[i]);
					// Find the previous link of bridges that link to this current bridge
					DirectionBridgeBuild oldLink = incomingBridge.findLink();
					if (oldLink == null) continue;
					// If the current bridge links to the previous link, that bridge
					if (linkPos != oldLink.pos()) {
						oldLink.occupied[incomingBridge.rotation%4] = null;
						formSegment(oldLink);
					}
				}
				dirBridge.tile.build = dirBridge;
			}
			// Form for link
			Segment segment = findSegStrict(Point2.x(linkPos), Point2.y(linkPos), linkDir(bridge));
			if (segment == null || segment.bridgeType != bridge.block) {
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

	static void bridgeRemoved(Building bridge) {
		lastConfigs.remove(bridge.pos());
		// Remove itself
		Segment self = findSegStrict(bridge.tileX(), bridge.tileY(), linkDir(bridge));
		if (self != null) {
			getTree(self.linkDir()).remove(self);
			allSegments.remove(self);
		}
		if (bridge instanceof DirectionBridgeBuild dirBridge) {
			IntSeq incoming = allIncoming(dirBridge);
			// Hack to pretend the building is already gone
			bridge.tile.build = null; // Hack to pretend the building is already gone
			for (int i = 0; i < incoming.size; i++) {
				DirectionBridgeBuild incomingBridge = (DirectionBridgeBuild) world.build(incoming.items[i]);
				DirectionBridgeBuild link = incomingBridge.findLink();
				if (link == null) continue;
				link.occupied[incomingBridge.rotation%4] = incomingBridge;
				if (!segHead(link)) {
					Segment segment = findSegStrict(link.tileX(), link.tileY(), link.rotation);
					if (segment == null) continue;
					getTree(segment.linkDir()).remove(segment);
					allSegments.remove(segment);
				}
			}
			bridge.tile.build = bridge;
		}
		// Make new Segment for link if possible
		Building link = world.build(getLink(bridge));
		if (link instanceof ItemBridgeBuild itemLink) {
			itemLink.incoming.removeValue(bridge.pos());
			formSegment(itemLink);
		} else if (link instanceof DirectionBridgeBuild dirLink) {
			bridge.tile.build = null; // Hack to pretend the building is already gone
			IntSeq linkIncoming = allIncoming(dirLink);
			bridge.tile.build = bridge;
			// linkIncoming should already not include the current bridge
			// Perform a manual seghead check
			int linkDir = dirLink.relativeTo(bridge);
			boolean segHead = true;
			for (int i = 0; i < linkIncoming.size; i++) {
				int pos = linkIncoming.items[i];
				// TODO this if statement was written at 12am check for bs
				if (dirLink.relativeTo(Point2.x(pos), Point2.y(pos)) == linkDir) {
					segHead = false;
					break;
				}
			}
			if (segHead) {
				// TODO linkDir() should never return -1 as the dirLink should always link to the dirLink which must exist?
				dirLink.occupied[linkDir(bridge)%4] = null;
				formSegment(dirLink);
			}
		}
		// Update those linked to the bridge.
		Seq<Segment> intersected = new Seq<>();
		both(tree -> {
			intersected.clear();
			tree.intersect(bridge.tileX(), bridge.tileY(), 1, 1, intersected);
			intersected.each(segment -> {
				// Jank
				int removeIndex = segment.passing.indexOf(bridge.pos());
				if (removeIndex == -1) {
					return; // Segment isn't involved, skip it
				}
				tree.remove(segment);
				// We now need to update the segment ends because DirectionBridges can automatically relink
				bridge.tile.build = null; // Hack to remove the building ahead of time
				segment.updateEnd();
				bridge.tile.build = bridge;
				if (segment.passing.size >= 2) {
					tree.insert(segment); // Segment is long enough, we keep it
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
			if ((building instanceof ItemBridgeBuild b)) {
				if (b.link != -1) lastConfigs.put(b.pos(), b.link);
				formSegment(b);
			} else if (building instanceof DirectionBridgeBuild) {
				formSegment(building);
			}
		});
		Log.info("Segments reloaded in @ ms, @/@ segments total",
				Time.elapsed(), allSegments.count(segment -> segment.start.team == Vars.player.team()), allSegments.size);
	}

	public static Segment findSeg(int sx, int sy, int firstAxisDir) {
		// In theory an axis for firstAxisDir also works I think.
		int firstAxis = firstAxisDir%2;
		Seq<Segment> out = new Seq<>();
		getTree(firstAxis).intersect(sx, sy, 1, 1, out);
		Segment res = out.find(segment -> segment.start.tileX() == sx && segment.start.tileY() == sy);
		if (res != null) return res;
		out.clear();
		getTree(1-firstAxis).intersect(sx, sy, 1, 1, out);
		return out.find(segment -> segment.start.tileX() == sx && segment.start.tileY() == sy);
	}

	public static Segment findSegStrict(int sx, int sy, int dir) {
		Seq<Segment> out = new Seq<>();
		getTree(dir).intersect(sx, sy, 1, 1, out);
		return out.find(segment -> segment.start.tileX() == sx && segment.start.tileY() == sy && segment.linkDir() == dir);
	}

	public static void formSegment(int pos) {
		formSegment(world.build(pos));
	}

	public static void formSegment(Building bridge) {
		if (segHead(bridge) && linkValid(bridge)) {
			Segment found = findSeg(bridge.tileX(), bridge.tileY(), linkDir(bridge));
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