/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.sim.cloudsimsdn.sdn.policies.selectlink;

import org.sim.cloudsimsdn.sdn.physicalcomponents.Link;
import org.sim.cloudsimsdn.sdn.physicalcomponents.Node;

import java.util.List;

public class LinkSelectionPolicyFirst implements LinkSelectionPolicy {

	// Choose the first link
	public Link selectLink(List<Link> links, int flowId, Node src, Node dest, Node prevNode) {
		return links.get(0);
	}

	@Override
	public boolean isDynamicRoutingEnabled() {
		return false;
	}
}
