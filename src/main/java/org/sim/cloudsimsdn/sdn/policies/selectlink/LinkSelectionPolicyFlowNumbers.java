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

public class LinkSelectionPolicyFlowNumbers implements LinkSelectionPolicy {
	public LinkSelectionPolicyFlowNumbers() {
	}

	// Choose the least full link by comparing the number of flows inside
	public Link selectLink(List<Link> links, int flowId, Node src, Node dest, Node prevNode) {
		Link lighter = links.get(0);
		for(Link l:links) {
			if(l.getChannelCount(src) < lighter.getChannelCount(src)) {
				// Less traffic flows using this link
				lighter = l;
			}
		}
		return lighter;
	}

	@Override
	public boolean isDynamicRoutingEnabled() {
		return true;
	}
}
