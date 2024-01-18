/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */

package org.sim.cloudsimsdn.sdn;

import org.sim.cloudsimsdn.Cloudlet;

import java.util.List;

public interface CloudletSchedulerMonitor {
	public long getTotalProcessingPreviousTime(double currentTime, List<Double> mipsShare);
	public boolean isVmIdle();
	public double getTimeSpentPreviousMonitoredTime(double currentTime);

	public int getCloudletTotalPesRequested();
	public List<Cloudlet> getFailedCloudlet();
}
