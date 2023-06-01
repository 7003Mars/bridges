package me.mars.blocks;

import arc.math.geom.Point2;
import arc.scene.ui.layout.Table;
import arc.struct.Seq;
import arc.util.Reflect;
import mindustry.Vars;
import mindustry.ctype.MappableContent;
import mindustry.entities.units.BuildPlan;
import mindustry.world.blocks.power.PowerNode;

public class WeavedNode extends PowerNode {
	PowerNode parent;

	public WeavedNode(PowerNode parent) {
		super("weaved-"+parent.name);
		this.parent = parent;
		BlockHandler.cloneStats(this, parent);
		this.laserRange = parent.laserRange;
		this.maxNodes = parent.maxNodes;
		this.init();
	}

	@Override
	public void displayExtra(Table table) {
		table.clear();
		Vars.ui.content.show(this.parent);
	}

	@Override
	public void load() {
		String name = this.name;
		Reflect.set(MappableContent.class, this, "name", parent.name);
		super.load();
		Reflect.set(MappableContent.class, this, "name", name);
		this.region = parent.region;
	}

	@Override
	public void loadIcon() {
		String name = this.name;
		Reflect.set(MappableContent.class, this, "name", parent.name);
		super.loadIcon();
		Reflect.set(MappableContent.class, this, "name", name);
		this.region = parent.region;
	}

	@Override
	public void changePlacementPath(Seq<Point2> points, int rotation) {
	}

	@Override
	public void handlePlacementLine(Seq<BuildPlan> plans) {
		Seq<BuildPlan> filtered = plans.select(plan -> plan.block instanceof WeavedNode);
		if (filtered.size < 2) return;
		Seq<Point2> points = new Seq<>();
		filtered.each(plan -> {
			points.clear();
			for (BuildPlan other : filtered) {
				if (plan.within(other, this.parent.laserRange* Vars.tilesize) && other != plan) {
					points.add(new Point2(other.x, other.y).sub(plan.x, plan.y));
				}
			}
			plan.config = points.toArray(Point2.class);
		});
	}

	@Override
	public void onNewPlan(BuildPlan plan) {
		plan.block = this.parent;
	}
}
