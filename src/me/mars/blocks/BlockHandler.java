package me.mars.blocks;

import arc.Core;
import arc.Events;
import arc.struct.ObjectMap;
import arc.struct.OrderedMap;
import arc.struct.Seq;
import arc.util.Reflect;
import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.EventType;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.game.Schematic;
import mindustry.gen.Icon;
import mindustry.input.InputHandler;
import mindustry.type.Category;
import mindustry.ui.dialogs.SchematicsDialog;
import mindustry.ui.fragments.PlacementFragment;
import mindustry.world.Block;
import mindustry.world.blocks.distribution.BufferedItemBridge;
import mindustry.world.blocks.power.PowerNode;
import mindustry.world.meta.BuildVisibility;

public class BlockHandler {
	public static OrderedMap<String, Category> glyphMapping = OrderedMap.of(
			"\ue871", Category.turret,
			"\ue85e", Category.production,
			"\ue814", Category.distribution,
			"\ue85c", Category.liquid,
			"\ue810", Category.power,
			"\ue84d", Category.defense,
			"\ue830", Category.crafting,
			"\ue86d", Category.units,
			"\ue853", Category.effect,
			"\ue80e", Category.logic
	);
	public static void init() {
		Seq<SchematicBlock> schematicBlocks = new Seq<>();
		Events.on(ContentInitEvent.class, contentInitEvent -> {
			if (Core.settings.getBool("bridging.custom-blocks", false)) {
				new WeavedNode((PowerNode) Blocks.powerNode);
				new ShortBridge((BufferedItemBridge) Blocks.itemBridge, 2);
				new ShortBridge((BufferedItemBridge) Blocks.itemBridge, 3);
				new SchematicGuideBlock();
			}
			Seq<String> glyphs = glyphMapping.orderedKeys();
			for (Schematic schematic : Vars.schematics.all()) {
				Seq<String> filteredTags = schematic.labels.select(glyphs::contains);
				String firstTag = filteredTags.firstOpt();
				if (firstTag == null) continue;
				schematicBlocks.add(new SchematicBlock(schematic, glyphMapping.get(firstTag)));
			}
		});

		Events.on(EventType.ClientLoadEvent.class, clientLoadEvent -> {
			// TODO: Incredibly cursed, please refactor this
			// Load SchematicBlock icons after other blocks have loaded their icons
			SchematicBlock.loadMapping();
			schematicBlocks.each(b -> {
				Block block = SchematicBlock.iconMapping.get(b.schematic.name());
				b.blockIcon = block != null? block.fullIcon : Icon.none.getRegion();
				b.loadIcon();
			});
			// Adding tags
			Seq<String> tags = Reflect.get(SchematicsDialog.class, Vars.ui.schematics, "tags");
			for (String glyph : glyphMapping.keys()) {
				tags.addUnique(glyph);
			}
			Reflect.invoke(SchematicsDialog.class, Vars.ui.schematics, "tagsChanged", null);
			// Making the blocks "clickable"
			ObjectMap<Category, Block> selectedBlocks = Reflect.get(PlacementFragment.class,
							Vars.ui.hudfrag.blockfrag, "selectedBlocks");
			Events.run(EventType.Trigger.update, () -> {
				InputHandler input = Vars.control.input;
				if (input.block instanceof ButtonBlock button) {
					selectedBlocks.put(button.category, null);
					input.block = null;
					button.clicked();
				}
			});
		});

	}

	public static void cloneStats(Block block, Block target) {
		block.stats = target.stats;
		block.health = target.health;
		block.size = target.size;
		block.buildCost = target.buildCost;
		block.requirements(target.category, target.requirements.clone());
		block.buildVisibility = target.unlocked() ? target.buildVisibility : BuildVisibility.hidden;
	}
}