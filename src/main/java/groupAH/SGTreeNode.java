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
    private final Map<AbstractAction, SGTreeNode> children = new HashMap<>();
    // --- Statistics ---
    private final int depth;
    private AbstractGameState state;
    private int fmCallsCount = 0;
    private int visitCount = 0;
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
        setState(state);
    }

    private void setState(AbstractGameState newState) {
        this.state = newState;
        if (newState.isNotTerminal()) {
            for (AbstractAction action : player.getForwardModel().computeAvailableActions(state, params.actionSpace)) {
                children.put(action, null); // mark a new node to be expanded
            }
        }
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
            if (node.state.isNotTerminal()) {
                node = node.expand();
            }

            // --- 3. Simulation ---
            double result = node.simulate();

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
        boolean iAmMoving = state.getCurrentPlayer() == player.getPlayerID();
        double perspective = iAmMoving ? 1.0 : -1.0;

        double exploitation = (child.value / child.visitCount) * perspective;
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

    private SGTreeNode select() {
        SGTreeNode bestChild = null;
        double bestValue = Double.NEGATIVE_INFINITY;

        for (SGTreeNode child : this.children.values()) {
            if (child == null) continue;

            if (child.visitCount == 0) {
                return child;
            }

            double ucbValue = getUCBValue(child);

            if (ucbValue > bestValue) {
                bestValue = ucbValue;
                bestChild = child;
            }
        }
        return bestChild;
    }

    private SGTreeNode expand() {
        List<AbstractAction> untriedMoves = getUntriedActions();
        if (untriedMoves.isEmpty()) {
            return null; // Should not happen if not a terminal node
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

            // Choose a random move
            AbstractAction next = possibleMoves.get(random.nextInt(possibleMoves.size()));
            advance(currentState, next);
            rolloutDepth++;
        }

        // Evaluate final state and return normalised score from the perspective of the player
        // whose turn it was at this.state
        double value = params.getStateHeuristic().evaluateState(currentState, player.getPlayerID());
        if (Double.isNaN(value)) throw new AssertionError("Illegal heuristic value - should be a number");
        return value * Math.pow(params.discountFactor, rolloutDepth) ;
    }

    private void backpropagate(double result) {
        SGTreeNode currentNode = this;
        while (currentNode != null) {
            currentNode.incrementVisitCounter();
            // The result needs to be handled based on whose turn it was.
            // If the parent is for the OTHER player, the result might be negated.
            // For simplicity here, we just add the value.
            currentNode.value += result;
            currentNode = currentNode.parent;
        }
    }
}
