package bales;
/*
 * ProcSP.java postprocesses Stanford Parser output in the Parser's -outputFormat "penn".
 * It identifies the most inward noun phrases and places a delimiter at sentence boundaries.
 * It also adds the np_ node type prefix to each noun phrase.
 * Copyright 2008 Michael Bales (firstname.lastname@dbmi.columbia.edu)
 * All rights reserved
 * 
 * Michael Bales (firstname.lastname@dbmi.columbia.edu)
 * Columbia University, New York, NY
 */
import java.io.*;
import java.util.StringTokenizer;
public class ProcSP  {
	public static void main(String args[]) {
		if (args.length != 2) {
			System.err.println("Usage: cmd infile outfile");
			//System.exit(1);
		}
		int lc=0; // to count lines in parsed file
		int tc=0; // to count number of tokens in each line
		int n=0; // tokenizer's current position in string
		int npCount=0; // to count how many noun phrases are identified in each sentence

		try {
			// Count number of lines in parsed file
			BufferedReader in = new BufferedReader(new FileReader(args[0]));
			File file=new File(args[1]); // declaration of output filename
			BufferedWriter output=new BufferedWriter(new FileWriter(file));
			String str;
			while ((str = in.readLine()) != null) {
				lc++;
			}
			in.close();
			// Now go line by line.
			BufferedReader in2 = new BufferedReader(new FileReader(args[0]));
			while ((str = in2.readLine()) != null) {
				// First, count number of tokens in line
				{
					tc=0;
					StringTokenizer tokenizer = new StringTokenizer(str, "( )");
					while (tokenizer.hasMoreTokens())
					{
						tc++;
						tokenizer.nextToken();
					}
				}
				// Done counting number of tokens in line.
				if(tc==0)
				{
					//					System.out.println("_");
					output.newLine();
					System.out.println();
					npCount=0; // Reset count of noun phrases in each sentence
				}
				else if(tc>0)
				{
					// Now set up string array ThisLine to store tokens in the line
					String[] ThisLine=new String[tc]; //
					{
						n=0;
						StringTokenizer tokenizer = new StringTokenizer(str, "( )");
						while (tokenizer.hasMoreTokens())
						{
							String parsedIn = tokenizer.nextToken();
							ThisLine[n]=parsedIn;
							n++;
						} // end while
					} // done putting tokens in ThisLine
					// Now process tokens in ThisLine to determine what should be output
					for(int i=0; i<tc; i++)
					{
						//					System.out.println(ThisLine[i]);
						// Check if the token is an "NP" tag, indicating the start of a noun phrase 
						if(ThisLine[i].equals("NP"))
						{
							npCount++;
							//						System.out.println("FOUND AN NP");
							// Read remainder of line and output noun phrase
							String buildNP=""; // Create new string to build identified noun phrase
							i++; // Skip initial trailing Penn Treebank NP tag
							// i was main counter for processing line;
							// Now j, below, will be counter for the number of tokens following
							// the detection of an NP
							for(int j=i; j<tc; j++)
							{					
								if(ThisLine[i].equals("DT")) // If next Penn Treebank tag is determiner
								{
									if((tc-j)>2) // As long as the DT is not the second-last token;
										// If DT is the second-last token, skipping two tokens ahead
										// would result in an Array Out of Bounds Exception
									{	
										//									want to ignore determiners, so skip two tokens ahead		
										j++;
										//									increment counters for i and j to skip Penn Treebank tag for next token					
										i++; // increment counter for i to keep i counter in sync with j counter

										j++;
										//									increment counters for i and j to skip Penn Treebank tag for next token					
										i++; // increment counter for i to keep i counter in sync with j counter
									}
								}
								i++;
								j++;
								// increment counters for i and j to skip Penn Treebank tag for next token					
								i++; // increment counter for i to keep i counter in sync with j counter
								buildNP=buildNP + ThisLine[j];
								// add the next token of the noun phrase
								if((tc-j)>1) // Only add a space if another token is to follow
								{
									buildNP=buildNP + " ";
								}
								//							System.out.println("SINGLE TOKEN IS " + ThisLine[j]);
							}
							//						System.out.println("FULL NOUN PHRASE IS " + buildNP);
							if(buildNP.equals(" "))
							{
								// do nothing
							}
							else
								System.out.println(buildNP);
							if(buildNP.equals(" "))
							{
								npCount--; // Decrement noun phrase counter; 	
								// a line consisting of "(NP" by itself does not count;
								// npCount variable determines whether to add a delimiter
							}

							if(buildNP.compareTo(" ")>0) // As long as it is not a line consisting of "(NP" by itself
							{
								//							System.out.println(buildNP);
								// add the "np" noun phrase node type identifier and underscore delimiter
								output.write("np_" + buildNP);

								// THE FOLLOWING LINE WAS COMMENTED OUT ON 7-25-08 TO TEST TF-IDF
								// System.out.print("np_" + buildNP);
								if(npCount>0) // only print vertical bar delimiter if it is not the first noun phrase
								{
									output.write("|");
									// THE FOLLOWING LINE WAS COMMENTED OUT ON 7-25-08 TO TEST TF-IDF
									// System.out.print("|");
								}			
							}
						}
					}
				}
			} // end while
			in.close();
			output.close();
		} // end try
		catch (IOException e) {
		} // end catch
	} // end main
} // end class
