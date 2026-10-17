package bales;


import java.util.EventObject;

/**
 * An Event class to notify that chages on the graph where made
 * used by Minimizer and/or subclasses
 * @version  $Revision: 1.3 $; $Date: 2007/12/13 08:45:53 $
 * @author Damien Zufferey
 */
public class GraphEvent extends EventObject {

    private static final long serialVersionUID = 200604171207L;


    /**
     * Constructor
     * @param source    the source of the event
     */
    public GraphEvent(Object source) {
        super(source);
    }

}