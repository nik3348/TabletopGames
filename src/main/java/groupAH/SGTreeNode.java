package groupAH;

import core.AbstractGameState;
import core.actions.AbstractAction;
import players.PlayerConstants;
import utilities.ElapsedCpuTimer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static players.PlayerConstants.*;
import static utilities.Utils.noise;

/**
 * TreeNode for Monte Carlo Tree Search (MCTS)
 */
public class SGTreeNode {
    private final SGPlayer player;
    private final SGPlayerParams params;
    private final Random random;
    // --- Tree structure ---
    private final SGTreeNode root;
    private final SGTreeNode parent;
    private final AbstractGameState state;
    private final Map<AbstractAction, SGTreeNode> children = new HashMap<>();
    // --- Statistics ---
    private final int depth;
    private int fmCallsCount = 0;
    private double visitCount = 0;
    private double value = 0.0;

    public SGTreeNode(SGPlayer player, SGTreeNode parent, AbstractGameState state) {
        this.player = player;
        this.parent = parent;

        if (parent == null) {
            this.root = this;
            this.depth = 0;
        } else {
            this.root = parent.root;
            this.depth = parent.depth + 1;
        }

        this.params = (SGPlayerParams) player.getParameters();
        this.random = new Random(player.getParameters().getRandomSeed());
        this.state = state;
        if (state.isNotTerminal()) {
            for (AbstractAction action : player.getForwardModel().computeAvailableActions(state, params.actionSpace)) {
                children.put(action, null); // mark a new node to be expanded
            }
        }
    }

    public void incrementFMCounter() {
        fmCallsCount++;
    }

    private List<AbstractAction> getUntriedActions() {
        return children.keySet().stream().filter(a -> children.get(a) == null).toList();
    }

    private void advance(AbstractGameState state, AbstractAction action) {
        player.getForwardModel().next(state, action);
        root.incrementFMCounter();
    }

    private double getUCBValue(SGTreeNode child) {
        double exploitation = child.value / child.visitCount;
        double exploration = Math.sqrt(Math.log(this.visitCount) / child.visitCount);
        double progressiveBias = params.getStateHeuristic().evaluateState(state, player.getPlayerID()) / (1 + child.visitCount);
        double ucbValue = exploitation + params.explorationParameter * exploration + progressiveBias;
        return noise(ucbValue, params.epsilon, random.nextDouble());
    }

    /**
     * Computes the feedback adjustment weight factor for the GBY variant.
     * <p>
     * GBY stands for:
     * G – exponential partition (segments grow exponentially),
     * B – exponential partition boundary,
     * Y – exponential weighting (later segments weighted more heavily).
     * <p>
     * This version adapts dynamically based on the current number of simulations,
     * without requiring a predefined total simulation count.
     * <p>
     * Reference:
     * [1] F. Xie and Z. Liu, “Backpropagation Modification in Monte-Carlo Game Tree Search,”
     * in 2009 Third International Symposium on Intelligent Information Technology Application,
     * 2009, pp. 125–128. doi: 10.1109/IITA.2009.331.
     *
     * @param simIndex the index (1-based) of the current simulation
     * @param K        number of segments (commonly 5)
     * @return the computed GBY weight factor
     */
    private double computeWeightFactor(int simIndex, int K) {
        double currentTotal = root.visitCount;

        if (simIndex < 1) simIndex = 1;
        if (currentTotal < 1) currentTotal = 1;

        // Compute ratio dynamically: how far are we in the current search?
        double ratio = (double) simIndex / (currentTotal + 1);

        // Exponential partition into K segments
        int segment = (int) Math.ceil(K * ratio);
        segment = Math.max(1, Math.min(segment, K));

        // Exponential weighting: later segments get higher weight
        return Math.pow(2, segment - 1);
    }

    public void mctsSearch() {
        boolean stop = false;
        int lastFmCallCount = -1;
        int iters = 0;

        // Variables for tracking time budget
        double avgTimeTaken;
        double acumTimeTaken = 0;
        long remaining;
        int remainingLimit = params.breakMS;
        ElapsedCpuTimer elapsedTimer = new ElapsedCpuTimer();
        PlayerConstants budgetType = params.budgetType;
        if (budgetType == BUDGET_TIME) {
            elapsedTimer.setMaxTimeMillis(params.budget);
        }

        while (!stop) {
            SGTreeNode node = this;
            ElapsedCpuTimer elapsedTimerIteration = new ElapsedCpuTimer();

            // --- 1. Selection ---
            while (node.state.isNotTerminal() && node.depth < params.maxTreeDepth) {
                if (!node.getUntriedActions().isEmpty()) {
                    // This node has actions we haven't expanded yet. Stop selection.
                    break;
                }
                // This node is fully expanded, so select its best child and continue.
                node = node.select();
            }

            // --- 2. Expansion ---
            if (node.state.isNotTerminal() && node.depth < params.maxTreeDepth) {
                node = node.expand();
            }

            // --- 3. Simulation ---
            double result = node.simulate();

            // --- 4. Backpropagation ---
            node.backpropagate(result, iters);
            iters++;

            // Check stopping condition
            if (budgetType == BUDGET_TIME) {
                // Time budget
                acumTimeTaken += (elapsedTimerIteration.elapsedMillis());
                avgTimeTaken = acumTimeTaken / iters;
                remaining = elapsedTimer.remainingTimeMillis();
                stop = remaining <= 2 * avgTimeTaken || remaining <= remainingLimit;
            } else if (budgetType == BUDGET_ITERATIONS) {
                // Iteration budget
                stop = iters >= params.budget;
            } else if (budgetType == BUDGET_FM_CALLS) {
                // FM calls budget
                stop = root.fmCallsCount > params.budget;

                if (root.fmCallsCount == lastFmCallCount) {
                    // The FM count did not increase. This means the
                    // entire tree is explored. We must stop.
                    stop = true;
                }

                // Update for next iteration
                lastFmCallCount = root.fmCallsCount;
            }
        }
    }

    public AbstractAction getBestAction() {
        AbstractAction bestAction = null;
        double bestValue = Double.NEGATIVE_INFINITY;

        for (Map.Entry<AbstractAction, SGTreeNode> entry : children.entrySet()) {
            SGTreeNode child = entry.getValue();
            if (child != null) {
                double childValue = getUCBValue(child);

                // Apply small noise to break ties randomly
                childValue = noise(childValue, params.epsilon, random.nextDouble());

                if (childValue > bestValue) {
                    bestValue = childValue;
                    bestAction = entry.getKey();
                }
            }
        }

        if (bestAction == null) {
            throw new AssertionError("Unexpected - no select made.");
        }

        return bestAction;
    }

    private SGTreeNode select() {
        SGTreeNode bestChild = null;
        boolean iAmMoving = state.getCurrentPlayer() == player.getPlayerID();
        double bestValue = iAmMoving ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;

        for (SGTreeNode child : children.values()) {
            if (child == null) continue;
            if (child.visitCount == 0) return child;

            double ucbValue = getUCBValue(child);

            if ((iAmMoving && ucbValue > bestValue) || (!iAmMoving && ucbValue < bestValue)) {
                bestValue = ucbValue;
                bestChild = child;
            }
        }

        return bestChild;
    }

    private SGTreeNode expand() {
        List<AbstractAction> untriedMoves = getUntriedActions();
        if (untriedMoves.isEmpty()) {
            // Should not happen if not a terminal node
            return null;
        }

        // Take one untried move
        AbstractAction chosen = untriedMoves.get(random.nextInt(untriedMoves.size()));

        // Create the new game state that results from this move
        AbstractGameState nextState = state.copy();
        advance(nextState, chosen.copy());

        // Create the new child node
        SGTreeNode childNode = new SGTreeNode(player, this, nextState);
        children.put(chosen, childNode);
        return childNode;
    }

    private double simulate() {
        int rolloutDepth = 0;
        AbstractGameState currentState = this.state.copy();

        // Loop until the game is over
        while (currentState.isNotTerminal() && rolloutDepth < params.rolloutLength) {
            List<AbstractAction> possibleMoves = player.getForwardModel().computeAvailableActions(currentState, player.parameters.actionSpace);
            AbstractAction next = possibleMoves.get(random.nextInt(possibleMoves.size()));
            advance(currentState, next);
            rolloutDepth++;
        }

        double value = params.getStateHeuristic().evaluateState(currentState, player.getPlayerID());
        if (Double.isNaN(value)) throw new AssertionError("Illegal heuristic value - should be a number");
        return value * Math.pow(params.discountFactor, rolloutDepth);
    }

    private void backpropagate(double result, int simIndex) {
        // Compute the weight factor
        double w = computeWeightFactor(simIndex, params.GBY_K);

        // Backpropagation
        SGTreeNode currentNode = this;
        while (currentNode != null) {
            currentNode.visitCount += 1;
            currentNode.value += result * w;
            currentNode = currentNode.parent;
        }
    }
}
