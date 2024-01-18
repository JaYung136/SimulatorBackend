/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.sim.cloudsimsdn.sdn.policies.vmallocation.overbooking;

import org.sim.cloudsimsdn.Vm;
import org.sim.cloudsimsdn.sdn.policies.vmallocation.VmGroup;

public interface VmMigrationPolicyGroupInterface {
	public void addVmInVmGroup(Vm vm, VmGroup vmGroup);
}
