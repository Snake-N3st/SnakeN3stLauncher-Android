package mc.snakenest.launcher.net;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HttpJsonClientTest {
    @Test void jsonAndStreamBodiesRoundTripAndSignedRedirectIsNotFollowed() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        AtomicBoolean destinationVisited=new AtomicBoolean();
        server.createContext("/json",exchange->{
            assertEquals("application/json",exchange.getRequestHeaders().getFirst("Accept"));
            byte[] body="{\"name\":\"Snake N3st\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });
        server.createContext("/post",exchange->{
            assertEquals("POST",exchange.getRequestMethod());
            assertTrue(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8).contains("client_id"));
            exchange.sendResponseHeaders(204,-1);exchange.close();
        });
        server.createContext("/blob",exchange->{
            byte[] body={0,1,2,3};exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });
        server.createContext("/redirect",exchange->{exchange.getResponseHeaders().add("Location","/destination");exchange.sendResponseHeaders(302,-1);exchange.close();});
        server.createContext("/destination",exchange->{destinationVisited.set(true);exchange.sendResponseHeaders(204,-1);exchange.close();});
        server.start();
        try {
            URI base=URI.create("http://127.0.0.1:"+server.getAddress().getPort());
            HttpJsonClient client=new HttpJsonClient();
            assertTrue(client.get(base.resolve("/json")).rawBody().contains("Snake N3st"));
            assertEquals(204,client.postJson(base.resolve("/post"),Map.of("client_id","test")).statusCode());
            try(RawResponse raw=client.getRaw(base.resolve("/blob"))) { assertArrayEquals(new byte[]{0,1,2,3},raw.body().readAllBytes()); }
            assertEquals(302,client.get(base.resolve("/redirect?signature=private")).statusCode());
            try(RawResponse raw=client.getRaw(base.resolve("/redirect?signature=private"))) { assertEquals(302,raw.statusCode()); }
            assertFalse(destinationVisited.get());
        } finally { server.stop(0); }
    }
    @Test void interruptionPreventsOpeningConnection() {
        Thread.currentThread().interrupt();
        try { assertThrows(InterruptedException.class,()->new HttpJsonClient().get(URI.create("http://127.0.0.1:1"))); }
        finally { Thread.interrupted(); }
    }
}
