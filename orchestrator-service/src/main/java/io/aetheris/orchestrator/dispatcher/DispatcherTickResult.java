package io.aetheris.orchestrator.dispatcher;
import java.util.List;
public record DispatcherTickResult(int workersSeen,int dispatched,List<String> details){}
