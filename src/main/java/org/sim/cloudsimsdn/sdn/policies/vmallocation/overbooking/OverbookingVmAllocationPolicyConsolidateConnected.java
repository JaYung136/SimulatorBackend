/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.sim.cloudsimsdn.sdn.policies.vmallocation.overbooking;

import org.sim.cloudsimsdn.Host;
import org.sim.cloudsimsdn.Vm;
import org.sim.cloudsimsdn.sdn.physicalcomponents.SDNHost;
import org.sim.cloudsimsdn.sdn.policies.selecthost.HostSelectionPolicy;
import org.sim.cloudsimsdn.sdn.policies.vmallocation.VmAllocationInGroup;
import org.sim.cloudsimsdn.sdn.policies.vmallocation.VmGroup;
import org.sim.cloudsimsdn.sdn.policies.vmallocation.VmMigrationPolicy;
import org.sim.cloudsimsdn.sdn.virtualcomponents.SDNVm;

import java.util.ArrayList;
import java.util.List;

public class OverbookingVmAllocationPolicyConsolidateConnected extends OverbookingVmAllocationPolicy implements VmAllocationInGroup {
	public OverbookingVmAllocationPolicyConsolidateConnected(
			List<? extends Host> list,
			HostSelectionPolicy hostSelectionPolicy,
			VmMigrationPolicy vmMigrationPolicy) {
		super(list, hostSelectionPolicy, vmMigrationPolicy);
	}

	protected List<SDNHost> getHostListVmGroup(VmGroup vmGroup) {
		List<SDNHost> hosts = new ArrayList<SDNHost>();

		for(SDNVm vm:vmGroup.<SDNVm>getVms()) {
			SDNHost h = (SDNHost)this.getHost(vm);
			if(h != null)
				hosts.add(h);
		}

		return hosts;
	}
	/**
	 * Allocates a host for a given VM Group.
	 *
	 * @param vm VM specification
	 * @return $true if the host could be allocated; $false otherwise
	 * @pre $none
	 * @post $none
	 */
	@Override
	public boolean allocateHostForVmInGroup(Vm vm, VmGroup vmGroup) {
		if(vmMigrationPolicy instanceof VmMigrationPolicyGroupInterface) {
			((VmMigrationPolicyGroupInterface)vmMigrationPolicy).addVmInVmGroup(vm, vmGroup);
		}

		List<SDNHost> connectedHosts = getHostListVmGroup(vmGroup);

		if(connectedHosts.size() == 0) {
			// This VM is the first VM to be allocated
			return allocateHostForVm(vm);	// Use the Most Full First
		}
		else {
			// Other VMs in the group has been already allocated
			// Try to put this VM into one of the correlated hosts
			if(allocateHostForVm(vm, hostSelectionPolicy.selectHostForVm((SDNVm)vm, connectedHosts)) == true) {
				return true;
			}
			else {
				// Cannot create VM to correlated hosts. Use the Most Full First
				return allocateHostForVm(vm);
			}
		}
	}

}
