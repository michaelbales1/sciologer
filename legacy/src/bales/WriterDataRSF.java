package bales;


import java.io.PrintWriter;

/*****************************************************************
 * Writer for co-change graphs in RSF format.
 * @version  $Revision: 1.22 $; $Date: 2007/12/13 08:45:54 $
 * @author   Dirk Beyer
 *****************************************************************/
public class WriterDataRSF extends WriterData {

  private PrintWriter out;

  public WriterDataRSF(GraphData graph,
                       PrintWriter out) {
    super(graph);
    this.out = out;
  }

  /*****************************************************************
   * Writes the graph data in RSF (relational standard format).
   *****************************************************************/
  public void write() {
    for (int i = 0; i < graph.edges.size(); ++i) {
      GraphEdgeInt e = graph.edges.get(i);
      out.print( e.relName + "\t" );
      out.print( graph.vertices.get(e.x).name + "\t" );
      out.print( graph.vertices.get(e.y).name );
      out.println();
    }
  }

};