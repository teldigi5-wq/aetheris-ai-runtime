// Stage 46.18: tiny fixed native executable for reproducibility proof only.
// No filesystem reads/writes, network, microphone, model, desktop actions,
// process spawning, shell commands, credentials or data from the actual user.
// The runner builds the same source twice and does NOT ship this artifact.
#define WIN32_LEAN_AND_MEAN
#include <cstdio>
#include <cwchar>

int wmain(int argc, wchar_t** argv) {
    if (argc != 2 || std::wcscmp(argv[1], L"--self-test") != 0) return 4;
    std::puts("STAGE46_18_FIXED_SYNTHETIC_ARTIFACT_SELFTEST_ONLY");
    return 0;
}
