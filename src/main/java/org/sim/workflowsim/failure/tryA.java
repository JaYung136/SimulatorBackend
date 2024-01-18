package org.sim.workflowsim.failure;


import org.apache.commons.math3.distribution.*;
import org.sim.cloudbus.cloudsim.Log;
import org.sim.workflowsim.utils.DistributionGenerator;

public class tryA {
    public static void main(String[] args) {
        RealDistribution distribution = new BetaDistribution(0.1, 0.1);
        double[] ret = distribution.sample(20);
        for(double d: ret) {
            Log.printLine(d);
        }
    }
}
