import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Simulation {

    private enum Channel {
        PRIMARY,
        BRIDGE
    }

    public ArrayList<PipeComponent> components;

    /**
     * Creates a simulation over the provided component list.
     *
     * @param components components participating in simulation
     */
    public Simulation(ArrayList<PipeComponent> components) {
        this.components = components;
        initialize();
    }

    /**
     * Resets simulation state and loads initial tank amounts.
     */
    public void initialize() {
        for(PipeComponent pc : components) {
            pc.amountSim = pc.amountBridge = pc.state = 0;
            if(pc.type == PipeComponent.ComponentType.TANK) {
                pc.amountSim = pc.tankAmount;
            }
        }
    }

    /**
     * Advances simulation by one staged tick.
     *
     * @return true when no fluid moved during this step, otherwise false
     */
    public boolean step() {
        Map<PipeComponent, Integer> snapshotPrimary = new HashMap<>();
        Map<PipeComponent, Integer> snapshotBridge = new HashMap<>();
        Map<PipeComponent, Integer> incomingPrimary = new HashMap<>();
        Map<PipeComponent, Integer> incomingBridge = new HashMap<>();
        Map<PipeComponent, Integer> nextState = new HashMap<>();
        Map<PipeComponent, PipeComponent.Orientation> plannedPrimarySendDirection = new HashMap<>();
        Map<PipeComponent, PipeComponent.Orientation> plannedBridgeSendDirection = new HashMap<>();
        Map<PipeComponent, Boolean> sentPrimary = new HashMap<>();
        Map<PipeComponent, Boolean> sentBridge = new HashMap<>();
        boolean noChanges = true;

        for(PipeComponent pc : components) {
            snapshotPrimary.put(pc, pc.amountSim);
            snapshotBridge.put(pc, pc.amountBridge);
            incomingPrimary.put(pc, 0);
            incomingBridge.put(pc, 0);
            nextState.put(pc, pc.state);
            sentPrimary.put(pc, false);
            sentBridge.put(pc, false);
        }

        for(PipeComponent sender : components) {
            if(!hasAnyFluid(sender, snapshotPrimary, snapshotBridge)) {
                continue;
            }

            if(sender.type == PipeComponent.ComponentType.BRIDGE) {
                if(attemptSend(sender, Channel.PRIMARY, snapshotPrimary, snapshotBridge, incomingPrimary, incomingBridge,
                        nextState, plannedPrimarySendDirection, plannedBridgeSendDirection, sentPrimary, sentBridge)) {
                    noChanges = false;
                }
                if(attemptSend(sender, Channel.BRIDGE, snapshotPrimary, snapshotBridge, incomingPrimary, incomingBridge,
                        nextState, plannedPrimarySendDirection, plannedBridgeSendDirection, sentPrimary, sentBridge)) {
                    noChanges = false;
                }
            } else {
                if(attemptSend(sender, Channel.PRIMARY, snapshotPrimary, snapshotBridge, incomingPrimary, incomingBridge,
                        nextState, plannedPrimarySendDirection, plannedBridgeSendDirection, sentPrimary, sentBridge)) {
                    noChanges = false;
                }
            }
        }

        for(PipeComponent pc : components) {
            int outPrimary = 0;
            int outBridge = 0;
            if(Boolean.TRUE.equals(sentPrimary.get(pc))) {
                outPrimary = 1;
            }
            if(Boolean.TRUE.equals(sentBridge.get(pc))) {
                outBridge = 1;
            }

            pc.amountSim = snapshotPrimary.get(pc) - outPrimary + incomingPrimary.get(pc);
            pc.amountBridge = snapshotBridge.get(pc) - outBridge + incomingBridge.get(pc);
            pc.state = nextState.get(pc);
        }

        return noChanges;
    }

    /**
     * Checks whether a component has fluid on any channel in the step snapshot.
     */
    private boolean hasAnyFluid(PipeComponent pc, Map<PipeComponent, Integer> snapshotPrimary,
                                Map<PipeComponent, Integer> snapshotBridge) {
        return snapshotPrimary.get(pc) + snapshotBridge.get(pc) > 0;
    }

    /**
     * Indicates whether a direction belongs to the east/west bridge channel.
     */
    private boolean usesBridgeChannel(PipeComponent.Orientation dir) {
        PipeComponent.Orientation normalized = normalizeDirection(dir);
        return normalized == PipeComponent.Orientation.EAST || normalized == PipeComponent.Orientation.WEST;
    }

    /**
     * Indicates whether a component uses its bridge storage for a direction.
     */
    private boolean usesBridgeChannelForComponent(PipeComponent pc, PipeComponent.Orientation dir) {
        return pc.type == PipeComponent.ComponentType.BRIDGE && usesBridgeChannel(dir);
    }

    /**
     * Checks if sender has fluid available for a chosen output direction.
     */
    private boolean hasFluidForDirection(PipeComponent sender, PipeComponent.Orientation dir,
                                         Map<PipeComponent, Integer> snapshotPrimary,
                                         Map<PipeComponent, Integer> snapshotBridge) {
        if(sender.type == PipeComponent.ComponentType.BRIDGE) {
            return usesBridgeChannel(dir) ? snapshotBridge.get(sender) > 0 : snapshotPrimary.get(sender) > 0;
        }
        return snapshotPrimary.get(sender) > 0;
    }

    /**
     * Attempts to send one unit from a component on the requested channel.
     */
    private boolean attemptSend(PipeComponent sender,
                                Channel channel,
                                Map<PipeComponent, Integer> snapshotPrimary,
                                Map<PipeComponent, Integer> snapshotBridge,
                                Map<PipeComponent, Integer> incomingPrimary,
                                Map<PipeComponent, Integer> incomingBridge,
                                Map<PipeComponent, Integer> nextState,
                                Map<PipeComponent, PipeComponent.Orientation> plannedPrimarySendDirection,
                                Map<PipeComponent, PipeComponent.Orientation> plannedBridgeSendDirection,
                                Map<PipeComponent, Boolean> sentPrimary,
                                Map<PipeComponent, Boolean> sentBridge) {
        if(hasAlreadySent(sender, channel, sentPrimary, sentBridge)) {
            return false;
        }

        Set<PipeComponent> visiting = new HashSet<>();
        visiting.add(sender);
        PipeComponent.Orientation outputDir = chooseOutputDirection(sender, channel, snapshotPrimary, snapshotBridge,
                incomingPrimary, incomingBridge, nextState, plannedPrimarySendDirection, plannedBridgeSendDirection,
                sentPrimary, sentBridge, true, visiting);
        if(outputDir == null) {
            return false;
        }

        PipeComponent receiver = getComponentInDirectionFrom(sender, outputDir);
        if(receiver == null || !canReceiveFrom(receiver, opposite(outputDir))) {
            return false;
        }

        PipeComponent.Orientation incomingDir = opposite(outputDir);
        if(receiver.type == PipeComponent.ComponentType.CONVERGER &&
                !convergerShouldAcceptFrom(receiver, incomingDir, snapshotPrimary, snapshotBridge, nextState,
                        plannedPrimarySendDirection, plannedBridgeSendDirection, sentPrimary, sentBridge)) {
            return false;
        }

        if(!hasFluidForDirection(sender, outputDir, snapshotPrimary, snapshotBridge)) {
            return false;
        }

        boolean receiverWillFreeOne = willFreeIncomingSlotThisStep(receiver, incomingDir, snapshotPrimary, snapshotBridge,
                incomingPrimary, incomingBridge, nextState, plannedPrimarySendDirection, plannedBridgeSendDirection,
                sentPrimary, sentBridge, true, visiting);

        if(!hasCapacityForIncoming(receiver, incomingDir, snapshotPrimary, snapshotBridge, incomingPrimary,
                incomingBridge, receiverWillFreeOne)) {
            return false;
        }

        boolean receiverBridgeChannel = usesBridgeChannelForComponent(receiver, incomingDir);
        if(receiverBridgeChannel) {
            incomingBridge.put(receiver, incomingBridge.get(receiver) + 1);
        } else {
            incomingPrimary.put(receiver, incomingPrimary.get(receiver) + 1);
        }

        if(sender.type == PipeComponent.ComponentType.SPLITTER) {
            advanceSplitterStateAfterSend(sender, outputDir, nextState);
        }
        if(receiver.type == PipeComponent.ComponentType.CONVERGER) {
            advanceConvergerStateAfterReceive(receiver, incomingDir, nextState);
        }

        if(channel == Channel.BRIDGE) {
            sentBridge.put(sender, true);
            plannedBridgeSendDirection.put(sender, outputDir);
        } else {
            sentPrimary.put(sender, true);
            plannedPrimarySendDirection.put(sender, outputDir);
        }

        return true;
    }

    /**
     * Checks if receiver can accept one incoming unit for the direction and projected in-step traffic.
     */
    private boolean hasCapacityForIncoming(PipeComponent receiver, PipeComponent.Orientation incomingDir,
                                           Map<PipeComponent, Integer> snapshotPrimary,
                                           Map<PipeComponent, Integer> snapshotBridge,
                                           Map<PipeComponent, Integer> incomingPrimary,
                                           Map<PipeComponent, Integer> incomingBridge,
                                           boolean receiverWillFreeOne) {
        if(receiver.type == PipeComponent.ComponentType.TANK) {
            return true;
        }

        if(receiver.type == PipeComponent.ComponentType.BRIDGE) {
            if(usesBridgeChannel(incomingDir)) {
                int effectiveBridge = snapshotBridge.get(receiver) + incomingBridge.get(receiver) - (receiverWillFreeOne ? 1 : 0);
                return effectiveBridge < 1;
            }
            int effectivePrimary = snapshotPrimary.get(receiver) + incomingPrimary.get(receiver) - (receiverWillFreeOne ? 1 : 0);
            return effectivePrimary < 1;
        }

        int effectivePrimary = snapshotPrimary.get(receiver) + incomingPrimary.get(receiver) - (receiverWillFreeOne ? 1 : 0);
        return effectivePrimary < 1;
    }

    /**
     * Checks whether receiver is expected to free one slot this step via an outgoing transfer.
     */
    private boolean willFreeIncomingSlotThisStep(PipeComponent receiver,
                                                 PipeComponent.Orientation incomingDir,
                                                 Map<PipeComponent, Integer> snapshotPrimary,
                                                 Map<PipeComponent, Integer> snapshotBridge,
                                                 Map<PipeComponent, Integer> incomingPrimary,
                                                 Map<PipeComponent, Integer> incomingBridge,
                                                 Map<PipeComponent, Integer> nextState,
                                                 Map<PipeComponent, PipeComponent.Orientation> plannedPrimarySendDirection,
                                                 Map<PipeComponent, PipeComponent.Orientation> plannedBridgeSendDirection,
                                                 Map<PipeComponent, Boolean> sentPrimary,
                                                 Map<PipeComponent, Boolean> sentBridge,
                                                 boolean allowPrediction,
                                                 Set<PipeComponent> visiting) {
        PipeComponent.Orientation outputDir;
        Channel channel = usesBridgeChannelForComponent(receiver, incomingDir) ? Channel.BRIDGE : Channel.PRIMARY;
        if(hasAlreadySent(receiver, channel, sentPrimary, sentBridge)) {
            outputDir = channel == Channel.BRIDGE ? plannedBridgeSendDirection.get(receiver) : plannedPrimarySendDirection.get(receiver);
        } else if(allowPrediction && !visiting.contains(receiver)) {
            Set<PipeComponent> nextVisiting = new HashSet<>(visiting);
            nextVisiting.add(receiver);
            outputDir = chooseOutputDirection(receiver, channel, snapshotPrimary, snapshotBridge, incomingPrimary, incomingBridge,
                    nextState, plannedPrimarySendDirection, plannedBridgeSendDirection, sentPrimary, sentBridge, true,
                    nextVisiting);
        } else {
            outputDir = null;
        }

        if(outputDir == null) {
            return false;
        }

        if(receiver.type == PipeComponent.ComponentType.BRIDGE) {
            return usesBridgeChannel(outputDir) == usesBridgeChannel(incomingDir);
        }
        return true;
    }

    /**
     * Selects a sender output direction under snapshot constraints.
     */
    private PipeComponent.Orientation chooseOutputDirection(PipeComponent sender,
                                                            Channel channel,
                                                            Map<PipeComponent, Integer> snapshotPrimary,
                                                            Map<PipeComponent, Integer> snapshotBridge,
                                                            Map<PipeComponent, Integer> incomingPrimary,
                                                            Map<PipeComponent, Integer> incomingBridge,
                                                            Map<PipeComponent, Integer> nextState,
                                                            Map<PipeComponent, PipeComponent.Orientation> plannedPrimarySendDirection,
                                                            Map<PipeComponent, PipeComponent.Orientation> plannedBridgeSendDirection,
                                                            Map<PipeComponent, Boolean> sentPrimary,
                                                            Map<PipeComponent, Boolean> sentBridge,
                                                            boolean allowReceiverFreePrediction,
                                                            Set<PipeComponent> visiting) {
        PipeComponent.Orientation[] candidates = outputCandidatesForState(sender, nextState.get(sender), channel);
        for(PipeComponent.Orientation dir : candidates) {
            if(dir == null || !hasFluidForDirection(sender, dir, snapshotPrimary, snapshotBridge)) {
                continue;
            }

            PipeComponent receiver = getComponentInDirectionFrom(sender, dir);
            if(receiver == null) {
                continue;
            }

            PipeComponent.Orientation incomingDir = opposite(dir);
            if(!canReceiveFrom(receiver, incomingDir)) {
                continue;
            }

            if(receiver.type == PipeComponent.ComponentType.CONVERGER &&
                    !convergerShouldAcceptFrom(receiver, incomingDir, snapshotPrimary, snapshotBridge, nextState,
                    plannedPrimarySendDirection, plannedBridgeSendDirection, sentPrimary, sentBridge)) {
                continue;
            }

            boolean receiverWillFreeOne = willFreeIncomingSlotThisStep(receiver, incomingDir, snapshotPrimary, snapshotBridge,
                    incomingPrimary, incomingBridge, nextState, plannedPrimarySendDirection, plannedBridgeSendDirection,
                    sentPrimary, sentBridge, allowReceiverFreePrediction, visiting);

            if(hasCapacityForIncoming(receiver, incomingDir, snapshotPrimary, snapshotBridge, incomingPrimary,
                    incomingBridge, receiverWillFreeOne)) {
                return dir;
            }
        }
        return null;
    }

    /**
     * Produces ordered output candidates for a component using current state.
     */
    private PipeComponent.Orientation[] outputCandidatesForState(PipeComponent pc, int state, Channel channel) {
        switch(pc.type) {
            case TANK, PIPE, CURVED_PIPE, CONVERGER:
                return channel == Channel.BRIDGE
                        ? new PipeComponent.Orientation[0]
                        : new PipeComponent.Orientation[]{pc.orientation};
            case SPLITTER:
                if(channel == Channel.BRIDGE) {
                    return new PipeComponent.Orientation[0];
                }
                PipeComponent.Orientation[] dirs = new PipeComponent.Orientation[]{
                        pc.orientation,
                        clockwise(pc.orientation),
                        counterClockwise(pc.orientation)
                };
                int start = state % 3;
                return new PipeComponent.Orientation[]{
                        dirs[start],
                        dirs[(start + 1) % 3],
                        dirs[(start + 2) % 3]
                };
            case BRIDGE:
                return channel == Channel.BRIDGE
                    ? new PipeComponent.Orientation[]{PipeComponent.Orientation.EAST, PipeComponent.Orientation.WEST}
                    : new PipeComponent.Orientation[]{PipeComponent.Orientation.NORTH, PipeComponent.Orientation.SOUTH};
        }
        return new PipeComponent.Orientation[0];
    }

    /**
     * Checks if a converger should accept a unit from the given incoming side.
     */
    private boolean convergerShouldAcceptFrom(PipeComponent converger, PipeComponent.Orientation incomingDir,
                                              Map<PipeComponent, Integer> snapshotPrimary,
                                              Map<PipeComponent, Integer> snapshotBridge,
                              Map<PipeComponent, Integer> nextState,
                              Map<PipeComponent, PipeComponent.Orientation> plannedPrimarySendDirection,
                              Map<PipeComponent, PipeComponent.Orientation> plannedBridgeSendDirection,
                              Map<PipeComponent, Boolean> sentPrimary,
                              Map<PipeComponent, Boolean> sentBridge) {
        PipeComponent.Orientation preferred = preferredConvergerIncomingDirection(converger, snapshotPrimary, snapshotBridge,
            nextState, plannedPrimarySendDirection, plannedBridgeSendDirection, sentPrimary, sentBridge);
        return preferred != null && normalizeDirection(preferred) == normalizeDirection(incomingDir);
    }

    /**
     * Chooses the preferred converger input side based on offers and current state.
     */
    private PipeComponent.Orientation preferredConvergerIncomingDirection(PipeComponent converger,
                                                                          Map<PipeComponent, Integer> snapshotPrimary,
                                                                          Map<PipeComponent, Integer> snapshotBridge,
                                                                          Map<PipeComponent, Integer> nextState,
                                                                          Map<PipeComponent, PipeComponent.Orientation> plannedPrimarySendDirection,
                                                                          Map<PipeComponent, PipeComponent.Orientation> plannedBridgeSendDirection,
                                                                          Map<PipeComponent, Boolean> sentPrimary,
                                                                          Map<PipeComponent, Boolean> sentBridge) {
        PipeComponent.Orientation[] orderedInputs = orderedConvergerInputDirections(converger, nextState.get(converger));
        for(PipeComponent.Orientation inputDir : orderedInputs) {
            PipeComponent offerer = getComponentInDirectionFrom(converger, inputDir);
            if(offerer == null) {
                continue;
            }

            PipeComponent.Orientation senderDir = opposite(inputDir);
            Channel channel = usesBridgeChannelForComponent(offerer, senderDir) ? Channel.BRIDGE : Channel.PRIMARY;
            if(hasAlreadySent(offerer, channel, sentPrimary, sentBridge)) {
                continue;
            }

            if(!hasFluidForDirection(offerer, senderDir, snapshotPrimary, snapshotBridge)) {
                continue;
            }

            PipeComponent.Orientation[] candidates = outputCandidatesForState(offerer, nextState.get(offerer), channel);
            for(PipeComponent.Orientation candidate : candidates) {
                if(normalizeDirection(candidate) == normalizeDirection(senderDir)) {
                    return inputDir;
                }
            }
        }
        return null;
    }

    /**
     * Checks whether a component has already sent on a specific channel this step.
     */
    private boolean hasAlreadySent(PipeComponent pc, Channel channel, Map<PipeComponent, Boolean> sentPrimary,
                                   Map<PipeComponent, Boolean> sentBridge) {
        return channel == Channel.BRIDGE ? Boolean.TRUE.equals(sentBridge.get(pc)) : Boolean.TRUE.equals(sentPrimary.get(pc));
    }

    /**
     * Returns converger input sides in state-priority order.
     */
    private PipeComponent.Orientation[] orderedConvergerInputDirections(PipeComponent converger, int state) {
        PipeComponent.Orientation[] inputDirs = new PipeComponent.Orientation[]{
                counterClockwise(converger.orientation),
                opposite(converger.orientation),
                clockwise(converger.orientation)
        };
        int start = state % 3;
        return new PipeComponent.Orientation[]{
                inputDirs[start],
                inputDirs[(start + 1) % 3],
                inputDirs[(start + 2) % 3]
        };
    }

    /**
     * Advances splitter state after a successful send.
     */
    private void advanceSplitterStateAfterSend(PipeComponent splitter, PipeComponent.Orientation sentDir,
                                               Map<PipeComponent, Integer> nextState) {
        PipeComponent.Orientation[] dirs = new PipeComponent.Orientation[]{
                splitter.orientation,
                clockwise(splitter.orientation),
                counterClockwise(splitter.orientation)
        };
        for(int i = 0; i < 3; ++i) {
            if(dirs[i] == sentDir) {
                nextState.put(splitter, (i + 1) % 3);
                return;
            }
        }
    }

    /**
     * Advances converger state after selecting a successful input.
     */
    private void advanceConvergerStateAfterReceive(PipeComponent converger, PipeComponent.Orientation inputDir,
                                                   Map<PipeComponent, Integer> nextState) {
        PipeComponent.Orientation[] inputDirs = new PipeComponent.Orientation[]{
                counterClockwise(converger.orientation),
                opposite(converger.orientation),
                clockwise(converger.orientation)
        };
        for(int i = 0; i < 3; ++i) {
            if(inputDirs[i] == inputDir) {
                nextState.put(converger, (i + 1) % 3);
                return;
            }
        }
    }

    /**
     * Checks whether a receiver accepts fluid from a side based on type and orientation.
     */
    private boolean canReceiveFrom(PipeComponent receiver, PipeComponent.Orientation incomingDir) {
        incomingDir = normalizeDirection(incomingDir);
        switch(receiver.type) {
            case TANK, PIPE, SPLITTER:
                return incomingDir == opposite(receiver.orientation);
            case CURVED_PIPE:
                return incomingDir == getCurvedPipeInputDirection(receiver);
            case CONVERGER:
                return incomingDir == counterClockwise(receiver.orientation) ||
                        incomingDir == opposite(receiver.orientation) ||
                        incomingDir == clockwise(receiver.orientation);
            case BRIDGE:
                return true;
        }
        return false;
    }

    /**
     * Returns the input side for a curved pipe based on whether it is mirrored.
     */
    private PipeComponent.Orientation getCurvedPipeInputDirection(PipeComponent pc) {
        return isMirroredOrientation(pc.orientation)
                ? counterClockwise(pc.orientation)
                : clockwise(pc.orientation);
    }

    /**
     * Converts mirrored directions to their cardinal equivalents for connectivity checks.
     */
    private PipeComponent.Orientation normalizeDirection(PipeComponent.Orientation orientation) {
        return switch(orientation) {
            case NORTH_M -> PipeComponent.Orientation.NORTH;
            case SOUTH_M -> PipeComponent.Orientation.SOUTH;
            case EAST_M -> PipeComponent.Orientation.EAST;
            case WEST_M -> PipeComponent.Orientation.WEST;
            default -> orientation;
        };
    }

    /**
     * Indicates whether an orientation is one of the mirrored curved-pipe variants.
     */
    private boolean isMirroredOrientation(PipeComponent.Orientation orientation) {
        return orientation == PipeComponent.Orientation.NORTH_M ||
                orientation == PipeComponent.Orientation.SOUTH_M ||
                orientation == PipeComponent.Orientation.EAST_M ||
                orientation == PipeComponent.Orientation.WEST_M;
    }

    /**
     * Rotates an orientation clockwise.
     */
    private PipeComponent.Orientation clockwise(PipeComponent.Orientation o) {
        return switch (o) {
            case NORTH, NORTH_M -> PipeComponent.Orientation.EAST;
            case SOUTH, SOUTH_M -> PipeComponent.Orientation.WEST;
            case EAST, EAST_M -> PipeComponent.Orientation.SOUTH;
            case WEST, WEST_M -> PipeComponent.Orientation.NORTH;
        };
    }

    /**
     * Rotates an orientation counterclockwise.
     */
    private PipeComponent.Orientation counterClockwise(PipeComponent.Orientation o) {
        return switch (o) {
            case NORTH, NORTH_M -> PipeComponent.Orientation.WEST;
            case SOUTH, SOUTH_M -> PipeComponent.Orientation.EAST;
            case EAST, EAST_M -> PipeComponent.Orientation.NORTH;
            case WEST, WEST_M -> PipeComponent.Orientation.SOUTH;
        };
    }

    /**
     * Returns the opposite direction.
     */
    private PipeComponent.Orientation opposite(PipeComponent.Orientation o) {
        return switch (o) {
            case NORTH, NORTH_M -> PipeComponent.Orientation.SOUTH;
            case SOUTH, SOUTH_M -> PipeComponent.Orientation.NORTH;
            case EAST, EAST_M -> PipeComponent.Orientation.WEST;
            case WEST, WEST_M -> PipeComponent.Orientation.EAST;
        };
    }

    /**
     * Finds the adjacent component one cell away from origin in the given direction.
     */
    private PipeComponent getComponentInDirectionFrom(PipeComponent origin, PipeComponent.Orientation direction) {
        if(direction == null) return null;
        int dx = origin.X;
        int dy = origin.Y;
        switch(direction) {
            case NORTH, NORTH_M -> --dy;
            case SOUTH, SOUTH_M -> ++dy;
            case EAST, EAST_M -> ++dx;
            case WEST, WEST_M -> --dx;
        }
        for(PipeComponent pc : components) {
            if(pc.X == dx && pc.Y == dy) {
                return pc;
            }
        }
        return null;
    }
}
