package bales;

import java.awt.Color;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

public class Eleph1ntJob
{
	public final  String endl = System.getProperty("line.separator");
	// Format identifiers.
	/** CVS log format (only input).*/
	public final  int NODEDATA = 0;
	/** Node data specifying size, shape, color, label, texture in vertical bar delimited format*/
	public final  int POSITIONDATA = 1;
	/** Position data specifying node positionx, positiony, positionz, size, label, color, labelvis in tab delimited format*/
	/** Where labelvis indicates whether the label is visible, by default*/
	public final  int LINKSDATA = 1;
	/** Links data specifying link positionx, positiony, positionz for one end and positionx, positiony, positionz for the other end*/
	public final  int X3D = 2;
	/** Graph layout in X3D format.*/
	public final  int POV = 3;
	/** Graph layout in POV format.*/
	public final  int KML = 4;
	/** Graph layout in KML format.*/

	// Default I/O.
	//	 BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
	public PrintWriter out ;// = new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out)));

	// Input format
	public int inFormat = NODEDATA;
	public String nodeData = ""; // Empty string for no input file name (standard input).
	public String positionData = ""; // Empty string for no input file name (standard input).
	public String linksData = ""; // Empty string for no input file name (standard input).
	public String outfile = ""; // Empty string for no output file name (standard output).
	// Output format.
	public int outFormat = POV; // Default value; is set later based on input parameters

	// For layout output.
	public int fontSize = 14;
	public Color backColor = Color.WHITE;
	public boolean showEdges = false;
	public boolean singleFile = false; // for single file mode
	public float scalePos = 1.0f;
	public boolean annotAll = false;
	public boolean annotNone = false;
	public boolean iconColorNone = false;
	public boolean iconResizeNone = false;
	public boolean runExtProg = false;
	public boolean getLinks = false;
	public boolean excludeLinks = false;
	public boolean URL = false;
	public String iconMode="symbol"; // Default iconMode is symbol
	public String iconExtension="png"; // Files in the o/models/symbol folder have the extension png

	public Eleph1ntJob(String outFile)
	{
		this.outfile = outFile;
	}
}
