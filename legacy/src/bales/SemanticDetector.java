package bales;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;


public class SemanticDetector
{
	//maxwords defines the max amount of words in a checked window
        private String umlsmapfile = "umls_with_lexical_variants_plus.txt";
	public static int MAXWORDS = 6;
	String[] checked = new String[0];
	boolean[] answers = new boolean[0];
	String[] found = new String[0];
	HashMap<String, String> hash;
	public int count = 0;

	BufferedWriter logWriter;

	public SemanticDetector(String filename)
        {
            umlsmapfile = filename;
            try {
                    logWriter = new BufferedWriter(new FileWriter(new File("log.txt")));
            } catch (IOException e) {
                    e.printStackTrace();
            }
            hash = readHashMap();
	}

	//this reads from the file 'with_lexical_variants.txt', which contains
	//noun phrases, one per line, and drops them into a hashmap
	private HashMap<String, String> readHashMap()
	{
		HashMap<String, String> newHash = new HashMap<String, String>();
		try
		{
			BufferedReader reader = new BufferedReader(new FileReader(new File(umlsmapfile)));
			String reading = reader.readLine();
			while(reading != null)
			{
				int brk1 = reading.indexOf('|');
                                int brk2 = reading.indexOf('|', brk1+1);
                                String tag = "NN";
                                if (brk2 <= 0) { brk2 = reading.length(); }
                                else { tag = reading.substring(brk2+1); }
                                
                                newHash.put(reading.substring(brk1+1, brk2),tag);
				reading = reader.readLine();
			}
			return newHash;
		}
		catch(Exception e)
		{
			e.printStackTrace();
			System.exit(1);
			return null;
		}
	}
	
	//this method detects and marks up noun phrases in the linked list
	//the head of which is 'head'
	public StringNode detect(StringNode head)
	{
		StringNode current = head;
		int index = 0;
		int length = head.length();
		while(current != null)
		{
			for(int i = MAXWORDS; i > 0; i--)
			{
				int amountLater = i;
				if(!(amountLater + index >= length))
				{
					checkWindow(current,getLater(current,amountLater));
				}
			}
			current = current.next;
			index++;
		}
		return head;
	}
	
	//this method makes an array one slot bigger
	private static String[] increment(String[] array)
	{
		String[] newArray = new String[array.length+1];
		for(int i = 0; i < array.length; i++)
		{
			newArray[i] = array[i];
		}
		return newArray;
	}

	//this method makes an array one slot bigger
	private static StringNode[] increment(StringNode[] array)
	{
		StringNode[] newArray = new StringNode[array.length+1];
		for(int i = 0; i < array.length; i++)
		{
			newArray[i] = array[i];
		}
		return newArray;
	}

	//this method makes an array one slot bigger
	private static boolean[] increment(boolean[] array)
	{
		boolean[] newArray = new boolean[array.length+1];
		for(int i = 0; i < array.length; i++)
		{
			newArray[i] = array[i];
		}
		return newArray;
	}

	//this method turns a string with whitespace delimited tokens into a linked
	//list of tokens, and returns the head of that list
	public static StringNode whitespaceTokenize(String string, boolean isMarkedUp)
	{
		//TODO
//		System.out.println("String to check: " + string);
//		System.out.println("Maximum words: " + MAXWORDS);
		StringNode head = new StringNode();
		StringNode current = head;
		while(string.length() != 0)
		{
			if(string.indexOf(" ") != -1)
			{
				String working = string.substring(0,string.indexOf(" "));
				String otherStuff = "";
				if(isMarkedUp)
				{
					otherStuff = working.substring(working.indexOf("/")+1,working.length());
					if(otherStuff.indexOf("/") != -1)
					{
						current.type = otherStuff.substring(0,otherStuff.indexOf("/"));
					}
					else
					{
						current.type = otherStuff;
					}
					working = working.substring(0,working.indexOf("/"));
				}
				current.string = working;
				current.next = new StringNode();
				string = string.substring(string.indexOf(" "),string.length()).trim();
				current = current.next;
			}
			else
			{
				String working = string;
				String otherStuff = "";
				if(isMarkedUp)
				{
					otherStuff = working.substring(working.indexOf("/")+1,working.length());
					if(otherStuff.indexOf("/") != -1)
					{
						current.type = otherStuff.substring(0,otherStuff.indexOf("/"));
					}
					else
					{
						current.type = otherStuff;
					}
					working = working.substring(0,working.indexOf("/"));
				}
				current.string = working;
				string = "";
			}
		}
		return head;
	}

	//this method replaces all occurences of 'toReplace' in 'string' with 'replaceWith'
	private static String replace(String string, String toReplace, String replaceWith)
	{
		if(string != null)
		{
			while(string.indexOf(toReplace) != -1)
			{
				string = string.substring(0,string.indexOf(toReplace)) + replaceWith + string.substring(string.indexOf(toReplace)+toReplace.length(),string.length());
			}
			return string;
		}
		else
		{
			return null;
		}
	}
	
	//this method replaces all whitespace with spaces
	private static String replaceWhites(String string)
	{
		string = replace(string, "\t", " ");
		string = replace(string, "\n", " ");
		string = replace(string, "\r", " ");
		string = replace(string, "  ", " ");
		return string;
	}

	//this method removes all punctuation from a string.
	//just say no to racism.
	private static String replaceBlacks(String string)
	{
		string = replace(string, ".", "");
		string = replace(string, ",", "");
		string = replace(string, "?", "");
		string = replace(string, "\\", "");
		string = replace(string, "/", "");
		string = replace(string, ":", "");
		string = replace(string, ";", "");
		string = replace(string, "(", "");
		string = replace(string, ")", "");
		string = replace(string, "!", "");
		string = replace(string, "'", "");
		string = replace(string, "\"", "");
		return string;
	}

	//this method takes a file and whether or not it is marked up in word/type/parent
	//and then returns an array of stringnodes (the first node from each line) that
	//have been marked up
	public StringNode[] sentencesFile(String filename, boolean markedUp)
	{
		StringNode[] toReturn = null;
		try
		{
			toReturn = new StringNode[0];
			BufferedReader reader = new BufferedReader(new FileReader(new File(filename)));
			String reading = reader.readLine();
			while(reading != null)
			{
				toReturn = increment(toReturn);
				toReturn[toReturn.length-1] = detect(Detector.whitespaceTokenize(reading,markedUp));
				reading = reader.readLine();
			}
		}
		catch(Exception e)
		{
			e.printStackTrace();
			System.exit(1);
		}
		return toReturn;
	}

	//this method stuffs a file into a string
	public static String fileToSingleString(String filename)
	{
		try
		{
			BufferedReader reader = new BufferedReader(new FileReader(new File(filename)));
			String reading = reader.readLine();
			String toReturn = ""; 
			while(reading != null)
			{
				toReturn = reading.trim() + " ";
				reading = reader.readLine();
			}
			return replaceWhites(toReturn.trim());
		}
		catch(Exception e)
		{
			e.printStackTrace();
			System.exit(1);
			return null;
		}
	}
	
	//this method checks the window defined by nodes 'first' and 'last'
	//and if they register as a noun phrase, tags them
	private boolean checkWindow(StringNode first, StringNode last)
	{
		String toCheck = "";
		boolean toReturn = false;
		StringNode current = first;
		if(current != last)
		{
			while(current != last)
			{
				toCheck += current.string + " ";
				current = current.next;
			}
			toCheck += last.string;
		}
		else
		{
			toCheck = current.string;
		}
		toCheck = toCheck.trim().toLowerCase();
		if(!contains(checked,toCheck))
		{
			checked = increment(checked);
			answers = increment(answers);
			checked[checked.length-1] = toCheck;
			answers[answers.length-1] = check(toCheck);


			toReturn = answers[answers.length-1];
		}
		else
		{
			if(toReturn)
			{
				try
				{
				logWriter.write("Found: " + toCheck + " -> " + this.hash.get(toCheck));
				logWriter.newLine();
				logWriter.flush();
				}
				catch(Exception e)
				{
					e.printStackTrace();
					System.exit(1);
				}
			}
			toReturn = answers[indexOf(checked,toCheck)];
		}

		if(toReturn)
		{
			tagWindow(first,last);
//			System.out.println("Found: " + first + " -> " + last);
		}

		if(toReturn)
		{
			try
			{
				logWriter.write("Types: ");
				String lastType = hash.get(toCheck);
                                last.setType(lastType);
                                current = first;
				if(current != last)
				{
					while(current != last)
					{
						logWriter.write(current.toString() + "/" + current.type + " ");
						//lastType = current.type;
						current = current.next;
					}
					//lastType = current.type;
					logWriter.write(current.toString() + "/" + current.type + " ");
				}
				else
				{
					//lastType = current.type;
					logWriter.write(current.toString() + "/" + current.type);
				}
				logWriter.newLine();
				logWriter.write("Last tag: " + lastType);
				logWriter.newLine();
				logWriter.flush();
			}
			catch(Exception e)
			{
				e.printStackTrace();
				System.exit(1);
			}
		}
		return toReturn;
	}
	
	//this method tags the entire window defined by nodes 'first' and 'last'
	//as a noun phrase (if there isn't any overlap, that is)
	private void tagWindow(StringNode first, StringNode last)
	{
		System.out.flush();
		String wholeSpan = "";
		boolean worked = true;
		StringNode current = first;
		if(current != last)
		{
			while(current != last)
			{
				wholeSpan += current.string + " ";
				if(!current.setNounPhrase(count))
				{
					worked = false;
				}
				current = current.next;
			}
			if(!last.setNounPhrase(count))
			{
				worked = false;
			}
			wholeSpan += last.string;
		}
		else
		{
			wholeSpan += current.string;
			worked = current.setNounPhrase(count);
		}
		if(!worked)
		{
			System.out.println("ERROR (Overlap): " + wholeSpan);
			tagWindow(first,last,-1, count);
		}
		count++;
	}
	
	//this method tags the nodes of the window defined by 'first' and 'last'
	//with the given 'withTag' if they had the tag 'hadTag'
	private void tagWindow(StringNode first, StringNode last, int withTag, int hadTag)
	{
		String wholeSpan = "";
		if(first != last)
		{
			while(first != last)
			{
				wholeSpan += first.string;
				first = first.next;
				first.replaceTag(hadTag,withTag);
			}
		}
		else
		{
			wholeSpan += first.string;
		}
	}

	//this method returns true if 'string' is in 'array'
	private static boolean contains(String[] array, String string)
	{
		for(int i = 0; i < array.length; i++)
		{
			if(array[i].equals(string))
			{
				return true;
			}
		}
		return false;
	}

	//this method returns the index of 'string' in 'array'
	private static int indexOf(String[] array, String string)
	{
		for(int i = 0; i < array.length; i++)
		{
			if(array[i].equals(string))
			{
				return i;
			}
		}
		return -1;
	}
	
	//this method checks the string toCheck against the hash of noun phrases
	private boolean check(String toCheck)
	{
		toCheck = replaceBlacks(toCheck);
		if(this.hash.containsKey(toCheck) && !hash.get(toCheck).equals(""))
		{
			try
			{
				logWriter.write("Found: " + toCheck + " -> " + this.hash.get(toCheck));
				logWriter.newLine();
				logWriter.flush();
			}
			catch(Exception e)
			{
				e.printStackTrace();
				System.exit(1);
			}
			return true;
		}
		return false;
	}

	//this method returns the node that is 'index' spots after the
	//node called 'node' in the linked list
	private static StringNode getLater(StringNode node, int index)
	{
		for(int i = 0; i < index; i++)
		{
			if(node.next != null)
			{
				node = node.next;
			}
			else
			{
				return node;
			}
		}
		return node;
	}

	
	// this method stuffs an array of strings into a single string separated by spaces
	public static String putTogether(String[] args)
	{
		String toReturn = "";
		for(int i = 0; i < args.length; i++)
		{
			toReturn += args[i] + " ";
		}
		toReturn = toReturn.trim();
		return toReturn;
	}

	public static void main(String[] args)
	{
		SemanticDetector d = new SemanticDetector("C:\\AQUA\\project\\AQUA\\bin\\umls_test_all.txt");
		StringNode result = d.detect(Detector.whitespaceTokenize("This/a is/b an/c example/d string/e home/f health/g and/h fresh/i breath/j", true));
		System.out.println(result.toMarkedUpString());
//		if(args.length > 0)
//		{
//			System.out.println((new Detector()).detect(Detector.whitespaceTokenize(putTogether(args),true)).toMarkedUpString());
//		}
//		else
//		{
//			(new Detector()).sentencesFile("dsumlong.set", true);
//		}
	}
}
