package rv.util;

public class MathUtil
{
	public static float normalize(float value, float observedMin, float observedMax, float desiredMin, float desiredMax)
	{
		if (observedMin >= observedMax) {
			observedMin = 0.0f;
		}
		float a = (desiredMax - desiredMin) / (observedMax - observedMin);
		float b = desiredMax - a * observedMax;
		return a * value + b;
	}
}
