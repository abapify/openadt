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

        int port = startCallbackServer(ticketFuture, "integration-state");
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

  /** Returns the bound port for the started test callback server. */
    private static int startCallbackServer(CompletableFuture<String> ticketFuture, String csrfState) {
        com.sun.net.httpserver.HttpServer server =
            AdtHttpReentranceTicketFlow.startTestCallbackServer(ticketFuture, csrfState);
        int port = server.getAddress().getPort();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
        return port;
    }
}
