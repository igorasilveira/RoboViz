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
import java.util.Locale;

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
	private int CURRENT_SCREEN = 0;
	private static final int BAR_HEIGHT = 24;
	private static final int NAME_WIDTH = 220;
	private static final int SCORE_BOX_WIDTH = 56;
	private static final int Y_PAD = 4;
	private static final int TOP_SCREEN_OFFSET = 17;
	private static final int SIDE_SCREEN_OFFSET = 17;
	private static final int INFO_WIDTH = NAME_WIDTH * 2;

	private final TextRenderer tr1;
	private final TextRenderer tr2;

	private final Viewer viewer;

	public StatisticsOverlay(Viewer viewer)
	{
		this.viewer = viewer;
		tr1 = new TextRenderer(new Font("Arial", Font.PLAIN, 22), true, false);
		tr2 = new TextRenderer(new Font("Arial", Font.PLAIN, 20), true, false);
	}

	void render(GL2 gl, GameState gs, int screenW, int screenH)
	{
		long currentTimeMillis = System.currentTimeMillis();
		if (!shouldDisplayStatistics(currentTimeMillis)) {
			return;
		}

		int y = screenH - TOP_SCREEN_OFFSET;
		int x = screenW - INFO_WIDTH - SIDE_SCREEN_OFFSET;

		String teamL = gs.getUIStringTeamLeft();
		String teamR = gs.getUIStringTeamRight();

		// truncate team names that are too long to fit within bounds
		while (tr1.getBounds(teamL).getWidth() > NAME_WIDTH - 4)
			teamL = teamL.substring(0, teamL.length() - 1);
		while (tr1.getBounds(teamR).getWidth() > NAME_WIDTH - 4)
			teamR = teamR.substring(0, teamR.length() - 1);

		double lxpad = (NAME_WIDTH - tr1.getBounds(teamL).getWidth()) / 2;
		double rxpad = (NAME_WIDTH - tr1.getBounds(teamR).getWidth()) / 2;

		float[] lc = viewer.getWorldModel().getLeftTeam().getColorMaterial().getDiffuse();
		float[] rc = viewer.getWorldModel().getRightTeam().getColorMaterial().getDiffuse();

		gl.glBegin(GL2.GL_QUADS);
		gl.glColor4f(0, 0, 0, 0.5f);
		drawBox(gl, x - 3, y - 3, 2 * NAME_WIDTH + SCORE_BOX_WIDTH + 6, BAR_HEIGHT + 6);
		drawBox(gl, x - 3, y - 3, 2 * NAME_WIDTH + SCORE_BOX_WIDTH + 6, BAR_HEIGHT * 0.6f);

		gl.glColor4f(lc[0] * 0.8f, lc[1] * 0.8f, lc[2] * 0.8f, 0.65f);
		drawBox(gl, x, y, NAME_WIDTH, BAR_HEIGHT);
		gl.glColor4f(1, .3f, .3f, 0.65f);

		gl.glColor4f(rc[0] * 0.8f, rc[1] * 0.8f, rc[2] * 0.8f, 0.65f);
		drawBox(gl, x + NAME_WIDTH, y, NAME_WIDTH, BAR_HEIGHT);
		gl.glEnd();

		tr1.beginRendering(screenW, screenH);
		tr1.draw(teamL, (int) (x + lxpad), y + Y_PAD);
		tr1.draw(teamR, (int) (x + NAME_WIDTH + rxpad), y + Y_PAD);
		tr1.endRendering();

		float n = 1.0f;
		float opacity = 1.0f;
		drawFoul(gl, x, y - (int) (INFO_WIDTH * (n - 1)), INFO_WIDTH, INFO_WIDTH, screenW, screenH,
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
		teamColor[3] = opacity / 3;

		gl.glBegin(GL2.GL_QUADS);
		gl.glColor4fv(teamColor, 0);
		gl.glVertex2fv(new float[] {x + 18, y}, 0);
		gl.glVertex2fv(new float[] {x + w, y}, 0);
		gl.glVertex2fv(new float[] {x + w, y - h}, 0);
		gl.glVertex2fv(new float[] {x + 18, y - h}, 0);

		float[][] vertices = {{x + 2, y - 1}, {x + 16, y - 3}, {x + 14, y - INFO_WIDTH + 1}, {x, y - INFO_WIDTH + 3}};
		gl.glColor4fv(cardFillColor, 0);
		for (float[] vertex : vertices) {
			gl.glVertex2fv(vertex, 0);
		}
		gl.glEnd();

		tr2.setColor(0.9f, 0.9f, 0.9f, opacity);
		tr2.beginRendering(screenW, screenH);
		tr2.draw("FOUL", x + 22, y - h + 4);
		tr2.endRendering();
	}

	public static boolean shouldDisplayStatistics(long currentTimeMillis)
	{
		return true;
	}

	static void drawBox(GL2 gl, float x, float y, float w, float h)
	{
		gl.glVertex2f(x, y);
		gl.glVertex2f(x + w, y);
		gl.glVertex2f(x + w, y + h);
		gl.glVertex2f(x, y + h);
	}
}
