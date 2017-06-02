package bales;


import java.io.BufferedReader;
import java.util.StringTokenizer;
import java.util.Vector;

/*****************************************************************
 * Reader for co-change graphs in RSF format.
 * @version  $Revision: 1.29 $; $Date: 2007/12/13 08:45:53 $
 * @author   Dirk Beyer
 *****************************************************************/
public class ReaderDataGraphRSF extends ReaderDataGraph {

  /**
   * Constructor.
   * @param in  Stream reader object.
   */
  public ReaderDataGraphRSF(BufferedReader in) {
    super(in);
  }

  // Helper.
  public static String readEntry(StringTokenizer st) {
    String result = st.nextToken();
    if (result.charAt(0) == '"') {
      while (result.charAt(result.length() - 1) != '"') {
        result = result + ' ' + st.nextToken();
      }
      result = result.substring(1, result.length() - 1);
    }
    return result;
  }
  
  /*****************************************************************
   * Reads the edges of a graph in RSF (relational standard format)
   * from stream reader <code>in</code>, 
   * and stores them in a list (of <code>GraphEdgeString</code> elements).
   * @return List of string edges.
   *****************************************************************/
  protected Vector<GraphEdgeString> readEdges() {
    Vector<GraphEdgeString> result = new Vector<GraphEdgeString>();
    int lineno = 1;
    try {
      String lLine;
      while ((lLine = in.readLine()) != null) {
        StringTokenizer st = new StringTokenizer(lLine);
        
        GraphEdgeString edge = new GraphEdgeString();
		if (st.hasMoreTokens() && lLine.charAt(0)!='#') {
          // Relation name.
          edge.relName = st.nextToken();
          // Source vertex.
          edge.x = readEntry(st);
          // Target vertex.
          edge.y = readEntry(st);
          if (st.hasMoreTokens()) {
		    edge.w = st.nextToken();
		  } else {
		    edge.w = "1.0";
		  }

		  /*
		  int conf = Integer.parseInt(st.nextToken());
		  if (conf >300) {
		    result.add(edge);
		  }
		  */
		
		  result.add(edge);
		}
		++lineno;
      }
    }
    catch (Exception e) {
      System.err.println("Exception while reading the graph (readGraph) at line " 
                         + lineno + ":");
      System.err.println(e);
    }
    return result;
  }

};