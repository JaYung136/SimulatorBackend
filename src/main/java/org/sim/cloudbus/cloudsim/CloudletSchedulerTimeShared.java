/*
 * Title: CloudSim Toolkit Description: CloudSim (Cloud Simulation) Toolkit for Modeling and
 * Simulation of Clouds Licence: GPL - http://www.gnu.org/copyleft/gpl.html
 * 
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.sim.cloudbus.cloudsim;

import org.sim.cloudbus.cloudsim.core.CloudSim;
import org.sim.service.Constants;
import org.sim.workflowsim.Job;
import org.sim.workflowsim.Task;

import java.util.ArrayList;
import java.util.List;

/**
 * CloudletSchedulerTimeShared implements a policy of scheduling performed by a virtual machine.
 * Cloudlets execute time-shared in VM.
 * 
 * @author Rodrigo N. Calheiros
 * @author Anton Beloglazov
 * @since CloudSim Toolkit 1.0
 */
public class CloudletSchedulerTimeShared extends CloudletScheduler {

	/** The cloudlet exec list. */
	private List<? extends ResCloudlet> cloudletExecList;

	/** The cloudlet paused list. */
	private List<? extends ResCloudlet> cloudletPausedList;

	/** The cloudlet finished list. */
	private List<? extends ResCloudlet> cloudletFinishedList;

	/** The current cp us. */
	protected int currentCPUs;


	protected double currentRam;

	protected int usedPes;

	protected double usedRam;

	private List<Double> mipsshare;


	/**
	 * Creates a new CloudletSchedulerTimeShared object. This method must be invoked before starting
	 * the actual simulation.
	 * 
	 * @pre $none
	 * @post $none
	 */
	public CloudletSchedulerTimeShared() {
		super();
		cloudletExecList = new ArrayList<ResCloudlet>();
		cloudletPausedList = new ArrayList<ResCloudlet>();
		cloudletFinishedList = new ArrayList<ResCloudlet>();
		mipsshare = new ArrayList<>();
		currentCPUs = 0;
		usedRam = 0;
		usedPes = 0;
		currentRam = 0;
	}

	@Override
	public double getCpuUtilization() {
		getCapacity(getCurrentMipsShare());
		return (double)usedPes / currentCPUs;
	}

	@Override
	public double getRamUtilization() {
		getCapacity(getCurrentMipsShare());
		return (double)usedRam / currentRam;
	}


	/**
	 * Updates the processing of cloudlets running under management of this scheduler.
	 * 
	 * @param currentTime current simulation time
	 * @param mipsShare array with MIPS share of each processor available to the scheduler
	 * @return time predicted completion time of the earliest finishing cloudlet, or 0 if there is
	 *         no next events
	 * @pre currentTime >= 0
	 * @post $none
	 */
	@Override
	public double updateVmProcessing(double currentTime, List<Double> mipsShare, double ram) {
		setCurrentMipsShare(mipsShare);
		double timeSpam = currentTime - getPreviousTime();
		this.currentRam = ram;
		this.currentCPUs = mipsShare.size();
		getCapacity(mipsShare);
		String containers = "";
		//Log.printLine(getCloudletExecList().size());
		for (ResCloudlet rcl : getCloudletExecList()) {
			if(!((Job) rcl.getCloudlet()).getTaskList().isEmpty()) {
				containers += ((Job)rcl.getCloudlet()).getTaskList().get(0).name + " ";
				//Log.printLine("Remain length :"  + rcl.getRemainingCloudletLength());
			}

			rcl.updateCloudletFinishedSoFar((long) (getCapacity(mipsShare) * timeSpam * rcl.getNumberOfPes() * Consts.MILLION));
			/*if(!((Job) rcl.getCloudlet()).getTaskList().isEmpty()) {
				Log.printLine("Container " + ((Job) rcl.getCloudlet()).getTaskList().get(0).name + " remain: " + rcl.getRemainingCloudletLength());
			}*/
		}
		System.out.print(String.format("%-8s", " " + containers));
		System.out.print("\n");
		if (getCloudletExecList().size() == 0 && getCloudletPausedList().size() == 0) {
			setPreviousTime(currentTime);
			return 0.0;
		}

		// check finished cloudlets
		double nextEvent = Double.MAX_VALUE;
		List<ResCloudlet> toRemove = new ArrayList<ResCloudlet>();
		for (ResCloudlet rcl : getCloudletExecList()) {
			long remainingLength = rcl.getRemainingCloudletLength();
			if (remainingLength == 0 /*|| remainingLength <= (getCapacity(mipsShare) * rcl.getNumberOfPes() * 0.01)*/) {// finished: remove from the list
				toRemove.add(rcl);
				cloudletFinish(rcl);
				//this.usedRam -= ((Job)rcl.getCloudlet()).getRam();
				continue;
			}
		}
		getCloudletExecList().removeAll(toRemove);

		// estimate finish time of cloudlets
		List<ResCloudlet> rcls = new ArrayList<>();
		for (ResCloudlet rcl : getCloudletExecList()) {
			double estimatedFinishTime = currentTime
					+ (rcl.getRemainingCloudletLength() / (getCapacity(mipsShare) * rcl.getNumberOfPes()));
			if (estimatedFinishTime - currentTime < CloudSim.getMinTimeBetweenEvents()) {
				estimatedFinishTime = currentTime + CloudSim.getMinTimeBetweenEvents();
			}
			if(Constants.pause.containsKey(rcl.getCloudlet().getCloudletId())) {
				Double start = Constants.pause.get(rcl.getCloudlet().getCloudletId()).getKey();
				Double last = Constants.pause.get(rcl.getCloudlet().getCloudletId()).getValue();
				if(currentTime - rcl.getExecStartTime() >= start) {
					//Log.printLine(CloudSim.clock() + " : Container " + rcl.getCloudletId() + " is paused");
					//cloudletPause(rcl.getCloudletId());
					rcls.add(rcl);
					estimatedFinishTime = Math.min(estimatedFinishTime, rcl.getExecStartTime() + start + last);
				}
			}
			if (estimatedFinishTime < nextEvent) {
				nextEvent = estimatedFinishTime;
			}
		}
		for(ResCloudlet rcl: rcls) {
			cloudletPause(rcl.getCloudletId());
		}

		List<ResCloudlet> toExec = new ArrayList<ResCloudlet>();
		for(ResCloudlet rcl: getCloudletPausedList()) {
			Double start = Constants.pause.get(rcl.getCloudlet().getCloudletId()).getKey();
			Double last = Constants.pause.get(rcl.getCloudlet().getCloudletId()).getValue();
			if(currentTime >= rcl.getExecStartTime() + start + last) {
				//Log.printLine(CloudSim.clock() + " : Container " + rcl.getCloudletId() + " is in exec");
				toExec.add(rcl);
			} else {
				double n = rcl.getExecStartTime() + start + last;
				if(n - currentTime < CloudSim.getMinTimeBetweenEvents()) {
					n = currentTime + CloudSim.getMinTimeBetweenEvents();
				}
				//Log.printLine("pause need to be exec in : " + n);
				if(n < nextEvent) {
					nextEvent = n;
				}
			}
		}
		/*getCloudletPausedList().removeAll(toExec);
		getCloudletExecList().addAll(toExec);*/
		for(ResCloudlet rcl: toExec) {
			cloudletResume(rcl.getCloudletId());
		}
		setPreviousTime(currentTime);
		return nextEvent;
	}

	public Integer getExecSize() {
		return cloudletExecList.size();
	}

	@Override
	public void setInMigrate(Integer cloudletId) {
		List<ResCloudlet> toRemove = new ArrayList<ResCloudlet>();
		for (ResCloudlet rcl : getCloudletExecList()) {
			if(rcl.getCloudletId() == cloudletId) {
				if(rcl.getCloudletStatus() == Cloudlet.INEXEC)
					rcl.setCloudletStatus(Cloudlet.PAUSED);
				break;
			}
		}
	}

	/**
	 * Gets the capacity.
	 * 
	 * @param mipsShare the mips share
	 * @return the capacity
	 */
	protected double getCapacity(List<Double> mipsShare) {
		double capacity = 0.0;
		int cpus = 0;
		for (Double mips : mipsShare) {
			capacity += mips;
			if (mips > 0.0) {
				cpus++;
			}
		}
		currentCPUs = cpus;

		int pesInUse = 0;
		double ramInUse = 0;
		double rate = 1.0;
		for (ResCloudlet rcl : getCloudletExecList()) {
			if(rcl.getCloudletStatus() != Cloudlet.INEXEC) {
				rate = 0.1;
			}
			if(!((Job) rcl.getCloudlet()).getTaskList().isEmpty()) {
				for(Task t: ((Job)rcl.getCloudlet()).getTaskList()) {
					pesInUse += t.getNumberOfPes() * rate;
					ramInUse += t.getRam() * rate;
				}
			}
		}
		usedPes = Math.min(currentCPUs, pesInUse);
		usedRam = Math.min(currentRam, ramInUse);
		//Log.printLine("usedRam: " + usedRam + " curRam: " + currentRam);
		if(usedRam > currentRam) {
			Constants.nodeEnough = false;
		}
		//Log.printLine("peInUse: " + usedPes);
		if (pesInUse > currentCPUs) {
			Constants.nodeEnough = false;
			capacity /= pesInUse;
		} else {
			capacity /= currentCPUs;
		}
		return capacity;
	}

	/**
	 * Cancels execution of a cloudlet.
	 * 
	 * @param cloudletId ID of the cloudlet being cancealed
	 * @return the canceled cloudlet, $null if not found
	 * @pre $none
	 * @post $none
	 */
	@Override
	public Cloudlet cloudletCancel(int cloudletId) {
		boolean found = false;
		int position = 0;

		// First, looks in the finished queue
		found = false;
		for (ResCloudlet rcl : getCloudletFinishedList()) {
			if (rcl.getCloudletId() == cloudletId) {
				found = true;
				break;
			}
			position++;
		}

		if (found) {
			return getCloudletFinishedList().remove(position).getCloudlet();
		}

		// Then searches in the exec list
		position=0;
		for (ResCloudlet rcl : getCloudletExecList()) {
			if (rcl.getCloudletId() == cloudletId) {
				//usedPes -= ((Job)rcl.getCloudlet()).getNumberOfPes();
				//usedRam -= ((Job)rcl.getCloudlet()).getRam();
				found = true;
				break;
			}
			position++;
		}

		if (found) {
			ResCloudlet rcl = getCloudletExecList().remove(position);
			if (rcl.getRemainingCloudletLength() == 0) {
				cloudletFinish(rcl);
			} else {
				//rcl.setCloudletStatus(Cloudlet.CANCELED);
				rcl.updateCloudlet();
			}
			return rcl.getCloudlet();
		}

		// Now, looks in the paused queue
		found = false;
		position=0;
		for (ResCloudlet rcl : getCloudletPausedList()) {
			if (rcl.getCloudletId() == cloudletId) {
				found = true;
				rcl.setCloudletStatus(Cloudlet.CANCELED);
				break;
			}
			position++;
		}

		if (found) {
			return getCloudletPausedList().remove(position).getCloudlet();
		}

		return null;
	}

	/**
	 * Pauses execution of a cloudlet.
	 * 
	 * @param cloudletId ID of the cloudlet being paused
	 * @return $true if cloudlet paused, $false otherwise
	 * @pre $none
	 * @post $none
	 */
	@Override
	public boolean cloudletPause(int cloudletId) {
		boolean found = false;
		int position = 0;

		for (ResCloudlet rcl : getCloudletExecList()) {
			if (rcl.getCloudletId() == cloudletId) {
				found = true;
				break;
			}
			position++;
		}

		if (found) {
			// remove cloudlet from the exec list and put it in the paused list
			ResCloudlet rcl = getCloudletExecList().remove(position);
			if (rcl.getRemainingCloudletLength() == 0) {
				cloudletFinish(rcl);
			} else {
				rcl.setCloudletStatus(Cloudlet.PAUSED);
				getCloudletPausedList().add(rcl);
			}
			return true;
		}
		return false;
	}

	/**
	 * Processes a finished cloudlet.
	 * 
	 * @param rcl finished cloudlet
	 * @pre rgl != $null
	 * @post $none
	 */
	@Override
	public void cloudletFinish(ResCloudlet rcl) {
		rcl.setCloudletStatus(Cloudlet.SUCCESS);
		rcl.finalizeCloudlet();
		getCloudletFinishedList().add(rcl);
	}

	/**
	 * Resumes execution of a paused cloudlet.
	 * 
	 * @param cloudletId ID of the cloudlet being resumed
	 * @return expected finish time of the cloudlet, 0.0 if queued
	 * @pre $none
	 * @post $none
	 */
	@Override
	public double cloudletResume(int cloudletId) {
		boolean found = false;
		int position = 0;

		// look for the cloudlet in the paused list
		for (ResCloudlet rcl : getCloudletPausedList()) {
			if (rcl.getCloudletId() == cloudletId) {
				found = true;
				break;
			}
			position++;
		}

		if (found) {
			ResCloudlet rgl = getCloudletPausedList().remove(position);
			rgl.setCloudletStatus(Cloudlet.INEXEC);
			getCloudletExecList().add(rgl);

			// calculate the expected time for cloudlet completion
			// first: how many PEs do we have?

			double remainingLength = rgl.getRemainingCloudletLength();
			double estimatedFinishTime = CloudSim.clock()
					+ (remainingLength / (getCapacity(getCurrentMipsShare()) * rgl.getNumberOfPes()));

			return estimatedFinishTime;
		}

		return 0.0;
	}

	/**
	 * Receives an cloudlet to be executed in the VM managed by this scheduler.
	 * 
	 * @param cloudlet the submited cloudlet
	 * @param fileTransferTime time required to move the required files from the SAN to the VM
	 * @return expected finish time of this cloudlet
	 * @pre gl != null
	 * @post $none
	 */
	@Override
	public double cloudletSubmit(Cloudlet cloudlet, double fileTransferTime) {
		Double pauseTime = 0.0;
		if(!((Job) cloudlet).getTaskList().isEmpty()) {
			//Log.printLine("Container " + ((Job)cloudlet).getTaskList().get(0).name + " is submitted with length " + ((Job)cloudlet).getTaskList().get(0).getCloudletLength());
			for(Task t: ((Job)cloudlet).getTaskList()) {
				if(Constants.pause.containsKey(t.getCloudletId()) && t.ifFirstComputeTurn()) {
					//Log.printLine("Container " + t.getCloudletId() + " should be paused");
					pauseTime += Constants.pause.get(t.getCloudletId()).getValue();
					double extraSize = getCapacity(getCurrentMipsShare()) * pauseTime * cloudlet.getNumberOfPes();
					long length = (long) (cloudlet.getCloudletLength() + extraSize);
					cloudlet.setCloudletLength(length);
				}
			}
		}
		ResCloudlet rcl = new ResCloudlet(cloudlet);
		rcl.setCloudletStatus(Cloudlet.INEXEC);
		//Log.printLine("任务" + cloudlet.getCloudletId() + "进入执行队列，长度：" + rcl.getCloudletLength() + " 剩余长度： " + rcl.getRemainingCloudletLength());
		int cpus = 0;
		double rams = 0;
		for(Task t: ((Job)cloudlet).getTaskList()) {
			cpus += t.getNumberOfPes();
			rams += t.getRam();
		}
		for (int i = 0; i < cpus; i++) {
			rcl.setMachineAndPeId(0, i);
		}
		if(fileTransferTime == -1) {
			if ((usedRam + rams) / currentRam > Constants.ramUp || (double) (usedPes + cpus) / (double) currentCPUs > Constants.cpuUp) {
				//Log.printLine("cpu: " + us);
				return Double.MAX_VALUE;
			}
		}
		getCloudletExecList().add(rcl);

		// use the current capacity to estimate the extra amount of
		// time to file transferring. It must be added to the cloudlet length
		double extraSize = getCapacity(getCurrentMipsShare()) * fileTransferTime;
		long length = (long) (cloudlet.getCloudletLength() + extraSize);
		cloudlet.setCloudletLength(length);

		return cloudlet.getCloudletLength() / getCapacity(getCurrentMipsShare());
	}

	/*
	 * (non-Javadoc)
	 * @see cloudsim.CloudletScheduler#cloudletSubmit(cloudsim.Cloudlet)
	 */
	@Override
	public double cloudletSubmit(Cloudlet cloudlet) {
		return cloudletSubmit(cloudlet, 0.0);
	}

	/**
	 * Gets the status of a cloudlet.
	 * 
	 * @param cloudletId ID of the cloudlet
	 * @return status of the cloudlet, -1 if cloudlet not found
	 * @pre $none
	 * @post $none
	 */
	@Override
	public int getCloudletStatus(int cloudletId) {
		for (ResCloudlet rcl : getCloudletExecList()) {
			if (rcl.getCloudletId() == cloudletId) {
				return rcl.getCloudletStatus();
			}
		}
		for (ResCloudlet rcl : getCloudletPausedList()) {
			if (rcl.getCloudletId() == cloudletId) {
				return rcl.getCloudletStatus();
			}
		}
		return -1;
	}

	/**
	 * Get utilization created by all cloudlets.
	 * 
	 * @param time the time
	 * @return total utilization
	 */
	@Override
	public double getTotalUtilizationOfCpu(double time) {
		double totalUtilization = 0;
		for (ResCloudlet gl : getCloudletExecList()) {
			totalUtilization += gl.getCloudlet().getUtilizationOfCpu(time);
		}
		return totalUtilization;
	}

	/**
	 * Informs about completion of some cloudlet in the VM managed by this scheduler.
	 * 
	 * @return $true if there is at least one finished cloudlet; $false otherwise
	 * @pre $none
	 * @post $none
	 */
	@Override
	public boolean isFinishedCloudlets() {
		return getCloudletFinishedList().size() > 0;
	}

	/**
	 * Returns the next cloudlet in the finished list, $null if this list is empty.
	 * 
	 * @return a finished cloudlet
	 * @pre $none
	 * @post $none
	 */
	@Override
	public Cloudlet getNextFinishedCloudlet() {
		if (getCloudletFinishedList().size() > 0) {
			return getCloudletFinishedList().remove(0).getCloudlet();
		}
		return null;
	}

	/**
	 * Returns the number of cloudlets runnning in the virtual machine.
	 * 
	 * @return number of cloudlets runnning
	 * @pre $none
	 * @post $none
	 */
	@Override
	public int runningCloudlets() {
		return getCloudletExecList().size();
	}

	/**
	 * Returns one cloudlet to migrate to another vm.
	 * 
	 * @return one running cloudlet
	 * @pre $none
	 * @post $none
	 */
	@Override
	public Cloudlet migrateCloudlet() {
		ResCloudlet rgl = getCloudletExecList().remove(0);
		rgl.finalizeCloudlet();
		return rgl.getCloudlet();
	}

	/**
	 * Gets the cloudlet exec list.
	 * 
	 * @param <T> the generic type
	 * @return the cloudlet exec list
	 */
	@SuppressWarnings("unchecked")
	protected <T extends ResCloudlet> List<T> getCloudletExecList() {
		return (List<T>) cloudletExecList;
	}

	/**
	 * Sets the cloudlet exec list.
	 * 
	 * @param <T> the generic type
	 * @param cloudletExecList the new cloudlet exec list
	 */
	protected <T extends ResCloudlet> void setCloudletExecList(List<T> cloudletExecList) {
		this.cloudletExecList = cloudletExecList;
	}

	/**
	 * Gets the cloudlet paused list.
	 * 
	 * @param <T> the generic type
	 * @return the cloudlet paused list
	 */
	@SuppressWarnings("unchecked")
	protected <T extends ResCloudlet> List<T> getCloudletPausedList() {
		return (List<T>) cloudletPausedList;
	}

	/**
	 * Sets the cloudlet paused list.
	 * 
	 * @param <T> the generic type
	 * @param cloudletPausedList the new cloudlet paused list
	 */
	protected <T extends ResCloudlet> void setCloudletPausedList(List<T> cloudletPausedList) {
		this.cloudletPausedList = cloudletPausedList;
	}

	/**
	 * Gets the cloudlet finished list.
	 * 
	 * @param <T> the generic type
	 * @return the cloudlet finished list
	 */
	@SuppressWarnings("unchecked")
	protected <T extends ResCloudlet> List<T> getCloudletFinishedList() {
		return (List<T>) cloudletFinishedList;
	}

	/**
	 * Sets the cloudlet finished list.
	 * 
	 * @param <T> the generic type
	 * @param cloudletFinishedList the new cloudlet finished list
	 */
	protected <T extends ResCloudlet> void setCloudletFinishedList(List<T> cloudletFinishedList) {
		this.cloudletFinishedList = cloudletFinishedList;
	}

	/*
	 * (non-Javadoc)
	 * @see cloudsim.CloudletScheduler#getCurrentRequestedMips()
	 */
	@Override
	public List<Double> getCurrentRequestedMips() {
		List<Double> mipsShare = new ArrayList<Double>();
		return mipsShare;
	}

	/*
	 * (non-Javadoc)
	 * @see cloudsim.CloudletScheduler#getTotalCurrentAvailableMipsForCloudlet(cloudsim.ResCloudlet,
	 * java.util.List)
	 */
	@Override
	public double getTotalCurrentAvailableMipsForCloudlet(ResCloudlet rcl, List<Double> mipsShare) {
		return getCapacity(getCurrentMipsShare());
	}

	/*
	 * (non-Javadoc)
	 * @see cloudsim.CloudletScheduler#getTotalCurrentAllocatedMipsForCloudlet(cloudsim.ResCloudlet,
	 * double)
	 */
	@Override
	public double getTotalCurrentAllocatedMipsForCloudlet(ResCloudlet rcl, double time) {
		return 0.0;
	}

	/*
	 * (non-Javadoc)
	 * @see cloudsim.CloudletScheduler#getTotalCurrentRequestedMipsForCloudlet(cloudsim.ResCloudlet,
	 * double)
	 */
	@Override
	public double getTotalCurrentRequestedMipsForCloudlet(ResCloudlet rcl, double time) {
		// TODO Auto-generated method stub
		return 0.0;
	}

	@Override
	public double getCurrentRequestedUtilizationOfRam() {
		double ram = 0;
		for (ResCloudlet cloudlet : cloudletExecList) {
			ram += cloudlet.getCloudlet().getUtilizationOfRam(CloudSim.clock());
		}
		return ram;
	}

	@Override
	public double getCurrentRequestedUtilizationOfBw() {
		double bw = 0;
		for (ResCloudlet cloudlet : cloudletExecList) {
			bw += cloudlet.getCloudlet().getUtilizationOfBw(CloudSim.clock());
		}
		return bw;
	}

	@Override
	public ResCloudlet choseCloudletToMigrate() {
		ResCloudlet ret = null;
		for(ResCloudlet rcl: getCloudletExecList()) {
	    	if(ret == null || ret.getRemainingCloudletLength() > rcl.getRemainingCloudletLength()) {
				ret = rcl;
			}
		}
		return ret;
	}

}
