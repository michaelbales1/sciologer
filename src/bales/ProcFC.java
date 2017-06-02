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
import java.awt.Color;

// import java.awt.Toolkit; // for beep only
import org.apache.logging.log4j.Logger; 
import org.apache.logging.log4j.LogManager;

public class ProcFC  {
	private static final Logger logger = LogManager.getLogger(ProcFC.class.getName());
	public static void main(String args[]) {
		//		System.out.println("Launching full citation processor...");
		//		System.out.println(args.length);
		/*		if (args.length != 3) {
			System.err.println("Usage: cmd -fc infile utilfile -a outfile -filename filename");
			//System.exit(1);
		}
		 */
		// Types of nodes to include in the network
		int simpleAdjacency=0; // only output simple adjacency list for analytic purposes;
		// simpleAdjacency mode will output file in the "a" folder and then terminate the program. 
		int includePapers=1;
		int includeAuthors=1;
		int includeJournals=1;
		int includeInstitutions=1;
		int includeGrants=1;
		int includeMainHeadings=1;
		int includeSubstances=1;
		int includeAbstracts=1;
		int includeAbstractWords=1;
		int includeTags=1;
		int includeUmlsVariants=1;
		int parseInstitutions=1;
		int lineCount=0; // to count lines in file
		int auCountLeft=0; // to count authors in a single citation
		int auCountRight=0; // to count authors in a single citation
		// String filename=args[1];
		String fileName="";
		String fullCitationsFile="";
		String allTokenized=""; // For utility code to determine MEDLINE token frequencies
		String adjacenciesFileName="";
		// RESET MAXIMUM AUTHORS HERE
		//		int maxAuth=10; // maximum number of authors per citation for which to create links
		int maxAuth=10; // maximum number of authors per citation for which to create links		
		// NEW CODE HERE FEBRUARY 2010
		// Parse command-line options.
		for (int i = 0; i < args.length; ++i) {
			// Set location of full citations.
			if (args[i].equalsIgnoreCase("-fc")) {
				++i;
				chkAvail(args, i);
				try {
					//	 out = new PrintWriter(new BufferedWriter(new FileWriter(args[i])));
					fullCitationsFile=args[i].replaceAll("_"," ");
				}
				catch (Exception e) {
					logger.error("Exception while opening file '" + args[i] + "' for writing: ");
					logger.error(e); e.printStackTrace(); 
					//System.exit(1);
				}	
			}
			else if (args[i].equalsIgnoreCase("-filename")) {
				++i;
				chkAvail(args, i);
				try {
					//	 out = new PrintWriter(new BufferedWriter(new FileWriter(args[i])));
					fileName=args[i].replaceAll("_"," ");
				}
				catch (Exception e) {
					logger.error("Exception while opening file '" + args[i] + "' for writing: ");
					logger.error(e); e.printStackTrace(); 
					//System.exit(1);
				}	
			}
			else if (args[i].equalsIgnoreCase("-a")) {
				++i;
				chkAvail(args, i);
				try {
					//	 out = new PrintWriter(new BufferedWriter(new FileWriter(args[i])));
					adjacenciesFileName=args[i].replaceAll("_"," ");
				}
				catch (Exception e) {
					logger.error("Exception while opening file '" + args[i] + "' for writing: ");
					logger.error(e); e.printStackTrace(); 
					//System.exit(1);
				}	
			}
			// Whether to include links
			else if (args[i].equalsIgnoreCase("-excludeLinks")) {
			}
			// Whether to include journals
			else if (args[i].equalsIgnoreCase("-excludePapers")) {
				includePapers=0;
			}
			else if (args[i].equalsIgnoreCase("-excludeAuthors")) {
				includeAuthors=0;
			}
			else if (args[i].equalsIgnoreCase("-excludeJournals")) {
				includeJournals=0;
			}
			else if (args[i].equalsIgnoreCase("-excludeInstitutions")) {
				includeInstitutions=0;
			}
			else if (args[i].equalsIgnoreCase("-excludeGrants")) {
				includeGrants=0;
			}
			else if (args[i].equalsIgnoreCase("-excludeMainHeadings")) {
				includeMainHeadings=0;
			}
			else if (args[i].equalsIgnoreCase("-excludeSubstances")) {
				includeSubstances=0;
			}
			else if (args[i].equalsIgnoreCase("-excludeAbstracts")) {
				includeAbstracts=0;
			}
			else if (args[i].equalsIgnoreCase("-excludeTags")) {
				includeTags=0;
			}
			else if (args[i].equalsIgnoreCase("-excludeUmlsVariants")) {
				includeUmlsVariants=0;
			}

			// Run external program.
			else if (args[i].equalsIgnoreCase("-r") || args[i].equalsIgnoreCase("-runext")) {
				// do nothing
			}
			// Dummy option -- is used because arguments are not yet handled properly;
			// I plan to fix this -- MB
			else if (args[i].equalsIgnoreCase("-dummy")) {
				// do nothing
			}
		} // for parsing command-line options.	
		// END NEW CODE HERE FEBRUARY 2010		
		// Create empty file with correct name in procfc_util
		try {
			File file=new File(File_Settings.procFC_utilPath+fileName);
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
			// THE NEXT THREE LINES REPLACED FEBRUARY 2010
			// BufferedReader in = new BufferedReader(new FileReader(args[0]));
			// BufferedReader inp = new BufferedReader(new FileReader(args[0]));
			// BufferedReader inp2 = new BufferedReader(new FileReader(args[0]));

			BufferedReader in = new BufferedReader(new FileReader(fullCitationsFile));
			BufferedReader inp = new BufferedReader(new FileReader(fullCitationsFile));
			BufferedReader inp2 = new BufferedReader(new FileReader(fullCitationsFile));


			// System.out.println("____  _______");
			// System.out.println(File_Settings.procFC_utilPath+"0.txt");
			//			BufferedReader in2 = new BufferedReader(new FileReader(File_Settings.procFC_utilPath+"0.txt"));
			// THE NEXT LINE REMOVED FEBRUARY 2010
			//			BufferedReader in2 = new BufferedReader(new FileReader(File_Settings.procFC_utilPath+filename));
			BufferedReader in2 = new BufferedReader(new FileReader(File_Settings.procFC_utilPath+fileName));
			//			BufferedReader in3 = new BufferedReader(new FileReader(File_Settings.procFC_utilPath+"0.txt"));
			BufferedReader in3 = new BufferedReader(new FileReader(File_Settings.procFC_utilPath+fileName));
			BufferedReader in4 = new BufferedReader(new FileReader(File_Settings.procFC_utilPath+fileName));
			// in4 is to read in the repaired file and (optionally) tokenize and calculate frequencies for tagging.

			//			File file=new File(args[1]); // declaration of output filename
			// THE NEXT LINE REMOVED IN FEBRUARY 2010
			// File file=new File(args[2]); // declaration of output filename
			File file=new File(adjacenciesFileName);
			BufferedWriter output=new BufferedWriter(new FileWriter(file));
			//			BufferedWriter output2=new BufferedWriter(new FileWriter(File_Settings.procFC_utilPath+"0.txt"));
			BufferedWriter output2=new BufferedWriter(new FileWriter(File_Settings.procFC_utilPath+fileName));
			BufferedWriter output3=new BufferedWriter(new FileWriter(File_Settings.tokenizedPath+fileName));
			BufferedWriter output4=new BufferedWriter(new FileWriter(File_Settings.tokenized2Path+fileName));
			String str;
			// First count the number of lines in the file
			// System.out.println("2");
			while ((str = in.readLine()) != null)
			{
				lineCount++;
			}
			in.close();
			System.out.println("Identifying nodes in citation file with " + lineCount + " lines...");
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

			// Done populating the allLines array. Now do preprocessing steps:
			// fix the line wrap problem
			// merge ambiguous author names
			// pretokenize abstracts
			// identify UMLS lexical variants
			// write to output to the new array
			String field; // the data type
			String datum; // the data element
			String repaired; // the repaired (concatenated) data element
			String buildLinep;
			String Tokenized;
			int wrapCount=1;
			Hashtable<String, String> variants = new Hashtable(); // FOR NODE MERGING CODE
			Hashtable<String, String> umlsVariants = new Hashtable(); // FOR UMLS LEXICAL VARIANTS
			BufferedReader in9 = new BufferedReader(new FileReader(File_Settings.umlsVariantsPath+"umls_with_lexical_variants_all.txt"));


			/* This code is slow; temporarily removed on 5-4-10
			 * 
			 * 
			// Read file of UMLS lexical variants and put into hash table
			int variantsCount=0;
			while ((str = in9.readLine()) != null)
			{
				variantsCount++;
				umlsVariants.put(str, Integer.toString(variantsCount));
			}
			 */
			for(int i=0; i<lineCount-2; i++)
			{
				System.out.println("i=" + i + "lineCount=" + lineCount + " wrapCount=" + wrapCount);
				field=(allLines[i][0]);
				datum=(allLines[i][1]);
				int index=i; // the current line which will remain constant while looking for additional lines to concatenate
				if(i+2<lineCount) // only do this if not near the last line
				{
					while(allLines[index+wrapCount][0].equals("    "))
					{
						datum=datum+" "+allLines[index+wrapCount][1];
						wrapCount++;
						i++;
					}
				}
				wrapCount=1; // reset wrapCount value

				if(field.equals("")) // if it's a blank line between citations
					buildLinep=""; // regenerate the line
				else
					buildLinep=field+"- "+datum; // regenerate the line

				if(field.equals("AB  "))
				{
					//					System.out.println("AB");
					String preTokenized="";
					String foundVariants="";
					preTokenized=preTokenized+Tokenizer(datum,"abstractWord");
					foundVariants=foundVariants+IdentifyVariants(preTokenized,umlsVariants);
					output2.write(foundVariants);
					output2.write(preTokenized);
				}
				output2.write(buildLinep);
				output2.newLine();
				// NODE MERGING CODE
				// Normalize the author name field by taking only the first character of the last token
				// Jones SA is recorded as Jones S
				// Hash table format is two string-columns, normalized and variants
				// normalized: Jones S (normalized)
				// variants: Jones S / Jones SA / ... / Jones SK
				// Space is retained so that it remains possible to search the variants list for "Jones S"

				String normalized="";
				String datumWithSpace=datum + " ";
				// Populate the variants hashtable with the variants
				if(field.equals("AU  "))
				{
					normalized=Normalizer(datum);
					String currentVariants="";
					if(variants.containsKey(normalized))
						// if the normalized version is already in the variants hashtable
					{
						currentVariants=variants.get(normalized);
						if (currentVariants.contains(datumWithSpace))
							// The search is for "Jones S " so that the occurrence of "Jones SA" in the list does not incorrectly indicate that "Jones S" was found
						{
							//	Already contains this variant, so do nothing
						}
						else
							// Does not yet contain this variant, so add it
							variants.put(normalized, currentVariants+"/ "+datumWithSpace); // Add the variant
					}
					else
					{
						// the normalized version is not yet in the variants hashtable
						variants.put(normalized,datumWithSpace);
					}
				}
			}
			inp2.close();
			output2.close();
			// At this point, the repaired file has been written to disk.
			// The line breaks are eliminated; abstracts are pretokenized; author names are merged.

			// Now, optionally, go through the repaired file line by line,
			// tokenize selected elements, and calculate frequencies.

			// To start, try just tokenizing abstracts.

			if(includeTags==1)
			{
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
							allTokenized2=allTokenized2+Tokenizer(element,"inlineAbstract");
						}
					}
				output4.write(allTokenized2);
				output4.close();
				// Got the tokens.
				/*
			// Now, optionally, stem
			// Create array of parameters for SnowballStemmer

			String[] StemmerParams=new String[4];
			StemmerParams[0]="english";
			StemmerParams[1]=File_Settings.tokenized2Path + fileName;
			StemmerParams[2]="-o";
			StemmerParams[3]=File_Settings.stemmedPath + fileName;			
			try {
				StemmerImplemented.main(StemmerParams);
			} catch (Throwable e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
				 */
			}
			if(includeTags==1)
			{

				// Now read in this file to sort and calculate frequencies.
				int countLines=0;
				BufferedReader in5 = new BufferedReader(new FileReader(File_Settings.tokenized2Path+fileName));
				//			BufferedReader in5 = new BufferedReader(new FileReader(File_Settings.stemmedPath+fileName));

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

				BufferedReader in6 = new BufferedReader(new FileReader(File_Settings.tokenized2Path+fileName));
				//			BufferedReader in6 = new BufferedReader(new FileReader(File_Settings.stemmedPath+fileName));
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
				// Toolkit.getDefaultToolkit().beep();
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

				String[][] frequencies=new String[numUniqueValues][9];
				// Token, frequency in result set, frequency in all corpus, score, include (0 or 1), normalized frequency in results, normalized frequency in corpus, log normalized frequency in results, log normalized frequency in corpus
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

				/*
			// OLD WAY -- populate an array of all tokens in the corpus and their frequencies
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
				 */

				// NEW WAY -- populate a hash table of all tokens in the corpus and their frequencies
				Hashtable<String, String> htACT = new Hashtable();
				BufferedReader in8 = new BufferedReader(new FileReader(File_Settings.allCorpusTokensPath+"0.txt"));
				while ((str = in8.readLine()) != null)
				{
					StringTokenizer t = new StringTokenizer(str,"|");
					while(t.hasMoreTokens())
					{
						htACT.put(t.nextToken(), t.nextToken());
					}
				}

				// Now move through frequencies array to assign scores THE NEW WAY			
				System.out.println("Looking up tokens in result set in the hash table of tokens in corpus...");
				String freqInACT="";
				String curTokenInFreq="";
				for(int i=0; i<numUniqueValues; i=i+1)
				{
					curTokenInFreq=frequencies[i][0];
					if(htACT.containsKey(curTokenInFreq))
					{
						freqInACT = (String)htACT.get(curTokenInFreq);
					}
					else
						freqInACT="0";
					frequencies[i][2]=freqInACT;
				}




				// Done populating frequencies with data on frequency of token
				// in greater corpus. Now calculate scores.
				System.out.println("Calculating scores...");
				double Score=0;
				double freqInResults=0;
				double freqInCorpus=0;
				for(int i=0; i<numUniqueValues; i++)
				{
					freqInResults=(double)Integer.parseInt(frequencies[i][1]);

					//	if((frequencies[i][2]).length()>0) // if not null -- is null in cases where the word is in the results set but not in the Medline corpus
					//	{
					//		freqInCorpus=(double)Integer.parseInt(frequencies[i][2]);
					//	}
					//	else freqInCorpus=0;

					freqInCorpus=(double)Integer.parseInt(frequencies[i][2]);
					if(freqInCorpus==0)
						//	frequencies[i][3]="infinite";
					{
						frequencies[i][3]="1";
						freqInCorpus=1;
					}
					// else
					//{
					//					Score=freqInResults/freqInCorpus;
					// Normalized score:

					double numLinesInMedlineCorpus=862339;
					double normalizedFreqInResults=freqInResults/lineCount;
					frequencies[i][5]=Double.toString(normalizedFreqInResults);
					double normalizedFreqInCorpus=freqInCorpus/numLinesInMedlineCorpus;
					frequencies[i][6]=Double.toString(normalizedFreqInCorpus);
					double logNormalizedFreqInResults=Math.log(normalizedFreqInResults);
					frequencies[i][7]=Double.toString(logNormalizedFreqInResults);
					double logNormalizedFreqInCorpus=Math.log(normalizedFreqInCorpus);
					frequencies[i][8]=Double.toString(logNormalizedFreqInCorpus);
					Score=logNormalizedFreqInCorpus/logNormalizedFreqInResults;
					frequencies[i][3]=Double.toString(Score);
				}
				// Done assigning scores
				System.out.println("Done calculating scores");
				// Now calculate thresholds for inclusion of tags
				double threshold=1.6;
				// Now run through frequencies and assign include/not include values
				// 0 = do not include; 1 = include
				String scoreToEval="";
				double decimalScore=0;
				int curTokFreq=0;
				for(int i=0; i<numUniqueValues; i++)
				{
					scoreToEval=frequencies[i][3];
					decimalScore=Double.parseDouble(scoreToEval);
					curTokFreq=Integer.parseInt(frequencies[i][1]);

					// score is a decimal value

					if(decimalScore>threshold)
						// Include
						frequencies[i][4]="1";
					else
						// Exclude
						frequencies[i][4]="0";

				}
				// For development purposes, optionally output the Frequencies table as a CSV file.
				int outputFrequencies=0; // Set to 1 if you want to output the frequencies.
				// Note: For some reason this code runs extremely slowly!
				if(outputFrequencies==1)
				{
					String buildFreq="";
					String tokenO="";
					String freqInResultsO="";
					String freqInGreaterCorpusO="";

					String normalizedFreqInResultsO="";
					String normalizedFreqInCorpusO="";
					String logNormalizedFreqInResultsO="";
					String logNormalizedFreqInCorpusO="";

					String scoreO="";
					String includeO="";
					BufferedWriter freqOut=new BufferedWriter(new FileWriter(File_Settings.frequenciesPath+fileName+".csv"));
					buildFreq=buildFreq+"token,freq_in_results,freq_in_corpus,normalized_freq_in_results,normalized_freq_in_corpus,log_normalized_freq_in_results,log_normalized_freq_in_corpus,score,include" + "\r\n"; // Row headings
					for(int i=0; i<numUniqueValues; i++)
					{
						tokenO=frequencies[i][0].replaceAll("\"","\"\"");
						freqInResultsO=frequencies[i][1];
						freqInGreaterCorpusO=frequencies[i][2];
						scoreO=frequencies[i][3];
						includeO=frequencies[i][4];
						normalizedFreqInResultsO=frequencies[i][5];
						normalizedFreqInCorpusO=frequencies[i][6];
						logNormalizedFreqInResultsO=frequencies[i][7];
						logNormalizedFreqInCorpusO=frequencies[i][8];
						buildFreq=buildFreq + "\"" + tokenO + "\"," + "\"" + freqInResultsO + "\"," + "\"" + freqInGreaterCorpusO + "\"," + "\"" + normalizedFreqInResultsO + "\"," + "\"" + normalizedFreqInCorpusO + "\"," + "\"" + logNormalizedFreqInResultsO + "\"," + "\"" + logNormalizedFreqInCorpusO + "\"," + "\"" + scoreO + "\"," + "\"" + includeO  + "\"" + "\r\n";
					}
					freqOut.write(buildFreq);
					freqOut.close();
				}
				/* Now output the data on which tags are to be included, to a text file.
				 * Note -- these data were previously placed directly into a hashmap, but when this code was subsequently
				 * encapsulated in an if statement, the later reference to the hashmap (during link assignment)
				 * did not recognize the hashmap.
				 */

				BufferedWriter outputTagInclusions=new BufferedWriter(new FileWriter(File_Settings.tagInclusionsPath+fileName));
				String tokenH="";
				String includeH="";
				for(int i=0; i<numUniqueValues; i++)
				{
					tokenH=frequencies[i][0];
					includeH=frequencies[i][4];
					if(tokenH.length()>0)
					{
						outputTagInclusions.write(tokenH + "|" + includeH);
						outputTagInclusions.newLine();
					}
				}
				outputTagInclusions.close();

				// This code moved down in April 2010
				/*
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
				 */
			} // end if includeTags==1	


			// Now go line by line and assign adjacencies.
			System.out.println("Assigning links between nodes...");

			// First, read in the tagInclusion data from the previously generated text file
			// and place the contents in a hashmap

			int lineCountInc=0;
			if(includeTags==1)
				// Count the number of lines in tag inclusions data
			{
				String countTags="";
				BufferedReader inTags = new BufferedReader(new FileReader(File_Settings.tagInclusionsPath+fileName));
				while((countTags=inTags.readLine()) != null)
				{
					lineCountInc++;
				}
			}

			int sizeOfHashmap=0;

			if(includeTags==1)
			{
				sizeOfHashmap=lineCountInc;
			}

			// Create a hashmap indicating whether each tag is to be included, for faster lookup during link assignment
			// If tags are not to be included, HashMap remains unpopulated and is not used.
			HashMap<String, String> h = new HashMap<String, String>(sizeOfHashmap);
			if(includeTags==1)
			{
				String strTagInc="";
				String tagForInclusion="";
				String toInclude="";
				BufferedReader inTags2 = new BufferedReader(new FileReader(File_Settings.tagInclusionsPath+fileName));

				while ((strTagInc = inTags2.readLine()) != null)
				{
					StringTokenizer t = new StringTokenizer(strTagInc,"|");
					while(t.hasMoreTokens())
					{
						tagForInclusion=t.nextToken();
						toInclude=t.nextToken();
						if(tagForInclusion.length()>0)
						{
							if(toInclude.length()>0)
								h.put(tagForInclusion, toInclude);
						}
					}
					lineCountInc++;
				}
				inTags2.close();
			}

			String thisLine="";
			int linesThisCitation=0;
			// Now count how many lines there are in the next record
			int citationCount=0;
			while ((str = in2.readLine()) != null)
			{
				if(str.equals(""))
					// Reached a blank line. Now process what's in the array for this record.
				{
					citationCount++;
					//					System.out.println("Starting to assign links for citation " + citationCount + " at time " + System.currentTimeMillis());
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
						entityLeft=allLinesThisCitation[i][1];
						if(includeAuthors==1)
						{
							if (allLinesThisCitation[i][0].equalsIgnoreCase("AU  "))
							{
								auCountLeft++;
								nodeTypeLeft="person"; // author
								String getVariants="";
								String normalized=Normalizer(allLinesThisCitation[i][1]);
								getVariants=variants.get(normalized);
								int numberOfSlashes = getVariants.replaceAll("[^/]","").length();
								if(numberOfSlashes<2)
									// If there is only one variant, use it (e.g. "Jones SA")
									// If there are exactly two variants, use them (e.g. "Jones S / Jones SA")
									// If there are three or more variants, retain the original author name datum (e.g. don't use "Jones S / Jones SA / Jones SK" as a node label)
								{
									entityLeft=getVariants;
									// Don't do this; this is a bad idea:
									// allLinesThisCitation[i][1]=getVariants;
								}
								else
									entityLeft=allLinesThisCitation[i][1];								
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
						if(includeTags==1)
						{
							if (allLinesThisCitation[i][0].equalsIgnoreCase("ABW ")) nodeTypeLeft="tag";
						}
						if(includeUmlsVariants==1)
						{
							if (allLinesThisCitation[i][0].equalsIgnoreCase("UMLS")) nodeTypeLeft="umls";
						}
						if(includeSubstances==1)
						{
							if (allLinesThisCitation[i][0].equalsIgnoreCase("RN  ")) nodeTypeLeft="substance";
						}
						if(includeMainHeadings==1)
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
							/*
							if(nodeTypeLeft.equalsIgnoreCase("abstract"))
							{
								Tokenized=Tokenizer(entityLeft,"inlineAbstractTokenize"); 
								allTokenized=allTokenized + "|" + Tokenized;
							}
							 */


							// Now determine node types for the right-hand nodes in the adjacency
							for(int j=i+1; j<(linesThisCitation); j++)
							{
								if(includeAuthors==1)
								{
									if (allLinesThisCitation[j][0].equalsIgnoreCase("AU  "))
									{
										auCountRight++;
										nodeTypeRight="person"; // author
										String getVariants="";
										String normalized=Normalizer(allLinesThisCitation[j][1]);
										getVariants=variants.get(normalized);
										int numberOfSlashes = getVariants.replaceAll("[^/]","").length();
										if(numberOfSlashes<2)
											// If there is only one variant, use it (e.g. "Jones SA")
											// If there are exactly two variants, use them (e.g. "Jones S / Jones SA")
											// If there are three or more variants, retain the original author name datum (e.g. don't use "Jones S / Jones SA / Jones SK" as a node label)
										{
											entityRight=getVariants;
											// Don't do this; this is a bad idea:
											// allLinesThisCitation[j][1]=getVariants;
										}
										else
											entityRight=allLinesThisCitation[j][1];		
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
								if(includeAbstractWords==1)
								{
									if (allLinesThisCitation[j][0].equalsIgnoreCase("ABW ")) nodeTypeRight="tag";
								}
								if(includeUmlsVariants==1)
								{
									if (allLinesThisCitation[j][0].equalsIgnoreCase("UMLS")) nodeTypeRight="umls";
								}								
								if(includeMainHeadings==1)
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
									if(nodeTypeLeft.equals("person"))
									{
										// do nothing; value of entityLeft is already assigned
									}
									else
										entityLeft=allLinesThisCitation[i][1];
									if(nodeTypeLeft.equals("paper")) // title
									{	
										entityLeft=entityLeft + " (" + yearPublished + ")"; // append year published to title
										// System.out.println(entityLeft);
									}
									if(nodeTypeRight.equals("person"))
									{
										// do nothing; value of entityRight is already assigned
									}
									else
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
									if(entityRight.trim().equalsIgnoreCase("et al"))
										exclude=true;
									if(entityLeft.trim().equalsIgnoreCase("et al"))
										exclude=true;
									if(entityRight.trim().equalsIgnoreCase("et al."))
										exclude=true;
									if(entityLeft.trim().equalsIgnoreCase("et al."))
										exclude=true;
									// Only link the first author to the institution
									if(nodeTypeLeft.equalsIgnoreCase("institution"))
										if(nodeTypeRight.equalsIgnoreCase("person"))
											if(auCountRight>1)
												exclude=true;

									if(nodeTypeLeft.equalsIgnoreCase("tag"))
									{
										//										String foundKey="";
										entityLeft = entityLeft.toLowerCase();
										entityLeft = entityLeft.replaceAll("\\Q(\\E","");
										entityLeft = entityLeft.replaceAll("\\Q)\\E","");
										entityLeft = entityLeft.replaceAll("\\Q/\\E","");
										entityLeft = entityLeft.replaceAll("\\Q,\\E","");
										entityLeft = entityLeft.replaceAll("\\Q&\\E","");
										entityLeft = entityLeft.replaceAll("\\Q%\\E","");
										entityLeft = entityLeft.replaceAll("\\Q;\\E","");
										entityLeft = entityLeft.replaceAll("\\Q.\\E","");
										entityLeft = entityLeft.replaceAll("\\Q:\\E","");
//										entityLeft = entityLeft.replaceAll("\\Q\"\\E","'");

										// Exclude tags having a relative frequency below the threshold
										if(h.containsKey(entityLeft.toString()))
										{
											//											foundKey=h.get("a");
											if(h.get(entityLeft.toString()).equals("0"))
											{
												// Exclude tag
												exclude=true;
											}
										}

									}

									// If entity is empty string, exclude
									if(entityLeft.equals(""))
										exclude=true;
									if(entityRight.equals(""))
										exclude=true;

									// Remove unwanted links based on linking schema
									// Default link excludes for entity type person
									if(nodeTypeLeft.equalsIgnoreCase("person"))
									{
										//										if(nodeTypeRight.equalsIgnoreCase("person"))
										//											exclude=true;
										// if(nodeTypeRight.equalsIgnoreCase("journal"))
										//	exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("grant"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("substance"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("mainheading"))
											exclude=true;
										//										if(nodeTypeRight.equalsIgnoreCase("tag"))
										//											exclude=true;
										//	if(nodeTypeLeft.equalsIgnoreCase("abstract"))
										//		exclude=true;
									}
									if(nodeTypeRight.equalsIgnoreCase("person"))
									{
										//										if(nodeTypeLeft.equalsIgnoreCase("person"))
										//											exclude=true;
										// if(nodeTypeLeft.equalsIgnoreCase("journal"))
										//	exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("grant"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("substance"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("mainheading"))
											exclude=true;
										//										if(nodeTypeLeft.equalsIgnoreCase("tag"))
										//											exclude=true;

										//	if(nodeTypeLeft.equalsIgnoreCase("abstract"))
										//		exclude=true;
									}
									// Default link excludes for entity type tag
									if(nodeTypeLeft.equalsIgnoreCase("tag"))
									{
										//										if(nodeTypeRight.equalsIgnoreCase("person"))
										//											exclude=true;
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
										if(nodeTypeRight.equalsIgnoreCase("tag"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("umls"))
											exclude=true;
										//										if(nodeTypeRight.equalsIgnoreCase("abstract"))
										//											exclude=true;
									}
									if(nodeTypeRight.equalsIgnoreCase("tag"))
									{
										//										if(nodeTypeLeft.equalsIgnoreCase("person"))
										//											exclude=true;
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
										if(nodeTypeLeft.equalsIgnoreCase("tag"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("umls"))
											exclude=true;
									}									
									// Default link excludes for entity type umls lexical variant
									if(nodeTypeLeft.equalsIgnoreCase("umls"))
									{
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
										if(nodeTypeRight.equalsIgnoreCase("tag"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("umls"))
											exclude=true;
									}
									if(nodeTypeRight.equalsIgnoreCase("umls"))
									{
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
										if(nodeTypeLeft.equalsIgnoreCase("tag"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("umls"))
											exclude=true;
									}
									// Default link excludes for paper							
									if(nodeTypeLeft.equalsIgnoreCase("paper"))
									{
										if(nodeTypeRight.equalsIgnoreCase("institution"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("tag"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("umls"))
											exclude=true;
									}
									if(nodeTypeRight.equalsIgnoreCase("paper"))
									{
										if(nodeTypeLeft.equalsIgnoreCase("institution"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("tag"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("umls"))
											exclude=true;
									}
									// Default link excludes for journal	
									if(nodeTypeLeft.equalsIgnoreCase("journal"))
									{
										// if(nodeTypeRight.equalsIgnoreCase("person"))
										//	exclude=true;
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
										if(nodeTypeRight.equalsIgnoreCase("tag"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("umls"))
											exclude=true;
									}
									if(nodeTypeRight.equalsIgnoreCase("journal"))
									{
										//	if(nodeTypeLeft.equalsIgnoreCase("person"))
										//	exclude=true;
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
										if(nodeTypeLeft.equalsIgnoreCase("tag"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("umls"))
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
										if(nodeTypeRight.equalsIgnoreCase("tag"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("umls"))
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
										if(nodeTypeLeft.equalsIgnoreCase("tag"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("umls"))
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
										if(nodeTypeRight.equalsIgnoreCase("tag"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("umls"))
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
										if(nodeTypeLeft.equalsIgnoreCase("abstract"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("tag"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("umls"))
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
										if(nodeTypeRight.equalsIgnoreCase("tag"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("umls"))
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
										if(nodeTypeLeft.equalsIgnoreCase("tag"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("umls"))
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
										if(nodeTypeRight.equalsIgnoreCase("tag"))
											exclude=true;
										if(nodeTypeRight.equalsIgnoreCase("umls"))
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
										if(nodeTypeLeft.equalsIgnoreCase("tag"))
											exclude=true;
										if(nodeTypeLeft.equalsIgnoreCase("umls"))
											exclude=true;
										//										if(nodeTypeLeft.equalsIgnoreCase("mainheading"))
										//											exclude=true;
									}
									if(exclude==false) // Output this line only if the exclude flag hasn't been activated
									{
										if(simpleAdjacency==0)
											buildLine=(nodeTypeLeft + "-" + nodeTypeRight + " \"" + nodeTypeLeft + "_" + entityLeft.replaceAll("\\Q\"\\E","'") + "\" \"" + nodeTypeRight + "_"  + entityRight.replaceAll("\\Q\"\\E","'") +"\" 1");
										if(simpleAdjacency==1)
											buildLine=(entityLeft + "|" + entityRight);
										System.out.println(buildLine);
										output.write(buildLine);
										output.newLine();
									}
									nodeTypeRight=""; // Reset nodeTypeRight to null string
									exclude=false; // Reset exclude flag to false
								} // end if for right side (if entitytype is not null; if right-hand matches desired entity type)
							} // end for loop for right side
						} // end if for left side
						auCountRight=0; // Reset counter for author count in inner loop (remainder of lines for this citation)
						nodeTypeLeft=""; // Reset nodeTypeLeft to null string
						nodeTypeRight=""; // Reset nodeTypeLeft to null string
						entityLeft="";
						entityRight="";	
						exclude=false;
					} // end for loop to process the line of the citation
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

			//						System.out.println("allTokenized: " + allTokenized);
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
	private static String Tokenizer(String S, String preProc)  {
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
//			nextToken = nextToken.replaceAll("\\Q\"\\E","'");
			//            outString=outString + "|" + nextToken;
			if(preProc.equals("abstractWord"))
				outString=outString + "ABW - " + nextToken + "\r\n"; // Abstract word
			else if(preProc.equals("titleWord"))
				outString=outString + "TIW - " + nextToken + "\r\n"; // Title word
			else
				outString=outString + "\r\n" + nextToken;
		}
		return outString;
	}

	private static String IdentifyVariants(String S, Hashtable umlsVariants)
	{
		// Input is a tokenized abstract
		String outString="";
		String toEvaluate="";
		String[] allTokens=S.split("\r\nABW - ");
		int currentWord=0;
		int lastWord=allTokens.length;
		int window=5;
		for(int i=0;i<lastWord-window;i++)
		{
			// Compose the string you want to look up in the hash table of UMLS variants
			// Decremental lookup algorithm by Stephen B. Johnson
			// Get everything from UMLS into hashtable. Take the abstract.
			// Start at the first word and it�s a window of five.
			// Check hashtable and see if it�s there.
			// If it isn�t, see if the first four are there.
			// Suppose they are; save this and then go to the next word
			// and check for five; so you have this moving window.
			for(int j=window; j>0; j--)
			{
				for(int k=i; k<i+j; k++)
				{
					toEvaluate=toEvaluate+allTokens[k]+" ";
				}
				toEvaluate=toEvaluate.trim();
				if(umlsVariants.containsKey(toEvaluate))
				{
					outString=outString+"UMLS- " + toEvaluate + "\r\n"; // UMLS lexical variant
				}
				toEvaluate="";
			}
		}	
		return outString;
	}

	private static String Normalizer(String S) {
		String outString="";
		String nextToken="";
		String debug="";
		StringTokenizer t=new StringTokenizer(S, " ");
		int numTokens=t.countTokens();
		int tokenCount=1;
		while(t.hasMoreTokens())
		{
			if(tokenCount==numTokens)
				// on the last token
			{
				debug=t.nextToken();
				nextToken=(debug.substring(0,1)); // Only take the first character
			}
			else
				nextToken=t.nextToken();
			tokenCount++;
			outString=outString + nextToken + " ";
		}
		outString=outString.substring(0,outString.length()-1); // Take off last space
		return outString;
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
} // end class

