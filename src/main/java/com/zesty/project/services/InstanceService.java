package com.zesty.project.services;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Region;

import java.util.List;

public interface InstanceService {
    void start();
    List<Instance> getSortedInstanceByLunchTime(Regions region);
}
