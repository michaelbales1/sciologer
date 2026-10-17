package bales;
/*//////////////////////////////////////////////////////////////////////////////////////////////////////
/*NodeSort.java reorders nodes so that nodes of various types appear in a consecutive list.
 * The purpose for this is so that groups of nodes can be placed in separate folders in the output file.
 * Read in the adjacencies file. Instantiate vector v. Read through the file,
 * counting lines as you go. Each time a new node type is encountered, add it to v.
 * Now make a new array the size of the original array. Now read through v.
 * For each row of v, read through the original array and only output the matching node types to the new array.
 *///////////////////////////////////////////////////////////////////////////////////////////////////////
import java.io.*;
import java.util.StringTokenizer;
public class NodeSortByType  {
	public static void main(String args[]) {
		// System.out.println("Launching node sort by type...");
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
			// Instantiate vector to hold the different kinds of nodes
			// For now it can be an array
			String[] nodeTypes=new String[20]; // for now, maximum 20 node types
			String currentNode="";
			String currentNodeType="";
			String[] currentNodeTokenized=new String[2];
			int alreadySeenType=0;
			int indexAddingTypes=0;
//			Now count how many different kinds of nodes there are
			for(int i=0; i<lineCountp; i++)
			{
				currentNode=allPositions[i][4];
				StringTokenizer tokenizer = new StringTokenizer(currentNode,"_");
				while(tokenizer.hasMoreTokens())
				{
					currentNodeType=tokenizer.nextToken();
					currentNode=tokenizer.nextToken();
					break;
				}
				alreadySeenType=0;
				for(int j=0;j<20;j++)
				{
					if(currentNodeType.equals(nodeTypes[j]))
						{
						alreadySeenType=1;
						break;
						}
				}
				if(alreadySeenType==0)
					// Haven't seen this type yet.
				{
					// Add this one to list of node types
					nodeTypes[indexAddingTypes]=currentNodeType;
					indexAddingTypes++;
				}
			}
			// Done populating nodeTypes array
			String[] orderedPos=new String[lineCountp];
//			Now move through the types array
			int orderedPosIndex=0;
			for(int i=0; i<indexAddingTypes; i++)
				for(int j=0; j<lineCountp; j++)
				{
					StringTokenizer tokenizer=new StringTokenizer(allPositions[j][4],"_");
					while(tokenizer.hasMoreTokens())
					{
						currentNodeType=tokenizer.nextToken();
						currentNode=tokenizer.nextToken();
						break;
					}
					if(currentNodeType.equals(nodeTypes[i]))
						// It matches, so copy this line to the sorted positions array
					{
						orderedPos[orderedPosIndex]=(simplePositions[j]);
						orderedPosIndex++;
					}
				}
			// Prepare output file for writing
			File file=new File(args[1]); // declaration of output filename
			BufferedWriter output=new BufferedWriter(new FileWriter(file));	
			// Now move through the ordered array and write to output
			for(int i=0; i<lineCountp; i++)
			{
				output.write(orderedPos[i]);
				output.newLine();
			}
			// Close the output file.	
			output.close();
			// System.out.println("Finished sorting nodes by type");
		} // end try
		catch (IOException e) {
			System.out.print(e);
			e.printStackTrace();
		} // end catch
	} // end main

} // end class
