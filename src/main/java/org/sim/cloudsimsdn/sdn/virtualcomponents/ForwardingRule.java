/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.sim.cloudsimsdn.sdn.virtualcomponents;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import org.sim.cloudsimsdn.Log;
import org.sim.cloudsimsdn.core.CloudSim;
import org.sim.cloudsimsdn.sdn.nos.NetworkOperatingSystem;
import org.sim.cloudsimsdn.sdn.physicalcomponents.Node;
import org.sim.cloudsimsdn.sdn.physicalcomponents.SDNHost;
import org.sim.cloudsimsdn.sdn.physicalcomponents.switches.Switch;

import java.util.HashMap;
import java.util.Map;


/**
 * ForwardingRule class is to represent a forwarding table in each switch.
 * This is for VM routing, not host routing. Addresses used here are the addresses of VM.
 *
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public class ForwardingRule {

	Table<Integer, Integer, Map<Integer,Node>> table;

	public ForwardingRule(){
		this.table = HashBasedTable.create();
	}

	public void clear(){
		table.clear();
	}

	public void addRule(int src, int dest, int flowId, Node to){
		Map<Integer, Node> map = table.get(src, dest);
		if(map == null)
			map = new HashMap<Integer, Node>();
		map.put(flowId, to);
		table.put(src, dest, map);
	}

	public void removeRule(int src, int dest, int flowId){
		Map<Integer, Node> map = table.get(src, dest);

		if(map != null) {
			map.remove(flowId);
			if(map.isEmpty())
				table.remove(src, dest);
			else
				table.put(src, dest, map);
		}
	}

	public Node getRoute(int src, int dest, int flowId) {
		Map<Integer, Node> map = table.get(src, dest);
		if(map==null)
			return null;

		return map.get(flowId);
	}

	public void printForwardingTable(String thisNode) {
		for(Integer rowK:table.rowKeySet()) {
			Map<Integer, Map<Integer,Node>> row = table.row(rowK);
			for(Integer colK: row.keySet()) {
				Map<Integer, Node> nodes = row.get(colK);

				for(Integer flowId:nodes.keySet()) {
					Node node = nodes.get(flowId);
					if(node instanceof SDNHost) {
						Log.printLine(CloudSim.clock() + ": Forwarding table of " + thisNode + ": "+
								NetworkOperatingSystem.getVmName(rowK) + "|"+rowK+"->" +
								NetworkOperatingSystem.getVmName(colK) + "|"+colK+"->"+"(flow:"+flowId+")==> " +
								node.toString());
					}
					else if(node instanceof Switch) {
						Log.printLine(CloudSim.clock() + ": Forwarding table of " + thisNode + ": "+
								NetworkOperatingSystem.getVmName(rowK) + "|"+rowK+"->" +
								NetworkOperatingSystem.getVmName(colK) + "|"+colK+"->"+"(flow:"+flowId+")==> " +
								((Switch) node).getName());
					}
					else {
						Log.printLine(CloudSim.clock() + ": Forwarding table of " + thisNode + ": "+
								NetworkOperatingSystem.getVmName(rowK) + "|"+rowK+"->" +
								NetworkOperatingSystem.getVmName(colK) + "|"+colK+"->"+"(flow:"+flowId+")==> " +
								node.getAddress());
					}
				}
			}
		}
	}
}
