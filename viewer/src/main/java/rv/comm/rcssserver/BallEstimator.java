package rv.comm.rcssserver;

import static java.lang.Math.exp;
import static java.lang.Math.log;

import java.util.LinkedList;
import jsgl.math.vector.Vec3f;
import rv.Viewer;
import rv.world.WorldModel;

public class BallEstimator
{
	int indRefTime, indRefTime3, indRefTime4, indRefTime5;
	Vec3f refPos, refPos3, refPos4, refPos5, refPosAway;
	float refTime, refTime3, refTime4, refTime5, refTimeAway;

	float ballExpK;

	LinkedList<Vec3f> ballPosQueue = new LinkedList<>();
	LinkedList<Float> ballPosTimeQueue = new LinkedList<>();

	private Viewer viewer;
	private WorldModel world;
	private GameState gs;

	public BallEstimator(Viewer viewer)
	{
		this.viewer = viewer;
		this.world = viewer.getWorldModel();
		this.gs = world.getGameState();
		ballExpK = -1.05719f;
		indRefTime = indRefTime3 = indRefTime5 = 0;
		refTime = refTime3 = refTime4 = refTime5 = refTimeAway = 0.0f;
	}

	public void update()
	{
		if (ballPosQueue.size() == 6) {
			ballPosQueue.removeLast();
			ballPosTimeQueue.removeLast();
		}

		Vec3f ballRealPos = world.getBall().getPosition();
		float time = gs.getTime();

		ballPosQueue.addFirst(ballRealPos);
		ballPosTimeQueue.addFirst(time);
		refPos = ballRealPos;
		refTime = time;
		indRefTime = indRefTime3 = indRefTime4 = indRefTime5 = 0;
		boolean found3, found4, found5;
		found3 = found4 = found5 = false;

		for (int i = 1; i < ballPosQueue.size(); i++) {
			if (!found3 && refTime - ballPosTimeQueue.get(i) > 0.06 * 2.5) { // three vision cycles
				refPos3 = ballPosQueue.get(i);
				refTime3 = ballPosTimeQueue.get(i);
				indRefTime3 = i;
				found3 = true;
			}
			if (!found4 && refTime - ballPosTimeQueue.get(i) > 0.06 * 3.5) { // four vision cycles
				refPos4 = ballPosQueue.get(i);
				refTime4 = ballPosTimeQueue.get(i);
				indRefTime4 = i;
				found4 = true;
			}
			if (!found5 && refTime - ballPosTimeQueue.get(i) > 0.06 * 4.5) { // five vision cycles
				refPos5 = ballPosQueue.get(i);
				refTime5 = ballPosTimeQueue.get(i);
				indRefTime5 = i;
				found5 = true;
			}
		}
		if (viewer.getStatisticsParser().getMinimumDistanceToBall() < 0.3) {
			refPosAway = refPos;
			refTimeAway = refTime;
		}

		System.out.println("Best update size " + ballPosQueue.size() + " refPos " + refPos + " refTime " + refTime +
						   " refPos3 " + refPos3 + " refTime3 " + refTime3 + " refPosAway " + refPosAway +
						   " refTime3 " + refTimeAway + "\n");
	}

	Vec3f estimatedVel(float relTime)
	{
		if (indRefTime3 == 0)
			return new Vec3f(0.0f, 0.0f, 0.0f);

		float factorVel = (float) (-ballExpK / (exp(-ballExpK * (refTime - refTime3)) - 1) * (refTime - refTime3));

		Vec3f curVel = refPos.minus(refPos3).over(refTime - refTime3).times(factorVel);

		curVel.z = 0.0f;

		return curVel.times((float) exp(ballExpK * relTime));
	}

	// more precise (if ball is free), but with slower response
	// used at least 4 vision cycles

	Vec3f estimatedVel4(float relTime)
	{
		if (indRefTime4 == 0)
			return new Vec3f(0.0f, 0.0f, 0.0f);

		float factorVel = (float) (-ballExpK / (exp(-ballExpK * (refTime - refTime4)) - 1) * (refTime - refTime4));

		Vec3f curVel = refPos.minus(refPos4).over(refTime - refTime4).times(factorVel);

		curVel.z = 0.0f;

		return curVel.times((float) exp(ballExpK * relTime));
	}

	// more precise (if ball is free), but with slower response
	// used at least 5 vision cycles

	Vec3f estimatedVel5(float relTime)
	{
		if (indRefTime5 == 0)
			return new Vec3f(0.0f, 0.0f, 0.0f);

		float factorVel = (float) (-ballExpK / (exp(-ballExpK * (refTime - refTime5)) - 1) * (refTime - refTime5));

		Vec3f curVel = refPos.minus(refPos5).over(refTime - refTime5).times(factorVel);

		curVel.z = 0.0f;

		return curVel.times((float) exp(ballExpK * relTime));
	}

	Vec3f estimatedVelAway(float relTime)
	{
		if (refTimeAway == 0.0 || refTimeAway == refTime)
			return new Vec3f(0.0f, 0.0f, 0.0f);

		float factorVel =
				(float) (-ballExpK / (exp(-ballExpK * (refTime - refTimeAway)) - 1) * (refTime - refTimeAway));

		Vec3f curVel = refPos.minus(refPosAway).over(refTime - refTimeAway).times(factorVel);

		curVel.z = 0.0f;

		return curVel.times((float) exp(ballExpK * relTime));
	}

	Vec3f estimatedPos(float relTime)
	{
		Vec3f refVel = estimatedVel(0.0f);
		Vec3f estPos =
				refPos.plus(refVel.over(ballExpK).times((float) exp(ballExpK * relTime))).minus(refVel.over(ballExpK));

		estPos.z = refPos.z;

		return estPos;
	}

	// more precise (if ball is free), but with slower response

	Vec3f estimatedPos4(float relTime)
	{
		Vec3f refVel = estimatedVel4(0.0f);
		Vec3f estPos =
				refPos.plus(refVel.over(ballExpK).times((float) exp(ballExpK * relTime))).minus(refVel.over(ballExpK));

		estPos.z = refPos.z;

		return estPos;
	}

	// more precise (if ball is free), but with slower response

	Vec3f estimatedPos5(float relTime)
	{
		Vec3f refVel = estimatedVel5(0.0f);
		Vec3f estPos =
				refPos.plus(refVel.over(ballExpK).times((float) exp(ballExpK * relTime))).minus(refVel.over(ballExpK));

		estPos.z = refPos.z;

		return estPos;
	}

	float estimatedTime(float travelDistance)
	{
		float refVel = estimatedVel(0.0f).length();

		float aux = travelDistance / (refVel / ballExpK) + 1;

		if (aux < 0.0)
			return (float) 1e5;

		return (float) (log(aux) / ballExpK);
	}

	float estimatedTime4(float travelDistance)
	{
		float refVel = estimatedVel4(0.0f).length();

		float aux = travelDistance / (refVel / ballExpK) + 1;

		if (aux < 0.0)
			return (float) 1e5;

		return (float) (log(aux) / ballExpK);
	}

	float estimatedTime5(float travelDistance)
	{
		float refVel = estimatedVel5(0.0f).length();

		float aux = travelDistance / (refVel / ballExpK) + 1;

		if (aux < 0.0)
			return (float) 1e5;

		return (float) (log(aux) / ballExpK);
	}

	Vec3f estimatedFinalPos()
	{
		Vec3f refVel = estimatedVel(0.0f);
		Vec3f estPos = refPos.minus(refVel.over(ballExpK));

		estPos.z = refPos.z;

		return estPos;
	}

	Vec3f estimatedFinalPos4()
	{
		Vec3f refVel = estimatedVel4(0.0f);
		Vec3f estPos = refPos.minus(refVel.over(ballExpK));

		estPos.z = refPos.z;

		return estPos;
	}

	Vec3f estimatedFinalPos5()
	{
		Vec3f refVel = estimatedVel5(0.0f);
		Vec3f estPos = refPos.minus(refVel.over(ballExpK));

		estPos.z = refPos.z;

		return estPos;
	}

	Vec3f estimatedFinalPosAway()
	{
		Vec3f refVel = estimatedVelAway(0.0f);
		// Vec3f estPos = refPos - refVel/ballExpK;
		Vec3f estPos = world.getBall().getPosition().minus(refVel.over(ballExpK));
		;

		estPos.z = refPos.z;

		return estPos;
	}
}
