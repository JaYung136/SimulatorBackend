/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.sim.cloudsimsdn.sdn.physicalcomponents;

import java.util.List;

/**
 * Node represents network node (host or switch)
 *
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public interface Node {
	public int getAddress();
	public long getBandwidth();
	public void setRank(int rank);
	public int getRank();

	public void clearCnRoutingTable();
	public void addCnRoute(int srcVM, int destVM, int flowId, Node to);
	public Node getCnRoute(int srcVM, int destVM, int flowId);
	public void removeCnRoute(int srcVM, int destVM, int flowId);
	public void printCnRoute();

	public void addRoute(Node destHost, Link to);
	public List<Link> getRoute(Node destHost);

	public RoutingTable getRoutingTable();

	public void addLink(Link l);
	public Link getLinkTo(Node nextHop);

	public void updateNetworkUtilization();
}
