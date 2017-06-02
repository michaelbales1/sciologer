package bales;
//=======================================================================================
//ProcFC.java processes a text file containing row-by-row data
//In the following format:
//Groups of entities
//Each line has the entity and value
//There is a blank line between records.
//For example, PubMed citations in MEDLINE format.
//The collection of citations is represented line by line, with multiple lines per citation (record)
//There is a blank line between individual records
//Each line starts with a 1 to 4 character field name
//The data begins in column n
//=======================================================================================
import java.io.*;
import bales.File_Settings;

import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.Hashtable;
import java.util.Vector;
import java.util.Collections;
import java.util.Enumeration;
import java.awt.Toolkit; // for beep only
public class get_tokens  {
	public static void main(String args[]) {
		//		System.out.println("Launching full citation processor...");
		//		System.out.println(args.length);
		if (args.length != 3) {
			System.err.println("Usage: cmd infile utilfile outfile");
			//System.exit(1);
		}
		// Types of nodes to include in the network
		int simpleAdjacency=0; // only output simple adjacency list for analytic purposes;
		// simpleAdjacency mode will output file in the "a" folder and then terminate the program. 
		int includePapers=1;
		int includeAuthors=1;
		int includeJournals=0;
		int includeInstitutions=0;
		int includeGrants=0;
		int includeMainheadings=0;
		int includeSubstances=0;
		int includeAbstracts=1;
		int includeTags=1;
		int parseInstitutions=0;
		int lineCount=0; // to count lines in file
		int auCountLeft=0; // to count authors in a single citation
		int auCountRight=0; // to count authors in a single citation
		String filename=args[1];
		String allTokenized=""; // For utility code to determine MEDLINE token frequencies
		// RESET MAXIMUM AUTHORS HERE
		//		int maxAuth=10; // maximum number of authors per citation for which to create links
		int maxAuth=10; // maximum number of authors per citation for which to create links
		// Create empty file with correct name in procfc_util
		try {
			File file=new File(File_Settings.procFC_utilPath+filename);
			BufferedWriter makeBlankFile=new BufferedWriter(new FileWriter(file));
			makeBlankFile.close();
		} // end try
		catch (IOException e) {
			System.out.println(e);
			e.printStackTrace();
		} // end catch

		boolean exclude=false; // initialize the exclude variable to exclude authors past the maxAuth threshold
		try {
			// Count number of lines in file
			BufferedReader in = new BufferedReader(new FileReader(args[0]));
			BufferedReader inp = new BufferedReader(new FileReader(args[0]));
			BufferedReader inp2 = new BufferedReader(new FileReader(args[0]));
			// System.out.println("____  _______");
			// System.out.println(File_Settings.procFC_utilPath+"0.txt");
			//			BufferedReader in2 = new BufferedReader(new FileReader(File_Settings.procFC_utilPath+"0.txt"));
			BufferedReader in2 = new BufferedReader(new FileReader(File_Settings.procFC_utilPath+filename));
			//			BufferedReader in3 = new BufferedReader(new FileReader(File_Settings.procFC_utilPath+"0.txt"));
			BufferedReader in3 = new BufferedReader(new FileReader(File_Settings.procFC_utilPath+filename));
			BufferedReader in4 = new BufferedReader(new FileReader(File_Settings.procFC_utilPath+filename));
			// in4 is to read in the repaired file and (optionally) tokenize and calculate frequencies for tagging.

			//			File file=new File(args[1]); // declaration of output filename
			File file=new File(args[2]); // declaration of output filename
			BufferedWriter output=new BufferedWriter(new FileWriter(file));
			//			BufferedWriter output2=new BufferedWriter(new FileWriter(File_Settings.procFC_utilPath+"0.txt"));
			BufferedWriter output2=new BufferedWriter(new FileWriter(File_Settings.procFC_utilPath+filename));
			BufferedWriter output3=new BufferedWriter(new FileWriter(File_Settings.tokenizedPath+filename));
			BufferedWriter output4=new BufferedWriter(new FileWriter(File_Settings.tokenized2Path+filename));
			String str;
			// First count the number of lines in the file
			// System.out.println("2");
			while ((str = in.readLine()) != null)
			{
				lineCount++;
			}
			in.close();
			System.out.println("Assigning links between nodes (authors, journals, titles, and institutions) for a citation file with " + lineCount + " lines...");
			// Now read in the file to populate the array for preprocessing (fixing line wrap problem)
			String[][] allLines=new String[lineCount][2];
			int curIndex=0;
			String thisLinep;
			while ((str = inp.readLine()) != null)
			{
				// System.out.println("3");
				// System.out.println(str);
				int lineLength=str.length();
				if(str.equals("")) // If the line is blank, fill in the array with null values
				{
					allLines[curIndex][0]="";
					allLines[curIndex][1]="";
					curIndex++;
				}
				else
					if(lineLength<6) // Non-blank lines with length less than six
						// Non-blank lines with length less than six
						// can occur if there is an error in PubMed data or if download was truncated; skip such lines
					{
						allLines[curIndex][0]="";
						allLines[curIndex][1]="";
						curIndex++;
					}
				// If the line is not blank, and contains at least six characters, fill in the array with the text on the line
				if(lineLength>6) // Non-blank lines with length less than six
					// can occur if there is an error in PubMed data or if download was truncated; skip such lines
				{
					//					thisLinep=str;
					// Disallow rogue vertical bars, sometimes found in e-mail addresses
					// They are used as a delimiter downstream
					//					if(str.contains("|"))
					//						System.out.println("Found vertical bar");
					// The \\Q and \\E below are necessary because the vertical bar is a special character.
					// The replaceAll command does not work without them.
					thisLinep=str.replaceAll("\\Q|\\E","-verticalbar-");
					allLines[curIndex][0]=thisLinep.substring(0,4); // The first four characters
					allLines[curIndex][1]=thisLinep.substring(6); // All the remaining characters starting with the 7th
					curIndex++; // increment line counter
				}
			}
			inp.close();

			// Done populating the allLines array. Now fix the line wrap problem and write to output to the new array
			String field; // the data type
			String datum; // the data element
			String repaired; // the repaired (concatenated) data element
			String buildLinep;
			String Tokenized;
			int wrapCount=1;
			for(int i=0; i<lineCount; i++)
			{
				field=(allLines[i][0]);
				datum=(allLines[i][1]);
				int index=i; // the current line which will remain constant while looking for additional lines to concatenate
				if(i+1<lineCount) // only do this if not on the last line
					while(allLines[index+wrapCount][0].equals("    "))
					{
						datum=datum+" "+allLines[index+wrapCount][1];
						wrapCount++;
						i++;
					}
				wrapCount=1; // reset wrapCount value

				if(field.equals(""))
					buildLinep=""; // regenerate the line
				else
					buildLinep=field+"- "+datum; // regenerate the line
				output2.write(buildLinep);
				output2.newLine();	
			}
			inp2.close();
			output2.close();
			// At this point, the repaired file has been written to disk.
			// The line breaks are eliminated.

			// Now, optionally, go through the repaired file line by line,
			// tokenize selected elements, and calculate frequencies.

			// To start, try just tokenizing abstracts.
			//			if(includeTags==1)
			//			{
			String elementType="";
			String element="";
			String tokenized2="";
			String allTokenized2=""; // To gather tokenized strings so that they can
			// subsequently be written to output4 at tokenized2path
			System.out.println("Tokenizing abstracts...");
			while ((str = in4.readLine()) != null)
				if((str.compareTo("")<0)||(str.compareTo("")>0))
					// If it's not a blank line
				{
					elementType=str.substring(0,4);
					element=str.substring(6);
					if(elementType.equalsIgnoreCase("AB  "))
					{
						allTokenized2=allTokenized2+Tokenizer(element);
					}
				}
			output4.write(allTokenized2);
			output4.close();
			// Got the tokens. Now read in this file to sort and calculate frequencies.
			int countLines=0;
			BufferedReader in5 = new BufferedReader(new FileReader(File_Settings.tokenized2Path+filename));
			// in5 is to count lines in in6 to determine size of array.

			while ((str = in5.readLine()) != null)
			{
				countLines++;
			}
			/*
				// Set up an array with the appropriate number of elements
				String[] resultsTokens=new String[countLines];
			 */

			int counter=0;

			// Create a new HashTable that can be sorted
			Hashtable<String, String> ht = new Hashtable();

			BufferedReader in6 = new BufferedReader(new FileReader(File_Settings.tokenized2Path+filename));
			// in6 is to read in the list of tokens of the abstracts, etc., in the retrieved citations results set
			// so that it can be sorted and frequencies calculated.

			while ((str = in6.readLine()) != null)
			{
				ht.put(Integer.toString(counter), str);
				counter++;
			}
			String[] sorted=new String[counter]; // To contain the sorted token list
			// Have a list of tokens. Now sort the list.
			// Vector v = new Vector(ht.keySet());
			Vector v = new Vector(ht.values());
			Toolkit.getDefaultToolkit().beep();
			System.out.println("Alphabetizing tokens...");
			Collections.sort(v);
			counter=0; // reset counter for reuse
			// Populate sorted with data from the sorted hashtable.
			for (Enumeration e = v.elements(); e.hasMoreElements();) {
				String key = (String)e.nextElement();
				String val = (String)ht.get(key);
				sorted[counter]=key; // Place the token in its position in the sorted array
				counter++;
				//	System.out.println("Key: " + key + "     Val: " + val);
			}
			// Determine how many unique values there are
			String currentVal="";
			String previousVal="";
			int numUniqueValues=0;
			for (int i=0; i<counter; i++)
			{
				currentVal=sorted[i];
				if((currentVal.compareTo(previousVal)<0)||(currentVal.compareTo(previousVal)>0))
				{
					// New value
					numUniqueValues++;
				}
				previousVal=currentVal;
			}
			// Make frequencies array to accommodate the unique values

			String[][] frequencies=new String[numUniqueValues][5];
			// Token, frequency in result set, frequency in all corpus, score, include (0 or 1)
			//				numValues=0; // reset numValues
			currentVal=""; // reset current value
			previousVal=""; // reset previous value
			int currentIndex=0;
			int repeatValCount=1;
			for (int i=0; i<counter; i++)
			{
				currentVal=sorted[i];
				if((currentVal.compareTo("")<0)||(currentVal.compareTo("")>0)) // as long as it's not the very first entry
				{
					if((currentVal.compareTo(previousVal)<0)||(currentVal.compareTo(previousVal)>0)) // if it's a new value
					{
						frequencies[currentIndex][0]=previousVal; // Populate string for previous value
						frequencies[currentIndex][1]=Integer.toString(repeatValCount);
						// Populate frequency for previous value
						currentIndex++;
						repeatValCount=1; // Reset repeat value counter
					}
					else // increment the repeat value counter
						repeatValCount++;
					previousVal=currentVal;
				}
			}
			// Done populating frequencies array with unique values.
			// Now assign scores. We want tokens that are common in the retrieved result set
			// but not common in the corpus as a whole. 

			// First, read in the frequencies table for the corpus as a whole.
			// Required format is token|frequency

			BufferedReader in7 = new BufferedReader(new FileReader(File_Settings.allCorpusTokensPath+"0.txt"));
			countLines=0;
			while ((str = in7.readLine()) != null)
			{
				countLines++;
			}
			String[][] allTokensC=new String[countLines][2]; // All tokens in corpus
			BufferedReader in8 = new BufferedReader(new FileReader(File_Settings.allCorpusTokensPath+"0.txt"));
			countLines=0;
			while ((str = in8.readLine()) != null)
			{
				StringTokenizer t = new StringTokenizer(str,"|");
				while(t.hasMoreTokens())
				{
					allTokensC[countLines][0]=t.nextToken();
					allTokensC[countLines][1]=t.nextToken();
				}
				countLines++;
			}
			// Done populating array of all tokens in corpus with frequencies.
			// Now move through frequencies array to assign scores.

			String curTokenInFreq="";
			String curTokenInAll="";
			String freqInAll="";
			int foundMatch=0;
			System.out.println("Matching tokens in result set with tokens in corpus...");
			for(int i=0; i<numUniqueValues; i++)
			{
				curTokenInFreq=frequencies[i][0];
				curTokenInAll="";
				for(int j=0; j<countLines; j++)
				{	
					if(foundMatch==0); // Only continue if no match has been found yet
					{
						curTokenInAll=allTokensC[j][0];
						if(curTokenInFreq.equals(curTokenInAll))
							// Match, so populate frequencies with
							// the frequency in the entire corpus
						{
							foundMatch=1;
							freqInAll=allTokensC[j][1];
							frequencies[i][2]=freqInAll;
							// Token, frequency in results, frequency in all, score, include
						}
					}
				}
				if(foundMatch==0)
					// The token is not in the greater corpus
					// Assign a value of 0 to frequency
				{
					frequencies[i][2]="0";
				}
				foundMatch=0; // Reset foundMatch
			}
			// Done populating frequencies with data on frequency of token
			// in greater corpus. Now calculate scores.
			double Score=0;
			double freqInResults=0;
			double freqInCorpus=0;
			for(int i=0; i<numUniqueValues; i++)
			{
				freqInResults=(double)Integer.parseInt(frequencies[i][1]);
				freqInCorpus=(double)Integer.parseInt(frequencies[i][2]);
				if(freqInCorpus==0)
					frequencies[i][3]="infinite";
				else
				{
					Score=freqInResults/freqInCorpus;
					frequencies[i][3]=Double.toString(Score);
				}
			}
			// Done assigning scores

			// Now calculate thresholds for inclusion of tags
			//				int threshold=(int)numUniqueValues/5;
//			double threshold=0.52;
			double threshold=999;
			//			double threshold=0.1;
			double thresholdForInf=5;
			//			double thresholdForInf=5;

			// Now run through frequencies and assign include/not include values
			// 0 = do not include; 1 = include
			// &&&

			String scoreToEval="";
			double decimalScore=0;
			int curTokFreq=0;
			for(int i=0; i<numUniqueValues; i++)
			{
				scoreToEval=frequencies[i][3];
					if((scoreToEval.compareTo("infinite")<0)||(scoreToEval.compareTo("infinite")>0)) // if not "infinite"
					decimalScore=Double.parseDouble(scoreToEval);
				curTokFreq=Integer.parseInt(frequencies[i][1]);
				if(scoreToEval.equals("infinite"))
				{
					if(curTokFreq>thresholdForInf)
						// Include
						frequencies[i][4]="1";
					else
						// Exclude
						frequencies[i][4]="0";
				}
				else
					// score is a decimal value
				{
					if(decimalScore>threshold)
						// Include
						frequencies[i][4]="1";
					else
						// Exclude
						frequencies[i][4]="0";
				}
			}
			// For development purposes, optionally output the Frequencies table as a CSV file.
			int outputFrequencies=1;
			String buildFreq="";
			String tokenO="";
			String freqInResultsO="";
			String freqInGreaterCorpusO="";
			String scoreO="";
			String includeO="";
			if(outputFrequencies==1)
			{
				BufferedWriter freqOut=new BufferedWriter(new FileWriter(File_Settings.frequenciesPath+filename+".csv"));
				buildFreq=buildFreq+"token,freq_in_results,freq_in_corpus,score,include" + "\r\n"; // Row headings
				for(int i=0; i<numUniqueValues; i++)
				{
					tokenO=frequencies[i][0].replaceAll("\"","\"\"");
					freqInResultsO=frequencies[i][1];
					freqInGreaterCorpusO=frequencies[i][2];
					scoreO=frequencies[i][3];
					includeO=frequencies[i][4];
					buildFreq=buildFreq + "\"" + tokenO + "\"," + "\"" + freqInResultsO + "\"," + "\"" + freqInGreaterCorpusO + "\"," + "\"" + scoreO + "\"," + "\"" + includeO + "\"" + "\r\n";
				}
				freqOut.write(buildFreq);
				freqOut.close();
			}

			// Create a hashmap indicating whether each tag is to be included, for faster lookup later
			HashMap<String, String> h = new HashMap<String, String>(numUniqueValues);
			String tokenH="";
			String includeH="";
			for(int i=0; i<numUniqueValues; i++)
			{
				tokenH=frequencies[i][0];
				includeH=frequencies[i][4];
				h.put(tokenH, includeH);
			}

			//			} // End if includeTags=1



			// Now go line by line and assign adjacencies.
			String thisLine="";
			int linesThisCitation=0;
			// Now count how many lines there are in the next record
			while ((str = in2.readLine()) != null)
			{
				if(str.equals(""))
					// Reached a blank line. Now process what's in the array for this record.
				{
					auCountLeft=0; auCountRight=0; // Reset author counters
					String[][] allLinesThisCitation=new String[linesThisCitation][2];
					// Assemble mini-array allLinesThisCitation for all lines in the next abstract
					// then populate it for this citation
					for(int i=0; i<linesThisCitation; i++)
					{
						thisLine=in3.readLine();
						// Remember a sample data line might look like this:
						// AU  - Fazlioglu Y
						// or
						// TI  - A sensory integration therapy program on sensory problems for children with autism.
						allLinesThisCitation[i][0]=thisLine.substring(0,4); // The first four characters
						allLinesThisCitation[i][1]=thisLine.substring(6); // All the remaining characters starting with the 7th
					}
					// Done populating allLinesThisCitation array for this citation.
					// Now run through allLinesThisCitation array and generate adjacencies to output file
					// entityType will be populated only if it matches one of the desired types,
					// e.g. author, journal, institutional affiliation
					String nodeTypeLeft=""; // Empty string to hold type of node on left, to simplify expression
					String nodeTypeRight=""; // Empty string to hold type of node on right, to simplify expression
					String entityLeft=""; // Empty string to hold label/description of left entity, to simplify expression
					String entityRight=""; // Empty string to hold label/description of right entity, to simplify expression
					String yearPublished=""; // Empty string to hold year published
					String buildLine=""; // Empty string to build RSF output line by line
					for(int i=0; i<linesThisCitation; i++)
					{
						if(includeAuthors==1)
						{
							if (allLinesThisCitation[i][0].equalsIgnoreCase("AU  "))
							{
								auCountLeft++;
								nodeTypeLeft="person"; // author
							}
						}
						if (allLinesThisCitation[i][0].equalsIgnoreCase("DP  ")) // year published
						{
							yearPublished=allLinesThisCitation[i][1].substring(0,4); // Grab year published
						}
						if(includePapers==1)
						{
							if (allLinesThisCitation[i][0].equalsIgnoreCase("TI  ")) nodeTypeLeft="paper"; // title
						}
						if(includeJournals==1)
						{
							if (allLinesThisCitation[i][0].equalsIgnoreCase("TA  ")) nodeTypeLeft="journal";
						}
						if(includeGrants==1)
						{
							if (allLinesThisCitation[i][0].equalsIgnoreCase("GR  ")) nodeTypeLeft="grant";
						}
						if(includeInstitutions==1)
						{
							if (allLinesThisCitation[i][0].equalsIgnoreCase("AD  ")) nodeTypeLeft="institution";
						}
						if(includeAbstracts==1)
						{
							if (allLinesThisCitation[i][0].equalsIgnoreCase("AB  ")) nodeTypeLeft="abstract";
						}

						if(includeSubstances==1)
						{
							if (allLinesThisCitation[i][0].equalsIgnoreCase("RN  ")) nodeTypeLeft="substance";
						}
						if(includeMainheadings==1)
						{
							if (allLinesThisCitation[i][0].equalsIgnoreCase("MH  "))
								// Only accept starred main headings (the ones the article is really
								// considered to be "about" by the catalogers at NLM
							{
								if(allLinesThisCitation[i][1].contains("*"))
								{
									nodeTypeLeft="mainheading";
								}
								if(allLinesThisCitation[i][1].contains("/"))
								{
									nodeTypeLeft="mainheading";
								}
							}
						}
						entityLeft=allLinesThisCitation[i][1];
						if(nodeTypeLeft.equals("paper")) // title
						{	
							entityLeft=entityLeft + " (" + yearPublished + ")"; // append year published to title
							// System.out.println(entityLeft);
						}

						//						StringTokenizer tokenizer=new StringTokenizer(allLinesThisCitation[i][1]," ");
						StringTokenizer tokenizer=new StringTokenizer(entityLeft," ");
						// Count the tokens
						int numTokens=tokenizer.countTokens();
						// Instantiate an array to hold the tokens
						String[] a = new String[numTokens];
						String currentTag="";

						if (nodeTypeLeft.length()>0) // In other words, if entityType is not null
							// (i.e., if the current line matches one of the desired entity types)
							// then create adjacencies
						{
							if(nodeTypeLeft.equalsIgnoreCase("abstract"))
							{
								Tokenized=Tokenizer(entityLeft);
								allTokenized=allTokenized + "|" + Tokenized;
							}


							// Now determine node types for the right-hand nodes in the adjacency
							for(int j=i+1; j<(linesThisCitation); j++)
							{
								if(includeAuthors==1)
								{
									if (allLinesThisCitation[j][0].equalsIgnoreCase("AU  "))
									{
										auCountRight++;
										nodeTypeRight="person"; // author
									}
								}
								if(includePapers==1)
								{
									if (allLinesThisCitation[j][0].equalsIgnoreCase("TI  ")) nodeTypeRight="paper";
								}
								if(includeJournals==1)
								{
									if (allLinesThisCitation[j][0].equalsIgnoreCase("TA  ")) nodeTypeRight="journal";
								}
								if(includeGrants==1)
								{
									if (allLinesThisCitation[j][0].equalsIgnoreCase("GR  ")) nodeTypeRight="grant";
								}
								if(includeInstitutions==1)
								{
									if (allLinesThisCitation[j][0].equalsIgnoreCase("AD  ")) nodeTypeRight="institution";
								}
								if(includeSubstances==1)
								{
									if (allLinesThisCitation[j][0].equalsIgnoreCase("RN  ")) nodeTypeRight="substance";
								}
								if(includeAbstracts==1)
								{
									if (allLinesThisCitation[j][0].equalsIgnoreCase("AB  ")) nodeTypeRight="abstract";
								}
								if(includeMainheadings==1)
								{
									if (allLinesThisCitation[j][0].equalsIgnoreCase("MH  "))
										// Only accept starred main headings (the ones the article is really
										// considered to be "about" by the catalogers at NLM
									{
										if(allLinesThisCitation[j][1].contains("*"))
										{
											nodeTypeRight="mainheading";
										}
										if(allLinesThisCitation[j][1].contains("/"))
										{
											nodeTypeRight="mainheading";
										}
									}
								}
								if (nodeTypeRight.length()>0) // In other words, if entityType is not null
									// (i.e., if the current line matches one of the desired entity types)
									// then run through the remainder of the citation to create adjacencies
								{
									entityLeft=allLinesThisCitation[i][1];
									if(nodeTypeLeft.equals("paper")) // title
									{	
										entityLeft=entityLeft + " (" + yearPublished + ")"; // append year published to title
										// System.out.println(entityLeft);
									}
									entityRight=allLinesThisCitation[j][1];

									// Process MeSH headings to make terms with "/*" equivalent to terms with "/"
									// by removing "*"
									if(nodeTypeLeft.equals("mainheading"))
									{
										entityLeft=entityLeft.replaceAll("\\*", "");
									}
									if(nodeTypeRight.equals("mainheading"))
									{
										entityRight=entityRight.replaceAll("\\*", "");
									}
									// RUN PARSEINST
									// to pull out institution from long institution name								
									if(parseInstitutions==1)
									{
										if(nodeTypeLeft.equals("institution"))
										{
											entityLeft=parseInst(entityLeft);
										}
										if(nodeTypeRight.equals("institution"))
										{
											entityRight=parseInst(entityRight);
										}
									}
									//									*/
									// Now generate expression suitable for output format,
									// in this case RSF format used in CCVisu
									if(auCountRight>maxAuth)
									{
										if(nodeTypeRight.equalsIgnoreCase("person"))
											exclude=true;
									}
									if(auCountLeft>maxAuth)
									{
										if(nodeTypeLeft.equalsIgnoreCase("person"))
											exclude=true;
									}
									if(entityRight.equalsIgnoreCase("et al"))
										exclude=true;
									if(entityLeft.equalsIgnoreCase("et al"))
										exclude=true;

									// Only link the first author to the institution
									if(nodeTypeLeft.equalsIgnoreCase("institution"))
										if(nodeTypeRight.equalsIgnoreCase("person"))
											if(auCountRight>1)
												exclude=true;

									// Remove unwanted links based on data schema
									// Default link excludes for entity type person
									if(nodeTypeLeft.equalsIgnoreCase("person"))
									{
										//										if(nodeTypeRight.equalsIgnoreCase("person"))
										//											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("journal"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("grant"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("substance"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("mainheading"))
											exclude=true;
									//	if(nodeTypeLeft.equalsIgnoreCase("abstract"))
									//		exclude=true;
									}
									if(nodeTypeRight.equalsIgnoreCase("person"))
									{
										//										if(nodeTypeLeft.equalsIgnoreCase("person"))
										//											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("journal"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("grant"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("substance"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("mainheading"))
											exclude=true;
									//	if(nodeTypeLeft.equalsIgnoreCase("abstract"))
									//		exclude=true;
									}
									// Default link excludes for paper							
									if(nodeTypeLeft.equalsIgnoreCase("paper"))
									{
										if(nodeTypeRight.equalsIgnoreCase("institution"))
											exclude=true;
									}
									if(nodeTypeRight.equalsIgnoreCase("paper"))
									{
										if(nodeTypeLeft.equalsIgnoreCase("institution"))
											exclude=true;
									}
									// Default link excludes for journal	
									if(nodeTypeLeft.equalsIgnoreCase("journal"))
									{
										if(nodeTypeRight.equalsIgnoreCase("person"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("grant"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("substance"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("institution"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("mainheading"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("abstract"))
											exclude=true;
									}
									if(nodeTypeRight.equalsIgnoreCase("journal"))
									{
										if(nodeTypeLeft.equalsIgnoreCase("person"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("grant"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("substance"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("institution"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("mainheading"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("abstract"))
											exclude=true;
									}
									// Default link excludes for grant
									if(nodeTypeLeft.equalsIgnoreCase("grant"))
									{
										if(nodeTypeRight.equalsIgnoreCase("person"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("journal"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("grant"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("substance"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("institution"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("mainheading"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("abstract"))
											exclude=true;
									}

									if(nodeTypeRight.equalsIgnoreCase("grant"))
									{
										if(nodeTypeLeft.equalsIgnoreCase("person"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("journal"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("grant"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("substance"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("institution"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("mainheading"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("abstract"))
											exclude=true;
									}
									// Default link excludes for substance
									if(nodeTypeLeft.equalsIgnoreCase("substance"))
									{
										if(nodeTypeRight.equalsIgnoreCase("person"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("journal"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("grant"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("substance"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("institution"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("mainheading"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("abstract"))
											exclude=true;
									}

									if(nodeTypeRight.equalsIgnoreCase("substance"))
									{
										if(nodeTypeLeft.equalsIgnoreCase("person"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("journal"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("grant"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("substance"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("institution"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("mainheading"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("abstract"))
											exclude=true;
									}
									// Default link excludes for institution
									if(nodeTypeLeft.equalsIgnoreCase("institution"))
									{
										//										if(nodeTypeRight.equalsIgnoreCase("paper"))
										//											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("journal"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("grant"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("substance"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("mainheading"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("abstract"))
											exclude=true;
									}
									if(nodeTypeRight.equalsIgnoreCase("institution"))
									{
										//										if(nodeTypeLeft.equalsIgnoreCase("paper"))
										//											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("journal"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("grant"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("substance"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("mainheading"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("abstract"))
											exclude=true;
									}
									// Default link excludes for mainheading
									if(nodeTypeLeft.equalsIgnoreCase("mainheading"))
									{
										if(nodeTypeRight.equalsIgnoreCase("person"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("journal"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("grant"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("substance"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("institution"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("abstract"))
											exclude=true;
										//										if(nodeTypeRight.equalsIgnoreCase("mainheading"))
										//											exclude=true;
									}
									if(nodeTypeRight.equalsIgnoreCase("mainheading"))
									{
										if(nodeTypeLeft.equalsIgnoreCase("person"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("journal"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("grant"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("substance"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("institution"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("abstract"))
											exclude=true;
										//										if(nodeTypeLeft.equalsIgnoreCase("mainheading"))
										//											exclude=true;
									}
									if(exclude==false) // Output this line only if the exclude flag hasn't been activated
									{
										if(simpleAdjacency==0)
											buildLine=(nodeTypeLeft + "-" + nodeTypeRight + " \"" + nodeTypeLeft + "_" + entityLeft + "\" \"" + nodeTypeRight + "_"  + entityRight +"\" 1");
										if(simpleAdjacency==1)
											buildLine=(entityLeft + "|" + entityRight);
										// System.out.println(buildLine);
										output.write(buildLine);
										output.newLine();
									}
									nodeTypeRight=""; // Reset nodeTypeRight to null string
									exclude=false; // Reset exclude flag to false
								} // end if for right side (if entitytype is not null; if right-hand matches desired entity type)
							} // end for loop for right side

							// Now assign links from elements to their tags
							if(includeTags==1)
							{
								for(int tok=0; tok<numTokens;tok++)
									a[tok]=(tokenizer.nextToken());

								// Now output the tags with their parents
								for(int gt=0; gt<numTokens; gt++)
								{
									//		System.out.println(a[gt]);
									currentTag=a[gt];
									nodeTypeRight="tag";
									entityRight = currentTag.toLowerCase();
									entityRight = entityRight.replaceAll("\\Q(\\E","");
									entityRight = entityRight.replaceAll("\\Q)\\E","");
									entityRight = entityRight.replaceAll("\\Q/\\E","");
									entityRight = entityRight.replaceAll("\\Q,\\E","");
									entityRight = entityRight.replaceAll("\\Q&\\E","");
									entityRight = entityRight.replaceAll("\\Q%\\E","");
									entityRight = entityRight.replaceAll("\\Q;\\E","");
									entityRight = entityRight.replaceAll("\\Q.\\E","");
									entityRight = entityRight.replaceAll("\\Q:\\E","");

									double r=Math.random();
									int includeTag=0;
									//								if(r>0.9) // Only pick one in ten words in abstracts
									String foundKey="";
									// Determine whether the token has been selected for inclusion
									if(h.containsKey(currentTag.toString()))
									{
										foundKey=h.get("a");
										if(h.get(currentTag.toString()).equals("1"))
										{
											// Include tag
											includeTag=1;
										}
									}

									if(nodeTypeLeft.equals("abstract"))	
									{
										if(includeTag==1)
										{
											buildLine=(nodeTypeLeft + "-" + "tag" + " \"" + nodeTypeLeft + "_" + entityLeft + "\" \"" + nodeTypeRight + "_"  + entityRight +"\" 1");
											output.write(buildLine);
											output.newLine();
										}
									}
									/* TEMPORARILY REMOVED; abstracts only, for now
									if(nodeTypeLeft.equals("paper") || nodeTypeLeft.equals("mainheading"))
									{
										if(includeTag==1)
										{
											buildLine=(nodeTypeLeft + "-" + "tag" + " \"" + nodeTypeLeft + "_" + entityLeft + "\" \"" + nodeTypeRight + "_"  + entityRight +"\" 1");
											//										System.out.println(buildLine);
											output.write(buildLine);
											output.newLine();
										}
									}
									 */

								} // end if for output tags with their parents
							} // end if for assigning links to tags
						} // end if for left side
						auCountRight=0; // Reset counter for author count in inner loop (remainder of lines for this citation)
						nodeTypeLeft=""; // Reset nodeTypeLeft to null string
						nodeTypeRight=""; // Reset nodeTypeLeft to null string
						entityLeft="";
						entityRight="";	
						exclude=false;
					} // end for loop to process the citation
					auCountLeft=0; // Reset counter for number of authors in the citation
				} // end if; now proceed on with iterator in2
				if(str.equals("")==false) // as long as not currently on a blank line
					linesThisCitation++;
				else
				{
					linesThisCitation=0;
					in3.readLine();
				}
			} // end while; iterator in2 has reached the end of the file
			in.close();
			output.close();
			System.out.println("Finished assigning links.");
			output3.write(allTokenized);
			output3.close();

			System.out.println(allTokenized);
		} // end try
		catch (IOException e) {
			System.out.println(e);
			e.printStackTrace();
		} // end catch
	} // end main

	public static String parseInst(String inst) {
		int tokenCount=0; // initialize token counter
		int curIndex=0; // initialize current index in array of comma-delimited strings
		String instShort=""; // initialize return variable, shortened institution name
		String currentToken=""; // initialize value of current token in array
		String[] institutions={
				"Centers", // for CDC
				"Clinique",
				"College",
				"University",
				"Corporation",
				"Council",
				"Hopital",
				"Hospital",
				"Institut",
				"Institute",
				"Institutes", // for NIH
				"Institutet",
				"Laboratoire",
				"Laboratory",
				"Library",
				"Ltd",
				"Samusocial",
				"Univ.",
				"Universidad",
				"Universidade",
				"Universita",
				"Universitas",
				"Universitat",
				"Universitatsklinikum",
				"Universite",
				"Universiteit",
		};
		StringTokenizer tokenizer = new StringTokenizer(inst, ","); // split input string on commas
		String currentInstitution="";
		// now count the number of tokens
		while(tokenizer.hasMoreTokens())
		{
			tokenizer.nextToken();
			tokenCount++;
		}
		String[] tokens=new String[tokenCount]; // initialize array to store tokens for further manipulation
		StringTokenizer tokenizer2 = new StringTokenizer(inst, ","); // second tokenizer to read tokens into array
		while(tokenizer2.hasMoreTokens())
		{
			tokens[curIndex]=tokenizer2.nextToken(); // read tokens into array
			curIndex++;
		}
		for(int i=0; i<curIndex; i++)
		{
			currentToken=tokens[i];
			for(int j=0; j<institutions.length; j++)
				//		if(currentToken.contains("University")==true)
			{
				currentInstitution=institutions[j];
				if(currentToken.contains(currentInstitution)==true)
				{
					//					System.out.println(currentToken);
					instShort=currentToken.trim();
				}
			}
		}
		if(instShort=="") // If institution parser was not able to resolve a short institutional affiliation
			return inst;
		else
			return instShort;
	}
	private static String Tokenizer(String S)  {
		String outString="";
		String nextToken="";
		StringTokenizer t=new StringTokenizer(S," ");
		while(t.hasMoreTokens())
		{
			nextToken=t.nextToken();
			nextToken = nextToken.toLowerCase();
			nextToken = nextToken.replaceAll("\\Q(\\E","");
			nextToken = nextToken.replaceAll("\\Q)\\E","");
			nextToken = nextToken.replaceAll("\\Q/\\E","");
			nextToken = nextToken.replaceAll("\\Q,\\E","");
			nextToken = nextToken.replaceAll("\\Q&\\E","");
			nextToken = nextToken.replaceAll("\\Q%\\E","");
			nextToken = nextToken.replaceAll("\\Q;\\E","");
			nextToken = nextToken.replaceAll("\\Q.\\E","");
			nextToken = nextToken.replaceAll("\\Q:\\E","");
			nextToken = nextToken.replaceAll("\\Q=\\E","");
			//            outString=outString + "|" + nextToken;
			outString=outString + "\r\n" + nextToken;
		}
		return outString;
	}
} // end class

