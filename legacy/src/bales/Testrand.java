package bales;

public class Testrand {
	public static void main(String args[]) {
		String seed="";
		for(int i=0; i<1000; i++)
		{
		seed=Integer.toString((int)(1000+Math.random()*1000));
		System.out.println(seed);
		}
	}
}