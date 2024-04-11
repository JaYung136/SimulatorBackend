package org.sim.controller;

public class AssignInfo {
    public String app;
    public Double pausestart = 0.0;
    public Double pauseend = 0.0;
    public String ip;
    public Double starttime;
    public Double endtime;
    public String type="cn";
    public String datacenter="A";
    public Double containerperiod;
    AssignInfo(String app, String ip, Double starttime, Double endtime,Double pausestart, Double pauseend, Double containerperiod, String platform){
        this.app = app;
        this.ip = ip;
        this.starttime = starttime;
        this.endtime = endtime;
        this.pausestart = pausestart;
        this.pauseend = pauseend;
        this.containerperiod = containerperiod;
        this.datacenter = platform;
    }
}
