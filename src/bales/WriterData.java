package bales;


/*****************************************************************
 * Writer for output data.
 * @version  $Revision: 1.17 $; $Date: 2007/12/13 08:45:53 $
 * @author   Dirk Beyer
 *****************************************************************/
public abstract class WriterData {
  /** End of line.*/
  protected final static String endl = CCVisu.endl;

  /** Graph representation.*/
  protected GraphData graph;

  /**
   * Constructor.
   * @param graph  Graph representation.
   */
  public WriterData(GraphData graph) {
    this.graph = graph;
  }

  /*****************************************************************
   * Writes the graph or layout data.
   *****************************************************************/
  public abstract void write();

};
