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
    private int visitCount = 0;
    private double value = 0.0;

    public SGTreeNode(SGPlayer player, AbstractGameState state) {
        this.player = player;
        this.state = state;
        this.parent = null;
        this.root = this;
        this.depth = 0;
        this.params = (SGPlayerParams) player.getParameters();
        this.random = new Random(player.getParameters().getRandomSeed());

        for (AbstractAction action : player.getForwardModel().computeAvailableActions(state, params.actionSpace)) {
            children.put(action, null); // mark a new node to be expanded
        }
    }

    public SGTreeNode(SGPlayer player, SGTreeNode parent, List<AbstractAction> actions) {
        this.player = player;
        this.parent = parent;
        this.root = parent.root;
        this.depth = parent.depth + 1;
        this.state = null; // state is only stored at root

        this.params = (SGPlayerParams) player.getParameters();
        this.random = new Random(player.getParameters().getRandomSeed());

        for (AbstractAction action : actions) {
            children.put(action, null); // mark a new node to be expanded
        }
    }

    public AbstractGameState getStateCopy() {
        if (parent == null) return root.state.copy();
        throw new IllegalStateException("Only root node stores the game state");
    }

    private List<AbstractAction> getUntriedActions() {
        return children.keySet().stream().filter(a -> children.get(a) == null).toList();
    }

    private void advance(AbstractGameState state, AbstractAction action) {
        player.getForwardModel().next(state, action);
        root.incrementFMCounter();
    }

    public void incrementFMCounter() {
        fmCallsCount++;
    }

    public void incrementVisitCounter() {
        visitCount++;
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
            ElapsedCpuTimer elapsedTimerIteration = new ElapsedCpuTimer();
            SGTreeNode node = this;
            AbstractGameState simState = node.getStateCopy(); // root stores the real game state

            // --- 1. Selection ---
            while (node.depth < params.maxTreeDepth) {
                if (!node.getUntriedActions().isEmpty()) {
                    // This node has actions we haven't expanded yet. Stop selection.
                    break;
                }
                // This node is fully expanded, so select its best child and continue.
                node = node.select(simState);
            }

            // --- 2. Expansion ---
            if (simState.isNotTerminal()) {
                node = node.expand(simState);
            }

            // --- 3. Simulation ---
            double result = node.simulate(simState);

            // --- 4. Backpropagation ---
            node.backpropagate(result);
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
                lastFmCallCount = root.fmCallsCount; // Update for next iteration
            }
        }
    }

    private double getUCBValue(SGTreeNode child) {
        double exploitation = child.value / child.visitCount;
        double exploration = Math.sqrt(Math.log(this.visitCount) / child.visitCount);
        double ucbValue = exploitation + params.explorationParameter * exploration;
        return noise(ucbValue, params.epsilon, random.nextDouble());
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

    private SGTreeNode select(AbstractGameState simState) {
        SGTreeNode bestChild = null;
        boolean iAmMoving = simState.getCurrentPlayer() == player.getPlayerID();
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

    private SGTreeNode expand(AbstractGameState simState) {
        List<AbstractAction> untried = getUntriedActions();
        AbstractAction chosen = untried.get(random.nextInt(untried.size()));

        advance(simState, chosen);
        List<AbstractAction> actions = player.getForwardModel().computeAvailableActions(simState, params.actionSpace);
        SGTreeNode child = new SGTreeNode(player, this, actions);
        children.put(chosen, child);

        return child;
    }

    private double simulate(AbstractGameState simState) {
        int rolloutDepth = 0;
        while (simState.isNotTerminal() && rolloutDepth < params.rolloutLength) {
            List<AbstractAction> moves = player.getForwardModel().computeAvailableActions(simState, params.actionSpace);
            AbstractAction move = moves.get(random.nextInt(moves.size()));
            advance(simState, move);
            rolloutDepth++;
        }

        double value = params.getStateHeuristic().evaluateState(simState, player.getPlayerID());
        return value * Math.pow(params.discountFactor, rolloutDepth);
    }

    private void backpropagate(double result) {
        SGTreeNode currentNode = this;
        while (currentNode != null) {
            currentNode.incrementVisitCounter();
            currentNode.value += result;
            currentNode = currentNode.parent;
        }
    }
}
