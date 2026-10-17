package bales;
import org.apache.logging.log4j.Logger; 
import org.apache.logging.log4j.LogManager;

import java.io.PrintWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
public class controll3rW
{
	private static final Logger logger = LogManager.getLogger(controll3rW.class.getName());
	public controll3rW(String query, PrintWriter out, String output_path) throws IOException
	{
		//Let's do a sanity check on the directory
		File dir = new File(output_path);
		if (dir == null)
		{
			logger.fatal("Directory is null: " + output_path);
			//TODO: We should throw an exception here, but not a generic exception
		}
		else
		{
			if (!dir.exists())				
			{
				logger.debug("Creating directory");
				dir.mkdir();
			}	
			if (!dir.exists() || !dir.isDirectory() || !dir.canWrite())
			{
				logger.fatal("Problem with directory: " + dir.getAbsolutePath());
				//TODO: We should throw an exception here, but not a generic exception
			}
			else //We should be good to go..
			{
				output_path = dir.getAbsolutePath();
				logger.info("Query I received was: " + query);
				logger.info("My root path is " + output_path);
				out.println("Normally I would run the query for: " + query);
				out.println("Normally I would use " + output_path + " as the place I would store my temp and output files");
				File test_file = new File(output_path+"/test.txt");
				PrintWriter file_out = new PrintWriter(new FileOutputStream(test_file, true));
				file_out.print(query);
				file_out.close();		
				logger.info("Done performing query");
			} // Checking directory
		} //dir is null
	} // constructor
} //class
