package bales;


import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

/*****************************************************************
 * Contains the representation of a graph.
 * This class is a collection of the data structures needed 
 * for layout and transformation processing.
 * @version  $Revision: 1.22 $; $Date: 2007/12/13 08:45:53 $
 * @author   Dirk Beyer
 *****************************************************************/
public class GraphData {
  /** Maps a vertex id to a GraphVertex.*/
  public Vector<GraphVertex> vertices;
  /** Maps a vertex name to a GraphVertex.*/
  public Map<String,GraphVertex> nameToVertex;
  /** Edges of type GraphEdgeInt. Only used if (inFormat < LAY).*/
  public Vector<GraphEdgeInt> edges;
  /** Layout. Only used if (outFormat >= LAY).*/
  public float[][] pos;

  /** Constructor.*/
  public GraphData() {
    vertices      = new Vector<GraphVertex>();
    nameToVertex  = new HashMap<String,GraphVertex>(); //TreeMap<String,GraphVertex>();
    edges         = new Vector<GraphEdgeInt>();
    // The initialization of 'pos' is postponed until the number of vertices is known,
    // done by method 'initializeLayout'. 
  }
};


