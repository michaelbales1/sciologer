package bales;


import java.io.PrintWriter;

/*****************************************************************
 * Writer for layouts in text format.
 * @version  $Revision: 1.23 $; $Date: 2008/04/21 $
 * @author   Michael Bales
 *****************************************************************/
public class WriterDataLAYBrief extends WriterData {

  private PrintWriter out;

  public WriterDataLAYBrief(GraphData graph, 
                       PrintWriter out) {
    super(graph);
    this.out = out;
  }

  /*****************************************************************
   * Writes the layout data in text format LAY.
   *****************************************************************/
  public void write() {
    for (int i = 0; i < graph.vertices.size(); ++i) {
      GraphVertex curVertex = graph.vertices.get(i);
      if ( curVertex.showVertex ) {
        out.println(
                    graph.pos[i][0] + " "
                  + graph.pos[i][1] + " "
                  + graph.pos[i][2] + " "
                  );
      } 
    }
  }

};