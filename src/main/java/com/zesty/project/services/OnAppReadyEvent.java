package com.zesty.project.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
public class OnAppReadyEvent implements ApplicationListener<ApplicationReadyEvent> {

    @Autowired
    private InstanceService instanceService;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent applicationReadyEvent) {
        instanceService.start();
    }
}
