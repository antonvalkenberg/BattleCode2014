package simplePastr;

import java.util.HashMap;

import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.Robot;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Team;

public class Soldier extends AbstractRobotType {

	private boolean inactive = false;
	private SoldierRole role;
	private PathFinder pathFinder;
	private PathFinderMLineBug intelligPathFinder;
	MapLocation bestPastrLocation = new MapLocation(0, 0);
	HashMap<MapLocation, Integer> visited = new HashMap<MapLocation, Integer>();

	public Soldier(RobotController rc) {
		super(rc);
	}

	@Override
	protected void act() throws GameActionException {
		if (inactive || !rc.isActive()) {
			return;
		}
		if (rc.isConstructing()) {
			inactive = true;
		}
		switch (role) {
		case ATTACKER:
			actAttacker();
			break;
		case NOISE_TOWER_BUILDER:
			break;
		case PASTR_BUILDER:
			actPastrBuilder();
			break;
		case PROTECTOR:
			actProtector();
			break;
		default:
			break;
		}
	}

	@Override
	protected void init() throws GameActionException {
		bestPastrLocation = Channel.getBestPastrLocation(rc);
		role = Channel.requestSoldierRole(rc);
		rc.setIndicatorString(0, role.toString());
		Channel.announceSoldierRole(rc, role);
		pathFinder = new PathFinderSimple(rc);
		intelligPathFinder = new PathFinderMLineBug(rc);
		intelligPathFinder.setTarget(rc.getLocation(), bestPastrLocation);
	}

	private void visit(MapLocation loc) {
		for (MapLocation old : visited.keySet()) {
			visited.put(old, visited.get(old) + 1);
		}
		visited.put(loc, 0);
	}

	private void actAttacker() throws GameActionException {
		Team we = rc.getTeam();
		Team opponent = we.opponent();
		MapLocation currentLoc = rc.getLocation();
		visit(currentLoc);

		MapLocation[] nextToAttack = null;
		// opponent's pastr?
		MapLocation[] pastrOpponentAll = rc.sensePastrLocations(opponent);
		if (pastrOpponentAll != null) {
			nextToAttack = pastrOpponentAll.clone();
		} else {
			// communicating opponents?
			MapLocation[] robotsOpponentAll = rc
					.senseBroadcastingRobotLocations(opponent);
			if (robotsOpponentAll != null) {
				nextToAttack = robotsOpponentAll.clone();
			}
		}

		if (nextToAttack.length != 0) {
			boolean shoot = false;

			// attack any pastr in range
			MapLocation target = nextToAttack[0];
			for (int i = 0; i < nextToAttack.length; i++) {
				target = nextToAttack[i];
				if (rc.canAttackSquare(target)) {
					rc.attackSquare(target);
					shoot = true;
					break;
				}
			}

			if (!shoot) {
				Direction nextDir = pathFinder.getNextDirection(visited,
						target, currentLoc);

				int i = 0;
				while (!rc.canMove(nextDir) && i < 8) {
					nextDir = C.DIRECTIONS[i++];
				}
				if (rc.canMove(nextDir)) {
					rc.sneak(nextDir);
				}
			}

		} else {
			actProtector();
		}
	}

	// private void actPastrBuilder() throws GameActionException {
	// MapLocation currentLoc = rc.getLocation();
	// if (currentLoc.x == bestPastrLocation.x
	// && currentLoc.y == bestPastrLocation.y) {
	// rc.construct(RobotType.PASTR);
	// } else {
	// visit(currentLoc);
	// Direction dir = pathFinder.getNextDirection(visited,
	// bestPastrLocation, currentLoc);
	// if (rc.canMove(dir)) {
	// rc.move(dir);
	// }
	// }
	// }

	private void actPastrBuilder() throws GameActionException {
		MapLocation currentLoc = rc.getLocation();
		if (currentLoc.x == bestPastrLocation.x
				&& currentLoc.y == bestPastrLocation.y) {
			rc.construct(RobotType.PASTR);
		} else {
			intelligPathFinder.move();
		}
	}

	private void actProtector() throws GameActionException {
		MapLocation currentLoc = rc.getLocation();
		if (PathFinder.distance(currentLoc, bestPastrLocation) > 4) {
			visit(currentLoc);
			Direction dir = pathFinder.getNextDirection(visited,
					bestPastrLocation, currentLoc);
			rc.yield();
			if (rc.canMove(dir)) {
				rc.move(dir);
			} else {
				dir = C.DIRECTIONS[randall.nextInt(C.DIRECTIONS.length)];
				if (rc.canMove(dir)) {
					rc.move(dir);
				}
			}
		} else {
			Robot[] nearbyEnemies = rc.senseNearbyGameObjects(Robot.class, 10,
					rc.getTeam().opponent());
			if (nearbyEnemies.length > 0) {
				RobotInfo robotInfo = rc.senseRobotInfo(nearbyEnemies[0]);
				rc.attackSquare(robotInfo.location);

			} else {
				// Sneak towards the enemy
				Direction toEnemy = rc.getLocation().directionTo(
						rc.senseEnemyHQLocation());
				if (rc.canMove(toEnemy)) {
					rc.sneak(toEnemy);
				} else {
					// move randomly
					Direction moveDirection = C.DIRECTIONS[randall.nextInt(8)];
					if (rc.canMove(moveDirection)) {
						rc.move(moveDirection);
					}
				}
			}
		}

	}
}
