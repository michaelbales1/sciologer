package bales;
/*//////////////////////////////////////////////////////////////////////////////////////////////////////
/* NodeSortByDegree.java reorders nodes by node degree, or number of connections.
 * The purpose is to assign colors to bins. The ordered list is needed so that the
 * bins can be made appropriate sizes.
 * Read in the adjacencies file to array a. Instantiate array a2 of size a.
 * Read through the file and determine the maximum node degree value d.
 * Now read through a d times.
 * Grab nodes of the current d value and add them to the next row of a2.
 * The algorithm should be of the complexity O(n*d) where n is the number of nodes
 * and d is the node degree.
 * Now divide d by b, where b is the number of bins.
 * Go through a2 and add a value from 1 to b to each node.
 *///////////////////////////////////////////////////////////////////////////////////////////////////////
import java.io.*;
import java.util.StringTokenizer;
public class NodeSortByDegree  {
	public static void main(String args[]) {
		// System.out.println("Launching node sort by degree...");
		if (args.length != 2) {
			System.err.println("Usage: cmd positionsIn positionsOut");
			//System.exit(1);
		}
		int lineCountp=0; // to count lines in positions file
		try {
			BufferedReader inp = new BufferedReader(new FileReader(args[0]));
			BufferedReader inp2 = new BufferedReader(new FileReader(args[0]));
			BufferedReader inp3 = new BufferedReader(new FileReader(args[0]));
			String str;
			// First count the number of lines in the positions file
			while ((str = inp.readLine()) != null)
			{
				lineCountp++;
			}
			inp.close();
			// Now read in the positions file and populate a simple 1xn positions array
			String[] simplePositions=new String[lineCountp];
			int curIndexS=0;  // Initialize variable for current line
			while ((str = inp3.readLine()) != null)
			{
				simplePositions[curIndexS]=str;
				curIndexS++;
			}
			// Now read in the positions file and populate the positions array
			// Currently there are 7 positions in the positions array
			String[][] allPositions=new String[lineCountp][7];
			int curIndex=0; // Initialize variable for current line 
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

			/*
			// Instantiate array to hold the different kinds of nodes
			String[] nodeTypes=new String[20]; // for now, maximum 20 node types
			 */

			//			String currentNode="";
			int currentNodeDegree=0;
			int maxNodeDegree=0;
			//			String[] currentNodeTokenized=new String[2];
			//			int alreadySeenType=0;
			//			int indexAddingTypes=0;
			//			Now count how many different kinds of nodes there are

			// Now figure out what the maximum node degree is
			for(int i=0; i<lineCountp; i++)
			{
//				System.out.println("api3: " + allPositions[i][3]);
//				System.out.println("api3.replaceAll: " + (allPositions[i][3].replaceAll("\\Q.0\\E","")));
//				System.out.println("Integer.parseInt(api3.replaceAll): " + Integer.parseInt(allPositions[i][3].replaceAll("\\Q.0\\E","")));
//				System.out.println();
				currentNodeDegree=Integer.parseInt((allPositions[i][3].replaceAll("\\Q.0\\E","")));
//				System.out.println("last successful currentNodeDegree was: " + currentNodeDegree + " and i was " +i);
				if(i==266)
				{
//					System.out.println(currentNodeDegree);
				}
				//				maxNodeDegree=Integer.parseInt(currentNodeDegree)
				if(currentNodeDegree>maxNodeDegree)
					maxNodeDegree=currentNodeDegree;		
			}

			// Create a new array to store the ordered postitions
			String[] orderedPos=new String[lineCountp];

			// Now move through original positions array maxNodeDegree times, starting at 0
			// On the nth time through the loop, if the current node's degree matches n,
			// put this node's data into the output array.
			curIndex=0; // Reset current index to 0
			
			// Make a hash table to map each node degree to a color bin
//			int[][]myHash=new int[lineCountp][2];
			int[][]myHash=new int[maxNodeDegree][2];
			
			// Prepare to assign each node to a color bin
			double numBins=100; // SET THE NUMBER OF COLOR BINS HERE
			double approxBin=0;
			int bin=0;
			String myBin="";

			for(int i=0; i<maxNodeDegree; i++)
			{
				for(int j=0; j<lineCountp; j++)
				{
					currentNodeDegree=Integer.parseInt((allPositions[j][3].replaceAll("\\Q.0\\E","")));
					if(currentNodeDegree==i) // The current position row has a node degree matching the current index value
					{
						orderedPos[curIndex]=simplePositions[j];
						approxBin=(double)curIndex/(double)lineCountp*numBins; // Divide the node degree by the number of bins
						bin=(int)Math.round(approxBin); // Round the value to the nearest integer
						myHash[i][0]=currentNodeDegree;
						myHash[i][1]=bin;
//						System.out.println(i);
						curIndex++;
					}
				}
			}

			for(int i=0; i<lineCountp; i++)
			{
//				currentNodeDegree=Integer.parseInt((allPositions[i][3].replaceAll(".0","")));
//				myBin=String.valueOf(bin); // Cast the bin value to a string to prepare to populate the array
//				allPositions[i][5]=myBin; // Update the positions array with the bin value
			}
			
			// Now update the allPositions array, which is already ordered by type, with the color bin data:
			// Read through the allPositions array, and for each degree value, go through myHash to identify the
			// appropriate bin value. Update the fifth position in allPositions with the bin value.
			
			for(int i=0; i<lineCountp; i++)
			{
				currentNodeDegree=Integer.parseInt((allPositions[i][3].replaceAll("\\Q.0\\E","")));
//				for(int j=0; j<lineCountp; j++)
				for(int j=0; j<maxNodeDegree; j++)
					if(myHash[j][0]==currentNodeDegree)
					{
						myBin=String.valueOf(myHash[j][1]);
						allPositions[i][5]=myBin; // Update the positions array with the bin value
						break; /// Go to the next i value in the allPositions array
					}
			}
				
			
			// Prepare output file for writing
			File file=new File(args[1]); // declaration of output filename
			BufferedWriter output=new BufferedWriter(new FileWriter(file));	
			// Now move through the allPositions array, which now has color bin data, and write to output
			for(int i=0; i<lineCountp; i++)
			{
//				output.write(orderedPos[i]);
				for(int j=0; j<7; j++)
				{
				output.write(allPositions[i][j] + "\t");
				}
				output.newLine();
			}
				
			// Close the output file.	
			output.close();
			// System.out.println("Finished sorting nodes by degree");
		} // end try
		catch (IOException e) {
			System.err.println(e);
			e.printStackTrace();
		} // end catch
	} // end main

} // end class
