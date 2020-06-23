package rv.comm.rcssserver;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import jsgl.math.vector.Vec3f;
import rv.Viewer;
import rv.util.MatrixUtil;
import rv.world.Team;
import rv.world.WorldModel;
import rv.world.objects.Agent;
import rv.world.objects.Ball;

public class StatisticsParser implements GameState.ServerMessageReceivedListener, GameState.GameStateChangeListener
{
	public interface StatisticsParserListener {
		void goalReceived(Statistic goalStatistic);
		void goalKickReceived(Statistic goalKickStatistic);
		void cornerKickReceived(Statistic cornerKickStatistic);
		void dribbleStartReceived(Agent dribbler);
		void dribbleStopReceived();
		void kickInReceived(Statistic kickInStatistic);
		void offsideReceived(Statistic offSideStatistic);
		void foulReceived(Statistic foulStatistic);
		void freeKickReceived(Statistic freeKickStatistic);
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
	private Agent prevDribbler = null;
	private double prevBallVelocity = 0;
	private double kickerDistanceTrigger = 1;
	private float velocityDeltaTrigger = 0.01f;
	private float degreeDeltaTrigger = 3f;
	private float shotDistanceTrigger = 2f;

	private float kickTimeGap = 1f;
	private float kickTimeDelta = 0f;

	private float dribbleTimeGap = 1f;
	private float dribbleTimeDelta = 0f;
	private int dribbleMinTouches = 2;
	private int dribbleMinDistance = 1;
	private int prevDribbleTouches = 0;
	private Vec3f dribbleInitialPosition = null;

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

	private BallEstimator ballEstimator;

	public StatisticsParser(WorldModel world, Viewer viewer)
	{
		this.viewer = viewer;
		this.world = world;
		this.ballEstimator = new BallEstimator(viewer);

		time = 0;
		prevTime = 0;

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
		boolean isFoul = false;
		boolean isOffside = false;
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
							isOffside = true;
							System.out.println("OFFSIDE 1" + Arrays.toString(atoms));
							break;
						case GameState.OFFSIDE_RIGHT:
							statistic.type = StatisticsParser.StatisticType.OFFSIDE;
							statistic.team = 2;
							statistic.index = Integer.parseInt(atoms[1]);
							isOffside = true;
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
					isFoul = true;
					// System.out.println("FOUL " + Arrays.toString(atoms));
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
				if (isFreeKick) {
					for (StatisticsParserListener l : spListeners) {
						l.freeKickReceived(statistic);
					}
				}
				if (isKickIn) {
					for (StatisticsParserListener l : spListeners) {
						l.kickInReceived(statistic);
					}
				}
				if (isOffside) {
					for (StatisticsParserListener l : spListeners) {
						l.offsideReceived(statistic);
					}
				}
				if (isFoul) {
					for (StatisticsParserListener l : spListeners) {
						l.foulReceived(statistic);
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
			detectKick();
			storePositions();

			prevAgent = agent;

			float cycleTime = time - prevTime;

			dribbleTimeDelta += cycleTime;
			//			kickTimeDelta += cycleTime;
			possessionStoreDelta += cycleTime;
			possessionGraphDelta += cycleTime;
			positionStoreDelta += cycleTime;
		} catch (NullPointerException e) {
			System.err.println("Initializing statistics " + e.getMessage());
		}
	}

	private void storePositions()
	{
		if (positionStoreDelta < positionStoreGap)
			return;

		positionStoreDelta = 0;
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

		positionsCount++;
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

	private void detectKick()
	{
		Ball ball = world.getBall();

		if (ball == null || agent == null)
			return;

		if (checkOutOfBounds(agent)) {
			for (StatisticsParserListener l : spListeners) {
				l.dribbleStopReceived();
			}
		}

		Vec3f currentBallVelocity = ballEstimator.estimatedVel4(0.0f);
		Vec3f ballPosition = ball.getPosition();
		double roundedCurrentVelocityDelta = (double) Math.round(currentBallVelocity.length() * 1000) / 1000;

		//		System.out.println("-------------------- ");
		int minutes = (int) Math.floor(time / 60.0);
		int seconds = (int) (time - minutes * 60);
		String timeText = String.format(Locale.US, "%02d:%02d", minutes, seconds);
		//		System.out.println("time " + timeText);
		//		System.out.println("velocity " + roundedCurrentVelocityDelta);
		//		System.out.println("distance " + ballPosition.minus(agent.getPosition()).length());
		if (roundedCurrentVelocityDelta > prevBallVelocity && prevTime < time &&
				ballPosition.minus(agent.getPosition()).length() < kickerDistanceTrigger &&
				roundedCurrentVelocityDelta > velocityDeltaTrigger) {
			// Drible detection
			//			System.out.println("KICK " + new Random().nextInt((1000 - 1) + 1) + 1);
			//			boolean hasShot = detectShot();
			//
			//			if (hasShot) {
			//				return;
			//			}

			detectDribble();
		}

		prevBallVelocity = roundedCurrentVelocityDelta;
	}

	private boolean checkOutOfBounds(Agent agent)
	{
		Vec3f agentPosition = agent.getPosition();
		return Math.abs(agentPosition.x) > fieldLength / 2 || Math.abs(agentPosition.z) > fieldWidth / 2;
	}

	private boolean detectShot()
	{
		int leftPossessionMultiplier = agent.getTeam().getID() == world.getLeftTeam().getID() ? -1 : 1;
		Vec3f ballFinalPos = ballEstimator.estimatedFinalPosAway();
		//
		//		if (ballFinalPos.x * leftPossessionMultiplier <= world.getBall().getPosition().x *
		// leftPossessionMultiplier) { 			return false;
		//		}

		System.out.println("player post + " + agent.getPosition().toString());
		System.out.println("Last post + " + ballEstimator.estimatedFinalPos4().toString());
		//		if (leftPossessionMultiplier * agent.getPosition().x > 0) {
		float goalLineX = leftPossessionMultiplier * fieldLength / 2;
		float goalLineDistanceX = goalLineX - leftPossessionMultiplier * shotDistanceTrigger;

		Vec3f shotVector = ballFinalPos.minus(agent.getPosition());
		float goalCenterZ = fieldWidth / 2;
		float shotAreaEnd = goalCenterZ + world.getGameState().getGoalWidth() / 2 + shotDistanceTrigger;

		if (ballFinalPos.x * leftPossessionMultiplier >= goalLineDistanceX * leftPossessionMultiplier) {
			Statistic statistic = new Statistic(time, StatisticType.SHOT, agent.getTeam().getID(), agent.getID());

			float zCoordinate = agent.getPosition().y +
								(shotVector.y * ((goalLineDistanceX - agent.getPosition().x) * shotVector.x));

			if (-shotAreaEnd <= zCoordinate && zCoordinate <= shotAreaEnd) {
				statistic.type = StatisticType.SHOT_TARGET;
			} else {
				zCoordinate =
						agent.getPosition().y + (shotVector.y * ((goalLineX - agent.getPosition().x) * shotVector.x));

				if (-shotAreaEnd <= zCoordinate && zCoordinate <= shotAreaEnd) {
					statistic.type = StatisticType.SHOT_TARGET;
				}
			}

			System.out.println("DETECTED SHOT TYPE " + statistic.type.name);
			addStatistic(statistic.type.name(), statistic);
			return true;
		}
		return false;
	}

	private void detectDribble()
	{
		if (prevDribbler == null)
			prevDribbler = agent;

		if (agent.getTeam().getID() == prevDribbler.getTeam().getID() && agent.getID() == prevDribbler.getID()) {
			if (dribbleTimeDelta < dribbleTimeGap) {
				return;
			}

			if (dribbleInitialPosition == null) {
				dribbleInitialPosition = world.getBall().getPosition();
			}
			prevDribbleTouches++;

			if (prevDribbleTouches == 1) {
				for (StatisticsParserListener spl : spListeners) {
					spl.dribbleStartReceived(prevDribbler);
				}
			}
			dribbleTimeDelta = 0;
		} else {
			for (StatisticsParserListener spl : spListeners) {
				spl.dribbleStopReceived();
			}
			float dribbleDistance = world.getBall().getPosition().minus(dribbleInitialPosition).length();
			if (prevDribbleTouches >= dribbleMinTouches && dribbleDistance >= dribbleMinDistance) {
				dribbleInitialPosition = null;
				this.addStatistic(StatisticType.DRIBLE.name(),
						new Statistic(time, StatisticType.DRIBLE,
								prevDribbler.getTeam().getID() == world.getLeftTeam().getID() ? 1 : 2,
								prevDribbler.getID()));
			}
			prevDribbler = agent;
			prevDribbleTouches = 0;
		}
	}

	private void detectPossession(GameState gs)
	{
		if (possessionStoreDelta < possessionStoreGap)
			return;

		possessionStoreDelta = 0;
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
		this.ballEstimator.update();
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
