package org.openadt.sap.adt.fallback.http;


import org.openadt.config.OpenAdtConfig;
import org.openadt.config.SystemProfile;
interface AdtHttpTicketProvider {
    String acquireTicket(OpenAdtConfig config, SystemProfile system);
}
