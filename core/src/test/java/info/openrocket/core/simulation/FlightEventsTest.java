package info.openrocket.core.simulation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import info.openrocket.core.document.Simulation;
import info.openrocket.core.logging.SimulationAbort;
import info.openrocket.core.logging.Warning;
import info.openrocket.core.rocketcomponent.AxialStage;
import info.openrocket.core.rocketcomponent.BodyTube;
import info.openrocket.core.rocketcomponent.FlightConfigurationId;
import info.openrocket.core.rocketcomponent.InnerTube;
import info.openrocket.core.rocketcomponent.Parachute;
import info.openrocket.core.rocketcomponent.ParallelStage;
import info.openrocket.core.rocketcomponent.Rocket;
import info.openrocket.core.simulation.exception.SimulationException;
import info.openrocket.core.util.BaseTestCase;
import info.openrocket.core.util.TestRockets;

/**
 * Tests to verify that simulations contain all the expected flight events.
 */
public class FlightEventsTest extends BaseTestCase {
	
	private static final double EPSILON = 0.005;

	/**
	 * Tests for a single stage design.
	 */
	@Test
	public void testSingleStage() throws SimulationException {
		final Rocket rocket = TestRockets.makeEstesAlphaIII();
		final AxialStage stage = rocket.getStage(0);
		final InnerTube motorMountTube = (InnerTube) stage.getChild(1).getChild(2);
		final Parachute parachute = (Parachute) stage.getChild(1).getChild(3);

		final Simulation sim = new Simulation(rocket);
		sim.getOptions().setISAAtmosphere(true);
		sim.getOptions().setTimeStep(0.05);
		sim.setFlightConfigurationId(TestRockets.TEST_FCID_0);

		sim.simulate();

		// Test branch count
		final int branchCount = sim.getSimulatedData().getBranchCount();
		assertEquals(1, branchCount, " Single stage simulation invalid branch count ");

		final FlightEvent[] expectedEvents = new FlightEvent[] {
				new FlightEvent(FlightEvent.Type.LAUNCH, 0.0, rocket),
				new FlightEvent(FlightEvent.Type.IGNITION, 0.0, motorMountTube),
				new FlightEvent(FlightEvent.Type.LIFTOFF, 0.1275, null),
				new FlightEvent(FlightEvent.Type.LAUNCHROD, 0.13, null),
				new FlightEvent(FlightEvent.Type.BURNOUT, 2.0, motorMountTube),
				new FlightEvent(FlightEvent.Type.EJECTION_CHARGE, 2.0, stage),
				new FlightEvent(FlightEvent.Type.SIM_WARN, 2.0, null, new Warning.HighSpeedDeployment(80.6)),
				new FlightEvent(FlightEvent.Type.RECOVERY_DEVICE_DEPLOYMENT, 2.001, parachute),
				new FlightEvent(FlightEvent.Type.APOGEE, 2.48, rocket),
				new FlightEvent(FlightEvent.Type.GROUND_HIT, 42.97, null),
				new FlightEvent(FlightEvent.Type.SIMULATION_END, 42.97, null)
		};

		checkEvents(expectedEvents, sim, 0);
	}

	/**
	 * Tests for a multi-stage design.
	 */
	@Test
	public void testMultiStage() throws SimulationException {
		final Rocket rocket = TestRockets.makeMultiStageEventTestRocket();

		final Simulation sim = new Simulation(rocket);
		sim.getOptions().setISAAtmosphere(true);
		sim.getOptions().setTimeStep(0.05);
		sim.getOptions ().getAverageWindModel().setAverage(0.1);
		rocket.getSelectedConfiguration().setAllStages();
		FlightConfigurationId fcid = rocket.getSelectedConfiguration().getFlightConfigurationID();
		sim.setFlightConfigurationId(fcid);

		sim.simulate();

		// Test branch count
		final int expectedBranchCount = 3;
		final int actualBranchCount = sim.getSimulatedData().getBranchCount();
		assertEquals(expectedBranchCount, actualBranchCount, " Multi-stage simulation invalid branch count ");

		final AxialStage sustainer = rocket.getStage(0);
		final BodyTube sustainerBody = (BodyTube) sustainer.getChild(1);

		final AxialStage centerBooster = rocket.getStage(1);
		final BodyTube centerBoosterBody = (BodyTube) centerBooster.getChild(0);

		final ParallelStage sideBoosters = (ParallelStage) centerBoosterBody.getChild(1);
		final BodyTube sideBoosterBodies = (BodyTube) sideBoosters.getChild(1);
		final Parachute sideChutes = (Parachute) sideBoosterBodies.getChild(0);

		SimulationAbort simAbort = new SimulationAbort(SimulationAbort.Cause.TUMBLE_UNDER_THRUST);
		
		Warning warn = new Warning.HighSpeedDeployment(53.2);
		warn.setSources(null);
		
		// events whose time is too variable to check are given a time of 1200
		for (int b = 0; b < actualBranchCount; b++) {
			FlightEvent[] expectedEvents = switch (b) {
				// Sustainer
				case 0 -> new FlightEvent[]{
						new FlightEvent(FlightEvent.Type.LAUNCH, 0.0, rocket),
						new FlightEvent(FlightEvent.Type.IGNITION, 0.0, sideBoosterBodies),
						new FlightEvent(FlightEvent.Type.IGNITION, 0.01, centerBoosterBody),
						new FlightEvent(FlightEvent.Type.LIFTOFF, 0.073, null),
						new FlightEvent(FlightEvent.Type.LAUNCHROD, 0.075, null),
						new FlightEvent(FlightEvent.Type.BURNOUT, 1.05, sideBoosterBodies),
						new FlightEvent(FlightEvent.Type.EJECTION_CHARGE, 1.05, sideBoosters),
						new FlightEvent(FlightEvent.Type.STAGE_SEPARATION, 1.05, sideBoosters),
						new FlightEvent(FlightEvent.Type.BURNOUT, 2.11, centerBoosterBody),
						new FlightEvent(FlightEvent.Type.EJECTION_CHARGE, 2.11, centerBooster),
						new FlightEvent(FlightEvent.Type.STAGE_SEPARATION, 2.11, centerBooster),
						new FlightEvent(FlightEvent.Type.IGNITION, 2.11, sustainerBody),
						new FlightEvent(FlightEvent.Type.TUMBLE, 2.37, null),
						new FlightEvent(FlightEvent.Type.SIM_ABORT, 1200, null, simAbort)
				};

				// Center Booster
				case 1 -> new FlightEvent[]{
						new FlightEvent(FlightEvent.Type.IGNITION, 0.01, centerBoosterBody),
						new FlightEvent(FlightEvent.Type.BURNOUT, 2.11, centerBoosterBody),
						new FlightEvent(FlightEvent.Type.EJECTION_CHARGE, 2.11, centerBooster),
						new FlightEvent(FlightEvent.Type.STAGE_SEPARATION, 2.11, centerBooster),
						new FlightEvent(FlightEvent.Type.TUMBLE, 2.38, null),
						new FlightEvent(FlightEvent.Type.APOGEE, 3.89, rocket),
						new FlightEvent(FlightEvent.Type.GROUND_HIT, 1200, null),
						new FlightEvent(FlightEvent.Type.SIMULATION_END, 1200, null)
				};

				// Side Boosters
				case 2 -> new FlightEvent[]{
						new FlightEvent(FlightEvent.Type.IGNITION, 0.0, sideBoosterBodies),
						new FlightEvent(FlightEvent.Type.BURNOUT, 1.05, sideBoosterBodies),
						new FlightEvent(FlightEvent.Type.EJECTION_CHARGE, 1.05, sideBoosters),
						new FlightEvent(FlightEvent.Type.STAGE_SEPARATION, 1.05, sideBoosters),
						new FlightEvent(FlightEvent.Type.SIM_WARN, 1.05, null, warn),
						new FlightEvent(FlightEvent.Type.RECOVERY_DEVICE_DEPLOYMENT, 1.051, sideChutes),
						new FlightEvent(FlightEvent.Type.APOGEE, 1.35, rocket),
						new FlightEvent(FlightEvent.Type.GROUND_HIT, 1200, null),
						new FlightEvent(FlightEvent.Type.SIMULATION_END, 1200, null)
				};
				default -> throw new IllegalStateException("Invalid branch number " + b);
			};

			checkEvents(expectedEvents, sim, b);
		}
	}

	private void checkEvents(FlightEvent[] expectedEvents, Simulation sim, int branchNo) {

		FlightEvent[] actualEvents = sim.getSimulatedData().getBranch(branchNo).getEvents().toArray(new FlightEvent[0]);

		// Test that all expected events are present, in the right order, at the right
		// time, from the right sources
		for (int i = 0; i < Math.min(expectedEvents.length, actualEvents.length); i++) {
			final FlightEvent expected = expectedEvents[i];
			Warning expectedWarning = null;
			if (expected.getType() == FlightEvent.Type.SIM_WARN) {
				expectedWarning = (Warning) expected.getData();
			}
			
			final FlightEvent actual = actualEvents[i];
			Warning actualWarning = null;
			if (actual.getType() == FlightEvent.Type.SIM_WARN) {
				actualWarning = (Warning) actual.getData();
			}

			assertTrue(((expectedWarning == null) && (actualWarning == null)) ||
					   ((expectedWarning != null) && expectedWarning.equals(actualWarning)) ||
					   ((actualWarning != null) && actualWarning.equals(expectedWarning)),
					   "Branch " + branchNo + " FlightEvent " + i + ": " + expectedWarning
					   + " not found; " + actualWarning + " found instead");
			
			assertSame(expected.getType(), actual.getType(),
					   "Branch " + branchNo + " FlightEvent " + i + ": type " + expected.getType()
					   + " not found; FlightEvent " + actual.getType() + " found instead");

			if (1200 != expected.getTime()) {
				// event times that are dependent on simulation step time shouldn't be held to
				// tighter bounds than that
				double epsilon = (actual.getType() == FlightEvent.Type.TUMBLE) ||
						(actual.getType() == FlightEvent.Type.APOGEE) ||
						(actual.getType() == FlightEvent.Type.GROUND_HIT) ? (5 * sim.getOptions().getTimeStep())
						: EPSILON;
				assertEquals(expected.getTime(), actual.getTime(), epsilon,
						"Branch " + branchNo + " FlightEvent " + i + " type " + expected.getType() + " has wrong time ");
			}

			// Test that the event sources are correct
			assertEquals(expected.getSource(), actual.getSource(),
					"Branch " + branchNo + " FlightEvent " + i + " type " + expected.getType() + " has wrong source ");

			// If it's a warning event, make sure the warning types match
			if (expected.getType() == FlightEvent.Type.SIM_WARN) {
				assertInstanceOf(Warning.class, actual.getData(), "SIM_WARN event data is not a Warning");
				assertEquals((expected.getData()), actual.getData(), "Expected: " + expected.getData() + " but was: " + actual.getData());
			}
		}

		// Test event count
		assertEquals(expectedEvents.length, actualEvents.length, "Branch " + branchNo + " incorrect number of events ");
	}
}
