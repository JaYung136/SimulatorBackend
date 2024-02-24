/**
 * Copyright 2012-2013 University Of Southern California
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.sim.workflowsim;

import org.sim.cloudbus.cloudsim.*;
import org.sim.cloudbus.cloudsim.core.CloudSim;
import org.sim.cloudbus.cloudsim.core.CloudSimTags;
import org.sim.cloudbus.cloudsim.core.SimEntity;
import org.sim.cloudbus.cloudsim.core.SimEvent;
import org.sim.service.Constants;
import org.sim.workflowsim.reclustering.ReclusteringEngine;
import org.sim.workflowsim.utils.Parameters;

import java.util.*;

/**
 * WorkflowEngine represents a engine acting on behalf of a user. It hides VM
 * management, as vm creation, submission of cloudlets to this VMs and
 * destruction of VMs.
 *
 * @author Weiwei Chen
 * @since WorkflowSim Toolkit 1.0
 * @date Apr 9, 2013
 */
public final class WorkflowEngine extends SimEntity {
    Boolean shouldStop = false;

    Boolean start = true;

    Map<String, Integer> computeTime = new HashMap<>();

    Map<Job, Double> startTime = new HashMap<>();

    List<Integer> sucs = new ArrayList<>();

    private VmAllocationPolicy vmAllocationPolicy;
    /**
     * The job list.
     */
    protected List<? extends Cloudlet> jobsList;
    /**
     * The job submitted list.
     */
    protected List<? extends Cloudlet> jobsSubmittedList;
    /**
     * The job received list.
     */
    protected List<? extends Cloudlet> jobsReceivedList;
    /**
     * The job submitted.
     */
    protected int jobsSubmitted;
    protected List<? extends Vm> vmList;
    protected List<? extends Host> hostList;
    /**
     * The associated scheduler id*
     */
    private List<Integer> schedulerId;
    private List<WorkflowScheduler> scheduler;

    /**
     * Created a new WorkflowEngine object.
     *
     * @param name name to be associated with this entity (as required by
     * Sim_entity class from simjava package)
     * @throws Exception the exception
     * @pre name != null
     * @post $none
     */
    public WorkflowEngine(String name) throws Exception {
        this(name, 1);
    }

    private Boolean shouldStop() {
        for(Map.Entry e: computeTime.entrySet()) {
            if((Integer)e.getValue() < Constants.repeatTime) {
                //Log.printLine((String)e.getKey() + " still need repeat");
                return false;
            }
        }
        return true;
    }

    public WorkflowEngine(String name, int schedulers) throws Exception {
        super(name);

        setJobsList(new ArrayList<>());
        setJobsSubmittedList(new ArrayList<>());
        setJobsReceivedList(new ArrayList<>());

        jobsSubmitted = 0;

        setSchedulers(new ArrayList<>());
        setSchedulerIds(new ArrayList<>());

        for (int i = 0; i < schedulers; i++) {
            WorkflowScheduler wfs = new WorkflowScheduler(name + "_Scheduler_" + i);
            getSchedulers().add(wfs);
            getSchedulerIds().add(wfs.getId());
            wfs.setWorkflowEngineId(this.getId());
        }
    }

    /**
     * This method is used to send to the broker the list with virtual machines
     * that must be created.
     *
     * @param list the list
     * @param schedulerId the scheduler id
     */
    public void submitVmList(List<? extends Vm> list, int schedulerId) {
        getScheduler(schedulerId).submitVmList(list);
    }

    public void submitHostList(List<? extends Host> list, int schedulerId) {
        this.hostList = new ArrayList<>(list);
        getScheduler(schedulerId).submitHostList(list);
    }

    public void submitVmList(List<? extends Vm> list) {
        //bug here, not sure whether we should have different workflow schedulers
        getScheduler(0).submitVmList(list);
        setVmList(list);
    }
    
    public List<? extends Vm> getAllVmList(){
        if(this.vmList != null && !this.vmList.isEmpty()){
            return this.vmList;
        }
        else{
            List list = new ArrayList();
            for(int i = 0;i < getSchedulers().size();i ++){
                list.addAll(getScheduler(i).getVmList());
            }
            return list;
        }
    }

    /**
     * This method is used to send to the broker the list of cloudlets.
     *
     * @param list the list
     */
    public void submitCloudletList(List<? extends Cloudlet> list) {
        getJobsList().addAll(list);
    }

    /**
     * Processes events available for this Broker.
     *
     * @param ev a SimEvent object
     */
    @Override
    public void processEvent(SimEvent ev) {
        if(shouldStop){
            return;
        }
        //Log.printLine("WorkflowEngine: " + ev.getTag());
        switch (ev.getTag()) {
            // Resource characteristics request
            case CloudSimTags.RESOURCE_CHARACTERISTICS_REQUEST:
                processResourceCharacteristicsRequest(ev);
                break;
            //this call is from workflow scheduler when all vms are created
            case CloudSimTags.CLOUDLET_SUBMIT:
                submitJobs();
                break;
            case CloudSimTags.CLOUDLET_RETURN:
                processJobReturn(ev);
                break;
            case CloudSimTags.END_OF_SIMULATION:
                shutdownEntity();
                break;
            case WorkflowSimTags.JOB_SUBMIT:
                processJobSubmit(ev);
                break;
            case WorkflowSimTags.JOB_NEED_REPEAT:
                processJobRepeat(ev);
                break;
            case WorkflowSimTags.ENGINE_EVENT:
                processEngineEvent();
                break;
            case CloudSimTags.VM_CREATE:
                try {
                    processVmCreate(ev);
                }catch (Exception e) {

                }
            default:
                processOtherEvent(ev);
                break;
        }
    }

    /**
     * Process a request for the characteristics of a PowerDatacenter.
     *
     * @param ev a SimEvent object
     */
    protected void processResourceCharacteristicsRequest(SimEvent ev) {
        for (int i = 0; i < getSchedulerIds().size(); i++) {
            schedule(getSchedulerId(i), 0, CloudSimTags.RESOURCE_CHARACTERISTICS_REQUEST);
        }
    }

    protected void processEngineEvent() {
        Boolean ifRepeat = false;
        for(Iterator<Job> iterator = startTime.keySet().iterator(); iterator.hasNext(); ) {
            Job job = iterator.next();
            Double start = startTime.get(job);
            //Log.printLine(job.getTaskList().get(0).name + " need to repeat at " + start);
            if(CloudSim.clock() >= start) {
                ifRepeat = true;
                iterator.remove();
                int newId = getJobsList().size() + getJobsSubmittedList().size();
                // Log.printLine("jobs: " + getJobsList().size());
                getJobsList().addAll(ReclusteringEngine.process(job, newId));
            }
        }
        send(this.getId(), 1, WorkflowSimTags.ENGINE_EVENT);
        if(ifRepeat)
            sendNow(this.getId(), CloudSimTags.CLOUDLET_SUBMIT, null);
    }

    protected void processJobRepeat(SimEvent ev) {
        Job job = (Job) ev.getData();
        int newId = getJobsList().size() + getJobsSubmittedList().size();
        // Log.printLine("jobs: " + getJobsList().size());
        getJobsList().addAll(ReclusteringEngine.process(job, newId));
    }
    /**
     * Binds a scheduler with a datacenter.
     *
     * @param datacenterId the data center id
     * @param schedulerId the scheduler id
     */
    public void bindSchedulerDatacenter(int datacenterId, int schedulerId) {
        getScheduler(schedulerId).bindSchedulerDatacenter(datacenterId);
    }

    /**
     * Binds a datacenter to the default scheduler (id=0)
     *
     * @param datacenterId dataceter Id
     */
    public void bindSchedulerDatacenter(int datacenterId) {
        bindSchedulerDatacenter(datacenterId, 0);
    }
   
    /**
     * Process a submit event
     *
     * @param ev a SimEvent object
     */
    protected void processJobSubmit(SimEvent ev) {
        List<? extends Cloudlet> list = (List) ev.getData();
        Log.printLine("rec: " + list.size());
        this.vmAllocationPolicy = new VmAllocationPolicyK8s(Constants.hosts);
        setJobsList(list);
        for(Cloudlet j: list) {
            Job job = (Job)j;
            if(job.getTaskList().size() >= 1) {
                computeTime.put(job.getTaskList().get(0).name, 0);
            }
        }
    }

    private void processVmCreate(SimEvent ev) throws Exception {
        Log.printLine("尝试创建容器");
        List<Job> list = (List) ev.getData();

        for(Job job: list) {
            if(job.getTaskList().size() == 0) {
                job.setCloudletStatus(Cloudlet.SUCCESS);
                sendNow(getId(), CloudSimTags.CLOUDLET_RETURN, job);
                continue;
            }
            Task task = job.getTaskList().get(0);
            CondorVM containerTmp = new CondorVM(task.getCloudletId(), 1, 0, task.getNumberOfPes(), (int) task.getRam(), 0, 0, "Xen", new CloudletSchedulerTimeShared());
            boolean result = this.vmAllocationPolicy.allocateHostForVm(containerTmp);
            if(!result) {
                this.shouldStop = true;
                Constants.nodeEnough = false;
                Log.printLine("节点资源不足");
            }else{
                job.setCloudletStatus(Cloudlet.SUCCESS);
                sendNow(getId(), CloudSimTags.CLOUDLET_RETURN, job);
                Log.printLine(task.name + "被分配至节点" + this.vmAllocationPolicy.getHost(containerTmp).getName());
                Constants.schedulerResult.put(task.name, this.vmAllocationPolicy.getHost(containerTmp).getId());
            }
        }
    }

    /**
     * Process a job return event.
     *
     * @param ev a SimEvent object
     * @pre ev != $null
     * @post $none
     */
    protected void processJobReturn(SimEvent ev) {
        Job job = (Job) ev.getData();
        //Log.print("任务 " + job.getCloudletId() + "  结束，");
        if (job.getCloudletStatus() == Cloudlet.FAILED) {
            // Reclusteringengine will add retry job to jobList
            if(job.getTaskList().size() >= 1) {
                job.getTaskList().get(0).finishCompute();
            }
            int newId = getJobsList().size() + getJobsSubmittedList().size();
            getJobsList().addAll(ReclusteringEngine.process(job, newId));
            //Log.print("执行失败\n");
        } else {
            //Log.print("执行成功\n");
            if(job.getTaskList().size() >= 1) {
                job.getTaskList().get(0).finishCompute();
                Integer time = computeTime.get(job.getTaskList().get(0).name);
                if(time == null) {
                    time = 1;
                }else {
                    time ++;
                    Log.printLine(job.getTaskList().get(0).name + " 结束周期 " + time);

                }
                computeTime.put(job.getTaskList().get(0).name, time);
               // schedule(getId(), job.getTaskList().get(0).getPeriodTime(), WorkflowSimTags.JOB_NEED_REPEAT, job);
                if(time < Constants.repeatTime)
                    startTime.put(job, CloudSim.clock() + job.getTaskList().get(0).getPeriodTime());
            }
        }

        getJobsReceivedList().add(job);
        jobsSubmitted--;
        //Log.printLine("Job submitted: " + jobsSubmitted  + "job received: " + getJobsReceivedList().size());
        if (getJobsList().isEmpty() && jobsSubmitted == 0 && shouldStop()) {
            //send msg to all the schedulers
            shouldStop = true;
            Constants.finishTime = CloudSim.clock();
            Log.printLine("任务全部执行结束，记录当前时间：" + Constants.finishTime);
            for (int i = 0; i < getSchedulerIds().size(); i++) {
                sendNow(getSchedulerId(i), CloudSimTags.END_OF_SIMULATION, null);
            }
        } else {
            sendNow(this.getId(), CloudSimTags.CLOUDLET_SUBMIT, null);
        }
        if(start) {
            send(this.getId(), 1, WorkflowSimTags.ENGINE_EVENT);
            start = false;
        }
    }

    /**
     * Overrides this method when making a new and different type of Broker.
     *
     *
     * @param ev a SimEvent object
     */
    protected void processOtherEvent(SimEvent ev) {
        /*if (ev == null) {
            Log.printLine(getName() + ".processOtherEvent(): " + "Error - an event is null.");
            return;
        }
        Log.printLine(getName() + ".processOtherEvent(): "
                + "Error - event unknown by this DatacenterBroker.");*/
    }

    /**
     * Checks whether a job list contains a id
     *
     * @param jobList the job list
     * @param id the job id
     * @return
     */
    private boolean hasJobListContainsID(List jobList, int id) {
        for (Iterator it = jobList.iterator(); it.hasNext();) {
            Job job = (Job) it.next();
            if (job.getCloudletId() == id) {
                return true;
            }
        }
        return false;
    }

    /**
     * Submit jobs to the created VMs.
     *
     * @pre $none
     * @post $none
     */
    protected void submitJobs() {
       // Log.printLine("Workflow engine submit "+getJobsList().size()+"jobs");
        List<Job> list = getJobsList();
        Map<Integer, List> allocationList = new HashMap<>();
        for (int i = 0; i < getSchedulers().size(); i++) {
            List<Job> submittedList = new ArrayList<>();
            allocationList.put(getSchedulerId(i), submittedList);
        }
        int num = list.size();
        for (int i = 0; i < num; i++) {
            //at the beginning
            Job job = list.get(i);
            //Dont use job.isFinished() it is not right
            if (!hasJobListContainsID(this.getJobsReceivedList(), job.getCloudletId())) {
                List<Job> parentList = job.getParentList();
                boolean flag = true;
                for (Job parent : parentList) {
                    if (!hasJobListContainsID(this.getJobsReceivedList(), parent.getCloudletId())) {
                        flag = false;
                        break;
                    }
                }
                /**
                 * This job's parents have all completed successfully. Should
                 * submit.
                 */
                if (flag) {
                    List submittedList = allocationList.get(job.getUserId());
                    submittedList.add(job);
                    jobsSubmitted++;
                    getJobsSubmittedList().add(job);
                    list.remove(job);
                    i--;
                    num--;
                    //break;
                }
            }

        }
        /**
         * If we have multiple schedulers. Divide them equally.
         */
        for (int i = 0; i < getSchedulers().size(); i++) {

            List submittedList = allocationList.get(getSchedulerId(i));
            //divid it into sublist

            int interval = Parameters.getOverheadParams().getWEDInterval();

            double delay = 0.0;
            if(Parameters.getOverheadParams().getWEDDelay()!=null){
                delay = Parameters.getOverheadParams().getWEDDelay(submittedList);
            }

            double delaybase = delay;
            int size = submittedList.size();
            Log.printLine(size);
            if (interval > 0 && interval <= size) {
                int index = 0;
                List subList = new ArrayList();
                while (index < size) {
                    subList.add(submittedList.get(index));
                    index++;
                    if (index % interval == 0) {
                        //create a new one
                        //Log.printLine("cc");
                        schedule(getSchedulerId(i), delay, CloudSimTags.CLOUDLET_SUBMIT, subList);
                        delay += delaybase;
                        subList = new ArrayList();
                    }
                }
                if (!subList.isEmpty()) {
                    schedule(getSchedulerId(i), delay, CloudSimTags.CLOUDLET_SUBMIT, subList);
                }
            } else if (!submittedList.isEmpty()) {
                if(!Constants.ifSimulate && Parameters.getSchedulingAlgorithm() == Parameters.SchedulingAlgorithm.K8S)
                    sendNow(this.getId(), CloudSimTags.VM_CREATE, submittedList);
                else
                    sendNow(this.getSchedulerId(i), CloudSimTags.CLOUDLET_SUBMIT, submittedList);
            }
        }
    }

    /*
     * (non-Javadoc)
     * @see cloudsim.core.SimEntity#shutdownEntity()
     */
    @Override
    public void shutdownEntity() {
        //Log.printLine(getName() + " is shutting down...");
    }

    /*
     * (non-Javadoc)
     * @see cloudsim.core.SimEntity#startEntity()
     * Here we creata a message when it is started
     */
    @Override
    public void startEntity() {
        //Log.printLine(getName() + " Workflow engine is starting... id is " + getId());
        schedule(getId(), 0, CloudSimTags.RESOURCE_CHARACTERISTICS_REQUEST);
    }

    /**
     * Gets the job list.
     *
     * @param <T> the generic type
     * @return the job list
     */
    @SuppressWarnings("unchecked")
    public <T extends Cloudlet> List<T> getJobsList() {
        return (List<T>) jobsList;
    }

    /**
     * Sets the job list.
     *
     * @param <T> the generic type
     * @param jobsList the new job list
     */
    private <T extends Cloudlet> void setJobsList(List<T> jobsList) {
        this.jobsList = jobsList;
    }

    /**
     * Gets the job submitted list.
     *
     * @param <T> the generic type
     * @return the job submitted list
     */
    @SuppressWarnings("unchecked")
    public <T extends Cloudlet> List<T> getJobsSubmittedList() {
        return (List<T>) jobsSubmittedList;
    }

    /**
     * Sets the job submitted list.
     *
     * @param <T> the generic type
     * @param jobsSubmittedList the new job submitted list
     */
    private <T extends Cloudlet> void setJobsSubmittedList(List<T> jobsSubmittedList) {
        this.jobsSubmittedList = jobsSubmittedList;
    }

    /**
     * Gets the job received list.
     *
     * @param <T> the generic type
     * @return the job received list
     */
    @SuppressWarnings("unchecked")
    public <T extends Cloudlet> List<T> getJobsReceivedList() {
        return (List<T>) jobsReceivedList;
    }

    /**
     * Sets the job received list.
     *
     * @param <T> the generic type
     * @param jobsReceivedList the new job received list
     */
    private <T extends Cloudlet> void setJobsReceivedList(List<T> jobsReceivedList) {
        this.jobsReceivedList = jobsReceivedList;
    }

    /**
     * Gets the vm list.
     *
     * @param <T> the generic type
     * @return the vm list
     */
    @SuppressWarnings("unchecked")
    public <T extends Vm> List<T> getVmList() {
        return (List<T>) vmList;
    }

    /**
     * Sets the vm list.
     *
     * @param <T> the generic type
     * @param vmList the new vm list
     */
    private <T extends Vm> void setVmList(List<T> vmList) {
        this.vmList = vmList;
    }

    /**
     * Gets the schedulers.
     *
     * @return the schedulers
     */
    public List<WorkflowScheduler> getSchedulers() {
        return this.scheduler;
    }

    /**
     * Sets the scheduler list.
     *
     * @param list the new scheduler list
     */
    private void setSchedulers(List list) {
        this.scheduler = list;
    }

    /**
     * Gets the scheduler id.
     *
     * @return the scheduler id
     */
    public List<Integer> getSchedulerIds() {
        return this.schedulerId;
    }

    /**
     * Sets the scheduler id list.
     *
     * @param list the new scheduler id list
     */
    private void setSchedulerIds(List list) {
        this.schedulerId = list;
    }

    /**
     * Gets the scheduler id list.
     *
     * @param index
     * @return the scheduler id list
     */
    public int getSchedulerId(int index) {
        if (this.schedulerId != null) {
            return this.schedulerId.get(index);
        }
        return 0;
    }

    /**
     * Gets the scheduler .
     *
     * @param schedulerId
     * @return the scheduler
     */
    public WorkflowScheduler getScheduler(int schedulerId) {
        if (this.scheduler != null) {
            return this.scheduler.get(schedulerId);
        }
        return null;
    }
}
