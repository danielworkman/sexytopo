package org.hwyl.sexytopo.model.sketch;

import org.hwyl.sexytopo.model.graph.Coord2D;

/**
 * Created by rls on 01/06/15.
 */
public class TextDetail extends SinglePositionDetail {

    private final String text;

    public TextDetail(Coord2D location, String text, Colour colour) {
        super(colour, location);
        this.text = text;
    }

    public String getText() {
        return text;
    }

}
