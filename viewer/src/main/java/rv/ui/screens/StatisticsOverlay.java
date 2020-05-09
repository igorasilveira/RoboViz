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
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import jsgl.jogl.view.Viewport;
import jsgl.math.vector.Vec2f;
import jsgl.math.vector.Vec3f;
import rv.Configuration;
import rv.Viewer;
import rv.comm.rcssserver.GameState;
import rv.comm.rcssserver.SExp;
import rv.util.MathUtil;
import rv.util.MatrixUtil;
import rv.util.jogl.VectorUtil;
import rv.world.Team;
import rv.world.WorldModel;
import rv.world.objects.Agent;
import rv.world.objects.Ball;

public class StatisticsOverlay
		extends ScreenBase implements GameState.ServerMessageReceivedListener, GameState.GameStateChangeListener
{
	public enum StatisticType {
		OFFSIDE(0, "offside"),
		FOUL(1, "foul"),
		FREE_KICK(2, "free_kick"),
		CORNER(4, "corner"),
		KICK_IN(5, "kick_in"),
		GOAL_KICK(6, "goal_kick"),
		PASS(7, "pass"),
		DRIBLE(8, "drible"),
		POSSESSION(9, "possession"),
		SHOT(10, "shot"),
		SHOT_TARGET(11, "shot_target"),
		GOAL(12, "goal");

		private int index;
		private String name;

		StatisticType(int index, String name)
		{
			this.index = index;
			this.name = name;
		}

		public String toString()
		{
			return name;
		}
	}

	public static class Statistic
	{
		public float time;
		public int index;
		public StatisticType type;
		public int team;
		public int agentID;
		public long receivedTime;

		public Statistic(float time)
		{
			this.time = time;
			this.receivedTime = System.currentTimeMillis();
		}

		public Statistic(float time, StatisticType type, int team, int agentID)
		{
			this.time = time;
			this.type = type;
			this.team = team;
			this.agentID = agentID;
		}
	}

	public static class PossessionStatistic
	{
		public float time;
		public float leftPossession;

		public PossessionStatistic(float time, float leftPossession)
		{
			this.time = time;
			this.leftPossession = leftPossession;
		}
	}

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
	private int[][] ballPositions;
	private int[][] leftTeamPositions;
	private int[][] rightTeamPositions;

	private float positionStoreGap = 1f;
	private float positionStoreDelta = 0f;
	private int positionsCount = 0;
	private int heatMapY = 300;

	private float possessionStoreGap = 1f;
	private float possessionStoreDelta = 0f;
	private float possessionGraphGap = 5f;
	private float possessionGraphDelta = 0f;

	// Kick detection variables
	// Refactor into a module

	private Agent prevAgent = null;
	private Vec3f prevBallPosition;
	private Vec3f prevBallVelocity;
	private float velocityDeltaTrigger = 0.01f;
	private float degreeDeltaTrigger = 3f;

	private float kickTimeGap = 2f;
	private float kickTimeDelta = 0f;

	private float dribleTimeGap = 2f;
	private float dribleTimeDelta = 0f;
	private int dribleMinTouches = 2;
	private int prevDribleTouches = 0;

	// End of kick detection variables

	// Graph variable
	private final int GRAPH_WIDTH = 400;
	private final int GRAPH_HEIGHT = 250;
	private final int GRAPH_PADDING = 20;

	private float time;
	private float prevTime;

	// Global cycle variables
	private Agent agent = null;

	private StatisticsPanel[] panels;
	private List<Statistic> statistics = new CopyOnWriteArrayList<>();
	private List<PossessionStatistic> possessionValuesOverTime = new CopyOnWriteArrayList<>();

	private final TextRenderer tr1;
	private final TextRenderer tr2;

	private final Viewer viewer;

	public StatisticsOverlay(Viewer viewer)
	{
		this.viewer = viewer;

		time = 0;
		prevTime = 0;

		prevBallPosition = new Vec3f(0, 0, 0);
		prevBallVelocity = new Vec3f(0, 0, 0);

		viewer.getWorldModel().getGameState().addListener((GameState.ServerMessageReceivedListener) this);
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

		if (statistics.isEmpty()) {
			return;
		}

		parseStatistics();
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
			PossessionStatistic statistic = possessionValuesOverTime.get(i);

			gl.glEnable(GL2.GL_POINT_SMOOTH);
			gl.glPointSize(pSize);
			gl.glBegin(GL2.GL_POINTS);

			// Left team
			gl.glColor3fv(viewer.getWorldModel().getLeftTeam().getColorMaterial().getDiffuse(), 0);
			gl.glVertex3f(graphBottomLeftX + (statistic.time / 600) * localGraphWidth,
					graphBottomLeftY + statistic.leftPossession * localGraphHeight, 0);

			gl.glEnd();

			if (i > 0) {
				PossessionStatistic prevStatistic = possessionValuesOverTime.get(i - 1);

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

	private void parseStatistics()
	{
		// Possession
		if (possessionGraphDelta >= possessionGraphGap) {
			List<StatisticsOverlay.Statistic> possessionStatistics =
					statistics.stream()
							.filter(statistic -> statistic.type.equals(StatisticType.POSSESSION))
							.collect(Collectors.toList());

			List<StatisticsOverlay.Statistic> leftPossessionStatistics =
					possessionStatistics.stream().filter(statistic -> statistic.team == 1).collect(Collectors.toList());

			if (possessionStatistics.size() > 0) {
				float totalSize = (float) possessionStatistics.size();
				float leftPossession = leftPossessionStatistics.size() / totalSize;

				possessionValuesOverTime.add(new PossessionStatistic(time, leftPossession));
			}

			possessionGraphDelta = 0;
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
		List<StatisticsOverlay.Statistic> goalsLeft =
				statistics.stream()
						.filter(statistic -> statistic.type.equals(StatisticType.GOAL) && statistic.team == 1)
						.collect(Collectors.toList());

		List<StatisticsOverlay.Statistic> goalsRight =
				statistics.stream()
						.filter(statistic -> statistic.type.equals(StatisticType.GOAL) && statistic.team == 2)
						.collect(Collectors.toList());

		drawEventList(gl, vp, goalsLeft, true);
		drawEventList(gl, vp, goalsRight, false);
	}

	private void drawEventList(GL2 gl, Viewport vp, List<Statistic> eventList, boolean leftTeam)
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

		for (Statistic statistic : eventList) {
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
			//			gl.glColor4f(0, 0, 0, 0.2f);
			//			drawBox(gl, 10, 300, heatMapWidth, heatMapHeight);

			// Ball
			drawPositions(gl, ballPositions, positionsCount, displayWidth, heatMapHeight, 0, 255, 0);
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
		// Offside
		List<StatisticsOverlay.Statistic> offsideStatistics =
				statistics.stream()
						.filter(statistic -> statistic.type.equals(StatisticType.OFFSIDE))
						.collect(Collectors.toList());

		List<StatisticsOverlay.Statistic> leftOffsideStatistics =
				offsideStatistics.stream().filter(statistic -> statistic.team == 1).collect(Collectors.toList());

		// Foul
		List<StatisticsOverlay.Statistic> foulStatistics =
				statistics.stream()
						.filter(statistic -> statistic.type.equals(StatisticType.FOUL))
						.collect(Collectors.toList());

		List<StatisticsOverlay.Statistic> leftFoulStatistics =
				foulStatistics.stream().filter(statistic -> statistic.team == 1).collect(Collectors.toList());

		// Corner
		List<StatisticsOverlay.Statistic> cornerStatistics =
				statistics.stream()
						.filter(statistic -> statistic.type.equals(StatisticType.CORNER))
						.collect(Collectors.toList());

		List<StatisticsOverlay.Statistic> leftCornerStatistics =
				cornerStatistics.stream().filter(statistic -> statistic.team == 1).collect(Collectors.toList());

		// Free Kick
		List<StatisticsOverlay.Statistic> freeKickStatistics =
				statistics.stream()
						.filter(statistic -> statistic.type.equals(StatisticType.FREE_KICK))
						.collect(Collectors.toList());

		List<StatisticsOverlay.Statistic> leftFreeKickStatistics =
				freeKickStatistics.stream().filter(statistic -> statistic.team == 1).collect(Collectors.toList());

		// Pass
		List<StatisticsOverlay.Statistic> passStatistics =
				statistics.stream()
						.filter(statistic -> statistic.type.equals(StatisticType.PASS))
						.collect(Collectors.toList());

		List<StatisticsOverlay.Statistic> leftPassStatistics =
				passStatistics.stream().filter(statistic -> statistic.team == 1).collect(Collectors.toList());

		// Goal Kick
		List<StatisticsOverlay.Statistic> goalKickStatistics =
				statistics.stream()
						.filter(statistic -> statistic.type.equals(StatisticType.GOAL_KICK))
						.collect(Collectors.toList());

		List<StatisticsOverlay.Statistic> leftGoalKickStatistics =
				goalKickStatistics.stream().filter(statistic -> statistic.team == 1).collect(Collectors.toList());

		// Kick In
		List<StatisticsOverlay.Statistic> kickInStatistics =
				statistics.stream()
						.filter(statistic -> statistic.type.equals(StatisticType.KICK_IN))
						.collect(Collectors.toList());

		List<StatisticsOverlay.Statistic> leftKickInStatistics =
				kickInStatistics.stream().filter(statistic -> statistic.team == 1).collect(Collectors.toList());

		// Dribles
		List<StatisticsOverlay.Statistic> dribleStatistics =
				statistics.stream()
						.filter(statistic -> statistic.type.equals(StatisticType.DRIBLE))
						.collect(Collectors.toList());

		List<StatisticsOverlay.Statistic> leftDribleStatistics =
				dribleStatistics.stream().filter(statistic -> statistic.team == 1).collect(Collectors.toList());

		ArrayList<String> titlesOne = new ArrayList<>(
				Arrays.asList("Fouls", "Offsides", "Corners", "Free kicks", "Goal Kicks", "Kick ins", "Dribles"));
		ArrayList<List<String>> valuesOne = new ArrayList<>();

		valuesOne.add(Arrays.asList(String.valueOf(leftFoulStatistics.size()),
				String.valueOf(foulStatistics.size() - leftFoulStatistics.size())));
		valuesOne.add(Arrays.asList(String.valueOf(leftOffsideStatistics.size()),
				String.valueOf(offsideStatistics.size() - leftOffsideStatistics.size())));
		valuesOne.add(Arrays.asList(String.valueOf(leftCornerStatistics.size()),
				String.valueOf(cornerStatistics.size() - leftCornerStatistics.size())));
		valuesOne.add(Arrays.asList(String.valueOf(leftFreeKickStatistics.size()),
				String.valueOf(freeKickStatistics.size() - leftFreeKickStatistics.size())));
		valuesOne.add(Arrays.asList(String.valueOf(leftGoalKickStatistics.size()),
				String.valueOf(goalKickStatistics.size() - leftGoalKickStatistics.size())));
		valuesOne.add(Arrays.asList(String.valueOf(leftKickInStatistics.size()),
				String.valueOf(kickInStatistics.size() - leftKickInStatistics.size())));
		valuesOne.add(Arrays.asList(String.valueOf(leftDribleStatistics.size()),
				String.valueOf(dribleStatistics.size() - leftDribleStatistics.size())));

		// Possession
		if (!possessionValuesOverTime.isEmpty()) {
			PossessionStatistic lastStatistic = possessionValuesOverTime.get(possessionValuesOverTime.size() - 1);
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

	private void parsePlayModeStatistics(GameState gs, SExp exp)
	{
		for (SExp se : exp.getChildren()) {
			String[] atoms = se.getAtoms();

			if (atoms != null) {
				String atomName = atoms[0];

				Statistic statistic = new Statistic(time);

				switch (atomName) {
				case GameState.TIME:
					prevTime = time;
					time = Float.parseFloat(atoms[1]);
					break;
				case GameState.PLAY_MODE:

					if (gs.isInitialized()) {
						int mode = Integer.parseInt(atoms[1]);
						String playMode = gs.getPlayModes()[mode];

						switch (playMode) {
						case GameState.OFFSIDE_LEFT:
							statistic.type = StatisticsOverlay.StatisticType.OFFSIDE;
							statistic.team = 1;
							statistic.index = Integer.parseInt(atoms[1]);
							System.out.println("OFFSIDE 1" + Arrays.toString(atoms));
							break;
						case GameState.OFFSIDE_RIGHT:
							statistic.type = StatisticsOverlay.StatisticType.OFFSIDE;
							statistic.team = 2;
							statistic.index = Integer.parseInt(atoms[1]);
							System.out.println("OFFSIDE 2" + Arrays.toString(atoms));
							break;
						case GameState.KICK_IN_LEFT:
							statistic.type = StatisticsOverlay.StatisticType.KICK_IN;
							statistic.team = 1;
							statistic.index = Integer.parseInt(atoms[1]);
							System.out.println("KICK_IN 1" + Arrays.toString(atoms));
							break;
						case GameState.KICK_IN_RIGHT:
							statistic.type = StatisticsOverlay.StatisticType.KICK_IN;
							statistic.team = 2;
							statistic.index = Integer.parseInt(atoms[1]);
							System.out.println("KICK_IN 2" + Arrays.toString(atoms));
							break;
						case GameState.GOAL_KICK_LEFT:
							statistic.type = StatisticsOverlay.StatisticType.GOAL_KICK;
							statistic.team = 1;
							statistic.index = Integer.parseInt(atoms[1]);
							System.out.println("GOAL_KICK 1" + Arrays.toString(atoms));
							break;
						case GameState.GOAL_KICK_RIGHT:
							statistic.type = StatisticsOverlay.StatisticType.GOAL_KICK;
							statistic.team = 2;
							statistic.index = Integer.parseInt(atoms[1]);
							System.out.println("GOAL_KICK 2" + Arrays.toString(atoms));
							break;
						case GameState.FREE_KICK_LEFT:
						case GameState.DIRECT_FREE_KICK_LEFT:
							statistic.type = StatisticsOverlay.StatisticType.FREE_KICK;
							statistic.team = 1;
							statistic.index = Integer.parseInt(atoms[1]);
							System.out.println("FREE KICK 1 " + Arrays.toString(atoms));
							break;
						case GameState.FREE_KICK_RIGHT:
						case GameState.DIRECT_FREE_KICK_RIGHT:
							statistic.type = StatisticsOverlay.StatisticType.FREE_KICK;
							statistic.team = 2;
							statistic.index = Integer.parseInt(atoms[1]);
							System.out.println("FREE KICK 2 " + Arrays.toString(atoms));
							break;
						case GameState.CORNER_KICK_LEFT:
							statistic.type = StatisticsOverlay.StatisticType.CORNER;
							statistic.team = 1;
							statistic.index = Integer.parseInt(atoms[1]);
							System.out.println("CORNER 1 " + Arrays.toString(atoms));
							break;
						case GameState.CORNER_KICK_RIGHT:
							statistic.type = StatisticsOverlay.StatisticType.CORNER;
							statistic.team = 2;
							statistic.index = Integer.parseInt(atoms[1]);
							System.out.println("CORNER 2 " + Arrays.toString(atoms));
							break;
						case GameState.GOAL_LEFT:
							statistic.type = StatisticsOverlay.StatisticType.GOAL;
							statistic.team = 1;
							statistic.index = Integer.parseInt(atoms[1]);
							System.out.println("GOAL 1 " + Arrays.toString(atoms));
							break;
						case GameState.GOAL_RIGHT:
							statistic.type = StatisticsOverlay.StatisticType.GOAL;
							statistic.team = 2;
							statistic.index = Integer.parseInt(atoms[1]);
							System.out.println("GOAL 2 " + Arrays.toString(atoms));
							break;
						}
					}
					break;
				case GameState.FOUL:
					statistic.index = Integer.parseInt(atoms[1]);
					statistic.type = StatisticsOverlay.StatisticType.FOUL;
					statistic.team = Integer.parseInt(atoms[3]);
					statistic.agentID = Integer.parseInt(atoms[4]);
					break;
				}

				if (statistic.type != null)
					addStatistic(statistic);
			}
		}
	}

	private void calculateStateStatistics(GameState gs)
	{
		if (!gs.isInitialized() || !gs.isPlaying())
			return;

		WorldModel world = viewer.getWorldModel();
		Ball ball = world.getBall();
		Team leftTeam = world.getLeftTeam();
		Team rightTeam = world.getRightTeam();

		if (leftTeam.getAgents().isEmpty() || rightTeam.getAgents().isEmpty())
			return;

		try {
			detectPossession(gs, world);
			detectKick(gs, world);
			storePositions(gs, world);

			Vec3f currentBallVelocityExtracted = new Vec3f(ball.getPosition().x, ball.getPosition().y, 0);

			Vec3f currentBallVelocity =
					VectorUtil.calculateVelocity(currentBallVelocityExtracted.minus(prevBallPosition), time - prevTime);
			prevBallPosition = new Vec3f(ball.getPosition().x, ball.getPosition().y, 0);
			prevAgent = agent;

			float cycleTime = time - prevTime;

			dribleTimeDelta += cycleTime;
			kickTimeDelta += cycleTime;
			possessionStoreDelta += cycleTime;
			possessionGraphDelta += cycleTime;
			positionStoreDelta += cycleTime;
		} catch (NullPointerException e) {
			System.err.println("Initializing statistics");
		}
	}

	private void storePositions(GameState gs, WorldModel world)
	{
		if (positionStoreDelta >= positionStoreGap) {
			Ball ball = world.getBall();
			Team leftTeam = world.getLeftTeam();
			Team rightTeam = world.getRightTeam();

			// Ball
			int BallXPosition = MatrixUtil.normalizeIndex(
					Math.round(fieldLength / 2f - ball.getPosition().x) - 1, 0, (int) fieldLength);
			int BallYPosition = MatrixUtil.normalizeIndex(
					Math.round(fieldWidth / 2f - ball.getPosition().z) - 1, 0, (int) fieldWidth);
			ballPositions[BallYPosition][BallXPosition] += 1;

			// Left Team
			storeTeamPositions(leftTeam, fieldLength, fieldWidth, leftTeamPositions);
			// Right Team
			storeTeamPositions(rightTeam, fieldLength, fieldWidth, rightTeamPositions);

			positionStoreDelta = 0;
			positionsCount++;
		}
	}

	private void storeTeamPositions(Team team, float fieldLength, float fieldWidth, int[][] teamPositions)
	{
		for (Agent player : team.getAgents()) {
			if (player.getID() != 1) {
				int XPosition = MatrixUtil.normalizeIndex(
						Math.round(fieldLength / 2f - player.getPosition().x) - 1, 0, (int) (fieldLength - 1));
				int YPosition = MatrixUtil.normalizeIndex(
						Math.round(fieldWidth / 2f - player.getPosition().z) - 1, 0, (int) (fieldWidth - 1));
				teamPositions[YPosition][XPosition] += 1;
			}
		}
	}

	private void detectKick(GameState gs, WorldModel world)
	{
		Ball ball = world.getBall();
		Team leftTeam = world.getLeftTeam();
		Team rightTeam = world.getRightTeam();

		Vec3f currentBallVelocityExtracted = new Vec3f(ball.getPosition().x, ball.getPosition().y, 0);

		Vec3f currentBallVelocity =
				VectorUtil.calculateVelocity(currentBallVelocityExtracted.minus(prevBallPosition), time - prevTime);

		Vec3f velocityDelta = currentBallVelocity.minus(prevBallVelocity);

		//
		if (((Math.abs(velocityDelta.length()) > velocityDeltaTrigger) ||
					Math.abs(VectorUtil.calculateAngle(prevBallVelocity, currentBallVelocity)) > degreeDeltaTrigger) &&
				prevTime < time) {
			// Drible detection
			detectDrible(world);
			detectShot(gs, world, velocityDelta);
		}

		//            if (prevBallPosition.minus(ball.getPosition()).length() < 0.01f) {
		//                System.out.println("Ball same position");
		//            }
	}

	private void detectShot(GameState gs, WorldModel world, Vec3f currentBallVelocity)
	{
		System.out.println("Field length " + fieldLength);
		System.out.println("Field width " + fieldWidth);
		boolean leftPossession = agent.getTeam().getID() == world.getLeftTeam().getID();
		if (Math.abs(agent.getPosition().x) >= fieldLength / 4 &&
				(leftPossession ? 1 : -1) * agent.getPosition().x > 0) {
			if (currentBallVelocity.length() > 10) {
				float goalLineX = (leftPossession ? -1 : 1) * fieldLength / 2;
				float yCoordinate =
						agent.getPosition().y +
						(currentBallVelocity.y * ((goalLineX - agent.getPosition().x) * currentBallVelocity.x));
				float goalWidthEnd = gs.getGoalWidth() / 2;
				StatisticType typeShot =
						Math.abs(yCoordinate) < goalWidthEnd ? StatisticType.SHOT_TARGET : StatisticType.SHOT;

				this.statistics.add(new Statistic(time, typeShot, agent.getTeam().getID(), agent.getID()));
			}
		}
	}

	private void detectDrible(WorldModel world)
	{
		if (prevTime < time && dribleTimeDelta >= dribleTimeGap) {
			//			System.out.println("Agent team: " + agent.getTeam().getID());
			//			System.out.println("Prev Agent team: " + prevAgent.getTeam().getID());
			//			System.out.println("Agent ID: " + agent.getID());
			//			System.out.println("Prev Agent ID: " + prevAgent.getID());
			//
			//			System.out.println("Dribles " + prevDribleTouches);
			if (agent.getTeam().getID() == prevAgent.getTeam().getID() && agent.getID() == prevAgent.getID()) {
				prevDribleTouches++;
			} else {
				if (prevDribleTouches >= dribleMinTouches) {
					this.addStatistic(new Statistic(time, StatisticType.DRIBLE,
							agent.getTeam().getID() == world.getLeftTeam().getID() ? 1 : 2, agent.getID()));
				}
				prevDribleTouches = 0;
			}
			dribleTimeDelta = 0;
		}
	}

	private void detectPossession(GameState gs, WorldModel world)
	{
		if (possessionStoreDelta >= possessionStoreGap) {
			Ball ball = world.getBall();
			Team leftTeam = world.getLeftTeam();
			Team rightTeam = world.getRightTeam();

			float minimumDistanceToBall = 1000;
			agent = null;

			for (Agent player : leftTeam.getAgents()) {
				float distanceToBall = Math.abs(player.getPosition().length() - ball.getPosition().length());
				if (distanceToBall < minimumDistanceToBall) {
					minimumDistanceToBall = distanceToBall;
					agent = player;
				}
			}

			for (Agent player : rightTeam.getAgents()) {
				float distanceToBall = Math.abs(player.getPosition().length() - ball.getPosition().length());
				if (distanceToBall < minimumDistanceToBall) {
					agent = player;
				}
			}

			addStatistic(new Statistic(time, StatisticType.POSSESSION,
					agent.getTeam().getID() == world.getLeftTeam().getID() ? 1 : 2, agent.getID()));
			possessionStoreDelta = 0;
		}
	}

	@Override
	public void gsServerMessageReceived(GameState gs, SExp exp)
	{
		synchronized (this)
		{
			if (!isInitialized && gs.isInitialized())
				initializeMaps();

			parsePlayModeStatistics(gs, exp);
			calculateStateStatistics(gs);
		}
	}

	private void initializeMaps()
	{
		ballPositions = new int[(int) fieldWidth][(int) fieldLength];
		leftTeamPositions = new int[(int) fieldWidth][(int) fieldLength];
		rightTeamPositions = new int[(int) fieldWidth][(int) fieldLength];

		isInitialized = true;
	}

	@Override
	public void gsServerMessageProcessed(GameState gs)
	{
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
	}

	public void addStatistic(Statistic statistic)
	{
		boolean alreadyHaveStatistic = false;
		for (Statistic s : statistics) {
			if (s.type == statistic.type && s.team == statistic.team && s.agentID == statistic.agentID &&
					Math.abs(statistic.time - s.time) < 1.0) {
				alreadyHaveStatistic = true;
				break;
			}
		}
		if (!alreadyHaveStatistic) {
			statistics.add(statistic);
		}
	}
}
