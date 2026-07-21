package com.acme.tedemo.service.impl;

import com.acme.tedemo.configuration.KeycloakClient;
import com.acme.tedemo.model.PendingApproval;
import com.acme.tedemo.service.DeviceApprovalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class DeviceApprovalServiceImpl implements DeviceApprovalService {

    private static final Logger log = LoggerFactory.getLogger(DeviceApprovalServiceImpl.class);

    // In-memory store of pending approvals — fine for a single-node demo, not persistent.
    private final ConcurrentMap<String, PendingApproval> byId = new ConcurrentHashMap<>();
    private final KeycloakClient kc;

    public DeviceApprovalServiceImpl(KeycloakClient kc) {
        this.kc = kc;
    }

    @Override
    public void record(PendingApproval approval) {
        byId.put(approval.getId(), approval);
        log.info("CIBA approval request received for user='{}' (binding='{}', scope='{}')",
                approval.getLoginHint(), approval.getBindingMessage(), approval.getScope());
    }

    @Override
    public List<PendingApproval> pendingFor(String customerUsername) {
        return byId.values().stream()
                .filter(a -> customerUsername.equals(a.getLoginHint()))
                .sorted(Comparator.comparingLong(PendingApproval::getCreatedAt).reversed())
                .toList();
    }

    @Override
    public void approve(String id, String customerUsername) {
        decide(id, customerUsername, "SUCCEED", PendingApproval.State.APPROVED);
    }

    @Override
    public void deny(String id, String customerUsername) {
        decide(id, customerUsername, "CANCELLED", PendingApproval.State.DENIED);
    }

    // Only act on a request that was actually addressed to the signed-in customer. 
    private void decide(String id, String customer, String status, PendingApproval.State newState) {
        Optional.ofNullable(byId.get(id))
                .filter(approval -> customer.equals(approval.getLoginHint()))
                .ifPresentOrElse(approval -> {
                    String callback = kc.props().realmProtocolBase() + "/ext/ciba/auth/callback";
                    int code = kc.postJsonBearer(callback, approval.getBearerToken(), Map.of("status", status));
                    log.info("CIBA callback status={} for user='{}' -> HTTP {}", status, approval.getLoginHint(), code);
                    approval.setState(newState);
                }, () -> log.warn("'{}' tried to decide request '{}' that isn't theirs (or is unknown)", customer, id));
    }
}
