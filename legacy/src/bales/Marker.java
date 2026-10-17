package bales;


/*****************************************************************
 * Marks a given vertex under certain conditions.
 * @version  $Revision: 1.4 $; $Date: 2007/12/13 08:45:53 $
 * @author   Dirk Beyer
 *****************************************************************/
public class Marker {
  /** Constructor.*/
  public Marker() {
  }

  /*****************************************************************
   * Special marking for certain vertices by setting attributes of the vertex,
   * e.g., color and showName. 
   * This method should be overwritten by a user-defined subclass in order to 
   * implement some 'highlighting' functionality.
   * @param vertex  Vertex of the graph representation.
   *****************************************************************/
  public void mark(GraphVertex vertex) {
	/* Example:
	if(   vertex.name.startsWith("xyz")
	   || vertex.name.startsWith("zyx")
	  ) {
	  vertex.color = Color.RED;
	  vertex.showName = true;
	}
	*/
  }
};
