package io.aetheris.orchestrator.reach;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.aetheris.orchestrator.reach.ReachTypes.*;

@Service
public class ReachCatalogService {

    private final List<BackendDescriptor> backends;
    private final Map<String, BackendDescriptor> byId;

    public ReachCatalogService() {
        this.backends = List.of(
                backend("webpage.http-reader", "Web page HTTP reader", Channel.WEB_PAGE, Transport.HTTP_READER,
                        Set.of(Capability.READ), 10, false, true, false, ImplementationStatus.ADAPTER_REQUIRED,
                        InstallScope.NONE, "Add an allowlisted HTTP reader adapter before enabling arbitrary URL reads."),
                backend("webpage.browser", "Governed browser fallback", Channel.WEB_PAGE, Transport.BROWSER,
                        Set.of(Capability.READ), 20, false, true, false, ImplementationStatus.ADAPTER_REQUIRED,
                        InstallScope.USER_LOCAL, "Requires the future governed browser bridge and physical-machine validation."),

                backend("websearch.mcp", "Web search MCP", Channel.WEB_SEARCH, Transport.MCP,
                        Set.of(Capability.SEARCH, Capability.READ), 10, false, true, false, ImplementationStatus.ADAPTER_REQUIRED,
                        InstallScope.USER_LOCAL, "Register an owner-approved search MCP server and implement the Reach MCP execution bridge."),
                backend("websearch.browser", "Governed browser search fallback", Channel.WEB_SEARCH, Transport.BROWSER,
                        Set.of(Capability.SEARCH, Capability.READ), 20, false, true, false, ImplementationStatus.ADAPTER_REQUIRED,
                        InstallScope.USER_LOCAL, "Requires the future governed browser bridge."),

                backend("rss.http", "RSS/Atom reader", Channel.RSS, Transport.RSS,
                        Set.of(Capability.READ, Capability.FEED), 10, false, true, false, ImplementationStatus.ADAPTER_REQUIRED,
                        InstallScope.NONE, "Add a bounded RSS/Atom fetcher with URL and response-size controls."),
                backend("rss.mcp", "RSS MCP fallback", Channel.RSS, Transport.MCP,
                        Set.of(Capability.READ, Capability.FEED), 20, false, true, false, ImplementationStatus.ADAPTER_REQUIRED,
                        InstallScope.USER_LOCAL, "Register an approved RSS MCP server."),

                backend("github.official-api", "GitHub official API", Channel.GITHUB, Transport.OFFICIAL_API,
                        Set.of(Capability.READ, Capability.SEARCH, Capability.REPOSITORY), 10, false, true, true,
                        ImplementationStatus.IMPLEMENTED, InstallScope.NONE,
                        "Uses the existing governed GitHub adapter; runtime credentials still remain external to the repository."),
                backend("github.gh-cli", "GitHub CLI fallback", Channel.GITHUB, Transport.CLI,
                        Set.of(Capability.READ, Capability.SEARCH, Capability.REPOSITORY), 20, false, true, true,
                        ImplementationStatus.ADAPTER_REQUIRED, InstallScope.USER_LOCAL,
                        "Use a user-local gh CLI only after a dedicated scoped adapter is implemented."),

                backend("youtube.yt-dlp", "YouTube yt-dlp", Channel.YOUTUBE, Transport.CLI,
                        Set.of(Capability.READ, Capability.SEARCH, Capability.TRANSCRIPT), 10, false, true, false,
                        ImplementationStatus.ADAPTER_REQUIRED, InstallScope.USER_LOCAL,
                        "Install user-local yt-dlp only after the Reach CLI adapter is available."),
                backend("youtube.mcp", "YouTube MCP fallback", Channel.YOUTUBE, Transport.MCP,
                        Set.of(Capability.READ, Capability.SEARCH, Capability.TRANSCRIPT), 20, false, true, false,
                        ImplementationStatus.ADAPTER_REQUIRED, InstallScope.USER_LOCAL,
                        "Register an owner-approved YouTube MCP server."),

                backend("reddit.cli", "Reddit CLI", Channel.REDDIT, Transport.CLI,
                        Set.of(Capability.READ, Capability.SEARCH, Capability.FEED), 10, false, true, false,
                        ImplementationStatus.ADAPTER_REQUIRED, InstallScope.USER_LOCAL,
                        "Use a user-local Reddit reader only through a dedicated Reach adapter."),
                backend("reddit.browser", "Reddit browser fallback", Channel.REDDIT, Transport.BROWSER,
                        Set.of(Capability.READ, Capability.SEARCH, Capability.FEED), 20, false, true, false,
                        ImplementationStatus.ADAPTER_REQUIRED, InstallScope.USER_LOCAL,
                        "Requires the governed browser bridge."),

                backend("x.cli", "X/Twitter reader CLI", Channel.X, Transport.CLI,
                        Set.of(Capability.READ, Capability.SEARCH, Capability.PROFILE, Capability.FEED), 10, false, true, true,
                        ImplementationStatus.ADAPTER_REQUIRED, InstallScope.USER_LOCAL,
                        "Authenticated sessions must stay local and must never be committed to the repository."),
                backend("x.browser", "X/Twitter browser fallback", Channel.X, Transport.BROWSER,
                        Set.of(Capability.READ, Capability.SEARCH, Capability.PROFILE, Capability.FEED), 20, false, true, true,
                        ImplementationStatus.ADAPTER_REQUIRED, InstallScope.USER_LOCAL,
                        "Requires owner-approved local browser session access."),

                backend("facebook.browser", "Facebook governed browser", Channel.FACEBOOK, Transport.BROWSER,
                        Set.of(Capability.READ, Capability.PROFILE, Capability.FEED), 10, false, true, true,
                        ImplementationStatus.ADAPTER_REQUIRED, InstallScope.USER_LOCAL,
                        "Requires owner-approved local browser session access."),
                backend("facebook.mcp", "Facebook MCP fallback", Channel.FACEBOOK, Transport.MCP,
                        Set.of(Capability.READ, Capability.PROFILE, Capability.FEED), 20, false, true, true,
                        ImplementationStatus.ADAPTER_REQUIRED, InstallScope.USER_LOCAL,
                        "Register only an owner-approved connector with explicit data-class limits."),

                backend("instagram.browser", "Instagram governed browser", Channel.INSTAGRAM, Transport.BROWSER,
                        Set.of(Capability.READ, Capability.PROFILE, Capability.FEED), 10, false, true, true,
                        ImplementationStatus.ADAPTER_REQUIRED, InstallScope.USER_LOCAL,
                        "Requires owner-approved local browser session access."),
                backend("instagram.mcp", "Instagram MCP fallback", Channel.INSTAGRAM, Transport.MCP,
                        Set.of(Capability.READ, Capability.PROFILE, Capability.FEED), 20, false, true, true,
                        ImplementationStatus.ADAPTER_REQUIRED, InstallScope.USER_LOCAL,
                        "Register only an owner-approved connector with explicit data-class limits."),

                backend("linkedin.browser", "LinkedIn governed browser", Channel.LINKEDIN, Transport.BROWSER,
                        Set.of(Capability.READ, Capability.SEARCH, Capability.PROFILE, Capability.FEED), 10, false, true, true,
                        ImplementationStatus.ADAPTER_REQUIRED, InstallScope.USER_LOCAL,
                        "Public profile reads may be automated later; mutations remain approval-gated."),
                backend("linkedin.mcp", "LinkedIn MCP fallback", Channel.LINKEDIN, Transport.MCP,
                        Set.of(Capability.READ, Capability.SEARCH, Capability.PROFILE), 20, false, true, true,
                        ImplementationStatus.ADAPTER_REQUIRED, InstallScope.USER_LOCAL,
                        "Register only an owner-approved connector."),

                backend("bilibili.cli", "Bilibili reader CLI", Channel.BILIBILI, Transport.CLI,
                        Set.of(Capability.READ, Capability.SEARCH, Capability.TRANSCRIPT), 10, false, true, false,
                        ImplementationStatus.ADAPTER_REQUIRED, InstallScope.USER_LOCAL,
                        "Use a user-local reader through a dedicated Reach adapter."),
                backend("bilibili.browser", "Bilibili browser fallback", Channel.BILIBILI, Transport.BROWSER,
                        Set.of(Capability.READ, Capability.SEARCH), 20, false, true, false,
                        ImplementationStatus.ADAPTER_REQUIRED, InstallScope.USER_LOCAL,
                        "Requires the governed browser bridge."),

                backend("xiaohongshu.mcp", "XiaoHongShu MCP", Channel.XIAOHONGSHU, Transport.MCP,
                        Set.of(Capability.READ, Capability.SEARCH, Capability.PROFILE, Capability.FEED), 10, false, true, true,
                        ImplementationStatus.ADAPTER_REQUIRED, InstallScope.USER_LOCAL,
                        "Register an owner-approved local connector; keep session material local."),
                backend("xiaohongshu.browser", "XiaoHongShu browser fallback", Channel.XIAOHONGSHU, Transport.BROWSER,
                        Set.of(Capability.READ, Capability.SEARCH, Capability.PROFILE, Capability.FEED), 20, false, true, true,
                        ImplementationStatus.ADAPTER_REQUIRED, InstallScope.USER_LOCAL,
                        "Requires the governed browser bridge."),

                backend("v2ex.http-reader", "V2EX HTTP reader", Channel.V2EX, Transport.HTTP_READER,
                        Set.of(Capability.READ, Capability.SEARCH, Capability.FEED), 10, false, true, false,
                        ImplementationStatus.ADAPTER_REQUIRED, InstallScope.NONE,
                        "Add an allowlisted V2EX reader adapter."),
                backend("v2ex.browser", "V2EX browser fallback", Channel.V2EX, Transport.BROWSER,
                        Set.of(Capability.READ, Capability.SEARCH, Capability.FEED), 20, false, true, false,
                        ImplementationStatus.ADAPTER_REQUIRED, InstallScope.USER_LOCAL,
                        "Requires the governed browser bridge."),

                backend("xueqiu.mcp", "Xueqiu MCP", Channel.XUEQIU, Transport.MCP,
                        Set.of(Capability.READ, Capability.SEARCH, Capability.FEED), 10, false, true, true,
                        ImplementationStatus.ADAPTER_REQUIRED, InstallScope.USER_LOCAL,
                        "Financial information access remains research-only unless separate trading policy permits more."),
                backend("xueqiu.browser", "Xueqiu browser fallback", Channel.XUEQIU, Transport.BROWSER,
                        Set.of(Capability.READ, Capability.SEARCH, Capability.FEED), 20, false, true, true,
                        ImplementationStatus.ADAPTER_REQUIRED, InstallScope.USER_LOCAL,
                        "Requires the governed browser bridge."),

                backend("xiaoyuzhou.mcp", "Xiaoyuzhou podcast MCP", Channel.XIAOYUZHOU, Transport.MCP,
                        Set.of(Capability.READ, Capability.SEARCH, Capability.TRANSCRIPT), 10, false, true, false,
                        ImplementationStatus.ADAPTER_REQUIRED, InstallScope.USER_LOCAL,
                        "Register an owner-approved local connector."),
                backend("xiaoyuzhou.browser", "Xiaoyuzhou browser fallback", Channel.XIAOYUZHOU, Transport.BROWSER,
                        Set.of(Capability.READ, Capability.SEARCH), 20, false, true, false,
                        ImplementationStatus.ADAPTER_REQUIRED, InstallScope.USER_LOCAL,
                        "Requires the governed browser bridge.")
        );
        this.byId = backends.stream().collect(Collectors.toUnmodifiableMap(BackendDescriptor::id, Function.identity()));
    }

    public List<ChannelDescriptor> channels() {
        List<ChannelDescriptor> result = new ArrayList<>();
        for (Channel channel : Channel.values()) {
            List<BackendDescriptor> channelBackends = backendsFor(channel);
            EnumSet<Capability> capabilities = EnumSet.noneOf(Capability.class);
            channelBackends.forEach(backend -> capabilities.addAll(backend.capabilities()));
            result.add(new ChannelDescriptor(channel, displayName(channel), capabilities, channelBackends));
        }
        return List.copyOf(result);
    }

    public List<BackendDescriptor> backends() {
        return backends;
    }

    public List<BackendDescriptor> backendsFor(Channel channel) {
        if (channel == null) return List.of();
        return backends.stream()
                .filter(backend -> backend.channel() == channel)
                .sorted(Comparator.comparingInt(BackendDescriptor::priority))
                .toList();
    }

    public BackendDescriptor getRequired(String id) {
        BackendDescriptor descriptor = byId.get(id);
        if (descriptor == null) throw new NoSuchElementException("Unknown Reach backend: " + id);
        return descriptor;
    }

    private BackendDescriptor backend(
            String id,
            String displayName,
            Channel channel,
            Transport transport,
            Set<Capability> capabilities,
            int priority,
            boolean billable,
            boolean sendsDataOffDevice,
            boolean credentialRequired,
            ImplementationStatus implementationStatus,
            InstallScope installScope,
            String installHint) {
        return new BackendDescriptor(id, displayName, channel, transport, capabilities, priority, billable,
                sendsDataOffDevice, credentialRequired, implementationStatus, installScope, installHint);
    }

    private String displayName(Channel channel) {
        return switch (channel) {
            case WEB_PAGE -> "Web pages";
            case WEB_SEARCH -> "Web search";
            case RSS -> "RSS / Atom";
            case GITHUB -> "GitHub";
            case YOUTUBE -> "YouTube";
            case REDDIT -> "Reddit";
            case X -> "X / Twitter";
            case FACEBOOK -> "Facebook";
            case INSTAGRAM -> "Instagram";
            case LINKEDIN -> "LinkedIn";
            case BILIBILI -> "Bilibili";
            case XIAOHONGSHU -> "XiaoHongShu";
            case V2EX -> "V2EX";
            case XUEQIU -> "Xueqiu";
            case XIAOYUZHOU -> "Xiaoyuzhou Podcast";
        };
    }
}
