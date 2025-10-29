package groupAH;

import core.AbstractGameState;
import core.components.Counter;
import core.interfaces.IStateHeuristic;
import games.sushigo.SGGameState;

public class SushiGoHeuristic implements IStateHeuristic {
    @Override
    public double evaluateState(AbstractGameState gs, int playerId) {
        SGGameState state = (SGGameState) gs;
        int playerScore = state.getPlayerScore()[playerId].getValue();

        int maxScore = Integer.MIN_VALUE;
        int minScore = Integer.MAX_VALUE;

        for (Counter score : state.getPlayerScore()) {
            int val = score.getValue();
            if (val > maxScore) maxScore = val;
            if (val < minScore) minScore = val;
        }

        // Relative normalized heuristic mapped to [-1, 1]
        double relative = (playerScore - minScore) / (double)(maxScore - minScore + 1);
        double heuristic = 2 * relative - 1;

        return heuristic;
    }

    @Override
    public double minValue() {
        return IStateHeuristic.super.minValue();
    }

    @Override
    public double maxValue() {
        return IStateHeuristic.super.maxValue();
    }
}
