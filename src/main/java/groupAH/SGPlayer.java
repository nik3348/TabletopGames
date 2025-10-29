package groupAH;

import core.AbstractGameState;
import core.AbstractPlayer;
import core.actions.AbstractAction;

import java.util.List;

public class SGPlayer extends AbstractPlayer {

    public SGPlayer(SGPlayerParams params) {
        super(params, "SGPlayer");
    }

    public SGPlayer(SGPlayerParams params, String name) {
        super(params, name);
    }

    @Override
    public AbstractAction _getAction(AbstractGameState gameState, List<AbstractAction> possibleActions) {
        SGTreeNode root = new SGTreeNode(null, gameState, this);

        int iterations = 1000; // or use time limit
        for (int i = 0; i < iterations; i++) {
            root.runIteration();
        }

        return root.getBestAction();
    }

    @Override
    public AbstractPlayer copy() {
        return null;
    }
}
