package groupAH;

import core.AbstractGameState;
import core.components.Deck;
import core.interfaces.IStateHeuristic;
import games.sushigo.SGGameState;
import games.sushigo.cards.SGCard;

import java.util.Arrays;

public class SGHeuristic implements IStateHeuristic {

    @Override
    public double evaluateState(AbstractGameState gs, int playerId) {
        SGGameState state = (SGGameState) gs;

        // --- 1. Lead over other players (score + estimated bonuses) ---
        double leadHeuristic = getLeadHeuristic(state, playerId);

        // --- 2. Partial value of Maki cards in hand (1 < 2 < 3) ---
        double makiValue = getHandMakiValue(state, playerId);

        // --- 3. Estimated Pudding bonus mid-round ---
        double puddingBonus = getEstimatedPuddingBonus(state, playerId);

        // --- Total heuristic ---
        return leadHeuristic + makiValue + puddingBonus;
    }

    // --- Lead heuristic: playerScore - maxOtherScore (including estimated Maki/Pudding) ---
    private double getLeadHeuristic(SGGameState state, int playerId) {
        int n = state.getNPlayers();
        double[] adjustedScores = new double[n];

        for (int i = 0; i < n; i++) {
            double baseScore = state.getPlayerScore()[i].getValue();
            double makiBonus = getEstimatedMakiBonus(state, i);
            double puddingBonus = getEstimatedPuddingBonus(state, i);
            adjustedScores[i] = baseScore + makiBonus + puddingBonus;
        }

        double playerScore = adjustedScores[playerId];

        // Find the leader among other players
        double maxOther = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            if (i != playerId && adjustedScores[i] > maxOther) {
                maxOther = adjustedScores[i];
            }
        }

        return playerScore - maxOther;
    }

    // --- Value Maki cards in hand (1 < 2 < 3 rolls) ---
    private double getHandMakiValue(SGGameState state, int playerId) {
        Deck<SGCard> hand = state.getPlayedCards().get(playerId);
        double bonus = 0.0;

        for (SGCard card : hand) {
            if (card.type == SGCard.SGCardType.Maki) {
                bonus += 0.01 * card.count;
            }
        }

        return bonus;
    }

    // --- Estimate Maki bonus mid-round ---
    private double getEstimatedMakiBonus(SGGameState state, int playerId) {
        if (!state.isNotTerminal()) return 0.0;

        int n = state.getNPlayers();
        int[] makiCounts = new int[n];

        for (int i = 0; i < n; i++) {
            makiCounts[i] = state.getPlayedCardTypes(SGCard.SGCardType.Maki, i).getValue();
        }

        int playerMaki = makiCounts[playerId];
        int maxMaki = Arrays.stream(makiCounts).max().orElse(0);
        int secondMax = Arrays.stream(makiCounts).filter(x -> x < maxMaki).max().orElse(0);

        if (playerMaki == maxMaki && maxMaki > 0) return 6.0;
        if (playerMaki == secondMax && secondMax > 0) return 3.0;

        return 0.0;
    }

    // --- Estimate Pudding bonus mid-round ---
    private double getEstimatedPuddingBonus(SGGameState state, int playerId) {
        if (!state.isNotTerminal()) return 0.0;

        int n = state.getNPlayers();
        int[] puddingCounts = new int[n];

        for (int i = 0; i < n; i++) {
            puddingCounts[i] = state.getPlayedCardTypesAllGame()[i].get(SGCard.SGCardType.Pudding).getValue();
        }

        int max = Arrays.stream(puddingCounts).max().orElse(0);
        int min = Arrays.stream(puddingCounts).min().orElse(0);

        int playerValue = puddingCounts[playerId];
        if (playerValue == max && max > min) return 6.0;
        if (playerValue == min && max > min) return -6.0;

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
