/* 
Settings for file paths for CiteVis project 
 */
package bales;
public class File_Settings { 
	// Note that upon implementation it is necessary to put the file umls_with_lexical_variants_all.txt
	// into the e u folder
	//Windows by default
	public static String _bP="c:\\"; // change when locating to new system
	//	public final static String _bP="t:\\"; // change when locating to new system
	//	public final static String _eP="t:\\"; // path to external drive
	public static String kmzPath=_bP+"e\\k\\";
	public static String queriesPath=_bP+"e\\q\\";
	public static String fullCitationsPath=_bP+"e\\fc\\";
	public static String procFC_utilPath=_bP+"e\\fc_util\\";
	public static String tokenizedPath=_bP+"e\\tok\\";
	public static String tokenized2Path=_bP+"e\\tok2\\"; // Selected elements, tokenized
	public static String stemmedPath=_bP+"e\\stemmed\\"; // Selected elements, stemmed
	public static String allCorpusTokensPath=_bP+"e\\act\\";
	public static String tokensPath=_bP+"e\\t\\";
	public static String tagInclusionsPath=_bP+"e\\ti\\";
	public static String umlsVariantsPath=_bP+"e\\u\\";
	public static String adjacenciesPath=_bP+"e\\a\\";
	//	public static String adjacenciesPath=_eP+"e\\a\\";
	public static String abstractsPath=_bP+"e\\ab\\";
	public static String parsedAbstracts=_bP+"e\\d\\";
	public static String nounPhrases=_bP+"e\\np\\";
	public static String positionsPath=_bP+"e\\p\\";
	public static String frequenciesPath=_bP+"e\\f\\";
	public static String orderedPositionsByTypePath=_bP+"e\\pt\\";
	public static String orderedPositionsByDegreePath=_bP+"e\\pd\\";
	public static String linksPath=_bP+"e\\li\\";
	//	public static String positionsPath=_eP+"e\\p\\";
	public static String outputPath=_bP+"e\\o\\";
	//	public static String outputPath=_eP+"e\\o\\";
	public static String googleEarthPath=_bP+"program files\\Google\\Google Earth\\client\\googleearth.exe";

	static
	{
		String os_name = System.getProperty("os.name");

		if (os_name != null) System.out.println("OS: " + os_name);

		if (os_name != null && os_name.indexOf("Windows") < 0)
		{
			// If no Windows in os_name, assume unix
			//	 String _bP="/localprojects/citeviz/webapps/citeviz/";
			// String _bP="/localprojects/citeviz/";
			String _bP="/Users/meb7002/large/";
			//	 String _bP="/tmp/";
			//	 public final static String _bP="/usr/local/Bales/"; // change when locating to new system
			kmzPath=_bP+"e/k/";
			queriesPath=_bP+"e/q/";
			fullCitationsPath=_bP+"e/fc/";
			procFC_utilPath=_bP+"e/fc_util/";
			tokenizedPath=_bP+"e/tok/";
			tokenized2Path=_bP+"e/tok2/"; // Selected elements, tokenized
			stemmedPath=_bP+"e/stemmed/"; // Selected elements, stemmed
			allCorpusTokensPath=_bP+"e/act/";
			tokensPath=_bP+"e/t/";
			tagInclusionsPath=_bP+"e/ti/";
			umlsVariantsPath=_bP+"e/u/";
			adjacenciesPath=_bP+"e/a/";
			abstractsPath=_bP+"e/ab/";
			parsedAbstracts=_bP+"e/d/";
			nounPhrases=_bP+"e/np/";
			positionsPath=_bP+"e/p/";
			frequenciesPath=_bP+"e/f/";
			orderedPositionsByTypePath=_bP+"e/pt/";
			orderedPositionsByDegreePath=_bP+"e/pd/";
			linksPath=_bP+"e/li/";
			outputPath=_bP+"e/o/";
		//	googleEarthPath=_bP+"Google/Google Earth/googleearth.exe";
			googleEarthPath=_bP+"/Applications/Google Earth.app";
		}	
	}
}