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

package rv.ui.view;

import jsgl.jogl.view.FPCamera;
import jsgl.jogl.view.Viewport;
import jsgl.math.vector.Vec2f;
import jsgl.math.vector.Vec3f;
import rv.Viewer;
import rv.comm.rcssserver.GameState;
import rv.comm.rcssserver.StatisticsParser;
import rv.ui.CameraSetting;
import rv.world.ISelectable;
import rv.world.WorldModel;
import rv.world.objects.Agent;

public class LiveDirectorCamera implements StatisticsParser.StatisticsParserListener, GameState.GameStateChangeListener
{
	private boolean enabled = false;
	private boolean initializedTarget = false;
	private final FPCamera camera;
	private CameraSetting[] cameras;
	private GameState gs;
	private WorldModel world;
	private Viewer viewer;
	private CameraType currentCameraType = CameraType.BEFORE_LIVE;
	private boolean hasChangedCameraType = false;
	private boolean isLeftTeam = false;

	private float PITCH_SIDE_DISTANCE_MULTIPLIER = 0.8f;

	private ISelectable target;
	private double playbackSpeed = 1;
	private Vec3f lastScreenPos;
	private static final float DRIBBLE_LISTENER_GAP = 10;
	private float lastDribbleStopTime = 0;
	private float time = 0;

	public boolean isEnabled()
	{
		return enabled;
	}

	public enum CameraType {
		BEFORE_LIVE(0, "before_live"),
		LIVE(1, "live"),
		GOAL_KICK(2, "goal_kick"),
		CORNER(3, "corner");

		private int index;
		private String name;

		CameraType(int index, String name)
		{
			this.index = index;
			this.name = name;
		}

		public String toString()
		{
			return name;
		}

		public boolean equals(CameraType other)
		{
			return index == other.index && name == other.name;
		}
	}

	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
		if (enabled) {
			changeCameraType(gs.isInitialized() ? CameraType.LIVE : CameraType.BEFORE_LIVE);
			camera.setPosition(cameras[currentCameraType.index].getPosition().clone());
			camera.setRotation(cameras[currentCameraType.index].getRotation().clone());
		}
	}

	public void setPlaybackSpeed(double playbackSpeed)
	{
		this.playbackSpeed = playbackSpeed;
	}

	public LiveDirectorCamera(FPCamera camera, Viewer viewer)
	{
		this.camera = camera;
		this.viewer = viewer;
		this.world = viewer.getWorldModel();
		this.gs = world.getGameState();
		viewer.getStatisticsParser().addListener(this);
		lastScreenPos = null;
	}

	private void initTarget()
	{
		if (gs.isInitialized()) {
			if (currentCameraType.name == "live")
				target = viewer.getWorldModel().getBall();

			initializedTarget = true;
		}
	}

	private void updateCameras()
	{
		float fl = gs.getFieldLength();
		float fw = gs.getFieldWidth();

		double fov = Math.toRadians(100);
		float aerialHeight = (float) (0.3 * fw / Math.tan(fov * 0.5) * 1.8);
		int teamMultiplier = isLeftTeam ? 1 : -1;

		cameras = new CameraSetting[] {// BEFORE_LIVE camera
				new CameraSetting(new Vec3f(0, aerialHeight, PITCH_SIDE_DISTANCE_MULTIPLIER * fw), new Vec2f(-35, 0)),
				// LIVE camera
				new CameraSetting(
						new Vec3f(0, aerialHeight, -PITCH_SIDE_DISTANCE_MULTIPLIER * fw), new Vec2f(-35, 180)),
				// GOAL_KICK camera
				new CameraSetting(new Vec3f(((fl / 2) + 2.4f) * teamMultiplier, -2.5f, -4.4f),
						new Vec2f(30, 90 * teamMultiplier + 65 * teamMultiplier)),
				// CORNER camera
				new CameraSetting(new Vec3f(((fl / 2) - 2) * teamMultiplier * -1, aerialHeight / 2, -fw / 2 - 3),
						new Vec2f(-30, 180 - 15 * teamMultiplier * -1))};
	}

	public void update(Viewport screen)
	{
		if (!enabled)
			return;

		updateCameras();

		if (hasChangedCameraType) {
			updateCameraByType();
			hasChangedCameraType = false;
		}

		if (currentCameraType.equals(CameraType.LIVE)) {
			handleLiveFeed(screen);
			return;
		}

		if (currentCameraType.equals(CameraType.GOAL_KICK)) {
			handleGoalKickFeed();
			return;
		}
	}

	private void handleGoalKickFeed()
	{
		Vec3f cameraTargetPosition = cameras[CameraType.GOAL_KICK.index].getPosition().clone();
		//		System.out.println("camera default position:" + cameraTargetPosition);
		cameraTargetPosition.add(Vec3f.unitY().times(3));
		Vec2f cameraTargetRotation = cameras[CameraType.GOAL_KICK.index].getRotation().clone();
		cameraTargetRotation.add(Vec2f.unitX().times(-20));
		cameraTargetRotation.add(Vec2f.unitY().times(-30 * (isLeftTeam ? 1 : -1)));
		//		System.out.println("camera position: " + camera.getPosition().toString());
		//		System.out.println("new camera position: " + cameraTargetPosition.toString());
		//		System.out.println("lerp: " + Vec3f.lerp(cameraTargetPosition, camera.getPosition(), 0.01f).toString());

		camera.setPosition(Vec3f.lerp(cameraTargetPosition, camera.getPosition(), 0.01f));
		camera.setRotation(Vec2f.lerp(cameraTargetRotation, camera.getRotAngle(), 0.01f));
		//		System.exit(1);
	}

	private void updateCameraByType()
	{
		CameraSetting goalKickSetting = cameras[currentCameraType.index];
		camera.setPosition(goalKickSetting.getPosition().clone());
		camera.setRotation(goalKickSetting.getRotation().clone());
	}

	private void handleLiveFeed(Viewport screen)
	{
		if (!initializedTarget)
			initTarget();

		if (target != null) {
			float scale = (float) (1 - (0.02f * playbackSpeed));
			if (target instanceof Agent) {
				scale = 0.95f;
			} else {
				scale = scaleWithBallSpeed(screen, scale);
			}

			Vec3f cameraTargetPosition = offsetTargetPosition(target.getPosition());
			Vec2f cameraTargetRotation = offsetTargetRotation(target.getPosition());

			camera.setPosition(Vec3f.lerp(cameraTargetPosition, camera.getPosition(), scale));
			camera.setRotation(Vec2f.lerp(cameraTargetRotation, camera.getRotAngle(), scale));
		} else {
			camera.setPosition(
					Vec3f.lerp(cameras[currentCameraType.index].getPosition().clone(), camera.getPosition(), 0.01f));
			camera.setRotation(
					Vec2f.lerp(cameras[currentCameraType.index].getRotation().clone(), camera.getRotAngle(), 0.01f));
		}
	}

	private float scaleWithBallSpeed(Viewport screen, float scale)
	{
		// Get position of target relative to screen
		Vec3f screenPos = camera.project(target.getPosition(), screen);

		if (lastScreenPos == null) {
			lastScreenPos = screenPos;
		}

		// Maximum factor that velocity can increase scale by
		float VEL_SCALE_FACTOR_MAX = 3.0f;

		// Amount that screen velocity is multiplied by when determining scale
		float VEL_SCALE_FACTOR = 0.003f;

		double screenVel = Math.sqrt(Math.pow((double) (lastScreenPos.x - screenPos.x), 2.0) +
									 Math.pow((double) (lastScreenPos.y - screenPos.y), 2.0));
		scale = (float) Math.max(
				Math.min(1 - screenVel * VEL_SCALE_FACTOR, scale), 1 - (0.02f * playbackSpeed * VEL_SCALE_FACTOR_MAX));
		lastScreenPos = screenPos;

		return scale;
	}

	/**
	 * Tries to keep the ball in the middle of the screen (unless we're near a field edge, then it
	 * shifts the position a bit to fill as much of the screen with the field as possible)
	 */
	private Vec2f offsetTargetRotation(Vec3f targetPos)
	{
		Vec2f targetRotation = new Vec2f(-30, 180);

		float distance = camera.getPosition().minus(targetPos).length();
		targetRotation.x =
				(float) (-30 - Math.toDegrees(Math.acos((targetPos.y - camera.getPosition().y) / distance)) * 0.01);
		targetRotation.y = 180 + (float) Math.toDegrees(Math.asin((targetPos.x - camera.getPosition().x) / distance));

		return targetRotation;
	}

	private Vec3f offsetTargetPosition(Vec3f targetPos)
	{
		Vec3f targetPosition = targetPos;
		float maxDistance = gs.getFieldWidth() / (target instanceof Agent ? 3 : 2);
		Vec3f rayVector = cameras[CameraType.LIVE.index].getPosition().minus(targetPos);
		rayVector.div(rayVector.length());
		rayVector.mul(maxDistance);

		targetPos.add(rayVector);
		return targetPosition;
	}

	private float fuzzyValue(float value, float lower, float upper)
	{
		if (value <= lower)
			return 1;
		if (value >= upper)
			return 0;
		return weight(1 - ((value - lower) / (upper - lower)));
	}

	/** maps t values from 0...1 to -1...1 using a quadratic function */
	private float weight(float t)
	{
		float result = (float) -(Math.sqrt(1 - Math.pow(2 * t - 1, 2)) - 1);
		if (t < 0.5)
			result *= -1;
		return result;
	}

	@Override
	public void goalReceived(StatisticsParser.Statistic goalStatistic)
	{
	}

	@Override
	public void goalKickReceived(StatisticsParser.Statistic goalKickStatistic)
	{
		isLeftTeam = goalKickStatistic.team == 1;
		//		System.out.println("Camera received goal kick for team " + (isLeftTeam ? "LEFT" : "RIGHT"));
		changeCameraType(CameraType.GOAL_KICK);
	}

	@Override
	public void cornerKickReceived(StatisticsParser.Statistic cornerKickStatistic)
	{
	}

	@Override
	public void dribbleStartReceived(Agent dribbler)
	{
		//		System.out.println("Camera received dribble start for team " + dribbler.getTeam().getName() + " agent "
		//+ 						   dribbler.getID());
		float dt = time - lastDribbleStopTime;
		if (dt > DRIBBLE_LISTENER_GAP)
			target = dribbler;
	}

	@Override
	public void dribbleStopReceived()
	{
		//		System.out.println("Camera received dribble stop");
		lastDribbleStopTime = time;
		target = world.getBall();
	}

	@Override
	public void kickInReceived(StatisticsParser.Statistic kickInStatistic)
	{
	}

	@Override
	public void offsideReceived(StatisticsParser.Statistic offSideStatistic)
	{
	}

	@Override
	public void foulReceived(StatisticsParser.Statistic foulStatistic)
	{
	}

	@Override
	public void freeKickReceived(StatisticsParser.Statistic freeKickStatistic)
	{
	}

	@Override
	public void playOnReceived()
	{
		//		System.out.println("Camera received play on");
		changeCameraType(CameraType.LIVE);
	}

	@Override
	public void gsMeasuresAndRulesChanged(GameState gs)
	{
	}

	@Override
	public void gsPlayStateChanged(GameState gs)
	{
	}

	@Override
	public void gsTimeChanged(GameState gs)
	{
		time = gs.getTime();
	}

	private void changeCameraType(CameraType type)
	{
		if (currentCameraType.equals(type)) {
			return;
		}
		currentCameraType = type;
		hasChangedCameraType = true;
	}
}
