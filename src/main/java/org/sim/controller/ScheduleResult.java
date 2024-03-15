package org.sim.controller;

public class ScheduleResult {
    public Integer host;
    public String name;
    public Integer cpu;
    public Double ram;

    public ScheduleResult(Integer id, String name, Integer c, Double r) {
        this.host = id;
        this.name = name;
        this.cpu = c;
        this.ram = r;
    }
}
