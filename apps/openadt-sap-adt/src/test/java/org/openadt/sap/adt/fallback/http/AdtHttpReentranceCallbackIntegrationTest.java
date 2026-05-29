package org.openadt.sap.adt.fallback.http;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdtHttpReentranceCallbackIntegrationTest {
    @Test
    void ticketReceivedPageAttemptsToCloseTab() {
        String page = AdtHttpReentranceTicketFlow.TICKET_RECEIVED_PAGE;
        assertTrue(page.contains("window.close()"));
        assertTrue(page.contains("OpenADT ticket received"));
    }

    @Test
    void callbackServerAcceptsSapMalformedRedirectQuery() throws Exception {
        CompletableFuture<String> ticketFuture = new CompletableFuture<>();
        com.sun.net.httpserver.HttpServer server =
            AdtHttpReentranceTicketFlow.startTestCallbackServer(ticketFuture, "487d1d71-44b3-45e3-9d36-a01b5fcdfb7a");
        try {
            int port = server.getAddress().getPort();
            URI malformed = URI.create(
                "http://localhost:"
                    + port
                    + "/adt/redirect?state=487d1d71-44b3-45e3-9d36-a01b5fcdfb7a?_=20260524191034.9654920"
                    + "&reentrance-ticket=integration-ticket-value"
            );
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(malformed).GET().build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals("integration-ticket-value", ticketFuture.get(5, TimeUnit.SECONDS));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void callbackServerAcceptsMalformedTicketWhenItIsLastParameter() throws Exception {
        CompletableFuture<String> ticketFuture = new CompletableFuture<>();
        com.sun.net.httpserver.HttpServer server =
            AdtHttpReentranceTicketFlow.startTestCallbackServer(ticketFuture, "integration-state");
        try {
            int port = server.getAddress().getPort();
            URI malformed = URI.create(
                "http://localhost:"
                    + port
                    + "/adt/redirect?state=integration-state&reentrance-ticket=integration-ticket-value?_=20260524191034.9654920"
            );
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(malformed).GET().build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertEquals("integration-ticket-value", ticketFuture.get(5, TimeUnit.SECONDS));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void callbackServerAcceptsReentranceTicketQueryParam() throws Exception {
        CompletableFuture<String> ticketFuture = new CompletableFuture<>();
        com.sun.net.httpserver.HttpServer server =
            AdtHttpReentranceTicketFlow.startTestCallbackServer(ticketFuture, "integration-state");
        try {
            int port = server.getAddress().getPort();
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
        } finally {
            server.stop(0);
        }
    }
}
