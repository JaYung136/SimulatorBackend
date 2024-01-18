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

import java.util.List;

public abstract class HostSelectionPolicy {
	protected VmAllocationPolicyEx vmAllocPolicy=null;

	public void setVmAllocationPolicy(VmAllocationPolicyEx vmAllocationPolicyEx) {
		vmAllocPolicy=vmAllocationPolicyEx;
	}

	public abstract List<Host> selectHostForVm(SDNVm vm, List<SDNHost> hosts);
}
