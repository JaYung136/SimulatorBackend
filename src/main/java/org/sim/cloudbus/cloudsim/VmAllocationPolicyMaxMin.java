package org.sim.cloudbus.cloudsim;

import org.sim.workflowsim.CondorVM;

import java.util.ArrayList;
import java.util.List;

public class VmAllocationPolicyMaxMin extends VmAllocationPolicySimple{

    /**
     * Creates the new VmAllocationPolicySimple object.
     *
     * @param list the list
     * @pre $none
     * @post $none
     */
    public VmAllocationPolicyMaxMin(List<? extends Host> list) {
        super(list);
    }

    @Override
    public boolean scheduleAll() {
        while(true) {
            Vm maxVm = null;
            for(Vm v1: getContainerList()) {
                if(!getVmTable().containsKey(v1.getUid())) {
                    maxVm = v1;
                    break;
                }
            }
            if(maxVm == null)
                return true;
            for(Vm v2: getContainerList()) {
                if(((CondorVM)v2).getLength() > ((CondorVM)maxVm).getLength() && !getVmTable().containsKey(v2.getUid())) {
                    maxVm = v2;
                }
            }
            if(!allocateHostForVm(maxVm))
                return false;
        }
    }

    @Override
    public boolean allocateHostForVm(Vm vm) {
        int requiredPes = vm.getNumberOfPes();
        boolean result = false;
        int tries = 0;
        List<Integer> freePesTmp = new ArrayList<Integer>();
        for (Integer freePes : getFreePes()) {
            freePesTmp.add(freePes);
        }

        if (!getVmTable().containsKey(vm.getUid())) {
            do {
                int moreFree = Integer.MIN_VALUE;
                int idx = -1;
                for (int i = 0; i < freePesTmp.size(); i++) {
                    if(freePesTmp.get(i) == Integer.MIN_VALUE) {
                        continue;
                    }
                    int totalUsedPes = getHostList().get(i).getNumberOfPes() - freePesTmp.get(i);
                    if (totalUsedPes > moreFree) {
                        moreFree = totalUsedPes;
                        idx = i;
                    }
                }
                if(idx == -1) {
                    return false;
                }
                //Log.printLine(((CondorVM)vm).getName() + " 选中 " + idx);
                Host host = getHostList().get(idx);
                result = host.vmCreate(vm);

                if (result) {
                    getVmTable().put(vm.getUid(), host);
                    getUsedPes().put(vm.getUid(), requiredPes);
                    getFreePes().set(idx, getFreePes().get(idx) - requiredPes);
                    result = true;
                    break;
                } else {
                    freePesTmp.set(idx, Integer.MIN_VALUE);
                }
                tries++;
            } while (!result && tries < getFreePes().size());
        }
        return result;
    }
}
