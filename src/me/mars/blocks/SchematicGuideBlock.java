package me.mars.blocks;

import arc.scene.ui.layout.Table;
import mindustry.Vars;
import mindustry.gen.Icon;
import mindustry.type.Category;
import mindustry.type.ItemStack;

public class SchematicGuideBlock extends ButtonBlock {
	public SchematicGuideBlock() {
		super("Custom button help", Icon.eye.getRegion());
		requirements(Category.production, new ItemStack[]{});
	}

	@Override
	public void clicked() {
		Vars.ui.content.show(this);
	}

	@Override
	public void displayExtra(Table table) {
		// TODO: Bundles
		table.add("Tag a schematic with the icon corresponding to its category to add it to the placement menu");
		table.row();
		table.add("Restart required for changes to take effect");
	}
}
