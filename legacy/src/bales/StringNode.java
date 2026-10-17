package bales;

public class StringNode
{
	public String string;
	public StringNode next;
	public int nounPhrase = -1;
	public String type;
	
	public void setType(String type)
	{
		this.type = type;
	}
	
	public String toString()
	{
		return string;
	}

	public int length()
	{
		int count = 0;
		StringNode current = this;
		while(current != null)
		{
			count++;
			current = current.next;
		}
		return count;
	}
	
	public boolean setNounPhrase(int number)
	{
		//System.out.println("setting " + this.string + " to " + number + " was " + nounPhrase);
		if(nounPhrase != -1)
		{
			//System.out.println("returning false");
			return false;
		}
		else
		{
			nounPhrase = number;
			return true;
		}
	}
	
	public String toMarkedUpString()
	{
		String toReturn = "";
		if(((this.next != null)&&(this.nounPhrase != -1))&&(this.next.nounPhrase==this.nounPhrase))
		{
			toReturn += this.string + "_" + this.next.toMarkedUpString();
		}
		else if(this.next != null)
		{
			toReturn += this.string + "/" + this.type + " " + this.next.toMarkedUpString();
		}
		else
		{
			toReturn += this.string + "/" + this.type;
		}
		return toReturn;
	}

	public void replaceTag(int hadTag, int withTag)
	{
		if(this.nounPhrase == hadTag)
		{
			this.nounPhrase = withTag;
		}
	}
}
