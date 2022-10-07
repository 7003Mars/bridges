package me.mars;

import arc.Core;
import arc.func.Floatc2;
import arc.input.KeyCode;
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
				DragTable.this.setPosition(DragTable.this.x - this.lastX + x, DragTable.this.y - this.lastY + y);
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
