package bales;
//=======================================================================================
//Tokenizer.java tokenizes text
//=======================================================================================
import java.io.*;
import bales.File_Settings;
import java.util.StringTokenizer;
public class Tokenizer  {
	public static String main(String S) {
		String outString="";
		StringTokenizer t=new StringTokenizer(S," ");
		while(t.hasMoreTokens())
			outString=outString + "|" + t.nextToken();
		return outString;
	}
}