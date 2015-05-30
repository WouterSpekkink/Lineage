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


import java.util.HashMap;
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
    
    /* Currently I am assuming that we're working with only two dimensions
    Later I might want to add an iterator to determine the number of
    dimensions to be used. */
    
    public static final String LINEAGE = "Lineage";
    private String originName = "";
    private int N;
    Node origin; 
    private boolean isDirected;
    boolean [] nodeAncestors;
    private boolean isCanceled;
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
  
    public void execute(Graph hgraph, AttributeModel attributeModel) {
        isCanceled = false;
        //Look if the result column already exist and create it if needed
        AttributeTable nodeTable = attributeModel.getNodeTable();
        AttributeTable edgeTable = attributeModel.getEdgeTable();
        AttributeColumn col = nodeTable.getColumn(LINEAGE);
        if (col == null) {
            col = nodeTable.addColumn(LINEAGE, "Lineage", AttributeType.STRING, AttributeOrigin.COMPUTED, "Unrelated");
        } else {
            nodeTable.removeColumn(col);
            col = nodeTable.addColumn(LINEAGE, "Lineage", AttributeType.STRING, AttributeOrigin.COMPUTED, "Unrelated");
        } 
              
        //Lock to graph. This is important to have consistent results if another
        //process is currently modifying it.
        hgraph.readLock();
        
        N = hgraph.getNodeCount();
        HashMap<Node, Integer> indicies = createIndiciesMap(hgraph);
                
        // Initialize the list of ancestors
        for (Node n : hgraph.getNodes()) {
            NodeData info = n.getNodeData();
            String tempName = info.getId();
            if (tempName == null ? originName == null : tempName.equals(originName)) {
                origin = n;
                foundNode = true;
            } 
        }
        
        if(foundNode) {
            origin.getAttributes().setValue(col.getIndex(), "Origin");
            List<Node> doNodesAnc = new CopyOnWriteArrayList<Node>();
            List<Node> doNodesDes = new CopyOnWriteArrayList<Node>();
        
            // I should somehow loop through the results after this.
            NodeIterable nodeIterAnc = getNodeIterAnc(hgraph, origin);
            NodeIterable nodeIterDes = getNodeIterDes(hgraph, origin);
        
            for (Node node : nodeIterAnc) {
                doNodesAnc.add(node);  
                int n_index = indicies.get(node);
                node.getAttributes().setValue(col.getIndex(), "Ancestor");   
            }
        
            for (Node node : nodeIterDes) {
                doNodesDes.add(node);
                int n_index = indicies.get(node);
                node.getAttributes().setValue(col.getIndex(), "Descendant");
            }
        
            while(nodesLeftAnc) {
                if(doNodesAnc.isEmpty()) {
                    nodesLeftAnc = false;
                } else {
                    for (Node node : doNodesAnc) {
                        NodeIterable nodeIterTwo = getNodeIterAnc(hgraph, node);
                        for(Node nodeTwo : nodeIterTwo) {
                            int n_index = indicies.get(nodeTwo);
                            nodeTwo.getAttributes().setValue(col.getIndex(), "Ancestor");
                            doNodesAnc.add(nodeTwo);
                        }
                    
                        doNodesAnc.remove(node);
                    }   
                }
            }
            while(nodesLeftDes) {
                if(doNodesDes.isEmpty()) {
                    nodesLeftDes = false;
                } else {
                    for (Node node : doNodesDes) {
                        NodeIterable nodeIterTwo = getNodeIterDes(hgraph, node);
                        for(Node nodeTwo : nodeIterTwo) {
                            int n_index = indicies.get(nodeTwo);
                            nodeTwo.getAttributes().setValue(col.getIndex(), "Descendant");
                            doNodesDes.add(nodeTwo);
                        }
                    
                        doNodesDes.remove(node);
                    }   
                }
            }

        }
        hgraph.readUnlock();
    }
   
    private NodeIterable getNodeIterAnc(Graph thisGraph, Node n) {
        NodeIterable nodeIter;
        nodeIter = ((DirectedGraph) thisGraph).getPredecessors(n);
        return nodeIter;
    }
    
    private NodeIterable getNodeIterDes(Graph thisGraph, Node n) {
        NodeIterable nodeIter;
        nodeIter = ((DirectedGraph) thisGraph).getSuccessors(n);
        return nodeIter;
    }
              
    public  HashMap<Node, Integer> createIndiciesMap(Graph hgraph) {
       HashMap<Node, Integer> indicies = new HashMap<Node, Integer>();
        int index = 0;
        for (Node s : hgraph.getNodes()) {
            indicies.put(s, index);
            index++;
        } 
        return indicies;
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
