
package net.prgarnett.notedatabase;

import java.awt.Desktop;
import java.util.Map.*;
import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFileChooser;
import org.apache.commons.lang3.ArrayUtils;
import org.neo4j.driver.v1.AuthTokens;
import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.GraphDatabase;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.neo4j.driver.v1.Transaction;

/**
 * Swing application for creating and editing Neo4j graph databases.
 * Loading a database requires 4 CSV files to be saved in database folder: 
 * NodeProperties.csv : vertical list of each node type with properties for each node across each row. ID property not necessary, but the first property must be 'name'.
 * RelationshipProperties.csv : vertical list of each relationship type with properties across each row
 * NodeRelationships.csv : vertical list of each node type with possible relationship types across each row. Nodes are the source (node1) of the directed relationship (node1-rel-node2)
 * RelationshipNodes.csv : vertical list of each relationship type with possible node types across each row. Nodes are node2 of the directed relationship (node1-rel-de2)
 * @author Katherine Newling
 */
public class GraphDatabaseForm extends javax.swing.JFrame
{
    private Driver driver;
    private Session session;
    private HashMap<String, String[]> nodeProperties;// map linking node types to their properties
    private HashMap<String, String[]> relationshipProperties;// map linking relationship types to their properties
    private HashMap<String, String[]> nodeRelationships;// map linking node types to their relationship types
    private HashMap<String, String[]> relationshipNodes;// map linking relationship types to the node types they point to
    private ArrayList<String[]> createNodePropertyList;// list of new properties and their values to be added to selected node
    private ArrayList<String[]> createRelationshipPropertyList; // list of new properties and their values to be added to selected relationship
    private ArrayList<String> allNodeIDs; // list of all the node IDs in current database
    
    /**
     * getValuesFromCSV: fills a 2D arraylist of strings with values from given CSV file
     * @param filePath
     * @return 
     */
    private ArrayList<ArrayList<String>> getValuesFromCSV(String filePath)
    {
	String csvFile = filePath;
	BufferedReader br = null;
	String line = "";
	ArrayList<ArrayList<String>> stringList = new ArrayList<>();
        
	try
        {
            br = new BufferedReader(new FileReader(csvFile));
            while ((line = br.readLine()) != null)
            {
                String[] s = line.split(",");
                stringList.add(new ArrayList<>(Arrays.asList(s)));
            }
	}
        catch (FileNotFoundException e)
        {
            System.out.println("File: "+filePath+" not found.");
	}
        catch (IOException e)
        {
            System.out.println("IO Exception - getValuesFromCSV: " + e.getMessage());
	}
        finally 
        {
            if (br != null) {
                try
                {
                    br.close();
                } 
                catch (IOException e) 
                {
                    System.err.println("IO Exception - getValuesFromCSV: " + e.getMessage());
                }
            }
	}
	return stringList;
    }
    
    /**
     * createMapFromLists: converts 2D arraylist of strings into maps. e.g. each node type as a key and its properties as values
     * @param nodeVals
     * @return 
     */  
    private HashMap<String, String[]>  createMapFromLists(ArrayList<ArrayList<String>> nodeVals)
    {
        HashMap<String, String[]> map = new HashMap<>();
        String[] keys = new String[nodeVals.size()];
        for(int i=0; i<nodeVals.size(); i++)
        {
            keys[i] = nodeVals.get(i).get(0);
            int rowLength = nodeVals.get(i).size();
            String[] props = new String[rowLength-1];

            for(int j=0; j<nodeVals.get(i).size()-1; j++)
            {
                props[j] = nodeVals.get(i).get(j+1);
            }

            if(props.length==0)
            {
                props = new String[]{" ", " "};
            }

            map.put(keys[i], props);
        }
        return map;
    }
    
    /**
     * setAllNodeIDs: execute Cypher query to find all the current node IDs
     */
    
    private void setAllNodeIDs()
    {
        allNodeIDs = new ArrayList<>();

        StatementResult result = session.run( "MATCH (n) RETURN n.ID AS ID" );
        while ( result.hasNext() )
        {
            Record record = result.next();
            Map<String,Object> row = record.asMap();
            String rows = "";
            for ( Entry<String,Object> column : row.entrySet() )
            {
                rows += column.getValue();
                allNodeIDs.add(rows);
            }
        }
        allNodeIDs.removeAll(Collections.singleton("null"));
        java.util.Collections.sort(allNodeIDs);
    }
   /**
    * getNewID: find an unused ID by finding largest current ID and adding 1
    * @return String ID (a long as a String)
    */
    private String getNewID()
    {
        ArrayList<Long> allNodeIDsLong = new ArrayList<>();
        
        if (allNodeIDs.size()>0)
        {
            for(String s : allNodeIDs)
            {
                try
                {
                    allNodeIDsLong.add(Long.valueOf(s));//convert to long
                }
                catch (NumberFormatException e)
                {
                    System.out.println ("Cannot convert String to long.");
                    return null;
                }
            }
        
            Collections.sort(allNodeIDsLong);
        
            long maxID = allNodeIDsLong.get(allNodeIDsLong.size()-1);
            long newID = maxID+1;
            allNodeIDs.add(String.valueOf(newID));
            return String.valueOf(newID);
        }
        else
        {
            allNodeIDs.add("1");
            return "1";
        }
    }
    
    /**
     * setNewDatabaseFields: start a new GraphDB service and load all of the current nodes, relationships, etc. into the drop-down boxes
     * @param DB_STRING 
     */
    private void setNewDatabaseFields(String DB_STRING)
    {
        //shutdown old dbservice if it's running
        if(driver!=null)
        {
            driver.close();
        }
        if(session!=null)
        {
            session.close();
        }
        
        driver = GraphDatabase.driver( DB_STRING, AuthTokens.basic( "neo4j", "Nufoa23" ));
        session = driver.session();
       
       //put all of the current node IDs into a list
        setAllNodeIDs();
       
       //Display new database in text area
       jTextPane1.setText(jTextPane1.getText()+"\n Set database to: " + DB_STRING);
       
       //Load from 4 csv files in current database folder. These are: 1. Nodes to properties 2. Relationships to properties 3. Node to relationships 4. Relationship to nodes
       ArrayList<ArrayList<String>> nodeVals = getValuesFromCSV(DB_STRING+"\\NodeProperties.csv");
       ArrayList<ArrayList<String>> relVals = getValuesFromCSV(DB_STRING+"\\RelationshipProperties.csv");
       ArrayList<ArrayList<String>> nodeRels = getValuesFromCSV(DB_STRING+"\\NodeRelationships.csv");
       ArrayList<ArrayList<String>> relNodes = getValuesFromCSV(DB_STRING+"\\RelationshipNodes.csv");
       
       //convert list arrays into maps 
       nodeProperties = createMapFromLists(nodeVals);
       relationshipProperties = createMapFromLists(relVals);
       nodeRelationships = createMapFromLists(nodeRels);
       relationshipNodes = createMapFromLists(relNodes);
           
       //set combobox and text fields    
       
       //create node panel       
       ComboBoxType1.setModel(new javax.swing.DefaultComboBoxModel(getAllNodeTypes()));       
       ComboBoxProperty1.setModel(new javax.swing.DefaultComboBoxModel(getPropertyTypes(ComboBoxType1.getSelectedItem().toString())));
       ComboBoxProperty1.setEditable(true);
       
       //node 1 panel
       ComboBoxType2.setModel(new javax.swing.DefaultComboBoxModel(getAllNodeTypes()));
       ComboBoxName1.setModel(new javax.swing.DefaultComboBoxModel(getNodeNames(ComboBoxType2.getSelectedItem().toString()))); 
       ComboBoxID1.setModel(new javax.swing.DefaultComboBoxModel(getNodeIDs(ComboBoxType2.getSelectedItem().toString(), ComboBoxName1.getSelectedItem().toString())));
       ComboBoxProperty2.setModel(new javax.swing.DefaultComboBoxModel(getPropertyTypesfromID(ComboBoxID1.getSelectedItem().toString())));
       TextField2.setText(getNodePropertyValue(ComboBoxProperty2.getSelectedItem().toString(), ComboBoxID1.getSelectedItem().toString())[0]);
       
       //current relationship panel
       ComboBoxRelationship1.setModel(new javax.swing.DefaultComboBoxModel(getRelationshipTypes(ComboBoxType2.getSelectedItem().toString())));
       ComboBoxProperty3.setModel(new javax.swing.DefaultComboBoxModel(getRelationshipProperties(ComboBoxRelationship1.getSelectedItem().toString())));
       // leave TextField3 blank for now
         
       refreshNode2Panel1();
      
       TextField3.setText(getRelationshipPropertyValue(ComboBoxID1.getSelectedItem().toString(), ComboBoxID2.getSelectedItem().toString(), ComboBoxRelationship1.getSelectedItem().toString(), ComboBoxProperty3.getSelectedItem().toString())[0]);
       
       refreshCreateRelationshipPanel();
       refreshNode2Panel2();
    }
    
    /**
     * refreshNode1Panel: update drop-down boxes in first panel
     * 
     */
    
    private void refreshNode1Panel()
    {
       ComboBoxName1.setModel(new javax.swing.DefaultComboBoxModel(getNodeNames(ComboBoxType2.getSelectedItem().toString()))); 
       ComboBoxID1.setModel(new javax.swing.DefaultComboBoxModel(getNodeIDs(ComboBoxType2.getSelectedItem().toString(), ComboBoxName1.getSelectedItem().toString())));
       ComboBoxProperty2.setModel(new javax.swing.DefaultComboBoxModel(getPropertyTypesfromID(ComboBoxID1.getSelectedItem().toString())));
       TextField2.setText(getNodePropertyValue(ComboBoxProperty2.getSelectedItem().toString(), ComboBoxID1.getSelectedItem().toString())[0]);
        
    }
    
    /**
     * refreshCurrentRelationshipPanel: update drop-down boxes in current relationship panel
     */
    
    private void refreshCurrentRelationshipPanel()
    {
        ComboBoxRelationship1.setModel(new javax.swing.DefaultComboBoxModel(getRelationshipTypes(ComboBoxType2.getSelectedItem().toString())));
        ComboBoxProperty3.setModel(new javax.swing.DefaultComboBoxModel(getRelationshipProperties(ComboBoxRelationship1.getSelectedItem().toString())));
        TextField3.setText(getRelationshipPropertyValue(ComboBoxID1.getSelectedItem().toString(), 
        ComboBoxID2.getSelectedItem().toString(), ComboBoxRelationship1.getSelectedItem().toString(),  ComboBoxProperty3.getSelectedItem().toString())[0]);
    }
    
    /**
     * refreshCreateRelationshipPanel: update drop-down boxes in create relationship panel
     */
    
    private void refreshCreateRelationshipPanel()
    {
        ComboBoxRelationship2.setModel(new javax.swing.DefaultComboBoxModel(getRelationshipTypes(ComboBoxType2.getSelectedItem().toString())));
        ComboBoxProperty4.setModel(new javax.swing.DefaultComboBoxModel(getRelationshipProperties(ComboBoxRelationship2.getSelectedItem().toString())));
        TextField4.setText("");//empty for new relationship
    }
    
    /**
     * refreshNode2Panel1: update drop-down boxes in Node 2 panel 1
     */
    
    private void refreshNode2Panel1()
    {
        ComboBoxType3.setModel(new javax.swing.DefaultComboBoxModel(getNode2Types(ComboBoxRelationship1.getSelectedItem().toString())));
        ComboBoxName3.setModel(new javax.swing.DefaultComboBoxModel(getNode2Names(ComboBoxID1.getSelectedItem().toString(), 
               ComboBoxRelationship1.getSelectedItem().toString())));
        ComboBoxID2.setModel(new javax.swing.DefaultComboBoxModel(getNode2IDs(ComboBoxID1.getSelectedItem().toString(), 
               ComboBoxRelationship1.getSelectedItem().toString(), ComboBoxName3.getSelectedItem().toString())));
    }
    
    /**
     * refreshNode2Panel2: update drop-down boxes in Node 2 panel 2
     */
    
    private void refreshNode2Panel2()
    {
        ComboBoxType4.setModel(new javax.swing.DefaultComboBoxModel(getNode2Types(ComboBoxRelationship2.getSelectedItem().toString())));
        ComboBoxName4.setModel(new javax.swing.DefaultComboBoxModel(getNodeNames(ComboBoxType4.getSelectedItem().toString()))); 
        ComboBoxID3.setModel(new javax.swing.DefaultComboBoxModel(getNodeIDs(ComboBoxType4.getSelectedItem().toString(), 
        ComboBoxName4.getSelectedItem().toString())));
    }
      
      /**
       * refreshPanels: update all panel boxes
       */
      
    private void refreshPanels()
    {
        refreshCurrentRelationshipPanel();
        refreshNode2Panel1();
        TextField3.setText(getRelationshipPropertyValue(ComboBoxID1.getSelectedItem().toString(), ComboBoxID2.getSelectedItem().toString(), ComboBoxRelationship1.getSelectedItem().toString(), ComboBoxProperty3.getSelectedItem().toString())[0]);
        refreshCreateRelationshipPanel();
        refreshNode2Panel2();
    }
    
    /**
     * 
     * @return 
     */
    
    private String[] getAllNodeTypes()
    {
        return nodeProperties.keySet().toArray(new String[nodeProperties.size()]);
    }
    
    /**
     * getPropertyTypes: get list of property types for a given node type from global nodeProperties list
     * @param nodeType
     * @return 
     */
    private String[] getPropertyTypes(String nodeType)
    {
        return nodeProperties.get(nodeType);
    }
    
    /**
     * getQueryResults: execute given Cypher query and return result
     * @param queryString
     * @return alphabetically sorted array of strings
     */
    private String[] getQueryResults(String queryString)
    {
        ArrayList<String> resultList = new ArrayList<>();
        
        StatementResult result = session.run( queryString );
        while ( result.hasNext() )
        {
            Record record = result.next();
            Map<String,Object> row = record.asMap();
            String rows = "";
            for ( Entry<String,Object> column : row.entrySet() )
            {
                rows += column.getValue();
                resultList.add(rows);
            }
        }
        
        java.util.Collections.sort(resultList);
        String[] returnList = resultList.toArray(new String[resultList.size()]);

        if(returnList.length>0)
        {
            return returnList;
        }
        else
        {
            return new String[]{" "};
        }
    }
    
    /**
     * getNodeNames: find the names of all nodes of a given type
     * @param nodeType
     * @return array of strings
     */
    
    private String[] getNodeNames (String nodeType)
    {    
        if(!nodeType.equals(" "))
        {
            String queryString = "match (n:"+nodeType+") return n.name";   
            
            return getQueryResults(queryString);
        }
        else
        {
            return new String[]{" "};
        }
    }
    
    /**
     * getNodeIDs: find the IDs of all nodes of given name and type
     * @param nodeType
     * @param nodeName
     * @return array of strings
     */
    
    private String[] getNodeIDs (String nodeType, String nodeName)
    {
        if(!nodeType.equals(" ")&& !nodeName.equals(" "))
        {
            String queryString = "match (n:"+nodeType+") where n.name = '"+nodeName+"'return n.ID";   
            
            return getQueryResults(queryString);
        }
        else
        {
            return new String[]{" "};
        }
    }
    
    /**
     * getPropertyTypesfromID: execute Cypher query to find a given node's property types from its ID
     * @param ID
     * @return array of strings
     */
    private String[] getPropertyTypesfromID (String ID)
    {
        ArrayList<String> resultList = new ArrayList<>();
        
        if(!ID.equals(" "))
        {
            StatementResult result = session.run( "match (n {ID: '"+ID+"'}) return n" );
                
            while ( result.hasNext() )
            {
                Record record = result.next();
                Map<String,Object> row = record.asMap();

                for ( Entry<String,Object> column : row.entrySet() )
                {
                    resultList.add(column.getKey());
                }
            }

            resultList.remove("ID");

            java.util.Collections.sort(resultList);

            String[] returnList = resultList.toArray(new String[resultList.size()]);

            if(returnList.length>0)
            {
                 return returnList;
            }
            else
            {
                return new String[]{" "};
            }
        }
        else
        {
            return new String[]{" "};
        }
    }
    
    /**
     * getNodePropertyValue: find the value of a property of a given node using its ID
     * @param propertyType
     * @param ID
     * @return array of Strings (should only contain one)
     */
    
    private String[] getNodePropertyValue(String propertyType, String ID)
    {
        if(!propertyType.equals(" ")&& !ID.equals(" "))
        {
            String queryString = "match (n) where n.ID = '"+ID+"' return n."+propertyType;   
            
            return getQueryResults(queryString);
        }
        else
        {
            return new String[]{" "};
        }
    }
    
    /**
     * getRelationshipTypes: find a given node types relationship types from global map nodeRelationships
     * @param nodeType
     * @return array of Strings
     */
    private String[] getRelationshipTypes(String nodeType)
    {
        String[] res = nodeRelationships.get(nodeType);   
        String[] emp = {" "};
        if(res!=null)
        {
            return res;
        }
        else
        {
            return emp;
        }
    }
    
    /**
     * getRelationshipProperties:find a given relationship type's properties from global map relationshipProperties
     * @param relType
     * @return array of strings
     */
    
    private String[] getRelationshipProperties(String relType)
    {
        String[] res = relationshipProperties.get(relType);
        String[] emp = {" "};
        if(res!=null)
        {
            return res;
        }
        else
        {
            return emp;
        }
    }
    
    /**
     * getRelationshipPropertyValue: find the value of a particular relationship property, given both node IDs and property type
     * @param ID1
     * @param ID2
     * @param RelType
     * @param propType
     * @return array of strings
     */
    
    private String[] getRelationshipPropertyValue(String ID1, String ID2, String RelType, String propType)
    {
        if(!ID1.equals(" ")&& !ID2.equals(" ")&& !RelType.equals(" ")&& !propType.equals(" "))
        {
            String queryString = "match (n1)-[r:"+RelType+"]-(n2) where n1.ID = '"+ID1+"' and n2.ID = '"+ID2+"' return r."+propType;   
            
            return  getQueryResults(queryString);
                   
        }
        else
        {
            return new String[]{" "};
        }
    }
    
    /**
     * getNode2Types: find all possible node 2 types for a specific relationship type from global map relationshipNodes
     * @param relType
     * @return array of strings
     */
    
    private String[] getNode2Types(String relType)
    {
        String[] res = relationshipNodes.get(relType);
        String[] emp = {" "};
        if(res!=null)
        {
            return res;
        }
        else
        {
            return emp;
        }
    }
    
    /**
     * getNode2Names: find the names of all nodes with which a given node (node1 ID) has a specific relationship type
     * @param ID1
     * @param relType
     * @return array of strings
     */
    
    private String[] getNode2Names(String ID1, String relType)
    {
        if(!ID1.equals(" ")&& !relType.equals(" "))
        {
            String queryString = "match (n1)-[r:"+relType+"]->(n2) where n1.ID = '"+ID1+"' return n2.name";   
            
            return getQueryResults(queryString);
        }
        else
        {
            return new String[]{" "};
        }
    }
    
    /**
     * getNode2IDs: find the IDs of all nodes with given name for which a given node (node1 ID) has a specific relationship type
     * @param ID1
     * @param relType
     * @param node2name
     * @return array of strings
     */
    
    private String[] getNode2IDs(String ID1, String relType, String node2name)
    {
        if(!ID1.equals(" ")&& !node2name.equals(" ")&& !relType.equals(" "))
        {          
            String queryString = "match (n1)-[r:"+relType+"]->(n2) where n1.ID = '"+ID1+"' and n2.name = '"+node2name+"' return n2.ID";   

            return getQueryResults(queryString);
        }
        else
        {
            return new String[]{" "};
        }
    }
    
    /**
     * updateNodePropertyValues: update the given node's property values from new values given by user in pop-up dialog
     * @param nodeType
     * @param nodeID 
     */
    
    private void updateNodePropertyValues(String nodeType, String nodeID)
    {
        String[] props = getPropertyTypesfromID(nodeID);    
        String[] currentVals = new String[props.length];
        
        for(int j = 0; j<props.length; j++)
        {
            currentVals[j] = getNodePropertyValue(props[j], nodeID)[0];
        }
        
        PropertyDialog p = new PropertyDialog(this, true, "Node",nodeType, props, currentVals); 
        String[] newVals = p.showDialog();
       
        for (int i = 0; i<props.length; i++)
        {
            updateNodePropertyValue(nodeID, props[i], newVals[i]);
        }   
        
        jTextPane1.setText(jTextPane1.getText()+"\n Updated "+props.length+" node properties.");
    }
    
    /**
     * updateNodePropertyValue: update a particular node's property value, given it's ID, the property type and the new value
     * @param nodeID
     * @param propertyType
     * @param newValue 
     */
    private void updateNodePropertyValue(String nodeID, String propertyType, String newValue)
    {
        session.run( "match (node1) where node1.ID = '"+nodeID+ "' set node1."+propertyType+" = '"+newValue +"'" ) ;

        jTextPane1.setText(jTextPane1.getText()+"\n Updated a node property: Node: "+nodeID+" Property: "+propertyType+" New value: "+newValue); 
    }
    
    /**
     * updateRelationshipPropertyValue: update a given relationship's property value
     * @param node1ID
     * @param node2ID
     * @param relType
     * @param propertyType
     * @param newValue 
     */
    private void updateRelationshipPropertyValue(String node1ID, String node2ID, String relType, String propertyType, String newValue)
    {
        String matchString = "match (node1)- [r:"+relType+"]-> (node2) ";
        String whereString = " where node1.ID = '"+node1ID+"' and node2.ID = '"+node2ID+"' ";
        String setString = " set r."+propertyType+" = '"+newValue +"'";
        System.out.println(matchString+whereString+setString);
        session.run( matchString+whereString+setString);
    }
    
/**
 * getNodeSurroundings: used for displaying a particular node. Get all nodes within 2 edges from the given node. 
 * @param ID
 * @return array of strings containing nodes IDs and relationships - node 1 ID, node 1 name, node 2 ID, node 2 name, relationship type
 */
   
    private String[][] getNodeSurroundings(String ID)
    {
        String queryString = "match (n1) where n1.ID = '"+ID+"' return n1.name"; 
        String[] returned = getQueryResults(queryString);
        String nodeName = returned[0];
        String[][] arrayResults1;
        String[][] arrayResults2;
      
        if(!ID.equals(" "))
        {
            String rows = "";
            queryString = "match (n1)-[r]-(n2) where n1.ID = '"+ID+"' return n2.ID, n2.name, type(r)"; 
            
            StatementResult result = session.run( queryString );
            {
                while ( result.hasNext() )
                {
                    Record record = result.next();
                    Map<String,Object> row = record.asMap();
                    for ( Entry<String,Object> column : row.entrySet() )
                    {
                        rows += column.getValue() + "; ";
                    }
                   
                }
                
                String[] newOne = rows.split(";");
                int numberOfSurroundingNodes = newOne.length/3;
                  
                String[][] res = new String[numberOfSurroundingNodes+1][5];//result format: node1ID ;node1name ;node2ID ;node2name ;relationshipType
                res[0][0] = ID;
                res[0][1] = nodeName;
                res[0][2] = "";
                res[0][3] = "";
                res[0][4] = "";
                 
                for (int i = 1;i<numberOfSurroundingNodes+1; i++)
                {
                    res[i][0] = ID;//node 1 ID

                    res[i][1] = nodeName;//node 1 name

                    res[i][2] = newOne[3*(i-1)+2].trim();//node 2 ID

                    res[i][3] = newOne[3*(i-1)].trim();//node 2 name

                    res[i][4] = newOne[3*(i-1)+1].trim();// relationship type
                }
                
                arrayResults1 = res;
            
                rows = "";
                queryString = "match (n1)-[r]-(n2)-[r2]-(n3) where n1.ID = '"+ID+"' return n2.ID,n2.name, n3.ID,n3.name, type(r2)"; 
                
                StatementResult result2 = session.run( queryString );

                while ( result2.hasNext() )
                {
                    Record record = result2.next();
                    Map<String,Object> row = record.asMap();
                    for ( Entry<String,Object> column : row.entrySet() )
                    {
                        rows += column.getValue() + "; ";
                    }
                }
                System.out.println(rows);

                String[] newOne2 = rows.split(";");

                int numberOfSurroundingNodes2 = newOne2.length/5;

                String[][] res2 = new String[numberOfSurroundingNodes2][5];


                for (int i = 0;i<numberOfSurroundingNodes2; i++)
                {
                    res2[i][0] = newOne2[5*(i)].trim();//node 2 ID

                    res2[i][1] = newOne2[5*(i)+2].trim();// node 2 name

                    res2[i][2] = newOne2[5*(i)+1].trim();//node 3 ID

                    res2[i][3] = newOne2[5*(i)+4].trim();//node 3 name

                    res2[i][4] = newOne2[5*(i)+3].trim();//rel type

                }
                arrayResults2 = res2;
            }
            String[][] results = (String[][]) ArrayUtils.addAll(arrayResults1, arrayResults2);
            return results;
        }
        else
        {
            return new String[][]{{"", "", ""},{"", "", ""}};
        }
    }
    
    
  /**
   * getAllNodes: used for displaying graph. Get all node IDs+names in current graph.
   * @return node ID, node name, node type
   */
    private String[][] getAllNodes()
    {
        String queryString = "match (n) return n.ID, n.name, labels(n)";
        StatementResult result = session.run( queryString );
        {
            String rows = "";
            while( result.hasNext() )
            {
                Record record = result.next();
                Map<String,Object> row = record.asMap();
                for ( Entry<String,Object> column : row.entrySet() )
                {
                    rows += column.getValue() + "; ";
                }
            }
            String[] newOne = rows.split(";");
            
            int numberOfNodes = newOne.length/3;

            String[][] res = new String[numberOfNodes][3];
            String origName;
            
            for (int i = 0;i<numberOfNodes; i++)
            {
                res[i][0] = newOne[3*(i)].trim();//node ID?

                res[i][1] = newOne[3*(i)+2].trim();//node name?

                origName = newOne[3*(i)+1];
                origName = origName.replace("[","");
                origName = origName.replace("]","");

                res[i][2] = origName.trim();// type of node with brackets removed
            }
            
            return res;
       }
   }
       
        
   /**
   * getAllRelationships: used for displaying graph. Get all node relationships.
   * @return node 1 ID, relationship type, node 2 ID
   */       
    
    
    private String[][] getAllRelationships()
    {
        String queryString = "match (n1)-[r]->(n2) return n1.ID, type(r), n2.ID";
        StatementResult result = session.run( queryString );
        {
            String rows = "";
            while( result.hasNext() )
            {
                Record record = result.next();
                Map<String,Object> row = record.asMap();
                for ( Entry<String,Object> column : row.entrySet() )
                {
                    rows += column.getValue() + "; ";
                }
            }
                                 
            String[] newOne = rows.split(";");

            for (String newOne1 : newOne)
            {
                System.out.println(newOne1);
            }

            int numberOfRels = newOne.length/3;

            String[][] res = new String[numberOfRels][3];
            String origName = "";
            
            for (int i = 0;i<numberOfRels; i++)
            {
                res[i][0] = newOne[3*(i)].trim();//node 1 ID?

                res[i][1] = newOne[3*(i)+2].trim();//node 2 ID

                res[i][2] = newOne[3*(i)+1].trim();// relationship type

            }
           return res;
        }
    }
    
     /**
      * writeHTMLFileForGraphDisplay: writes graphdisplay.html to be used to display whole graph. Contains Sigma Javascript.
      * @param nodes
      * @param rels
      * @throws FileNotFoundException 
      */
     private void writeHTMLFileForGraphDisplay(String [][] nodes, String[][] rels) throws FileNotFoundException
     {
        int numberNodes = nodes.length;
        int numberEdges = rels.length;
        
        String initialText = "<html>\n" +
            "<head>\n" +
            "<style type=\"text/css\">\n" +
            "  #container {\n" +
            "    max-width: 400px;\n" +
            "    height: 400px;\n" +
            "    margin: auto;\n" +
            "  }\n" +
            "</style>\n" +
            "</head>\n" +
            "<body>\n" +
            "\n" +
            "<h1></h1>\n" +
            "\n" +

             "<button onclick=\"stopButtonClicked()\">Stop layout algorithm</button>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/sigma.core.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/conrad.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/utils/sigma.utils.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/utils/sigma.polyfills.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/sigma.settings.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/classes/sigma.classes.dispatcher.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/classes/sigma.classes.configurable.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/classes/sigma.classes.graph.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/classes/sigma.classes.camera.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/classes/sigma.classes.quad.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/classes/sigma.classes.edgequad.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/captors/sigma.captors.mouse.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/captors/sigma.captors.touch.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/sigma.renderers.canvas.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/sigma.renderers.webgl.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/sigma.renderers.svg.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/sigma.renderers.def.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/webgl/sigma.webgl.nodes.def.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/webgl/sigma.webgl.nodes.fast.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/webgl/sigma.webgl.edges.def.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/webgl/sigma.webgl.edges.fast.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/webgl/sigma.webgl.edges.arrow.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/canvas/sigma.canvas.labels.def.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/canvas/sigma.canvas.hovers.def.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/canvas/sigma.canvas.nodes.def.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/canvas/sigma.canvas.edges.def.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/canvas/sigma.canvas.edges.curve.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/canvas/sigma.canvas.edges.arrow.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/canvas/sigma.canvas.edges.curvedArrow.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/canvas/sigma.canvas.edgehovers.def.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/canvas/sigma.canvas.edgehovers.curve.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/canvas/sigma.canvas.edgehovers.arrow.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/canvas/sigma.canvas.edgehovers.curvedArrow.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/canvas/sigma.canvas.extremities.def.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/svg/sigma.svg.utils.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/svg/sigma.svg.nodes.def.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/svg/sigma.svg.edges.def.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/svg/sigma.svg.edges.curve.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/svg/sigma.svg.labels.def.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/svg/sigma.svg.hovers.def.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/middlewares/sigma.middlewares.rescale.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/middlewares/sigma.middlewares.copy.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/misc/sigma.misc.animation.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/misc/sigma.misc.bindEvents.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/misc/sigma.misc.bindDOMEvents.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/misc/sigma.misc.drawHovers.js\"></script>\n" +
            "\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/plugins/sigma.plugins.animate/sigma.plugins.animate.js\"></script>\n" +
            "\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/plugins/sigma.parsers.json/sigma.parsers.json.min.js\"></script>\n" +
            "\n" +
              "<script src=\"D:/JavaApplets/sigma.js-1.0.3/plugins/sigma.renderers.edgeLabels/settings.js\"></script>\n" +
             "<script src=\"D:/JavaApplets/sigma.js-1.0.3/plugins/sigma.renderers.edgeLabels/sigma.canvas.edges.labels.curve.js\"></script>\n" +
             "<script src=\"D:/JavaApplets/sigma.js-1.0.3/plugins/sigma.renderers.edgeLabels/sigma.canvas.edges.labels.curvedArrow.js\"></script>\n" +
             "<script src=\"D:/JavaApplets/sigma.js-1.0.3/plugins/sigma.renderers.edgeLabels/sigma.canvas.edges.labels.def.js\"></script>\n" +
                "<script src=\"D:/JavaApplets/sigma.js-1.0.3/plugins/sigma.layout.forceAtlas2/worker.js\"></script>\n" +
                "<script src=\"D:/JavaApplets/sigma.js-1.0.3/plugins/sigma.layout.forceAtlas2/supervisor.js\"></script>\n" +
                "\n" +
                "<script src=\"D:/JavaApplets/sigma.js-1.0.3/plugins/sigma.plugins.dragNodes/sigma.plugins.dragNodes.js\"></script>\n" +
                "\n" +
                "<script src=\"D:/JavaApplets/sigma.js-1.0.3/plugins/sigma.parsers.gexf/gexf-parser.js\"></script>\n" +
                "<script src=\"D:/JavaApplets/sigma.js-1.0.3/plugins/sigma.parsers.gexf/sigma.parsers.gexf.js\"></script>\n" +
                "<script src=\"D:/JavaApplets/sigma.js-1.0.3/plugins/sigma.plugins.filter/sigma.plugins.filter.js\"></script>" +
            "\n" +
            "<div id=\"container\">\n" +
            "    \n" +
            "  <style>\n" +
            "    #graph-container {\n" +
            "      top: 0;\n" +
            "      bottom: 0;\n" +
            "      left: 500;\n" +
            "      right: 0;\n" +
            "      position: absolute;\n" +
            "    }\n" +
            "  </style>\n" +
            "  <div id=\"graph-container\"></div>\n" +
            "</div>\n" +
            "<script>";
        
        
        String javaScriptText1 = "    var i,\n" +
            "    s,\n" +
            "    N = "+numberNodes+",\n" +
            "    E = "+numberEdges+",\n" +
            "    g = {\n" +
            "      nodes: [],\n" +
            "      edges: []\n" +
            "    };\n" +
            "// Generate a graph:\n" ;
                    
        String javaScriptText2 = "";
        for (int i = 0; i<numberNodes; i++)
        {
            javaScriptText2 = javaScriptText2+ "  g.nodes.push({\n" +
                "    id: '"+nodes[i][0]+"',\n" +
                "    label: '"+nodes[i][1]+"',\n" +
                "    x: Math.random(),\n" +
                "    y: Math.random(),\n" +
                "    size: 50,\n" +
                "    color: '#888'\n" +
                "  });\n" ;
         }
                  
        String javaScriptText3 = "";
        for (int j = 0; j<numberEdges; j++)
        {
            javaScriptText3 = javaScriptText3 +
                "  g.edges.push({\n" +
                "    id: 'e' + "+j+",\n" +
                "    source:'"+rels[j][0]+"' ,\n" +
                "    target:'"+rels[j][1]+"',\n" +
                "    label: '"+rels[j][2]+"',\n" +
                "    color: '#c6583e',\n" +
                "    size: 4,\n"+
                "    hover_color: '#000'\n" +
                "  });\n";
        }
                             
        javaScriptText3 = javaScriptText3 + "s = new sigma({ \n" +
            " graph: g,\n" +
            "  renderer: {\n" +
            "    container: document.getElementById('graph-container'),\n" +
            "    type: 'canvas'\n" +
            "  },\n" +
            "  settings: {\n" +
            "    edgeLabelSize: 'proportional',\n" +
            "enableEdgeHovering: true,\n"+
            "edgeHoverColor: '#999',\n"+
            "defaultEdgeHoverColor: '#000',\n"+
            "edgeHoverSizeRatio: 1,\n"+
            "edgeHoverExtremities: true\n"+
            "  }\n"+

            "}); " +
            "s.startForceAtlas2({worker: true, barnesHutOptimize: false});\n" +
            "\n" +
            "\n" +
            "function stopButtonClicked() {\n" +
            "    s.stopForceAtlas2({worker: true, barnesHutOptimize: false});\n" +
            "}\n" +
            "\n" +
            "// Initialize the dragNodes plugin:\n" +
            "var dragListener = sigma.plugins.dragNodes(s, s.renderers[0]);\n" +
            "dragListener.bind('startdrag', function(event) {\n" +
            "  console.log(event);\n" +
            "});\n" +
            "dragListener.bind('drag', function(event) {\n" +
            "  console.log(event);\n" +
            "});\n" +
            "dragListener.bind('drop', function(event) {\n" +
            "  console.log(event);\n" +
            "});\n" +
            "dragListener.bind('dragend', function(event) {\n" +
            "  console.log(event);\n" +
            "});";
        
        String finalText = "</script></html>";
        try (PrintWriter textOut = new PrintWriter("\\graphdisplay.html"))
        {
            textOut.print(initialText+javaScriptText1+javaScriptText2+javaScriptText3+finalText);
            textOut.flush();
        }
     }
        
     /**
      * writeHTMLFileForGraphDisplay: writes graphdisplay.html to be used to display specific node and its surrounding nodes within 2 edges. Takes 2D string array where first string is the ID of the central node. Contains Sigma Javascript.
      * @param nodes
      * @param rels
      * @throws FileNotFoundException 
      */    
    private void writeHTMLFile(String[][] result) throws FileNotFoundException           
    {
        int numberNodes = result.length;
        int numberEdges = result.length-1;
        
        String mainNodeID = result[0][0];
        
        String mainNodeName = result[0][1];

        String initialText = "<html>\n" +
            "<head>\n" +
            "<style type=\"text/css\">\n" +
            "  #container {\n" +
            "    max-width: 400px;\n" +
            "    height: 400px;\n" +
            "    margin: auto;\n" +
            "  }\n" +
            "</style>\n" +
            "</head>\n" +
            "<body>\n" +
            "\n" +
            "<h1></h1>\n" +
            "\n" +
            "<button onclick=\"stopButtonClicked()\">Stop layout algorithm</button>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/sigma.core.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/conrad.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/utils/sigma.utils.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/utils/sigma.polyfills.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/sigma.settings.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/classes/sigma.classes.dispatcher.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/classes/sigma.classes.configurable.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/classes/sigma.classes.graph.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/classes/sigma.classes.camera.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/classes/sigma.classes.quad.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/classes/sigma.classes.edgequad.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/captors/sigma.captors.mouse.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/captors/sigma.captors.touch.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/sigma.renderers.canvas.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/sigma.renderers.webgl.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/sigma.renderers.svg.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/sigma.renderers.def.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/webgl/sigma.webgl.nodes.def.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/webgl/sigma.webgl.nodes.fast.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/webgl/sigma.webgl.edges.def.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/webgl/sigma.webgl.edges.fast.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/webgl/sigma.webgl.edges.arrow.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/canvas/sigma.canvas.labels.def.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/canvas/sigma.canvas.hovers.def.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/canvas/sigma.canvas.nodes.def.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/canvas/sigma.canvas.edges.def.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/canvas/sigma.canvas.edges.curve.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/canvas/sigma.canvas.edges.arrow.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/canvas/sigma.canvas.edges.curvedArrow.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/canvas/sigma.canvas.edgehovers.def.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/canvas/sigma.canvas.edgehovers.curve.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/canvas/sigma.canvas.edgehovers.arrow.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/canvas/sigma.canvas.edgehovers.curvedArrow.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/canvas/sigma.canvas.extremities.def.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/svg/sigma.svg.utils.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/svg/sigma.svg.nodes.def.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/svg/sigma.svg.edges.def.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/svg/sigma.svg.edges.curve.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/svg/sigma.svg.labels.def.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/renderers/svg/sigma.svg.hovers.def.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/middlewares/sigma.middlewares.rescale.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/middlewares/sigma.middlewares.copy.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/misc/sigma.misc.animation.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/misc/sigma.misc.bindEvents.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/misc/sigma.misc.bindDOMEvents.js\"></script>\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/src/misc/sigma.misc.drawHovers.js\"></script>\n" +
            "\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/plugins/sigma.plugins.animate/sigma.plugins.animate.js\"></script>\n" +
            "\n" +
            "<script src=\"D:/JavaApplets/sigma.js-1.0.3/plugins/sigma.parsers.json/sigma.parsers.json.min.js\"></script>\n" +
            "\n" +
              "<script src=\"D:/JavaApplets/sigma.js-1.0.3/plugins/sigma.renderers.edgeLabels/settings.js\"></script>\n" +
             "<script src=\"D:/JavaApplets/sigma.js-1.0.3/plugins/sigma.renderers.edgeLabels/sigma.canvas.edges.labels.curve.js\"></script>\n" +
             "<script src=\"D:/JavaApplets/sigma.js-1.0.3/plugins/sigma.renderers.edgeLabels/sigma.canvas.edges.labels.curvedArrow.js\"></script>\n" +
             "<script src=\"D:/JavaApplets/sigma.js-1.0.3/plugins/sigma.renderers.edgeLabels/sigma.canvas.edges.labels.def.js\"></script>\n" +
                "<script src=\"D:/JavaApplets/sigma.js-1.0.3/plugins/sigma.layout.forceAtlas2/worker.js\"></script>\n" +
                "<script src=\"D:/JavaApplets/sigma.js-1.0.3/plugins/sigma.layout.forceAtlas2/supervisor.js\"></script>\n" +
                "\n" +
                "<script src=\"D:/JavaApplets/sigma.js-1.0.3/plugins/sigma.plugins.dragNodes/sigma.plugins.dragNodes.js\"></script>\n" +
                "\n" +
                "<script src=\"D:/JavaApplets/sigma.js-1.0.3/plugins/sigma.parsers.gexf/gexf-parser.js\"></script>\n" +
                "<script src=\"D:/JavaApplets/sigma.js-1.0.3/plugins/sigma.parsers.gexf/sigma.parsers.gexf.js\"></script>\n" +
                "<script src=\"D:/JavaApplets/sigma.js-1.0.3/plugins/sigma.plugins.filter/sigma.plugins.filter.js\"></script>" +
            "\n" +
            "<div id=\"container\">\n" +
            "    \n" +
            "  <style>\n" +
            "    #graph-container {\n" +
            "      top: 0;\n" +
            "      bottom: 0;\n" +
            "      left: 500;\n" +
            "      right: 0;\n" +
            "      position: absolute;\n" +
            "    }\n" +
            "  </style>\n" +
            "  <div id=\"graph-container\"></div>\n" +
            "</div>\n" +
            "<script>";
        
        
        String javaScriptText1 = "    var i,\n" +
            "    s,\n" +
            "    N = "+numberNodes+",\n" +
            "    E = "+numberEdges+",\n" +
            "    g = {\n" +
            "      nodes: [],\n" +
            "      edges: []\n" +
            "    };\n" +
            "// Generate a graph:\n" +
            "  g.nodes.push({\n" +
            "    id: '"+mainNodeID+"',\n" +
            "    label: '"+mainNodeName+"' ,\n" +
            "    x: 0.5,\n" +
            "    y: 0.5,\n" +
            "    size: 50,\n" +
            "    color: '#ec5148'\n" +
            "  });\n" ;
                  
        String javaScriptText2 = "";
        List<String> usedIDs = new ArrayList<String>();
        for (int i = 1; i<numberNodes; i++)
        {
            if (!usedIDs.contains(result[i][2])){
            javaScriptText2 = javaScriptText2+ "  g.nodes.push({\n" +
                "    id: '"+result[i][2]+"',\n" +
                "    label: '"+result[i][3]+"',\n" +
                "    x: Math.random(),\n" +
                "    y: Math.random(),\n" +
                "    size: 50,\n" +
                "    color: '#888'\n" +
                "  });\n" ;

            usedIDs.add(result[i][2]);
        }      
        
        javaScriptText2 = javaScriptText2 + "  g.edges.push({\n" +
            "    id: 'e' + "+i+",\n" +
            "    source: '"+result[i][0]+"' ,\n" +
            "    target: '"+result[i][2]+"',\n" +
            "    label: '"+result[i][4]+"',\n" +
            "    size: 4,\n"+
            "    hover_color: '#000',\n" +
            "    color: '#c6583e'\n" +
            "  });\n";
        }
                             
            javaScriptText2 = javaScriptText2 + "s = new sigma({ \n" +
                " graph: g,\n" +
                "  renderer: {\n" +
                "    container: document.getElementById('graph-container'),\n" +
                "    type: 'canvas'\n" +
                "  },\n" +
                "  settings: {\n" +
                "    edgeLabelSize: 'proportional',\n" +
                "enableEdgeHovering: true,\n"+
                "edgeHoverColor: '#999',\n"+
                "defaultEdgeHoverColor: '#000',\n"+
                "edgeHoverSizeRatio: 1,\n"+
                "edgeHoverExtremities: true\n"+
                "  }\n"+
                             
                "}); " +
                "s.startForceAtlas2({worker: true, barnesHutOptimize: false});\n" +
                "\n" +
                "\n" +
                "function stopButtonClicked() {\n" +
                "    s.stopForceAtlas2({worker: true, barnesHutOptimize: false});\n" +
                "}\n" +
                "\n" +
                "// Initialize the dragNodes plugin:\n" +
                "var dragListener = sigma.plugins.dragNodes(s, s.renderers[0]);\n" +
                "dragListener.bind('startdrag', function(event) {\n" +
                "  console.log(event);\n" +
                "});\n" +
                "dragListener.bind('drag', function(event) {\n" +
                "  console.log(event);\n" +
                "});\n" +
                "dragListener.bind('drop', function(event) {\n" +
                "  console.log(event);\n" +
                "});\n" +
                "dragListener.bind('dragend', function(event) {\n" +
                "  console.log(event);\n" +
                "});";
          
        String finalText = "</script></html>";
        try (PrintWriter textOut = new PrintWriter("\\graphdisplay.html")) 
        {
            textOut.print(initialText+javaScriptText1+javaScriptText2+finalText);
            textOut.flush();
        }
    }
    
    

    /**
     * Creates new form GraphDatabaseForm. 
     * @param driver
     * @param session 
     */
    public GraphDatabaseForm(Driver driver, Session session)
    {
        this.driver = driver;
        this.session = session;
        initComponents();
        customInitComponents();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        FileChooser = new javax.swing.JFileChooser();
        jPanel3 = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        ComboBoxType2 = new javax.swing.JComboBox();
        jLabel4 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        jTextField2 = new javax.swing.JTextField();
        ButtonDeleteNode = new javax.swing.JButton();
        ComboBoxProperty2 = new javax.swing.JComboBox();
        TextField2 = new javax.swing.JTextField();
        ComboBoxName1 = new javax.swing.JComboBox();
        jLabel16 = new javax.swing.JLabel();
        ComboBoxID1 = new javax.swing.JComboBox();
        ButtonEditProperties1 = new javax.swing.JButton();
        ButtonViewNode = new javax.swing.JButton();
        jPanel5 = new javax.swing.JPanel();
        jLabel9 = new javax.swing.JLabel();
        ComboBoxRelationship1 = new javax.swing.JComboBox();
        ButtonDeleteRelationship = new javax.swing.JButton();
        jLabel13 = new javax.swing.JLabel();
        ComboBoxProperty3 = new javax.swing.JComboBox();
        ButtonEditProperties2 = new javax.swing.JButton();
        TextField3 = new javax.swing.JTextField();
        jPanel6 = new javax.swing.JPanel();
        jLabel8 = new javax.swing.JLabel();
        ComboBoxType3 = new javax.swing.JComboBox();
        jLabel12 = new javax.swing.JLabel();
        ComboBoxName3 = new javax.swing.JComboBox();
        jLabel17 = new javax.swing.JLabel();
        ComboBoxID2 = new javax.swing.JComboBox();
        ButtonEditProperties3 = new javax.swing.JButton();
        jLabel2 = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        jScrollPane1 = new javax.swing.JScrollPane();
        jTextPane1 = new javax.swing.JTextPane();
        jPanel2 = new javax.swing.JPanel();
        ComboBoxType1 = new javax.swing.JComboBox();
        TextField1 = new javax.swing.JTextField();
        jLabel10 = new javax.swing.JLabel();
        jLabel11 = new javax.swing.JLabel();
        ComboBoxProperty1 = new javax.swing.JComboBox();
        ButtonAddProperty1 = new javax.swing.JButton();
        ButtonCreateNode = new javax.swing.JButton();
        ButtonClearProperties1 = new javax.swing.JButton();
        ButtonAddAllProperties1 = new javax.swing.JButton();
        jPanel4 = new javax.swing.JPanel();
        jLabel5 = new javax.swing.JLabel();
        ComboBoxRelationship2 = new javax.swing.JComboBox();
        ButtonCreateRelationship = new javax.swing.JButton();
        jLabel6 = new javax.swing.JLabel();
        TextField4 = new javax.swing.JTextField();
        ComboBoxProperty4 = new javax.swing.JComboBox();
        ButtonAddProperty2 = new javax.swing.JButton();
        ButtonAddAllProperties2 = new javax.swing.JButton();
        ButtonClearProperties2 = new javax.swing.JButton();
        jPanel7 = new javax.swing.JPanel();
        jLabel14 = new javax.swing.JLabel();
        ComboBoxType4 = new javax.swing.JComboBox();
        jLabel15 = new javax.swing.JLabel();
        ComboBoxName4 = new javax.swing.JComboBox();
        jLabel18 = new javax.swing.JLabel();
        ComboBoxID3 = new javax.swing.JComboBox();
        ButtonEditProperties4 = new javax.swing.JButton();
        jPanel8 = new javax.swing.JPanel();
        jLabel20 = new javax.swing.JLabel();
        jLabel21 = new javax.swing.JLabel();
        jLabel22 = new javax.swing.JLabel();
        TextFieldLoadNodes = new javax.swing.JTextField();
        ButtonAddNodes = new javax.swing.JButton();
        ButtonBrowse1 = new javax.swing.JButton();
        TextFieldLoadRelationships = new javax.swing.JTextField();
        ButtonBrowse2 = new javax.swing.JButton();
        ButtonAddRelationships = new javax.swing.JButton();
        TextFieldLoadDB = new javax.swing.JTextField();
        ButtonBrowseDB = new javax.swing.JButton();
        ButtonLoadDB = new javax.swing.JButton();
        ButtonViewGraph = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jPanel3.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Node 1", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 0, 18))); // NOI18N

        jLabel3.setText("Node Type:");

        ComboBoxType2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ComboBoxType2ActionPerformed(evt);
            }
        });

        jLabel4.setText("Node Name:");

        jLabel7.setText("Node Properties:");

        jTextField2.setText("jTextField2");

        ButtonDeleteNode.setText("Delete Node");
        ButtonDeleteNode.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ButtonDeleteNodeActionPerformed(evt);
            }
        });

        ComboBoxProperty2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ComboBoxProperty2ActionPerformed(evt);
            }
        });

        TextField2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                TextField2ActionPerformed(evt);
            }
        });

        ComboBoxName1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ComboBoxName1ActionPerformed(evt);
            }
        });

        jLabel16.setText("Node ID:");

        ComboBoxID1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ComboBoxID1ActionPerformed(evt);
            }
        });

        ButtonEditProperties1.setText("Edit Properties");
        ButtonEditProperties1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ButtonEditProperties1ActionPerformed(evt);
            }
        });

        ButtonViewNode.setText("View Node");
        ButtonViewNode.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ButtonViewNodeActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel3)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                                .addComponent(ComboBoxType2, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.PREFERRED_SIZE, 250, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(ButtonDeleteNode, javax.swing.GroupLayout.PREFERRED_SIZE, 150, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(ButtonViewNode, javax.swing.GroupLayout.PREFERRED_SIZE, 150, javax.swing.GroupLayout.PREFERRED_SIZE)
                                    .addComponent(ButtonEditProperties1, javax.swing.GroupLayout.PREFERRED_SIZE, 152, javax.swing.GroupLayout.PREFERRED_SIZE)))
                            .addComponent(jLabel4)
                            .addComponent(ComboBoxName1, javax.swing.GroupLayout.PREFERRED_SIZE, 250, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(266, 266, 266)
                        .addComponent(jTextField2, javax.swing.GroupLayout.PREFERRED_SIZE, 200, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jLabel16)
                    .addComponent(TextField2, javax.swing.GroupLayout.PREFERRED_SIZE, 250, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel7)
                    .addComponent(ComboBoxProperty2, javax.swing.GroupLayout.PREFERRED_SIZE, 250, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(ComboBoxID1, javax.swing.GroupLayout.PREFERRED_SIZE, 250, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addGap(105, 105, 105)
                        .addComponent(jTextField2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addGap(33, 33, 33)
                        .addComponent(jLabel3)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(ComboBoxType2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(jLabel4)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(ComboBoxName1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(18, 18, 18)
                .addComponent(jLabel16)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ComboBoxID1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(jLabel7, javax.swing.GroupLayout.PREFERRED_SIZE, 16, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ComboBoxProperty2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(TextField2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(ButtonEditProperties1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ButtonViewNode)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(ButtonDeleteNode)
                .addGap(25, 25, 25))
        );

        jPanel5.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Current Relationships", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 0, 18))); // NOI18N

        jLabel9.setText("Relationship Type:");

        ComboBoxRelationship1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ComboBoxRelationship1ActionPerformed(evt);
            }
        });

        ButtonDeleteRelationship.setText("Delete Relationship");
        ButtonDeleteRelationship.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ButtonDeleteRelationshipActionPerformed(evt);
            }
        });

        jLabel13.setText("Relationship Properties:");

        ComboBoxProperty3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ComboBoxProperty3ActionPerformed(evt);
            }
        });

        ButtonEditProperties2.setText("Edit Properties");
        ButtonEditProperties2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ButtonEditProperties2ActionPerformed(evt);
            }
        });

        TextField3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                TextField3ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(ButtonDeleteRelationship)
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel9)
                            .addComponent(jLabel13))
                        .addGap(30, 30, 30)
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(ButtonEditProperties2, javax.swing.GroupLayout.PREFERRED_SIZE, 145, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addComponent(ComboBoxProperty3, javax.swing.GroupLayout.PREFERRED_SIZE, 250, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(ComboBoxRelationship1, javax.swing.GroupLayout.PREFERRED_SIZE, 250, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addComponent(TextField3, javax.swing.GroupLayout.PREFERRED_SIZE, 250, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                .addGap(0, 26, Short.MAX_VALUE))
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addGap(30, 30, 30)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(ComboBoxRelationship1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel9))
                .addGap(18, 18, 18)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel13)
                    .addComponent(ComboBoxProperty3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(TextField3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(ButtonEditProperties2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 60, Short.MAX_VALUE)
                .addComponent(ButtonDeleteRelationship)
                .addContainerGap())
        );

        jPanel6.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Node 2", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 0, 18))); // NOI18N

        jLabel8.setText("Node Type:");

        ComboBoxType3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ComboBoxType3ActionPerformed(evt);
            }
        });

        jLabel12.setText("Node Name:");

        ComboBoxName3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ComboBoxName3ActionPerformed(evt);
            }
        });

        jLabel17.setText("Node ID:");

        ComboBoxID2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ComboBoxID2ActionPerformed(evt);
            }
        });

        ButtonEditProperties3.setText("Edit Properties");
        ButtonEditProperties3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ButtonEditProperties3ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel6Layout = new javax.swing.GroupLayout(jPanel6);
        jPanel6.setLayout(jPanel6Layout);
        jPanel6Layout.setHorizontalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addGap(21, 21, 21)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jLabel17)
                    .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addComponent(ComboBoxType3, 0, 250, Short.MAX_VALUE)
                        .addComponent(jLabel8)
                        .addComponent(jLabel12)
                        .addComponent(ComboBoxName3, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel6Layout.createSequentialGroup()
                .addContainerGap(21, Short.MAX_VALUE)
                .addGroup(jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(ComboBoxID2, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 250, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(ButtonEditProperties3, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 150, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap())
        );
        jPanel6Layout.setVerticalGroup(
            jPanel6Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel6Layout.createSequentialGroup()
                .addGap(27, 27, 27)
                .addComponent(jLabel8)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ComboBoxType3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel12)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ComboBoxName3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel17)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ComboBoxID2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(ButtonEditProperties3)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jLabel2.setFont(new java.awt.Font("Tahoma", 0, 18)); // NOI18N
        jLabel2.setText("Database:");

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Output", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 0, 18))); // NOI18N

        jScrollPane1.setViewportView(jTextPane1);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1)
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 186, Short.MAX_VALUE))
        );

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Create Node", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 0, 18))); // NOI18N

        ComboBoxType1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ComboBoxType1ActionPerformed(evt);
            }
        });

        jLabel10.setText("Node Type:");

        jLabel11.setText("Node Properties:");

        ButtonAddProperty1.setText("Add Property");
        ButtonAddProperty1.setActionCommand("Add Attribute");
        ButtonAddProperty1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ButtonAddProperty1ActionPerformed(evt);
            }
        });

        ButtonCreateNode.setText("Create Node");
        ButtonCreateNode.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ButtonCreateNodeActionPerformed(evt);
            }
        });

        ButtonClearProperties1.setText("Clear Properties");
        ButtonClearProperties1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ButtonClearProperties1ActionPerformed(evt);
            }
        });

        ButtonAddAllProperties1.setText("Add All Properties");
        ButtonAddAllProperties1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ButtonAddAllProperties1ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(ButtonAddAllProperties1, javax.swing.GroupLayout.PREFERRED_SIZE, 150, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addComponent(jLabel10)
                        .addComponent(jLabel11)
                        .addComponent(ComboBoxType1, 0, 250, Short.MAX_VALUE)
                        .addComponent(ComboBoxProperty1, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(TextField1))
                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addComponent(ButtonAddProperty1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(ButtonCreateNode, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(ButtonClearProperties1, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 150, Short.MAX_VALUE)))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(31, 31, 31)
                .addComponent(jLabel10)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ComboBoxType1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(28, 28, 28)
                .addComponent(jLabel11)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ComboBoxProperty1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(TextField1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(ButtonAddProperty1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ButtonAddAllProperties1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ButtonClearProperties1)
                .addGap(37, 37, 37)
                .addComponent(ButtonCreateNode)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel4.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Create Relationship", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 0, 18))); // NOI18N

        jLabel5.setText("Relationship Type:");

        ComboBoxRelationship2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ComboBoxRelationship2ActionPerformed(evt);
            }
        });

        ButtonCreateRelationship.setText("Create Relationship");
        ButtonCreateRelationship.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ButtonCreateRelationshipActionPerformed(evt);
            }
        });

        jLabel6.setText("Relationship Properties:");

        ButtonAddProperty2.setText("Add Property");
        ButtonAddProperty2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ButtonAddProperty2ActionPerformed(evt);
            }
        });

        ButtonAddAllProperties2.setText("Add All Properties");
        ButtonAddAllProperties2.setActionCommand("ButtonAddAllProperties2");
        ButtonAddAllProperties2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ButtonAddAllProperties2ActionPerformed(evt);
            }
        });

        ButtonClearProperties2.setText("Clear Properties");

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(jPanel4Layout.createSequentialGroup()
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel5)
                            .addComponent(jLabel6))
                        .addGap(28, 28, 28)
                        .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(ComboBoxProperty4, javax.swing.GroupLayout.PREFERRED_SIZE, 250, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(ComboBoxRelationship2, javax.swing.GroupLayout.PREFERRED_SIZE, 250, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(TextField4, javax.swing.GroupLayout.PREFERRED_SIZE, 250, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addComponent(ButtonCreateRelationship, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(ButtonAddProperty2, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(ButtonClearProperties2, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(ButtonAddAllProperties2, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addGap(0, 28, Short.MAX_VALUE))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addGap(24, 24, 24)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel5)
                    .addComponent(ComboBoxRelationship2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(26, 26, 26)
                .addGroup(jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel6)
                    .addComponent(ComboBoxProperty4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addComponent(TextField4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(ButtonAddProperty2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(ButtonAddAllProperties2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(ButtonClearProperties2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(ButtonCreateRelationship)
                .addContainerGap())
        );

        jPanel7.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Node 2", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 0, 18))); // NOI18N

        jLabel14.setText("Node Type:");

        ComboBoxType4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ComboBoxType4ActionPerformed(evt);
            }
        });

        jLabel15.setText("Node Name:");

        ComboBoxName4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ComboBoxName4ActionPerformed(evt);
            }
        });

        jLabel18.setText("Node ID:");

        ComboBoxID3.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ComboBoxID3ActionPerformed(evt);
            }
        });

        ButtonEditProperties4.setText("Edit Properties");
        ButtonEditProperties4.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ButtonEditProperties4ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel7Layout = new javax.swing.GroupLayout(jPanel7);
        jPanel7.setLayout(jPanel7Layout);
        jPanel7Layout.setHorizontalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel7Layout.createSequentialGroup()
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jLabel18)
                            .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jLabel15)
                                    .addComponent(jLabel14)
                                    .addComponent(ComboBoxType4, javax.swing.GroupLayout.PREFERRED_SIZE, 250, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addComponent(ComboBoxName4, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 250, javax.swing.GroupLayout.PREFERRED_SIZE))))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel7Layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addGroup(jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(ComboBoxID3, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 250, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(ButtonEditProperties4, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 150, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addContainerGap())
        );
        jPanel7Layout.setVerticalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addGap(28, 28, 28)
                .addComponent(jLabel14)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ComboBoxType4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(jLabel15)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ComboBoxName4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(26, 26, 26)
                .addComponent(jLabel18)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ComboBoxID3, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(29, 29, 29)
                .addComponent(ButtonEditProperties4)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel8.setBorder(javax.swing.BorderFactory.createTitledBorder(null, "Bulk Updates", javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Tahoma", 0, 18))); // NOI18N

        jLabel21.setText("Load Nodes:");
        jLabel21.setToolTipText("");

        jLabel22.setText("Load Relationships:");
        jLabel22.setToolTipText("");

        ButtonAddNodes.setText("Add Nodes");
        ButtonAddNodes.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ButtonAddNodesActionPerformed(evt);
            }
        });

        ButtonBrowse1.setText("Browse");
        ButtonBrowse1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ButtonBrowse1ActionPerformed(evt);
            }
        });

        ButtonBrowse2.setText("Browse");
        ButtonBrowse2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ButtonBrowse2ActionPerformed(evt);
            }
        });

        ButtonAddRelationships.setText("Add Relationships");
        ButtonAddRelationships.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ButtonAddRelationshipsActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel8Layout = new javax.swing.GroupLayout(jPanel8);
        jPanel8.setLayout(jPanel8Layout);
        jPanel8Layout.setHorizontalGroup(
            jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel8Layout.createSequentialGroup()
                .addGroup(jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel8Layout.createSequentialGroup()
                        .addContainerGap()
                        .addGroup(jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel8Layout.createSequentialGroup()
                                .addGap(0, 0, Short.MAX_VALUE)
                                .addComponent(jLabel20))
                            .addGroup(jPanel8Layout.createSequentialGroup()
                                .addComponent(TextFieldLoadRelationships, javax.swing.GroupLayout.PREFERRED_SIZE, 223, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(ButtonBrowse2, javax.swing.GroupLayout.DEFAULT_SIZE, 94, Short.MAX_VALUE))
                            .addGroup(jPanel8Layout.createSequentialGroup()
                                .addComponent(jLabel22)
                                .addGap(0, 0, Short.MAX_VALUE))
                            .addGroup(jPanel8Layout.createSequentialGroup()
                                .addComponent(TextFieldLoadNodes, javax.swing.GroupLayout.PREFERRED_SIZE, 223, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(ButtonBrowse1, javax.swing.GroupLayout.DEFAULT_SIZE, 94, Short.MAX_VALUE))))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel8Layout.createSequentialGroup()
                        .addGap(0, 0, Short.MAX_VALUE)
                        .addGroup(jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(ButtonAddNodes, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 143, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(ButtonAddRelationships, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.PREFERRED_SIZE, 143, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addContainerGap())
            .addGroup(jPanel8Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel21)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel8Layout.setVerticalGroup(
            jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel8Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel21)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(TextFieldLoadNodes, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(ButtonBrowse1))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jLabel20)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(ButtonAddNodes)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel22)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(TextFieldLoadRelationships, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(ButtonBrowse2))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(ButtonAddRelationships)
                .addGap(370, 370, 370))
        );

        TextFieldLoadDB.setText("D:\\Databases\\JTSDatabase");
        TextFieldLoadDB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                TextFieldLoadDBActionPerformed(evt);
            }
        });

        ButtonBrowseDB.setText("Browse");
        ButtonBrowseDB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ButtonBrowseDBActionPerformed(evt);
            }
        });

        ButtonLoadDB.setText("Load");
        ButtonLoadDB.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ButtonLoadDBActionPerformed(evt);
            }
        });

        ButtonViewGraph.setText("View Graph");
        ButtonViewGraph.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ButtonViewGraphActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addComponent(jPanel8, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, 300, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18))
                            .addGroup(layout.createSequentialGroup()
                                .addGap(14, 14, 14)
                                .addComponent(jLabel2)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(TextFieldLoadDB, javax.swing.GroupLayout.PREFERRED_SIZE, 305, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(13, 13, 13)
                                .addComponent(ButtonBrowseDB, javax.swing.GroupLayout.PREFERRED_SIZE, 111, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(ButtonLoadDB, javax.swing.GroupLayout.PREFERRED_SIZE, 111, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(layout.createSequentialGroup()
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(jPanel5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(jPanel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                    .addComponent(jPanel6, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                    .addComponent(jPanel7, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                            .addComponent(ButtonViewGraph, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE))))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addGap(27, 27, 27)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel2)
                    .addComponent(TextFieldLoadDB, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(ButtonBrowseDB)
                    .addComponent(ButtonLoadDB)
                    .addComponent(ButtonViewGraph))
                .addGap(30, 30, 30)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                            .addComponent(jPanel6, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jPanel5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jPanel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(jPanel7, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addComponent(jPanel3, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addGroup(javax.swing.GroupLayout.Alignment.LEADING, layout.createSequentialGroup()
                        .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel8, javax.swing.GroupLayout.PREFERRED_SIZE, 247, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    /**
     * customInitComponents
    */
    private void customInitComponents()
    {
        createNodePropertyList = new ArrayList<>();
        createRelationshipPropertyList = new ArrayList<>();
        FileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        this.setTitle("Graph Database Updates");
    }
    
     /**
      * ButtonAddProperty1ActionPerformed: add new property to list of new node properties
      * @param evt 
      */
    
    private void ButtonAddProperty1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ButtonAddProperty1ActionPerformed
     
        //add new property to list    
        String[] s = {ComboBoxProperty1.getSelectedItem().toString(),TextField1.getText()};
        createNodePropertyList.add(s);
        jTextPane1.setText(jTextPane1.getText()+"\n Added node property: "+ComboBoxProperty1.getSelectedItem().toString()+": "+TextField1.getText());

        //add possible new property type to hashmap of node property types
        List<String> props = new ArrayList(Arrays.asList(nodeProperties.get(ComboBoxType1.getSelectedItem().toString())));//get properties for this node type and turn into list to be searched
        if (!props.contains(ComboBoxProperty1.getSelectedItem().toString()))//if current property is not already in the list   
        {
           props.add(ComboBoxProperty1.getSelectedItem().toString());
           nodeProperties.replace(ComboBoxType1.getSelectedItem().toString(), props.toArray(new String[props.size()]));
        }
    }//GEN-LAST:event_ButtonAddProperty1ActionPerformed

    /**
     * ComboBoxType1ActionPerformed: 
     * @param evt 
     */
    private void ComboBoxType1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ComboBoxType1ActionPerformed
       //reset node properties    
       ComboBoxProperty1.setModel(new javax.swing.DefaultComboBoxModel(getPropertyTypes(ComboBoxType1.getSelectedItem().toString())));
    }//GEN-LAST:event_ComboBoxType1ActionPerformed

    /**
     * ButtonCreateNodeActionPerformed:
     * @param evt 
     */
    private void ButtonCreateNodeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ButtonCreateNodeActionPerformed

        String IDString = getNewID();
        String propertyList = "ID: '"+IDString+"',";

        for (int i = 0; i<createNodePropertyList.size(); i++)
        {
            propertyList = propertyList +createNodePropertyList.get(i)[0]+": '"+createNodePropertyList.get(i)[1]+"',";
        }

        propertyList = propertyList.substring(0,propertyList.length()-1);//remove last comma
        session.run( "create (newNode:"+ ComboBoxType1.getSelectedItem().toString()+"{"+propertyList+"})") ;
        
        jTextPane1.setText(jTextPane1.getText()+"\n Created new node: "+ComboBoxType1.getSelectedItem().toString()+" "+propertyList);
        createNodePropertyList.clear();
         
        refreshNode1Panel();
        refreshPanels();
    }//GEN-LAST:event_ButtonCreateNodeActionPerformed

    /**
     * ButtonClearProperties1ActionPerformed:
     * @param evt 
     */
    private void ButtonClearProperties1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ButtonClearProperties1ActionPerformed
      
        createNodePropertyList.clear();
        jTextPane1.setText(jTextPane1.getText()+"\n Cleared current list of properties for new node.");
    }//GEN-LAST:event_ButtonClearProperties1ActionPerformed

    /**
     * ComboBoxType2ActionPerformed:
     * @param evt 
     */
    private void ComboBoxType2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ComboBoxType2ActionPerformed
        
        String [] names = getNodeNames((String) ComboBoxType2.getSelectedItem());
        ComboBoxName1.setModel(new javax.swing.DefaultComboBoxModel(names));
        ComboBoxID1.setModel(new javax.swing.DefaultComboBoxModel(getNodeIDs(ComboBoxType2.getSelectedItem().toString(), ComboBoxName1.getSelectedItem().toString())));
        ComboBoxProperty2.setModel(new javax.swing.DefaultComboBoxModel(getPropertyTypesfromID(ComboBoxID1.getSelectedItem().toString())));
        TextField2.setText(getNodePropertyValue(ComboBoxProperty2.getSelectedItem().toString(), ComboBoxID1.getSelectedItem().toString())[0]);

        refreshPanels();
    }//GEN-LAST:event_ComboBoxType2ActionPerformed

    /**
     * ComboBoxName1ActionPerformed:
     * @param evt 
     */
    private void ComboBoxName1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ComboBoxName1ActionPerformed
        
        ComboBoxID1.setModel(new javax.swing.DefaultComboBoxModel(getNodeIDs(ComboBoxType2.getSelectedItem().toString(), ComboBoxName1.getSelectedItem().toString())));
        ComboBoxProperty2.setModel(new javax.swing.DefaultComboBoxModel(getPropertyTypesfromID(ComboBoxID1.getSelectedItem().toString())));
        TextField2.setText(getNodePropertyValue(ComboBoxProperty2.getSelectedItem().toString(), ComboBoxID1.getSelectedItem().toString())[0]);

        refreshPanels();
    }//GEN-LAST:event_ComboBoxName1ActionPerformed

    /**
     * ComboBoxRelationship1ActionPerformed:
     * @param evt 
     */
    private void ComboBoxRelationship1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ComboBoxRelationship1ActionPerformed
         
        if(!ComboBoxRelationship1.getSelectedItem().equals(" ")) 
        {
            ComboBoxProperty3.setModel(new javax.swing.DefaultComboBoxModel(relationshipProperties.get(ComboBoxRelationship1.getSelectedItem())));

            ComboBoxType3.setModel(new javax.swing.DefaultComboBoxModel(getNode2Types(ComboBoxRelationship1.getSelectedItem().toString())));
            ComboBoxName3.setModel(new javax.swing.DefaultComboBoxModel(getNode2Names(ComboBoxID1.getSelectedItem().toString(), ComboBoxRelationship1.getSelectedItem().toString())));
            ComboBoxID2.setModel(new javax.swing.DefaultComboBoxModel(getNode2IDs(ComboBoxID1.getSelectedItem().toString(), ComboBoxRelationship1.getSelectedItem().toString(), ComboBoxName3.getSelectedItem().toString())));

            TextField3.setText(getRelationshipPropertyValue(ComboBoxID1.getSelectedItem().toString(), ComboBoxID2.getSelectedItem().toString(), ComboBoxRelationship1.getSelectedItem().toString(), ComboBoxProperty3.getSelectedItem().toString())[0]);
        }
    }//GEN-LAST:event_ComboBoxRelationship1ActionPerformed

    /**
     * ComboBoxRelationship2ActionPerformed:
     * @param evt 
     */
    private void ComboBoxRelationship2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ComboBoxRelationship2ActionPerformed
        
        if(!ComboBoxRelationship2.getSelectedItem().equals(" "))
        {
            ComboBoxProperty4.setModel(new javax.swing.DefaultComboBoxModel(relationshipProperties.get(ComboBoxRelationship2.getSelectedItem())));
            refreshNode2Panel2();
        }    
    }//GEN-LAST:event_ComboBoxRelationship2ActionPerformed

    /**
     * ComboBoxType4ActionPerformed:
     * @param evt 
     */
    private void ComboBoxType4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ComboBoxType4ActionPerformed
       
        ComboBoxName4.setModel(new javax.swing.DefaultComboBoxModel(getNodeNames(ComboBoxType4.getSelectedItem().toString()))); 
        ComboBoxID3.setModel(new javax.swing.DefaultComboBoxModel(getNodeIDs(ComboBoxType4.getSelectedItem().toString(), ComboBoxName4.getSelectedItem().toString())));
    }//GEN-LAST:event_ComboBoxType4ActionPerformed

    /**
     * ButtonAddProperty2ActionPerformed:
     * @param evt 
     */
    private void ButtonAddProperty2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ButtonAddProperty2ActionPerformed
        
        String[] s = {ComboBoxProperty4.getSelectedItem().toString(),TextField4.getText()};
        createRelationshipPropertyList.add(s);
        jTextPane1.setText(jTextPane1.getText()+"\n Added relationship property: "+ComboBoxProperty4.getSelectedItem().toString()+": "+TextField4.getText());

        //add possible new property type to hashmap of relationship property types
        List<String> props = new ArrayList(Arrays.asList(relationshipProperties.get(ComboBoxRelationship2.getSelectedItem().toString())));//get properties for this node type and turn into list to be searched
        if (!props.contains(ComboBoxProperty4.getSelectedItem().toString()))//if current property is not already in the list   
        {
            props.add(ComboBoxProperty4.getSelectedItem().toString());
            relationshipProperties.replace(ComboBoxRelationship2.getSelectedItem().toString(), props.toArray(new String[props.size()]));
        }
    }//GEN-LAST:event_ButtonAddProperty2ActionPerformed

    /**
     * ButtonCreateRelationshipActionPerformed:
     * @param evt 
     */
    private void ButtonCreateRelationshipActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ButtonCreateRelationshipActionPerformed
        
        String propertyList = "";

        for (int i = 0; i<createRelationshipPropertyList.size(); i++)
        {
            propertyList = propertyList +createRelationshipPropertyList.get(i)[0]+": '"+createRelationshipPropertyList.get(i)[1]+"',";
        }

        if(propertyList.length()>0)
        {
            propertyList = propertyList.substring(0, propertyList.length()-1);
        }
        
        String matchString = "match (node1), (node2) ";
        String whereString = "where node1.ID = '"+ComboBoxID1.getSelectedItem().toString()+"' and node2.ID = '"+ComboBoxID3.getSelectedItem().toString()+"' ";
        String createString = "create (node1) - [r:"+ComboBoxRelationship2.getSelectedItem().toString() + "{ "+propertyList+"}]->(node2)";
        System.out.println(matchString+whereString+createString);
            
        session.run(matchString+whereString+createString);
        
        jTextPane1.setText(jTextPane1.getText()+"\n Created new relationship: "+ComboBoxName1.getSelectedItem().toString()+" - "+ComboBoxRelationship2.getSelectedItem().toString()+" "+propertyList+ " -> "+ComboBoxName4.getSelectedItem().toString());
        createRelationshipPropertyList.clear();
        
        refreshCurrentRelationshipPanel();
        refreshNode2Panel1();
        TextField3.setText(getRelationshipPropertyValue(ComboBoxID1.getSelectedItem().toString(), ComboBoxID2.getSelectedItem().toString(), ComboBoxRelationship1.getSelectedItem().toString(), ComboBoxProperty3.getSelectedItem().toString())[0]);
    }//GEN-LAST:event_ButtonCreateRelationshipActionPerformed

    /**
     * ComboBoxType3ActionPerformed:
     * @param evt 
     */
    private void ComboBoxType3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ComboBoxType3ActionPerformed
        ComboBoxName3.setModel(new javax.swing.DefaultComboBoxModel(getNode2Names(ComboBoxID1.getSelectedItem().toString(), ComboBoxRelationship1.getSelectedItem().toString())));
        ComboBoxID2.setModel(new javax.swing.DefaultComboBoxModel(getNode2IDs(ComboBoxID1.getSelectedItem().toString(), ComboBoxRelationship1.getSelectedItem().toString(), ComboBoxName3.getSelectedItem().toString())));
 
    }//GEN-LAST:event_ComboBoxType3ActionPerformed

    /**
     * ButtonAddAllProperties2ActionPerformed:
     * @param evt 
     */
    private void ButtonAddAllProperties2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ButtonAddAllProperties2ActionPerformed
        String relType =  ComboBoxRelationship2.getSelectedItem().toString();       
        String[] props = relationshipProperties.get(relType);
        String[] currentVals = new String[props.length];
        PropertyDialog p = new PropertyDialog(this, true, "Relationship",relType, props, currentVals); 
        String[] vals = p.showDialog();
                
        createRelationshipPropertyList.clear();
        
        for (int i = 0; i<props.length; i++)
        {
            String[] s = {props[i],vals[i]};
            createRelationshipPropertyList.add(s);
        } 
        
        jTextPane1.setText(jTextPane1.getText()+"\n Added "+props.length+" new relationship properties.");
    }//GEN-LAST:event_ButtonAddAllProperties2ActionPerformed


    private void TextField2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_TextField2ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_TextField2ActionPerformed

    /**
     * ComboBoxProperty2ActionPerformed:
     * @param evt 
     */
    private void ComboBoxProperty2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ComboBoxProperty2ActionPerformed
        TextField2.setText(getNodePropertyValue(ComboBoxProperty2.getSelectedItem().toString(), ComboBoxID1.getSelectedItem().toString())[0]);
    }//GEN-LAST:event_ComboBoxProperty2ActionPerformed
/**
 * ButtonBrowse1ActionPerformed:
 * @param evt 
 */
    private void ButtonBrowse1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ButtonBrowse1ActionPerformed
        
        int returnVal =FileChooser.showOpenDialog(this);
    
        if (returnVal == JFileChooser.APPROVE_OPTION) 
        {
            File file = FileChooser.getSelectedFile();
            TextFieldLoadNodes.setText(file.getAbsolutePath());
        }
        else
        {
            System.out.println("File access cancelled by user.");
        }
    }//GEN-LAST:event_ButtonBrowse1ActionPerformed
/**
 * ButtonAddNodesActionPerformed:
 * @param evt 
 */
    private void ButtonAddNodesActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ButtonAddNodesActionPerformed
     
        ArrayList<ArrayList<String>> nodes = getValuesFromCSV(TextFieldLoadNodes.getText());
        
        for(int i=0; i<nodes.size(); i++)//for each row of file
        {
            int rowLength = nodes.get(i).size();
            if(rowLength>=4)
            {
                String nodeID = nodes.get(i).get(0);
                String nodeType = nodes.get(i).get(1);

                String propertyString = "ID: '"+nodeID+"',";

                int k = 2;
                
                for(int j=0; j<(rowLength-2)/2; j++)
                {
                    propertyString = propertyString+nodes.get(i).get(k)+": '"+nodes.get(i).get(k+1)+"',";
                    k+=2;
                }
                     
                propertyString = propertyString.substring(0,propertyString.length()-1);//remove last comma
                      
                session.run( "create (newNode:"+ nodeType+"{"+propertyString+"})") ;
                jTextPane1.setText(jTextPane1.getText()+"\n Created new node: "+nodeType+" "+propertyString);
                    
            }
            else
            {
                System.out.println("Invalid row in file. Each row needs at least 4 values: ID, node type, name, node name.");
            }      
        }
    }//GEN-LAST:event_ButtonAddNodesActionPerformed

    /**
     * ButtonBrowse2ActionPerformed:
     * @param evt 
     */
    private void ButtonBrowse2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ButtonBrowse2ActionPerformed
        int returnVal =FileChooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION)
        {
            File file = FileChooser.getSelectedFile();
     
            TextFieldLoadRelationships.setText(file.getAbsolutePath());
        }
        else
        {
            System.out.println("File access cancelled by user.");
        }
    }//GEN-LAST:event_ButtonBrowse2ActionPerformed

    /**
     * ButtonAddRelationshipsActionPerformed:
     * @param evt 
     */
    private void ButtonAddRelationshipsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ButtonAddRelationshipsActionPerformed
        
        ArrayList<ArrayList<String>> relationships = getValuesFromCSV(TextFieldLoadRelationships.getText());
        
        for(int i=0; i<relationships.size(); i++)//for each row of file
        {
            int rowLength = relationships.get(i).size();
            if(rowLength>=3)
            {
                String ID1 = relationships.get(i).get(0);
                String ID2 = relationships.get(i).get(1);
                String relType = relationships.get(i).get(2);

                String propertyString = "";

                int k = 3;
                for(int j=0; j<(rowLength-3)/2; j++)
                {
                    propertyString = propertyString+relationships.get(i).get(k)+": '"+relationships.get(i).get(k+1)+"',";
                    k+=2;
                }
                if(!propertyString.equals(""))
                {
                    propertyString = propertyString.substring(0,propertyString.length()-1);//remove last comma
                }
                      
                //make new relationship
                String matchString = "match (node1), (node2) ";
                String whereString = "where node1.ID = '"+ID1+"' and node2.ID = '"+ID2+"' ";
                String createString = "create (node1) - [r:"+relType + "{"+propertyString+"}]->(node2)";
                System.out.println(matchString+whereString+createString);

                session.run( matchString+whereString+createString) ;

                jTextPane1.setText(jTextPane1.getText()+"\n Created new relationship: "+ID1+" - "+relType+" "+propertyString+ " -> "+ID2);
            }
            else
            {
                System.out.println("Invalid row in file. Each row needs at least 3 values: ID1, ID2, relationship type.");
            }
       }
    }//GEN-LAST:event_ButtonAddRelationshipsActionPerformed

    /**
     * ButtonAddAllProperties1ActionPerformed:
     * @param evt 
     */
    private void ButtonAddAllProperties1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ButtonAddAllProperties1ActionPerformed
        String nodeType =  ComboBoxType1.getSelectedItem().toString();       
        String[] props = nodeProperties.get(nodeType);
        String[] currentVals = new String[props.length];
        PropertyDialog p = new PropertyDialog(this, true, "Node",nodeType, props, currentVals); 
        String[] vals = p.showDialog();
                
        createNodePropertyList.clear();
        
        for (int i = 0; i<props.length; i++)
        {
            String[] s = {props[i],vals[i]};
            createNodePropertyList.add(s);
        }   
        jTextPane1.setText(jTextPane1.getText()+"\n Added "+props.length+" new node properties.");
    }//GEN-LAST:event_ButtonAddAllProperties1ActionPerformed

    /**
     * ComboBoxID1ActionPerformed:
     * @param evt 
     */
    private void ComboBoxID1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ComboBoxID1ActionPerformed
        
        ComboBoxProperty2.setModel(new javax.swing.DefaultComboBoxModel(getPropertyTypesfromID(ComboBoxID1.getSelectedItem().toString())));
        TextField2.setText(getNodePropertyValue(ComboBoxProperty2.getSelectedItem().toString(), ComboBoxID1.getSelectedItem().toString())[0]);
       
        refreshPanels();
    }//GEN-LAST:event_ComboBoxID1ActionPerformed

    /**
     * ComboBoxProperty3ActionPerformed:
     * @param evt 
     */
    private void ComboBoxProperty3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ComboBoxProperty3ActionPerformed
        
        TextField3.setText(getRelationshipPropertyValue(ComboBoxID1.getSelectedItem().toString(), ComboBoxID2.getSelectedItem().toString(), ComboBoxRelationship1.getSelectedItem().toString(), ComboBoxProperty3.getSelectedItem().toString())[0]);
    }//GEN-LAST:event_ComboBoxProperty3ActionPerformed

    /**
     * ComboBoxName3ActionPerformed:
     * @param evt 
     */
    private void ComboBoxName3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ComboBoxName3ActionPerformed
        
        ComboBoxID2.setModel(new javax.swing.DefaultComboBoxModel(getNode2IDs(ComboBoxID1.getSelectedItem().toString(), ComboBoxRelationship1.getSelectedItem().toString(), ComboBoxName3.getSelectedItem().toString())));
         //ComboBoxProperty3.setModel(new javax.swing.DefaultComboBoxModel(getRelationshipProperties()));
        TextField3.setText(getRelationshipPropertyValue(ComboBoxID1.getSelectedItem().toString(), ComboBoxID2.getSelectedItem().toString(), ComboBoxRelationship1.getSelectedItem().toString(), ComboBoxProperty3.getSelectedItem().toString())[0]);
    }//GEN-LAST:event_ComboBoxName3ActionPerformed

  
    private void ComboBoxID3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ComboBoxID3ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_ComboBoxID3ActionPerformed

    private void ComboBoxName4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ComboBoxName4ActionPerformed
        
        ComboBoxID3.setModel(new javax.swing.DefaultComboBoxModel(getNodeIDs(ComboBoxType4.getSelectedItem().toString(), ComboBoxName4.getSelectedItem().toString())));
    }//GEN-LAST:event_ComboBoxName4ActionPerformed

    private void TextField3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_TextField3ActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_TextField3ActionPerformed

    private void ComboBoxID2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ComboBoxID2ActionPerformed
        
        TextField3.setText(getRelationshipPropertyValue(ComboBoxID1.getSelectedItem().toString(), ComboBoxID2.getSelectedItem().toString(), ComboBoxRelationship1.getSelectedItem().toString(), ComboBoxProperty3.getSelectedItem().toString())[0]);
    }//GEN-LAST:event_ComboBoxID2ActionPerformed

    /**
     * ButtonDeleteNodeActionPerformed:
     * @param evt 
     */
    private void ButtonDeleteNodeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ButtonDeleteNodeActionPerformed
        
        String ID1 = ComboBoxID1.getSelectedItem().toString();
        if(!ID1.equals(" "))
        {
            String queryString = "match (n)-[r]->() where n.ID = '"+ID1+"' return r";
            String[] res = getQueryResults(queryString);//check for current relationships
            queryString = "match ()-[r]->(n) where n.ID = '"+ID1+"' return r";   
            String[] res2 = getQueryResults(queryString);//check for current relationships
            
            if(res[0].equals(" ")&&res2[0].equals(" "))
            {
                queryString = "match (n) where n.ID = '"+ID1+"' delete n";   

                session.run(queryString);

                jTextPane1.setText(jTextPane1.getText()+"\n Deleted node: "+ID1);
                refreshNode1Panel();
                refreshPanels();
            }
            else
            {
                jTextPane1.setText(jTextPane1.getText()+"\n Cannot delete node - delete all current relationships first. ");
            }
        }
    }//GEN-LAST:event_ButtonDeleteNodeActionPerformed

    /**
     * ButtonDeleteRelationshipActionPerformed:
     * @param evt 
     */
    private void ButtonDeleteRelationshipActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ButtonDeleteRelationshipActionPerformed
        
        String ID1 = ComboBoxID1.getSelectedItem().toString();
        String ID2 = ComboBoxID2.getSelectedItem().toString();
        String relType = ComboBoxRelationship1.getSelectedItem().toString();
        
        if(!ID1.equals(" ") && !ID2.equals(" ") && !relType.equals(" "))
        {
            String queryString = "match (n1)-[r:"+relType+"]->(n2) where n1.ID = '"+ID1+"'and n2.ID = '"+ID2+"' delete r";   
            System.out.println("delete query: "+queryString);

            session.run(queryString);

            jTextPane1.setText(jTextPane1.getText()+"\n Deleted relationship: "+ID1+" - "+relType+" -> "+ID2);
            refreshNode1Panel();
            refreshPanels();
        }
        else
        {
            jTextPane1.setText(jTextPane1.getText()+"\n No relationship to delete. ");  
        }
    }//GEN-LAST:event_ButtonDeleteRelationshipActionPerformed

    /**
     * ButtonEditProperties1ActionPerformed:
     * @param evt 
     */
    private void ButtonEditProperties1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ButtonEditProperties1ActionPerformed
        
        String nodeType =  ComboBoxType2.getSelectedItem().toString(); 
        String nodeID = ComboBoxID1.getSelectedItem().toString();
        updateNodePropertyValues(nodeType, nodeID);
    }//GEN-LAST:event_ButtonEditProperties1ActionPerformed

    /**
     * ButtonEditProperties2ActionPerformed:
     * @param evt 
     */
    private void ButtonEditProperties2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ButtonEditProperties2ActionPerformed
        
        String node1ID =  ComboBoxID1.getSelectedItem().toString(); 
        String node2ID = ComboBoxID2.getSelectedItem().toString();
        String relType = ComboBoxRelationship1.getSelectedItem().toString();
        String[] props = getRelationshipProperties(relType);
        String[] currentVals = new String[props.length];
        
        for(int j = 0; j<props.length; j++)
        {
            currentVals[j] = getRelationshipPropertyValue(node1ID, node2ID, relType, props[j])[0];
        }
        
        PropertyDialog p = new PropertyDialog(this, true, "Relationship",relType, props, currentVals); 
        String[] newVals = p.showDialog();
       
        for (int i = 0; i<props.length; i++)
        {
            updateRelationshipPropertyValue(node1ID,node2ID,relType, props[i], newVals[i]);
        }   
        
        jTextPane1.setText(jTextPane1.getText()+"\n Updated "+props.length+" relationship properties.");
    }//GEN-LAST:event_ButtonEditProperties2ActionPerformed

    /**
     *ButtonEditProperties3ActionPerformed: 
     * @param evt 
     */
    private void ButtonEditProperties3ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ButtonEditProperties3ActionPerformed
        
        String nodeType =  ComboBoxType3.getSelectedItem().toString(); 
        String nodeID = ComboBoxID2.getSelectedItem().toString();
        updateNodePropertyValues(nodeType, nodeID);
        
    }//GEN-LAST:event_ButtonEditProperties3ActionPerformed

    /**
     * ButtonEditProperties4ActionPerformed:
     * @param evt 
     */
    private void ButtonEditProperties4ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ButtonEditProperties4ActionPerformed
        
        String nodeType =  ComboBoxType4.getSelectedItem().toString(); 
        String nodeID = ComboBoxID3.getSelectedItem().toString();
        updateNodePropertyValues(nodeType, nodeID);
        
    }//GEN-LAST:event_ButtonEditProperties4ActionPerformed

    /**
     * ButtonBrowseDBActionPerformed:
     * @param evt 
     */
    private void ButtonBrowseDBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ButtonBrowseDBActionPerformed
     
        int returnVal =FileChooser.showOpenDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION)
        {
            File file = FileChooser.getSelectedFile();
            TextFieldLoadDB.setText(file.getAbsolutePath());    
        }
        else
        {
            System.out.println("File access cancelled by user.");
        }
    }//GEN-LAST:event_ButtonBrowseDBActionPerformed

    /**
     * ButtonLoadDBActionPerformed:
     * @param evt 
     */
    private void ButtonLoadDBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ButtonLoadDBActionPerformed
        setNewDatabaseFields(TextFieldLoadDB.getText());
    }//GEN-LAST:event_ButtonLoadDBActionPerformed

    private void ButtonViewNodeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ButtonViewNodeActionPerformed
        //get the current node

        String currentNodeID = ComboBoxID1.getSelectedItem().toString();
        //query to get its relationships
        
        String[][] result = getNodeSurroundings(currentNodeID);
        
        //write javascript to make mini graph...
        try
        {
            writeHTMLFile(result);
        }
        catch (FileNotFoundException ex)
        {
            Logger.getLogger(GraphDatabaseForm.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        
        String htmlFilePath = "\\graphdisplay.html"; 
        File htmlFile = new File(htmlFilePath);
        try
        {
            Desktop.getDesktop().browse(htmlFile.toURI());
        }
        catch (IOException ex)
        {
            System.out.println(ex.getMessage());
        }
    }//GEN-LAST:event_ButtonViewNodeActionPerformed

    private void ButtonViewGraphActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ButtonViewGraphActionPerformed
        // TODO add your handling code here:
        String[][] graphNodes = getAllNodes();
        
        for (String[] graphNode : graphNodes)
        {
            System.out.println(":" + graphNode[0] + ":" + graphNode[1] + ":" + graphNode[2]);
        }
        String[][] graphRelationships = getAllRelationships();
        
        for (String[] graphRelationship : graphRelationships)
        {
            System.out.println(":" + graphRelationship[0] + ":" + graphRelationship[1] + ":" + graphRelationship[2]);
        }
        //write javascript to make mini graph...
        try
        {
            writeHTMLFileForGraphDisplay(graphNodes,graphRelationships);
        }
        catch (FileNotFoundException ex)
        {
            Logger.getLogger(GraphDatabaseForm.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        String htmlFilePath = "\\graphdisplay.html"; 
        File htmlFile = new File(htmlFilePath);
        try
        {
            Desktop.getDesktop().browse(htmlFile.toURI());
        }
        catch (IOException ex)
        {
            System.out.println(ex.getMessage());
        }
    }//GEN-LAST:event_ButtonViewGraphActionPerformed

    private void TextFieldLoadDBActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_TextFieldLoadDBActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_TextFieldLoadDBActionPerformed

    
    
    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) 
    {
        
    }

    //private AutocompleteJComboBox AutoCompleteCombo1;
    //private  JScrollPane scroll;
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton ButtonAddAllProperties1;
    private javax.swing.JButton ButtonAddAllProperties2;
    private javax.swing.JButton ButtonAddNodes;
    private javax.swing.JButton ButtonAddProperty1;
    private javax.swing.JButton ButtonAddProperty2;
    private javax.swing.JButton ButtonAddRelationships;
    private javax.swing.JButton ButtonBrowse1;
    private javax.swing.JButton ButtonBrowse2;
    private javax.swing.JButton ButtonBrowseDB;
    private javax.swing.JButton ButtonClearProperties1;
    private javax.swing.JButton ButtonClearProperties2;
    private javax.swing.JButton ButtonCreateNode;
    private javax.swing.JButton ButtonCreateRelationship;
    private javax.swing.JButton ButtonDeleteNode;
    private javax.swing.JButton ButtonDeleteRelationship;
    private javax.swing.JButton ButtonEditProperties1;
    private javax.swing.JButton ButtonEditProperties2;
    private javax.swing.JButton ButtonEditProperties3;
    private javax.swing.JButton ButtonEditProperties4;
    private javax.swing.JButton ButtonLoadDB;
    private javax.swing.JButton ButtonViewGraph;
    private javax.swing.JButton ButtonViewNode;
    private javax.swing.JComboBox ComboBoxID1;
    private javax.swing.JComboBox ComboBoxID2;
    private javax.swing.JComboBox ComboBoxID3;
    private javax.swing.JComboBox ComboBoxName1;
    private javax.swing.JComboBox ComboBoxName3;
    private javax.swing.JComboBox ComboBoxName4;
    private javax.swing.JComboBox ComboBoxProperty1;
    private javax.swing.JComboBox ComboBoxProperty2;
    private javax.swing.JComboBox ComboBoxProperty3;
    private javax.swing.JComboBox ComboBoxProperty4;
    private javax.swing.JComboBox ComboBoxRelationship1;
    private javax.swing.JComboBox ComboBoxRelationship2;
    private javax.swing.JComboBox ComboBoxType1;
    private javax.swing.JComboBox ComboBoxType2;
    private javax.swing.JComboBox ComboBoxType3;
    private javax.swing.JComboBox ComboBoxType4;
    private javax.swing.JFileChooser FileChooser;
    private javax.swing.JTextField TextField1;
    private javax.swing.JTextField TextField2;
    private javax.swing.JTextField TextField3;
    private javax.swing.JTextField TextField4;
    private javax.swing.JTextField TextFieldLoadDB;
    private javax.swing.JTextField TextFieldLoadNodes;
    private javax.swing.JTextField TextFieldLoadRelationships;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel13;
    private javax.swing.JLabel jLabel14;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel16;
    private javax.swing.JLabel jLabel17;
    private javax.swing.JLabel jLabel18;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel20;
    private javax.swing.JLabel jLabel21;
    private javax.swing.JLabel jLabel22;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JPanel jPanel6;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JPanel jPanel8;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTextField jTextField2;
    private javax.swing.JTextPane jTextPane1;
    // End of variables declaration//GEN-END:variables
}

