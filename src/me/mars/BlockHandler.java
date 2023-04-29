package me.mars;

import arc.Events;
import me.mars.blocks.WeavedNode;
import mindustry.content.Blocks;
import mindustry.game.EventType.ContentInitEvent;
import mindustry.world.blocks.power.PowerNode;

public class BlockHandler {
	public static void init() {
		Events.on(ContentInitEvent.class, contentInitEvent -> {
			new WeavedNode((PowerNode) Blocks.powerNode);
		});
	}
}
