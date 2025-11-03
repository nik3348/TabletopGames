package groupAH;

import core.AbstractGameState;
import core.interfaces.IStateHeuristic;
import players.PlayerConstants;
import players.PlayerParameters;

import java.util.Arrays;

public class SGPlayerParams extends PlayerParameters {
    public double explorationParameter = Math.sqrt(2);
    public double epsilon = 1e-6;
    public double discountFactor = 1.0;
    public double rolloutBiasTemp = 0.2; // Unused currently
    public double maxDecisionTimeMs = 1000.0;
    public int rolloutLength = 100;
    public int maxTreeDepth = 100;
    public int GBY_K = 5;
    public SGEnums.Policy actionPolicy = SGEnums.Policy.SIMPLE;
    public SGEnums.Policy selectionPolicy = SGEnums.Policy.SIMPLE;
    public IStateHeuristic heuristic = AbstractGameState::getHeuristicScore;

    public SGPlayerParams() {
        addTunableParameter("explorationParameter", Math.sqrt(2), Arrays.asList(0.0, 0.1, 1.0, Math.sqrt(2), 3.0, 10.0));
        addTunableParameter("epsilon", 1e-6, Arrays.asList(1e-10, 1e-8, 1e-6, 1e-4, 1e-2));
        addTunableParameter("discountFactor", 1.0, Arrays.asList(0.0, 0.5, 0.9, 0.95, 0.99, 1.0));
        addTunableParameter("rolloutBiasTemp", 0.2, Arrays.asList(0.0, 0.1, 0.2, 1.0, 2.0, 5.0));
        addTunableParameter("maxDecisionTimeMs", 1000.0, Arrays.asList(100.0, 300.0, 1000.0, 3000.0, 10000.0));
        addTunableParameter("rolloutLength", 100, Arrays.asList(0, 3, 10, 30, 100));
        addTunableParameter("maxTreeDepth", 100, Arrays.asList(1, 5, 10, 30, 100));
        addTunableParameter("GBY_K", 5, Arrays.asList(1, 5, 10, 30, 100));
        addTunableParameter("actionPolicy", SGEnums.Policy.SIMPLE, Arrays.asList(SGEnums.Policy.values()));
        addTunableParameter("selectionPolicy", SGEnums.Policy.SIMPLE, Arrays.asList(SGEnums.Policy.values()));
        addTunableParameter("heuristic", (IStateHeuristic) AbstractGameState::getHeuristicScore);
        addTunableParameter("budgetType", PlayerConstants.BUDGET_TIME, Arrays.asList(PlayerConstants.values()));
        addTunableParameter("budget", 1000, Arrays.asList(100, 300, 1000, 3000, 10000, 30000, 100000));
    }

    @Override
    public void _reset() {
        super._reset();
        explorationParameter = (double) getParameterValue("explorationParameter");
        epsilon = (double) getParameterValue("epsilon");
        discountFactor = (double) getParameterValue("discountFactor");
        rolloutBiasTemp = (double) getParameterValue("rolloutBiasTemp");
        maxDecisionTimeMs = (double) getParameterValue("maxDecisionTimeMs");
        rolloutLength = (int) getParameterValue("rolloutLength");
        maxTreeDepth = (int) getParameterValue("maxTreeDepth");
        GBY_K = (int) getParameterValue("GBY_K");
        actionPolicy = (SGEnums.Policy) getParameterValue("actionPolicy");
        selectionPolicy = (SGEnums.Policy) getParameterValue("selectionPolicy");
        heuristic = (IStateHeuristic) getParameterValue("heuristic");
    }

    @Override
    protected SGPlayerParams _copy() {
        // All the copying is done in TunableParameters.copy()
        // Note that any *local* changes of parameters will not be copied
        // unless they have been 'registered' with setParameterValue("name", value)
        return new SGPlayerParams();
    }

    @Override
    public IStateHeuristic getStateHeuristic() {
        return heuristic;
    }

    @Override
    public SGPlayer instantiate() {
        return new SGPlayer((SGPlayerParams) this.copy());
    }
}
