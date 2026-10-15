// Stage 46.17 — bounded read-only WFP CLASSIFY_DROP snapshot lab.
// Does not install/modify WFP policy, enable diagnostic logging, or capture
// user traffic. No event data or personal network identifiers are printed.
// A snapshot without a verified app + local ephemeral test destination +
// timestamp + filter/layer correlation MUST NOT certify network denial.
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <fwpmu.h>
#include <fwperr.h>
#include <cstdio>
#pragma comment(lib, "fwpuclnt.lib")
#pragma comment(lib, "rpcrt4.lib")

namespace {
struct WfpEngine {
    HANDLE value = nullptr;
    ~WfpEngine() { if (value) FwpmEngineClose0(value); }
};
struct EventEnum {
    HANDLE engine;
    HANDLE value = nullptr;
    explicit EventEnum(HANDLE engineHandle) : engine(engineHandle) {}
    ~EventEnum() { if (value) FwpmNetEventDestroyEnumHandle0(engine, value); }
};
}

int wmain(int argc, wchar_t** argv) {
    (void) argv;
    if (argc != 1) return 4; // No untrusted executable/path/IP/port input.
    WfpEngine engine;
    DWORD openStatus = FwpmEngineOpen0(
        nullptr, RPC_C_AUTHN_WINNT, nullptr, nullptr, &engine.value);
    if (openStatus != ERROR_SUCCESS || !engine.value) {
        std::puts("STAGE46_17_WFP_SNAPSHOT_UNAVAILABLE");
        std::puts("STAGE46_17_WFP_PROCESS_CORRELATION_NOT_VERIFIED");
        return 0; // Read-only diagnostic unavailable, not a release gate.
    }
    // Restrict the fixed snapshot to CLASSIFY_DROP, not arbitrary traffic.
    FWPM_FILTER_CONDITION0 condition = {};
    condition.fieldKey = FWPM_CONDITION_NET_EVENT_TYPE;
    condition.matchType = FWP_MATCH_EQUAL;
    condition.conditionValue.type = FWP_UINT32;
    condition.conditionValue.uint32 = FWPM_NET_EVENT_TYPE_CLASSIFY_DROP;

    FWPM_NET_EVENT_ENUM_TEMPLATE0 filter = {};
    filter.numFilterConditions = 1;
    filter.filterCondition = &condition;
    EventEnum enumerator(engine.value);
    DWORD status = FwpmNetEventCreateEnumHandle0(engine.value, &filter,
                                                  &enumerator.value);
    if (status != ERROR_SUCCESS || !enumerator.value) {
        std::puts("STAGE46_17_WFP_SNAPSHOT_UNAVAILABLE");
        std::puts("STAGE46_17_WFP_PROCESS_CORRELATION_NOT_VERIFIED");
        return 0;
    }
    constexpr UINT32 kMaxSnapshotEvents = 16;
    FWPM_NET_EVENT0** events = nullptr;
    UINT32 returned = 0;
    status = FwpmNetEventEnum0(engine.value, enumerator.value, kMaxSnapshotEvents,
                                &events, &returned);
    if (status == FWP_E_NET_EVENTS_DISABLED) {
        std::puts("STAGE46_17_WFP_EVENT_LOGGING_DISABLED");
    } else if (status != ERROR_SUCCESS || returned > kMaxSnapshotEvents) {
        std::puts("STAGE46_17_WFP_SNAPSHOT_UNAVAILABLE");
    } else if (returned == 0) {
        std::puts("STAGE46_17_WFP_SNAPSHOT_EMPTY");
    } else {
        // Never access or print the app IDs, usernames, IP/port tuples or
        // unrelated filter IDs returned by the kernel.
        std::puts("STAGE46_17_WFP_UNCORRELATED_DROP_SNAPSHOT_PRESENT");
    }
    if (events) FwpmFreeMemory0(reinterpret_cast<void**>(&events));
    std::puts("STAGE46_17_WFP_PROCESS_CORRELATION_NOT_VERIFIED");
    std::puts("STAGE46_17_NO_WFP_FILTER_OR_AUDIT_CONFIGURATION_CHANGED");
    return 0;
}
