package bales;


import java.util.EventListener;

/**
 * Interface for classes that receive GraphEvent (i.e. the graph has changed)
 * The purpose of this interface is providing a clean way 
 * to animate the graph while minimizing it. 
 * @version  $Revision: 1.4 $; $Date: 2007/12/13 08:45:53 $
 * @author Damien Zufferey
 */
public interface GraphEventListener extends EventListener {

    public void onGraphEvent(GraphEvent evt);
}