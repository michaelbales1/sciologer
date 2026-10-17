package bales;


/*****************************************************************
 * Represents an edge between two vertices x and y 
 * (given as vertex names) of weight w (also String).
 * @version  $Revision: 1.5 $; $Date: 2007/12/13 08:45:53 $
 * @author   Dirk Beyer
 *****************************************************************/
public class GraphEdgeString {
      /** The name of the relation the egde belongs to*/
      public String relName;
      /** Source vertex of edge.*/
	  public String x;
      /** Target vertex of edge.*/
	  public String y;
      /** Edge weight.*/
	  public String w;
	};