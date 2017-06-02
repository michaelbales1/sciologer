package bales;
/*
 * ElephUlam generates a pattern of cubes such that the presence or absence of
 * a cube is determined by divisibility of the cube's x, y, and z values based on
 * a simple formula.
 * 
 * Copyright 2009 Michael Bales (firstname.lastname@dbmi.columbia.edu)
 * All rights reserved
 * 
 * eleph1nt.java integrates node position data with node metadata and exports to KML.
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
public class BalesCube 
{
	public static void main(String args[])
	{
		boolean runExtProg = false;
		String outfile = ""; // Empty string for no output file name (standard output).
		PrintWriter out = new PrintWriter(new BufferedWriter(
				new OutputStreamWriter(System.out)));
		// Parse command-line options.
		for (int i = 0; i < args.length; ++i) {
			// General options without argument.
			// Change output writer.
			if (args[i].equalsIgnoreCase("-o")) {
				++i;
				try {
					//	 out = new PrintWriter(new BufferedWriter(new FileWriter(args[i])));
					outfile=args[i].replaceAll("_"," ");
				}
				catch (Exception e) {
					System.out.println("Exception while opening file '" + args[i] + "' for writing: ");
				}
			}

			// Run external program.
			else if (args[i].equalsIgnoreCase("-r") || args[i].equalsIgnoreCase("-runext")) {
				runExtProg=true;
			}
		}
		try {
			//			BufferedReader in2 = new BufferedReader(new FileReader("myt.txt"));
			//			BufferedReader inND = new BufferedReader(new FileReader(args[0]));
			//			BufferedReader inND = new BufferedReader(new FileReader(nodeData));
			String str;
			File file=new File(outfile); // declaration of output filename
			BufferedWriter output=new BufferedWriter(new FileWriter(file));
			output.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");output.newLine();
			output.write("<kml xmlns=\"http://www.opengis.net/kml/2.2\">");output.newLine();
			output.write("<Document>");output.newLine();
			output.write("<name>Bales Cube</name>");output.newLine();
			output.write("      <bgColor>ffffffff</bgColor>");output.newLine();
			// MODIFY MAIN SCALE VARIABLE HERE
			double mainScale=1;
			// MODIFY LATITUDE/LONGITUDE SCALE HERE
			double xyScale=mainScale*0.2;
			// MODIFY ALTITUDE DISPLACEMENT HERE
			double altitudeDisplacement=10000;
			//					// MODIFY ALTITUDE SCALE HERE
			double altitudeScale=mainScale*3000;

			// MODIFY RANGE SCALE HERE
			double colorScale=255; // Default is 255; can be raised to turn outliers white and achieve more color variation in main graph
			double rangeScale=mainScale*100000;
			double iconScale=mainScale*120;
			int latticeSize=180;
			output.write("        <altitude>" + altitudeDisplacement + "</altitude>");output.newLine();
			output.write("        <heading>0</heading>");output.newLine();
			output.write("        <tilt>0</tilt>");output.newLine();
			output.write("        <range>" + rangeScale + "</range>");output.newLine();
			//		output.write("        <altitudeMode>clamptoground</altitudeMode>");
			output.write("        <altitudeMode>absolute</altitudeMode>");output.newLine();
			for(int z=1;z<latticeSize;z++)
			{
				for(int x=1;x<latticeSize;x++)
				{
					for(int y=1;y<latticeSize;y++)
					{
						if(z%(x*y)==0)
						{
							/*
						output.write("    <Placemark>");
						output.write("      <Point>");
						output.write("        <altitudeMode>clamptoground</altitudeMode>");
						output.write("        <coordinates>"+(x*xyScale)+","+(y*xyScale)+","+(z*altitudeScale+altitudeDisplacement)+"</coordinates>");
						output.write("      </Point>");
						output.write("      <Style>");
						output.write("      </Style>");
						output.write("    </Placemark>");
							 */					
							output.write("    <Placemark>");output.newLine();
							output.write("      <Model id=\"ID\">");output.newLine();
							output.write("        <altitudeMode>absolute</altitudeMode>");output.newLine();
							output.write("        <Location>");output.newLine();
							output.write("            <longitude>"+(100+x*xyScale)+"</longitude>");output.newLine();
							output.write("            <latitude>"+(100+y*xyScale/4)+"</latitude>");output.newLine();
							output.write("            <altitude>"+(z*altitudeScale+altitudeDisplacement)+"</altitude>");output.newLine();
							output.write("        </Location>");output.newLine();
							output.write("        <Orientation>");output.newLine();
							output.write("            <heading>0</heading>");output.newLine();
							output.write("            <tilt>0</tilt>");output.newLine();
							output.write("            <roll>0</roll>");output.newLine();
							output.write("        </Orientation>");output.newLine();
							output.write("        <Scale>");output.newLine();
							// ADJUST SIZE OF ICONS HERE, FOR MODELS MODELS MODELS
							// FOR MODELS MODELS MODELS MODELS MODELS MODELS MODELS
							output.write("            <x>" + iconScale + "</x>");output.newLine();
							output.write("            <y>" + iconScale + "</y>");output.newLine();
							output.write("            <z>" + iconScale + "</z>");output.newLine();
							output.write("        </Scale>");output.newLine();
							output.write("        <Link>");output.newLine();
							// In the next line, use the node type to call the icon by name.
							output.write("            <href>models/" + "balescube" + "/" + "cube" + "." + "dae" +"</href>");output.newLine();
							output.write("        </Link>");output.newLine();
							output.write("      </Model>");output.newLine();
							output.write("    </Placemark>");output.newLine();					
						} // end z
					}// end y
				} // end x
			}

			output.write("</Document>");output.newLine();
			output.write("</kml>");output.newLine();
			output.close();
			if(runExtProg==true)
			{
					try{
						//						ProcessBuilder p=new ProcessBuilder("\"c:\\program files\\Google\\Google Earth\\googleearth.exe\" " + "\"" + outfile + "\"");
						ProcessBuilder p=new ProcessBuilder(File_Settings.googleEarthPath+"\" " + "\"" + outfile);
						p.start();
					}
					catch(Exception e)
					{
						System.out.println("Error when launching Google Earth.");
					}
				}
		} // end try
		catch (IOException e) {
			System.out.println("File handling error");
		} // end catch

	} // end main

} // end class
