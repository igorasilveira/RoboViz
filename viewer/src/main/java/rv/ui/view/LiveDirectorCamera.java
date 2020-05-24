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
import rv.ui.CameraSetting;
import rv.world.ISelectable;
import rv.world.WorldModel;
import rv.world.objects.Agent;
import rv.world.objects.Ball;

public class LiveDirectorCamera
{
	private boolean enabled = false;
	private boolean initializedCameras = false;
	private boolean initializedTarget = false;
	private final FPCamera camera;
	private CameraSetting[] cameras;
	private GameState gs;
	private WorldModel world;
	private Viewer viewer;

	private float PITCH_SIDE_DISTANCE_MULTIPLIER = 0.8f;

	private ISelectable target;
	private double playbackSpeed = 1;
	private Vec3f lastScreenPos;

	public boolean isEnabled()
	{
		return enabled;
	}

	public void setEnabled(boolean enabled)
	{
		this.enabled = enabled;
		if (enabled) {
			int cameraIndex = gs.isInitialized() ? 1 : 0;
			camera.setPosition(cameras[cameraIndex].getPosition().clone());
			camera.setRotation(cameras[cameraIndex].getRotation().clone());
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
		lastScreenPos = null;
	}

	private void initTarget()
	{
		if (gs.isInitialized()) {
			target = viewer.getWorldModel().getBall();
			initializedTarget = true;
		}
	}

	private void initCameras()
	{
		float fl = gs.getFieldLength();
		float fw = gs.getFieldWidth();

		double fov = Math.toRadians(100);
		float aerialHeight = (float) (0.3 * fw / Math.tan(fov * 0.5) * 1.8);

		cameras = new CameraSetting[] {
				new CameraSetting(new Vec3f(0, aerialHeight, PITCH_SIDE_DISTANCE_MULTIPLIER * fw), new Vec2f(-25, 0)),
				new CameraSetting(
						new Vec3f(0, aerialHeight, -PITCH_SIDE_DISTANCE_MULTIPLIER * fw), new Vec2f(-25, 180))};
		initializedCameras = true;
	}

	public void update(Viewport screen)
	{
		if (!enabled)
			return;

		if (!initializedCameras)
			initCameras();

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
			camera.setPosition(Vec3f.lerp(cameras[0].getPosition().clone(), camera.getPosition(), 0.01f));
			camera.setRotation(Vec2f.lerp(cameras[0].getRotation().clone(), camera.getRotAngle(), 0.01f));
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
		float maxDistance = gs.getFieldWidth() / 2;
		Vec3f rayVector = cameras[1].getPosition().minus(targetPos);
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
}
