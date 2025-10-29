package groupAH;

import core.AbstractGameState;
import core.AbstractPlayer;
import core.actions.AbstractAction;
import players.PlayerParameters;

import java.util.List;

public class SushiGoPlayer extends AbstractPlayer {

    public SushiGoPlayer(PlayerParameters params, String name) {
        super(params, name);
    }

    @Override
    public AbstractAction _getAction(AbstractGameState gameState, List<AbstractAction> possibleActions) {
        TreeNode root = new TreeNode(null, gameState, null);

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
