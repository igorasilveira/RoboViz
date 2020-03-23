package rv.util.jogl;

import jsgl.math.vector.Vec3f;

public class VectorUtil
{
	public static Vec3f calculateVelocity(Vec3f deltaPosition, float deltaTime)
	{
		Vec3f velocity = deltaPosition;
		velocity.div(deltaTime);
		return velocity;
	}

	public static double calculateAngle(Vec3f vec1, Vec3f vec2)
	{
		float cosine = (vec1.dot(vec2)) / (vec1.length() * vec2.length());
		return Math.toDegrees(Math.acos(cosine));
	}
}
