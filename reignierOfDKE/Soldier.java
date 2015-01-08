package reignierOfDKE;

import java.util.ArrayList;
import java.util.List;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.Robot;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Team;

public class Soldier extends AbstractRobotType {

	private boolean inactive = false;
	private int teamId;
	private int id;
	private PathFinderSnailTrail pathFinderSnailTrail;
	private PathFinderAStar pathFinderAStar;
	protected PathFinderGreedy pathFinderGreedy;
	private MapLocation myPreviousLocation;
	private Team us;
	private Team opponent;
	private MapLocation enemyHq;
	private int fleeCounter = 0;
	private final int MAX_CIRCULATE = 30;
	private final int MIN_CIRCULATE = 10;

	Task task = Task.GOTO;
	MapLocation target = new MapLocation(0, 0);
	MapLocation myLoc;

	private static final int CLOSE_TEAM_MEMBER_DISTANCE_THRESHOLD = 5;
	private static final double WAIT_FOR_TEAM_FRACTION_THRESHOLD = 0.5;

	public Soldier(RobotController rc) {
		super(rc);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see teamreignofdke.AbstractRobotType#act()
	 */
	@Override
	protected void act() throws GameActionException {
		Channel.signalAlive(rc, id);
		myLoc = rc.getLocation();
		updateTask();
		if (rc.isActive()) {
			actMicro(target, task);
		} else {
			return;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see teamreignofdke.AbstractRobotType#init()
	 */
	@Override
	protected void init() throws GameActionException {
		Channel.announceSoldierType(rc, RobotType.SOLDIER);
		id = Channel.requestSoldierId(rc);
		Channel.signalAlive(rc, id);

		teamId = Channel.requestTeamId(rc);
		Channel.announceTeamId(rc, id, teamId);
		rc.setIndicatorString(0, "SOLDIER [" + id + "] TEAM [" + teamId + "]");
		target = Channel.getTarget(rc, teamId);
		task = Channel.getTask(rc, teamId);

		us = rc.getTeam();
		opponent = us.opponent();
		pathFinderAStar = new PathFinderAStar(rc, id);
		pathFinderSnailTrail = new PathFinderSnailTrail(rc,
				pathFinderAStar.map, pathFinderAStar.hqSelfLoc,
				pathFinderAStar.hqEnemLoc, pathFinderAStar.ySize,
				pathFinderAStar.xSize);
		pathFinderSnailTrail.setTarget(target);
		pathFinderGreedy = new PathFinderGreedy(rc, randall);
		enemyHq = pathFinderAStar.hqEnemLoc;

		myPreviousLocation = rc.getLocation();
	}

	private void actMicro(MapLocation target, Task task)
			throws GameActionException {
		Robot[] closeOpponents = rc.senseNearbyGameObjects(Robot.class,
				RobotType.SOLDIER.sensorRadiusSquared, opponent);
		boolean oppHqInRange = myLoc.distanceSquaredTo(enemyHq) <= RobotType.SOLDIER.sensorRadiusSquared;
		if (fleeCounter > 0) {
			if (fleeCounter == 1) {
				// this adds a "random" factor, such that the robots not always
				// go backwards and forward on the same line
				pathFinderGreedy.setTarget(target);
			}
			pathFinderGreedy.move();
			fleeCounter--;
			return;
		}
		if (size(closeOpponents) < 1
				|| (size(closeOpponents) == 1 && oppHqInRange)) {
			// no opponents in sight - go for your task
			switch (task) {
			case BUILD_NOISETOWER:
				if (myLoc.isAdjacentTo(target)
						&& rc.senseObjectAtLocation(target) != null) {
					Channel.broadcastTask(rc, Task.CIRCULATE, target, teamId);
					rc.construct(RobotType.NOISETOWER);
					break;
				}
			case BUILD_PASTR:
				if (myLoc.equals(target)) {
					Channel.broadcastTask(rc, Task.BUILD_NOISETOWER, target,
							teamId);
					rc.construct(RobotType.PASTR);
					break;
				}
			case GOTO:
				// Check if I am the leader of my team
				if (amILeader()) {
					// Sense how many of my team members are close
					int closeTeamMembers = getNumberOfCloseTeamMembers();
					int totalTeamMembers = Channel.getSoldierCountOfTeam(rc,
							teamId) - 1; // -1 cause we ourselves are 1
					rc.setIndicatorString(2, "Leading " + closeTeamMembers
							+ "/" + totalTeamMembers + " team members");
					double closeTeamFraction = closeTeamMembers
							/ (totalTeamMembers * 1.0);
					// Check if we need to wait for our team members
					if (closeTeamFraction > WAIT_FOR_TEAM_FRACTION_THRESHOLD) {
						// Calculate the route to the target
						if (!target.equals(pathFinderAStar.getTarget())) {
							pathFinderAStar.setTarget(target);
						}
						// If we have reached the target, set temporary target
						// to
						// target
						if (myLoc.equals(target)) {
							Channel.broadcastTemporaryTarget(rc, teamId, target);
						} else if (!pathFinderAStar.move()) {
							// Something went wrong
							doRandomMove();
						} else {
							// Means the leader moved
							// Broadcast temporary target (current + direction)
							MapLocation tempTarget = myLoc
									.add(myPreviousLocation.directionTo(myLoc));
							Channel.broadcastTemporaryTarget(rc, teamId,
									tempTarget);
							// Save my previous location
							myPreviousLocation = myLoc;
						}
					}
				} else {
					rc.setIndicatorString(2, "Following");
					// If I am not the leader, check if my leader has set a
					// temporary target
					MapLocation tempTarget = Channel.getTemporaryTarget(rc,
							teamId);
					if (tempTarget.x > 0 || tempTarget.y > 0) {
						rc.setIndicatorString(2, "Following to " + tempTarget);
						// Move to temporary target
						if (!tempTarget
								.equals(pathFinderSnailTrail.getTarget())) {
							pathFinderSnailTrail.setTarget(tempTarget);
						}
						// If we fail to move where we want to go
						if (!pathFinderSnailTrail.move()) {
							// Move random
							doRandomMove();
						}
					} else {
						doRandomMove();
					}
				}
				break;
			case CIRCULATE:
				circulate(target);
				break;
			case ACCUMULATE:
				pathFinderGreedy.setTarget(target);
				pathFinderGreedy.move();
				break;
			default:
				break;
			}
		} else {
			List<Robot> closeSoldiers = new ArrayList<Robot>();
			for (Robot robot : closeOpponents) {
				RobotInfo ri = rc.senseRobotInfo(robot);
				if (ri.type.equals(RobotType.SOLDIER)) {
					closeSoldiers.add(robot);
				}
			}
			MapLocation oppAt = rc.senseLocationOf(closeOpponents[0]);
			if (oppAt.equals(enemyHq)) {
				oppAt = rc.senseLocationOf(closeOpponents[1]);
			}
			Robot[] closeFriends = rc.senseNearbyGameObjects(Robot.class,
					oppAt, RobotType.SOLDIER.sensorRadiusSquared, us);
			if (closeSoldiers.size() > size(closeFriends)) {
				// there are more opponents. Better get away.
				fleeCounter = 4;
				MapLocation away = myLoc.add(oppAt.directionTo(myLoc), 10);
				pathFinderGreedy.setTarget(away);
			} else {
				// we are dominating!
				int distance = myLoc.distanceSquaredTo(oppAt);
				if (distance <= RobotType.SOLDIER.attackRadiusMaxSquared) {
					rc.attackSquare(oppAt);
				} else {
					pathFinderGreedy.setTarget(oppAt);
					pathFinderGreedy.move();
				}
			}
		}
	}

	private void updateTask() {
		Task newTask = Channel.getTask(rc, teamId);
		MapLocation newTarget = Channel.getTarget(rc, teamId);
		if (!newTarget.equals(target) || !newTask.equals(task)) {
			task = newTask;
			target = newTarget;
		}
		rc.setIndicatorString(1, "DOING TASK " + task + " ON TARGET " + target);
	}

	private void doRandomMove() {
		Direction random = C.DIRECTIONS[randall.nextInt(C.DIRECTIONS.length)];
		if (rc.canMove(random)) {
			try {
				rc.move(random);
			} catch (GameActionException e) {
				e.printStackTrace();
			}
		}
	}

	private boolean amILeader() {
		// Return whether or not my ID is the ID of the leader of my team
		return id == Channel.getLeaderIdOfTeam(rc, teamId);
	}

	private int getNumberOfCloseTeamMembers() {
		int closeTeamMembers = 0;
		// Loop through all robots
		for (int id = 0; id < GameConstants.MAX_ROBOTS; id++) {
			if (id != this.id) {
				// Check if the robot is alive
				if (Channel.isAlive(rc, id)) {
					// Check the alive robot is on the same team
					if (teamId == Channel.getTeamIdOfSoldier(rc, id)) {
						// Get the position of this robot
						MapLocation teamMemberLocation = Channel
								.getLocationOfSoldier(rc, id);
						int distance = PathFinder.distance(myLoc,
								teamMemberLocation);
						if (distance <= CLOSE_TEAM_MEMBER_DISTANCE_THRESHOLD) {
							closeTeamMembers++;
						}
					}
				}
			}
		}
		return closeTeamMembers;
	}

	/**
	 * checks if the given array is null or empty
	 * 
	 * @param array
	 * @return
	 */
	public static final <T> int size(T[] array) {
		if (array == null) {
			return 0;
		}
		return array.length;
	}

	private void circulate(MapLocation center) {
		int distance = myLoc.distanceSquaredTo(center);

		if (distance >= MIN_CIRCULATE && distance <= MAX_CIRCULATE) {
			doRandomMove();
		} else {
			if (distance < MIN_CIRCULATE) {
				pathFinderGreedy
						.setTarget(myLoc.add(center.directionTo(myLoc)));
			} else {
				pathFinderGreedy.setTarget(center);
			}
			try {
				pathFinderGreedy.move();
			} catch (GameActionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}
}
