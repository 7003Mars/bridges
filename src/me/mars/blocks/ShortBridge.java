package me.mars.blocks;

import arc.scene.ui.layout.Table;
import arc.util.Reflect;
import mindustry.Vars;
import mindustry.ctype.MappableContent;
import mindustry.entities.units.BuildPlan;
import mindustry.world.blocks.distribution.BufferedItemBridge;

public class ShortBridge extends BufferedItemBridge {
	BufferedItemBridge parent;
	public ShortBridge(BufferedItemBridge parent, int range) {
		super(parent.name+"["+range+"]");
		this.parent = parent;
		BlockHandler.cloneStats(this, parent);
		this.range = Math.min(parent.range-1, range);
		this.itemCapacity = parent.itemCapacity;
		this.bufferCapacity = parent.bufferCapacity;
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
	public void onNewPlan(BuildPlan plan) {
		plan.block = this.parent;
	}
}
