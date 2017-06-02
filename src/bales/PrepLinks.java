package bales;
/*//////////////////////////////////////////////////////////////////////////////////////////////////////
/*PrepLinks.java is designed to output x, y, and z coordinates for each end of a line segment connecting two nodes.
 * It requires as input 1) an adjacency list in the RSF format described by CCVisu; and
 * 2) a positions data file in the format described in eleph1nt.
 * It joins the data from these two files,
 * generating a list of links (lines) specified by the x, y, and z coordinates for both sides of each link.
 * These links can then be read in later by eleph1nt when it is writing links in whatever format desired.
 *///////////////////////////////////////////////////////////////////////////////////////////////////////
import java.io.*;
import java.util.StringTokenizer;
public class PrepLinks  {
	public static void main(String args[]) {
		System.out.println("Launching link preparer...");
		if (args.length != 3) {
			System.err.println("Usage: cmd positionsIn adjacenciesIn linksOut");
			//System.exit(1);
		}
		int lineCountp=0; // to count lines in positions file
		int lineCounta=0; // to count lines in adjacencies file
		try {
			BufferedReader inp = new BufferedReader(new FileReader(args[0]));
			BufferedReader inp2 = new BufferedReader(new FileReader(args[0]));
			String str;
			// First count the number of lines in the positions file
			while ((str = inp.readLine()) != null)
			{
				lineCountp++;
			}
			inp.close();

			// Now read in the positions file and populate the positions array
			// Currently there are 7 positions in the positions array
			String[][] allPositions=new String[lineCountp][7];
			int curIndex=0; // Initialize variable for current token 
			int curToken=0; // Initialize variable for current token in the line
			while ((str = inp2.readLine()) != null)
			{
				curToken=0; // Reset current token to zero
				// Now create a tokenizer to split each line on the tab delimiters
				StringTokenizer tokenizer = new StringTokenizer(str, "\t");
				while(tokenizer.hasMoreTokens())
				{
					// Fill in the positions array with the tokenized data
					allPositions[curIndex][curToken]=tokenizer.nextToken();
					curToken++; // increment to next token position in array
				}
				curIndex++;
			}
			inp2.close(); // Close the input file

			// Now move on to reading in the adjacencies...
			// Count number of lines in adjacencies file
			BufferedReader ina = new BufferedReader(new FileReader(args[1]));
			BufferedReader ina2 = new BufferedReader(new FileReader(args[1]));
			String stra; // String to hold the contents of each line in the adjacencies file
			// First count the number of lines in the file
			while ((stra = ina.readLine()) != null)
			{
				lineCounta++;
			}
			ina.close();

			// Now read in the adjacencies file and populate the adjacencies array
			// Currently there are 4 positions in the adjacencies array
			String[][] allAdjacencies=new String[lineCounta][4];
			curIndex=0; // Reset index of current row in array 
			curToken=0; // Reset current token index
			// The RSF format in which the associations are stored is not StringTokenizer-friendly.
			// Set up three strings that successively fix up the row by removing existing vertical bars, then replacing
			// 1) quote-space-quote; 2)quote-space; and 3) space-quote
			// with tokenizer-friendly vertical bar delimiters.
			String strFixed0="";
			String strFixed1="";
			String strFixed2="";
			String strFixed3="";
			while ((str = ina2.readLine()) != null)
			{
				// The RSF format in which the associations are stored is not StringTokenizer-friendly
				// So these lines make it a vertical bar-delimited line and get rid of the quotes as text qualifier.
				strFixed0=str.replaceAll(("|"),""); // In case there are any vertical bars in the text
				strFixed1=strFixed0.replaceAll(("\" \""),"|");
				strFixed2=strFixed1.replaceAll((" \""),"|");
				strFixed3=strFixed2.replaceAll(("\" "),"|");
				curToken=0; // Reset current token
				StringTokenizer tokenizer = new StringTokenizer(strFixed3, "|");
//				System.out.println(curIndex);
				while(tokenizer.hasMoreTokens())
				{
					allAdjacencies[curIndex][curToken]=tokenizer.nextToken();
//					System.out.println(allAdjacencies[curIndex][curToken]);
					curToken++; // increment to next token position in array
				}
				curIndex++;
			}
			ina2.close();

			// Now prepare output file for writing
			File file=new File(args[2]); // declaration of output filename
			BufferedWriter output=new BufferedWriter(new FileWriter(file));

			// Now set up variables to process the adjacencies data

			String nodeLeft="";
			String nodeRight="";
			// String variables to store the X, Y, and Z coordinates of the left and right nodes in the line segment
			String nodeLeftX="";
			String nodeLeftY="";
			String nodeLeftZ="";
			String nodeRightX="";
			String nodeRightY="";
			String nodeRightZ="";

			// Now go through the adjacencies data row by row, get positions for the left-side nodes,
			// write them to the output file; get positions for the right-side nodes,
			// write them to the output file; and make a new line character.
			for(int i=0; i<lineCounta; i++)
			{
				nodeLeft=allAdjacencies[i][1];
				for(int j=0; j<lineCountp; j++)
				{
					if(allPositions[j][4].equals(nodeLeft))
					{
						nodeLeftX=allPositions[j][0];
						nodeLeftY=allPositions[j][1];
						nodeLeftZ=allPositions[j][2];
						break;
					}
				}
				// Now get the positions of the right side node into the array
				nodeRight=allAdjacencies[i][2];
				for(int j=0; j<lineCountp; j++)
				{
					if(allPositions[j][4].trim().equals(nodeRight.trim()))
					{
						nodeRightX=allPositions[j][0];
						nodeRightY=allPositions[j][1];
						nodeRightZ=allPositions[j][2];
						break;
					}
				}
				// Now output the x, y, and z coordinates of each end of the line segment
				// (Six numeric values on each line, stored as strings, in the format:
				// double, double, double -space- double, double, double
				output.write(nodeLeftX + "\t" + nodeLeftY + "\t" + nodeLeftZ + "\t" + nodeRightX + "\t" + nodeRightY + "\t" + nodeRightZ + "\t");
				output.newLine();
			}
			// Close the output file.	
			output.close();
			System.out.println("Finished preparing links");
		} // end try
		catch (IOException e) 
		{
			System.err.println(e);
		} // end catch
	} // end main

} // end class
