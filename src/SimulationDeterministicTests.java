import java.util.ArrayList;

public class SimulationDeterministicTests {

    /**
     * Runs all deterministic simulation checks.
     *
     * @param args CLI arguments (unused)
     */
    public static void main(String[] args) {
        testBridgeDualChannelOutput();
        testConvergerReceivesFromMirroredCurve();
        testOneInOneOutSameStepAndUnlimitedTankReceive();
        testCurvedPipeMirroredOrientation();
        testSplitterFallbackAndStatePersistence();
        testConvergerPriorityAndStatePersistence();
        System.out.println("All deterministic simulation tests passed.");
    }

    /**
     * Verifies that convergers can accept input sent from mirrored curved pipes.
     */
    private static void testConvergerReceivesFromMirroredCurve() {
        ArrayList<PipeComponent> parts = new ArrayList<>();

        PipeComponent source = new PipeComponent(1, 0, PipeComponent.ComponentType.TANK, PipeComponent.Orientation.SOUTH);
        source.tankAmount = 2;
        PipeComponent mirroredCurve = new PipeComponent(1, 1, PipeComponent.ComponentType.CURVED_PIPE, PipeComponent.Orientation.EAST_M);
        PipeComponent converger = new PipeComponent(2, 1, PipeComponent.ComponentType.CONVERGER, PipeComponent.Orientation.EAST);

        parts.add(source);
        parts.add(mirroredCurve);
        parts.add(converger);

        Simulation sim = new Simulation(parts);
        sim.step();
        sim.step();

        assertEquals("converger accepted from mirrored curve", 1, converger.amountSim);
    }

    /**
     * Verifies that a bridge can emit one unit on its north/south channel and one unit on its east/west channel in the same step.
     */
    private static void testBridgeDualChannelOutput() {
        ArrayList<PipeComponent> parts = new ArrayList<>();

        PipeComponent bridge = new PipeComponent(1, 1, PipeComponent.ComponentType.BRIDGE, PipeComponent.Orientation.NORTH);
        PipeComponent northSink = new PipeComponent(1, 0, PipeComponent.ComponentType.PIPE, PipeComponent.Orientation.NORTH);
        PipeComponent eastSink = new PipeComponent(2, 1, PipeComponent.ComponentType.PIPE, PipeComponent.Orientation.EAST);

        parts.add(bridge);
        parts.add(northSink);
        parts.add(eastSink);

        Simulation sim = new Simulation(parts);
        bridge.amountSim = 1;
        bridge.amountBridge = 1;

        sim.step();
        assertEquals("bridge primary channel sent north", 1, northSink.amountSim);
        assertEquals("bridge east/west channel sent east", 1, eastSink.amountSim);
        assertEquals("bridge primary channel emptied", 0, bridge.amountSim);
        assertEquals("bridge east/west channel emptied", 0, bridge.amountBridge);
    }

    /**
     * Verifies curved pipes route input/output correctly for both normal and mirrored orientations.
     */
    private static void testCurvedPipeMirroredOrientation() {
        ArrayList<PipeComponent> receiveParts = new ArrayList<>();

        PipeComponent source = new PipeComponent(0, 0, PipeComponent.ComponentType.TANK, PipeComponent.Orientation.EAST);
        source.tankAmount = 2;
        PipeComponent mirroredCurve = new PipeComponent(1, 0, PipeComponent.ComponentType.CURVED_PIPE, PipeComponent.Orientation.NORTH_M);
        PipeComponent blockedNorth = new PipeComponent(1, -1, PipeComponent.ComponentType.PIPE, PipeComponent.Orientation.NORTH);
        blockedNorth.amountSim = 1;

        receiveParts.add(source);
        receiveParts.add(mirroredCurve);
        receiveParts.add(blockedNorth);

        Simulation receiveSim = new Simulation(receiveParts);
        blockedNorth.amountSim = 1;
        receiveSim.step();
        assertEquals("mirrored curved pipe received from west", 1, mirroredCurve.amountSim);
        assertEquals("mirrored curved pipe output blocked", 1, blockedNorth.amountSim);

        ArrayList<PipeComponent> outputParts = new ArrayList<>();

        PipeComponent mirroredCurveWithFluid = new PipeComponent(1, 0, PipeComponent.ComponentType.CURVED_PIPE, PipeComponent.Orientation.NORTH_M);
        mirroredCurveWithFluid.amountSim = 1;
        PipeComponent northSink = new PipeComponent(1, -1, PipeComponent.ComponentType.PIPE, PipeComponent.Orientation.NORTH);

        outputParts.add(mirroredCurveWithFluid);
        outputParts.add(northSink);

        Simulation outputSim = new Simulation(outputParts);
        mirroredCurveWithFluid.amountSim = 1;
        outputSim.step();
        assertEquals("mirrored curved pipe sent north", 1, northSink.amountSim);
        assertEquals("mirrored curved pipe emptied after send", 0, mirroredCurveWithFluid.amountSim);
    }

    /**
     * Verifies that components can send and receive in the same step and tanks accept unlimited incoming fluid.
     */
    private static void testOneInOneOutSameStepAndUnlimitedTankReceive() {
        ArrayList<PipeComponent> parts = new ArrayList<>();

        PipeComponent leftTank = new PipeComponent(0, 0, PipeComponent.ComponentType.TANK, PipeComponent.Orientation.EAST);
        leftTank.tankAmount = 3;
        PipeComponent middlePipe = new PipeComponent(1, 0, PipeComponent.ComponentType.PIPE, PipeComponent.Orientation.EAST);
        PipeComponent rightPipe = new PipeComponent(2, 0, PipeComponent.ComponentType.PIPE, PipeComponent.Orientation.EAST);
        PipeComponent sinkTank = new PipeComponent(3, 0, PipeComponent.ComponentType.TANK, PipeComponent.Orientation.EAST);
        sinkTank.tankAmount = 0;

        parts.add(leftTank);
        parts.add(middlePipe);
        parts.add(rightPipe);
        parts.add(sinkTank);

        Simulation sim = new Simulation(parts);

        // Start middle as full to force simultaneous in+out behavior.
        middlePipe.amountSim = 1;

        sim.step();
        assertEquals("step1 middle unchanged due to in+out", 1, middlePipe.amountSim);
        assertEquals("step1 right received", 1, rightPipe.amountSim);

        sim.step();
        // Right pipe sends into sink tank while middle sends into right and receives from left.
        assertEquals("step2 sink tank received", 1, sinkTank.amountSim);
        assertEquals("step2 middle still balanced", 1, middlePipe.amountSim);
        assertEquals("step2 right still balanced", 1, rightPipe.amountSim);

        sim.step();
        // Tank has no cap and should continue accumulating.
        assertEquals("step3 sink tank accumulated", 2, sinkTank.amountSim);
    }

    /**
     * Verifies splitter fallback order and state persistence across steps.
     */
    private static void testSplitterFallbackAndStatePersistence() {
        ArrayList<PipeComponent> parts = new ArrayList<>();

        PipeComponent tank = new PipeComponent(0, 1, PipeComponent.ComponentType.TANK, PipeComponent.Orientation.EAST);
        tank.tankAmount = 6;

        PipeComponent splitter = new PipeComponent(1, 1, PipeComponent.ComponentType.SPLITTER, PipeComponent.Orientation.EAST);

        // East is splitter's primary output and starts blocked.
        PipeComponent eastBlocked = new PipeComponent(2, 1, PipeComponent.ComponentType.PIPE, PipeComponent.Orientation.EAST);
        PipeComponent southOut = new PipeComponent(1, 2, PipeComponent.ComponentType.PIPE, PipeComponent.Orientation.SOUTH);
        PipeComponent northOut = new PipeComponent(1, 0, PipeComponent.ComponentType.PIPE, PipeComponent.Orientation.NORTH);

        parts.add(tank);
        parts.add(splitter);
        parts.add(eastBlocked);
        parts.add(southOut);
        parts.add(northOut);

        Simulation sim = new Simulation(parts);

        // Pre-fill primary output to force splitter fallback to next candidate.
        eastBlocked.amountSim = 1;

        sim.step();
        // Tank sends to splitter; splitter cannot send yet.
        assertEquals("step1 splitter amount", 1, splitter.amountSim);
        assertEquals("step1 splitter state", 0, splitter.state);

        sim.step();
        // Primary (east) is full, so splitter should choose clockwise (south).
        assertEquals("step2 south received", 1, southOut.amountSim);
        assertEquals("step2 splitter balanced", 1, splitter.amountSim);
        assertEquals("step2 splitter state advanced from south", 2, splitter.state);

        sim.step();
        // With same-step in+out enabled, splitter now sends north and receives from tank in one tick.
        assertEquals("step3 north received", 1, northOut.amountSim);
        assertEquals("step3 splitter still balanced", 1, splitter.amountSim);
        assertEquals("step3 splitter state advanced from north", 0, splitter.state);

        sim.step();
        // All three outputs are now full in this fixture, so splitter cannot send and state persists.
        assertEquals("step4 south unchanged", 1, southOut.amountSim);
        assertEquals("step4 splitter unchanged", 1, splitter.amountSim);
        assertEquals("step4 splitter state persisted", 0, splitter.state);
    }

    /**
     * Verifies converger input arbitration and state progression across steps.
     */
    private static void testConvergerPriorityAndStatePersistence() {
        ArrayList<PipeComponent> parts = new ArrayList<>();

        // For converger oriented EAST, valid inputs are NORTH(CCW), WEST(opposite), SOUTH(CW).
        PipeComponent northSource = new PipeComponent(2, 1, PipeComponent.ComponentType.TANK, PipeComponent.Orientation.SOUTH);
        northSource.tankAmount = 8;
        PipeComponent westSource = new PipeComponent(1, 2, PipeComponent.ComponentType.TANK, PipeComponent.Orientation.EAST);
        westSource.tankAmount = 8;
        PipeComponent southSource = new PipeComponent(2, 3, PipeComponent.ComponentType.TANK, PipeComponent.Orientation.NORTH);
        southSource.tankAmount = 8;

        PipeComponent converger = new PipeComponent(2, 2, PipeComponent.ComponentType.CONVERGER, PipeComponent.Orientation.EAST);

        // Long output chain so converger can discharge repeatedly.
        PipeComponent out1 = new PipeComponent(3, 2, PipeComponent.ComponentType.CONVERGER, PipeComponent.Orientation.EAST);
        PipeComponent out2 = new PipeComponent(4, 2, PipeComponent.ComponentType.PIPE, PipeComponent.Orientation.EAST);
        PipeComponent out3 = new PipeComponent(5, 2, PipeComponent.ComponentType.PIPE, PipeComponent.Orientation.EAST);
        PipeComponent out4 = new PipeComponent(6, 2, PipeComponent.ComponentType.PIPE, PipeComponent.Orientation.EAST);
        PipeComponent out5 = new PipeComponent(7, 2, PipeComponent.ComponentType.PIPE, PipeComponent.Orientation.EAST);

        parts.add(northSource);
        parts.add(westSource);
        parts.add(southSource);
        parts.add(converger);
        parts.add(out1);
        parts.add(out2);
        parts.add(out3);
        parts.add(out4);
        parts.add(out5);

        Simulation sim = new Simulation(parts);

        // Step 1: all three sources can offer; initial preferred input is CCW (north).
        sim.step();
        assertEquals("step1 converger keeps 1 unit", 1, converger.amountSim);
        assertEquals("step1 converger next state", 1, converger.state);
        assertEquals("step1 north spent 1", 7, northSource.amountSim);
        assertEquals("step1 west unchanged", 8, westSource.amountSim);
        assertEquals("step1 south unchanged", 8, southSource.amountSim);

        // Step 2: converger can send out and receive in same step.
        // Preferred input with state=1 is opposite (west).
        sim.step();
        assertEquals("step2 converger still 1", 1, converger.amountSim);
        assertEquals("step2 converger next state", 2, converger.state);
        assertEquals("step2 west spent 1", 7, westSource.amountSim);
        assertEquals("step2 north unchanged", 7, northSource.amountSim);
        assertEquals("step2 south unchanged", 8, southSource.amountSim);

        // Step 3: with recursive in+out support, converger can now discharge and refill from CW (south).
        sim.step();
        assertEquals("step3 converger still 1", 1, converger.amountSim);
        assertEquals("step3 converger next state", 0, converger.state);
        assertEquals("step3 south spent 1", 7, southSource.amountSim);

        // Step 4: state is now 0, so converger should select CCW input (north).
        sim.step();
        assertEquals("step4 converger still 1", 1, converger.amountSim);
        assertEquals("step4 converger next state", 1, converger.state);
        assertEquals("step4 north spent again", 6, northSource.amountSim);
    }

    /**
     * Simple assertion helper for integer comparisons.
     *
     * @param label description of the assertion
     * @param expected expected value
     * @param actual actual value
     */
    private static void assertEquals(String label, int expected, int actual) {
        if(expected != actual) {
            throw new IllegalStateException(label + " expected=" + expected + " actual=" + actual);
        }
    }
}
