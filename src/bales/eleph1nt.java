package bales;
/*
 * Eleph1nt is a tool to integrate network position data with node data
 * and export to a variety of network file formats.
 * 
 * Copyright 2008 Michael Bales (firstname.lastname@dbmi.columbia.edu)
 * All rights reserved
 * 
 * eleph1nt.java integrates node position data with node metadata and exports to X3D.
 * Input file format for node metadata is vertical bar delimited
 * Input file format for node position data in x y z format
 * Two choices for generating x y z values are to copy a portion of MOL format output
 * from Pajek into a new file, or to use output from CCVisu, available here:
 * http://www.cs.sfu.ca/~dbeyer/CCVisu/
 * 
 * Michael Bales (firstname.lastname@dbmi.columbia.edu)
 * Columbia University, New York, NY
 */
//
import java.awt.Color;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.StringTokenizer;

import org.apache.logging.log4j.Logger; 
import org.apache.logging.log4j.LogManager;

import bales.File_Settings;
// import org.apache.log4j.Logger;

public class eleph1nt 
{
//	static Logger logger = Logger.getLogger(eleph1nt.class);
	private static final Logger logger = LogManager.getLogger(eleph1nt.class.getName());

	/**************************************************************
	 * Main program. Performs the following steps.
	 * 1) Parses and handles the command line options.
	 * 2) Reads the input: network data followed by layout data.
	 * 3) Writes the output in format specified in the command line arguments
	 *****************************************************************/
	// End of line varies from one operating system to the next
	// endl is appropriate line separator for user's system
	public final static String endl = System.getProperty("line.separator");
	// Format identifiers.
	/** CVS log format (only input).*/
	public final static int NODEDATA = 0;
	/** Node data specifying size, shape, color, label, texture in vertical bar delimited format*/
	public final static int POSITIONDATA = 1;
	/** Position data specifying node positionx, positiony, positionz, size, label, color, labelvis in tab delimited format*/
	/** Where labelvis indicates whether the label is visible, by default*/
	public final static int LINKSDATA = 1;
	/** Links data specifying link positionx, positiony, positionz for one end and positionx, positiony, positionz for the other end*/
	public final static int X3D = 2;
	/** Graph layout in X3D format.*/
	public final static int POV = 3;
	/** Graph layout in POV format.*/
	public final static int KML = 4;
	/** Graph layout in KML format.*/
	public final static int SVG = 5;
	/** Graph layout in SVG format.*/
	public static void main(String args[]) {
		if (args.length == 0) {
			printHelp();
			//System.exit(0);
		}	
		// Default I/O.
		//	 BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		PrintWriter out = new PrintWriter(new BufferedWriter(
				new OutputStreamWriter(System.out)));

		// Input format
		int inFormat = NODEDATA;
		String nodeData = ""; // Empty string for no input file name (standard input).
		String positionData = ""; // Empty string for no input file name (standard input).
		String linksData = ""; // Empty string for no input file name (standard input).
		String outfile = ""; // Empty string for no output file name (standard output).
		// Output format.
		int outFormat = POV; // Default value; is set later based on input parameters

		// For layout output.
		int fontSize = 14;
		Color backColor = Color.WHITE;
		boolean showEdges = false;
		boolean singleFile = false; // for single file mode
		float scalePos = 1.0f;
		boolean annotAll = false;
		boolean annotNone = false;
		boolean iconColorNone = false;
		boolean iconResizeNone = false;
		boolean runExtProg = false;
		boolean getLinks = false;
		boolean excludeLinks = false;
		boolean excludePapers = false;
		boolean excludeAuthors = false;
		boolean excludeJournals = false;
		boolean excludeInstitutions = false;
		boolean excludeGrants = false;
		boolean excludeMainHeadings = false;
		boolean excludeSubstances = false;
		boolean excludeAbstracts = false;
		boolean excludeTags = false;
		boolean excludeUmlsVariants = false;
		boolean displayElement = true;
		boolean URL = false;
		String iconMode="symbol"; // Default iconMode is symbol
		String iconExtension="png"; // Files in the o/models/symbol folder have the extension png

		// Parse command-line options.
		for (int i = 0; i < args.length; ++i) {
			// General options without argument.
			// Help.
			if (args[i].equalsIgnoreCase("-h") || args[i].equalsIgnoreCase("--help")) {
				printHelp();
				out.close();
				//System.exit(0);
			}
			// Version.
			else if (args[i].equalsIgnoreCase("-v") || args[i].equalsIgnoreCase("--version")) {
				printVersion();
				out.close();
				//System.exit(0);
			}

			// Run external program.
			else if (args[i].equalsIgnoreCase("-r") || args[i].equalsIgnoreCase("-runext")) {
				runExtProg=true;
			}

			// Change input reader for node data.
			else if (args[i].equalsIgnoreCase("-nd")) {
				++i;
				chkAvail(args, i);
				try {
					nodeData = args[i];
					//	 in = new BufferedReader(new FileReader(args[i]));
				}
				catch (Exception e) {
					logger.error("Exception while opening file '" + args[i] + "' for reading: ");
					logger.error(e); e.printStackTrace(); 
					//System.exit(1);
				}
			}

			// Change input reader for position data
			else if (args[i].equalsIgnoreCase("-pd")) {
				++i;
				chkAvail(args, i);
				try {
					positionData = args[i].replaceAll("_"," ");
					//	 in = new BufferedReader(new FileReader(args[i]));
				}
				catch (Exception e) {
					logger.error("Exception while opening file '" + args[i] + "' for reading: ");
					logger.error(e); e.printStackTrace(); 
					//System.exit(1);
				}
			}

			// Change input reader for links data
			else if (args[i].equalsIgnoreCase("-ld")) {
				++i;
				chkAvail(args, i);
				getLinks=true;
				try {
					linksData = args[i].replaceAll("_"," ");
					//	 in = new BufferedReader(new FileReader(args[i]));
				}
				catch (Exception e) {
					logger.error("Exception while opening file '" + args[i] + "' for reading: ");
					logger.error(e); e.printStackTrace(); 
					//System.exit(1);
				}
			}

			// Whether to include links
			else if (args[i].equalsIgnoreCase("-excludeLinks")) {
				excludeLinks=true;
			}
			// Whether to include journals
			else if (args[i].equalsIgnoreCase("-excludePapers")) {
				excludePapers=true;
			}
			else if (args[i].equalsIgnoreCase("-excludeAuthors")) {
				excludeAuthors=true;
			}
			else if (args[i].equalsIgnoreCase("-excludeJournals")) {
				excludeJournals=true;
			}
			else if (args[i].equalsIgnoreCase("-excludeInstitutions")) {
				excludeInstitutions=true;
			}
			else if (args[i].equalsIgnoreCase("-excludeGrants")) {
				excludeGrants=true;
			}
			else if (args[i].equalsIgnoreCase("-excludeMainHeadings")) {
				excludeMainHeadings=true;
			}
			else if (args[i].equalsIgnoreCase("-excludeSubstances")) {
				excludeSubstances=true;
			}
			else if (args[i].equalsIgnoreCase("-excludeAbstracts")) {
				excludeAbstracts=true;
			}
			else if (args[i].equalsIgnoreCase("-excludeTags")) {
				excludeTags=true;
			}
			else if (args[i].equalsIgnoreCase("-excludeUmlsVariants")) {
				excludeUmlsVariants=true;
			}

			// Change output writer.
			else if (args[i].equalsIgnoreCase("-o")) {
				++i;
				chkAvail(args, i);
				try {
					//	 out = new PrintWriter(new BufferedWriter(new FileWriter(args[i])));
					outfile=args[i].replaceAll("_"," ");
				}
				catch (Exception e) {
					logger.error("Exception while opening file '" + args[i] + "' for writing: ");
					logger.error(e); e.printStackTrace(); 
					//System.exit(1);
				}
			}

			// Input format.
			else if (args[i].equalsIgnoreCase("-inFormat")) {
				++i;
				chkAvail(args, i);
				inFormat = getFormat(args[i]);
				if (inFormat > NODEDATA) {
					logger.error("Usage error: '" + args[i] + "' is not supported as input format.");
					//System.exit(1);
				}
			}
			// Output format.
			else if (args[i].equalsIgnoreCase("-outFormat")) {
				++i;
				chkAvail(args, i);
				outFormat = getFormat(args[i]);
			}

			// Options for output writers.
			// Font size for annotations in the layout.
			else if (args[i].equalsIgnoreCase("-fontSize")) {
				++i;
				chkAvail(args, i);
				fontSize = Integer.parseInt(args[i]);
			}
			// Background color.
			else if (args[i].equalsIgnoreCase("-backcolor")) {
				++i;
				chkAvail(args, i);
				if (args[i].equalsIgnoreCase("black")) {
					backColor = Color.BLACK;
				} else if (args[i].equalsIgnoreCase("white")) {
					backColor = Color.WHITE;
				} else if (args[i].equalsIgnoreCase("gray")) {
					backColor = Color.GRAY;
				} else if (args[i].equalsIgnoreCase("lightgray")) {
					backColor = Color.LIGHT_GRAY;
				} else {
					logger.error("Usage error: Color '" + args[i] + "' unknown.");
				}
			}

			// Set icon mode
			else if (args[i].equalsIgnoreCase("-iconMode"))
			{
				i++;
				chkAvail(args, i);
				// Set icon mode to the specified argument
				iconMode = args[i];
				// Now assign the appropriate file extension to use when selecting icons from the models directory 
				if((iconMode.equals("model"))|(iconMode.equals("simplemodel")))
					iconExtension="dae";
				else if(iconMode.equals("drawing"))
					iconExtension="png";
				else if(iconMode.equals("symbol"))
					iconExtension="png";
				else if(iconMode.equals("photo"))
					iconExtension="jpg";
			}

			// Show the Edges
			else if (args[i].equalsIgnoreCase("-showEdges")) {
				showEdges = true;
			}

			// Single file mode
			else if (args[i].equalsIgnoreCase("-singleFile")) {
				singleFile = true;
			}

			// For all.
			// Annotate each vertex with its name.
			else if (args[i].equalsIgnoreCase("-annotAll")) {
				annotAll = true;
			}
			// Annotate no vertex.
			else if (args[i].equalsIgnoreCase("-annotNone")) {
				annotNone = true;
			}

			// Do not add color to any icons.
			else if (args[i].equalsIgnoreCase("-iconColorNone")) {
				iconColorNone = true;
			}

			// Do not resize any icons.
			else if (args[i].equalsIgnoreCase("-iconResizeNone")) {
				iconResizeNone = true;
			}


			// Allow to open the URLs
			else if (args[i].equalsIgnoreCase("-openURL")) {
				URL = true;
			}

			// Dummy option -- is used because arguments are not yet handled properly;
			// I plan to fix this -- MB
			else if (args[i].equalsIgnoreCase("-dummy")) {
				// do nothing
			}

			// Unknown option.
			else {
				logger.error("Usage error: Option '" + args[i] + "' unknown.");
				//System.exit(1);
			}
		} // for parsing command-line options.
		//	 String outfile="c:\\NMDAPOV.x3d";

		int lineCount=0; // is used to store the number of lines counted by the initial counting routine for positions data
		int lineCountLinks=0; // is used to store the number of lines counted by the initial counting routine for links data
		int alc=0; // keeps track of position in file for initial read-in to memory and for once-through
		int alca=0; // keeps track of position in array for tokenizer, from zero to one
		String footer; // string to store footer of X3D file
		String result; // string to store the line-by-line output -- be it header, data, or footer
		//	 Writer output=null; // writer to store the entire output string
		try {
			//	 output.write();
			//	 BufferedReader in = new BufferedReader(new FileReader("myt.txt"));
			//	 BufferedReader in = new BufferedReader(new FileReader(args[1]));
			//	 BufferedReader in = new BufferedReader(new FileReader(nodeData));
			if(singleFile==true)
			{
				File f=new File(File_Settings._bP+File_Settings.positionsPath);
				// Create an array including all the files in the positions folder
				File files[]=f.listFiles();
				positionData=(File_Settings._bP+File_Settings.positionsPath + files[0].getName());
			}
			BufferedReader in = new BufferedReader(new FileReader(positionData));	
			String str; // string to hold the lines as they're read sequentially
			// First count the number of rows, to know how large to make the array
			while ((str = in.readLine()) != null)
			{
				lineCount++;
			}
			in.close();

			if(getLinks==true)
			{

				BufferedReader inLinks = new BufferedReader(new FileReader(linksData));	
				str=""; // string to hold the lines as they're read sequentially
				// First count the number of rows, to know how large to make the array
				while ((str = inLinks.readLine()) != null)
				{
					lineCountLinks++;
				}
				inLinks.close();

			}
		} // end try
		catch (IOException e) {
			logger.error(e); e.printStackTrace(); 
		} // end catch

		// set up an n by 3 string array to store the node data elements
		//		String[][] AllLinesND=new String[lineCount][3];  // Node data: size|diffusecolor diffusecolor diffusecolor|label
		// set up an n by 4 string array to store the position information and optional label
		String[][] AllLinesPD=new String[lineCount][9];

		/* Position and node data:
		 * x, y, z, degree, "type_label", color, labelvis, "type", "label"
		 * The seven leftmost data elements are read directly from the file
		 * These files are CCVisu's RSF-format output.
		 * A sample input format of this data is as follows:
		 * -28.165995	14.799589	1.2726835	5.0	"np_new brain imaging study"	65280	false
		 * The two rightmost data elements "type" and "label" are added by this program
		 * By parsing out the node type and the label from the concatenated node_label string in position [4] of array
		 * See below for more details.
		 */ 

		try {
			//			BufferedReader in2 = new BufferedReader(new FileReader("myt.txt"));
			//			BufferedReader inND = new BufferedReader(new FileReader(args[0]));
			//			BufferedReader inND = new BufferedReader(new FileReader(nodeData));
			String str;
			File file=new File(outfile); // declaration of output filename
			BufferedWriter output=new BufferedWriter(new FileWriter(file));

			/*

			while ((str = inND.readLine()) != null) {
				{
					StringTokenizer tokenizer = new StringTokenizer(str, "|");
					alca=0; // initialize the position in the tokenizer to zero
					while(tokenizer.hasMoreTokens())
					{
						AllLinesND[alc][alca]=tokenizer.nextToken();
						alca++; // increment to next token position in array
					}
					// finished populating the array for that line  
					alc++; // increment alc to move to the next row in the AllLines array
				}
				// now populate the array for the next line
			}

			 */

			alc=0;
			BufferedReader inPD = new BufferedReader(new FileReader(positionData));
			// Initialize values to determine ranges of x and y values

			double minmax[][]=new double[4][2];
			minmax[0][0]=99999; // initialize minimum x value
			minmax[0][1]=-99999; // maximum x value
			minmax[1][0]=99999; // minimum y value
			minmax[1][1]=-99999; // maximum y value
			minmax[2][0]=99999; // minimum z value
			minmax[2][1]=-99999; // maximum z value
			minmax[3][0]=99999; // minimum z value
			minmax[3][1]=-99999; // maximum z value
			double currentVal;
			while ((str = inPD.readLine()) != null) {
				{
					//					StringTokenizer tokenizer = new StringTokenizer(str, " ");
					StringTokenizer tokenizer = new StringTokenizer(str, "\t");
					alca=0; // initialize the position in the tokenizer to zero
					String typeAndLabel=""; // declare empty string to hold concatenated type and label (see below)

					while(tokenizer.hasMoreTokens())
					{
						AllLinesPD[alc][alca]=tokenizer.nextToken();
						if(alca==4)
						{
							typeAndLabel=AllLinesPD[alc][alca];
							StringTokenizer TLtokenizer = new StringTokenizer(typeAndLabel, "_");
							AllLinesPD[alc][7]=TLtokenizer.nextToken();
							AllLinesPD[alc][8]=TLtokenizer.nextToken();
						}
						/* The purpose of the preceding lines is to parse the node types and labels,
						 * which are passed to this program as one string representing the node label.
						 * In other words, the labels and types are concatenated when they are passed to this program
						 * and the code below tokenizes them out and places them in the two positions that were added
						 * in the two right-most columns of the AllLinesPD array.
						 */
						// Determine whether the current value is a new minumum or maximum
						if(alca==0 || alca==1 || alca==2 || alca==3)
						{
							currentVal=Double.parseDouble(AllLinesPD[alc][alca]);
							if(currentVal<minmax[alca][0])
								minmax[alca][0]=currentVal; // It is the new minimum
							if(currentVal>minmax[alca][1])
								minmax[alca][1]=currentVal; // It is the new maximum
						}
						alca++; // increment to next token position in array	
					}
					// finished populating the array for that line  
					alc++; // increment alc to move to the next row in the AllLines array
				}
				// now populate the array for the next line
			}

			// Determine cutoff values for displaying tags
			// double minValToDisplayTag=2*Math.log(minmax[3][1]);
			double minValToDisplayTag=0;
			//			double maxValToDisplayTag=2*Math.log(minmax[3][1]);

			//			double minValToDisplayTag=2;
			double maxValToDisplayTag=99999;

			// Now poplulate links data array
			String[][] AllLinesLD=new String[lineCountLinks][6];

			/* Tab-delimited links data
			 * x1, y1, z1, x2, y2, z2
			 * Where 1 is one end of the line and 2 is the other end of the line.
			 */ 

			try {
				alc=0;
				alca=0;
				BufferedReader inLD = new BufferedReader(new FileReader(linksData));
				while ((str = inLD.readLine()) != null) {
					{
						StringTokenizer tokenizer = new StringTokenizer(str, "\t");
						alca=0; // initialize the position in the tokenizer to zero
						while(tokenizer.hasMoreTokens())
						{
							AllLinesLD[alc][alca]=tokenizer.nextToken();
							alca++; // increment to next token position in array	
						}
						// finished populating the array for that line  
						alc++; // increment alc to move to the next row in the AllLines array
					}
					// now populate the array for the next line
				}			
				// Done reading the positions and links data into memory; now begin algorithm
				alc=0;

				// good header=("<?xml version=\"1.0\" encoding=\"UTF-8\"?> <!DOCTYPE X3D PUBLIC \"ISO//Web3D//DTD X3D 3.0//EN\"   \"http://www.web3d.org/specifications/x3d-3.0.dtd\"><X3D version='3.0' profile='Immersive' xmlns:xsd='http://www.w3.org/2001/XMLSchema-instance'xsd:noNamespaceSchemaLocation='http://www.web3d.org/specifications/x3d-3.0.xsd'><head></head>");

				if(outFormat==X3D)
				{
					output.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");output.newLine();
					output.write("<!DOCTYPE X3D PUBLIC \"ISO//Web3D//DTD X3D 3.0//EN\"   \"http://www.web3d.org/specifications/x3d-3.0.dtd\">");output.newLine();
					output.write("<X3D version='3.0' profile='Immersive' xmlns:xsd='http://www.w3.org/2001/XMLSchema-instance'");output.newLine();
					output.write("xsd:noNamespaceSchemaLocation='http://www.web3d.org/specifications/x3d-3.0.xsd'>");output.newLine();
					output.write("  <head>");output.newLine();
					output.write("  </head>");output.newLine();
					output.write("  <Scene><Background skyColor='1.00 1.00 1.00'/>");output.newLine();
					// Only generate a node if its node degree is above specified threshold
					// SET THRESHOLD HERE
					if(Integer.parseInt(AllLinesPD[alc][3])>10)
					{
						for(int i=0; i<lineCount; i++)
						{
							//							result="    <Transform translation='" + AllLinesPD[i] + "'>";
							result="    <Transform translation='" + AllLinesPD[i][0] + " " + AllLinesPD[i][1] + " " + AllLinesPD[i][2] + "'>";
							output.write(result);output.newLine();
							//							output.write("<Anchor DEF='PubMedLink' description='Link to PubMed' parameter='target=_blank' url='http://www.ncbi.nlm.nih.gov/sites/entrez?dispmax=20&amp;db=pubmed&amp;cmd_current=Limits&amp;orig_db=PubMed&amp;cmd=Search&amp;term=%22" + (AllLinesPD[i][8].replaceAll(" ", "%20")).replaceAll("\"","").replaceAll("'","") + "%22%5Bmh%5D&amp;doptcmdl=DocSum'>");output.newLine();
							//							NOTE -- adding back in %5Bmh%5D
							output.write("<Anchor DEF='PubMedLink' description='Link to PubMed' parameter='target=_blank' url='http://www.ncbi.nlm.nih.gov/sites/entrez?dispmax=20&amp;db=pubmed&amp;cmd_current=Limits&amp;orig_db=PubMed&amp;cmd=Search&amp;term=%22" + (AllLinesPD[i][8].replaceAll(" ", "%20")).replaceAll("\"","").replaceAll("'","") + "%22%5Bmh%5D&amp;doptcmdl=DocSum'>");output.newLine();
							output.write("        <Shape>");output.newLine();
							//							output.write("            <Appearance><Material diffuseColor='" + AllLinesND[i][1] + "'/></Appearance>");output.newLine(); 
							output.write("            <Appearance><Material diffuseColor='0.2 0.7 0.2'/></Appearance>");output.newLine();
							//							output.write("            <Sphere radius='" + AllLinesND[i][0] + "'/>");output.newLine();
							// MODIFY SPHERE SIZES ON THE NEXT LINE
							output.write("            <Sphere radius='" + Double.parseDouble(AllLinesPD[i][3])*0.04 + "'/>");output.newLine();
							output.write("         </Shape>");output.newLine();
							output.write("         </Anchor>");output.newLine();
							// Now output labels
							//							output.write("<Transform translation='0 -" + (AllLinesND[i][0]) + " 0'>"); // Transform for label
							// MODIFY LABEL OFFSET ON THE NEXT LINE
							output.write("      <Transform translation='0 " + (Double.parseDouble(AllLinesPD[i][3])*(-0.08)) + " 0'>");output.newLine();
							// Transform translation for label. Offset in the x direction is equal to negative n where n is sphere size.
							output.write("        <Shape>");output.newLine();
							//							output.write("          <Text solid='true' string='\"" + AllLinesND[i][2] + "\"'>");output.newLine();
							output.write("          <Text solid='true' string='" + AllLinesPD[i][8].replaceAll("'", "") + "'>");output.newLine();
							// MODIFY FONT SIZE ON THE NEXT LINE
							output.write("            <FontStyle justify='\"MIDDLE\"' size='0.5'/>");output.newLine();
							output.write("          </Text>");output.newLine();
							output.write("          <Appearance>");output.newLine();
							output.write("            <Material diffuseColor='0 0 0'/>");output.newLine();
							output.write("          </Appearance>");output.newLine();
							output.write("        </Shape>");output.newLine();
							output.write("      </Transform>");output.newLine(); // End transform for label
							output.write("    </Transform>");output.newLine(); // End transform for shape
						}
					} // end if for threshold
					footer=("</Scene></X3D>");
					output.write(footer);output.newLine();
				}

				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				//
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				//
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				//
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				//
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				//
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				//
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				//
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				//
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				// POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV POV
				//
				// NEW POV NEW POV NEW POV NEW POV NEW POV NEW POV NEW POV NEW POV NEW POV NEW POV NEW POV NEW POV NEW POV

				if(outFormat==POV)
				{
					double mainScale=1;
					double pxScale=1; // Default value; is reset below
					double pyScale=1; // Default value; is reset below
					double pzScale=1; // Default value; is reset below
					//					double pxScale=mainScale*100;
					//					double pyScale=mainScale*100;
					//					double pzScale=mainScale*100;
					//					double iconScale=0.001;
					//					double iconScale=0.05; USING THIS ONE RECENTLY
					double iconScale=mainScale; // Default value; is reset below
					double colorScale=1;
					double blueVal=0;	
					double greenVal=0;
					double redVal=0;
					// POV-Ray color settings
					boolean colorBasedOnDegree = false;
					boolean colorBasedOnType = false;
					// NOTE -- these are POV-Ray color settings only
					boolean colorBasedOnTypeWithColorList = false;
					boolean colorBasedOnPosition = true;
					// End POV-Ray color settings

					if(colorBasedOnType==true || colorBasedOnTypeWithColorList==true)
					{
						pxScale=mainScale*0.5;
						pyScale=mainScale*0.5;
						pzScale=mainScale*0.5;
						//						iconScale=mainScale*0.2;
						iconScale=mainScale*0.5;
						//						iconScale=mainScale*0.014;
					}
					if(colorBasedOnPosition==true)
					{
						pxScale=mainScale;
						pyScale=mainScale;
						pzScale=mainScale;
						// iconScale=mainScale*0.5; Current 12-2010
					//	iconScale=mainScale*2;
						iconScale=mainScale*10;
					}
					// int numTypes=9;
					int numTypes=7;
					//					double iconScale=0.3;

					output.write("#declare Shiny =");output.newLine();
					output.write("  texture {");output.newLine();
					output.write("    finish { ");output.newLine();
					output.write("      ambient 0");output.newLine();
					output.write("      diffuse 0.95");output.newLine();
					output.write("      specular 0.2");output.newLine();
					output.write("      roughness 0.012904");output.newLine();
					output.write("      reflection {");output.newLine();
					output.write("        0.025, 0.1");output.newLine();
					output.write("      }");output.newLine();
					output.write("      conserve_energy");output.newLine();
					output.write("    }");output.newLine();
					output.write("  }");output.newLine();
					/*
					output.write("#declare Shiny =");output.newLine();
					output.write("  texture {");output.newLine();
					output.write("    finish { ");output.newLine();
					output.write("      ambient 0");output.newLine();
					output.write("      diffuse 0.95");output.newLine();
					output.write("      specular 0.7");output.newLine();
					output.write("      roughness 0.012904");output.newLine();
					output.write("      reflection {");output.newLine();
					output.write("        0.025, 0.1");output.newLine();
					output.write("      }");output.newLine();
					output.write("      conserve_energy");output.newLine();
					output.write("    }");output.newLine();
					output.write("  }");output.newLine();
					 */
					String textureVal="ShinyRed"; // Transparency value or "alpha"; default is red
					output.write("#version 3.61;");output.newLine();
					output.write("#include \"colors.inc\"");output.newLine();
					// output.write("#include \"shapes.inc\"");output.newLine();
					output.write("#include \"c:\\shapes.inc\"");output.newLine();
					output.write("");output.newLine();
					output.write("global_settings {");output.newLine();
					output.write("  assumed_gamma 2.0");output.newLine();
					output.write("}");output.newLine();
					output.write("");output.newLine();
					output.write("#declare ShinyDarkTurquoise =");output.newLine();
					output.write("  texture {");output.newLine();
					output.write("    pigment { DarkTurquoise }");output.newLine();
					output.write("    finish { ");output.newLine();
					output.write("      ambient 0");output.newLine();
					output.write("      diffuse 0.95");output.newLine();
					output.write("      specular 0.7");output.newLine();
					output.write("      roughness 0.012904");output.newLine();
					output.write("      reflection {");output.newLine();
					output.write("        0.025, 0.1");output.newLine();
					output.write("      }");output.newLine();
					output.write("      conserve_energy");output.newLine();
					output.write("    }");output.newLine();
					output.write("  }");output.newLine();
					output.write("#declare ShinyForestGreen =");output.newLine();
					output.write("  texture {");output.newLine();
					output.write("    pigment { ForestGreen }");output.newLine();
					output.write("    finish { ");output.newLine();
					output.write("      ambient 0");output.newLine();
					output.write("      diffuse 0.95");output.newLine();
					output.write("      specular 0.7");output.newLine();
					output.write("      roughness 0.012904");output.newLine();
					output.write("      reflection {");output.newLine();
					output.write("        0.025, 0.1");output.newLine();
					output.write("      }");output.newLine();
					output.write("      conserve_energy");output.newLine();
					output.write("    }");output.newLine();
					output.write("  }");output.newLine();
					output.write("#declare ShinyYellow =");output.newLine();
					output.write("  texture {");output.newLine();
					output.write("    pigment { Yellow }");output.newLine();
					output.write("    finish { ");output.newLine();
					output.write("      ambient 0");output.newLine();
					output.write("      diffuse 0.95");output.newLine();
					output.write("      specular 0.7");output.newLine();
					output.write("      roughness 0.012904");output.newLine();
					output.write("      reflection {");output.newLine();
					output.write("        0.025, 0.1");output.newLine();
					output.write("      }");output.newLine();
					output.write("      conserve_energy");output.newLine();
					output.write("    }");output.newLine();
					output.write("  }");output.newLine();
					output.write("#declare ShinyOrange =");output.newLine();
					output.write("  texture {");output.newLine();
					output.write("    pigment { Orange }");output.newLine();
					output.write("    finish { ");output.newLine();
					output.write("      ambient 0");output.newLine();
					output.write("      diffuse 0.95");output.newLine();
					output.write("      specular 0.7");output.newLine();
					output.write("      roughness 0.012904");output.newLine();
					output.write("      reflection {");output.newLine();
					output.write("        0.025, 0.1");output.newLine();
					output.write("      }");output.newLine();
					output.write("      conserve_energy");output.newLine();
					output.write("    }");output.newLine();
					output.write("  }");output.newLine();
					output.write("#declare ShinyRed =");output.newLine();
					output.write("  texture {");output.newLine();
					output.write("    pigment { Red }");output.newLine();
					output.write("    finish { ");output.newLine();
					output.write("      ambient 0");output.newLine();
					output.write("      diffuse 0.95");output.newLine();
					output.write("      specular 0.7");output.newLine();
					output.write("      roughness 0.012904");output.newLine();
					output.write("      reflection {");output.newLine();
					output.write("        0.025, 0.1");output.newLine();
					output.write("      }");output.newLine();
					output.write("      conserve_energy");output.newLine();
					output.write("    }");output.newLine();
					output.write("  }");output.newLine();
					output.write("#declare ShinyFirebrick =");output.newLine();
					output.write("  texture {");output.newLine();
					output.write("    pigment { Firebrick }");output.newLine();
					output.write("    finish { ");output.newLine();
					output.write("      ambient 0");output.newLine();
					output.write("      diffuse 0.95");output.newLine();
					output.write("      specular 0.7");output.newLine();
					output.write("      roughness 0.012904");output.newLine();
					output.write("      reflection {");output.newLine();
					output.write("        0.025, 0.1");output.newLine();
					output.write("      }");output.newLine();
					output.write("      conserve_energy");output.newLine();
					output.write("    }");output.newLine();
					output.write("  }");output.newLine();
					output.write("#declare ShinyViolet =");output.newLine();
					output.write("  texture {");output.newLine();
					output.write("    pigment { Violet }");output.newLine();
					output.write("    finish { ");output.newLine();
					output.write("      ambient 0");output.newLine();
					output.write("      diffuse 0.95");output.newLine();
					output.write("      specular 0.7");output.newLine();
					output.write("      roughness 0.012904");output.newLine();
					output.write("      reflection {");output.newLine();
					output.write("        0.025, 0.1");output.newLine();
					output.write("      }");output.newLine();
					output.write("      conserve_energy");output.newLine();
					output.write("    }");output.newLine();
					output.write("  }");output.newLine();
					output.write("#declare ShinyCadetBlue =");output.newLine();
					output.write("  texture {");output.newLine();
					output.write("    pigment { CadetBlue }");output.newLine();
					output.write("    finish { ");output.newLine();
					output.write("      ambient 0");output.newLine();
					output.write("      diffuse 0.95");output.newLine();
					output.write("      specular 0.7");output.newLine();
					output.write("      roughness 0.012904");output.newLine();
					output.write("      reflection {");output.newLine();
					output.write("        0.025, 0.1");output.newLine();
					output.write("      }");output.newLine();
					output.write("      conserve_energy");output.newLine();
					output.write("    }");output.newLine();
					output.write("  }");output.newLine();
					output.write("  ");output.newLine();
					output.write("");output.newLine();
					output.write("camera {");output.newLine();
					//					output.write("  location <0, 1, -11>");output.newLine();
					//					output.write("  location <0, 10, 0>");output.newLine();
					output.write("  location <" + 0 + ", 0, " + "-500"+">");output.newLine();
					//					output.write("  rotate <" + 0 + ", 0, " + "90"+">");output.newLine();
					//					output.write("  location <-2, 1, -2>");output.newLine();
					//					output.write("  sky <5, 0, 0>");output.newLine();
					//					output.write("  up y");output.newLine();
					//					output.write("  right x * image_width/image_height");output.newLine();
					//					output.write("  look_at 0");output.newLine();
					//					output.write("  look_at <0, 0, 0>");output.newLine();
					output.write("  look_at <" + 0 + ", 0, " + 0+">");output.newLine();
					output.write("}");output.newLine();
					output.write("");output.newLine();
					/*
										output.write("light_source {");output.newLine();
										output.write("  <0, 0, -10000>, rgb <1, 1, 1>");output.newLine();
										output.write("}");output.newLine();
					 */

					output.write("light_source {");output.newLine();
					//					output.write("  <20, 50, -150>, rgb <255, 231, 204>/255");output.newLine();
					output.write("  <400, 400, -5000>, rgb <255, 255, 255>/255");output.newLine();
					output.write("  area_light");output.newLine();
					output.write("  x * 2, y * 2, 15, 15");output.newLine();
					output.write("  circular");output.newLine();
					output.write("  orient");output.newLine();
					output.write("  adaptive 1");output.newLine();
					output.write("  jitter");output.newLine();
					output.write("  shadowless");output.newLine();
					output.write("}");output.newLine();
// Can remove these lines for black background
					output.write("");output.newLine();
					output.write("plane {");output.newLine();
					//					output.write("  -z, -5");output.newLine();
					//					output.write("  y, -0.02");output.newLine();
					output.write("  -z, -1000");output.newLine();
					output.write("texture { Shiny }");output.newLine();
					output.write("  pigment { White }");output.newLine();
					output.write("}");output.newLine();
							
					output.write("union {");output.newLine();
					// Main grouping (union) statement to allow for rotation of entire network as desired about the Z-axis
					int typeCounter=0;
					int colorCounter=1;
					double cubeWidth=14; // Modify cube width to generate a different group of colors
					// Acceptable values of cubeWidth are up to double the number of types
					double seed=10*Math.random(); // Modify seed to achieve slightly different colors
					double numCubes=Math.pow(cubeWidth,3);
					//					int selectCubeInterval=(int)((numCubes/numTypes)+seed);
					int selectCubeInterval=(int)((numCubes/numTypes)+12*Math.random());
					double[][] colorsByType= new double[numTypes][3];

					/* The following code is designed to maximize the differentiation of colors
					 * selected in RGB. Set up a cube and fill it with boxes. Divide the total number
					 * of boxes by the desired number of color choices (selectCubeInterval, above.)
					 * Now number the boxes; move through the cube and when you come to a box that
					 * has a number matching the selectCubeInterval, put it into the colorsByType array.
					 * Once you have the desired number of colors, break out of the loops to avoid an
					 * array index out of bounds exception. The cubeWidth value can be modified to achieve
					 * different selections of colors.
					 */
					for(double r=0; r<cubeWidth; r++)
					{
						for(double g=0; g<cubeWidth; g++)
						{
							for(double b=0; b<cubeWidth; b++)
							{		
								if(colorCounter%selectCubeInterval==0)
								{
									colorsByType[typeCounter][0]=r/cubeWidth;
									colorsByType[typeCounter][1]=g/cubeWidth;
									colorsByType[typeCounter][2]=b/cubeWidth;
									typeCounter++;
								}
								colorCounter++;
								if(typeCounter==numTypes)
									break;
							}
							if(typeCounter==numTypes)
								break;
						}
						if(typeCounter==numTypes)
							break;
					}
					if(colorBasedOnTypeWithColorList==true)
					{
						colorsByType[0][0]=1;
						colorsByType[0][1]=0;
						colorsByType[0][2]=0;
						colorsByType[1][0]=0.5;
						colorsByType[1][1]=1;
						colorsByType[1][2]=1;
						colorsByType[2][0]=0.25;
						colorsByType[2][1]=0.25;
						colorsByType[2][2]=0.25;
						colorsByType[3][0]=1;
						colorsByType[3][1]=0;
						colorsByType[3][2]=1;
						colorsByType[4][0]=0;
						colorsByType[4][1]=0.5;
						colorsByType[4][2]=0;
						colorsByType[5][0]=0;
						colorsByType[5][1]=0;
						colorsByType[5][2]=1;
						colorsByType[6][0]=1;
						colorsByType[6][1]=1;
						colorsByType[6][2]=0;
						/*
						colorsByType[7][0]=0.75;
						colorsByType[7][1]=0.75;
						colorsByType[7][2]=0.75;
						colorsByType[8][0]=0;
						colorsByType[8][1]=1;
						colorsByType[8][2]=0;
						 */
					}
					// Main loop to create nodes based on data in AllLinesPD
					for(int i=0; i<lineCount; i++)
					{
						double currentType;	
						if(colorBasedOnType==true || colorBasedOnTypeWithColorList==true)
						{
							currentType=Double.parseDouble(AllLinesPD[i][6]);
							redVal=colorsByType[(int)currentType][0];
							greenVal=colorsByType[(int)currentType][1];
							blueVal=colorsByType[(int)currentType][2];
							textureVal="Shiny";
						}
						if(colorBasedOnDegree==true)
						{
							// Determine color based on node degree
							// Note that these values are determined previously by the NodeSortByDegree program
							if(Integer.parseInt(AllLinesPD[i][5])>0)
								textureVal="ShinyDarkTurquoise";
							if(Integer.parseInt(AllLinesPD[i][5])>50)
								textureVal="ShinyForestGreen";
							if(Integer.parseInt(AllLinesPD[i][5])>75)
								textureVal="ShinyYellow";
							if(Integer.parseInt(AllLinesPD[i][5])>88)
								textureVal="ShinyOrange";
							if(Integer.parseInt(AllLinesPD[i][5])>94)
								textureVal="ShinyRed";
							if(Integer.parseInt(AllLinesPD[i][5])>97)
								textureVal="ShinyFirebrick";	
							if(Integer.parseInt(AllLinesPD[i][5])>98)
								textureVal="ShinyViolet";
						}


						// USE THIS BLOCK IF CALCULATING COLORS BASED ON POSITION
						double xrange=minmax[0][1]-minmax[0][0];
						double yrange=minmax[1][1]-minmax[1][0];
						double zrange=minmax[2][1]-minmax[2][0];
						if(colorBasedOnPosition==true)
						{
							redVal=(Double.parseDouble(AllLinesPD[i][0])-minmax[0][0])/xrange;
							greenVal=(Double.parseDouble(AllLinesPD[i][1])-minmax[1][0])/yrange;
							blueVal=(Double.parseDouble(AllLinesPD[i][2])-minmax[2][0])/yrange;
						}

						output.write("union {");output.newLine();
						output.write("  object {");output.newLine();


						// output.write("          <scale>" + Math.log(2*placemarkScale)*placemarkSizeFactor + "</scale>");output.newLine();


						// output.write("    Sphere_Cap(0.2, " + iconScale*(Math.log(2*Double.parseDouble(AllLinesPD[i][3]))) + ")");output.newLine();
						String currentNode=AllLinesPD[i][7]; // 

						//						output.write("    " + currentNode + "(0.05, " + iconScale*(Math.log(2*Double.parseDouble(AllLinesPD[i][3]))) + ")");output.newLine();
						//						output.write("    " + currentNode + "(" + iconScale*(Math.log(Double.parseDouble(AllLinesPD[i][3]))) + ")");output.newLine();
						output.write("    " + currentNode + "(" + iconScale*(Math.sqrt(Double.parseDouble(AllLinesPD[i][3]))) + ")");output.newLine();
						//	if(currentNode.equals("institution"))
						//		output.write("    " + currentNode + "(0.2, " + iconScale*(Math.log(2*Double.parseDouble(AllLinesPD[i][3]))) + ")");output.newLine();

						// Remember to reverse the Y and Z values for POV output:
						output.write("    translate <" + ((Double.parseDouble(AllLinesPD[i][0]))*pxScale) + ", " + ((Double.parseDouble(AllLinesPD[i][1]))*pyScale)+","+((Double.parseDouble(AllLinesPD[i][2]))*pzScale)+">");output.newLine();
						output.write("  }");output.newLine();
						if(colorBasedOnDegree==true || colorBasedOnType==true || colorBasedOnTypeWithColorList==true || colorBasedOnPosition==true)
							output.write("  texture { " + textureVal + " }");output.newLine();
							if(colorBasedOnDegree==true || colorBasedOnType==true || colorBasedOnTypeWithColorList==true || colorBasedOnPosition==true)
								output.write("  pigment{rgb<" + redVal + "," + greenVal + "," + blueVal + ">}");output.newLine();
								output.write("}");output.newLine();	
								output.write("  ");output.newLine();
					}
/*
					// GENERATE LINKS

					for(int i=0; i<lineCountLinks; i++)
					{	
						output.write("union {");output.newLine();
						output.write("  object {");output.newLine();
						output.write("    Round_Tube(<"+((Double.parseDouble(AllLinesLD[i][0]))*pxScale)+", " +
								((Double.parseDouble(AllLinesLD[i][1]))*pyScale)+", "+
								((Double.parseDouble(AllLinesLD[i][2]))*pzScale)+">,<"+
								((Double.parseDouble(AllLinesLD[i][3]))*pxScale)+", "+
								((Double.parseDouble(AllLinesLD[i][4]))*pyScale)+", "+
								//								((Double.parseDouble(AllLinesLD[i][5]))*pzScale)+">, 0.5, 0.25, 0.1, no)");output.newLine();
								//								((Double.parseDouble(AllLinesLD[i][4]))*pzScale)+">, 0.01, 0.005, 0.002, no)");output.newLine();
								((Double.parseDouble(AllLinesLD[i][5]))*pzScale)+">, 0.1, 0.05, 0.020, no)");output.newLine();
								//						output.write("    translate <0,0,0>");output.newLine();
								output.write("  }");output.newLine();
								output.write("  texture { Shiny }");output.newLine();
								output.write("  pigment { White transmit -0.2}");output.newLine();
								output.write("}");output.newLine();
					}
					inLD.close(); // Done with links data
*/
					// Rotate entire network here
					// output.write("rotate <0,0,90>");output.newLine();
					// Translate entire network here
					// output.write("translate <-8,-10,0>");output.newLine();
					output.write("}");output.newLine(); // End grouping of nodes and links to allow for rotation of entire network
					// Generate optional color and type legends
					int generateColorLegend=0;
					int generateShapeLegend=0;
					// Modify size of legend boxes here
					double boxSize=2;
					// double whiteSpaceAtTop=10; // Was used for obesity project
					double whiteSpaceAtTop=-4;
					double ymax=minmax[1][1];
					double ymin=minmax[1][0];
					double yrange=ymax-ymin;
					double xmax=minmax[0][1];
					double xmin=minmax[0][0];
					double xrange=xmax-xmin;
					// double yspacing=(yrange/numTypes)*0.3;
					// double yspacing=(yrange/numTypes)*0.37;
					double yspacing=(yrange/numTypes)*0.45;
					// double xdisplacement1=-xrange*-0.2; // For types legend; was used for obesity project
					// double xdisplacement2=-xrange*-0.2; // For shapes legend; was used for obesity project
					double xdisplacement1=-xrange*-0.05; // For types legend
					double xdisplacement2=-xrange*-0.05; // For shapes legend


					int position=0;
					if(generateColorLegend==1)
					{
						for(int i=0; i<numTypes; i++)
						{
							redVal=colorsByType[i][0];
							greenVal=colorsByType[i][1];
							blueVal=colorsByType[i][2];

							output.write("union {");output.newLine();
							output.write("  object {");output.newLine();
							output.write("  shortbox("+boxSize+")");output.newLine();
							output.write("  translate<" + ((xmax-xdisplacement1)*pxScale) + ", " + (ymax-(i+whiteSpaceAtTop)*yspacing)*pyScale + ", " + 0.0 +">");output.newLine();
							output.write("  }");output.newLine();
							output.write("  texture { Shiny }");output.newLine();
							output.write("  pigment{rgb<" + redVal + "," + greenVal + "," + blueVal + ">}");output.newLine();
							output.write("  }");output.newLine();
						}
					}
					if(generateShapeLegend==1)
					{
						// int startingDisplacement=20; // to account for space occupied by color legend and white space at top // Was used for obesity project
						int startingDisplacement=3; // to account for space occupied by color legend and white space at top
						position=1;
						output.write("union {");output.newLine();
						output.write("  object {");output.newLine();
						// output.write("  deanprof("+boxSize+")");output.newLine();
						output.write("  nodetype1("+boxSize+")");output.newLine();
						output.write("  translate<" + ((xmax-xdisplacement2)*pxScale) + ", " + ((ymax-((startingDisplacement+position)*yspacing))*pyScale) + ", " + 0.7 +">");output.newLine();
						output.write("  }");output.newLine();
						output.write("  texture { Shiny }");output.newLine();
						output.write("  pigment{rgb<0.95,0.95,0.95>}");output.newLine();
						output.write("  }");output.newLine();

						position=2;
						output.write("union {");output.newLine();
						output.write("  object {");output.newLine();
						// output.write("  assocprof("+boxSize+")");output.newLine();
						output.write("  nodetype2("+boxSize+")");output.newLine();
						output.write("  translate<" + ((xmax-xdisplacement2)*pxScale) + ", " + ((ymax-((startingDisplacement+position)*yspacing))*pyScale) + ", " + 0.7 +">");output.newLine();
						output.write("  }");output.newLine();
						output.write("  texture { Shiny }");output.newLine();
						output.write("  pigment{rgb<0.95,0.95,0.95>}");output.newLine();
						output.write("  }");output.newLine();

						position=3;
						output.write("union {");output.newLine();
						output.write("  object {");output.newLine();
						// output.write("  asstprof("+boxSize+")");output.newLine();
						output.write("  nodetype3("+boxSize+")");output.newLine();
						output.write("  translate<" + ((xmax-xdisplacement2)*pxScale) + ", " + ((ymax-((startingDisplacement+position)*yspacing))*pyScale) + ", " + 0.7 +">");output.newLine();
						output.write("  }");output.newLine();
						output.write("  texture { Shiny }");output.newLine();
						output.write("  pigment{rgb<0.95,0.95,0.95>}");output.newLine();
						output.write("  }");output.newLine();

						position=4;
						output.write("union {");output.newLine();
						output.write("  object {");output.newLine();
						// output.write("  otherrank("+boxSize+")");output.newLine();
						output.write("  nodetype4("+boxSize+")");output.newLine();
						output.write("  translate<" + ((xmax-xdisplacement2)*pxScale) + ", " + ((ymax-((startingDisplacement+position)*yspacing))*pyScale) + ", " + 0.7 +">");output.newLine();
						output.write("  }");output.newLine();
						output.write("  texture { Shiny }");output.newLine();
						output.write("  pigment{rgb<0.95,0.95,0.95>}");output.newLine();
						output.write("  }");output.newLine();

						position=5;
						output.write("union {");output.newLine();
						output.write("  object {");output.newLine();
						// output.write("  unknownrank("+boxSize+")");output.newLine();
						output.write("  nodetype5("+boxSize+")");output.newLine();
						output.write("  translate<" + ((xmax-xdisplacement2)*pxScale) + ", " + ((ymax-((startingDisplacement+position)*yspacing))*pyScale) + ", " + 0.0 +">");output.newLine();
						output.write("  }");output.newLine();
						output.write("  texture { Shiny }");output.newLine();
						output.write("  pigment{White}");output.newLine();
						output.write("  }");output.newLine();

					}

				}

				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG
				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG
				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG
				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG
				// ---------------------------------------------------------------------------------------------------------------
				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG
				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG
				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG
				// ---------------------------------------------------------------------------------------------------------------
				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG
				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG
				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG
				// ---------------------------------------------------------------------------------------------------------------
				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG
				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG
				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG
				// ---------------------------------------------------------------------------------------------------------------
				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG
				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG
				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG
				// ---------------------------------------------------------------------------------------------------------------
				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG
				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG
				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG
				// ---------------------------------------------------------------------------------------------------------------
				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG
				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG
				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG
				// ---------------------------------------------------------------------------------------------------------------
				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG
				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG
				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG
				// ---------------------------------------------------------------------------------------------------------------
				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG
				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG
				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG
				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG
				// ---------------------------------------------------------------------------------------------------------------
				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG
				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG
				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG
				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG
				// SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG SVG

				if(outFormat==SVG)
					// SVG OUTPUT FORMAT
				{
					// double svgwidth=1920;
					// double svgheight=1080;

					double svgwidth=1000*1;
					double svgheight=750*1;

					// double svgwidth=640;
					// double svgheight=480;

					output.write("<?xml version=\"1.0\"?>");output.newLine();
					output.write("<svg width=\"" + svgwidth + "\"" + " height=\"" + svgheight + "\" xmlns=\"http://www.w3.org/2000/svg\">");output.newLine();
					output.write("  <title>Sciologer output</title>");output.newLine();
					// double xyScale=10;
					double xyScale=svgwidth/850;
					// double xyScale=3000; //TEMP; use above one
					double iconResizeFactorSVG=svgwidth/2000;
					// double iconResizeFactorSVG=svgwidth/200000; //TEMP; use above one
					double svgxmid=svgwidth/2;
					double svgymid=svgheight/2;
					String opacityValSVG=""; // Transparency value or "alpha"
					String blueValSVG="";	
					String greenValSVG="";
					String redValSVG="";
// 					double fillOpacity=0.8;
//					double strokeOpacity=0.8;
					double fillOpacity=1;
					double strokeOpacity=1;
					
					boolean colorBasedOnPosition=false;
					boolean colorBasedOnCustomSpectrum=true;

					// Generate Links
					// output.write("  <g stroke=\"lightgrey\" >");output.newLine(); // begin group for links
					// output.write("  <g stroke=\"darkgrey\" >");output.newLine(); // begin group for links
					output.write("  <g stroke=\"darkslategrey\" >");output.newLine(); // begin group for links
					for(int i=0; i<lineCountLinks; i++)
					{
						double lx1=(Double.parseDouble(AllLinesLD[i][0]))*xyScale;
						double ly1=(Double.parseDouble(AllLinesLD[i][1]))*xyScale;
						double lz1=(Double.parseDouble(AllLinesLD[i][2]))*xyScale; // Not used
						double lx2=(Double.parseDouble(AllLinesLD[i][3]))*xyScale;
						double ly2=(Double.parseDouble(AllLinesLD[i][4]))*xyScale;
						double lz2=(Double.parseDouble(AllLinesLD[i][5]))*xyScale;  // Not used

						double lx1Orig=lx1;
						double ly1Orig=ly1;
						double lx2Orig=lx2;
						double ly2Orig=ly2;

						// Rotate X and Y values. See http://en.wikipedia.org/wiki/Rotation_%28mathematics%29 for rotation formula
						double theta=0; // Angle of rotation of link positions (e.g., 90)
						
						
						lx1=lx1Orig*Math.cos(theta)-ly1Orig*Math.sin(theta);
						ly1=lx1Orig*Math.sin(theta)+ly1Orig*Math.cos(theta);
						lx2=lx2Orig*Math.cos(theta)-ly2Orig*Math.sin(theta);
						ly2=lx2Orig*Math.sin(theta)+ly2Orig*Math.cos(theta);

						// Center links on canvas and reduce decimal precision
						lx1=round2(lx1+svgxmid);
						lx2=round2(lx2+svgxmid);
						ly1=round2(ly1+svgymid);
						ly2=round2(ly2+svgymid);

						output.write("    <line x1=\"" + lx1 + "\" y1=\"" + ly1 + "\" x2=\"" + lx2 + "\" y2=\"" + ly2 + "\" stroke-width=\"" + round2(xyScale/5) + "\" />");output.newLine();
					}
					output.write("  </g>");output.newLine(); // End group for links

					output.write("  <g>");output.newLine(); // Begin group for nodes
					for(int i=0; i<lineCount; i++)
					{
						double iconScaleSVG=(Double.parseDouble(AllLinesPD[i][3]))*iconResizeFactorSVG;
						// iconScaleSVG=iconScaleSVG*iconResizeFactorSVG; // Resize icon based on resize factor
						double xvalSVG=((Double.parseDouble(AllLinesPD[i][0]))*xyScale);
						double yvalSVG=((Double.parseDouble(AllLinesPD[i][1]))*xyScale);
						double xvalSVGOrig=xvalSVG;
						double yvalSVGOrig=yvalSVG;
						// Rotate X and Y values. See http://en.wikipedia.org/wiki/Rotation_%28mathematics%29 for rotation formula
						double thetaN=0; // Angle of rotation of node positions

						xvalSVG=xvalSVGOrig*Math.cos(thetaN)-yvalSVGOrig*Math.sin(thetaN);
						yvalSVG=xvalSVGOrig*Math.sin(thetaN)+yvalSVGOrig*Math.cos(thetaN);
						// Center image on canvas
						xvalSVG=xvalSVG+svgxmid;
						yvalSVG=yvalSVG+svgymid;

						String nodeShapeSVG="";
						
						
						// USE THIS BLOCK IF CALCULATING COLORS BASED ON POSITION
						double xrange=minmax[0][1]-minmax[0][0];
						double yrange=minmax[1][1]-minmax[1][0];
						double zrange=minmax[2][1]-minmax[2][0];
						
						if(colorBasedOnPosition==true)
						{					
							double myRedVal=(Double.parseDouble(AllLinesPD[i][0])-minmax[0][0])/xrange;
							int myRedInt=(int)(round3(myRedVal));
							redValSVG=Integer.toHexString(myRedInt);
							
							if (redValSVG.length()==1)
							{
								redValSVG="0" + redValSVG;
							}
							
							double myGreenVal=(Double.parseDouble(AllLinesPD[i][1])-minmax[1][0])/yrange;
							int myGreenInt=(int)(round3(myGreenVal));
							greenValSVG=Integer.toHexString(myGreenInt);
							
							if (greenValSVG.length()==1)
							{
								greenValSVG="0" + greenValSVG;
							}
							
							double myBlueVal=(Double.parseDouble(AllLinesPD[i][2])-minmax[2][0])/zrange;
							int myBlueInt=(int)(round3(myBlueVal));
							blueValSVG=Integer.toHexString(myBlueInt);
							
							if (blueValSVG.length()==1)
							{
								blueValSVG="0" + blueValSVG;
							}
						}
												
						if(colorBasedOnCustomSpectrum==true)
						{
						// Set color based on custom spectrum	
						if(AllLinesPD[i][6].equals("0"))
							// coral red
						{
							opacityValSVG="CC";
							blueValSVG="40";
							greenValSVG="40";
							redValSVG="FF";
						}
						if(AllLinesPD[i][6].equals("2"))
							// scarlet 
						{
							opacityValSVG="CC";
							blueValSVG="00";
							greenValSVG="24";
							redValSVG="FF";
						}
						if(AllLinesPD[i][6].equals("1"))
							// light blue
						{
							opacityValSVG="CC";
							blueValSVG="FF";
							greenValSVG="99";
							redValSVG="66";
						}
						if(AllLinesPD[i][6].equals("3"))
							// dark red
						{
							opacityValSVG="CC";
							blueValSVG="00";
							greenValSVG="00";
							redValSVG="8B";
						}
						if(AllLinesPD[i][6].equals("4"))
							// orange
						{
							opacityValSVG="CC";
							blueValSVG="00";
							greenValSVG="66";
							redValSVG="FF";
						}

						if(AllLinesPD[i][6].equals("5"))
							// blue
						{
							opacityValSVG="CC";
							blueValSVG="FF";
							greenValSVG="00";
							redValSVG="00";
						}
						if(AllLinesPD[i][6].equals("6"))
							// yellow
						{
							opacityValSVG="CC";
							blueValSVG="00";
							greenValSVG="FF";
							redValSVG="FF";
						}
						}
						// Resize nodes based on logarithmic transform
						// iconScaleSVG=Math.log(1*iconScaleSVG)*Math.log(5*iconScaleSVG)*iconResizeFactorSVG;
						// iconScaleSVG=Math.log(10*iconScaleSVG)*iconResizeFactorSVG;
						iconScaleSVG=Math.log(10*iconScaleSVG)*xyScale/4;


						// Make final adjustments to equalize icon scale to make shapes visually equivalent in size
						if(AllLinesPD[i][7].equals("department"))
						{
							nodeShapeSVG="ellipse";
							iconScaleSVG=iconScaleSVG*1.5;
						}
						if(AllLinesPD[i][7].equals("principal-investigator"))
						{
							nodeShapeSVG="ellipse";
							iconScaleSVG=iconScaleSVG*1.5;
						}
						if(AllLinesPD[i][7].equals("research-scientist"))
						{
							nodeShapeSVG="triangle";
							iconScaleSVG=iconScaleSVG*1.9;
						}
						if(AllLinesPD[i][7].equals("seed-fund-recipient"))
						{
							nodeShapeSVG="inverted-triangle";
							iconScaleSVG=iconScaleSVG*1.9;
						}
						if(AllLinesPD[i][7].equals("collaborator-university"))
						{
							nodeShapeSVG="diamond";
							iconScaleSVG=iconScaleSVG*1.9;
						}
						if(AllLinesPD[i][7].equals("collaborator-industry"))
						{
							nodeShapeSVG="square";
							iconScaleSVG=iconScaleSVG*2.8;
						}
						if(AllLinesPD[i][7].equals("nodetype6"))
						{
							iconScaleSVG=iconScaleSVG*10;
						}
						if(AllLinesPD[i][7].equals("gene"))
						{
							nodeShapeSVG="ellipse";
							iconScaleSVG=iconScaleSVG*1.8;
						}
						if(AllLinesPD[i][7].equals("region"))
						{
							nodeShapeSVG="square";
							iconScaleSVG=iconScaleSVG*1.4;
						}
						if(AllLinesPD[i][7].equals("argene"))
						{
							nodeShapeSVG="ellipse";
							iconScaleSVG=iconScaleSVG*1.8;
						}
						if(nodeShapeSVG.equals("ellipse"))
							// With outline (stroke-width):
							// output.write("    <ellipse fill=\"#" + redValSVG + greenValSVG + blueValSVG + "\" stroke=\"black\" fill-opacity=\"" + fillOpacity + "\" stroke-opacity=\"" + strokeOpacity +"\" stroke-width=\"" + round2(xyScale/3) + "\" cx=\""+round2(xvalSVG)+"\" cy=\""+round2(yvalSVG)+"\" rx=\"" + round2(iconScaleSVG) + "\" ry=\"" + round2(iconScaleSVG) + "\"/>");output.newLine();
							// Without outline (stroke-width):
							output.write("    <ellipse fill=\"#" + redValSVG + greenValSVG + blueValSVG + 
									"\" stroke=\"black\" fill-opacity=\"" + fillOpacity + "\" stroke-opacity=\"" + strokeOpacity +
									"\" stroke-width=\"" + round2(xyScale/10)+"\" cx=\""+round2(xvalSVG)+"\" cy=\""+round2(yvalSVG)+
									"\" rx=\"" + round2(iconScaleSVG) + "\" ry=\"" + round2(iconScaleSVG) + "\"/>");output.newLine();
							if(nodeShapeSVG.equals("triangle"))
							{
								double triangleTopX=round2(xvalSVG);
								double triangleTopY=round2(yvalSVG-iconScaleSVG);
								double triangleLowerLeftX=round2(xvalSVG-iconScaleSVG*1.73205081/2); //
								double triangleLowerLeftY=round2(yvalSVG+iconScaleSVG/2); //
								double triangleLowerRightX=round2(xvalSVG+iconScaleSVG*1.73205081/2); //
								double triangleLowerRightY=round2(yvalSVG+iconScaleSVG/2); //
								output.write("    <polygon fill=\"#" + redValSVG + greenValSVG + blueValSVG + "\" stroke=\"black\" fill-opacity=\"" + fillOpacity + "\" stroke-opacity=\"" + strokeOpacity +"\" stroke-width=\"" + round2(xyScale/3) + "\" points=\"" +
										triangleTopX + "," + triangleTopY + " " + triangleLowerLeftX + "," + triangleLowerLeftY + " " + triangleLowerRightX + "," + triangleLowerRightY +"\" />");output.newLine();
							}
							if(nodeShapeSVG.equals("inverted-triangle"))
							{
								//							<ellipse transform="translate(900 200) rotate(-30)" 
								double triangleTopX=round2(xvalSVG);
								double triangleTopY=round2(yvalSVG-iconScaleSVG);
								double triangleLowerLeftX=round2(xvalSVG-iconScaleSVG*1.73205081/2); //
								double triangleLowerLeftY=round2(yvalSVG+iconScaleSVG/2); //
								double triangleLowerRightX=round2(xvalSVG+iconScaleSVG*1.73205081/2); //
								double triangleLowerRightY=round2(yvalSVG+iconScaleSVG/2); //
								output.write("    <polygon transform=\"rotate(60 " + xvalSVG + " " + yvalSVG + ")\" fill=\"#" + redValSVG + greenValSVG + blueValSVG + "\" stroke=\"black\" fill-opacity=\"" + fillOpacity + "\" stroke-opacity=\"" + strokeOpacity +"\" stroke-width=\"" + round2(xyScale/3) + "\" points=\"" +
										triangleTopX + "," + triangleTopY + " " + triangleLowerLeftX + "," + triangleLowerLeftY + " " + triangleLowerRightX + "," + triangleLowerRightY +"\" />");output.newLine();							
							}
							if(nodeShapeSVG.equals("square"))
							{
								output.write("    <rect fill=\"#" + redValSVG + greenValSVG + blueValSVG + 
										"\" stroke=\"black\" fill-opacity=\"" + fillOpacity + "\" stroke-opacity=\"" + strokeOpacity +
										"\" stroke-width=\"" + round2(xyScale/5) + "\" x=\""+round2((xvalSVG-iconScaleSVG/2))+
										"\" y=\""+round2((yvalSVG-iconScaleSVG/2))+"\" width=\"" + iconScaleSVG + "\" height=\"" + 
										iconScaleSVG + "\"/>");output.newLine();
							}
							if(nodeShapeSVG.equals("diamond"))
							{
								double diamondTopX=round2(xvalSVG);
								double diamondTopY=round2(yvalSVG-iconScaleSVG);
								double diamondRightX=round2(xvalSVG+iconScaleSVG);
								double diamondRightY=round2(yvalSVG);
								double diamondBottomX=round2(xvalSVG);
								double diamondBottomY=round2(yvalSVG+iconScaleSVG);
								double diamondLeftX=round2(xvalSVG-iconScaleSVG);
								double diamondLeftY=round2(yvalSVG);
								output.write("    <polygon fill=\"#" + redValSVG + greenValSVG + blueValSVG + "\" stroke=\"black\" fill-opacity=\"" + fillOpacity + "\" stroke-opacity=\"" + strokeOpacity +"\" stroke-width=\"" + round2(xyScale/3) + "\" points=\"" +
										diamondTopX + "," + diamondTopY + " " + diamondRightX + "," + diamondRightY + " " + diamondBottomX + "," + diamondBottomY + " " + diamondLeftX + "," + diamondLeftY +"\" />");output.newLine();

										// OLD output.write("<rect transform=\"rotate(45)\" fill=\"#" + redValSVG + greenValSVG + blueValSVG + "\" stroke=\"black\" stroke-width=\"3\" x=\""+xvalSVG+"\" y=\""+yvalSVG+"\" width=\"" + iconScaleSVG + "\" height=\"" + iconScaleSVG + "\"/>");output.newLine();
										// OLD output.write("<rect transform=\"rotate(45 " + xvalSVG + " " + yvalSVG + ")\" fill=\"#" + redValSVG + greenValSVG + blueValSVG + "\" stroke=\"black\" fill-opacity=\"" + fillOpacity + "\" stroke-opacity=\"" + strokeOpacity +"\" stroke-width=\"" + xyScale/3 + "\" x=\""+(xvalSVG-iconScaleSVG/2)+"\" y=\""+(yvalSVG-iconScaleSVG/2)+"\" width=\"" + iconScaleSVG + "\" height=\"" + iconScaleSVG + "\"/>");output.newLine();
							}
							// Add label
							String longLabelSVG=AllLinesPD[i][8].replaceAll("\"", "");
							double labelOffsetX=iconScaleSVG/1.5;
							double labelOffsetY=iconScaleSVG/2;			
							double fontSizeSVG=round2(xyScale*0.6);
							// double fontSizeSVG=round2(xyScale*0.01); // TEMP. Use above.
//							output.write("    <text x=\""+round2((xvalSVG+labelOffsetX))+"\" y=\""+round2((yvalSVG-labelOffsetY))+"\" font-family=\"Tahoma\" font-weight=\"bold\" font-size=\"" + round2(xyScale*1) + "\" fill=\"black\" >" + longLabelSVG + "</text>");output.newLine();
//PUTITBACK							output.write("    <text x=\""+round2((xvalSVG+labelOffsetX))+"\" y=\""+round2((yvalSVG-labelOffsetY))+"\" font-family=\"Tahoma\" font-weight=\"bold\" font-size=\"" + fontSizeSVG + "\" fill=\"#" + redValSVG + greenValSVG + blueValSVG +"\" >" + longLabelSVG + "</text>");output.newLine();
							
							
							 
					}
					output.write("  </g>");output.newLine(); // end group for nodes
					output.write("</svg>");output.newLine();
				}
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML
				// ---------------------------------------------------------------------------------------------------------------
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML
				// ---------------------------------------------------------------------------------------------------------------
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML
				// ---------------------------------------------------------------------------------------------------------------
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML
				// ---------------------------------------------------------------------------------------------------------------
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML
				// ---------------------------------------------------------------------------------------------------------------
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML
				// ---------------------------------------------------------------------------------------------------------------
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML
				// ---------------------------------------------------------------------------------------------------------------
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML
				// ---------------------------------------------------------------------------------------------------------------
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML
				// ---------------------------------------------------------------------------------------------------------------
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML
				// KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML KML

				if(outFormat==KML)
					// KML OUTPUT FORMAT
				{
					output.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");output.newLine();
					output.write("<kml xmlns=\"http://www.opengis.net/kml/2.2\">");output.newLine();
					output.write("<Document>");output.newLine();
					output.write("<name>"+nodeData.replace(".kml","") +"</name>");output.newLine();
					output.write("  <Style id=\"radioFolder\">");output.newLine();
					output.write("    <ListStyle>");output.newLine();
					output.write("      <listItemType>radioFolder</listItemType>");output.newLine();
					output.write("    </ListStyle>");output.newLine();
					output.write("  </Style>");output.newLine();
					output.write("  <Style id=\"myBalloonStyle\">");output.newLine();
					output.write("    <BalloonStyle>");output.newLine();
					output.write("      <bgColor>ffffffff</bgColor>");output.newLine();
					output.write("      <text><![CDATA[");output.newLine();
					output.write("      <b><font color=\"#CC0000\" size=\"+3\">$[name]</font></b>");output.newLine();
					output.write("      $[description]");output.newLine();
					output.write("      <br/><br/>");output.newLine();
					output.write("      ]]></text>");output.newLine();
					output.write("    </BalloonStyle>");output.newLine();
					output.write("  </Style>");output.newLine();
					//					output.write("  <Folder>");output.newLine();
					output.write("      <LookAt>");output.newLine();
					// MODIFY MAIN SCALE VARIABLE HERE
					double mainScale=0.5;
					// double mainScale=0.1; OLD DEFAULT
					// NOTE -- in Google Earth version 5.1.3533.1731 released in November 2009
					// mainScale set to 0.1 caused large icon sizes and disappearing icons
					// for Pajek-generated positions
					// double mainScale=100;
					// MODIFY ALTITUDE DISPLACEMENT HERE
					// double altitudeDisplacement=mainScale*250000; OLD DEFAULT
					double altitudeDisplacement=mainScale*50000; // CURRENT 7-2010 Used a long time with 15 CCVisu iterations -- disappearing icons less of a problem
					// double altitudeDisplacement=mainScale*250000; // Used a long time more recently with 15 CCVisu iterations -- good for small net
					// double altitudeDisplacement=mainScale*750000; // Used to produce large network
					//		double altitudeDisplacement=mainScale*25000;
					//					// MODIFY ALTITUDE SCALE HERE
					// double altitudeScale=mainScale*20; OLD DEFAULT
					double altitudeScale=mainScale*150; // CURRENT 6-2010
					// double altitudeScale=mainScale*250; // NEW 6-2010
					//					double altitudeScale=mainScale*100;
					// MODIFY LATITUDE/LONGITUDE SCALE HERE
					//					double xyScale=mainScale*0.001; OLD DEFAULT
					double xyScale=mainScale*0.01; // TEMP
					// double xyScale=mainScale*0.01; // CURRENT 10-2010
					// double xyScale=mainScale*0.02; // NEW 6-2010
					// MODIFY RANGE SCALE HERE
					// double rangeScale=mainScale*100000;
					double colorScale=255; // Default is 255; can be raised to turn outliers white and achieve more color variation in main graph
//					double rangeScale=mainScale*2000000; // CURRENT 7-2010
					double rangeScale=mainScale*00000; // TEMP 11-2010 
					// double rangeScale=mainScale*1300000; // Used for large network
					// double placemarkScale=0.2;
					double placemarkScale=0; // Value is assigned later
					// ADJUST SIZE OF PLACEMARKS HERE
					// MODIFYING THE NEXT LINE WILL CHANGE THE SIZE OF ALL PLACEMARKS
					// double placemarkSizeFactor=mainScale*2;
					//					double placemarkSizeFactor=mainScale*0.7;
					//					double placemarkSizeFactor=mainScale*1.2;
					//					// double placemarkSizeFactor=mainScale*0.2; RECENT 3-2009
					// double placemarkSizeFactor=mainScale*0.5; RECENT 1-2010
					// double placemarkSizeFactor=mainScale*0.1; CURRENT
					// double placemarkSizeFactor=mainScale*0.1; // CURRENT 10-2014
					// double placemarkSizeFactor=mainScale*0.16; // CURRENT 12-2010
					// double placemarkSizeFactor=mainScale*0.02; // Used for large networks
					double placemarkSizeFactor=mainScale*0.05; // Used for large networks
					double colorBasedOnPosition=1; // 0 for false, 1 for true
					double colorBasedOnDegree=0; // 0 for false, 1 for true
					double colorKMLBasedOnType=0;
					double colorKMLBasedOnCategoryStandardSpectrum=0;
					double colorKMLBasedOnCategoryCustomSpectrum=0;
					double colorKMLBasedOnCategoryHurricaneSpectrum=0;
					String label=""; // Will be used when creating shortened labels
					String longLabel=""; // Will be used when creating shortened labels
					String shortenedLabel=""; // Will be used when creating shortened labels
					output.write("        <altitude>" + altitudeDisplacement + "</altitude>");output.newLine();
					output.write("        <heading>0</heading>");output.newLine();
					output.write("        <tilt>0</tilt>");output.newLine();
					output.write("        <range>" + rangeScale + "</range>");output.newLine();
					// output.write("        <altitudeMode>clamptoground</altitudeMode>");output.newLine();
					output.write("        <altitudeMode>absolute</altitudeMode>");output.newLine();
					output.write("      </LookAt>");output.newLine();

					output.write("  <Folder>");output.newLine();
					output.write(" <name>Backgrounds</name>");output.newLine();
					output.write("      <open>1</open>");output.newLine();
					// ADD GREY GROUND OVERLAY
					output.write("		<GroundOverlay>");output.newLine();
					output.write("   	  <name>Grey background</name>");output.newLine();
					output.write("  	  <drawOrder>1</drawOrder>");output.newLine();
					output.write("				<Icon>");output.newLine();				
					output.write("				  <href>models/iconGrey.gif</href>");output.newLine();
					output.write("				</Icon>");output.newLine();
					output.write("        <LatLonBox>");output.newLine();
					output.write("          <north>90</north>");output.newLine();
					output.write("          <south>-90</south>");output.newLine();
					output.write("          <east>200</east>");output.newLine();
					output.write("          <west>-200</west>");output.newLine();
					output.write("          <rotation>0</rotation>");output.newLine();
					output.write("        </LatLonBox>");output.newLine();
					output.write("      </GroundOverlay>");output.newLine();

					// ADD BLACK GROUND OVERLAY
					output.write("		<GroundOverlay>");output.newLine();
					output.write("   	  <name>Black background</name>");output.newLine();
					output.write("  	  <drawOrder>1</drawOrder>");output.newLine();
					output.write("				<Icon>");output.newLine();				
					output.write("				  <href>models/iconBlack.gif</href>");output.newLine();
					output.write("				</Icon>");output.newLine();
					output.write("        <LatLonBox>");output.newLine();
					output.write("          <north>90</north>");output.newLine();
					output.write("          <south>-90</south>");output.newLine();
					output.write("          <east>200</east>");output.newLine();
					output.write("          <west>-200</west>");output.newLine();
					output.write("          <rotation>0</rotation>");output.newLine();
					output.write("        </LatLonBox>");output.newLine();
					output.write("      </GroundOverlay>");output.newLine();

					// ADD WHITE GROUND OVERLAY
					output.write("		<GroundOverlay>");output.newLine();
					output.write("   	  <name>White background</name>");output.newLine();
					output.write("  	  <drawOrder>1</drawOrder>");output.newLine();
					output.write("				<Icon>");output.newLine();				
					output.write("				  <href>models/iconWhite.gif</href>");output.newLine();
					output.write("				</Icon>");output.newLine();
					output.write("        <LatLonBox>");output.newLine();
					output.write("          <north>90</north>");output.newLine();
					output.write("          <south>-90</south>");output.newLine();
					output.write("          <east>200</east>");output.newLine();
					output.write("          <west>-200</west>");output.newLine();
					output.write("          <rotation>0</rotation>");output.newLine();
					output.write("        </LatLonBox>");output.newLine();
					output.write("      </GroundOverlay>");output.newLine();
					output.write("      <styleUrl>#radioFolder</styleUrl>");output.newLine();
					output.write("  </Folder>");output.newLine(); // End of backgrounds


					// GENERATE NODES
					output.write("      <Folder>");output.newLine(); // Folder to contain nodes
					output.write("      <name>Nodes</name>");output.newLine();

					String currentNode="";
					String currentNodeType="";
					String expectedNodeType="";
					int expectedTypeFlag=0;

					for(int i=0; i<lineCount; i++)
					{
						// Tokenize nodes to determine whether a new folder should be opened
						StringTokenizer tokenizer=new StringTokenizer(AllLinesPD[i][4],"_");
						while(tokenizer.hasMoreTokens())
						{
							currentNodeType=tokenizer.nextToken();
							currentNode=tokenizer.nextToken();
							break;
						}
						if(i==0) // If this is the first node encountered in the file, make a new folder
						{
							expectedTypeFlag=1;
							expectedNodeType=currentNodeType;
							output.write("         <Folder>");output.newLine(); // Folder to contain nodes of this type
							output.write("         <name>" + currentNodeType + "</name>");output.newLine();
						}
						if(currentNodeType.equals(expectedNodeType))
							expectedTypeFlag=1;
						if(expectedTypeFlag==0) // A new node type is encountered; close old folder and open a new one
						{
							expectedNodeType=currentNodeType;
							output.write("         </Folder>");output.newLine(); // Folder to contain nodes of this type
							output.write("         <Folder>");output.newLine(); // Folder to contain nodes of this type
							output.write("         <name>" + currentNodeType + "</name>");output.newLine();
						}
						expectedTypeFlag=0; // Reset newTypeFlag

						// Only generate a node if its node degree is above specified threshold
						// SET THRESHOLD HERE
						//						if((Double.parseDouble(AllLinesPD[i][3])>0) && (Double.parseDouble(AllLinesPD[i][3])<2000))

						double iconSizeFactor=0; // Value is assigned later

						if((Double.parseDouble(AllLinesPD[i][3])>0))
							// Now generate 3D models
							if(iconMode.equals("model"))
							{
								output.write("    <Placemark>");output.newLine();
								longLabel=AllLinesPD[i][4].replaceAll("\"", "");
								if(longLabel.length()>40)
									label=longLabel;
								else // Shorten label; take the first 20 and the last 20 characters.
									label=(longLabel.substring(0,20))+"..."+(longLabel.substring(longLabel.length()-20,longLabel.length()));
								output.write("      <name>Sp-"+AllLinesPD[i][4].replaceAll("\"", "")+"</name>");output.newLine();	
								output.write("    <styleUrl>#myBalloonStyle</styleUrl>");output.newLine();
								output.write("      <Model id=\"ID\">");output.newLine();
								// output.write("        <altitudeMode>clamptoground</altitudeMode>");output.newLine();
								output.write("        <altitudeMode>absolute</altitudeMode>");output.newLine();
								output.write("        <Location>");output.newLine();
								output.write("            <longitude>"+((Double.parseDouble(AllLinesPD[i][0]))*xyScale)+"</longitude>");output.newLine();
								output.write("            <latitude>"+((Double.parseDouble(AllLinesPD[i][1]))*xyScale)+"</latitude>");output.newLine();
								output.write("            <altitude>"+((Double.parseDouble(AllLinesPD[i][2]))*altitudeScale+altitudeDisplacement)+"</altitude>");output.newLine();
								output.write("        </Location>");output.newLine();
								output.write("        <Orientation>");output.newLine();
								output.write("            <heading>0</heading>");output.newLine();
								output.write("            <tilt>0</tilt>");output.newLine();
								output.write("            <roll>0</roll>");output.newLine();
								output.write("        </Orientation>");output.newLine();
								output.write("        <Scale>");output.newLine();
								// ADJUST SIZE OF ICONS HERE, FOR MODELS MODELS MODELS
								// FOR MODELS MODELS MODELS MODELS MODELS MODELS MODELS
								double iconSizeMult=0.05;
								if(AllLinesPD[i][7].equals("institution"))
									// iconSizeFactor=3;
									iconSizeFactor=iconSizeMult*5;
								if(AllLinesPD[i][7].equals("person"))
									// iconSizeFactor=5;
									iconSizeFactor=iconSizeMult*1;
								if(AllLinesPD[i][7].equals("fperson"))
									// iconSizeFactor=5;
									iconSizeFactor=iconSizeMult*1;
								if(AllLinesPD[i][7].equals("journal"))
									// iconSizeFactor=800;
									iconSizeFactor=iconSizeMult*5;
								if(AllLinesPD[i][7].equals("grant"))
									iconSizeFactor=iconSizeMult*3;
								if(AllLinesPD[i][7].equals("tag"))
									iconSizeFactor=iconSizeMult*3;
								if(AllLinesPD[i][7].equals("umls"))
									iconSizeFactor=iconSizeMult*3;
								if(AllLinesPD[i][7].equals("substance"))
									iconSizeFactor=iconSizeMult*2;
								if(AllLinesPD[i][7].equals("mainheading"))
									iconSizeFactor=iconSizeMult*1;
								if(AllLinesPD[i][7].equals("paper"))
									iconSizeFactor=iconSizeMult*1;
								if(AllLinesPD[i][7].equals("abstract"))
									iconSizeFactor=iconSizeMult*1;
								if(AllLinesPD[i][7].equals("department"))
									iconSizeFactor=iconSizeMult*2;
								if(AllLinesPD[i][7].equals("principal-investigator"))
									iconSizeFactor=iconSizeMult*2;
								if(AllLinesPD[i][7].equals("research-scientist"))
									iconSizeFactor=iconSizeMult*2;
								if(AllLinesPD[i][7].equals("seed-fund-recipient"))
									iconSizeFactor=iconSizeMult*2;
								if(AllLinesPD[i][7].equals("collaborator-university"))
									iconSizeFactor=iconSizeMult*2;
								if(AllLinesPD[i][7].equals("collaborator-industry"))
									iconSizeFactor=iconSizeMult*2;
								if(AllLinesPD[i][7].equals("gene"))
									iconSizeFactor=iconSizeMult*2;
								if(AllLinesPD[i][7].equals("Gene"))
									iconSizeFactor=iconSizeMult*2;
								if(AllLinesPD[i][7].equals("function"))
									iconSizeFactor=iconSizeMult*2;
								if(AllLinesPD[i][7].equals("process"))
									iconSizeFactor=iconSizeMult*2;
								if(AllLinesPD[i][7].equals("component"))
									iconSizeFactor=iconSizeMult*2;
								if(AllLinesPD[i][7].equals("nodetype6"))
									iconSizeFactor=iconSizeMult*2;
								if(AllLinesPD[i][7].equals("OtherGene"))
									iconSizeFactor=iconSizeMult*2;
								if(AllLinesPD[i][7].equals("SyndromicAutism"))
									iconSizeFactor=iconSizeMult*2;
								if(AllLinesPD[i][7].equals("SyndromicAssociation"))
									iconSizeFactor=iconSizeMult*2;
								if(AllLinesPD[i][7].equals("RareSingleGeneMutationAssociation"))
									iconSizeFactor=iconSizeMult*2;
								if(AllLinesPD[i][7].equals("RareSingleGeneMutation"))
									iconSizeFactor=iconSizeMult*2;
								if(AllLinesPD[i][7].equals("RareSingleGeneMutationNoAssociation"))
									iconSizeFactor=iconSizeMult*2;
								if(AllLinesPD[i][7].equals("Internode"))
									iconSizeFactor=iconSizeMult*2;
								if(AllLinesPD[i][7].equals("InternodeR"))
									iconSizeFactor=iconSizeMult*2;
								if(AllLinesPD[i][7].equals("InternodeS"))
									iconSizeFactor=iconSizeMult*2;
								if(AllLinesPD[i][7].equals("InternodeSR"))
									iconSizeFactor=iconSizeMult*2;
								if(AllLinesPD[i][7].equals("region"))
									iconSizeFactor=iconSizeMult*2;
								if(AllLinesPD[i][7].equals("argene"))
									iconSizeFactor=iconSizeMult*2;
								// COMMENT OUT THE NEXT LINE TO MAKE ALL ICONS THE SAME SIZE
								double iconScale=((Double.parseDouble(AllLinesPD[i][3]))*iconSizeFactor);
								output.write("            <x>" + iconScale + "</x>");output.newLine();
								output.write("            <y>" + iconScale + "</y>");output.newLine();
								output.write("            <z>" + iconScale + "</z>");output.newLine();
								output.write("        </Scale>");output.newLine();
								output.write("        <Link>");output.newLine();
								// In the next line, use the node type to call the icon by name.
								output.write("            <href>models/" + iconMode + "/" + AllLinesPD[i][7] + "." + iconExtension +"</href>");output.newLine();
								output.write("        </Link>");output.newLine();
								output.write("      </Model>");output.newLine();
								output.write("    </Placemark>");output.newLine();
							}
							else // If iconMode is not model
							{
								// ADJUST SIZE OF ICONS HERE, FOR ICONS OTHER THAN MODELS
								if(AllLinesPD[i][7].equals("institution"))
									iconSizeFactor=15;
								if(AllLinesPD[i][7].equals("person"))
									iconSizeFactor=2;
								if(AllLinesPD[i][7].equals("fperson"))
									iconSizeFactor=2;
								if(AllLinesPD[i][7].equals("journal"))
									iconSizeFactor=10;
								if(AllLinesPD[i][7].equals("grant"))
									iconSizeFactor=7;
								if(AllLinesPD[i][7].equals("tag"))
									iconSizeFactor=2;
								if(AllLinesPD[i][7].equals("umls"))
									iconSizeFactor=2;
								if(AllLinesPD[i][7].equals("substance"))
									iconSizeFactor=15;
								if(AllLinesPD[i][7].equals("mainheading"))
									iconSizeFactor=2;
								if(AllLinesPD[i][7].equals("paper"))
									iconSizeFactor=5;
								if(AllLinesPD[i][7].equals("abstract"))
									iconSizeFactor=2;
								if(AllLinesPD[i][7].equals("department"))
									iconSizeFactor=10;
								if(AllLinesPD[i][7].equals("principal-investigator"))
									iconSizeFactor=10;
								if(AllLinesPD[i][7].equals("research-scientist"))
									iconSizeFactor=10;
								if(AllLinesPD[i][7].equals("seed-fund-recipient"))
									iconSizeFactor=10;
								if(AllLinesPD[i][7].equals("collaborator-university"))
									iconSizeFactor=10;
								if(AllLinesPD[i][7].equals("collaborator-industry"))
									iconSizeFactor=10;
								if(AllLinesPD[i][7].equals("nodetype6"))
									iconSizeFactor=10;
								if(AllLinesPD[i][7].equals("gene"))
									iconSizeFactor=3;
								if(AllLinesPD[i][7].equals("Gene"))
									iconSizeFactor=6;
								if(AllLinesPD[i][7].equals("function"))
									iconSizeFactor=10;
								if(AllLinesPD[i][7].equals("process"))
									iconSizeFactor=10;
								if(AllLinesPD[i][7].equals("component"))
									iconSizeFactor=10;
								if(AllLinesPD[i][7].equals("OtherGene"))
									iconSizeFactor=10;
								if(AllLinesPD[i][7].equals("SyndromicAutism"))
									iconSizeFactor=10;
								if(AllLinesPD[i][7].equals("SyndromicAssociation"))
									iconSizeFactor=10;
								if(AllLinesPD[i][7].equals("RareSingleGeneMutationAssociation"))
									iconSizeFactor=10;
								if(AllLinesPD[i][7].equals("RareSingleGeneMutation"))
									iconSizeFactor=10;
								if(AllLinesPD[i][7].equals("RareSingleGeneMutationNoAssociation"))
									iconSizeFactor=10;
								if(AllLinesPD[i][7].equals("Internode"))
									iconSizeFactor=10;
								if(AllLinesPD[i][7].equals("InternodeR"))
									iconSizeFactor=10;
								if(AllLinesPD[i][7].equals("InternodeSR"))
									iconSizeFactor=10;
								if(AllLinesPD[i][7].equals("region"))
									iconSizeFactor=3;
								if(AllLinesPD[i][7].equals("argene"))
									iconSizeFactor=3;
								displayElement=true;
								if(currentNodeType.equals("tag"))
								{
									if((Double.parseDouble(AllLinesPD[i][3]))<minValToDisplayTag || ((Double.parseDouble(AllLinesPD[i][3]))>maxValToDisplayTag))
									{
										//										System.out.println("Exclude");
										displayElement=false;
									}
								}
								if(displayElement==true)
								{
									output.write("    <Placemark>");output.newLine();
									if(annotAll==true)
										//									output.write("      <name>");			
										longLabel=(AllLinesPD[i][8].replaceAll("\"", "").replaceAll("<", "").replaceAll(">", "").replaceAll("&quot;", "'")); // Fix special characters
									if(longLabel.length()<40)
										label=longLabel;
									else // Shorten label; take the first 20 and the last 20 characters.
										label=(longLabel.substring(0,20))+"..."+(longLabel.substring(longLabel.length()-20,longLabel.length()));
									//									System.out.println(label);
									output.write(label);											
									output.newLine();
									output.write("      <description>");output.newLine();
									//									output.write("        <![CDATA[DESCRIPTION]]>");output.newLine();
									//									output.write("        <![CDATA[<a href=\"c:\\e\\o\\1.kml;balloonFlyto\">MYKML</a>]]>");output.newLine();
									output.write("        <![CDATA[" +
											"<h2><b>" + AllLinesPD[i][7] + ":</b></h2><p><h3>" + // Node type
											AllLinesPD[i][8] + // Node name
											"<a href=\"http://localhost/Sciologer/servlet/bales.CVResults?formQuery=" +
											(AllLinesPD[i][8].replaceAll(" ", "+")).replaceAll("\"","").replaceAll("'","") + "\"><br>Sciologer</a><br>" +
											"<a href=\"http://www.ncbi.nlm.nih.gov/sites/entrez?dispmax=20&amp;db=pubmed&amp;cmd_current=Limits&amp;orig_db=PubMed&amp;cmd=Search&amp;term=%22" + 
											(AllLinesPD[i][8].replaceAll(" ", "%20")).replaceAll("\"","").replaceAll("'","") + "%22&amp;doptcmdl=DocSum'\">PubMed</a>" +
											"<br>" +
											"<a href=\"http://scholar.google.com/scholar?q=" + (AllLinesPD[i][8].replaceAll(" ", "%20")).replaceAll("\"","").replaceAll("'","") + "&hl=en&lr=&btnG=Search\">Google Scholar</a></h3>" +
									"]]>");output.newLine();				
									//									output.write("        <![CDATA[<a href=\"http://maps.google.com/mapfiles/kml/shapes/shaded_dot.png\">PubMed</a>]]>");output.newLine();
									output.write("      </description>");output.newLine();
									output.write("      <styleUrl>#myBalloonStyle</styleUrl>");output.newLine();
									output.write("      <Point>");output.newLine();
									// output.write("        <altitudeMode>clamptoground</altitudeMode>");output.newLine();
									output.write("        <altitudeMode>absolute</altitudeMode>");output.newLine();
									output.write("        <coordinates>"+((Double.parseDouble(AllLinesPD[i][0]))*xyScale)+","+((Double.parseDouble(AllLinesPD[i][1]))*xyScale)+","+((Double.parseDouble(AllLinesPD[i][2]))*altitudeScale+altitudeDisplacement)+"</coordinates>");output.newLine();
									output.write("      </Point>");output.newLine();
									output.write("      <Style>");output.newLine();
									output.write("        <IconStyle>");output.newLine();
									//									output.write("          <color>88ffffff</color>");output.newLine();
									//									output.write("          <colorMode>normal</colorMode>");output.newLine();

									String opacityVal=""; // Transparency value or "alpha"
									String blueVal="";	
									String greenVal="";
									String redVal="";

									// USE THIS BLOCK IF CALCULATING COLORS BASED ON TYPE
									if(colorKMLBasedOnType==1)
									{
										if(AllLinesPD[i][7].equals("gene"))
										{
											opacityVal="ff";
											redVal="00";
											greenVal="00";
											blueVal="ff";
										}
										if(AllLinesPD[i][7].equals("argene"))
										{
											opacityVal="ff";
											redVal="ff";
											greenVal="00";
											blueVal="00";
										}
									}

									// USE THIS BLOCK IF CALCULATING COLORS BASED ON CATEGORY -- Standard Spectrum
									if(colorKMLBasedOnCategoryStandardSpectrum==1)
									{
										if(AllLinesPD[i][6].equals("0"))
											// red
										{
											opacityVal="DC";
											blueVal="00";
											greenVal="00";
											redVal="FF";
										}
										if(AllLinesPD[i][6].equals("1"))
											// light blue
										{
											opacityVal="94";
											blueVal="FF";
											greenVal="99";
											redVal="66";
										}
										if(AllLinesPD[i][6].equals("2"))
											// light grey
										{
											opacityVal="B8";
											blueVal="C0";
											greenVal="C0";
											redVal="C0";
										}
										if(AllLinesPD[i][6].equals("3"))
											// fuchsia 
										{
											opacityVal="CA";
											blueVal="FF";
											greenVal="00";
											redVal="FF";
										}
										if(AllLinesPD[i][6].equals("4"))
											// dark green
										{
											opacityVal="DC";
											blueVal="00";
											greenVal="80";
											redVal="00";
										}
										if(AllLinesPD[i][6].equals("5"))
											// blue
										{
											opacityVal="BB";
											blueVal="FF";
											greenVal="00";
											redVal="00";
										}
										if(AllLinesPD[i][6].equals("6"))
											// yellow
										{
											opacityVal="B8";
											blueVal="00";
											greenVal="FF";
											redVal="FF";
										}
									}

									// USE THIS BLOCK IF CALCULATING COLORS BASED ON CATEGORY -- Custom Spectrum
									if(colorKMLBasedOnCategoryCustomSpectrum==1)
									{
										if(AllLinesPD[i][6].equals("0"))
											// coral red
										{
											opacityVal="CC";
											blueVal="40";
											greenVal="40";
											redVal="FF";
										}
										if(AllLinesPD[i][6].equals("1"))
											// scarlet 
										{
											opacityVal="CC";
											blueVal="00";
											greenVal="24";
											redVal="FF";
										}
										if(AllLinesPD[i][6].equals("2"))
											// light blue
										{
											opacityVal="CC";
											blueVal="FF";
											greenVal="99";
											redVal="66";
										}
										if(AllLinesPD[i][6].equals("3"))
											// dark red
										{
											opacityVal="CC";
											blueVal="00";
											greenVal="00";
											redVal="8B";
										}
										if(AllLinesPD[i][6].equals("4"))
											// orange
										{
											opacityVal="CC";
											blueVal="00";
											greenVal="66";
											redVal="FF";
										}
										if(AllLinesPD[i][6].equals("5"))
											// blue
										{
											opacityVal="CC";
											blueVal="FF";
											greenVal="00";
											redVal="00";
										}
										if(AllLinesPD[i][6].equals("6"))
											// yellow
										{
											opacityVal="CC";
											blueVal="00";
											greenVal="FF";
											redVal="FF";
										}
									}


									// USE THIS BLOCK IF CALCULATING COLORS BASED ON CATEGORY -- Hurricane Spectrum
									if(colorKMLBasedOnCategoryHurricaneSpectrum==1)
									{
										if(AllLinesPD[i][6].equals("0"))
											// light blue
										{
											opacityVal="94";
											blueVal="FF";
											greenVal="99";
											redVal="66";
										}

										if(AllLinesPD[i][6].equals("1"))
											// green
										{
											opacityVal="A6";
											blueVal="00";
											greenVal="FF";
											redVal="66";
										}
										if(AllLinesPD[i][6].equals("2"))
											// yellow
										{
											opacityVal="B8";
											blueVal="00";
											greenVal="FF";
											redVal="FF";
										}
										if(AllLinesPD[i][6].equals("3"))
											// orange 
										{
											opacityVal="CA";
											blueVal="00";
											greenVal="33";
											redVal="FF";
										}
										if(AllLinesPD[i][6].equals("4"))
											// red
										{
											opacityVal="DC";
											blueVal="00";
											greenVal="00";
											redVal="FF";
										}
										if(AllLinesPD[i][6].equals("5"))
											// dark red
										{
											opacityVal="EE";
											blueVal="00";
											greenVal="00";
											redVal="CC";
										}
										if(AllLinesPD[i][6].equals("6"))
											// purple
										{
											opacityVal="FF";
											blueVal="00";
											greenVal="00";
											redVal="99";
										}
									}


									// USE THIS BLOCK IF CALCULATING COLORS BASED ON POSITION
									double xrange=minmax[0][1]-minmax[0][0];
									double yrange=minmax[1][1]-minmax[1][0];
									double zrange=minmax[2][1]-minmax[2][0];
									if(colorBasedOnPosition==1)
									{
										opacityVal="ff";
										//									System.out.println("redexp="+((colorScale*(Double.parseDouble(AllLinesPD[i][0])-minmax[0][0])/xrange)));
										if((colorScale*(Double.parseDouble(AllLinesPD[i][0])-minmax[0][0])/xrange)>255)
											redVal="ff";
										else
											redVal=Integer.toHexString((int)(colorScale*(Double.parseDouble(AllLinesPD[i][0])-minmax[0][0])/xrange));
										if (redVal.length()==1)
										{
											redVal="0" + redVal;
										}
										if((colorScale*(Double.parseDouble(AllLinesPD[i][1])-minmax[1][0])/yrange)>255)
											greenVal="ff";
										else
											greenVal=Integer.toHexString((int)(colorScale*(Double.parseDouble(AllLinesPD[i][1])-minmax[1][0])/yrange));
										if (greenVal.length()==1)
										{
											greenVal="0" + greenVal;
										}
										if((colorScale*(Double.parseDouble(AllLinesPD[i][2])-minmax[2][0])/yrange)>255)
											blueVal="ff";
										else
											blueVal=Integer.toHexString((int)(colorScale*(Double.parseDouble(AllLinesPD[i][2])-minmax[2][0])/yrange));
										if (blueVal.length()==1)
										{
											blueVal="0" + blueVal;
										}
									}
									/*
								if(colorBasedOnPosition==1)
								{
									//									opacityVal="93";
									opacityVal="dd";
									blueVal=(Integer.toHexString((int)(colorScale*(Math.abs(Double.parseDouble(AllLinesPD[i][0]))))));
									if (blueVal.length()==1)
									{
										blueVal="0" + blueVal;
									}					
									greenVal=(Integer.toHexString((int)(colorScale*(Math.abs(Double.parseDouble(AllLinesPD[i][1]))))));
									if (greenVal.length()==1)
									{
										greenVal="0" + greenVal;
									}					
									redVal=(Integer.toHexString((int)(colorScale*(Math.abs(Double.parseDouble(AllLinesPD[i][2]))))));
									if (redVal.length()==1)
									{
										redVal="0" + redVal;
									}
									// System.out.println(AllLinesPD[i][0]);
									// System.out.println(blueVal);
									// System.out.println(Integer.toHexString((int)((0.255*Math.abs(Double.parseDouble(AllLinesPD[i][0]))))));
									// System.out.println("-");
								}
									 */
									if(colorBasedOnDegree==1) // Hurricane spectrum; currently 7 bins
									{
										if(Integer.parseInt(AllLinesPD[i][5])>0)
											// light blue
										{
											opacityVal="94";
											blueVal="FF";
											greenVal="99";
											redVal="66";
										}
										if(Integer.parseInt(AllLinesPD[i][5])>50)
											// green
										{
											opacityVal="A6";
											blueVal="00";
											greenVal="FF";
											redVal="66";
										}
										if(Integer.parseInt(AllLinesPD[i][5])>75)
											// yellow
										{
											opacityVal="B8";
											blueVal="00";
											greenVal="FF";
											redVal="FF";
										}
										if(Integer.parseInt(AllLinesPD[i][5])>88)
											// orange 
										{
											opacityVal="CA";
											blueVal="00";
											greenVal="33";
											redVal="FF";
										}
										if(Integer.parseInt(AllLinesPD[i][5])>94)
											// red
										{
											opacityVal="DC";
											blueVal="00";
											greenVal="00";
											redVal="FF";
										}
										if(Integer.parseInt(AllLinesPD[i][5])>97)
											// dark red
										{
											opacityVal="EE";
											blueVal="00";
											greenVal="00";
											redVal="CC";
										}
										if(Integer.parseInt(AllLinesPD[i][5])>98)
											// purple
										{
											opacityVal="FF";
											blueVal="00";
											greenVal="00";
											redVal="99";
										}
									}

									if(iconColorNone==false)
									{
										//										output.write("          <color>bb" + blueVal + greenVal+ redVal + "</color>");output.newLine();
										output.write("          <color>" + opacityVal + blueVal + greenVal+ redVal + "</color>");output.newLine();
										output.write("          <colorMode>normal</colorMode>");output.newLine();
									}

									// OLD COLOR CODE
									// ADJUST TRANSPARENCY LEVEL IN FIRST TWO DIGITS OF COLOR HERE
									// output.write("          <color>44ffffff</color>");output.newLine();
									// output.write("          <colorMode>random</colorMode>");output.newLine();

									// COMMENT OUT THE NEXT TWO LINES TO MAKE ALL THE PLACEMARKS THE SAME SIZE
									if(iconResizeNone==false)
										// placemarkScale=((Double.parseDouble(AllLinesPD[i][3]))*placemarkSizeFactor);
										placemarkScale=iconSizeFactor*(Double.parseDouble(AllLinesPD[i][3]));
										// placemarkScale=3;

									// COMMENT OUT THE NEXT LINE TO ALLOW THE PLACEMARKS TO BE DIFFERENT SIZES
									// OR, UNCOMMENT IT TO MAKE THEM ALL THE SAME SIZE EXCEPT FOR "LI" ICONS WHICH WILL BE SMALLER
									// if(AllLinesPD[i][7].equals("li"))
									//								output.write("          <scale>" + Math.sqrt(placemarkScale) + "</scale>");output.newLine();
									//								output.write("          <scale>" + Math.log(2*placemarkScale)*placemarkSizeFactor + "</scale>");output.newLine();
									//								output.write("          <scale>" + Math.log(1.1*placemarkScale)*placemarkSizeFactor + "</scale>");output.newLine();
									// WORKING HERE
									output.write("          <scale>" + Math.log(1*placemarkScale)*Math.log(5*placemarkScale)*placemarkSizeFactor + "</scale>");output.newLine();
									//								output.write("          <scale>" + placemarkScale + "</scale>");output.newLine();
									output.write("          <Icon>");output.newLine();
									//									output.write("            <href>http://maps.google.com/mapfiles/kml/shapes/shaded_dot.png</href>");output.newLine();
									//									*******
									// In the next line, use the node type to call the icon by name. Value of iconMode is folder
									
									// Just temporarily replaced and then I put it back, March 12, 2011:
									output.write("            <href>models/" + iconMode + "/" + AllLinesPD[i][7] + "." + iconExtension +"</href>");output.newLine();
									
									// temporarily replaced with:
									// output.write("            <href>http://cudial1.cpmc.columbia.edu/sciologer/models/" + iconMode + "/" + AllLinesPD[i][7] + "." + iconExtension +"</href>");output.newLine();
									
									output.write("          </Icon>");output.newLine();
									output.write("        </IconStyle>");output.newLine();
									output.write("      </Style>");output.newLine();
									output.write("    </Placemark>");output.newLine();
								}
							}
					}
					inPD.close(); // Done with positions data
					expectedTypeFlag=0; // Reset expectedTypeFlag for use in node labels code below.

					// Close folder and open a new one for labels
					//					output.write("  </Folder>");output.newLine();
					output.write("         </Folder>");output.newLine(); // End of folder to contain nodes of this type
					output.write("    </Folder>");output.newLine(); // End of folder to contain nodes

					output.write("      <Folder>");output.newLine(); // Folder to contain node labels
					output.write("        <name>Node labels</name>");output.newLine();
					output.write("        <visibility>0</visibility>");output.newLine();


					for(int i=0; i<lineCount; i++)
					{
						// Tokenize nodes to determine whether a new folder should be opened
						StringTokenizer tokenizer=new StringTokenizer(AllLinesPD[i][4],"_");
						while(tokenizer.hasMoreTokens())
						{
							currentNodeType=tokenizer.nextToken();
							currentNode=tokenizer.nextToken();
							break;
						}

						if(i==0) // If this is the first node encountered in the file, make a new folder
						{
							expectedTypeFlag=1;
							expectedNodeType=currentNodeType;
							output.write("         <Folder>");output.newLine(); // Folder to contain nodes of this type
							output.write("        <visibility>0</visibility>");output.newLine();
							output.write("         <name>" + currentNodeType + "</name>");output.newLine();
						}
						if(currentNodeType.equals(expectedNodeType))
							expectedTypeFlag=1;
						if(expectedTypeFlag==0) // A new node type is encountered; close old folder and open a new one
						{
							expectedNodeType=currentNodeType;
							output.write("         </Folder>");output.newLine(); // Folder to contain nodes of this type
							output.write("         <Folder>");output.newLine(); // Folder to contain nodes of this type
							output.write("        <visibility>0</visibility>");output.newLine();
							output.write("         <name>" + currentNodeType + "</name>");output.newLine();
						}
						expectedTypeFlag=0; // Reset newTypeFlag

						if((Double.parseDouble(AllLinesPD[i][3])>0))
						{
							output.write("      <Placemark>");output.newLine();
							longLabel=(AllLinesPD[i][8].replaceAll("\"", "").replaceAll("<", "").replaceAll(">", "").replaceAll("\\Q&quot;\\E", "'")); // Fix special characters
							StringTokenizer t=new StringTokenizer(longLabel," ");
							if(t.countTokens()<8)
								label=longLabel;
							else
							{
								int tokenCounter=0;
								int numTokens=t.countTokens();
								while(t.hasMoreTokens())
								{
									tokenCounter++;
									if(tokenCounter==4)
										label=label+" ...";
									if((tokenCounter==1)||(tokenCounter==2)||(tokenCounter==3)||(tokenCounter==numTokens-2)||(tokenCounter==numTokens-1)||(tokenCounter==numTokens))
										label=label+" "+t.nextToken();
									else
										t.nextToken();
								}
							}
							output.write(label);
							//							output.write("      <name>"+AllLinesPD[i][8].replaceAll("\"", "").replaceAll("<", "").replaceAll(">", "").replaceAll("&", "and")+"</name>");output.newLine(); // Fix special characters
							output.write("      <name>"+label+"</name>");output.newLine();
							longLabel="";
							label="";
							output.write("        <visibility>0</visibility>");output.newLine();
							output.write("      <Point>");output.newLine();
							// output.write("        <altitudeMode>clamptoground</altitudeMode>");output.newLine();
							output.write("        <altitudeMode>absolute</altitudeMode>");output.newLine();
							output.write("        <coordinates>"+((Double.parseDouble(AllLinesPD[i][0]))*xyScale)+","+((Double.parseDouble(AllLinesPD[i][1]))*xyScale)+","+((Double.parseDouble(AllLinesPD[i][2]))*altitudeScale+altitudeDisplacement)+"</coordinates>");output.newLine();
							output.write("      </Point>");output.newLine();
							output.write("      <Style>");output.newLine();
							output.write("        <IconStyle>");output.newLine();
							output.write("          <color>ffffffff</color>");output.newLine();
							output.write("          <colorMode>normal</colorMode>");output.newLine();
							output.write("          <scale>0.001</scale>");output.newLine();
							output.write("          <Icon>");output.newLine();
							output.write("            <href>models/drawing/dummy.png</href>");output.newLine();
							output.write("          </Icon>");output.newLine();
							output.write("        </IconStyle>");output.newLine();
							output.write("        <LabelStyle>");output.newLine();
							output.write("         <color>ffffffff</color>");output.newLine();
							output.write("         <colorMode>normal</colorMode>");output.newLine();
							output.write("         <scale>0.6</scale>");output.newLine();
							output.write("      </LabelStyle>");output.newLine();
							output.write("      </Style>");output.newLine();
							output.write("    </Placemark>");output.newLine();
						}
					} // end i

					output.write("         </Folder>");output.newLine(); // End of folder to contain node labels of this type
					output.write("    </Folder>");output.newLine(); // End of folder to contain node labels


					// GENERATE LINKS
					//					output.write("  <Folder>");output.newLine();
					// NEXT LINE REMOVED TEMPORARILY TO PREPARE FOR MARCH 2009 DEMO
					// if(excludeLinks==false)
					// REINSTANTIATED IN FEBRUARY 2010
					if(excludeLinks==false)
					{
						output.write("    <Style id=\"link\">");output.newLine();
						output.write("      <LineStyle>");output.newLine();
						//					output.write("        <color>220000ff</color>");output.newLine();
						//					output.write("        <color>200000ff</color>");output.newLine();
						// output.write("        <color>ff0000ff</color>");output.newLine();
						//output.write("        <color>ffcc9999</color>");output.newLine();
						output.write("        <color>99cc9999</color>");output.newLine();
						//					output.write("        <width>1</width>");output.newLine();
						output.write("        <width>1</width>");output.newLine();
						output.write("      </LineStyle>");output.newLine();
						output.write("    </Style>");output.newLine();
						output.write("      <Folder>");output.newLine(); // Folder to contain links
						output.write("        <name>Links</name>");output.newLine();
						for(int i=0; i<lineCountLinks; i++)
						{				
							output.write("        <Placemark>");output.newLine();
							output.write("          <name>Link</name>");output.newLine();
							output.write("          <styleUrl>#link</styleUrl>");output.newLine();
							output.write("          <LineString>");output.newLine();
							//						output.write("          <extrude>1</extrude>");output.newLine();
							output.write("            <tessellate>1</tessellate>");output.newLine();
							// output.write("            <altitudeMode>clamptoground</altitudeMode>");output.newLine();
							output.write("            <altitudeMode>absolute</altitudeMode>");output.newLine();
							output.write("            <coordinates>");output.newLine();						
							output.write("        " + ((Double.parseDouble(AllLinesLD[i][0]))*xyScale) + "," + ((Double.parseDouble(AllLinesLD[i][1]))*xyScale) + "," + ((Double.parseDouble(AllLinesLD[i][2]))*altitudeScale+altitudeDisplacement) + " " + ((Double.parseDouble(AllLinesLD[i][3]))*xyScale) + "," + ((Double.parseDouble(AllLinesLD[i][4]))*xyScale) + "," + ((Double.parseDouble(AllLinesLD[i][5]))*altitudeScale+altitudeDisplacement));output.newLine();
							output.write("            </coordinates>");output.newLine();
							output.write("          </LineString>");output.newLine();
							output.write("        </Placemark>");output.newLine();
						}
						inLD.close(); // Done with links data
						output.write("      </Folder>");output.newLine();
					}
					//					output.write("  </Folder>");output.newLine();
					output.write("</Document>");output.newLine();
					output.write("</kml>");output.newLine();
				}
			} // end try
			catch (IOException e) 
			{
				logger.error(e); e.printStackTrace(); 
			} // end catch

			//			*/

			logger.info("Finished writing output to " + outfile + ".");
			output.close(); // Finish writing output to file and close output file
			if(outFormat==KML)
			{
				String[] bKMZparams=new String[4];
				bKMZparams[0]=outfile;
				bKMZparams[1]=File_Settings.kmzPath;
				bKMZparams[2]=iconMode;
				//				bKMZparams[3]=outfile + ".kmz";
				bKMZparams[3]=outfile.substring(0,outfile.length()-4) + ".kmz"; // Strip off the .kml and add .kmz
				bundleKMZ.main(bKMZparams);
			}
			if(runExtProg==true)
			{
				if(outFormat==X3D)
				{
					try{
						//ProcessBuilder p=new ProcessBuilder("c:\\Program Files\\Media Machines\\Flux\\FluxPlayer.exe",("C:\\Documents and Settings\\Michael Bales\\My Documents\\j\\phc\\inspired2\\" + outfile),"start","c:\\eleph0ut.x3d");
						//ProcessBuilder p=new ProcessBuilder("c:\\Program Files\\Media Machines\\Flux\\FluxPlayer.exe",("C:\\Documents and Settings\\Michael Bales\\My Documents\\j\\phc\\inspired2\\" + outfile),"start","c:\\eleph0ut.x3d");
						//ProcessBuilder p=new ProcessBuilder("c:\\Program Files\\Media Machines\\Flux\\FluxPlayer.exe",outfile,"start",outfile);
						//p.start();
					}catch(Exception e) { logger.error(e); e.printStackTrace(); }
				}

				if(outFormat==POV)
				{
					try{
						ProcessBuilder p=new ProcessBuilder("\"c:\\program files\\pov-ray for windows v3.6\\bin\\pvengine.exe\" +R2 +A0.1 +J1.2 +Am2 +Q9 +W800 +H600 +FN +o \""+outfile+"\"");
						p.start();
					}catch(Exception e) { logger.error(e); e.printStackTrace(); }
				}

				// These lines temporarily commented out August 2010
				
				if(outFormat==KML)
				{
					try{
						//						ProcessBuilder p=new ProcessBuilder("\"c:\\program files\\Google\\Google Earth\\googleearth.exe\" " + "\"" + outfile + "\"");
						ProcessBuilder p=new ProcessBuilder(File_Settings.googleEarthPath+"\" " + "\"" + outfile);
						p.start();
					}catch(Exception e)
					{
						System.out.println("Error when launching Google Earth.");
						logger.error("Error when launching Google Earth.");
					}
				}

				 
			}
		} // end try
		catch (IOException e) {
			logger.error(e); e.printStackTrace(); 
		} // end catch
	} // end main
	public static double round2(double num) {
		double result = num * 100;
		result = Math.round(result);
		result = result / 100;
		return result;
	}
	
	public static double round3(double num) {
		double result = num * 255;
		result = Math.round(result);
		return result;
	}
	/*****************************************************************
	 * Prints version information.
	 *****************************************************************/
	private static void printVersion() {
		System.out.println(
				"eleph1nt 0.1, 2008-05-08. " + endl
				+ "Copyright 2010  Michael Bales (Columbia University, New York, NY). " + endl);
		//				+ "eleph1nt is free software, released under the GNU LGPL. ");
	}

	private static void printHelp() {
		// Usage and info message.
		System.out.print( 
				endl
				+ "This is eleph1nt, a tool to combine network node position data " + endl
				+ "with node layout data. " + endl
				+ endl
				+ "Usage: java eleph1nt [OPTION]... " + endl
				+ endl
				+ "Options: " + endl
				+ "General options: " + endl
				+ "   -h  --help        display this help message and exit. " + endl
				+ "   -v  --version     print version information and exit. " + endl
				+ "   -q  --nowarnings  quiet mode (default). " + endl
				+ "   -w  --warnings    enable warnings. " + endl
				+ "   -r  --runext 	  run external program after outfile complete. " +endl		  
				+ "   -verbose          verbose mode. " + endl
				+ "   -i <file>         read input data from given file (default: stdin). " + endl
				+ "   -o <file>         write output data to given file (default: stdout). " + endl
				+ "   -inFormat FORMAT  read input data in format FORMAT (default: RSF, see below). " + endl
				+ "   -outFormat FORMAT write output data in format FORMAT (default: DISP, see below). " + endl
				+ "   " + endl
				+ "Network writer options: " + endl
				+ "   -fontSize <int>   font size of vertex annotations (default: 14). " + endl
				+ "   -backColor COLOR  background color (default: WHITE). " + endl
				+ "                     Colors: BLACK, GRAY, LIGHTGRAY, WHITE." + endl
				+ "   -showEdges        Show the edges of the graph (available only for CVS and RFS inFomat)" + endl
				+ "                     (default: hide)" + endl
				+ "   -scalePos <float> scaling factor for the layout to adjust " + endl
				+ "                     (X3D and SVG only, default: 1.0). " + endl
				+ "   -annotAll         annotate each vertex with its name (default: no). " + endl
				+ "   -annotNone        annotate no vertex (default: no). " + endl
				+ "   -mark             highlight vertices using the MarkerExp class " + endl
				+ "                     (see source code for more details)" + endl
				+ "   -openURL          The node's name can be considered as URL and opened in a web Broswer. " + endl
				+ "                     This option used with DISP output require to hold CTRL KEY while clicking" + endl
				+ endl
				+ "DISP specific option" + endl
				+ "   -browser <Cmd>    The browser command. if not available, eleph1nt will try to guess." + endl
				+ endl
				+ "Formats: " + endl
				+ "   CVS               CVS log format (only input). " + endl
				+ "   RSF               graph in relational standard format. " + endl
				+ "   LAY               graph layout in textual format. " + endl
				+ "   VRML              graph layout in VRML format (only output). " + endl
				+ "   SVG               graph layout in SVG format (only output). " + endl
				+ "   DISP              display gaph layout on screen (only output). " + endl
				+ "http://dbmi.columbia.edu/~meb7002/eleph1nt/ " + endl
				+ endl
				+ "Report bugs to Michael Bales <firstname.lastname@dbmi.columbia.edu>. " + endl
				+ endl
		);
	}

	/*****************************************************************
	 * Checks whether the command line argument at index i has a follower argument.
	 * If there is no follower argument, it exits the program.
	 * @param args  String array containing the command line arguments.
	 * @param i     Index to check.
	 *****************************************************************/
	private static void chkAvail(String[] args, int i) {
		if (i == args.length) {
			logger.error("Usage error: Option '" + args[i-1] 
			                                            + "' requires an argument (file).");
			//System.exit(1);
		}
	}

	/*****************************************************************
	 * Transforms the format given as a string into the appropriate integer value.
	 * @param format  File format string to be transformed to int.
	 * @return        File format identifier.
	 *****************************************************************/
	private static int getFormat(String format) {
		int result = 0;
		if (format.equalsIgnoreCase("NODEDATA")) {
			result = NODEDATA;
		} else if (format.equalsIgnoreCase("POSITIONDATA")) {
			result = POSITIONDATA;
		} else if (format.equalsIgnoreCase("X3D")) {
			result = X3D;
		} else if (format.equalsIgnoreCase("POV")) {
			result = POV;
		} else if (format.equalsIgnoreCase("KML")) {
			result = KML;
		} else if (format.equalsIgnoreCase("SVG")) {
			result = SVG;
		} else {
			logger.error("Usage error: '" + format + "' is not a valid format.");
			//System.exit(1);
		}
		return result;
	}

} // end class
