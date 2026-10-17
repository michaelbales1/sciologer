package bales;

public class Job
{
	private JobStatus status;
	private String query;
	private String seed;
	private String includeLinks;
	private String includePapers;
	private String includeAuthors;
	private String includeJournals;
	private String includeInstitutions;
	private String includeGrants;
	private String includeMainHeadings;
	private String includeSubstances;
	private String includeAbstracts;
	private String includeTags;
	private String includeUmlsVariants;
	private String fcFldr=File_Settings.fullCitationsPath; // Default location of full citations folder
	private String adjFldr=File_Settings.adjacenciesPath; // Default location of adjacency lists folder
	private String outFileName;
	
	public Job(String query, String includeLinks, String includePapers, String includeAuthors, String includeJournals, String includeInstitutions, String includeGrants, String includeMainHeadings, String includeSubstances, String includeAbstracts, String includeTags, String includeUmlsVariants)
	{
		this.query = query;
		this.includeLinks = includeLinks;
		this.includePapers = includePapers;
		this.includeAuthors = includeAuthors;
		this.includeJournals = includeJournals;
		this.includeInstitutions = includeInstitutions;
		this.includeGrants = includeGrants;
		this.includeMainHeadings = includeMainHeadings;
		this.includeSubstances = includeSubstances;
		this.includeAbstracts = includeAbstracts;
		this.includeTags = includeTags;
		this.includeUmlsVariants = includeUmlsVariants;
		status = new JobStatus();
		seed = Integer.toString((int)(1000+Math.random()*1000));

//		this.outFileName = query.replaceAll("\"","'").replaceAll(":","-").replaceAll("\\*","-WC-") + seed;
		if (query.length()>100)
		this.outFileName = (query.replaceAll("\"","'").replaceAll(":","-").replaceAll("\\*","-WC-")).substring(0,100) + seed;
		else
			this.outFileName = (query.replaceAll("\"","'").replaceAll(":","-").replaceAll("\\*","-WC-")) + seed;
	}

	public String getKMLFileName()
	{
		return outFileName + ".kml";
	}

	public String getKMZFileName()
	{
		return outFileName + ".kmz";
	}

	
	public JobStatus getStatus()
	{
		return status;
	}
	
	public String getQuery()
	{
		return query;
	}
	
	public String getSeed()
	{
		return seed;
	}
	public String getIncludeLinks()
	{
		return includeLinks;
	}
	public String getIncludePapers()
	{
		return includePapers;
	}
	public String getIncludeAuthors()
	{
		return includeAuthors;
	}
	public String getIncludeJournals()
	{
		return includeJournals;
	}
	public String getIncludeInstitutions()
	{
		return includeInstitutions;
	}
	public String getIncludeGrants()
	{
		return includeGrants;
	}
	public String getIncludeMainHeadings()
	{
		return includeMainHeadings;
	}
	public String getIncludeSubstances()
	{
		return includeSubstances;
	}
	public String getIncludeAbstracts()
	{
		return includeAbstracts;
	}
	public String getIncludeTags()
	{
		return includeTags;
	}
	public String getIncludeUmlsVariants()
	{
		return includeUmlsVariants;
	}
	public void setfcFldr(String fcFldr)
	{
		this.fcFldr = fcFldr;
	}

	public void setadjFldr(String adjFldr)
	{
		this.adjFldr = adjFldr;
	}

	public String getfcFldr()
	{
		return fcFldr;
	}

	public String getadjFldr()
	{
		return adjFldr;
	}

}
