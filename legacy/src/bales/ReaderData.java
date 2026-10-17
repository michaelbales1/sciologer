package bales;


import java.io.BufferedReader;

/*****************************************************************
 * Reader for input data.
 * @version  $Revision: 1.7 $; $Date: 2007/12/13 08:45:53 $
 * @author   Dirk Beyer
 *****************************************************************/
public abstract class ReaderData {
  /** Input stream reader object. */
  protected BufferedReader in;

  /**
   * Constructor.
   * @param in  Stream reader object.
   */
  public ReaderData(BufferedReader in) {
    this.in = in;
  }

  /*****************************************************************
   * Reads the graph or layout data from stream reader <code>in</code>.
   * @param graph  <code>GraphData</code> object to store the read graph or layout data in.
   *****************************************************************/
  abstract public void read(GraphData graph);

};
