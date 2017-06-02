package bales;
//gennum.java outputs a lattice of integers.
//Two integers are linked if one is a multiple of the other
//Use the following output filter (see also below in code)
//for output to .rsf format, which can then be input into CCVisu.java:
//System.out.println("Lattice-"+max+" "+n+" "+i+" 1");
import java.net.URLEncoder;
import java.net.URL;
import java.net.URLConnection;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.BufferedReader;
import java.util.StringTokenizer;

//import org.apache.log4j.Logger;
import org.apache.logging.log4j.Logger; 
import org.apache.logging.log4j.LogManager;

import bales.File_Settings;

public class getPubs 
{
	private static final Logger logger = LogManager.getLogger(getPubs.class.getName());
	// End of line varies from one operating system to the next
	// endl is appropriate line separator for user's system
	public final static String endl = System.getProperty("line.separator");
	public static void main (String[] args)
	{
		// SET MAXIMUM CITATIONS HERE
		String dispmax="12500";
//		logger.info("Sciologer is querying Medline...");
//		System.out.println("Sciologer is querying Medline...");		
		if (args.length == 0) {
			printHelp();
			//System.exit(0);
		}		
		String myQuery=""; // Empty string for query
		String seed=""; // Empty string for seed
		// Parse command-line options.
		for (int i = 0; i < args.length; ++i)
		{
			if (args[i].equalsIgnoreCase("-query"))
			{
				i++;
				chkAvail(args, i);
				myQuery = args[i];
			}
			if (args[i].equalsIgnoreCase("-seed"))
			{
				i++;
				chkAvail(args, i);
				seed = args[i];
			}
		} // Done parsing command-line options

		try {
			// NOTE: After modifying the following line, update CVResults.java to maintain consistency of replaceAll replacements.
//			String fileName=(File_Settings.fullCitationsPath + myQuery.replaceAll("\"","'").replaceAll(":","-").replaceAll("\\*","-WC-") + ".txt"); // update CVResults for consistency
//			String fileName=(File_Settings.fullCitationsPath + myQuery.replaceAll("\"","'").replaceAll(":","-").replaceAll("\\*","-WC-") + ""); // update CVResults for consistency
			String fileName=(myQuery.replaceAll("\"","'").replaceAll(":","-").replaceAll("\\*","-WC-") + ""); // update CVResults for consistency
			if(fileName.length()>100) // If length is greater than 100 characters; maximum file name length is 255 when .txt and .kml extensions are included
//			fileName=(fileName.substring(0,100)+".txt");
				fileName=(fileName.substring(0,100));
			fileName=fileName+seed;
			String fileNameinFC=File_Settings.fullCitationsPath + fileName;
//				fileName=(fileName.substring(0,100).replaceAll(" ", "%20").replaceAll("\"","").replaceAll("'","")+"");
			File file=new File(fileNameinFC); // declaration of output filename
			BufferedWriter output=new BufferedWriter(new FileWriter(file));
			// Construct data for the Entrez etools program esearch
			String data = URLEncoder.encode("db", "UTF-8") + "=" + URLEncoder.encode("pubmed", "UTF-8");
			// INSERT QUERY TERMS ON THE NEXT LINE
			data += "&" + URLEncoder.encode("term", "UTF-8") + "=" + URLEncoder.encode(myQuery, "UTF-8");
//			data += "&" + URLEncoder.encode("term", "UTF-8") + "=" + URLEncoder.encode("\"scutchfield d\"", "UTF-8");
			data += "&" + URLEncoder.encode("retmax", "UTF-8") + "=" + URLEncoder.encode("10", "UTF-8");
			data += "&" + URLEncoder.encode("usehistory", "UTF-8") + "=" + URLEncoder.encode("y", "UTF-8");
			data += "&" + URLEncoder.encode("tool", "UTF-8") + "=" + URLEncoder.encode("Michael_Bales_Sciologer", "UTF-8");
			data += "&" + URLEncoder.encode("email", "UTF-8") + "=" + URLEncoder.encode("meb2108@columbia.edu", "UTF-8");
			// "Send E-utilities requests to https://eutils.ncbi.nlm.nih.gov, not the standard NCBI Web address."
			// Send data:
			URL url = new URL("https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi?");
			URLConnection conn = url.openConnection();
			conn.setDoOutput(true);
			OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream());
			wr.write(data);
			wr.flush();
			// Get the response
			BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			String line;
			String myQueryKeyToEOL="";
			String myQueryKey="";
			String myWebEnvToEOL="";
			String myWebEnv="";
			String currentToken="";
			
			
			for(int i=0; i<3; i++)
				// Read in the lines returned by the esearch command.
				// The elements of the response that is of interest here are:
				// Query Key and WebEnv.
			{
				line=rd.readLine();
//				System.out.println(line);

				if(line.contains("QueryKey"))
				{
//					System.out.println("Line contains QueryKey");
					StringTokenizer t=new StringTokenizer(line,">");
					while(t.hasMoreTokens())
					{
						currentToken=t.nextToken();
//						System.out.println(currentToken);
						if(currentToken.contains("/QueryKey"))
						{
							myQueryKey=currentToken.replaceAll("</QueryKey","");
//							 System.out.println("QueryKey is " + myQueryKey);
						}
						if(currentToken.contains("/WebEnv"))
						{
							myWebEnv=currentToken.replaceAll("</WebEnv","");
//							 System.out.println("WebEnv is " + myWebEnv);
						}
					}
				}

				/*
				if(line.length()>9)
					if(line.substring(2,10).equals("QueryKey"))
					{
						// System.out.println(line);
						// System.out.println(line.substring(2,10));
						myQueryKeyToEOL=line.substring(11);
					    System.out.println("QUERY KEY TO END OF LINE IS " + myQueryKeyToEOL);
						StringTokenizer tokenizer = new StringTokenizer(myQueryKeyToEOL, "<");
						myQueryKey=tokenizer.nextToken();
						System.out.println("QueryKey is " + myQueryKey);
					}

				if(line.length()>7)
					if(line.substring(2,8).equals("WebEnv"))
					{
						// System.out.println(line);
						// System.out.println(line.substring(2,8));
						myWebEnvToEOL=line.substring(9);
						System.out.println("WEBENV TO END OF LINE IS " + myWebEnvToEOL);
						StringTokenizer tokenizer2 = new StringTokenizer(myWebEnvToEOL, "<");
						myWebEnv=tokenizer2.nextToken();
						System.out.println("WebEnv IS " + myWebEnv);
					}
				 */	
			}
			
			
			
/*
			for(int i=0; i<10; i++)
				// Read in the lines returned by the esearch command.
				// The elements of the response that is of interest here are:
				// Query Key and WebEnv.
			{
				line=rd.readLine();
				if(line.length()>9)
					if(line.substring(2,10).equals("QueryKey"))
					{
						// System.out.println(line);
						// System.out.println(line.substring(2,10));
						myQueryKeyToEOL=line.substring(11);
						// System.out.println("QUERY KEY TO END OF LINE IS " + myQueryKeyToEOL);
						StringTokenizer tokenizer = new StringTokenizer(myQueryKeyToEOL, "<");
						myQueryKey=tokenizer.nextToken();
						// System.out.println("QueryKey is " + myQueryKey);
					}
				if(line.length()>7)
					if(line.substring(2,8).equals("WebEnv"))
					{
						// System.out.println(line);
						// System.out.println(line.substring(2,8));
						myWebEnvToEOL=line.substring(9);
						// System.out.println("WEBENV TO END OF LINE IS " + myWebEnvToEOL);
						StringTokenizer tokenizer2 = new StringTokenizer(myWebEnvToEOL, "<");
						myWebEnv=tokenizer2.nextToken();
						// System.out.println("WebEnv IS " + myWebEnv);
					}	
			}
			*/
			wr.close();
			rd.close();
//			logger.debug("Submitted query key to PubMed via Entrez eUtilities...");
			// Construct data for the Entrez etools program efetch
			String data2 = URLEncoder.encode("db", "UTF-8") + "=" + URLEncoder.encode("pubmed", "UTF-8");
			data2 += "&" + URLEncoder.encode("webenv", "UTF-8") + "=" + URLEncoder.encode(myWebEnv, "UTF-8");
			data2 += "&" + URLEncoder.encode("query_key", "UTF-8") + "=" + URLEncoder.encode(myQueryKey, "UTF-8");
			data2 += "&" + URLEncoder.encode("retmode", "UTF-8") + "=" + URLEncoder.encode("txt", "UTF-8");
			data2 += "&" + URLEncoder.encode("rettype", "UTF-8") + "=" + URLEncoder.encode("medline", "UTF-8");
			data2 += "&" + URLEncoder.encode("dispmax", "UTF-8") + "=" + URLEncoder.encode(dispmax, "UTF-8");
			data2 += "&" + URLEncoder.encode("tool", "UTF-8") + "=" + URLEncoder.encode("MichaelBalesDevelopingVisualExploratoryPubMedInterface", "UTF-8");
			data2 += "&" + URLEncoder.encode("email", "UTF-8") + "=" + URLEncoder.encode("meb2108@columbia.edu", "UTF-8");
//			logger.debug(data2);

			// Send data
//			URL url2 = new URL("https://www.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi?");
			// From http://www.ncbi.nlm.nih.gov/entrez/query/static/eutils_help.html:
			// "Send E-utilities requests to http://eutils.ncbi.nlm.nih.gov, not the standard NCBI Web address."
			URL url2 = new URL("https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi?");
			URLConnection conn2 = url2.openConnection();
			conn2.setDoOutput(true);
			OutputStreamWriter wr2 = new OutputStreamWriter(conn2.getOutputStream());
			wr2.write(data2);
			wr2.flush();
			logger.info("Downloading citations from PubMed (maximum " + dispmax +" citations)...");
			// System.out.println("Downloading citations from PubMed (maximum " + dispmax +" citations)...");
			// Get the response
			BufferedReader rd2 = new BufferedReader(new InputStreamReader(conn2.getInputStream()));
			String initialChar="";
			while ((line = rd2.readLine()) != null)
			{
				if(line.compareTo("")>0) // As long as the line isn't blank
				{
					initialChar=line.substring(0,1);
					if(initialChar.compareTo("<")==0) // If the line contains markup language tags 
						line=rd2.readLine(); // Skip the line
					else
					{
						// System.out.println(line);
						output.write(line);
						output.newLine();
					}
				}
				else // If the line is blank, just print it
				{
					// System.out.println(line);
					output.write(line);
					output.newLine();
				}
			}
			wr2.close();
			rd2.close();
			output.close();
			logger.info("Finished downloading PubMed citations.");
			// System.out.println("Finished downloading PubMed citations.");
			/*
			 * NOTE -- this code was used before when running from getPubs.
			 * Now all running is done from controll3r.
			// Now run controll3r on the file, in single-file mode
			String[] conParams=new String[4];
			conParams[0]="-fc";
			conParams[1]="c:\\e\\fc\\";
			conParams[2]="-a";
			conParams[3]="c:\\e\\a\\";
			controll3r.main(conParams);
			 */
		}
		catch (Exception e)
		{
		}
	} // end main
	private static void printHelp() 
	{
		// Usage and info message.
		System.out.print( 
				endl
				+ "This is getPubs, a tool to get publications from Medline via Entrez e-utilities." + endl
				+ "Required parameters: -query query seed" + endl
				+ "where seed is a random number."
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
}