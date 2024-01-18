/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.sim.cloudsimsdn.sdn.policies.vmallocation.overbooking;

import org.sim.cloudsimsdn.Host;
import org.sim.cloudsimsdn.sdn.policies.selecthost.HostSelectionPolicy;
import org.sim.cloudsimsdn.sdn.policies.vmallocation.VmMigrationPolicy;
import org.sim.cloudsimsdn.sdn.virtualcomponents.SDNVm;

import java.util.List;

public class OverBookingVmAllocationPolicyConsolidateCorrelatedPercentile extends OverbookingVmAllocationPolicyConsolidateConnected {

	public OverBookingVmAllocationPolicyConsolidateCorrelatedPercentile(
			List<? extends Host> list,
			HostSelectionPolicy hostSelectionPolicy,
			VmMigrationPolicy vmMigrationPolicy) {
		super(list, hostSelectionPolicy, vmMigrationPolicy);
	}

	@Override
	protected double getDynamicOverRatioMips(SDNVm vm, Host host) {
		double dor = super.getDynamicOverRatioMips(vm, host);
		double dor_percentage = OverbookingPercentileUtils.translateToPercentage(vm.getName(), dor);
		return dor_percentage;
	}
}
