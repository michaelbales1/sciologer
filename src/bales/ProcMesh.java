package bales;
//=======================================================================================
//ProcMesh.java reads in a file consisting of indexing terms assigned to journal articles.
//In the input file there is one line per article and the indexing terms are delimited by vertical bars.
//Sample input format:
//486;49320;7863;27800
//233;49322;486
//119
//Terms consisting of multiple words are hyphenated.
//The program outputs a pipe-delimited association list such that every indexing term is paired with
//every other indexing term that appears on the same line.
//Makes a pipe-delimited list for output of MeSH network terms to Txt2Pajek format.
//import java.util.*;
//=======================================================================================
import java.io.*;
import java.util.StringTokenizer;
//import java.util.ArrayList;
//import org.xml.sax.helpers.DefaultHandler;

//public class ProcMesh extends DefaultHandler {
public class ProcMesh  {
//	static private Writer origOut;
	public static void main(String args[]) {
		if (args.length != 2) {
			System.err.println("Usage: cmd infile outfile");
			//System.exit(1);
		}
		int n=0;
		int lineCount=0;
		try {
			BufferedReader in = new BufferedReader(new FileReader(args[0]));
			BufferedReader in2 = new BufferedReader(new FileReader(args[0]));
			File file=new File(args[1]); // declaration of output filename
			BufferedWriter output=new BufferedWriter(new FileWriter(file));
			String str;
			// First count the number of lines in the file
			while ((str = in.readLine()) != null)
			{
				lineCount++;
			}
			in.close();
					
			while ((str = in2.readLine()) != null) {
				{
					int tokenCount=0;
					// Count the number of tokens in the line
					StringTokenizer tokenizer = new StringTokenizer(str, "|");
					while (tokenizer.hasMoreTokens())
					{
						tokenCount++;
						tokenizer.nextToken();
					} // end while
					
					String[] ThisLine=new String[tokenCount];
					n=0;
					StringTokenizer tokenizer2 = new StringTokenizer(str, "|");
					while (tokenizer2.hasMoreTokens())
					{
						String MeshIn = tokenizer2.nextToken();
						ThisLine[n]=MeshIn;
						n++;
					} // end while
					for(int i=0; i<n; i++) // for each position in the array
					for(int j=0; j<n; j++)
					{
						if(i==j) break; // exclude loops (links from a keyword to itself)
/*
 * Original output format for ProcMesh
						System.out.println(ThisLine[i].trim()+"|"+ThisLine[j].trim());
						output.write("net" + ThisLine[i].trim()+"|"+ThisLine[j].trim());
*/

						// New output for CCVisu, for RSF net format
						System.out.println("net " + "\"" + ThisLine[j].trim()+"\"" + " "+ "\"" + ThisLine[i].trim()+"\"" + " 1");
						output.write("net " + "\"" + ThisLine[j].trim()+"\"" + " "+ "\"" + ThisLine[i].trim()+"\"" + " 1");
						
						output.newLine();
					}
//					} // end for
//					} // end for
				} // end while
			} // end while
			in2.close();
			output.close();
		} // end try
		catch (IOException e) {
		} // end catch
	} // end main
} // end class
