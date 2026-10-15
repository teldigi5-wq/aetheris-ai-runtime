package io.aetheris.orchestrator.voice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A fake ONLY-LOOPBACK Ollama server. No model downloaded or loaded. */
class SyntraLoopbackModelClientTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private HttpServer server;
    private final AtomicInteger tags = new AtomicInteger();
    private final AtomicInteger chats = new AtomicInteger();
    private final AtomicReference<JsonNode> request = new AtomicReference<>();
    private volatile String tagsBody;
    private volatile String chatBody;
    private volatile int tagsStatus;
    private volatile int chatStatus;
    private volatile String responseType;
    private volatile AtomicBoolean stopDuringChat;

    @BeforeEach void startLoopback() throws Exception {
        tags.set(0);
        chats.set(0);
        request.set(null);
        tagsBody="{\"models\":[{\"name\":\"qwen3:4b\",\"size\":2300000000,\"digest\":\"" + "a".repeat(64) + "\"}]}";
        chatBody="{\"done\":true,\"message\":{\"role\":\"assistant\",\"content\":\"Hello from a fake local model\"}}";
        tagsStatus=200;
        chatStatus=200;
        responseType="application/json";
        stopDuringChat=null;
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/api/tags",e->{
            tags.incrementAndGet();
            respond(e,tagsStatus,tagsBody);
        });
        server.createContext("/api/chat",e->{
            chats.incrementAndGet();
            try {
                request.set(JSON.readTree(e.getRequestBody()));
                if(stopDuringChat!=null) stopDuringChat.set(true);
            } catch(Exception parseFailure){
                respond(e,400,"{}");
                return;
            }
            respond(e,chatStatus,chatBody);
        });
        server.start();
    }
    private void respond(HttpExchange e,int status,String text) throws java.io.IOException {
        byte[] data=text.getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().add("Content-Type",responseType);
        e.sendResponseHeaders(status,data.length);
        try(var body=e.getResponseBody()) {body.write(data);}
    }
    @AfterEach void stopLoopback(){
        if(server!=null)server.stop(0);
    }
    private SyntraLoopbackModelClient adapter(){return new SyntraLoopbackModelClient("qwen3:4b",server.getAddress().getPort());}

    @Test void callsOnlySelectedInstalledLoopbackModelWithBoundedPromptAndNoTools() throws Exception {
        try(var client=adapter()) {
            String result=client.reply("Explain a Java array");
            assertThat(result).isEqualTo("Hello from a fake local model");
            assertThat(tags).hasValue(1);
            assertThat(chats).hasValue(1);
            JsonNode payload=request.get();
            assertThat(payload.path("model").asText()).isEqualTo("qwen3:4b");
            assertThat(payload.path("stream").asBoolean()).isFalse();
            assertThat(payload.has("tools")).isFalse();
            assertThat(payload.path("options").path("num_predict").asInt()).isEqualTo(256);
            assertThat(payload.path("messages").get(0).path("role").asText()).isEqualTo("system");
            assertThat(payload.path("messages").get(1).path("content").asText()).isEqualTo("Explain a Java array");
            client.reply("And give one example");
            assertThat(tags).hasValue(2);
            assertThat(chats).hasValue(2);
            assertThat(request.get().path("messages").size()).isEqualTo(4);
        }
    }

    @Test void refusesNonInstalledModelBeforeAnyChatOrImplicitPull() throws Exception {
        tagsBody="{\"models\":[{\"name\":\"llama3.2:3b\",\"size\":2100000000,\"digest\":\"" + "b".repeat(64) + "\"}]}";
        try(var client=adapter()){
            assertThatThrownBy(() -> client.reply("hello"))
                    .isInstanceOf(SyntraLoopbackModelClient.ModelUnavailableException.class)
                    .hasMessage("MODEL_NOT_INSTALLED_LOCALLY");
        }
        assertThat(tags).hasValue(1);
        assertThat(chats).hasValue(0);
    }

    @Test void refusesMalformedModelIdsAndEndpointInjection() {
        for(String model:new String[]{"http://evil", "../escape", "model/name", "",
                "a".repeat(85), "host?x=1", "model name", "x\\y"}) {
            assertThatThrownBy(()->new SyntraLoopbackModelClient(model))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(()->new SyntraLoopbackModelClient("valid",0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void refusesHtmlAndNon200WithoutEchoingRawModelResponse() throws Exception {
        try(var client=adapter()) {
            responseType="text/html";
            assertThatThrownBy(()->client.reply("private prompt"))
                    .isInstanceOf(SyntraLoopbackModelClient.ModelUnavailableException.class)
                    .hasMessage("MODEL_CONTENT_TYPE_DENIED");
        }
        responseType="application/json";
        tagsStatus=302;
        tagsBody="<html>redirect</html>";
        try(var client=adapter()) {
            assertThatThrownBy(()->client.reply("private prompt"))
                    .isInstanceOf(SyntraLoopbackModelClient.ModelUnavailableException.class)
                    .hasMessage("MODEL_HTTP_STATUS_DENIED");
        }
        assertThat(chats).hasValue(0);
    }

    @Test void refusesOversizedMalformedPartialOrNonAssistantResponses() throws Exception {
        for(String invalid:new String[]{
                "not json",
                "{\"done\":false,\"message\":{\"role\":\"assistant\",\"content\":\"partial\"}}",
                "{\"done\":true,\"message\":{\"role\":\"tool\",\"content\":\"run shell\"}}",
                "{\"done\":true,\"message\":{\"role\":\"assistant\",\"content\":123}}",
                "{\"done\":true,\"message\":{\"role\":\"assistant\",\"content\":\"" + "x".repeat(2500) + "\"}}",
                "{\"done\":true,\"message\":{\"role\":\"assistant\",\"content\":\"" + "x".repeat(20000) + "\"}}"
        }) {
            chatBody=invalid;
            try(var client=adapter()){
                assertThatThrownBy(()->client.reply("can you talk"))
                        .isInstanceOf(SyntraLoopbackModelClient.ModelUnavailableException.class);
            }
        }
    }

    @Test void stripsAnsiEscapesAndControlCharactersFromDisplayedModelOutput() throws Exception {
        chatBody="{\"done\":true,\"message\":{\"role\":\"assistant\",\"content\":\"Hello\\u001b[31m\\r\\u0007! \\nworld\"}}";
        try(var client=adapter()) {
            String answer=client.reply("Hi");
            assertThat(answer).doesNotContain("\u001b","\r","\u0007");
            assertThat(answer).contains("Hello","world");
        }
    }

    @Test void localConsoleNeedsWakeAndExplicitModelAdapterAndCannotExecuteDesktop() throws Exception {
        try(var client=adapter()){
            var out=new StringWriter();
            SyntraLocalTextCompanion.run(new StringReader(
                "ask say hi\nhi sintra\nask say hi\nopen editor\nquit\n"),
                out,()->false,client);
            assertThat(out.toString()).contains("DENIED: typed wake and explicit local-model opt-in required");
            assertThat(out.toString()).contains("LOCAL MODEL (untrusted text, never an action): Hello");
            assertThat(out.toString()).contains("PREVIEW ONLY: proposal recorded");
            assertThat(out.toString()).doesNotContain("SIMULATED ONLY");
            assertThat(chats).hasValue(1);
        }
    }

    @Test void stopWhileAwaitingLocalInferenceDiscardsEvenSuccessfulReply() throws Exception {
        var stop=new AtomicBoolean(false);
        stopDuringChat=stop;
        try(var client=adapter()) {
            var out=new StringWriter();
            SyntraLocalTextCompanion.run(new StringReader(
                    "hi syntra\nask what is Java\nquit\n"),out,stop::get,client);
            assertThat(chats).hasValue(1);
            assertThat(out.toString()).contains("STOP: local model answer discarded");
            assertThat(out.toString()).doesNotContain("LOCAL MODEL (untrusted text, never an action):");
        }
    }

    @Test void defaultCompanionDoesNotMakeAnyModelHttpRequest() throws Exception {
        var out=new StringWriter();
        SyntraLocalTextCompanion.run(new StringReader("hi syntra\nask what is Java\nquit\n"),
                out,()->false);
        assertThat(out.toString()).contains("DENIED: typed wake and explicit local-model opt-in required");
        assertThat(tags).hasValue(0);
        assertThat(chats).hasValue(0);
    }
    @Test void inventoryInspectionIsReadOnlyAndNeverStartsInference() throws Exception {
        try(var client=adapter()){
            var models=client.listInstalledModels();
            assertThat(models).hasSize(1);
            assertThat(models.get(0).name()).isEqualTo("qwen3:4b");
            assertThat(models.get(0).diskBytes()).isEqualTo(2_300_000_000L);
            assertThat(models.get(0).sha256Digest()).isEqualTo("a".repeat(64));
            var review=client.reviewSelectedModel();
            assertThat(review.model()).isEqualTo("qwen3:4b");
            assertThat(review.gpuCertified()).isFalse();
            assertThat(review.loadCertified()).isFalse();
            assertThat(chats).hasValue(0);
        }
    }

    @Test void midSessionModelDigestReplacementStopsFurtherInference() throws Exception {
        try(var client=adapter()){
            client.reply("Explain arrays");
            assertThat(chats).hasValue(1);
            tagsBody="{\"models\":[{\"name\":\"qwen3:4b\",\"size\":2300000000,\"digest\":\"" + "b".repeat(64) + "\"}]}";
            assertThatThrownBy(()->client.reply("Continue"))
                .isInstanceOf(SyntraLoopbackModelClient.ModelUnavailableException.class)
                .hasMessage("LOCAL_MODEL_DIGEST_CHANGED_SESSION_DENIED");
            assertThat(chats).hasValue(1);
        }
    }

    @Test void malformedMissingMetadataAndDuplicateEntriesNeverStartChat() throws Exception {
        for(String invalid:new String[]{
            "{\"models\":[{\"name\":\"qwen3:4b\"}]}",
            "{\"models\":[{\"name\":\"qwen3:4b\",\"size\":-1,\"digest\":\"" + "a".repeat(64) + "\"}]}",
            "{\"models\":[{\"name\":\"../../escape\",\"size\":2,\"digest\":\"" + "a".repeat(64) + "\"}]}",
            "{\"models\":[{\"name\":\"qwen3:4b\",\"size\":2,\"digest\":\"invalid\"}]}",
            "{\"models\":[{\"name\":\"qwen3:4b\",\"size\":2,\"digest\":\"" + "a".repeat(64) + "\"},{\"name\":\"qwen3:4b\",\"size\":2,\"digest\":\"" + "a".repeat(64) + "\"}]}"
        }) {
            tagsBody=invalid;
            try(var client=adapter()){
                assertThatThrownBy(()->client.reply("hello"))
                    .isInstanceOf(SyntraLoopbackModelClient.ModelUnavailableException.class);
            }
        }
        assertThat(chats).hasValue(0);
    }

    @Test void consoleListsModelsAndRamAdvisoryWithoutLoadingWeights() throws Exception {
        var out=new StringWriter();
        try(var client=adapter()) {
            SyntraLocalTextCompanion.run(new StringReader(
                "models\nmodel info\nhi syntra\nmodels\nmodel info\nquit\n"),
                out,()->false,client);
        }
        assertThat(out.toString()).contains("DENIED: typed wake and explicit local-model opt-in required");
        assertThat(out.toString()).contains("INSTALLED LOCAL MODELS: 1");
        assertThat(out.toString()).contains("MODEL REVIEW: qwen3:4b");
        assertThat(out.toString()).contains("GPU/VRAM: UNVERIFIED; loading/performance: NOT_CERTIFIED");
        assertThat(chats).hasValue(0);
    }

}
