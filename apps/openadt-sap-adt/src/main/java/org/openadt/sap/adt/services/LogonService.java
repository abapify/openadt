package org.openadt.sap.adt.services;

import com.sap.adt.destinations.logon.AdtLogonServiceFactory;
import com.sap.adt.destinations.logon.IAdtLogonService;
import com.sap.adt.destinations.model.IDestinationData;
import com.sap.adt.destinations.model.IAuthenticationToken;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.openadt.config.CliLog;
import org.openadt.config.OpenAdtException;
import org.openadt.sap.adt.sdk.AdtLogonStatusReport;
/**
 * SDK logon checks via {@link IAdtLogonService}.
 */
public final class LogonService {
    private static final Set<String> LOGGED_ON_DESTINATIONS = ConcurrentHashMap.newKeySet();
    private static final Map<String, Object> LOGON_LOCKS = new ConcurrentHashMap<>();

    private final IAdtLogonService logonService;

    public LogonService() {
        this(AdtLogonServiceFactory.createLogonService());
    }

    LogonService(IAdtLogonService logonService) {
        this.logonService = logonService;
    }

    public static void resetForTests() {
        LOGGED_ON_DESTINATIONS.clear();
        LOGON_LOCKS.clear();
    }

    public static void ensureLoggedOnForTransport(IDestinationData destinationData, IAuthenticationToken token)
        throws Exception {
        String destinationId = destinationData.getId();
        if (LOGGED_ON_DESTINATIONS.contains(destinationId)) {
            return;
        }
        Object lock = LOGON_LOCKS.computeIfAbsent(destinationId, id -> new Object());
        synchronized (lock) {
            if (LOGGED_ON_DESTINATIONS.contains(destinationId)) {
                return;
            }
            IProgressMonitor monitor = new NullProgressMonitor();
            CliLog.sdk("AdtLogonServiceFactory.createLogonService().ensureLoggedOn (first time for " + destinationId + ")");
            IAdtLogonService service = AdtLogonServiceFactory.createLogonService();
            IStatus status = service.ensureLoggedOn(destinationData, token, monitor);
            if (status != null && !status.isOK()) {
                throw new OpenAdtException("Failed to log on through ADT SDK: " + status.getMessage());
            }
            LOGGED_ON_DESTINATIONS.add(destinationId);
        }
    }

    public AdtLogonStatusReport status(SapAdtSessionContext context) {
        String destinationId = context.destinationId();
        boolean loggedOn = logonService.isLoggedOn(destinationId);
        String message = loggedOn ? "logged on" : "not logged on";
        return new AdtLogonStatusReport(loggedOn, destinationId, context.fromEclipse(), message);
    }

    public AdtLogonStatusReport logon(SapAdtSessionContext context) {
        IDestinationData destinationData = context.destinationData();
        IAuthenticationToken token = context.authenticationToken();
        String destinationId = destinationData.getId();
        IProgressMonitor monitor = new NullProgressMonitor();
        CliLog.sdk("AdtLogonServiceFactory.createLogonService().ensureLoggedOn (" + destinationId + ")");
        IStatus status = logonService.ensureLoggedOn(destinationData, token, monitor);
        if (status != null && !status.isOK()) {
            throw new OpenAdtException("Failed to log on through ADT SDK: " + status.getMessage());
        }
        LOGGED_ON_DESTINATIONS.add(destinationId);
        return new AdtLogonStatusReport(true, destinationId, context.fromEclipse(), "logon OK");
    }
}
