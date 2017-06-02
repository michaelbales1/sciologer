package bales;

import java.util.ArrayList;
import java.util.Iterator;

public class JobStatus
{
	private ArrayList<String> messages;

	public JobStatus()
	{
		messages = new ArrayList();
	}
	
	public void println(String msg)
	{
		messages.add(msg);
	}
	
	public String toString()
	{
		String out = "";
		Iterator i = messages.iterator();
		while (i.hasNext())
		{
			out = out.concat((String)i.next()+"\n");
		}
		return out;
	}
}
