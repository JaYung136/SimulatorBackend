package org.sim.controller;

public class WorkloadResult {
    public int jobid; //周期性，一个job可对应多条workloads
    public int workloadid;
    public String vmid;
    public String status;

    public String starttime;
    public String finishtime;
    public String time;

    public String destid;
    public String msgname;
}

