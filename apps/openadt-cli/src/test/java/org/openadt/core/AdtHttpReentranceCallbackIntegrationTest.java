package org.openadt.core;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdtHttpReentranceCallbackIntegrationTest {
    @Test
    void ticketReceivedPageAttemptsToCloseTab() {
        String page = AdtHttpReentranceTicketFlow.TICKET_RECEIVED_PAGE;
        assertTrue(page.contains("window.close()"));
        assertTrue(page.contains("OpenADT ticket received"));
    }

    @Test
    void callbackServerAcceptsReentranceTicketQueryParam() throws Exception {
        CompletableFuture<String> ticketFuture = new CompletableFuture<>();
        AdtHttpReentranceTicketFlow flow = new AdtHttpReentranceTicketFlow(
            key -> "true",
            uri -> { }
        );

        int port = startCallbackServer(flow, ticketFuture, "integration-state");
        URI callback = AdtHttpReentranceTicketFlow.buildCallbackUrl("localhost", port, "integration-state");
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(
            URI.create(callback + "&reentrance-ticket=integration-ticket-value")
        ).GET().build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("OpenADT ticket received"));
        assertTrue(response.body().contains("window.close()"));
        assertEquals("integration-ticket-value", ticketFuture.get(5, TimeUnit.SECONDS));
    }

  /** Mirrors package-private callback wiring without launching browser SSO. */
    private static int startCallbackServer(AdtHttpReentranceTicketFlow flow, CompletableFuture<String> ticketFuture, String csrfState)
        throws Exception {
        var method = AdtHttpReentranceTicketFlow.class.getDeclaredMethod(
            "createCallbackServer",
            String.class,
            int.class,
            CompletableFuture.class,
            String.class
        );
        method.setAccessible(true);
        com.sun.net.httpserver.HttpServer server =
            (com.sun.net.httpserver.HttpServer) method.invoke(flow, "localhost", 0, ticketFuture, csrfState);
        server.start();
        int port = server.getAddress().getPort();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
        return port;
    }
}
