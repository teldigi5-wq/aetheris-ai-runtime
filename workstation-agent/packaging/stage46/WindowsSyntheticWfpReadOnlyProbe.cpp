// Stage 46.16 — read-only Windows Filtering Platform diagnostic.
// SYNTHETIC CI LAB ONLY. Opens the Base Filtering Engine, reads the standard
// outbound ALE connection layers (IPv4/IPv6) and immediately closes it.
// There are deliberately NO FwpmFilterAdd/Delete, provider, sublayer, engine
// options, capture, event-subscription, registry or firewall-rule mutations.
// Layer visibility is NOT evidence that an AppContainer connection was denied.
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <rpc.h>
#include <initguid.h>
#include <fwpmu.h>
#include <cstdio>
#pragma comment(lib, "fwpuclnt.lib")
#pragma comment(lib, "rpcrt4.lib")

namespace {
struct ReadOnlyEngine {
    HANDLE handle = nullptr;
    ~ReadOnlyEngine() {
        if (handle != nullptr) FwpmEngineClose0(handle);
    }
};

bool readLayer(HANDLE engine, const GUID& key, const char* label) {
    FWPM_LAYER0* layer = nullptr;
    DWORD status = FwpmLayerGetByKey0(engine, &key, &layer);
    if (status != ERROR_SUCCESS || layer == nullptr) {
        std::fprintf(stderr, "STAGE46_16_WFP_%s_QUERY_ERROR_%lu\n",
                     label, static_cast<unsigned long>(status));
        if (layer != nullptr) FwpmFreeMemory0(reinterpret_cast<void**>(&layer));
        return false;
    }
    // Only a kernel-reported layer metadata lookup; NEVER inspect traffic,
    // grant/deny permissions, or infer a filtering decision for this worker.
    bool correctKey = IsEqualGUID(layer->layerKey, key) != 0;
    UINT32 fieldCount = layer->numFields;
    FwpmFreeMemory0(reinterpret_cast<void**>(&layer));
    if (!correctKey || fieldCount == 0) {
        std::fprintf(stderr, "STAGE46_16_WFP_%s_LAYER_METADATA_INCOMPLETE\n", label);
        return false;
    }
    std::printf("STAGE46_16_WFP_%s_READ_ONLY_LAYER_PRESENT\n", label);
    return true;
}
}
int wmain(int argc, wchar_t** argv) {
    // No user-supplied path, policy, IP address, port or tool parameters.
    (void) argv;
    if (argc != 1) return 4;
    ReadOnlyEngine engine;
    DWORD result = FwpmEngineOpen0(nullptr, RPC_C_AUTHN_WINNT,
                                   nullptr, nullptr, &engine.handle);
    if (result != ERROR_SUCCESS || engine.handle == nullptr) {
        std::fprintf(stderr, "STAGE46_16_WFP_READ_ONLY_ENGINE_UNAVAILABLE_%lu\n",
                     static_cast<unsigned long>(result));
        return 5;
    }
    std::puts("STAGE46_16_WFP_BFE_READ_ONLY_SESSION_OPENED");
    if (!readLayer(engine.handle, FWPM_LAYER_ALE_AUTH_CONNECT_V4, "ALE_AUTH_CONNECT_V4"))
        return 6;
    if (!readLayer(engine.handle, FWPM_LAYER_ALE_AUTH_CONNECT_V6, "ALE_AUTH_CONNECT_V6"))
        return 7;
    std::puts("STAGE46_16_WFP_NO_FILTER_OR_FIREWALL_RULES_MODIFIED");
    std::puts("STAGE46_16_NO_CORRELATED_BLOCK_EVENT_EGRESS_UNVERIFIED");
    return 0;
}
