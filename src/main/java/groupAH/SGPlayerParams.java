package groupAH;

import players.PlayerParameters;

public class SGPlayerParams extends PlayerParameters {

    @Override
    public SGPlayer instantiate() {
        return new SGPlayer(this);
    }
}
