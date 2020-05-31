package rv.comm.rcssserver;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import jsgl.math.vector.Vec3f;
import rv.Viewer;
import rv.util.MatrixUtil;
import rv.util.jogl.VectorUtil;
import rv.world.Team;
import rv.world.WorldModel;
import rv.world.objects.Agent;
import rv.world.objects.Ball;

public class StatisticsParser implements GameState.ServerMessageReceivedListener, GameState.GameStateChangeListener
{
	public interface StatisticsParserListener {
		/** Called when a goal is received */
		void goalReceived(Statistic goalStatistic);
		void goalKickReceived(Statistic goalKickStatistic);
		void cornerKickReceived(Statistic cornerKickStatistic);
		void playOnReceived();
	}

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

	private boolean isInitialized = false;

	private float minimumDistanceToBall = 1000;

	// TIMELINE

	private static final float TOTAL_GAME_TIME = 600;

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

	private float time;
	private float prevTime;

	// Global cycle variables
	private Agent agent = null;

	private Map<String, List<Statistic>> statistics;
	private List<PossessionStatistic> possessionValuesOverTime = new CopyOnWriteArrayList<>();

	private final Viewer viewer;
	private final WorldModel world;

	private final List<StatisticsParserListener> spListeners = new CopyOnWriteArrayList<>();

	public StatisticsParser(WorldModel world, Viewer viewer)
	{
		this.viewer = viewer;
		this.world = world;

		time = 0;
		prevTime = 0;

		prevBallPosition = new Vec3f(0, 0, 0);
		prevBallVelocity = new Vec3f(0, 0, 0);

		statistics = new HashMap<>();
		for (StatisticType type : StatisticType.values()) {
			statistics.put(type.name(), new CopyOnWriteArrayList<Statistic>());
		}

		world.getGameState().addListener((GameState.ServerMessageReceivedListener) this);
		world.getGameState().addListener((GameState.GameStateChangeListener) this);
	}

	public void addListener(StatisticsParserListener l)
	{
		spListeners.add(l);
	}

	public void removeListener(StatisticsParserListener l)
	{
		spListeners.remove(l);
	}

	public int[][] getBallPositions()
	{
		return ballPositions;
	}

	public int getPositionsCount()
	{
		return positionsCount;
	}

	public List<PossessionStatistic> getPossessionValuesOverTime()
	{
		return possessionValuesOverTime;
	}

	private void parseStatistics()
	{
		// Possession
		if (possessionGraphDelta >= possessionGraphGap) {
			List<StatisticsParser.Statistic> possessionStatistics = getStatisticList(StatisticType.POSSESSION.name());

			List<StatisticsParser.Statistic> leftPossessionStatistics =
					possessionStatistics.stream().filter(statistic -> statistic.team == 1).collect(Collectors.toList());

			if (possessionStatistics.size() > 0) {
				float totalSize = (float) possessionStatistics.size();
				float leftPossession = leftPossessionStatistics.size() / totalSize;

				possessionValuesOverTime.add(new PossessionStatistic(time, leftPossession));
			}

			possessionGraphDelta = 0;
		}
	}

	private void parsePlayModeStatistics(GameState gs, SExp exp)
	{
		boolean isGoal = false;
		boolean isKickIn = false;
		boolean isGoalKick = false;
		boolean isFreeKick = false;
		boolean isCornerKick = false;
		boolean isPlayOn = false;
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
						case GameState.PLAY_ON:
							isPlayOn = true;
							break;
						case GameState.OFFSIDE_LEFT:
							statistic.type = StatisticsParser.StatisticType.OFFSIDE;
							statistic.team = 1;
							statistic.index = Integer.parseInt(atoms[1]);
							System.out.println("OFFSIDE 1" + Arrays.toString(atoms));
							break;
						case GameState.OFFSIDE_RIGHT:
							statistic.type = StatisticsParser.StatisticType.OFFSIDE;
							statistic.team = 2;
							statistic.index = Integer.parseInt(atoms[1]);
							System.out.println("OFFSIDE 2" + Arrays.toString(atoms));
							break;
						case GameState.KICK_IN_LEFT:
							statistic.type = StatisticsParser.StatisticType.KICK_IN;
							statistic.team = 1;
							statistic.index = Integer.parseInt(atoms[1]);
							isKickIn = true;
							System.out.println("KICK_IN 1" + Arrays.toString(atoms));
							break;
						case GameState.KICK_IN_RIGHT:
							statistic.type = StatisticsParser.StatisticType.KICK_IN;
							statistic.team = 2;
							statistic.index = Integer.parseInt(atoms[1]);
							isKickIn = true;
							System.out.println("KICK_IN 2" + Arrays.toString(atoms));
							break;
						case GameState.GOAL_KICK_LEFT:
							statistic.type = StatisticsParser.StatisticType.GOAL_KICK;
							statistic.team = 1;
							statistic.index = Integer.parseInt(atoms[1]);
							isGoalKick = true;
							System.out.println("GOAL_KICK 1" + Arrays.toString(atoms));
							break;
						case GameState.GOAL_KICK_RIGHT:
							statistic.type = StatisticsParser.StatisticType.GOAL_KICK;
							statistic.team = 2;
							statistic.index = Integer.parseInt(atoms[1]);
							isGoalKick = true;
							System.out.println("GOAL_KICK 2" + Arrays.toString(atoms));
							break;
						case GameState.FREE_KICK_LEFT:
						case GameState.DIRECT_FREE_KICK_LEFT:
							statistic.type = StatisticsParser.StatisticType.FREE_KICK;
							statistic.team = 1;
							statistic.index = Integer.parseInt(atoms[1]);
							isFreeKick = true;
							System.out.println("FREE KICK 1 " + Arrays.toString(atoms));
							break;
						case GameState.FREE_KICK_RIGHT:
						case GameState.DIRECT_FREE_KICK_RIGHT:
							statistic.type = StatisticsParser.StatisticType.FREE_KICK;
							statistic.team = 2;
							statistic.index = Integer.parseInt(atoms[1]);
							isFreeKick = true;
							System.out.println("FREE KICK 2 " + Arrays.toString(atoms));
							break;
						case GameState.CORNER_KICK_LEFT:
							statistic.type = StatisticsParser.StatisticType.CORNER;
							statistic.team = 1;
							statistic.index = Integer.parseInt(atoms[1]);
							isCornerKick = true;
							System.out.println("CORNER 1 " + Arrays.toString(atoms));
							break;
						case GameState.CORNER_KICK_RIGHT:
							statistic.type = StatisticsParser.StatisticType.CORNER;
							statistic.team = 2;
							statistic.index = Integer.parseInt(atoms[1]);
							isCornerKick = true;
							System.out.println("CORNER 2 " + Arrays.toString(atoms));
							break;
						case GameState.GOAL_LEFT:
							statistic.type = StatisticsParser.StatisticType.GOAL;
							statistic.team = 1;
							statistic.index = Integer.parseInt(atoms[1]);
							isGoal = true;
							System.out.println("GOAL 1 " + Arrays.toString(atoms));
							break;
						case GameState.GOAL_RIGHT:
							statistic.type = StatisticsParser.StatisticType.GOAL;
							statistic.team = 2;
							statistic.index = Integer.parseInt(atoms[1]);
							isGoal = true;
							System.out.println("GOAL 2 " + Arrays.toString(atoms));
							break;
						}
					}
					break;
				case GameState.FOUL:
					statistic.index = Integer.parseInt(atoms[1]);
					statistic.type = StatisticsParser.StatisticType.FOUL;
					statistic.team = Integer.parseInt(atoms[3]);
					statistic.agentID = Integer.parseInt(atoms[4]);
					System.out.println("FOUL " + Arrays.toString(atoms));
					break;
				}

				if (statistic.type != null)
					addStatistic(statistic.type.name(), statistic);

				if (isGoal) {
					for (StatisticsParserListener l : spListeners) {
						l.goalReceived(statistic);
					}
				}
				if (isGoalKick) {
					for (StatisticsParserListener l : spListeners) {
						l.goalKickReceived(statistic);
					}
				}
				if (isPlayOn) {
					for (StatisticsParserListener l : spListeners) {
						l.playOnReceived();
					}
				}
				if (isCornerKick) {
					for (StatisticsParserListener l : spListeners) {
						l.cornerKickReceived(statistic);
					}
				}
			}
		}
	}

	private void calculateStatistics(GameState gs)
	{
		//		if (!gs.isInitialized() || !gs.isPlaying())
		//			return;

		Ball ball = world.getBall();
		Team leftTeam = world.getLeftTeam();
		Team rightTeam = world.getRightTeam();

		if (leftTeam.getAgents().isEmpty() || rightTeam.getAgents().isEmpty())
			return;

		try {
			parseStatistics();
			detectPossession(gs);
			//			detectKick(gs);
			storePositions(gs);

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

	private void storePositions(GameState gs)
	{
		if (positionStoreDelta >= positionStoreGap) {
			Ball ball = world.getBall();
			Team leftTeam = world.getLeftTeam();
			Team rightTeam = world.getRightTeam();

			// Ball
			int BallXPosition = MatrixUtil.normalizeIndex(
					Math.round(fieldLength / 2f - ball.getPosition().x) - 1, 0, (int) fieldLength - 1);
			int BallYPosition = MatrixUtil.normalizeIndex(
					Math.round(fieldWidth / 2f - ball.getPosition().z) - 1, 0, (int) fieldWidth - 1);
			ballPositions[BallYPosition][BallXPosition] += 1;

			// Left Team
			//			storeTeamPositions(leftTeam, fieldLength, fieldWidth, leftTeamPositions);
			// Right Team
			//			storeTeamPositions(rightTeam, fieldLength, fieldWidth, rightTeamPositions);

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

	private void detectKick(GameState gs)
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
			detectDrible();
			detectShot(gs, velocityDelta);
		}

		//            if (prevBallPosition.minus(ball.getPosition()).length() < 0.01f) {
		//                System.out.println("Ball same position");
		//            }
	}

	private void detectShot(GameState gs, Vec3f currentBallVelocity)
	{
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

				//				this.statistics.add(new Statistic(time, typeShot, agent.getTeam().getID(),
				// agent.getID()));
			}
		}
	}

	private void detectDrible()
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
					//					this.addStatistic(new Statistic(time, StatisticType.DRIBLE,
					//							agent.getTeam().getID() == world.getLeftTeam().getID() ? 1 : 2,
					// agent.getID()));
				}
				prevDribleTouches = 0;
			}
			dribleTimeDelta = 0;
		}
	}

	private void detectPossession(GameState gs)
	{
		if (possessionStoreDelta >= possessionStoreGap) {
			Ball ball = world.getBall();
			Team leftTeam = world.getLeftTeam();
			Team rightTeam = world.getRightTeam();

			minimumDistanceToBall = 1000;
			agent = null;

			for (Agent player : leftTeam.getAgents()) {
				float distanceToBall = player.getPosition().minus(ball.getPosition()).lengthSquared();
				if (distanceToBall < minimumDistanceToBall) {
					minimumDistanceToBall = distanceToBall;
					agent = player;
				}
			}

			for (Agent player : rightTeam.getAgents()) {
				float distanceToBall = player.getPosition().minus(ball.getPosition()).lengthSquared();
				if (distanceToBall < minimumDistanceToBall) {
					minimumDistanceToBall = distanceToBall;
					agent = player;
				}
			}

			addStatistic(StatisticType.POSSESSION.name(),
					new Statistic(time, StatisticType.POSSESSION,
							agent.getTeam().getID() == world.getLeftTeam().getID() ? 1 : 2, agent.getID()));
			possessionStoreDelta = 0;
		}
	}

	@Override
	public void gsServerMessageReceived(GameState gs, SExp exp)
	{
		synchronized (this)
		{
			if (!gs.isInitialized())
				return;

			if (!isInitialized)
				initializeMaps();

			parsePlayModeStatistics(gs, exp);
			calculateStatistics(gs);
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
		//		this.prevTime = this.time;
		//		this.time = gs.getTime();
	}

	public void addStatistic(String key, Statistic statistic)
	{
		boolean alreadyHaveStatistic = false;
		List<Statistic> keyStatistics = statistics.getOrDefault(key, new ArrayList<Statistic>());
		for (Statistic s : keyStatistics) {
			if (s.type == statistic.type && s.team == statistic.team && s.agentID == statistic.agentID &&
					Math.abs(statistic.time - s.time) < 2.0) {
				alreadyHaveStatistic = true;
				break;
			}
		}
		if (!alreadyHaveStatistic) {
			keyStatistics.add(statistic);
			statistics.replace(key, keyStatistics);
		}
	}

	public List<Statistic> getStatisticList(String key)
	{
		return statistics.getOrDefault(key, new CopyOnWriteArrayList<Statistic>());
	}

	public float getMinimumDistanceToBall()
	{
		return minimumDistanceToBall;
	}
}
