package bales;


import java.awt.Color;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.util.StringTokenizer;


/******************************************************************************
 * A class that save and load clusters' informations in WriterDataGraphicsDISP
 * @version  $Revision: 1.7 $; $Date: 2007/12/13 08:45:53 $
 * @author   Damien Zufferey
 *****************************************************************************/
public class ClusterReaderWriter {
    /*
     * the file are in text format of the form:
     * 
     * group1.name \t color \t visible \t info
     * \t node1.name
     * \t node2.name
     * \t ...
     * group2.name \t color \t visible \t info
     * ...
     * 
     * see class Cluster for informations about the fields
     */
    
    
    
    
    /**
     * read from a stream, build the clusters and pt them in the Writer...DISP
     * @param in - the input stream
     * @param writer - the WriterDataGraphicsDISP that contains the cluster
     */
    public static void read(BufferedReader in, WriterDataGraphicsDISP writer){
        //remove all existing cluster (except default)
        int size = writer.getNbOfCluster();
        while(size > 1){
            writer.removeCluster(1);
            size = writer.getNbOfCluster();
        }
        try{
            Cluster curCluster = null;
            String line = null;
            GraphData graph = writer.graph;
            while ((line = in.readLine()) != null) {             
                //vertex
                if(line.startsWith("\t")){
                    String name = line.substring(1, line.length());
                    GraphVertex curVertex = graph.nameToVertex.get(name);
                    if(curVertex != null){
                        curCluster.addNode(curVertex);
                    }
                }else{
                    //cluster
                    StringTokenizer st = new StringTokenizer(line,"\t");
                    curCluster = new Cluster(st.nextToken());
                    curCluster.setColor(new Color(Integer.parseInt(st.nextToken())));
                    curCluster.visible = (Boolean.valueOf(st.nextToken())).booleanValue();
                    curCluster.info = (Boolean.valueOf(st.nextToken())).booleanValue();
                    writer.addCluster(curCluster);
                }
            }
        }catch (Exception e){
            System.err.println("Exception while reading (ClusterReaderWriter.read): ");
            System.err.println(e);
        }
    }
    
    
    /**
     * write on a stream the informations needed to rebuild the clusters later
     * @param out - the output stream
     * @param writer - the WriterDataGraphicsDISP that contains the cluster
     */
    public static void write(PrintWriter out, WriterDataGraphicsDISP writer){
        int end = writer.getNbOfCluster();
        for(int i = 1; i < end; ++i){//begin at 1 => ignore default clt
            Cluster curCluster = writer.getCluster(i);
            out.println(curCluster.getName()+"\t"
                    + (curCluster.getColor().getRGB() & 0x00FFFFFF) + "\t"
                    + curCluster.visible + "\t"
                    + curCluster.info);
            int size = curCluster.size();
            for(int j = 0; j < size; ++j){
                GraphVertex curVertex = curCluster.getNode(j);
                out.println("\t"+curVertex.name);
            }
        }
    }
}