package bales;

import java.io.*;
import java.util.StringTokenizer;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.logging.log4j.Logger; 
import org.apache.logging.log4j.LogManager;


/********************************************************* 
 * CVResults is a servlet that reads the user's query from the
 * form data, and runs static main methods of various routines
 * in sequence, writing output to disk, based on the parameters
 * specified on the simple or the advanced search screen.
 ********************************************************/

public class CVResultsGE extends HttpServlet 
{
	private static final Logger logger = LogManager.getLogger(CVResultsGE.class.getName());
	private static final String QUERY_COUNT_NAME="QueryCount";
	public static final String SESSION_JOB_NAME="JOB";

	public void init()
	{
//		BasicConfigurator.configure();
//		logger.debug("Log configured");	
	}
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException 
	{
		String includeLinks="true"; // Will be turned to false if user has unchecked the box on the form
		String includePapers="true";
		String includeAuthors="true";
		String includeJournals="true";
		String includeInstitutions="true";
		String includeGrants="true";
		String includeMainHeadings="true";
		String includeSubstances="true";
		String includeAbstracts="true";
		String includeTags="true";
		String includeUmlsVariants="true";
		response.setContentType("text/html");
		PrintWriter out = response.getWriter();

		//Start session
		HttpSession session = request.getSession();
		Integer i = (Integer)session.getAttribute(QUERY_COUNT_NAME);
		if (i == null)
		{
			logger.info("New user. Creating session");
			session.setAttribute(QUERY_COUNT_NAME, new Integer(0));
		}
		else
		{
			int prev_count = i.intValue();
//			logger.info("User has been here " + prev_count + " previous times.");
			session.setAttribute(QUERY_COUNT_NAME, new Integer(prev_count+1));
		}

		// Get the user's query
		String userQuery=request.getParameter("formQuery");

		if (userQuery == null || userQuery.equals("null"))
		{
			out.println("Cannot query for null.");
		}
		else
		{

			if(request.getParameter("includeLinks")==null)
			{
//				System.out.println("CVResults reports that includeLinks is null.");
				includeLinks="false";
			}
			else
			{
				includeLinks=request.getParameter("includeLinks");
//				System.out.println("CVResults reports that includeLinks is " + includeLinks);
			}
			if(request.getParameter("includePapers")==null)
			{
				includePapers="false";
			}
			else
			{
				includePapers=request.getParameter("includePapers");
			}
			if(request.getParameter("includeAuthors")==null)
			{
				includeAuthors="false";
			}
			else
			{
				includeAuthors=request.getParameter("includeAuthors");
			}
			if(request.getParameter("includeJournals")==null)
			{
				includeJournals="false";
			}
			else
			{
				includeJournals=request.getParameter("includeJournals");
			}
			if(request.getParameter("includeInstitutions")==null)
			{
				includeInstitutions="false";
			}
			else
			{
				includeInstitutions=request.getParameter("includeInstitutions");
			}
			if(request.getParameter("includeGrants")==null)
			{
				includeGrants="false";
			}
			else
			{
				includeGrants=request.getParameter("includeGrants");
			}
			if(request.getParameter("includeMainHeadings")==null)
			{
				includeMainHeadings="false";
			}
			else
			{
				includeMainHeadings=request.getParameter("includeMainHeadings");
			}
			if(request.getParameter("includeSubstances")==null)
			{
				includeSubstances="false";
			}
			else
			{
				includeSubstances=request.getParameter("includeSubstances");
			}
			if(request.getParameter("includeAbstracts")==null)
			{
				includeAbstracts="false";
			}
			else
			{
				includeAbstracts=request.getParameter("includeAbstracts");
			}
			if(request.getParameter("includeTags")==null)
			{
				includeTags="false";
			}
			else
			{
				includeTags=request.getParameter("includeTags");
			}
			if(request.getParameter("includeUmlsVariants")==null)
			{
				includeUmlsVariants="false";
			}
			else
			{
				includeUmlsVariants=request.getParameter("includeUmlsVariants");
			}
			String docType =
				"<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.0 " +
				"Transitional//EN\">\n";

			// TODO: Try query via e-utils to determine how many results are returned

			// Now build parameters for controll3r
			String bcp="";
			int bcount=0;
			// Add the query to the parameters string
			bcp=bcp+"-q "; bcount++;
			bcp=bcp+userQuery.replaceAll(" ","_")+" "; bcount++;

			if(includeLinks.equals("true"))
			{
				// System.out.println("CVResults adds -includeLinks parameter to CParams.");
				bcp=bcp+"-includeLinks ";
				bcount++;
			}
			if(includePapers.equals("true"))
			{
				bcp=bcp+"-includePapers ";
				bcount++;
			}
			if(includeAuthors.equals("true"))
			{
				bcp=bcp+"-includeAuthors ";
				bcount++;
			}
			if(includeJournals.equals("true"))
			{
				bcp=bcp+"-includeJournals ";
				bcount++;
			}
			if(includeInstitutions.equals("true"))
			{
				bcp=bcp+"-includeInstitutions ";
				bcount++;
			}
			if(includeGrants.equals("true"))
			{
				bcp=bcp+"-includeGrants ";
				bcount++;
			}
			if(includeMainHeadings.equals("true"))
			{
				bcp=bcp+"-includeMainHeadings ";
				bcount++;
			}
			if(includeSubstances.equals("true"))
			{
				bcp=bcp+"-includeSubstances ";
				bcount++;
			}
			if(includeAbstracts.equals("true"))
			{
				bcp=bcp+"-includeAbstracts ";
				bcount++;
			}
			if(includeTags.equals("true"))
			{
				bcp=bcp+"-includeTags ";
				bcount++;
			}
			if(includeUmlsVariants.equals("true"))
			{
				bcp=bcp+"-includeUmlsVariants ";
				bcount++;
			}
			// System.out.println("bcp="+ bcp);
			// Now tokenize controll3r parameters into a String array
			String[] CParams=new String[bcount];
			int CPPos=0;
			StringTokenizer t= new StringTokenizer(bcp," ");
			while(t.hasMoreTokens())
			{
				CParams[CPPos]=t.nextToken().replaceAll("_"," ");
//				System.out.println("CParams=" + CParams[CPPos]);
				CPPos++;
			}

			Job job = new Job(userQuery, includeLinks, includePapers, includeAuthors, includeJournals, includeInstitutions, includeGrants, includeMainHeadings, includeSubstances, includeAbstracts, includeTags, includeUmlsVariants);
			session.setAttribute(SESSION_JOB_NAME, job);

			// Now run controll3r's main class with this query
			//			controll3r myController = new controll3r(userQuery.replaceAll(" ","_"), out, "", null, null, seed);
			//controll3r myController = new controll3r(userQuery, out, "", null, null, seed);
			controll3r myController = new controll3r(job);

			// Report that query was successful
			String title = "Query successful";
			String filename = job.getKMZFileName();
			String explanation = "Your file has been written to " + File_Settings.outputPath + filename;
			// out.println(explanation);
			// IMPORTANT: THE FOLLOWING LINE IS NEEDED IF YOU WANT THE BROWSER TO PRESENT THE FILE TO THE USER
			// out.println("<META http-equiv='refresh' content='0;URL=getFile?filename="+ filename +"'/>");

			out.println(File_Settings.outputPath + filename);
		}// null check
	} //doGet
}// class
