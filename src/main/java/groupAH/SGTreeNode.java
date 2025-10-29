package groupAH;

import core.AbstractGameState;
import core.actions.AbstractAction;

import java.util.*;

/**
 * TreeNode for Monte Carlo Tree Search (MCTS)
 */
public class SGTreeNode {
    protected SGPlayerParams params;

    // --- Tree structure ---
    SGTreeNode parent;
    Map<AbstractAction, SGTreeNode> children = new HashMap<>();

    // --- Game info ---
    final AbstractGameState state;
    final SGPlayer player;
    final int depth;

    // --- MCTS statistics ---
    int visitCount;
    double totalValue;

    // --- Config ---
    static final double EXPLORATION_CONSTANT = Math.sqrt(2);

    public SGTreeNode(SGTreeNode parent, AbstractGameState state, SGPlayer player) {
        this.parent = parent;
        this.state = state;
        this.player = player;

        if (parent != null) this.depth = parent.depth + 1;
        else this.depth = 0;
    }

    // ------------------------------
    // 1. Selection
    // ------------------------------
    SGTreeNode select() {
        SGTreeNode node = this;
        while (!node.state.isGameOver() && node.isFullyExpanded()) {
            node = node.children.values().stream().max(Comparator.comparingDouble(n -> n.getUCB1(EXPLORATION_CONSTANT))).orElseThrow();
        }
        return node;
    }

    // ------------------------------
    // 2. Expansion
    // ------------------------------
    SGTreeNode expand() {
        if (state.isGameOver()) return this;

        List<AbstractAction> possibleActions = player.getForwardModel().computeAvailableActions(state, params.actionSpace);
        Set<AbstractAction> triedActions = children.keySet();

        // Find untried actions
        List<AbstractAction> untried = new ArrayList<>();
        for (AbstractAction a : possibleActions) {
            if (!triedActions.contains(a)) {
                untried.add(a);
            }
        }

        if (untried.isEmpty()) return this; // fully expanded

        // Pick a random untried action
        AbstractAction action = untried.get(new Random().nextInt(untried.size()));

        // Generate next state
        AbstractGameState nextState = state.copy();
        this.player.getForwardModel().next(nextState, action);

        // Create child node
        SGTreeNode child = new SGTreeNode(this, nextState, this.player); children.put(action, child);

        return child;
    }

    // ------------------------------
    // 3. Simulation (Rollout)
    // ------------------------------
    double simulate() {
        AbstractGameState simState = state.copy();

        // Rollout until terminal state
        while (!simState.isGameOver()) {
            List<AbstractAction> actions = player.getForwardModel().computeAvailableActions(simState, params.actionSpace);
            if (actions.isEmpty()) break;
            AbstractAction randomAction = actions.get(new Random().nextInt(actions.size()));
            this.player.getForwardModel().next(simState, randomAction);
        }

        // Return reward from perspective of root player (index 0)
        return simState.getHeuristicScore(0);
    }

    // ------------------------------
    // 4. Backpropagation
    // ------------------------------
    void backpropagate(double reward) {
        SGTreeNode node = this;
        while (node != null) {
            node.visitCount++;
            node.totalValue += reward;
            node = node.parent;
        }
    }

    // ------------------------------
    // UCB1 Formula
    // ------------------------------
    double getUCB1(double explorationConstant) {
        if (visitCount == 0) return Double.POSITIVE_INFINITY;
        double meanValue = totalValue / visitCount;
        double explorationTerm = explorationConstant * Math.sqrt(Math.log(parent.visitCount + 1.0) / visitCount);
        return meanValue + explorationTerm;
    }

    boolean isFullyExpanded() {
        List<AbstractAction> possible = player.getForwardModel().computeAvailableActions(state, params.actionSpace);
        return possible != null && children.size() == possible.size();
    }

    // ------------------------------
    // Helper to run one full MCTS iteration
    // ------------------------------
    public void runIteration() {
        SGTreeNode selected = select();
        SGTreeNode expanded = selected.expand();
        double reward = expanded.simulate();
        expanded.backpropagate(reward);
    }

    // ------------------------------
    // Get best action after search
    // ------------------------------
    public AbstractAction getBestAction() {
        return children.entrySet().stream().max(Comparator.comparingInt(e -> e.getValue().visitCount)).map(Map.Entry::getKey).orElse(null);
    }

    @Override
    public String toString() {
        return "TreeNode{" + "depth=" + depth + ", visits=" + visitCount + ", totalValue=" + totalValue + ", children=" + children.size() + '}';
    }
}
