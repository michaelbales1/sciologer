package bales;


/*****************************************************************
 * Represents an edge between two vertices x and y 
 * (given as vertex ids) of weight w.
 * @version  $Revision: 1.15 $; $Date: 2007/12/13 08:45:53 $
 * @author   Dirk Beyer
 *****************************************************************/
public class GraphEdgeInt {
  /** The name of the relation the egde belongs to*/
  public String relName;
  /** Source vertex of edge.*/
  public int x;
  /** Target vertex of edge.*/
  public int y;
  /** Edge weight.*/
  public float w;
};