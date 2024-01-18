package org.sim.service.result;

public class LogEntity {
    public String time;
    public String cpuUtilization;
    public String ramUtilization;
    public Integer hostId;

    public LogEntity(String t, String c, String r, Integer i) {
        this.time = t;
        this.cpuUtilization = c;
        this.ramUtilization = r;
        this.hostId = i;
    }
}
