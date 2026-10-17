package bales;
import java.util.Hashtable;
import java.util.Vector;
import java.util.Collections;
import java.util.Enumeration;
public class SortHashtable {
  public static void main(String[] args) {
    // Create and populate hashtable
    Hashtable ht = new Hashtable();
    ht.put("1", "str");
    ht.put("2", "xyz");
    ht.put("3", "xyz");
    ht.put("4", "mno");
    // Sort hashtable.
    Vector v = new Vector(ht.values());
    Collections.sort(v);
    // Display (sorted) hashtable.
    for (Enumeration e = v.elements(); e.hasMoreElements();) {
      String key = (String)e.nextElement();
      String val = (String)ht.get(key);
      System.out.println("Key: " + key + "     Val: " + val);
    }
  }
}