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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import jsgl.jogl.view.Viewport;
import jsgl.math.vector.Vec2f;
import rv.Viewer;
import rv.comm.rcssserver.GameState;
import rv.comm.rcssserver.StatisticsParser;
import rv.util.MathUtil;
import rv.world.Team;
import rv.world.WorldModel;
import rv.world.objects.Agent;

public class StatisticsOverlay
		extends ScreenBase implements GameState.GameStateChangeListener, StatisticsParser.StatisticsParserListener
{
	private float fieldWidth = 180;
	private float fieldLength = 120;
	private float screenWidth = 1;
	private float screenHeight = 1;
	private static final float fieldOverlayWidthFactor = 0.3f;
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

	private boolean isInitialized = false;

	// TIMELINE

	private static final float TOTAL_GAME_TIME = 600;
	private static final float timelineWidthFactor = 0.5f;
	private static final float timelineHeightFactor = 0.07f;
	private static final float TIME_LINE_TIME_BAR_WIDTH = 4;
	private static final float TIME_LINE_EVENT_BAR_WIDTH = 15;
	private static final int TIME_LINE_END_HEIGHT = 5;
	private int timelineY = 400;

	// Heat map structures
	private static final float MIN_HEAT_OPACITY = 0.3f;
	private static final int MAP_SQUARE_SIZE = 10;
	private int positionsCount = 0;
	private int heatMapY = 300;

	// Graph variable
	private final int GRAPH_WIDTH = 400;
	private final int GRAPH_HEIGHT = 250;
	private final int GRAPH_PADDING = 20;

	private float time;
	private float prevTime;

	// Global cycle variables
	private Agent agent = null;

	private StatisticsPanel[] panels;

	private final TextRenderer tr1;
	private final TextRenderer tr2;

	private final Viewer viewer;
	private final StatisticsParser statisticsParser;

	public StatisticsOverlay(Viewer viewer)
	{
		this.viewer = viewer;
		this.statisticsParser = viewer.getStatisticsParser();
		statisticsParser.addListener(this);

		time = 0;
		prevTime = 0;

		viewer.getWorldModel().getGameState().addListener((GameState.GameStateChangeListener) this);

		tr1 = new TextRenderer(new Font("Arial", Font.PLAIN, 22), true, false);
		tr2 = new TextRenderer(new Font("Arial", Font.PLAIN, 20), true, false);

		DEPLOY_TIME = (long) (System.currentTimeMillis() + 10 * 1000.f);
	}

	void render(GL2 gl, GLU glu, Viewport vp, GameState gs, int screenW, int screenH)
	{
		long currentTimeMillis = System.currentTimeMillis();
		drawTimeLine(gl, glu, vp, gs);

		if (!shouldDisplayStatistics(currentTimeMillis, gs)) {
			return;
		}

		float n = 1.0f;

		buildPanels();
		//				gs.clearStatistics();

		float dt = (currentTimeMillis - DEPLOY_TIME) / 1000.0f;
		float opacity = dt > PANEL_SHOW_TIME ? 1.0f - (dt - PANEL_SHOW_TIME) / PANEL_FADE_TIME : 1.0f;
		drawPanel(gl, gs, screenW, screenH, n, opacity);
		//		drawHeatMap(gl, glu, vp);
		//		drawPossessionGraph(gl, glu, vp);
		n += opacity;
	}

	private void drawPossessionGraph(GL2 gl, GLU glu, Viewport vp)
	{
		List<StatisticsParser.PossessionStatistic> possessionValuesOverTime =
				statisticsParser.getPossessionValuesOverTime();
		if (possessionValuesOverTime.isEmpty())
			return;

		int bottomLeftX = (int) (screenWidth - Y_PAD - GRAPH_WIDTH);
		int bottomLeftY = (int) (screenHeight - 100 - GRAPH_HEIGHT);

		int pSize = (int) (screenWidth * 0.003);

		gl.glBegin(GL2.GL_QUADS);
		// Background
		gl.glColor4f(0.8f, 0.8f, 0.8f, 0.1f);
		drawBox(gl, bottomLeftX, bottomLeftY, GRAPH_WIDTH, GRAPH_HEIGHT);
		gl.glEnd();

		// TITLE
		int titleY = (int) (bottomLeftY + GRAPH_HEIGHT - Y_PAD - tr1.getBounds("Possession History").getHeight());
		tr1.beginRendering((int) screenWidth, (int) screenHeight);
		tr1.draw("Possession History",
				(int) (bottomLeftX + GRAPH_WIDTH / 2 - tr1.getBounds("Possession History").getWidth() / 2), titleY);
		tr1.endRendering();

		// INTERNAL WINDOW
		int graphBottomLeftX = bottomLeftX + GRAPH_PADDING;
		int graphBottomLeftY = bottomLeftY + GRAPH_PADDING;
		int graphTopRightX = graphBottomLeftX + GRAPH_WIDTH - GRAPH_PADDING * 2;
		int graphTopRightY = graphBottomLeftY +
							 (int) (GRAPH_HEIGHT - tr1.getBounds("Possession History").getHeight() - GRAPH_PADDING * 2);
		int localGraphWidth = graphTopRightX - graphBottomLeftX;
		int localGraphHeight = graphTopRightY - graphBottomLeftY;

		gl.glBegin(GL2.GL_QUADS);
		gl.glColor4f(0, 0, 0, 0.2f);
		drawBox(gl, graphBottomLeftX, graphBottomLeftY, localGraphWidth, localGraphHeight);
		gl.glEnd();

		for (int i = 0; i < possessionValuesOverTime.size(); i++) {
			StatisticsParser.PossessionStatistic statistic = possessionValuesOverTime.get(i);

			gl.glEnable(GL2.GL_POINT_SMOOTH);
			gl.glPointSize(pSize);
			gl.glBegin(GL2.GL_POINTS);

			// Left team
			gl.glColor3fv(viewer.getWorldModel().getLeftTeam().getColorMaterial().getDiffuse(), 0);
			gl.glVertex3f(graphBottomLeftX + (statistic.time / 600) * localGraphWidth,
					graphBottomLeftY + statistic.leftPossession * localGraphHeight, 0);

			gl.glEnd();

			if (i > 0) {
				StatisticsParser.PossessionStatistic prevStatistic = possessionValuesOverTime.get(i - 1);

				gl.glColor3fv(viewer.getWorldModel().getLeftTeam().getColorMaterial().getDiffuse(), 0);
				gl.glLineWidth(2);

				gl.glBegin(GL2.GL_LINES);
				gl.glVertex3f(graphBottomLeftX + (prevStatistic.time / 600) * localGraphWidth,
						graphBottomLeftY + prevStatistic.leftPossession * localGraphHeight, 0);

				gl.glVertex3f(graphBottomLeftX + (statistic.time / 600) * localGraphWidth,
						graphBottomLeftY + statistic.leftPossession * localGraphHeight, 0);
				gl.glEnd();

				// LEFT TEAM AREA CHART
				gl.glBegin(GL2.GL_QUADS);

				gl.glColor4f(viewer.getWorldModel().getLeftTeam().getColorMaterial().getDiffuse()[0],
						viewer.getWorldModel().getLeftTeam().getColorMaterial().getDiffuse()[1],
						viewer.getWorldModel().getLeftTeam().getColorMaterial().getDiffuse()[2], 0.5f);

				gl.glVertex2f(graphBottomLeftX + (prevStatistic.time / 600) * localGraphWidth,
						graphBottomLeftY + prevStatistic.leftPossession * localGraphHeight);

				gl.glVertex2f(graphBottomLeftX + (statistic.time / 600) * localGraphWidth,
						graphBottomLeftY + statistic.leftPossession * localGraphHeight);

				gl.glVertex2f(graphBottomLeftX + (statistic.time / 600) * localGraphWidth, graphBottomLeftY);

				gl.glVertex2f(graphBottomLeftX + (prevStatistic.time / 600) * localGraphWidth, graphBottomLeftY);
				gl.glEnd();

				// RIGHT TEAM AREA CHART
				gl.glBegin(GL2.GL_QUADS);

				gl.glColor4f(viewer.getWorldModel().getRightTeam().getColorMaterial().getDiffuse()[0],
						viewer.getWorldModel().getRightTeam().getColorMaterial().getDiffuse()[1],
						viewer.getWorldModel().getRightTeam().getColorMaterial().getDiffuse()[2], 0.5f);

				gl.glVertex2f(graphBottomLeftX + (prevStatistic.time / 600) * localGraphWidth,
						graphBottomLeftY + prevStatistic.leftPossession * localGraphHeight);

				gl.glVertex2f(graphBottomLeftX + (prevStatistic.time / 600) * localGraphWidth, graphTopRightY);

				gl.glVertex2f(graphBottomLeftX + (statistic.time / 600) * localGraphWidth, graphTopRightY);

				gl.glVertex2f(graphBottomLeftX + (statistic.time / 600) * localGraphWidth,
						graphBottomLeftY + statistic.leftPossession * localGraphHeight);
				gl.glEnd();
			}

			gl.glLineWidth(2);

			gl.glBegin(GL2.GL_LINES);
			gl.glColor4f(1f, 1f, 1f, 0.2f);
			gl.glVertex3f(graphBottomLeftX, graphBottomLeftY + 0.5f * localGraphHeight, 0);

			gl.glVertex3f(graphBottomLeftX + localGraphWidth, graphBottomLeftY + 0.5f * localGraphHeight, 0);
			gl.glEnd();
		}
	}

	private void drawTimeLine(GL2 gl, GLU glu, Viewport vp, GameState gs)
	{
		int timelineTotalWidth = (int) (vp.w * timelineWidthFactor);
		int timelineTotalHeight = (int) (vp.h * timelineHeightFactor) - TIME_LINE_END_HEIGHT;
		float padding = vp.h * timelineHeightFactor;
		Vec2f leftBottom = new Vec2f(screenWidth - padding - timelineTotalWidth, padding);
		Vec2f leftTop = new Vec2f(leftBottom.x, leftBottom.y + timelineTotalHeight);
		Vec2f rightBottom = new Vec2f(leftBottom.x + timelineTotalWidth, leftBottom.y);
		Vec2f rightTop = new Vec2f(leftBottom.x + timelineTotalWidth, leftBottom.y + timelineTotalHeight);

		gl.glBegin(GL2.GL_QUADS);
		// Background
		gl.glColor4f(1f, 1f, 1f, 0.1f);
		drawBox(gl, leftBottom.x, leftBottom.y, rightBottom.x - leftBottom.x, leftTop.y - leftBottom.y);

		gl.glColor4f(0.1f, 0.1f, 0.1f, 0.9f);

		// Middle Bar
		drawBox(gl, leftBottom.x, leftBottom.y + (leftTop.y - leftBottom.y) / 2 - TIME_LINE_TIME_BAR_WIDTH / 2,
				timelineTotalWidth, TIME_LINE_TIME_BAR_WIDTH);

		// Time Bar
		float timeElapsedPercentage = time / TOTAL_GAME_TIME;
		int borderWidth = 3;
		int elapsedTimeBarWidth = 2;

		// Time bar border
		gl.glColor4f(1f, 1f, 1f, 0.6f);
		drawBox(gl, leftBottom.x,
				leftBottom.y + (leftTop.y - leftBottom.y) / 2 -
						((TIME_LINE_TIME_BAR_WIDTH + elapsedTimeBarWidth + borderWidth) / 2),
				timelineTotalWidth * timeElapsedPercentage + elapsedTimeBarWidth,
				TIME_LINE_TIME_BAR_WIDTH + elapsedTimeBarWidth + borderWidth);

		gl.glColor4f(0.1f, 0.1f, 0.8f, 0.9f);
		drawBox(gl, leftBottom.x,
				leftBottom.y + (leftTop.y - leftBottom.y) / 2 - ((TIME_LINE_TIME_BAR_WIDTH + elapsedTimeBarWidth) / 2),
				timelineTotalWidth * timeElapsedPercentage, TIME_LINE_TIME_BAR_WIDTH + elapsedTimeBarWidth);
		gl.glEnd();

		drawTimelineEvents(gl, vp, gs);
	}

	private void drawTimelineEvents(GL2 gl, Viewport vp, GameState gs)
	{
		//		List<StatisticsParser.Statistic> goalsLeft =
		//				statistics.stream()
		//						.filter(statistic
		//								-> statistic.type.equals(StatisticsParser.StatisticType.GOAL) && statistic.team
		//== 1) 						.collect(Collectors.toList());
		//
		//		List<StatisticsParser.Statistic> goalsRight =
		//				statistics.stream()
		//						.filter(statistic
		//								-> statistic.type.equals(StatisticsParser.StatisticType.GOAL) && statistic.team
		//== 2) 						.collect(Collectors.toList());
		//
		//		drawEventList(gl, vp, goalsLeft, true);
		//		drawEventList(gl, vp, goalsRight, false);
	}

	private void drawEventList(GL2 gl, Viewport vp, List<StatisticsParser.Statistic> eventList, boolean leftTeam)
	{
		Team team = leftTeam ? viewer.getWorldModel().getLeftTeam() : viewer.getWorldModel().getRightTeam();
		int timelineTotalWidth = (int) (vp.w * timelineWidthFactor);
		int timelineTotalHeight = (int) (vp.h * timelineHeightFactor) - TIME_LINE_END_HEIGHT;
		float padding = vp.h * timelineHeightFactor;
		Vec2f leftBottom = new Vec2f(screenWidth - padding - timelineTotalWidth, padding);
		Vec2f leftTop = new Vec2f(leftBottom.x, leftBottom.y + timelineTotalHeight);
		Vec2f rightBottom = new Vec2f(leftBottom.x + timelineTotalWidth, leftBottom.y);
		Vec2f rightTop = new Vec2f(leftBottom.x + timelineTotalWidth, leftBottom.y + timelineTotalHeight);
		int eventBarThickness = 2;

		for (StatisticsParser.Statistic statistic : eventList) {
			float timePercentage = statistic.time / TOTAL_GAME_TIME;

			Vec2f eventLeftBottomX = new Vec2f(
					leftBottom.x + timelineTotalWidth * timePercentage, leftBottom.y + (leftTop.y - leftBottom.y) / 2);

			gl.glBegin(GL2.GL_QUADS);
			gl.glColor4f(0, 0, 0, 0.85f);
			drawBox(gl, eventLeftBottomX.x, eventLeftBottomX.y - (TIME_LINE_EVENT_BAR_WIDTH / 2), eventBarThickness,
					TIME_LINE_EVENT_BAR_WIDTH);
			gl.glEnd();

			// Draw point
			int pSize = (int) (screenWidth * 0.009);

			gl.glEnable(GL2.GL_POINT_SMOOTH);
			gl.glPointSize(pSize);
			gl.glBegin(GL2.GL_POINTS);
			gl.glColor4f(0f, 0f, 0f, 0.95f);
			gl.glVertex3f(eventLeftBottomX.x, eventLeftBottomX.y - 15 * (leftTeam ? 1 : -1), 0);
			gl.glEnd();

			gl.glPointSize(pSize - 1);
			gl.glBegin(GL2.GL_POINTS);
			gl.glColor3fv(team.getColorMaterial().getDiffuse(), 0);
			gl.glVertex3f(eventLeftBottomX.x, eventLeftBottomX.y - 15 * (leftTeam ? 1 : -1), 0);
			gl.glEnd();
			gl.glDisable(GL2.GL_POINT_SMOOTH);
		}
	}

	private void setView(GL2 gl, GLU glu, float hfw, float hfl, float widthFactor, boolean center, int yPos,
			float width, float height)
	{
		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glPushMatrix();
		gl.glLoadIdentity();
		gl.glOrtho(center ? -hfl : 0, hfl, center ? -hfw : 0, hfw, 1, 5);
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glPushMatrix();
		gl.glLoadIdentity();
		glu.gluLookAt(0, 4, 0, 0, 0, 0, 0, 0, 1);

		int displayWidth = (int) (screenWidth * widthFactor);
		int displayHeight = (int) (displayWidth * width / height);
		gl.glViewport(10, yPos, displayWidth, displayHeight);
	}

	private void unsetView(GL2 gl, Viewport vp)
	{
		gl.glMatrixMode(GL2.GL_PROJECTION);
		gl.glPopMatrix();
		gl.glMatrixMode(GL2.GL_MODELVIEW);
		gl.glPopMatrix();
		vp.apply(gl);
	}

	private void drawHeatMap(GL2 gl, GLU glu, Viewport vp)
	{
		WorldModel world = viewer.getWorldModel();
		int displayWidth = (int) (screenWidth * fieldOverlayWidthFactor);
		int heatMapHeight = (int) (displayWidth * fieldWidth / fieldLength);

		if (world.getField().getModel().isLoaded() && visible) {
			gl.glColor4f(1, 1, 1, 0.1f);
			setView(gl, glu, fieldWidth / 2, fieldLength / 2, fieldOverlayWidthFactor, true, heatMapY, fieldWidth,
					fieldLength);
			world.getField().render(gl);

			gl.glEnable(GL2.GL_QUADS);

			// Ball
			drawPositions(gl, statisticsParser.getBallPositions(), statisticsParser.getPositionsCount(), displayWidth,
					heatMapHeight, 0, 255, 0);
			// Left Team
			//		drawPositions(gl, leftTeamPositions, positionsCount * 10, heatMapHeight, 255, 0, 0);
			// Right Team
			//		drawPositions(gl, rightTeamPositions, positionsCount * 10, heatMapHeight, 0, 0, 255);

			gl.glDisable(GL2.GL_QUADS);

			unsetView(gl, vp);
		}
	}

	private void drawPositions(GL2 gl, int[][] positionsArray, int positionsTaken, int heatMapWidth, int heatMapHeight,
			int red, int green, int blue)
	{
		if (positionsArray == null)
			return;
		float[][] positionsRatio = new float[positionsArray.length][positionsArray[0].length];

		float maxRatio = 0;
		float minRatio = 10;

		for (int i = 0; i < positionsRatio.length; i++) {
			for (int j = 0; j < positionsRatio[0].length; j++) {
				int positionCountValue = positionsArray[i][j];
				if (positionCountValue > 0) {
					float positionRatioValue = (float) positionCountValue / positionsTaken;
					if (positionRatioValue > maxRatio)
						maxRatio = positionRatioValue;
					if (positionRatioValue < minRatio)
						minRatio = positionRatioValue;
					positionsRatio[i][j] = positionRatioValue;
				}
			}
		}

		gl.glBegin(GL2.GL_QUADS);
		int[][] ballPositions = statisticsParser.getBallPositions();
		for (int i = 0; i < positionsRatio.length; i++) {
			for (int j = 0; j < positionsRatio[0].length; j++) {
				float positionRatioValue = positionsRatio[i][j];
				if (positionRatioValue > 0) {
					gl.glColor4f(red, green, blue,
							MathUtil.normalize(positionRatioValue, minRatio, maxRatio, MIN_HEAT_OPACITY, 1));
					//					gl.glVertex3f(-j + fieldLength / 2, 1, -i + fieldWidth / 2);
					drawBox3f(gl, -j + fieldLength / 2 - 1, -i + fieldWidth / 2 - 1,
							fieldLength / ballPositions[0].length, fieldWidth / ballPositions.length);
				}
			}
		}
		gl.glEnd();
	}

	private void buildPanels()
	{
		//		 Offside
		List<StatisticsParser.Statistic> leftOffsideStatistics =
				statisticsParser.getStatisticList(StatisticsParser.StatisticType.OFFSIDE.toString())
						.stream()
						.filter(statistic -> statistic.team == 1)
						.collect(Collectors.toList());

		//		 Foul
		List<StatisticsParser.Statistic> leftFoulStatistics =
				statisticsParser.getStatisticList(StatisticsParser.StatisticType.FOUL.name())
						.stream()
						.filter(statistic -> statistic.team == 1)
						.collect(Collectors.toList());

		// Corner
		List<StatisticsParser.Statistic> leftCornerStatistics =
				statisticsParser.getStatisticList(StatisticsParser.StatisticType.CORNER.name())
						.stream()
						.filter(statistic -> statistic.team == 1)
						.collect(Collectors.toList());

		// Free Kick
		List<StatisticsParser.Statistic> leftFreeKickStatistics =
				statisticsParser.getStatisticList(StatisticsParser.StatisticType.FREE_KICK.name())
						.stream()
						.filter(statistic -> statistic.team == 1)
						.collect(Collectors.toList());

		// Goal Kick
		List<StatisticsParser.Statistic> leftGoalKickStatistics =
				statisticsParser.getStatisticList(StatisticsParser.StatisticType.GOAL_KICK.name())
						.stream()
						.filter(statistic -> statistic.team == 1)
						.collect(Collectors.toList());

		// Kick In
		List<StatisticsParser.Statistic> leftKickInStatistics =
				statisticsParser.getStatisticList(StatisticsParser.StatisticType.KICK_IN.name())
						.stream()
						.filter(statistic -> statistic.team == 1)
						.collect(Collectors.toList());

		// Dribles
		List<StatisticsParser.Statistic> leftDribleStatistics =
				statisticsParser.getStatisticList(StatisticsParser.StatisticType.DRIBLE.name())
						.stream()
						.filter(statistic -> statistic.team == 1)
						.collect(Collectors.toList());

		ArrayList<String> titlesOne = new ArrayList<String>(
				Arrays.asList("Fouls", "Offsides", "Corners", "Free kicks", "Goal Kicks", "Kick ins", "Dribles"));
		ArrayList<List<String>> valuesOne = new ArrayList<>();

		valuesOne.add(Arrays.asList(String.valueOf(leftFoulStatistics.size()),
				String.valueOf(statisticsParser.getStatisticList(StatisticsParser.StatisticType.FOUL.name()).size() -
							   leftFoulStatistics.size())));
		valuesOne.add(Arrays.asList(String.valueOf(leftOffsideStatistics.size()),
				String.valueOf(statisticsParser.getStatisticList(StatisticsParser.StatisticType.OFFSIDE.name()).size() -
							   leftOffsideStatistics.size())));
		valuesOne.add(Arrays.asList(String.valueOf(leftCornerStatistics.size()),
				String.valueOf(statisticsParser.getStatisticList(StatisticsParser.StatisticType.CORNER.name()).size() -
							   leftCornerStatistics.size())));
		valuesOne.add(Arrays.asList(String.valueOf(leftFreeKickStatistics.size()),
				String.valueOf(
						statisticsParser.getStatisticList(StatisticsParser.StatisticType.FREE_KICK.name()).size() -
						leftFreeKickStatistics.size())));
		valuesOne.add(Arrays.asList(String.valueOf(leftGoalKickStatistics.size()),
				String.valueOf(
						statisticsParser.getStatisticList(StatisticsParser.StatisticType.GOAL_KICK.name()).size() -
						leftGoalKickStatistics.size())));
		valuesOne.add(Arrays.asList(String.valueOf(leftKickInStatistics.size()),
				String.valueOf(statisticsParser.getStatisticList(StatisticsParser.StatisticType.KICK_IN.name()).size() -
							   leftKickInStatistics.size())));
		valuesOne.add(Arrays.asList(String.valueOf(leftDribleStatistics.size()),
				String.valueOf(statisticsParser.getStatisticList(StatisticsParser.StatisticType.DRIBLE.name()).size() -
							   leftDribleStatistics.size())));

		// Possession
		List<StatisticsParser.PossessionStatistic> possessionValuesOverTime =
				statisticsParser.getPossessionValuesOverTime();
		if (!possessionValuesOverTime.isEmpty()) {
			StatisticsParser.PossessionStatistic lastStatistic =
					possessionValuesOverTime.get(possessionValuesOverTime.size() - 1);
			titlesOne.add("Possession");
			valuesOne.add(Arrays.asList(Math.round(lastStatistic.leftPossession * 100) + "%",
					Math.round((1 - lastStatistic.leftPossession) * 100) + "%"));
		}

		StatisticsPanel statisticsPanelOne = new StatisticsPanel(titlesOne, valuesOne);

		panels = new StatisticsPanel[] {statisticsPanelOne};
	}

	private void drawPanel(GL2 gl, GameState gs, int screenW, int screenH, float n, float opacity)
	{
		int y = screenH - TOP_SCREEN_OFFSET;
		int x = screenW - INFO_WIDTH - SIDE_SCREEN_OFFSET;

		drawTeamNames(gl, gs, screenW, screenH);

		drawStatistics(gl, x, y - (int) (INFO_WIDTH * (n - 1)), INFO_WIDTH, INFO_WIDTH, screenW, screenH,
				1.0f); // CHANGE ME
	}

	private void drawTeamNames(GL2 gl, GameState gs, int screenW, int screenH)
	{
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
		screenWidth = vp.w;
		screenHeight = vp.h;
		render(gl, glu, vp, viewer.getWorldModel().getGameState(), vp.w, vp.h);
	}

	void drawStatistics(GL2 gl, int x, int y, int w, int h, int screenW, int screenH, float opacity)
	{
		float[] panelColor = new float[] {0.3f, 0.3f, 0.3f, 1.0f};

		panelColor[3] = opacity / 3;

		StatisticsPanel panel = panels[CURRENT_SCREEN];
		int numberOfLines = panel.getTitles().size();

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

		for (int i = 0; i < panel.getTitles().size(); i++) {
			tr2.draw(panel.getTitles().get(i), x + w / 2 - (int) tr2.getBounds(panel.getTitles().get(i)).getWidth() / 2,
					LINE_Y);
			tr2.draw(panel.getValues().get(i).get(0), x + Y_PAD, LINE_Y);
			tr2.draw(panel.getValues().get(i).get(1),
					x + w - Y_PAD - (int) tr2.getBounds(panel.getValues().get(i).get(1)).getWidth(), LINE_Y);
			LINE_Y -= (LINE_HEIGHT);
		}

		tr2.endRendering();
	}

	public static boolean shouldDisplayStatistics(long currentTimeMillis, GameState gs)
	{
		if (!gs.isInitialized() || !gs.isPlaying())
			return false;
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

	static void drawBox3f(GL2 gl, float x, float z, float w, float h)
	{
		gl.glVertex3f(x, 1, z);
		gl.glVertex3f(x + w, 1, z);
		gl.glVertex3f(x + w, 1, z + h);
		gl.glVertex3f(x, 1, z + h);
	}

	@Override
	public void gsMeasuresAndRulesChanged(GameState gs)
	{
		fieldWidth = gs.getFieldWidth();
		fieldLength = gs.getFieldLength();
	}

	@Override
	public void gsPlayStateChanged(GameState gs)
	{
	}

	@Override
	public void gsTimeChanged(GameState gs)
	{
		this.time = gs.getTime();
	}

	@Override
	public void goalReceived(StatisticsParser.Statistic statistic)
	{
		System.out.println("Goal received");
	}

	@Override
	public void goalKickReceived(StatisticsParser.Statistic goalKickStatistic)
	{
	}

	@Override
	public void cornerKickReceived(StatisticsParser.Statistic cornerKickStatistic)
	{
	}

	@Override
	public void playOnReceived()
	{
	}
}
