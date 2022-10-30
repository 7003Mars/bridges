package me.mars;

import arc.Core;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Lines;
import arc.math.Mathf;
import arc.math.geom.Point2;
import arc.math.geom.QuadTree;
import arc.math.geom.Rect;
import arc.struct.Bits;
import arc.struct.IntSeq;
import arc.struct.ObjectSet;
import arc.struct.Seq;
import arc.util.Nullable;
import arc.util.Time;
import arc.util.Tmp;
import arc.util.noise.VoronoiNoise;
import mindustry.Vars;
import mindustry.graphics.Layer;
import mindustry.graphics.Pal;
import mindustry.world.blocks.distribution.ItemBridge;
import mindustry.world.blocks.distribution.ItemBridge.ItemBridgeBuild;

import static mindustry.Vars.tilesize;

public class Segment implements QuadTree.QuadTreeObject {
	private static final int seed = Mathf.random(1, 1000);
	private static Seq<Segment> tmpSeq = new Seq<>();
	private static ObjectSet<Segment> tmpSet = new ObjectSet<>();

	public ItemBridge block;

	public ItemBridgeBuild start;
	public ItemBridgeBuild end;

	public @Nullable
	Segment next = null;
	public Segment endSegment = this;
	public Bits occupied = new Bits();
	public int selfIndex = 0, currentSize = 4;

	public float xOffset, yOffset;
	public Color color;
	//REMOVEME
	public int max = currentSize;
	// Ensure this seq is ordered
	public IntSeq passing = new IntSeq();

	public Segment(ItemBridgeBuild start) {
		
		this.start = start;
		this.updateEnd();
		this.block = (ItemBridge) start.block;
	}

	public void update() {
		// Updating index
		Bits free = new Bits();
		setRect(this.start.tileX(), this.start.tileY(), this.end.tileX(), this.end.tileY(), Tmp.r1);
		Seq<Segment> intersecting = new Seq<>();
		ModMain2.getTree(this.linkDir()).intersect(Tmp.r1, segment -> {
			if (segment.block == this.block && (segment.linkDir() == this.linkDir() || segment.end != this.end)) {
				intersecting.add(segment);
			}
		});
		intersecting.forEach(segment -> free.or(segment.occupied));
		int index = free.nextClearBit(0);
		this.selfIndex = index;
		intersecting.forEach(segment -> {
			if (index > segment.currentSize) {
				this.currentSize = index;
			}
			segment.occupied.set(index, true);
		});
		// Updating end and next segments
		this.next = ModMain2.findSeg(this.end.tileX(), this.end.tileY(), 1 - this.linkDir() % 2);
		if (this.next == null) {
			// This segment may be connected to the middle of another segment
			tmpSeq.clear();
			ModMain2.getTree(1-(this.linkDir() % 2)).intersect(this.end.tileX(), this.end.tileY(), 1, 1, tmpSeq);
			this.next = tmpSeq.find(segment -> segment.passing.contains(this.end.pos()));
		}
	}

	public void postUpdate() {
		// Set the last segment
		this.endSegment = this;
		ObjectSet<Segment> passed = tmpSet;
		passed.clear();
		Segment endSeg = this;
		while (endSeg.next != null && !passed.contains(endSeg)) {
			passed.add(endSeg);
			endSeg = endSeg.next;
		}
		this.endSegment = endSeg;
		// Updating max
		this.max = this.currentSize;
		setRect(this.start.tileX(), this.start.tileY(), this.end.tileX(), this.end.tileY(), Tmp.r1);
		ModMain2.getTree(this.linkDir()).intersect(Tmp.r1, segment -> {
			if (segment.currentSize > this.max) this.max = segment.currentSize;
		});
		// Updating the offsets for drawing
		int axis = this.linkDir() % 2;
		// TODO: Massive mess
		int emax = (this.max+1) & -2; // Rounding to next multiple of 2. Probably a better way to do this https://stackoverflow.com/a/9194117
		this.xOffset = axis == 1 ? this.block.offset + (this.selfIndex % 2 == 0 ? 0.5f : -0.5f)*tilesize * ((this.selfIndex+1) & -2)/emax : 0;
		this.yOffset = axis == 0 ? this.block.offset + (this.selfIndex % 2 == 0 ? 0.5f : -0.5f)*tilesize * ((this.selfIndex+1) & -2)/emax : 0;
	}

	public void updateEnd() {
		this.passing.clear();
		this.end = getEnd(this.start, this.passing);
		this.color = Color.HSVtoRGB(Math.abs((float) VoronoiNoise.valueNoise2D(this.end.tileX(), this.end.tileY(), seed))*360,
				100, 100);
	}

	static void setRect(int x, int y, int x2, int y2, Rect out) { // Non inclusive
		out.set(x, y, x2-x, y2-y);
		out.normalize();
		out.width++;
		out.height++;
	}

	public static ItemBridgeBuild getEnd(ItemBridgeBuild start, IntSeq bridges) {
		ItemBridgeBuild next = start;
		byte startDir = linkDir(start);
//		bridges.add(next.pos());

		while (Vars.world.build(next.link) instanceof ItemBridgeBuild nextLink && next != nextLink && linkDir(next) == startDir) {
			bridges.add(next.pos());
			next = nextLink;
		}
		bridges.add(next.pos());
		return next;
	}

	public boolean valid() {
		return this.start.isValid() && this.end != this.start && this.end.isValid()
				&& ModMain2.segHead(this.start) && ModMain2.linkValid(this.start) /*&& (ModMain2.linkValid(this.end) || ModMain2.segHead(this.end))*/;
	}

	public byte linkDir() {
		return this.start.relativeTo(this.end);
	}

	public static byte linkDir(ItemBridgeBuild build) {
		return build.relativeTo(Point2.x(build.link), Point2.y(build.link));
	}

	public void draw() {
		if (ModMain2.debugMode) Vars.ui.showLabel(this.selfIndex+":"+this.max, 0.1f, this.start.x, this.start.y);
		int linkAxis = this.linkDir() % 2;
		float x = this.start.x+this.xOffset, y = this.start.y+this.yOffset;
		float lx = this.end.tileX(), ly = this.end.tileY();
		float x2 = this.end.x+this.xOffset, y2 = this.end.y+this.yOffset;
//		// Add head offset
		Segment nextSeg = this.next;
		if (/*this.dstToEnd != 0*/nextSeg != null && this.linkDir() % 2 != nextSeg.linkDir() % 2) {
			x2+= nextSeg.xOffset;
			y2+= nextSeg.yOffset;
		}
		// Add max tail offset
		Seq<Segment> incomingP = tmpSeq.clear();
		ModMain2.getTree(1-linkAxis).intersect(this.start.tileX(), this.start.tileY(), 1, 1, incomingP);
		incomingP.filter(segment -> segment.end.pos() == this.start.pos());
		float maxXOffset = 0, maxYOffset = 0;
		if (incomingP.any()) {
			// what the hell
			if (linkAxis == 0) {
				maxXOffset = lx > this.start.tileX() ? tilesize/2 : -tilesize/2;
			} else {
				maxYOffset = ly > this.start.tileY() ? tilesize/2 : -tilesize/2;
			}
			for (Segment segment : incomingP) {
				if (linkAxis == 0 && lx > this.start.tileX() == segment.xOffset < maxXOffset) maxXOffset = segment.xOffset;
				if (linkAxis == 1 && ly > this.start.tileY() == segment.yOffset < maxYOffset) maxYOffset = segment.yOffset;
			}
			x+=maxXOffset;
			y+=maxYOffset;
		}
		// Actually drawing
		float drawSize = Math.min(1f, (float)3/4*tilesize/this.max);
		Lines.stroke(drawSize);
		Draw.color(this.endSegment.color, ModMain2.lineOpacity/100f);
		Draw.z(Layer.overlayUI);
		Lines.line(x, y, x2, y2);
		// Drawing the arrow(s)
		int linkDist = (int) Mathf.dstm(this.start.tileX(), this.start.tileY(), lx, ly);
//		float alpha = ((Time.time / 100f) % linkDist)/linkDist;
		float alpha = (Time.time / 2f % 100)/100;
		int arrows = Mathf.ceil((float) linkDist / this.block.range);

		Draw.color(Draw.getColor().inv());
		Draw.z(Layer.overlayUI+0.1f);
		for (int i = 0; i < arrows; i++) {
			Draw.rect(Core.atlas.white(),
					Mathf.lerp(x, x2, (alpha + i)/arrows), Mathf.lerp(y, y2, (alpha + i)/arrows), drawSize, drawSize);
		}
		Draw.reset();
	}

	public void drawHighlight() {
		Draw.z(Layer.overlayUI+0.1f);
		// Adding 3 pi by dice roll (or to sync it)
		float alpha = Mathf.absin(Time.time/7.5f + 3*Mathf.PI, 1f, 0.8f);
		Draw.color(ModMain2.fixedColor ? Pal.accent : this.endSegment.color, alpha);
		for (int i = 0; i < this.passing.size; i++) {
			int pos = this.passing.items[i];
			Lines.square((float)Point2.x(pos)*tilesize, (float)Point2.y(pos)*tilesize, 3f);
		}
	}

	@Override
	public void hitbox(Rect out) {
		setRect(this.start.tileX(), this.start.tileY(), this.end.tileX(), this.end.tileY(), out);
	}

	@Override
	public String toString() {
		return "Start: (" + this.start.tileX() + ", " + this.start.tileY() +
				"), End: (" + this.end.tileX() + ", " + this.end.tileY() +
				"), Valid: " + this.valid();
	}
}
