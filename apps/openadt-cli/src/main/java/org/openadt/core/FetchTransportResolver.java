package org.openadt.core;

import org.openadt.core.LocalProxyRegistry.ProxyEndpoint;

/**
 * Resolves the transport for {@code fetch}: warm local proxy when available, otherwise SAP SDK.
 */
public final class FetchTransportResolver {
    private FetchTransportResolver() {
    }

    public static AdtTransportClient resolve(OpenAdtConfig config, SystemProfile system, boolean direct) throws Exception {
        return resolve(config, system, direct, null);
    }

    public static AdtTransportClient resolve(
        OpenAdtConfig config,
        SystemProfile system,
        boolean direct,
        String profileName
    ) throws Exception {
        if (direct) {
            return AdtTransportFactory.create(config, system);
        }
        String alias = system.getAlias();
        if (alias != null) {
            var active = LocalProxyRegistry.findActive(alias, profileName);
            if (active.isPresent()) {
                return new LocalProxyHttpClient(active.get());
            }
        }
        return AdtTransportFactory.create(config, system);
    }
}
