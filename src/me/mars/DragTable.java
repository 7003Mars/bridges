package me.mars;

import arc.Core;
import arc.func.Floatc2;
import arc.input.KeyCode;
import arc.math.Mathf;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.ui.layout.Table;

public class DragTable extends Table {
	public DragTable() {
		super();
		addListener(new InputListener() {
			float lastX, lastY;

			@Override
			public void touchDragged(InputEvent event, float x, float y, int pointer) {
				if(Core.app.isMobile() && pointer != 0) return;
				x = Mathf.clamp(DragTable.this.x - this.lastX + x, 0, Core.graphics.getWidth()-DragTable.this.width);
				y = Mathf.clamp(DragTable.this.y - this.lastY + y, 0, Core.graphics.getHeight()-DragTable.this.height);
				DragTable.this.setPosition(x, y);
			}

			@Override
			public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button) {
				if(Core.app.isMobile() && pointer != 0) return false;
				this.lastX = x;
				this.lastY = y;
				return true;
			}


		});
	}
}
