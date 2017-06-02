package bales;


import java.util.Vector;

/*****************************************************************
 * Minimizer for a given energy model, which is set by the constructor
 * of the concrete minimizer implementation.
 * @version  $Revision: 1.11 $; $Date: 2007/12/13 08:45:53 $
 * @author   Dirk Beyer
 *****************************************************************/
public abstract class Minimizer { 
    
    /**when chages occur in the graph*/
    protected Vector<GraphEventListener> listener = new Vector<GraphEventListener>();
    
    /**
     * Constructor
     * @param listener a GraphEventListener
     */
    public void addGraphEventListener(GraphEventListener listener){
        this.listener.add(listener);
    }
    
    /**
     * Minimizes iteratively the energy using the Barnes-Hut algorithm.
     * Starts from the layout given by the positions in <code>pos</code>, 
     * and stores the computed layout as positions in <code>pos</code>.
     * @param nrIterations  Number of iterations. Choose appropriate values
     *                      by observing the convergence of energy.
     */
    public abstract void minimizeEnergy(int nrIterations);

};