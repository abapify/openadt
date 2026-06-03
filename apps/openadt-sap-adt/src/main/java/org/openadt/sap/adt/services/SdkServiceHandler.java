package org.openadt.sap.adt.services;

import org.openadt.sap.adt.sdk.SdkServiceArgs;
import org.openadt.sap.adt.sdk.SdkServiceResult;

/**
 * One registered SDK operation (see {@link SdkServiceRegistry}).
 */
public interface SdkServiceHandler {
    SdkServiceResult execute(SapAdtSessionContext context, SdkServiceArgs args);
}
