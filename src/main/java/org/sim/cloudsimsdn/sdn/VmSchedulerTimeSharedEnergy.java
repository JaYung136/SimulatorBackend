/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.sim.cloudsimsdn.sdn;

import org.sim.cloudsimsdn.Pe;
import org.sim.cloudsimsdn.VmSchedulerTimeShared;
import org.sim.cloudsimsdn.core.CloudSim;
import org.sim.cloudsimsdn.sdn.monitor.MonitoringValues;
import org.sim.cloudsimsdn.sdn.monitor.power.PowerUtilizationHistoryEntry;
import org.sim.cloudsimsdn.sdn.monitor.power.PowerUtilizationInterface;

import java.util.ArrayList;
import java.util.List;

/**
 * VmSchedulerTimeSharedEnergy is a VMM allocation policy that allocates one or more Pe to a VM, and
 * allows sharing of PEs by time. If there is no free PEs to the VM, allocation fails. Free PEs are
 * not allocated to VMs
 *
 * @author Rodrigo N. Calheiros
 * @author Anton Beloglazov
 * @author Jungmin Son
 *  * @since CloudSim Toolkit 1.0
 */
public class VmSchedulerTimeSharedEnergy extends VmSchedulerTimeShared implements PowerUtilizationInterface{


	public VmSchedulerTimeSharedEnergy(List<? extends Pe> pelist) {
		super(pelist);
	}

	@Override
	protected void setAvailableMips(double availableMips) {
		super.setAvailableMips(availableMips);
		addUtilizationEntry();
	}

	private List<PowerUtilizationHistoryEntry> utilizationHistories = null;
	private static double powerOffDuration = 0; //if host is idle for 1 hours, it's turned off.

	public void addUtilizationEntryTermination(double terminatedTime) {
		if(this.utilizationHistories != null)
			this.utilizationHistories.add(new PowerUtilizationHistoryEntry(terminatedTime, 0));
	}

	public List<PowerUtilizationHistoryEntry> getUtilizationHisotry() {
		return utilizationHistories;
	}

	public double getUtilizationEnergyConsumption() {

		double total=0;
		double lastTime=0;
		double lastUtilPercentage=0;
		if(this.utilizationHistories == null)
			return 0;

		for(PowerUtilizationHistoryEntry h:this.utilizationHistories) {
			double duration = h.startTime - lastTime;
			double utilPercentage = lastUtilPercentage;
			double power = calculatePower(utilPercentage);
			double energyConsumption = power * duration;

			// Assume that the host is turned off when duration is long enough
			if(duration > powerOffDuration && lastUtilPercentage == 0)
				energyConsumption = 0;

			total += energyConsumption;
			lastTime = h.startTime;
			lastUtilPercentage = h.utilPercentage;
		}
		return total/3600;	// transform to Whatt*hour from What*seconds
	}

	private double calculatePower(double u) {
		double power = 120 + 154 * u;
		return power;
	}

	private void addUtilizationEntry() {
		double time = CloudSim.clock();
		double totalMips = getTotalMips();
		double usingMips = totalMips - this.getAvailableMips();
		if(usingMips < 0) {
			System.err.println("addUtilizationEntry : using mips is negative, No way!");
		}
		if(utilizationHistories == null)
			utilizationHistories = new ArrayList<PowerUtilizationHistoryEntry>();
		this.utilizationHistories.add(new PowerUtilizationHistoryEntry(time, usingMips/getTotalMips()));
	}

	private double getTotalMips() {
		return this.getPeList().size() * this.getPeCapacity();
	}

	public MonitoringValues getMonitoringValuesCPUUtilization() {
		MonitoringValues mv = new MonitoringValues(MonitoringValues.ValueType.Utilization_Percentage);
		for(PowerUtilizationHistoryEntry entry:utilizationHistories) {
			mv.add(entry.utilPercentage, entry.startTime);
		}

		return mv;
	}

}
