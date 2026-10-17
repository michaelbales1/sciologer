package bales;


import java.awt.Color;
import java.io.BufferedReader;
import java.util.StringTokenizer;
import java.util.Vector;

/*****************************************************************
 * Reader for layouts in text format.
 * @version  $Revision: 1.26 $; $Date: 2007/12/13 08:45:53 $
 * @author   Dirk Beyer
 *****************************************************************/
public class ReaderDataLAY extends ReaderData {

  /**
   * Constructor.
   * @param in  Stream reader object.
   */
  public ReaderDataLAY(BufferedReader in) {
    super(in);
  }

  private class LayoutEntry {
    float[]   pos;
    GraphVertex vertex;
  };

  /*****************************************************************
   * Reads the layout data from stream reader <code>in</code>, in text format LAY.
   * @param graph   <code>GraphData</code> object to store the layout data in.
   *****************************************************************/
  public void read(GraphData graph){
    Vector<LayoutEntry> entryList = new Vector<LayoutEntry>();
    try {
      String lLine;
      while ((lLine = in.readLine()) != null) {
	    StringTokenizer st = new StringTokenizer(lLine);
	    LayoutEntry e = new LayoutEntry();
	    e.pos = new float[3];
	    e.pos[0] = Float.parseFloat(st.nextToken());
	    e.pos[1] = Float.parseFloat(st.nextToken());
	    e.pos[2] = Float.parseFloat(st.nextToken());
	    e.vertex = new GraphVertex();
	    e.vertex.degree = Float.parseFloat(st.nextToken());
	    e.vertex.name = ReaderDataGraphRSF.readEntry(st);
	    if (st.hasMoreTokens()) {
	      e.vertex.color = new Color(Integer.parseInt(st.nextToken()));
	    }
	    if (st.hasMoreTokens()) {
	      e.vertex.showName = (Boolean.valueOf(st.nextToken())).booleanValue();
	    }
	    entryList.add(e);
      }
    }
    catch (Exception e) {
      System.err.println("Exception while reading the layout (readLayoutText): ");
      System.err.println(e);
    }

    // Now we know the number of vertices.
    graph.vertices.setSize(entryList.size());
    graph.pos = new float[entryList.size()][];
    for (int i = 0; i < entryList.size(); ++i) {
      LayoutEntry e = (LayoutEntry) entryList.get(i);
      // Add vertex.
      e.vertex.id = i;
      e.vertex.isSource = false;
      graph.vertices.set(i, e.vertex);
      // Add vertex-to-number entry for vertex.
      if (graph.nameToVertex.containsKey(e.vertex.name)) {
        System.err.println("Input error: Vertex '" + e.vertex.name + "' exists twice in layout.");
      }
      graph.nameToVertex.put(e.vertex.name, e.vertex);
      // Add position for vertex.
      graph.pos[i] = e.pos;

      // Monochromatic.
      //e.vertex.color = Color.GREEN;
    }
    
    for (int i = 0; i < graph.vertices.size(); ++i) {
      GraphVertex curVertex = graph.vertices.get(i);
      CCVisu.marker.mark(curVertex);
    }

    return;
  }

};
