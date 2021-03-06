package bales;

import javax.servlet.http.HttpServlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.OutputStream;
import java.io.IOException;
import java.io.FileNotFoundException;

public class FileServer extends HttpServlet
{
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		try
		{
			String filename = request.getParameter("filename");
			OutputStream out = response.getOutputStream();
			
			//System.out.println(filename);
			//System.out.println(filename.indexOf("/")+"");
			
			//Check to make sure they are only passing file names and not paths
			if (filename != null && !filename.equals("") && filename.indexOf("/") < 0) 
			{	
				try
				{
					String filepath = File_Settings.outputPath;
		
					java.io.FileInputStream fileInputStream = new java.io.FileInputStream(filepath + filename);

					//Send the data back as KML
					response.setContentType("application/octet-stream");
					response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + '"');

					int i;
					while ((i=fileInputStream.read()) != -1) {
						out.write(i);
					}
		
					fileInputStream.close();
					out.close();	
				}
				catch (FileNotFoundException fnfe)
				{
					PrintWriter p = new PrintWriter(out);
					p.println("File not found");
					p.close();					
				}
			}
			else
			{
				PrintWriter p = new PrintWriter(out);
				p.println(""); // Temporarily removed for March 2009 demo -- MB
				//				p.println("Invalid file");
				p.close();
			}
		}	
		catch (IOException ioe)
		{
			throw new ServletException("Problem fetching file");
		}
	}
}