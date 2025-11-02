package groupAH;

import core.AbstractGameState;
import core.interfaces.IStateHeuristic;
import games.sushigo.SGGameState;
import games.sushigo.cards.SGCard;

import java.util.Arrays;

public class SGHeuristic implements IStateHeuristic {
    @Override
    public double evaluateState(AbstractGameState gs, int playerId) {
        SGGameState state = (SGGameState) gs;
        return getRobustLeaderHeuristic(state, playerId);
    }

    private double getRobustLeaderHeuristic(SGGameState state, int playerId) {
        int n = state.getNPlayers();

        // --- Step 1: Compute adjusted scores with potential bonuses ---
        double[] adjustedScores = new double[n];
        for (int i = 0; i < n; i++) {
            double baseScore = state.getPlayerScore()[i].getValue();
            double makiBonus = getEstimatedMakiBonus(state, i);
            double puddingBonus = getEstimatedPuddingBonus(state, i);
            adjustedScores[i] = baseScore + makiBonus + puddingBonus;
        }

        double playerScore = adjustedScores[playerId];

        // --- Step 2: Find the leader among other players ---
        double maxOtherScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            if (i == playerId) continue;
            if (adjustedScores[i] > maxOtherScore) maxOtherScore = adjustedScores[i];
        }

        // --- Step 3: Compute gap to leader ---
        return playerScore - maxOtherScore;
    }

    private double getEstimatedMakiBonus(SGGameState state, int playerId) {
        if (!state.isNotTerminal()) return 0;

        int[] makiCounts = new int[state.getNPlayers()];
        for (int i = 0; i < state.getNPlayers(); i++) {
            makiCounts[i] = state.getPlayedCardTypes()[i].get(SGCard.SGCardType.Maki).getValue();
        }

        int playerMaki = makiCounts[playerId];
        int maxMaki = Arrays.stream(makiCounts).max().orElse(0);
        int secondMaxMaki = Arrays.stream(makiCounts).filter(x -> x < maxMaki).max().orElse(0);

        if (playerMaki == maxMaki && maxMaki > 0) return 6.0;
        if (playerMaki == secondMaxMaki && secondMaxMaki > 0) return 3.0;
        return 0.0;
    }

    private double getEstimatedPuddingBonus(SGGameState state, int playerId) {
        if (!state.isNotTerminal()) return 0;

        int[] puddingCounts = new int[state.getNPlayers()];
        for (int i = 0; i < state.getNPlayers(); i++) {
            puddingCounts[i] = state.getPlayedCardTypesAllGame()[i].get(SGCard.SGCardType.Pudding).getValue();
        }

        int playerPudding = puddingCounts[playerId];
        int maxPudding = Arrays.stream(puddingCounts).max().orElse(0);
        int minPudding = Arrays.stream(puddingCounts).min().orElse(0);

        if (playerPudding == maxPudding && maxPudding > minPudding) return 6.0;
        if (playerPudding == minPudding && maxPudding > minPudding) return -6.0;
        return 0.0;
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
