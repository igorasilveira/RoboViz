/*
 *  Copyright 2011 RoboViz
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package rv.ui.screens;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.util.awt.TextRenderer;
import com.jogamp.opengl.util.gl2.GLUT;
import java.awt.Font;
import java.util.List;
import jsgl.jogl.view.Viewport;
import rv.Viewer;
import rv.comm.rcssserver.GameState;

/**
 * Displays a running list of fouls. Based off initial implementation by Sander van Dijk.
 *
 * @author patmac
 */
public class StatisticsOverlay extends ScreenBase
{
	private static final int FOUL_HEIGHT = 25;
	private static final int FOUL_WIDTH = 235;
	private static final float FOUL_SHOW_TIME = 8.0f;
	private static final float FOUL_FADE_TIME = 2.0f;
	private static final int TOP_SCREEN_OFFSET = 17;
	private static final int SIDE_SCREEN_OFFSET = 17;

	private final TextRenderer tr;

	private final Viewer viewer;

	public StatisticsOverlay(Viewer viewer)
	{
		this.viewer = viewer;
		tr = new TextRenderer(new Font("Arial", Font.PLAIN, 20), true, false);
	}

	void render(GL2 gl, GameState gs, int screenW, int screenH)
	{
		int y = screenH - TOP_SCREEN_OFFSET;
		int x = screenW - FOUL_WIDTH - SIDE_SCREEN_OFFSET;

		float[] lc = viewer.getWorldModel().getLeftTeam().getColorMaterial().getDiffuse();
		float[] rc = viewer.getWorldModel().getRightTeam().getColorMaterial().getDiffuse();

		List<GameState.Foul> fouls = gs.getFouls();
		float n = 1.0f;

		float opacity = 1.0f;
		drawFoul(gl, x, y - (int) (FOUL_HEIGHT * (n - 1)), FOUL_WIDTH, FOUL_HEIGHT, screenW, screenH,
				opacity, lc);
	}

	@Override
	public void render(GL2 gl, GLU glu, GLUT glut, Viewport vp)
	{
		render(gl, viewer.getWorldModel().getGameState(), vp.w, vp.h);
	}

	void drawFoul(GL2 gl, int x, int y, int w, int h, int screenW, int screenH, float opacity,
				  float[] teamColor)
	{
		float[] cardFillColor = new float[] {0.8f, 0.6f, 0.0f, 1.0f};

		cardFillColor[3] = opacity;
		System.out.println(opacity);
		teamColor[3] = opacity / 3;

		gl.glBegin(GL2.GL_QUADS);
		gl.glColor4fv(teamColor, 0);
		gl.glVertex2fv(new float[] {x + 18, y}, 0);
		gl.glVertex2fv(new float[] {x + w, y}, 0);
		gl.glVertex2fv(new float[] {x + w, y - h}, 0);
		gl.glVertex2fv(new float[] {x + 18, y - h}, 0);

		float[][] vertices = {{x + 2, y - 1}, {x + 16, y - 3}, {x + 14, y - FOUL_HEIGHT + 1}, {x, y - FOUL_HEIGHT + 3}};
		gl.glColor4fv(cardFillColor, 0);
		for (float[] vertex : vertices) {
			gl.glVertex2fv(vertex, 0);
		}
		gl.glEnd();

		tr.setColor(0.9f, 0.9f, 0.9f, opacity);
		tr.beginRendering(screenW, screenH);
		tr.draw("FOUL", x + 22, y - h + 4);
		System.out.println("FOUL");
		tr.endRendering();
	}

	public static boolean shouldDisplayFoul(GameState.Foul f, long currentTimeMillis)
	{
		float dt = (currentTimeMillis - f.receivedTime) / 1000.0f;
		return dt < FOUL_SHOW_TIME + FOUL_FADE_TIME;
	}
}
