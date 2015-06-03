/* Copyright 2015 Wouter Spekkink
Authors : Wouter Spekkink <wouterspekkink@gmail.com>
Website : http://www.wouterspekkink.org
DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
Copyright 2015 Wouter Spekkink. All rights reserved.
The contents of this file are subject to the terms of either the GNU
General Public License Version 3 only ("GPL") or the Common
Development and Distribution License("CDDL") (collectively, the
"License"). You may not use this file except in compliance with the
License. You can obtain a copy of the License at
http://gephi.org/about/legal/license-notice/
or /cddl-1.0.txt and /gpl-3.0.txt. See the License for the
specific language governing permissions and limitations under the
License. When distributing the software, include this License Header
Notice in each file and include the License files at
/cddl-1.0.txt and /gpl-3.0.txt. If applicable, add the following below the
License Header, with the fields enclosed by brackets [] replaced by
your own identifying information:
"Portions Copyrighted [year] [name of copyright owner]"
If you wish your version of this file to be governed by only the CDDL
or only the GPL Version 3, indicate your decision by adding
"[Contributor] elects to include this software in this distribution
under the [CDDL or GPL Version 3] license." If you do not indicate a
single choice of license, a recipient has the option to distribute
your version of this file under either the CDDL, the GPL Version 3 or
to extend the choice of license to its licensees as provided above.
However, if you add GPL Version 3 code and therefore, elected the GPL
Version 3 license, then the option applies only if the new code is
made subject to such option by the copyright holder.
Contributor(s): Wouter Spekkink

*/
package org.wouterspekkink.lineage;


import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.gephi.data.attributes.api.AttributeColumn;
import org.gephi.data.attributes.api.AttributeModel;
import org.gephi.data.attributes.api.AttributeOrigin;
import org.gephi.data.attributes.api.AttributeTable;
import org.gephi.data.attributes.api.AttributeType;
import org.gephi.graph.api.Graph;
import org.gephi.graph.api.GraphModel;
import org.gephi.graph.api.Node;
import org.gephi.statistics.spi.Statistics;
import org.gephi.graph.api.DirectedGraph;
import org.gephi.graph.api.GraphController;
import org.gephi.graph.api.NodeData;
import org.gephi.graph.api.NodeIterable;
import org.openide.util.Lookup;



/**
 * @author wouter
 */


public class Lineage implements Statistics {
    
 
    public static final String LINEAGE = "Lineage";
    public static final String ORIGIN = "IsOrigin";
    public static final String ANCESTOR = "IsAncestor";
    public static final String DESCENDANT = "IsDescendant";
    public static final String ADISTANCE = "DistanceAncestor";
    public static final String DDISTANCE = "DistanceDescendant";
    private String originName = "";
    private int counterA = -1;
    private int counterD = 1;    
    Node origin; 
    private boolean isDirected;
    boolean [] nodeAncestors;
    boolean foundNode = false;
    private boolean nodesLeftAnc = true;
    private boolean nodesLeftDes = true;
   
    public Lineage() {
        GraphController graphController = Lookup.getDefault().lookup(GraphController.class);
        if (graphController != null && graphController.getModel()!= null) {
            isDirected = graphController.getModel().isDirected();
        }
    }

    @Override
    public void execute(GraphModel graphModel, AttributeModel attributeModel) {
        Graph graph = null;
        isDirected = graphModel.isDirected();
        if (isDirected) {
            graph = graphModel.getDirectedGraphVisible();
        } else {
            graph = graphModel.getUndirectedGraphVisible();
        }
        execute(graph, attributeModel);
    }
  
    // Create all variable columns and initialize them
    // Columns will be removed (overwritten) if they are alread there.
    public void execute(Graph hgraph, AttributeModel attributeModel) {
        //Look if the result column already exist and create it if needed
        AttributeTable nodeTable = attributeModel.getNodeTable();
        AttributeColumn col = nodeTable.getColumn(LINEAGE);
        AttributeColumn col1 = nodeTable.getColumn(ORIGIN);
        AttributeColumn col2 = nodeTable.getColumn(ANCESTOR);
        AttributeColumn col3 = nodeTable.getColumn(DESCENDANT);
        AttributeColumn col4 = nodeTable.getColumn(ADISTANCE);
        AttributeColumn col5 = nodeTable.getColumn(DDISTANCE);
        
        if (col == null) {
            col = nodeTable.addColumn(LINEAGE, "Lineage", AttributeType.STRING, AttributeOrigin.COMPUTED, "Unrelated");
        } else {
            nodeTable.removeColumn(col);
            col = nodeTable.addColumn(LINEAGE, "Lineage", AttributeType.STRING, AttributeOrigin.COMPUTED, "Unrelated");
        } 
        if (col1 == null) {
            col1 = nodeTable.addColumn(ORIGIN, "IsOrigin", AttributeType.BOOLEAN, AttributeOrigin.COMPUTED, false);
        } else {
            nodeTable.removeColumn(col1);
            col1 = nodeTable.addColumn(ORIGIN, "IsOrigin", AttributeType.BOOLEAN, AttributeOrigin.COMPUTED, false);
        } 
        if (col2 == null) {
            col2 = nodeTable.addColumn(ANCESTOR, "IsAncestor", AttributeType.BOOLEAN, AttributeOrigin.COMPUTED, false);
        } else {
            nodeTable.removeColumn(col2);
            col2 = nodeTable.addColumn(ANCESTOR, "IsAncestor", AttributeType.BOOLEAN, AttributeOrigin.COMPUTED, false);
        } 
        if (col3 == null) {
            col3 = nodeTable.addColumn(DESCENDANT, "IsDescendant", AttributeType.BOOLEAN, AttributeOrigin.COMPUTED, false);
        } else {
            nodeTable.removeColumn(col3);
            col3 = nodeTable.addColumn(DESCENDANT, "IsDescendant", AttributeType.BOOLEAN, AttributeOrigin.COMPUTED, false);
        } 
        if (col4 == null) {
            col4 = nodeTable.addColumn(ADISTANCE, "DistanceAncestor", AttributeType.INT, AttributeOrigin.COMPUTED, 0);
        } else {
            nodeTable.removeColumn(col4);
            col4 = nodeTable.addColumn(ADISTANCE, "DistanceAncestor", AttributeType.INT, AttributeOrigin.COMPUTED, 0);
        } 
        if (col5 == null) {
            col5 = nodeTable.addColumn(DDISTANCE, "DistanceDescendant", AttributeType.INT, AttributeOrigin.COMPUTED, 0);
        } else {
            nodeTable.removeColumn(col5);
            col5 = nodeTable.addColumn(DDISTANCE, "DistanceDescendant", AttributeType.INT, AttributeOrigin.COMPUTED, 0);
        } 
              
        hgraph.readLock();
        
        // First let's find the origin that is submitted by the user and we'll only run the rest of the plugin if the origin is found.
        for (Node n : hgraph.getNodes()) {
            NodeData info = n.getNodeData();
            String tempName = info.getId();
            if (tempName == null ? originName == null : tempName.equals(originName)) {
                origin = n;
                foundNode = true;
            } 
        }
        
        // We only run the algorithm if an appropriate origin node was submitted by the user.
        if(foundNode) {
            origin.getAttributes().setValue(col.getIndex(), "Origin");
            origin.getAttributes().setValue(col1.getIndex(), true);
            List<Node> doNodesAnc = new CopyOnWriteArrayList<Node>();
            List<Node> doNodesDes = new CopyOnWriteArrayList<Node>();
        
            NodeIterable nodeIterAnc = getNodeIterAnc(hgraph, origin);
            NodeIterable nodeIterDes = getNodeIterDes(hgraph, origin);
        
            for (Node node : nodeIterAnc) {
                if(node.getNodeData().getAttributes().getValue(LINEAGE).equals("Unrelated")) {
                    doNodesAnc.add(node);
                    node.getAttributes().setValue(col.getIndex(), "Ancestor");
                    node.getAttributes().setValue(col2.getIndex(), true);
                    node.getAttributes().setValue(col4.getIndex(), counterA);
                } else if (node.getNodeData().getAttributes().getValue(LINEAGE).equals("Descendant")) {
                    node.getAttributes().setValue(col.getIndex(), "Hybrid");
                    node.getAttributes().setValue(col2.getIndex(), true);
                    node.getAttributes().setValue(col4.getIndex(), counterA);
                }
            }
            
            for (Node node : nodeIterDes) {
                if(node.getNodeData().getAttributes().getValue(LINEAGE).equals("Unrelated")) {
                    doNodesDes.add(node);
                    node.getAttributes().setValue(col.getIndex(), "Descendant");
                    node.getAttributes().setValue(col3.getIndex(), true);
                    node.getAttributes().setValue(col5.getIndex(), counterD);
                } else if (node.getNodeData().getAttributes().getValue(LINEAGE).equals("Ancestor")) {
                    node.getAttributes().setValue(col.getIndex(), "Hybrid");
                    node.getAttributes().setValue(col3.getIndex(), true);
                    node.getAttributes().setValue(col5.getIndex(), counterD);
                }
            }
        
            while(nodesLeftAnc) {
                if(doNodesAnc.isEmpty()) {
                    nodesLeftAnc = false;
                } else {
                    counterA -= 1;
                    for (Node node : doNodesAnc) {
                        NodeIterable nodeIterTwo = getNodeIterAnc(hgraph, node);
                        for(Node nodeTwo : nodeIterTwo) {
                            if(nodeTwo.getNodeData().getAttributes().getValue(LINEAGE).equals("Unrelated")) {
                                nodeTwo.getAttributes().setValue(col.getIndex(), "Ancestor");
                                nodeTwo.getAttributes().setValue(col2.getIndex(), true);
                                nodeTwo.getAttributes().setValue(col4.getIndex(), counterA);
                                doNodesAnc.add(nodeTwo);
                            } else if (nodeTwo.getNodeData().getAttributes().getValue(LINEAGE).equals("Descendant")) {
                                nodeTwo.getAttributes().setValue(col.getIndex(), "Hybrid");
                                nodeTwo.getAttributes().setValue(col2.getIndex(), true);
                                nodeTwo.getAttributes().setValue(col4.getIndex(), counterA);
                                doNodesDes.add(nodeTwo);
                            }
                        }
                        doNodesAnc.remove(node);
                    }   
                }
            }
            
            while(nodesLeftDes) {
                if(doNodesDes.isEmpty()) {
                    nodesLeftDes = false;
                } else {
                    counterD += 1;
                    for (Node node : doNodesDes) {

                        NodeIterable nodeIterTwo = getNodeIterDes(hgraph, node);
                        for(Node nodeTwo : nodeIterTwo) {
                            if(nodeTwo.getNodeData().getAttributes().getValue(LINEAGE).equals("Unrelated")) {
                                nodeTwo.getAttributes().setValue(col.getIndex(), "Descendant");
                                nodeTwo.getAttributes().setValue(col3.getIndex(), true);
                                nodeTwo.getAttributes().setValue(col5.getIndex(), counterD);
                                doNodesDes.add(nodeTwo);
                            } else if (nodeTwo.getNodeData().getAttributes().getValue(LINEAGE).equals("Ancestor")) {
                                nodeTwo.getAttributes().setValue(col.getIndex(), "Hybrid");
                                nodeTwo.getAttributes().setValue(col3.getIndex(), true);
                                nodeTwo.getAttributes().setValue(col5.getIndex(), counterD);
                                doNodesDes.add(nodeTwo);
                            }
                        }
                        doNodesDes.remove(node);
                    }   
                }
            }

        }
        hgraph.readUnlock();
    }
 
    private NodeIterable getNodeIterDes(Graph thisGraph, Node n) {
        NodeIterable nodeIter;
        nodeIter = ((DirectedGraph) thisGraph).getSuccessors(n);
        return nodeIter;
    }
    
    private NodeIterable getNodeIterAnc(Graph thisGraph, Node n) {
        NodeIterable nodeIter;
        nodeIter = ((DirectedGraph) thisGraph).getPredecessors(n);
        return nodeIter;
    }
                 
    public void setDirected(boolean isDirected) {
        this.isDirected = isDirected;
    }

    public boolean isDirected() {
        return isDirected;
    }
    
    public void setOrigin(String inputOrigin) {
        originName  = inputOrigin;
    }
    
    public String getOrigin() {
        return originName;
    }
    
    @Override
    public String getReport() {
        //This is the HTML report shown when execution ends. 
        //One could add a distribution histogram for instance
        String report = "<HTML> <BODY> <h1>Stress value</h1> "
                + "<hr>"
                + "<br> The results are reported in the Lineage column (see data laboratory)<br />"
                + "<br> <br />"
                + "</BODY></HTML>";
        return report;
    }
    
}
