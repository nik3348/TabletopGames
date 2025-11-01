package groupAH;

import core.AbstractGameState;
import core.interfaces.IStateHeuristic;
import players.PlayerParameters;

import java.util.Arrays;

public class SGPlayerParams extends PlayerParameters {
    public double explorationParameter = Math.sqrt(2);
    public int rolloutLength = 10;
    public int maxTreeDepth = 5;
    public double epsilon = 1e-6;
    public double discountFactor = 1.0;
    public IStateHeuristic heuristic = AbstractGameState::getHeuristicScore;

    public SGPlayerParams() {
        addTunableParameter("explorationParameter", Math.sqrt(2), Arrays.asList(0.0, 0.1, 1.0, Math.sqrt(2), 3.0, 10.0));
        addTunableParameter("rolloutLength", 10, Arrays.asList(0, 3, 10, 30, 100));
        addTunableParameter("maxTreeDepth", 100, Arrays.asList(1, 3, 10, 30, 100));
        addTunableParameter("epsilon", 1e-6);
        addTunableParameter("discountFactor", 1.0, Arrays.asList(0.0, 0.5, 0.9, 0.95, 0.99, 1.0));
        addTunableParameter("heuristic", (IStateHeuristic) AbstractGameState::getHeuristicScore);
    }

    @Override
    public void _reset() {
        super._reset();
        explorationParameter = (double) getParameterValue("explorationParameter");
        rolloutLength = (int) getParameterValue("rolloutLength");
        maxTreeDepth = (int) getParameterValue("maxTreeDepth");
        epsilon = (double) getParameterValue("epsilon");
        discountFactor = (double) getParameterValue("discountFactor");
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
