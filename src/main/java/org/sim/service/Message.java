package org.sim.service;

import org.sim.cloudsimsdn.Log;
import org.sim.cloudsimsdn.sdn.workload.Workload;

public class Message {
    private double period;

    private double periodStatic;
    private String destIP;

    private String srcIP;

    private String name;

    private Double size;
    private double precorded;

    public Message(double period, String dI, String sI, String n, Double size) {
        this.precorded = period * Math.random()*0.5;
        this.period = this.precorded;
//        this.period = 0; // 因为容器一运行就要发消息，所以设为0
        this.periodStatic = period;
        this.destIP = dI;
        this.srcIP = sI;
        this.name = n;
        this.size = size;
    }

    public Workload Tran2Workload(double current) {
        Workload w = new Workload(Constants.workloads.size(), 0, null);
        w.time = current;
        w.submitVmName = srcIP;
        w.destVmName = destIP;
        w.submitPktSize = size;
        w.msgName = name;
        return w;
    }

    public boolean IfSend(long length) {
        period -= length;
        if(period <= 0) {
            period = periodStatic;
            return true;
        }
        return false;
    }

    public void Rest() {
//        period = periodStatic;
        this.period = this.precorded;
    }
}
