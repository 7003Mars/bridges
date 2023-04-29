package me.mars.blocks;

import arc.math.geom.Point2;
import arc.struct.Seq;
import mindustry.Vars;
import mindustry.entities.units.BuildPlan;
import mindustry.type.Category;
import mindustry.world.blocks.power.PowerNode;

public class WeavedNode extends PowerNode {
	PowerNode parent;

	public WeavedNode(PowerNode parent) {
		super("weaved-"+parent.name);
		this.parent = parent;
		requirements(Category.power, parent.requirements.clone());
		this.maxNodes = parent.maxNodes;
		this.init();
//		String name = this.name;
//		Reflect.set(MappableContent.class, this, "name", parent.name);
//		Log.info("My name when loading is @", this.name);
//		this.load();
//		Reflect.set(MappableContent.class, this, "name", name);
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
