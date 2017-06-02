package bales;


import java.awt.Color;
import java.io.BufferedReader;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.Vector;

/***************************************************************************
 * Parse conditions from a file and marks a vertices verifing these conditions.
 * @version  $Revision: 1.4 $; $Date: 2007/12/13 08:45:53 $
 * @author   Damien Zufferey
 **************************************************************************/
public class MarkerScript extends Marker {

    private static final int equals = 1;
    private static final int contains = 2;
    private static final int starts = 3;
    private static final int ends = 4;
    
    /**used as mode for pattern interpretation*/
    protected static final Integer EQUALS = new Integer(equals);
    /**used as mode for pattern interpretation*/
    protected static final Integer CONTAINS = new Integer(contains);
    /**used as mode for pattern interpretation*/
    protected static final Integer STARTS = new Integer(starts);
    /**used as mode for pattern interpretation*/
    protected static final Integer ENDS = new Integer(ends);
    /**used as mode for pattern interpretation*/
    protected static final Integer NOT_EQUALS = new Integer(-equals);
    /**used as mode for pattern interpretation*/
    protected static final Integer NOT_CONTAINS = new Integer(-contains);
    /**used as mode for pattern interpretation*/
    protected static final Integer NOT_STARTS = new Integer(-starts);
    /**used as mode for pattern interpretation*/
    protected static final Integer NOT_ENDS = new Integer(-ends);
    
    /**
     * Objects containing a color and a list of conditions
     * @author Damien Zufferey
     */
    protected class ColorCondition{
        public Color color;
        public Vector<Integer> mode;
        public Vector<String> pattern;
        
        /** Constructor */
        public ColorCondition(Color c){
            color = c;
            mode = new Vector<Integer>();
            pattern = new Vector<String>();
        }
        
        /**
         * if the vertex correspond to the condition, changes its color
         * @param vertex
         */
        public void test(GraphVertex vertex){
            boolean affect = false;
            boolean keep = true;
            int end = pattern.size();
            for(int i = 0; i < end ; ++i){
                String pattern = this.pattern.get(i);
                Integer m = mode.get(i);
                keep = m.intValue() > 0;
                int how = Math.abs(m.intValue());
                if(how == equals){
                    if(vertex.name.equals(pattern)){
                        affect = keep;
                    }
                }else if(how == contains){
                    if(vertex.name.matches(".*"+pattern+".*")){
                        affect = keep;
                    }
                }else if(how == starts){
                    if(vertex.name.startsWith(pattern)){
                        affect = keep;
                    }
                }else if(how == ends){
                    if(vertex.name.endsWith(pattern)){
                        affect = keep;
                    }
                }
            }
            if(affect){
                vertex.color = color;
            }
        }
    }
    
    private Vector<ColorCondition> conditions;
    private boolean def = false;
    private Color defaultColor;
    
    /**
     * Constructor
     * @param in BufferedReader from a file containing the informations
     */
    public MarkerScript(BufferedReader in){
        //String to Integer
        HashMap<String,Integer> map = new HashMap<String,Integer>();
        map.put("EQUALS",EQUALS);
        map.put("CONTAINS",CONTAINS);
        map.put("STARTS",STARTS);
        map.put("ENDS",ENDS);
        map.put("NOT_EQUALS",NOT_EQUALS);
        map.put("NOT_CONTAINS",NOT_CONTAINS);
        map.put("NOT_STARTS",NOT_STARTS);
        map.put("NOT_ENDS",NOT_ENDS);
        
        conditions = new Vector<ColorCondition>();
        ColorCondition cc = null;
        //parse file
        try {
            while(in.ready()){
                String line = in.readLine();
                StringTokenizer st = new StringTokenizer(line);
                //doesn't process comment/empty lines
                if(!line.startsWith("#") && !line.matches("\\s*")){
                    
                    //conditions
                    if(line.startsWith("\t") || line.startsWith(" ")){
                        String tocken = st.nextToken();
                        Integer mode = map.get(tocken);
                        if(mode != null){
                            if(mode.intValue() < 0){//NOT_...
                                cc.mode.add(mode);
                                cc.pattern.add(st.nextToken());
                            }else{
                                cc.mode.insertElementAt(mode, 0);
                                cc.pattern.insertElementAt(st.nextToken(), 0);
                            }
                        }else if (tocken.equalsIgnoreCase("default")){
                            this.def = true;
                            this.defaultColor = cc.color;
                            conditions.remove(cc);
                            cc = null;
                        }
                    
                    //parse color for the following conditions
                    }else{
                        Color nc = new Color(Integer.parseInt(st.nextToken(),16));
                        cc = new ColorCondition(nc);
                        conditions.add(cc);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }
    
    /*****************************************************************
     * Special marking for certain vertices by setting attributes of the vertex
     * @param vertex  Vertex of the graph representation.
     *****************************************************************/
    public void mark(GraphVertex vertex) {
        //if default color exists
        if(def){
            vertex.color = defaultColor;
        }
        //test the conditions
        int end = conditions.size();
        for(int i = 0; i < end ; ++i){
            ((ColorCondition)conditions.get(i)).test(vertex);
        }
    }
}