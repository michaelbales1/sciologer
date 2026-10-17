package bales;


import java.io.PrintWriter;

/*****************************************************************
 * Writer for layouts in text format.
 * @version  $Revision: 1.23 $; $Date: 2007/12/13 08:45:53 $
 * @author   Michael Bales
 *****************************************************************/
public class WriterDataLAYL extends WriterData {

  private PrintWriter out;

  public WriterDataLAYL(GraphData graph, 
                       PrintWriter out) {
    super(graph);
    this.out = out;
  }

  /*****************************************************************
   * Writes the layout data in text format LAYL (brief with lables).
   *****************************************************************/
  public void write() {
    for (int i = 0; i < graph.vertices.size(); ++i) {
      GraphVertex curVertex = graph.vertices.get(i);
      if ( curVertex.showVertex ) {
        out.println(
                    graph.pos[i][0] + "\t"
                  + graph.pos[i][1] + "\t"
                  + graph.pos[i][2] + "\t"
                  + "\"" + curVertex.name + "\""
                  );
      } 
    }
  }

};