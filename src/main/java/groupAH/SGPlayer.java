package groupAH;

import core.AbstractGameState;
import core.AbstractPlayer;
import core.actions.AbstractAction;
import utilities.ElapsedCpuTimer;

import java.util.List;

public class SGPlayer extends AbstractPlayer {
    private final SGPlayerParams params;

    public SGPlayer(SGPlayerParams params) {
        super(params, "SG Player");
        this.params = params;
    }

    @Override
    public AbstractAction _getAction(AbstractGameState state, List<AbstractAction> actions) {
        ElapsedCpuTimer elapsedTimerIteration = new ElapsedCpuTimer();
        SGTreeNode root = new SGTreeNode(this, null, state);
        root.mctsSearch();

        if (elapsedTimerIteration.elapsedMillis() > params.maxDecisionTimeMs) {
            System.err.println("Warning: MCTS Player exceeded time budget in getAction(): "
                    + elapsedTimerIteration.elapsedMillis() + "ms > "
                    + params.maxDecisionTimeMs + "ms");
        }

        return root.getBestAction();
    }

    @Override
    public String toString() {
        return super.toString();
    }

    @Override
    public SGPlayer copy() {
        return new SGPlayer((SGPlayerParams) parameters.copy());
    }
}
