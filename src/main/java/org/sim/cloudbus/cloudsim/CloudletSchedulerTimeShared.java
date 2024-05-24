/*
 * Title: CloudSim Toolkit Description: CloudSim (Cloud Simulation) Toolkit for Modeling and
 * Simulation of Clouds Licence: GPL - http://www.gnu.org/copyleft/gpl.html
 * 
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.sim.cloudbus.cloudsim;

import org.sim.cloudbus.cloudsim.core.CloudSim;
import org.sim.cloudsimsdn.Log;
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
	 * 根据时间片长度更新任务的状态，包括：
	 * 1）任务是否执行完成
	 * 2）任务是否开始暂停
	 * 3）任务是否需要向其他节点发送消息
	 */
	@Override
	public double updateVmProcessing(double currentTime, List<Double> mipsShare, double ram) {
		setCurrentMipsShare(mipsShare);
		double timeSpam = currentTime - getPreviousTime();
		this.currentRam = ram;
		this.currentCPUs = mipsShare.size();
		// 调用这个函数后，如果需要对CPU进行分时复用，那么 mips * task.core 就是任务执行的速度
		double mips = getCapacity(mipsShare);
		int taskNum = getCloudletExecList().size();
		int coreNeed;
		List<ResCloudlet> toRemove = new ArrayList<ResCloudlet>();
		List<ResCloudlet> toPause = new ArrayList<>();
		//更新每个任务的运行状态并检查有无： 1）运行结束的任务； 2）开始暂停的任务
		for (ResCloudlet rcl : getCloudletExecList()) {
			coreNeed = 0;
			if(!((Job) rcl.getCloudlet()).getTaskList().isEmpty()) {
				for(Task t: ((Job)rcl.getCloudlet()).getTaskList())
					coreNeed += t.getNumberOfPes();
			}
			long length = 0;
			// 如果任务数小于核心数，那么每个任务占用一个核
			if(taskNum <= mipsShare.size() / 1000)
				length = (long) (mipsShare.get(0) * timeSpam);
			// 否则进行分时复用
			else
				length = (long) (mips * timeSpam * coreNeed);
			// 我们将 length 乘以 Consts.MILLION（这是因为在设置 CloudletFinishedSoFar 的初始值是就设为 cloudlet.length * Consts.MILLION）
			rcl.updateCloudletFinishedSoFar( length * Consts.MILLION);
			// 由任务来判断是否需要发送消息
			((Job)rcl.getCloudlet()).SendMessage(length, currentTime);
			// 如果任务运行结束或开始暂停，我们将任务从运行队列中剔除出去
			if(rcl.getRemainingCloudletLength() <= 0) {
				toRemove.add(rcl);
				cloudletFinish(rcl);
				for(Task t: ((Job)rcl.getCloudlet()).getTaskList()) {
					t.setTaskFinishTime(currentTime);
				}
				((Job)rcl.getCloudlet()).ResetMessage();
			}else if(((Job)rcl.getCloudlet()).IfStartPause(length)) {
				toPause.add(rcl);
				Log.printLine(currentTime + " : " + ((Job)rcl.getCloudlet()).getTaskList().get(0).name + " 开始暂停");
			}
		}
		if (getCloudletExecList().isEmpty() && getCloudletPausedList().isEmpty()) {
			setPreviousTime(currentTime);
			return 0.0;
		}
		// 将执行完成或者开始暂停的任务从执行队列中删除
		getCloudletExecList().removeAll(toRemove);
		getCloudletExecList().removeAll(toPause);
		double nextEvent = Double.MAX_VALUE;
		List<ResCloudlet> toExec = new ArrayList<ResCloudlet>();
		for(ResCloudlet rcl: getCloudletPausedList()) {
			coreNeed = 0;
			if(!((Job) rcl.getCloudlet()).getTaskList().isEmpty()) {
				for(Task t: ((Job)rcl.getCloudlet()).getTaskList())
					coreNeed += t.getNumberOfPes();
			}
			long length = (long) (mipsShare.get(0) * timeSpam);
			if(((Job) rcl.getCloudlet()).IfEndPause(length)) {
				toExec.add(rcl);
				Log.printLine(currentTime + " : " + ((Job)rcl.getCloudlet()).getTaskList().get(0).name + " 结束暂停");
			}
		}
		// 将开始暂停的任务加入暂停队列中
		getCloudletPausedList().addAll(toPause);
		// 将暂停结束的任务从暂停队列中删除，加入执行队列
		getCloudletExecList().addAll(toExec);
		getCloudletPausedList().removeAll(toExec);
		// 预估下一次任务完成需要的时间
		for (ResCloudlet rcl : getCloudletExecList()) {
			double estimatedFinishTime = currentTime
					+ (rcl.getRemainingCloudletLength() / (getCapacity(mipsShare) * rcl.getNumberOfPes()));
			if (estimatedFinishTime - currentTime < CloudSim.getMinTimeBetweenEvents()) {
				estimatedFinishTime = currentTime + CloudSim.getMinTimeBetweenEvents();
			}
			if (estimatedFinishTime < nextEvent) {
				nextEvent = estimatedFinishTime;
			}
		}
		for (ResCloudlet rcl: getCloudletPausedList()) {
			double estimatedFinishTime = currentTime
					+ (rcl.getRemainingCloudletLength() / (getCapacity(mipsShare) * rcl.getNumberOfPes()));
			if (estimatedFinishTime - currentTime < CloudSim.getMinTimeBetweenEvents()) {
				estimatedFinishTime = currentTime + CloudSim.getMinTimeBetweenEvents();
			}
			if (estimatedFinishTime < nextEvent) {
				nextEvent = estimatedFinishTime;
			}
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
		// cpus 就是所在物理节点的核心数 * 1000
		int cpus = mipsShare.size();
		currentCPUs = cpus;
		int taskNum = 0;
		int pesInUse = 0;
		double ramInUse = 0;
		double rate = 1.0;
		for (ResCloudlet rcl : getCloudletExecList()) {
			/*if(rcl.getCloudletStatus() != Cloudlet.INEXEC) {
				rate = 0.1;
			}*/
			if(!((Job) rcl.getCloudlet()).getTaskList().isEmpty()) {
				for(Task t: ((Job)rcl.getCloudlet()).getTaskList()) {
					pesInUse += t.getNumberOfPes() * rate;
					ramInUse += t.getRam() * rate;
					taskNum ++;
				}
			}
		}
		usedPes = Math.min(currentCPUs, pesInUse);
		usedRam = Math.min(currentRam, ramInUse);
		// 如果任务书小于核数，那么每个任务占用一个核；否则进行分时复用
		if (taskNum > (currentCPUs / 1000)) {
			return mipsShare.get(0) / pesInUse;
		} else {
			return mipsShare.get(0);
		}
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
				found = true;
				break;
			}
			position++;
		}

		if (found) {
			ResCloudlet rcl = getCloudletExecList().remove(position);
			if (rcl.getRemainingCloudletLength() == 0) {
				cloudletFinish(rcl);
				((Job)rcl.getCloudlet()).ResetMessage();
			} else {
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
	 * 接收一个任务，并判断是否有足够资源承接
	 * 注意：我们在调度时已经保证了资源是足够的，只有仿真是发生了迁移才可能资源不够
	 */
	@Override
	public double cloudletSubmit(Cloudlet cloudlet, double fileTransferTime) {
		ResCloudlet rcl = new ResCloudlet(cloudlet);
		rcl.setCloudletStatus(Cloudlet.INEXEC);
		int cpus = 0;
		double rams = 0;
		for(Task t: ((Job)cloudlet).getTaskList()) {
			cpus += t.getNumberOfPes();
			rams += t.getRam();
		}
		for (int i = 0; i < cpus; i++) {
			rcl.setMachineAndPeId(0, i);
		}
		//如果为-1，表示此时任务是迁移过来的任务
		if(fileTransferTime == -1) {
			if ((usedRam + rams) / currentRam > Constants.ramUp || (double) (usedPes + cpus) / (double) currentCPUs > Constants.cpuUp) {
				return Double.MAX_VALUE;
			}
		}
		getCloudletExecList().add(rcl);
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
