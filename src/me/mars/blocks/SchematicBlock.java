package me.mars.blocks;

import arc.Core;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectMap;
import arc.util.Nullable;
import mindustry.Vars;
import mindustry.game.Schematic;
import mindustry.gen.Icon;
import mindustry.type.Category;
import mindustry.world.Block;
import mindustry.world.blocks.ItemSelection;

public class SchematicBlock extends ButtonBlock {
	public static ObjectMap<String, Block> iconMapping = new ObjectMap<>();
	public static void loadMapping() {
		iconMapping = Core.settings.getJson("bridging.iconMap", ObjectMap.class, Block.class, ObjectMap::new);
	}

	public Schematic schematic;
	Block block;
	public SchematicBlock(Schematic schem, Category cat) {
		super(schem.name(), Icon.none.getRegion());
		this.requirements(cat, schem.requirements().toArray());
		this.schematic = schem;
		this.init();
	}

	@Override
	public void clicked() {
		Vars.control.input.useSchematic(this.schematic);
	}

	@Override
	public void displayExtra(Table table) {
		table.button("@schematic",() -> Vars.ui.schematics.showInfo(this.schematic)).fillX();
		table.row();
		// TODO: Bundles
		table.add("Change icon:");
		table.row();
		table.table(t -> ItemSelection.buildTable(t, Vars.content.blocks().select(b -> !(b instanceof ButtonBlock)),
				() -> this.block, this::iconChange)).fillX();
	}

	public void iconChange(@Nullable Block to) {
		if (to == null) {
			iconMapping.remove(this.schematic.name());
			this.fullIcon = this.uiIcon = Icon.none.getRegion();
		} else {
			iconMapping.put(this.schematic.name(), to);
			this.fullIcon = this.uiIcon = to.fullIcon;
		}
		Core.settings.putJson("bridging.iconMap", iconMapping);
		this.block = to;
	}

}
