package bales;


import java.awt.Color;

/*****************************************************************
 * Represents a vertex of the graph, including name, id, 
 * and several attributes.
 * @version  $Revision: 1.22 $; $Date: 2007/12/13 08:45:53 $
 * @author   Dirk Beyer
 *****************************************************************/
public class GraphVertex {
  public String  name;
  public int     id;
  /** True if the vertex is the source vertex of an edge.*/
  public boolean isSource;
  /** (Weighted) Edge degree of the vertex.*/
  public float   degree;
  public Color   color;
  /** True if the vertex shall be displayed.*/
  public boolean showVertex;
  /** True if the name shall be annotated in the visualization.*/
  public boolean showName;
  
  /** Constructor.*/
  public GraphVertex() {
  	    degree = 0.0f;
        color = Color.GREEN;
        showVertex = true;
        showName = false;
  }

};
