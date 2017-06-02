package bales;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.File;

import org.apache.logging.log4j.Logger; 
import org.apache.logging.log4j.LogManager;

public class CVResultsW extends HttpServlet
{
	private static final Logger logger = LogManager.getLogger(CVResultsW.class.getName());
	static String root_path;
	
	/* For stand-alone command-line execution
	 */
	public static void main(String[] args)
	{		
//		BasicConfigurator.configure();
		logger.debug("Log configured");
		
		//Default query, do we need to replace it?
		String query = "Duracell";
		if (args.length > 0 && args[0] != null)
			query = args[0];

		//Default directory, do we need to replace it?
		String output_directory = "output";
		if (args.length >1 && args[1] != null)
			output_directory = args[1];
		
		PrintWriter out = new PrintWriter(System.out);
		
		try
		{
			controll3rW my_controll3r = new controll3rW(query, out, output_directory);
		}
		catch (IOException ioe)
		{
			logger.fatal(ioe.getMessage());
		}
		out.close();
	}	

	/*** SERVLET CODE BEGINS HERE *********/

	/*init() is called when the servlet is loaded
	 * Set up the log and find where we are!
	 */
	public void init() throws ServletException
	{
		root_path = getServletContext().getRealPath("/");
		System.out.println("Servlet initialized.  Root is: " + root_path);
		
		String log_config_path = root_path + "/WEB-INF/log4j.properties";
		System.out.println("Trying to intialize log at " + log_config_path);
		
		try
		{
//			PropertyConfigurator.configure(log_config_path);
			logger.debug("Log successfully initialized");
		}
		catch (Exception e)
		{
			System.err.println("Unable to initialize log with config file, failing to basic configuration");
//			BasicConfigurator.configure();
		}
	}
	
	/* Handle GET and POST requests to the servlet exactly the same...
	 */
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		doPost(request, response);
	}
	
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		String query = request.getParameter("query");
		if (query == null || query.equals(""))
			query = "Duracell";
		logger.info("Query was: " + query);

		response.setContentType("text/plain");
		PrintWriter out = response.getWriter();
		String output_directory = root_path+"output";
		logger.debug("Output directory is: " + output_directory);
		
		controll3rW controller_for_this_thread = new controll3rW(query, out, output_directory);
		out.close(); //Don't forget this or we lose output!
	}


}
