package com.acme.tedemo.service;

import com.acme.tedemo.model.PendingApproval;

import java.util.List;


public interface DeviceApprovalService {


    void record(PendingApproval approval);


    List<PendingApproval> pendingFor(String customerUsername);


    void approve(String id, String customerUsername);

    
    void deny(String id, String customerUsername);
}
