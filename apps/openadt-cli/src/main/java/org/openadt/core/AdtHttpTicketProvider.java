package org.openadt.core;

interface AdtHttpTicketProvider {
    String acquireTicket(OpenAdtConfig config, SystemProfile system);
}
