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
    private volatile long artificialChatDelayMillis;

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
        artificialChatDelayMillis=0;
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
                if(artificialChatDelayMillis>0)
                    Thread.sleep(artificialChatDelayMillis);
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

    @Test void fixedContextAndUnloadHintDoNotEnableAnyToolsOrModels() throws Exception {
        try(var local=adapter()){
            local.reply("Explain Java methods");
            JsonNode posted=request.get();
            assertThat(posted.path("options").path("num_ctx").asInt()).isEqualTo(2048);
            assertThat(posted.path("options").path("num_predict").asInt()).isEqualTo(256);
            assertThat(posted.path("keep_alive").asText()).isEqualTo("0");
            assertThat(posted.path("stream").asBoolean()).isFalse();
            assertThat(posted.has("tools")).isFalse();
            var health=local.health();
            assertThat(health.completedReplies()).isEqualTo(1);
            assertThat(health.lastAttemptMillis()).isGreaterThanOrEqualTo(0L);
            assertThat(health.lastAttemptSucceeded()).isTrue();
            assertThat(health.loadingOrGpuCertified()).isFalse();
        }
    }

    @Test void repeatedModelFailuresOpenCircuitUntilExplicitLocalReset() throws Exception {
        tagsStatus=503;
        try(var local=adapter()){
            for(int i=0;i<3;i++) {
                assertThatThrownBy(()->local.reply("private question"))
                    .isInstanceOf(SyntraLoopbackModelClient.ModelUnavailableException.class)
                    .hasMessage("MODEL_HTTP_STATUS_DENIED");
            }
            assertThat(local.health().consecutiveFailures()).isEqualTo(3);
            assertThat(local.health().circuitOpen()).isTrue();
            assertThat(local.health().completedReplies()).isZero();
            assertThat(tags).hasValue(3);
            assertThat(chats).hasValue(0);
            tagsStatus=200;
            assertThatThrownBy(()->local.reply("private question"))
                .isInstanceOf(SyntraLoopbackModelClient.ModelUnavailableException.class)
                .hasMessage("LOCAL_MODEL_CIRCUIT_OPEN");
            assertThat(tags).hasValue(3);
            local.resetFailedSession();
            assertThat(local.health().circuitOpen()).isFalse();
            assertThat(local.reply("new consent-free text question")).contains("fake local model");
            assertThat(local.health().completedReplies()).isEqualTo(1);
            assertThat(local.health().consecutiveFailures()).isZero();
            assertThat(chats).hasValue(1);
        }
    }

    @Test void digestSubstitutionCannotBeBypassedByRetryResetOrReversion() throws Exception {
        try(var local=adapter()){
            local.reply("private one");
            tagsBody="{\"models\":[{\"name\":\"qwen3:4b\",\"size\":2300000000,\"digest\":\"" + "b".repeat(64) + "\"}]}";
            assertThatThrownBy(()->local.reply("private two"))
                .isInstanceOf(SyntraLoopbackModelClient.ModelUnavailableException.class)
                .hasMessage("LOCAL_MODEL_DIGEST_CHANGED_SESSION_DENIED");
            assertThat(local.health().modelIdentityBlocked()).isTrue();
            tagsBody="{\"models\":[{\"name\":\"qwen3:4b\",\"size\":2300000000,\"digest\":\"" + "a".repeat(64) + "\"}]}";
            local.resetFailedSession();
            assertThat(local.health().retainedTurns()).isZero();
            assertThatThrownBy(()->local.reply("private three"))
                .isInstanceOf(SyntraLoopbackModelClient.ModelUnavailableException.class)
                .hasMessage("LOCAL_MODEL_IDENTITY_BLOCKED");
            assertThat(chats).hasValue(1);
        }
    }

    @Test void clearingContextDeletesOnlyVolatilePromptsAndNeverInvokesHttp() throws Exception {
        try(var local=adapter()){
            local.reply("an example question");
            assertThat(local.health().retainedTurns()).isEqualTo(2);
            int before=chats.get();
            local.clearContext();
            assertThat(local.health().retainedTurns()).isZero();
            assertThat(chats).hasValue(before);
            local.reply("another question");
            assertThat(request.get().path("messages").size()).isEqualTo(2);
            assertThat(local.health().completedReplies()).isEqualTo(2);
        }
    }

    @Test void consoleCanInspectFailureHealthAndManuallyClearHistoryWithoutToolCalls()
            throws Exception {
        try(var local=adapter()){
            var out=new StringWriter();
            SyntraLocalTextCompanion.run(new StringReader(
                "model health\nhi syntra\nask explain java\nmodel health\nclear context\nmodel health\nmodel reset\nquit\n"),
                out,()->false,local);
            assertThat(out.toString()).contains("DENIED: typed wake and explicit local-model opt-in required");
            assertThat(out.toString()).contains("MODEL HEALTH: completed=1");
            assertThat(out.toString()).contains("LOCAL CONTEXT CLEARED");
            assertThat(out.toString()).contains("context turns=0");
            assertThat(out.toString()).contains("MODEL RETRY RESET");
            assertThat(out.toString()).doesNotContain("Launched", "Executed desktop");
            assertThat(chats).hasValue(1);
        }
    }

    @Test void slowLoopbackInferenceTimesOutAndNeverAddsChatHistory() throws Exception {
        artificialChatDelayMillis=550;
        try(var local=new SyntraLoopbackModelClient(
                "qwen3:4b",server.getAddress().getPort(),
                java.time.Duration.ofMillis(150))){
            assertThatThrownBy(()->local.reply("Do not run apps"))
                .isInstanceOf(SyntraLoopbackModelClient.ModelUnavailableException.class)
                .hasMessage("LOCAL_MODEL_UNAVAILABLE");
            var health=local.health();
            assertThat(health.completedReplies()).isZero();
            assertThat(health.consecutiveFailures()).isEqualTo(1);
            assertThat(health.retainedTurns()).isZero();
            assertThat(health.lastAttemptSucceeded()).isFalse();
            assertThat(health.lastAttemptMillis()).isGreaterThanOrEqualTo(100L);
            assertThat(health.loadingOrGpuCertified()).isFalse();
        }
    }

}
