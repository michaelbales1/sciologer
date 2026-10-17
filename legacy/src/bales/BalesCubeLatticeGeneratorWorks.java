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
public class BalesCubeLatticeGeneratorWorks 
{
	public static void main(String args[])
	{
		System.out.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
		System.out.println("<kml xmlns=\"http://www.opengis.net/kml/2.2\">");
		System.out.println("<Document>");
		System.out.println("<name>Bales Cube</name>");
		System.out.println("      <bgColor>ffffffff</bgColor>");
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
		double iconScale=mainScale*100;
		int latticeSize=20;
		System.out.println("        <altitude>" + altitudeDisplacement + "</altitude>");
		System.out.println("        <heading>0</heading>");
		System.out.println("        <tilt>0</tilt>");
		System.out.println("        <range>" + rangeScale + "</range>");
//		System.out.println("        <altitudeMode>clamptoground</altitudeMode>");
		System.out.println("        <altitudeMode>absolute</altitudeMode>");
		for(int x=1;x<latticeSize;x++)
		{
			for(int y=1;y<latticeSize;y++)
			{
				for(int z=1;z<latticeSize;z++)
				{
					if(z%(x*y)==0)
					{
/*
						System.out.println("    <Placemark>");
						System.out.println("      <Point>");
						System.out.println("        <altitudeMode>clamptoground</altitudeMode>");
						System.out.println("        <coordinates>"+(x*xyScale)+","+(y*xyScale)+","+(z*altitudeScale+altitudeDisplacement)+"</coordinates>");
						System.out.println("      </Point>");
						System.out.println("      <Style>");
						System.out.println("      </Style>");
						System.out.println("    </Placemark>");
	*/					
						System.out.println("    <Placemark>");
						System.out.println("      <Model id=\"ID\">");
						System.out.println("        <altitudeMode>absolute</altitudeMode>");
						System.out.println("        <Location>");
						System.out.println("            <longitude>"+(100+x*xyScale)+"</longitude>");
						System.out.println("            <latitude>"+(100+y*xyScale/4)+"</latitude>");
						System.out.println("            <altitude>"+(z*altitudeScale+altitudeDisplacement)+"</altitude>");
						System.out.println("        </Location>");
						System.out.println("        <Orientation>");
						System.out.println("            <heading>0</heading>");
						System.out.println("            <tilt>0</tilt>");
						System.out.println("            <roll>0</roll>");
						System.out.println("        </Orientation>");
						System.out.println("        <Scale>");
						// ADJUST SIZE OF ICONS HERE, FOR MODELS MODELS MODELS
						// FOR MODELS MODELS MODELS MODELS MODELS MODELS MODELS
						System.out.println("            <x>" + iconScale + "</x>");
						System.out.println("            <y>" + iconScale + "</y>");
						System.out.println("            <z>" + iconScale + "</z>");
						System.out.println("        </Scale>");
						System.out.println("        <Link>");
						// In the next line, use the node type to call the icon by name.
						System.out.println("            <href>models/" + "balescube" + "/" + "cube" + "." + "dae" +"</href>");
						System.out.println("        </Link>");
						System.out.println("      </Model>");
						System.out.println("    </Placemark>");						
					} // end z
				}// end y
	} // end x
}

System.out.println("</Document>");
System.out.println("</kml>");
} // end main

} // end class
