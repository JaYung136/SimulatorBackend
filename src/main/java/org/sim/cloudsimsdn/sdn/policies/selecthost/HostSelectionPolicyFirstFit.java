/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.sim.cloudsimsdn.sdn.policies.selecthost;

import org.sim.cloudsimsdn.Host;
import org.sim.cloudsimsdn.sdn.physicalcomponents.SDNHost;
import org.sim.cloudsimsdn.sdn.policies.vmallocation.VmAllocationPolicyEx;
import org.sim.cloudsimsdn.sdn.virtualcomponents.SDNVm;

import java.util.ArrayList;
import java.util.List;

public class HostSelectionPolicyFirstFit extends HostSelectionPolicy {
	@Override
	public List<Host> selectHostForVm(SDNVm vm, List<SDNHost> hosts) {
		return getFirstFitHostsForVm(vm, hosts, vmAllocPolicy);
	}

	public static List<Host> getFirstFitHostsForVm(SDNVm vm, List<SDNHost> hosts, VmAllocationPolicyEx vmAllocPolicy) {
		int numHosts = hosts.size();
		List<Host> hostCandidates = new ArrayList<Host>();
		boolean result = false;

		// Find the fit host for VM
		for(int idx = 0; result == false && idx < numHosts; idx++) {
			SDNHost host = hosts.get(idx);
			if(vmAllocPolicy.isResourceAllocatable(host, vm)) {
				hostCandidates.add(host);
			}
		}

		return hostCandidates;
	}
}
