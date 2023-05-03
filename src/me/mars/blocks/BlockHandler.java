package me.mars.blocks;

import arc.Events;
import mindustry.content.Blocks;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.world.Block;
import mindustry.world.blocks.distribution.BufferedItemBridge;
import mindustry.world.blocks.power.PowerNode;

public class BlockHandler {
	public static void init() {
		Events.on(ContentInitEvent.class, contentInitEvent -> {
			new WeavedNode((PowerNode) Blocks.powerNode);
			new ShortBridge((BufferedItemBridge) Blocks.itemBridge, 2);
			new ShortBridge((BufferedItemBridge) Blocks.itemBridge, 3);
		});
	}

	public static void cloneStats(Block block, Block target) {
		block.health = target.health;
		block.size = target.size;
		block.buildCost = target.buildCost;
		block.requirements(target.category, target.requirements.clone());

	}
}
