// Stage 46.14 — Windows AppContainer isolation TEST LAB.
// Fixed native synthetic fixture only. Never installed or enabled in the
// speech runtime, never receives transcripts/PCM, user files or real secrets.
// Build ONLY in the GitHub-hosted Windows 2022 CI synthetic test job.
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <userenv.h>
#include <aclapi.h>
#include <sddl.h>
#include <winsock2.h>
#include <ws2tcpip.h>
#include <stdio.h>
#include <string>
#include <vector>
#pragma comment(lib, "userenv.lib")
#pragma comment(lib, "advapi32.lib")
#pragma comment(lib, "ws2_32.lib")

namespace {
constexpr SIZE_T kJobCommit = 512ull * 1024ull * 1024ull;
constexpr DWORD kCpuRate = 5000;
constexpr DWORD kWaitMs = 4000;
constexpr wchar_t kProbeSwitch[] = L"--fixed-probe";

bool require(bool value, const char* what) {
    if (!value) fprintf(stderr, "STAGE46_14_FAIL_%s_WIN32_%lu\n", what, GetLastError());
    return value;
}
std::wstring windowsRoot() {
    wchar_t root[MAX_PATH] = {};
    DWORD length = GetEnvironmentVariableW(L"SystemRoot", root, MAX_PATH);
    return length > 0 && length < MAX_PATH ? std::wstring(root) : L"";
}
std::wstring ownExe() {
    wchar_t buffer[32768] = {};
    DWORD len = GetModuleFileNameW(nullptr, buffer, 32768);
    return len > 0 && len < 32768 ? std::wstring(buffer, len) : L"";
}
std::wstring tempDir() {
    wchar_t dir[MAX_PATH] = {};
    DWORD n = GetTempPathW(MAX_PATH, dir);
    return n > 0 && n < MAX_PATH ? std::wstring(dir) : L"";
}
void closeIf(HANDLE& handle) {
    if (handle != nullptr && handle != INVALID_HANDLE_VALUE) CloseHandle(handle);
    handle = nullptr;
}
bool changeSyntheticFileAcl(const std::wstring& file, PSID appSid, ACCESS_MODE operation) {
    PACL oldAcl = nullptr, newAcl = nullptr;
    PSECURITY_DESCRIPTOR descriptor = nullptr;
    DWORD ec = GetNamedSecurityInfoW(const_cast<LPWSTR>(file.c_str()),
          SE_FILE_OBJECT, DACL_SECURITY_INFORMATION,
          nullptr, nullptr, &oldAcl, nullptr, &descriptor);
    if (ec != ERROR_SUCCESS) { SetLastError(ec); return require(false, "FILE_ACL_READ"); }
    EXPLICIT_ACCESSW access = {};
    access.grfAccessPermissions = FILE_GENERIC_READ | FILE_GENERIC_EXECUTE;
    access.grfAccessMode = operation;
    access.grfInheritance = NO_INHERITANCE;
    access.Trustee.TrusteeForm = TRUSTEE_IS_SID;
    access.Trustee.TrusteeType = TRUSTEE_IS_WELL_KNOWN_GROUP;
    access.Trustee.ptstrName = reinterpret_cast<LPWSTR>(appSid);
    ec = SetEntriesInAclW(1, &access, oldAcl, &newAcl);
    if (ec == ERROR_SUCCESS) {
        ec = SetNamedSecurityInfoW(const_cast<LPWSTR>(file.c_str()),
             SE_FILE_OBJECT, DACL_SECURITY_INFORMATION,
             nullptr, nullptr, newAcl, nullptr);
    }
    if (newAcl) LocalFree(newAcl);
    if (descriptor) LocalFree(descriptor);
    if (ec != ERROR_SUCCESS) { SetLastError(ec); return require(false, "FILE_ACL_WRITE"); }
    return true;
}

int childMain(int argc, wchar_t** argv) {
    if (argc != 4 || wcscmp(argv[1], kProbeSwitch) != 0) return 85;
    // TokenIsAppContainer is Windows TOKEN_INFORMATION_CLASS value 29.
    HANDLE token = nullptr;
    DWORD isApp = 0, size = 0;
    if (!OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY, &token)) return 86;
    BOOL ok = GetTokenInformation(token, TokenIsAppContainer,
        &isApp, sizeof(isApp), &size);
    CloseHandle(token);
    if (!ok || isApp != 1) return 87;

    if (wcscmp(argv[2], L"TOKEN") == 0) return 0;
    if (wcscmp(argv[2], L"STALL") == 0) { Sleep(60'000); return 88; }

    if (wcscmp(argv[2], L"FILE") == 0) {
        HANDLE file = CreateFileW(argv[3], GENERIC_READ, FILE_SHARE_READ,
            nullptr, OPEN_EXISTING, FILE_ATTRIBUTE_NORMAL, nullptr);
        if (file != INVALID_HANDLE_VALUE) { CloseHandle(file); return 89; }
        return GetLastError() == ERROR_ACCESS_DENIED ? 0 : 90;
    }

    if (wcscmp(argv[2], L"LOOPBACK") == 0) {
        int port = _wtoi(argv[3]);
        if (port <= 0 || port >= 65536) return 91;
        WSADATA wsa = {};
        if (WSAStartup(MAKEWORD(2, 2), &wsa) != 0) return 92;
        SOCKET socketFd = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
        if (socketFd == INVALID_SOCKET) { WSACleanup(); return 93; }
        sockaddr_in target = {};
        target.sin_family = AF_INET;
        target.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
        target.sin_port = htons(static_cast<u_short>(port));
        int result = connect(socketFd, reinterpret_cast<sockaddr*>(&target), sizeof(target));
        int error = result == SOCKET_ERROR ? WSAGetLastError() : 0;
        closesocket(socketFd);
        WSACleanup();
        if (result == 0) return 94; // Network unexpectedly permitted.
        return (error == WSAEACCES || error == WSAENETUNREACH) ? 0 : 95;
    }
    return 96;
}

struct JobEvidence {
    HANDLE job = nullptr;
    bool create() {
        job = CreateJobObjectW(nullptr, nullptr);
        if (!require(job != nullptr, "JOB_CREATE")) return false;
        JOBOBJECT_EXTENDED_LIMIT_INFORMATION limits = {};
        limits.BasicLimitInformation.LimitFlags =
            JOB_OBJECT_LIMIT_ACTIVE_PROCESS | JOB_OBJECT_LIMIT_PROCESS_MEMORY |
            JOB_OBJECT_LIMIT_JOB_MEMORY | JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE;
        limits.BasicLimitInformation.ActiveProcessLimit = 1;
        limits.ProcessMemoryLimit = kJobCommit;
        limits.JobMemoryLimit = kJobCommit;
        if (!require(SetInformationJobObject(job, JobObjectExtendedLimitInformation,
              &limits, sizeof(limits)) != 0, "JOB_SET_MEMORY")) return false;
        JOBOBJECT_CPU_RATE_CONTROL_INFORMATION cpu = {};
        cpu.ControlFlags = JOB_OBJECT_CPU_RATE_CONTROL_ENABLE |
            JOB_OBJECT_CPU_RATE_CONTROL_HARD_CAP;
        cpu.CpuRate = kCpuRate;
        if (!require(SetInformationJobObject(job, JobObjectCpuRateControlInformation,
              &cpu, sizeof(cpu)) != 0, "JOB_SET_CPU")) return false;

        JOBOBJECT_EXTENDED_LIMIT_INFORMATION actual = {};
        JOBOBJECT_CPU_RATE_CONTROL_INFORMATION actualCpu = {};
        if (!require(QueryInformationJobObject(job, JobObjectExtendedLimitInformation,
               &actual, sizeof(actual), nullptr) != 0, "JOB_QUERY_MEMORY")) return false;
        if (!require(QueryInformationJobObject(job, JobObjectCpuRateControlInformation,
               &actualCpu, sizeof(actualCpu), nullptr) != 0, "JOB_QUERY_CPU")) return false;
        bool valid = actual.BasicLimitInformation.ActiveProcessLimit == 1 &&
            actual.ProcessMemoryLimit == kJobCommit &&
            actual.JobMemoryLimit == kJobCommit &&
            (actual.BasicLimitInformation.LimitFlags &
                JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE) != 0 &&
            actualCpu.CpuRate == kCpuRate &&
            (actualCpu.ControlFlags & JOB_OBJECT_CPU_RATE_CONTROL_HARD_CAP) != 0;
        if (valid) puts("STAGE46_14_KERNEL_JOB_LIMITS_READ_BACK");
        return require(valid, "JOB_KERNEL_POLICY_MISMATCH");
    }
    ~JobEvidence() { if (job) CloseHandle(job); }
};

bool runAppChild(PSID appSid, const std::wstring& executable,
                 const std::wstring& mode, const std::wstring& arg, bool stalled) {
    JobEvidence job;
    if (!job.create()) return false;
    SIZE_T attributesSize = 0;
    InitializeProcThreadAttributeList(nullptr, 1, 0, &attributesSize);
    if (!require(attributesSize > 0, "ATTRIBUTE_LENGTH")) return false;
    std::vector<BYTE> attributes(attributesSize);
    auto* list = reinterpret_cast<LPPROC_THREAD_ATTRIBUTE_LIST>(attributes.data());
    if (!require(InitializeProcThreadAttributeList(list, 1, 0,
                   &attributesSize) != 0, "ATTRIBUTE_INIT")) return false;
    SECURITY_CAPABILITIES capabilities = {};
    capabilities.AppContainerSid = appSid;
    capabilities.Capabilities = nullptr;
    capabilities.CapabilityCount = 0; // No internetClient, privateNetwork, etc.
    if (!require(UpdateProcThreadAttribute(list, 0,
              PROC_THREAD_ATTRIBUTE_SECURITY_CAPABILITIES,
              &capabilities, sizeof(capabilities), nullptr, nullptr) != 0,
              "ATTRIBUTE_SECURITY_CAPABILITIES")) {
        DeleteProcThreadAttributeList(list); return false;
    }
    STARTUPINFOEXW start = {};
    start.StartupInfo.cb = sizeof(start);
    start.lpAttributeList = list;
    PROCESS_INFORMATION pi = {};
    // Fixed executable + enumerated fixed modes, no arbitrary commands.
    std::wstring command = L"\"" + executable + L"\" " + kProbeSwitch +
                           L" " + mode + L" \"" + arg + L"\"";
    std::vector<wchar_t> writable(command.begin(), command.end());
    writable.push_back(L'\0');
    std::wstring root = windowsRoot();
    // Strict OS bootstrap allowlist: no account tokens, API keys, GITHUB_*
    // variables, passwords or arbitrary inherited environment. AppContainer
    // startup may need more OS path variables than ordinary CreateProcessW.
    const wchar_t* names[] = {L"APPDATA", L"LOCALAPPDATA", L"PATH",
        L"ProgramData", L"SystemDrive", L"SystemRoot", L"TEMP",
        L"TMP", L"USERPROFILE", L"WINDIR"};
    std::wstring environment;
    for (const wchar_t* name : names) {
        DWORD required = GetEnvironmentVariableW(name, nullptr, 0);
        if (required <= 1 || required > 32768) continue;
        std::vector<wchar_t> value(required);
        DWORD written = GetEnvironmentVariableW(name, value.data(), required);
        if (written == 0 || written >= required) continue;
        std::wstring str(value.data(), written);
        if (str.find(L'\r') != std::wstring::npos ||
            str.find(L'\n') != std::wstring::npos ||
            str.find(L'=') != std::wstring::npos) continue;
        environment += std::wstring(name) + L"=" + str;
        environment.push_back(L'\0');
    }
    if (environment.empty()) environment = L"SystemRoot=" + root + L'\0';
    environment.push_back(L'\0');
    bool ok = false, assigned = false;
    DWORD exitStatus = 0;
    if (require(CreateProcessW(executable.c_str(), writable.data(),
           nullptr, nullptr, FALSE,
           CREATE_SUSPENDED | CREATE_NO_WINDOW | CREATE_UNICODE_ENVIRONMENT |
               EXTENDED_STARTUPINFO_PRESENT,
           const_cast<wchar_t*>(environment.data()), nullptr,
           &start.StartupInfo, &pi) != 0, "APP_CONTAINER_LAUNCH")) {
        if (require(AssignProcessToJobObject(job.job, pi.hProcess) != 0,
                    "APP_CONTAINER_JOB_ASSIGN")) {
            assigned = true;
            if (require(ResumeThread(pi.hThread) != static_cast<DWORD>(-1),
                        "APP_CONTAINER_RESUME")) {
                DWORD waited = WaitForSingleObject(pi.hProcess,
                                  stalled ? 600 : kWaitMs);
                if (stalled) {
                    ok = waited == WAIT_TIMEOUT;
                    // Job handle close terminates stalled child and descendants.
                    CloseHandle(job.job);
                    job.job = nullptr;
                    DWORD death = WaitForSingleObject(pi.hProcess, 3000);
                    ok = ok && death == WAIT_OBJECT_0;
                    if (ok) puts("STAGE46_14_APP_CONTAINER_STALL_JOB_KILLED");
                } else if (waited == WAIT_OBJECT_0 &&
                           GetExitCodeProcess(pi.hProcess, &exitStatus)) {
                    ok = exitStatus == 0;
                    if (!ok) fprintf(stderr, "STAGE46_14_CHILD_%ls_EXIT_%lu\n",
                                     mode.c_str(), exitStatus);
                }
            }
        }
        if (!assigned) TerminateProcess(pi.hProcess, 97);
        if (pi.hThread) CloseHandle(pi.hThread);
        if (pi.hProcess) CloseHandle(pi.hProcess);
    }
    DeleteProcThreadAttributeList(list);
    return ok;
}

bool protectedSyntheticFile(const std::wstring& path, PSID appSid) {
    HANDLE h = CreateFileW(path.c_str(), GENERIC_READ | GENERIC_WRITE, 0,
              nullptr, CREATE_NEW, FILE_ATTRIBUTE_NORMAL, nullptr);
    if (!require(h != INVALID_HANDLE_VALUE, "SYNTHETIC_SENTINEL_CREATE"))
        return false;
    const char marker[] = "STAGE46_14_SYNTHETIC_NOT_REAL_DATA";
    DWORD written = 0;
    BOOL wrote = WriteFile(h, marker, sizeof(marker), &written, nullptr);
    CloseHandle(h);
    return require(wrote != 0, "SYNTHETIC_SENTINEL_WRITE") &&
           changeSyntheticFileAcl(path, appSid, DENY_ACCESS);
}
int parentMain() {
    std::wstring exe = ownExe(), tmp = tempDir(), root = windowsRoot();
    if (exe.empty() || tmp.empty() || root.empty()) return 2;
    std::wstring name = L"Syntra.Stage4614.CI." +
         std::to_wstring(GetCurrentProcessId()) + L"." +
         std::to_wstring(GetTickCount64() % 1000000000);
    PSID sid = nullptr;
    HRESULT hr = CreateAppContainerProfile(name.c_str(),
          L"Syntra CI Synthetic Capability Lab",
          L"Disposable synthetic test profile - never a speech host",
          nullptr, 0, &sid);
    if (hr != S_OK || !sid) {
        fprintf(stderr, "STAGE46_14_PROFILE_CREATION_FAILED_HRESULT_%08lx\n",
                static_cast<unsigned long>(hr));
        return 3;
    }
    bool success = false;
    std::wstring fixture = tmp + L"syntra-stage4614-" +
                    std::to_wstring(GetCurrentProcessId()) + L".synthetic";
    SOCKET listener = INVALID_SOCKET;
    bool wsaStarted = false;
    do {
        // Explicitly grant this specific disposable fixture executable
        // read/execute access. No private user file receives such a grant.
        if (!changeSyntheticFileAcl(exe, sid, GRANT_ACCESS)) break;
        if (!protectedSyntheticFile(fixture, sid)) break;
        if (!runAppChild(sid, exe, L"TOKEN", L"NONE", false)) break;
        puts("STAGE46_14_REAL_APP_CONTAINER_TOKEN_VERIFIED");
        if (!runAppChild(sid, exe, L"FILE", fixture, false)) break;
        puts("STAGE46_14_PROTECTED_SYNTHETIC_FILE_ACCESS_DENIED");

        WSADATA wsa = {};
        if (WSAStartup(MAKEWORD(2, 2), &wsa) != 0) break;
        wsaStarted = true;
        listener = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
        if (listener == INVALID_SOCKET) break;
        sockaddr_in address = {};
        address.sin_family = AF_INET;
        address.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
        address.sin_port = 0;
        if (bind(listener, reinterpret_cast<sockaddr*>(&address),
                 sizeof(address)) == SOCKET_ERROR || listen(listener, 1) == SOCKET_ERROR)
            break;
        int length = sizeof(address);
        if (getsockname(listener, reinterpret_cast<sockaddr*>(&address),
                        &length) == SOCKET_ERROR) break;
        if (!runAppChild(sid, exe, L"LOOPBACK",
            std::to_wstring(ntohs(address.sin_port)), false)) break;
        puts("STAGE46_14_LOOPBACK_CONNECT_DENIED_WITH_ZERO_CAPABILITIES");
        if (!runAppChild(sid, exe, L"STALL", L"NONE", true)) break;
        success = true;
    } while (false);

    if (listener != INVALID_SOCKET) closesocket(listener);
    if (wsaStarted) WSACleanup();
    DeleteFileW(fixture.c_str());
    FreeSid(sid);
    HRESULT deleted = DeleteAppContainerProfile(name.c_str());
    if (deleted != S_OK) {
        fprintf(stderr, "STAGE46_14_PROFILE_CLEANUP_FAILED_HRESULT_%08lx\n",
                static_cast<unsigned long>(deleted));
        success = false;
    }
    if (success) puts("STAGE46_14_WINDOWS_APP_CONTAINER_SYNTHETIC_SECURITY_PASS");
    else fputs("STAGE46_14_WINDOWS_APP_CONTAINER_SYNTHETIC_SECURITY_NO_GO\n", stderr);
    return success ? 0 : 5;
}
}
int wmain(int argc, wchar_t** argv) {
    if (argc > 1) return childMain(argc, argv);
    return parentMain();
}
