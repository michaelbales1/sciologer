package bales;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.PrintWriter;

/**
 * Servlet implementation class GetStatus
 */
public class GetStatus extends HttpServlet {
	private static final long serialVersionUID = 1L;
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public GetStatus() {
        super();
        // TODO Auto-generated constructor stub
    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doPost(request, response);
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		HttpSession session = request.getSession();
		PrintWriter out = response.getWriter();
		
		if (session == null || session.getAttribute(CVResults.SESSION_JOB_NAME) == null)
		{
//			out.println("I'm sorry, we weren't able to find a record of your query!");
			out.println("...");
		}
		else
		{
			Job job = (Job)session.getAttribute(CVResults.SESSION_JOB_NAME);
			// THE FOLLOWING LINE COMMENTED OUT IN FEBRUARY 2010
			//			out.println("Status for query: '" + job.getQuery() + "':\n");
			out.println(job.getStatus().toString());
			// THE FOLLOWING LINE COMMENTED OUT IN FEBRUARY 2010
			//			out.println("Rand: " + (Math.random() * 1000));
			out.println("");
		}
	}
}
