package reignierOfDKE;

import reignierOfDKE.C.MapType;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.Robot;
import battlecode.common.RobotController;
import battlecode.common.RobotType;

public class HQ extends AbstractRobotType {

	private int ySize;
	private int xSize;
	private MapLocation myHq;
	private MapLocation otherHq;
	private MapAnalyzer mapAnalyzer;
	/**
	 * soldiers are distributed over 3 teams: <br>
	 * team 0: 50% of soldiers -> flexible attacking team <br>
	 * team 1: 25% of soldiers -> build pastr / defend pastr <br>
	 * team 2: 25% of soldiers -> flexible attacking team / build 2nd pastr /
	 * defend 2nd pastr <br>
	 */
	private Team[] teams;
	private Direction spawningDefault;
	private int teamId = teamIdAssignment[0];
	private int pastrThreshold;

	private MapLocation pastr1 = new MapLocation(-1, -1);
	private MapLocation pastr2 = new MapLocation(-1, -1);

	private static final int[] teamIdAssignment = new int[] { 2, 0, 1, 0 };
	private int teamIndex = 0;

	// info about opponent, is updated in updateInfoAboutOpponent()
	private int countBrdCastingOppSoldiers = 0;
	private MapLocation oppSoldiersCenter;
	private int oppSoldiersMeanDistToCenter = 0;
	private int oppMilkQuantity = 0;

	public HQ(RobotController rc) {
		super(rc);
	}

	@Override
	protected void act() throws GameActionException {
		Team.updateSoldierCount(rc, teams);
		// Check if a robot is spawnable and spawn one if it is
		if (rc.isActive() && rc.senseRobotCount() < GameConstants.MAX_ROBOTS) {
			// determine team id of soldier to spawn:
			teamIndex++;
			teamIndex %= teams.length;
			teamId = teamIdAssignment[teamIndex];
			Channel.assignTeamId(rc, teamId);
			Robot[] closeOpponents = rc.senseNearbyGameObjects(Robot.class,
					RobotType.HQ.sensorRadiusSquared, rc.getTeam().opponent());
			if (Soldier.size(closeOpponents) > 0) {
				Direction away = rc.senseLocationOf(closeOpponents[0])
						.directionTo(myHq);
				spawningDefault = away;
			}
			if (rc.canMove(spawningDefault)) {
				rc.spawn(spawningDefault);
			} else {
				int i = 0;
				Direction dir = spawningDefault;
				while (!rc.canMove(dir) && i < C.DIRECTIONS.length) {
					dir = dir.rotateLeft();
				}
				if (rc.canMove(dir)) {
					rc.spawn(dir);
				}
			}
		}
		updateInfoAboutOpponent();
		if (rc.senseRobotCount() < 1) {
			// location between our HQ and opponent's HQ:
			MapLocation target = new MapLocation(
					(myHq.x * 3 / 4 + otherHq.x / 4),
					(myHq.y * 3 / 4 + otherHq.y / 4));

			teams[0].setTask(Task.CIRCULATE, target);
			teams[1].setTask(Task.CIRCULATE, target);
			teams[2].setTask(Task.CIRCULATE, target);
		} else {
			coordinateTroops();
		}
	}

	private void coordinateTroops() {
		MapLocation[] opponentPastrLocations = rc.sensePastrLocations(rc
				.getTeam().opponent());
		// If the opponent has any PASTRs
		if (Soldier.size(opponentPastrLocations) > 0) {
			// Send our teams 0 and 1 in for the kill
			teams[0].setTask(Task.GOTO, opponentPastrLocations[0]);
			teams[2].setTask(Task.GOTO, opponentPastrLocations[0]);
			if (oppMilkQuantity > 5000000) {
				teams[1].setTask(Task.GOTO, opponentPastrLocations[0]);
			} else {
				boolean pastrTaskAssigned = buildPastr(1);
			}
		} else {
			boolean pastrTaskAssigned = buildPastr(1);
			teams[0].setTask(Task.CIRCULATE, Channel.getTarget(rc, 1));
			teams[2].setTask(Task.CIRCULATE, Channel.getTarget(rc, 1));
		}
	}

	private boolean buildPastr(int teamId) {
		if (Channel.getTarget(rc, teamId).equals(pastr1)) {
			return false;
		}
		if (rc.senseRobotCount() > pastrThreshold) {
			// Check if we have any active PASTRs
			MapLocation[] ownPastrLocations = rc.sensePastrLocations(rc
					.getTeam());
			pastr1 = mapAnalyzer.evaluateBestPastrLoc();
			boolean build = true;
			if (Soldier.size(ownPastrLocations) > 0) {
				for (MapLocation mapLocation : ownPastrLocations) {
					if (mapLocation.equals(pastr1)) {
						build = false;
					}
				}
			}
			// Assign the correct tasks to the teams
			if (build) {
				teams[teamId].setTask(Task.BUILD_PASTR, pastr1);
			}
			return build;
		}
		return false;
	}

	@Override
	protected void init() throws GameActionException {
		ySize = rc.getMapHeight();
		xSize = rc.getMapWidth();
		myHq = rc.senseHQLocation();
		otherHq = rc.senseEnemyHQLocation();
		teams = Team.getTeams(rc);

		mapAnalyzer = new MapAnalyzer(rc, myHq, otherHq, ySize, xSize);
		// mapAnalyzer.generateRealDistanceMap(); // TODO: too expensive
		// mapAnalyzer.printMapAnalysisDistance();
		initPastrTreshold();
		spawningDefault = myHq.directionTo(otherHq);
		int i = 0;
		while (!rc.canMove(spawningDefault) && i < C.DIRECTIONS.length) {
			spawningDefault = C.DIRECTIONS[i];
			i++;
		}
	}

	private void initPastrTreshold() {
		MapType size = mapAnalyzer.getMapType();
		switch (size) {
		case Large:
			pastrThreshold = 2;
			break;
		case Medium:
			pastrThreshold = 5;
			break;
		default: // Small
			pastrThreshold = 10;
			break;
		}
	}

	private void updateInfoAboutOpponent() {
		countBrdCastingOppSoldiers = Channel.getCountOppBrdCastingSoldiers(rc);
		oppSoldiersCenter = Channel.getPositionalCenterOfOpponent(rc);
		oppSoldiersMeanDistToCenter = Channel.getOpponentMeanDistToCenter(rc);
		oppMilkQuantity = Channel.getOpponentMilkQuantity(rc);
	}
}
