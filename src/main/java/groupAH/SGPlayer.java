package groupAH;

import core.AbstractGameState;
import core.AbstractPlayer;
import core.actions.AbstractAction;

import java.util.List;

public class SGPlayer extends AbstractPlayer {

    public SGPlayer(SGPlayerParams params) {
        super(params, "SG Player");
    }

    @Override
    public AbstractAction _getAction(AbstractGameState state, List<AbstractAction> actions) {
        SGTreeNode root = new SGTreeNode(this, null, state);
        root.mctsSearch();
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
