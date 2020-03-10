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
import java.util.stream.Collectors;

import jsgl.jogl.view.Viewport;
import rv.Viewer;
import rv.comm.rcssserver.GameState;

public class StatisticsOverlay extends ScreenBase
{
	private int CURRENT_SCREEN = 0;
	private static long DEPLOY_TIME = 0;
	private static final int BAR_HEIGHT = 24;
	private static final int NAME_WIDTH = 220;
	private static final int SCORE_BOX_WIDTH = 5;
	private static final int Y_PAD = 4;
	private static final int LINE_HEIGHT = Y_PAD * 2 + 20;
	private static final int TOP_SCREEN_OFFSET = 150;
	private static final int SIDE_SCREEN_OFFSET = 17;
	private static final int INFO_WIDTH = NAME_WIDTH * 2;
	private static final float PANEL_SHOW_TIME = 8.0f;
	private static final float PANEL_FADE_TIME = 2.0f;

	private StatisticsPanel[] panels;

	private final TextRenderer tr1;
	private final TextRenderer tr2;

	private final Viewer viewer;

	public StatisticsOverlay(Viewer viewer)
	{
		this.viewer = viewer;
		tr1 = new TextRenderer(new Font("Arial", Font.PLAIN, 22), true, false);
		tr2 = new TextRenderer(new Font("Arial", Font.PLAIN, 20), true, false);
		DEPLOY_TIME = (long)(System.currentTimeMillis() + 10 * 1000.f);
	}

	void render(GL2 gl, GameState gs, int screenW, int screenH)
	{
		long currentTimeMillis = System.currentTimeMillis();
		if (!shouldDisplayStatistics(currentTimeMillis, gs)) {
			return;
		}

		List<GameState.Statistic> statistics = gs.getStatistics();
		float n = 1.0f;

//		if (statistics.isEmpty()) {
//			return;
//		}

		buildPanels(statistics);
//		gs.clearStatistics();

		float dt = (currentTimeMillis - DEPLOY_TIME) / 1000.0f;
		float opacity = dt > PANEL_SHOW_TIME ? 1.0f - (dt - PANEL_SHOW_TIME) / PANEL_FADE_TIME : 1.0f;
		drawPanel(gl, gs, screenW, screenH, n, opacity);
		n += opacity;
	}

	private void buildPanels(List<GameState.Statistic> statistics) {
		// Offside
		List<GameState.Statistic> offsideStatistics = statistics.stream()
				.filter(statistic -> statistic.type.equals(GameState.StatisticType.OFFSIDE))
				.collect(Collectors.toList());

		List<GameState.Statistic> leftOffsideStatistics = offsideStatistics.stream()
				.filter(statistic -> statistic.team == 1)
				.collect(Collectors.toList());

		// Foul
		List<GameState.Statistic> foulStatistics = statistics.stream()
				.filter(statistic -> statistic.type.equals(GameState.StatisticType.FOUL))
				.collect(Collectors.toList());

		List<GameState.Statistic> leftFoulStatistics = foulStatistics.stream()
				.filter(statistic -> statistic.team == 1)
				.collect(Collectors.toList());

		// Corner
		List<GameState.Statistic> cornerStatistics = statistics.stream()
				.filter(statistic -> statistic.type.equals(GameState.StatisticType.CORNER))
				.collect(Collectors.toList());

		List<GameState.Statistic> leftCornerStatistics = cornerStatistics.stream()
				.filter(statistic -> statistic.team == 1)
				.collect(Collectors.toList());

		// Free Kick
		List<GameState.Statistic> freeKickStatistics = statistics.stream()
				.filter(statistic -> statistic.type.equals(GameState.StatisticType.FREE_KICK))
				.collect(Collectors.toList());

		List<GameState.Statistic> leftFreeKickStatistics = freeKickStatistics.stream()
				.filter(statistic -> statistic.team == 1)
				.collect(Collectors.toList());

		// Pass
		List<GameState.Statistic> passStatistics = statistics.stream()
				.filter(statistic -> statistic.type.equals(GameState.StatisticType.PASS))
				.collect(Collectors.toList());

		List<GameState.Statistic> leftPassStatistics = passStatistics.stream()
				.filter(statistic -> statistic.team == 1)
				.collect(Collectors.toList());

		// Goal Kick
		List<GameState.Statistic> goalKickStatistics = statistics.stream()
				.filter(statistic -> statistic.type.equals(GameState.StatisticType.GOAL_KICK))
				.collect(Collectors.toList());

		List<GameState.Statistic> leftGoalKickStatistics = goalKickStatistics.stream()
				.filter(statistic -> statistic.team == 1)
				.collect(Collectors.toList());

		// Goal Kick
		List<GameState.Statistic> kickInStatistics = statistics.stream()
				.filter(statistic -> statistic.type.equals(GameState.StatisticType.KICK_IN))
				.collect(Collectors.toList());

		List<GameState.Statistic> leftKickInStatistics = kickInStatistics.stream()
				.filter(statistic -> statistic.team == 1)
				.collect(Collectors.toList());

		String[] titlesOne = {"Fouls", "Offsides", "Corners", "Free kicks", "Passes", "Goal Kicks", "Kick ins"};
		String[][] valuesOne = {
				{String.valueOf(leftFoulStatistics.size()), String.valueOf(foulStatistics.size() - leftFoulStatistics.size())},
				{String.valueOf(leftOffsideStatistics.size()), String.valueOf(offsideStatistics.size() - leftOffsideStatistics.size())},
				{String.valueOf(leftCornerStatistics.size()), String.valueOf(cornerStatistics.size() - leftCornerStatistics.size())},
				{String.valueOf(leftFreeKickStatistics.size()), String.valueOf(freeKickStatistics.size() - leftFreeKickStatistics.size())},
				{String.valueOf(leftPassStatistics.size()), String.valueOf(passStatistics.size() - leftPassStatistics.size())},
				{String.valueOf(leftGoalKickStatistics.size()), String.valueOf(goalKickStatistics.size() - leftGoalKickStatistics.size())},
				{String.valueOf(leftKickInStatistics.size()), String.valueOf(kickInStatistics.size() - leftKickInStatistics.size())}
		};

		StatisticsPanel statisticsPanelOne = new StatisticsPanel(titlesOne, valuesOne);

		panels = new StatisticsPanel[] {
				statisticsPanelOne
		};
	}

	private void drawPanel(GL2 gl, GameState gs, int screenW, int screenH, float n, float opacity) {

		int y = screenH - TOP_SCREEN_OFFSET;
		int x = screenW - INFO_WIDTH - SIDE_SCREEN_OFFSET;

		drawTeamNames(gl, gs, screenW, screenH);

		drawStatistics(gl, x, y - (int) (INFO_WIDTH * (n - 1)), INFO_WIDTH, INFO_WIDTH, screenW, screenH,
				1.0f); //CHANGE ME
	}

	private void drawTeamNames(GL2 gl, GameState gs, int screenW, int screenH) {
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
		drawBox(gl, x - 3, y - 3, 2 * NAME_WIDTH + SCORE_BOX_WIDTH, BAR_HEIGHT + 6);
		drawBox(gl, x - 3, y - 3, 2 * NAME_WIDTH + SCORE_BOX_WIDTH, BAR_HEIGHT * 0.6f);

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
	}

	@Override
	public void render(GL2 gl, GLU glu, GLUT glut, Viewport vp)
	{
		render(gl, viewer.getWorldModel().getGameState(), vp.w, vp.h);
	}

	void drawStatistics(GL2 gl, int x, int y, int w, int h, int screenW, int screenH, float opacity)
	{
		float[] panelColor = new float[] {0.3f, 0.3f, 0.3f, 1.0f};

		panelColor[3] = opacity / 3;

		StatisticsPanel panel = panels[CURRENT_SCREEN];
		int numberOfLines = panel.getTitles().length;

		gl.glBegin(GL2.GL_QUADS);
		gl.glColor4fv(panelColor, 0);
		gl.glVertex2fv(new float[] {x, y}, 0);
		gl.glVertex2fv(new float[] {x + w, y}, 0);
		gl.glVertex2fv(new float[] {x + w, y - (numberOfLines + 0.5f) * LINE_HEIGHT}, 0);
		gl.glVertex2fv(new float[] {x, y - (numberOfLines + 0.5f) * LINE_HEIGHT}, 0);

		gl.glEnd();

		int LINE_Y = y - Y_PAD - BAR_HEIGHT;

		tr2.setColor(0.9f, 0.9f, 0.9f, opacity);
		tr2.beginRendering(screenW, screenH);

		for (int i = 0; i < panel.getTitles().length; i++) {
			tr2.draw(panel.getTitles()[i], x + w / 2 - (int) tr2.getBounds(panel.getTitles()[i]).getWidth() / 2, LINE_Y);
			tr2.draw(panel.getValues()[i][0], x + Y_PAD, LINE_Y);
			tr2.draw(panel.getValues()[i][1], x + w - Y_PAD - (int) tr2.getBounds(panel.getValues()[i][1]).getWidth(), LINE_Y);
			LINE_Y -= (LINE_HEIGHT);
		}
		tr2.endRendering();
	}

	public static boolean shouldDisplayStatistics(long currentTimeMillis, GameState gs)
	{
		if (!gs.isInitialized()) return false;
		return true;
//		float dt = (currentTimeMillis - DEPLOY_TIME) / 1000.0f;
//		System.out.println(dt);
//		return dt < PANEL_SHOW_TIME + PANEL_FADE_TIME;
	}

	static void drawBox(GL2 gl, float x, float y, float w, float h)
	{
		gl.glVertex2f(x, y);
		gl.glVertex2f(x + w, y);
		gl.glVertex2f(x + w, y + h);
		gl.glVertex2f(x, y + h);
	}
}
