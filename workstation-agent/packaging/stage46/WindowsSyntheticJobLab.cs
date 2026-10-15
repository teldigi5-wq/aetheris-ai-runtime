// Stage 46.12: WINDOWS-ONLY inert test harness for real Windows Job Objects.
// Not an application component, signed host, restricted-token sandbox, service,
// audio engine, voice gateway, or production workstation installer.
// Uses a fixed precompiled Java fixture; no PCM, network, user commands, models,
// microphones, logs of user content, or arbitrary executable selection.
//
// IMPORTANT: all Windows Job Object properties apply to the child only;
// existing host-user filesystem and network privileges remain available.
// Compile and invoke only inside the dedicated Windows GitHub Actions lab.
using System;
using System.ComponentModel;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;

namespace SyntraStage4612Lab {
    public sealed class JobEvidence {
        public string Verdict { get; internal set; }
        public uint Pid { get; internal set; }
        public uint ExitCode { get; internal set; }
        public bool KernelLimitsReadBack { get; internal set; }
        public bool AssignedWhileSuspended { get; internal set; }
        public bool ChildExited { get; internal set; }
        public uint ActiveProcessLimit { get; internal set; }
        public ulong ProcessMemoryLimit { get; internal set; }
        public ulong JobMemoryLimit { get; internal set; }
        public uint CpuHardCapRate { get; internal set; }
        public bool KillOnJobClose { get; internal set; }
        public bool PrivilegeReducedTokenCreated { get; internal set; }
        public bool PrivilegeReducedTokenUsedForChild { get; internal set; }
        public int ChildEnabledPrivilegeCount { get; internal set; }
        public int ParentEnabledPrivilegeCount { get; internal set; }
    }

    public static class WindowsSyntheticJobLab {
        private const uint CREATE_SUSPENDED = 0x4;
        private const uint TOKEN_QUERY = 0x0008;
        private const uint TOKEN_DUPLICATE = 0x0002;
        private const uint DISABLE_MAX_PRIVILEGE = 0x00000001;
        private const uint LUA_TOKEN = 0x00000004;
        private const uint SE_PRIVILEGE_ENABLED = 0x00000002;
        private const int TOKEN_PRIVILEGES_INFORMATION = 3;

        private const uint CREATE_NO_WINDOW = 0x08000000;
        private const uint CREATE_UNICODE_ENVIRONMENT = 0x00000400;
        private const uint WAIT_OBJECT_0 = 0;
        private const uint WAIT_TIMEOUT = 0x102;
        private const uint WAIT_FAILED = 0xffffffff;
        private const uint JOB_OBJECT_LIMIT_ACTIVE_PROCESS = 0x8;
        private const uint JOB_OBJECT_LIMIT_PROCESS_MEMORY = 0x100;
        private const uint JOB_OBJECT_LIMIT_JOB_MEMORY = 0x200;
        private const uint JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE = 0x2000;
        private const uint JOB_OBJECT_CPU_RATE_CONTROL_ENABLE = 0x1;
        private const uint JOB_OBJECT_CPU_RATE_CONTROL_HARD_CAP = 0x4;
        private const uint CPU_RATE = 5000; // 50% of available CPU
        private const ulong MEMORY_BYTES = 512UL * 1024UL * 1024UL;
        private const string FIXED_MAIN =
            "io.aetheris.orchestrator.voice.SyntraSyntheticJobProbeMain";

        [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
        private struct STARTUPINFO {
            public uint cb;
            public string lpReserved, lpDesktop, lpTitle;
            public uint dwX, dwY, dwXSize, dwYSize, dwXCountChars, dwYCountChars;
            public uint dwFillAttribute, dwFlags;
            public ushort wShowWindow, cbReserved2;
            public IntPtr lpReserved2, hStdInput, hStdOutput, hStdError;
        }
        [StructLayout(LayoutKind.Sequential)]
        private struct PROCESS_INFORMATION {
            public IntPtr hProcess, hThread;
            public uint dwProcessId, dwThreadId;
        }
        [StructLayout(LayoutKind.Sequential)]
        private struct JOBOBJECT_BASIC_LIMIT_INFORMATION {
            public long PerProcessUserTimeLimit, PerJobUserTimeLimit;
            public uint LimitFlags;
            public UIntPtr MinimumWorkingSetSize, MaximumWorkingSetSize;
            public uint ActiveProcessLimit;
            public UIntPtr Affinity;
            public uint PriorityClass, SchedulingClass;
        }
        [StructLayout(LayoutKind.Sequential)]
        private struct IO_COUNTERS {
            public ulong ReadOperationCount, WriteOperationCount, OtherOperationCount;
            public ulong ReadTransferCount, WriteTransferCount, OtherTransferCount;
        }
        [StructLayout(LayoutKind.Sequential)]
        private struct JOBOBJECT_EXTENDED_LIMIT_INFORMATION {
            public JOBOBJECT_BASIC_LIMIT_INFORMATION BasicLimitInformation;
            public IO_COUNTERS IoInfo;
            public UIntPtr ProcessMemoryLimit, JobMemoryLimit;
            public UIntPtr PeakProcessMemoryUsed, PeakJobMemoryUsed;
        }
        [StructLayout(LayoutKind.Sequential)]
        private struct JOBOBJECT_CPU_RATE_CONTROL_INFORMATION {
            public uint ControlFlags, CpuRate;
        }

        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
        private static extern IntPtr CreateJobObject(IntPtr attributes, string name);
        [DllImport("kernel32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        private static extern bool SetInformationJobObject(
            IntPtr job, int infoClass, IntPtr info, uint size);
        [DllImport("kernel32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        private static extern bool QueryInformationJobObject(
            IntPtr job, int infoClass, IntPtr info, uint size, out uint returned);
        [DllImport("kernel32.dll", CharSet = CharSet.Unicode, EntryPoint = "CreateProcessW",
            SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        private static extern bool CreateProcess(
            string application, StringBuilder commandLine,
            IntPtr processAttributes, IntPtr threadAttributes,
            [MarshalAs(UnmanagedType.Bool)] bool inheritHandles,
            uint creationFlags, IntPtr environment, string currentDirectory,
            ref STARTUPINFO startup, out PROCESS_INFORMATION process);
        [DllImport("kernel32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        private static extern bool AssignProcessToJobObject(IntPtr job, IntPtr process);
        [DllImport("kernel32.dll")]
        private static extern IntPtr GetCurrentProcess();
        [DllImport("advapi32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        private static extern bool OpenProcessToken(IntPtr process, uint access, out IntPtr token);
        [DllImport("advapi32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        private static extern bool CreateRestrictedToken(IntPtr existingToken, uint flags,
            uint disableSidCount, IntPtr sidsToDisable,
            uint deletePrivilegeCount, IntPtr privilegesToDelete,
            uint restrictedSidCount, IntPtr restrictingSids, out IntPtr restrictedToken);
        [DllImport("advapi32.dll", CharSet = CharSet.Unicode, EntryPoint = "CreateProcessAsUserW", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        private static extern bool CreateProcessAsUser(IntPtr token, string application,
            StringBuilder commandLine, IntPtr processAttributes, IntPtr threadAttributes,
            [MarshalAs(UnmanagedType.Bool)] bool inheritHandles, uint creationFlags,
            IntPtr environment, string currentDirectory, ref STARTUPINFO startup,
            out PROCESS_INFORMATION process);
        [DllImport("advapi32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        private static extern bool GetTokenInformation(IntPtr token, int tokenInfoClass,
            IntPtr output, uint outputLength, out uint returnLength);
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern uint ResumeThread(IntPtr thread);
        [DllImport("kernel32.dll", SetLastError = true)]
        private static extern uint WaitForSingleObject(IntPtr handle, uint milliseconds);
        [DllImport("kernel32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        private static extern bool GetExitCodeProcess(IntPtr process, out uint code);
        [DllImport("kernel32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        private static extern bool TerminateProcess(IntPtr process, uint exitCode);
        [DllImport("kernel32.dll", SetLastError = true)]
        [return: MarshalAs(UnmanagedType.Bool)]
        private static extern bool CloseHandle(IntPtr handle);

        private static void Require(bool passed, string stage) {
            if (!passed) throw new Win32Exception(Marshal.GetLastWin32Error(),
                "STAGE46_12_" + stage);
        }

        private static void SetJobInfo<T>(IntPtr job, int infoClass, T info) where T : struct {
            int length = Marshal.SizeOf(typeof(T));
            IntPtr buffer = Marshal.AllocHGlobal(length);
            try {
                Marshal.StructureToPtr(info, buffer, false);
                Require(SetInformationJobObject(job, infoClass, buffer, (uint)length),
                    "JOB_LIMIT_REJECTED");
            } finally { Marshal.FreeHGlobal(buffer); }
        }

        private static T ReadJobInfo<T>(IntPtr job, int infoClass) where T : struct {
            int length = Marshal.SizeOf(typeof(T));
            IntPtr buffer = Marshal.AllocHGlobal(length);
            try {
                uint returned;
                Require(QueryInformationJobObject(job, infoClass, buffer,
                    (uint)length, out returned), "JOB_QUERY_FAILED");
                if (returned < length)
                    throw new InvalidOperationException("INCOMPLETE_KERNEL_JOB_LIMIT_EVIDENCE");
                return (T)Marshal.PtrToStructure(buffer, typeof(T));
            } finally { Marshal.FreeHGlobal(buffer); }
        }

        // Strictly read-only privilege evidence. This does NOT establish an
        // AppContainer, restricted filesystem DACLs or denied network access.
        private static int EnabledPrivilegeCount(IntPtr token) {
            uint bytes;
            GetTokenInformation(token, TOKEN_PRIVILEGES_INFORMATION,
                IntPtr.Zero, 0, out bytes);
            if (bytes < 4 || bytes > 65536)
                throw new InvalidOperationException("INVALID_TOKEN_PRIVILEGE_LENGTH");
            IntPtr buffer = Marshal.AllocHGlobal((int)bytes);
            try {
                Require(GetTokenInformation(token, TOKEN_PRIVILEGES_INFORMATION,
                    buffer, bytes, out bytes), "TOKEN_PRIVILEGE_QUERY_FAILED");
                uint count = (uint)Marshal.ReadInt32(buffer);
                if (count > 1000 || (ulong)4 + (ulong)count * 12 > (ulong)bytes)
                    throw new InvalidOperationException("INVALID_TOKEN_PRIVILEGE_LAYOUT");
                int enabled = 0;
                for (int i = 0; i < (int)count; i++) {
                    int attr = Marshal.ReadInt32(buffer, 4 + i * 12 + 8);
                    if (((uint)attr & SE_PRIVILEGE_ENABLED) != 0) enabled++;
                }
                return enabled;
            } finally { Marshal.FreeHGlobal(buffer); }
        }

        private static string SafePath(string value, string kind) {
            if (string.IsNullOrWhiteSpace(value) || value.Contains("\"") ||
                value.Contains("\0") || value.Contains("\r") || value.Contains("\n"))
                throw new ArgumentException("INVALID_" + kind);
            return Path.GetFullPath(value);
        }

        /// <summary>
        /// A suspended, inert synthetic child is bound to a memory/CPU/process
        /// limited Windows Job BEFORE its first instruction executes.
        /// On successful exit or timeout, closing the job destroys any members.
        /// Only local, fixed CI test modes are accepted.
        /// </summary>
        public static JobEvidence Run(string trustedJavaExe, string builtClassesDir,
                                      string mode, int maxWaitMillis) {
            return RunInternal(trustedJavaExe, builtClassesDir, mode, maxWaitMillis, false);
        }

        // Stage 46.13: explicit CI-only reduced-privilege identity variation.
        // Windows Job policy and fixed probe remain identical to Stage 46.12.
        // NO filesystem/network sandbox or production token attestation.
        public static JobEvidence RunPrivilegeReduced(string trustedJavaExe,
            string builtClassesDir, string mode, int maxWaitMillis) {
            return RunInternal(trustedJavaExe, builtClassesDir, mode, maxWaitMillis, true);
        }

        private static JobEvidence RunInternal(string trustedJavaExe, string builtClassesDir,
                                      string mode, int maxWaitMillis, bool useReducedToken) {
            if (Environment.OSVersion.Platform != PlatformID.Win32NT)
                throw new PlatformNotSupportedException("WINDOWS_JOB_LAB_ONLY");
            if (mode != "EXIT" && mode != "STALL" &&
                mode != "FAIL" && mode != "TRY_CHILD" &&
                mode != "CHECK_ENV")
                throw new ArgumentException("INVALID_FIXED_SYNTHETIC_MODE");
            if (maxWaitMillis < 100 || maxWaitMillis > 5000)
                throw new ArgumentException("INVALID_LAB_WAIT_BUDGET");

            string java = SafePath(trustedJavaExe, "JAVA_PATH");
            string classes = SafePath(builtClassesDir, "CLASSES_PATH");
            if (!string.Equals(Path.GetFileName(java), "java.exe",
                    StringComparison.OrdinalIgnoreCase) || !File.Exists(java))
                throw new ArgumentException("FIXED_JAVA_EXECUTABLE_REQUIRED");
            string probe = Path.Combine(classes, "io", "aetheris", "orchestrator",
                "voice", "SyntraSyntheticJobProbeMain.class");
            if (!File.Exists(probe))
                throw new ArgumentException("PRECOMPILED_SYNTHETIC_PROBE_REQUIRED");

            string root = Environment.GetEnvironmentVariable("SystemRoot");
            if (string.IsNullOrWhiteSpace(root) || root.Contains("\0") ||
                root.Contains("\r") || root.Contains("\n"))
                throw new InvalidOperationException("SAFE_SYSTEMROOT_UNAVAILABLE");
            // CREATE_UNICODE_ENVIRONMENT with a strict, minimal environment.
            // Never forward caller API tokens or a full inherited environment.
            string environment = "SystemRoot=" + root + "\0WINDIR=" + root + "\0\0";
            IntPtr environmentPtr = Marshal.StringToHGlobalUni(environment);
            IntPtr job = IntPtr.Zero;
            IntPtr callerToken = IntPtr.Zero;
            IntPtr reducedToken = IntPtr.Zero;
            PROCESS_INFORMATION pi = new PROCESS_INFORMATION();
            bool processCreated = false;
            bool assigned = false;
            var evidence = new JobEvidence();
            try {
                job = CreateJobObject(IntPtr.Zero, null);
                Require(job != IntPtr.Zero, "JOB_CREATE_FAILED");

                var limits = new JOBOBJECT_EXTENDED_LIMIT_INFORMATION();
                limits.BasicLimitInformation.LimitFlags =
                    JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE |
                    JOB_OBJECT_LIMIT_ACTIVE_PROCESS |
                    JOB_OBJECT_LIMIT_PROCESS_MEMORY |
                    JOB_OBJECT_LIMIT_JOB_MEMORY;
                limits.BasicLimitInformation.ActiveProcessLimit = 1;
                limits.ProcessMemoryLimit = new UIntPtr(MEMORY_BYTES);
                limits.JobMemoryLimit = new UIntPtr(MEMORY_BYTES);
                SetJobInfo(job, 9, limits);

                var cpu = new JOBOBJECT_CPU_RATE_CONTROL_INFORMATION();
                cpu.ControlFlags = JOB_OBJECT_CPU_RATE_CONTROL_ENABLE |
                    JOB_OBJECT_CPU_RATE_CONTROL_HARD_CAP;
                cpu.CpuRate = CPU_RATE;
                SetJobInfo(job, 15, cpu);

                // Confirm policy from the kernel before launching any child.
                var checkedLimits =
                    ReadJobInfo<JOBOBJECT_EXTENDED_LIMIT_INFORMATION>(job, 9);
                var checkedCpu =
                    ReadJobInfo<JOBOBJECT_CPU_RATE_CONTROL_INFORMATION>(job, 15);
                uint flags = checkedLimits.BasicLimitInformation.LimitFlags;
                evidence.ActiveProcessLimit =
                    checkedLimits.BasicLimitInformation.ActiveProcessLimit;
                evidence.ProcessMemoryLimit =
                    checkedLimits.ProcessMemoryLimit.ToUInt64();
                evidence.JobMemoryLimit = checkedLimits.JobMemoryLimit.ToUInt64();
                evidence.CpuHardCapRate = checkedCpu.CpuRate;
                evidence.KillOnJobClose =
                    (flags & JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE) != 0;
                evidence.KernelLimitsReadBack =
                    evidence.ActiveProcessLimit == 1 &&
                    evidence.ProcessMemoryLimit == MEMORY_BYTES &&
                    evidence.JobMemoryLimit == MEMORY_BYTES &&
                    evidence.CpuHardCapRate == CPU_RATE &&
                    evidence.KillOnJobClose &&
                    (flags & (JOB_OBJECT_LIMIT_ACTIVE_PROCESS |
                        JOB_OBJECT_LIMIT_PROCESS_MEMORY |
                        JOB_OBJECT_LIMIT_JOB_MEMORY)) ==
                    (JOB_OBJECT_LIMIT_ACTIVE_PROCESS |
                        JOB_OBJECT_LIMIT_PROCESS_MEMORY |
                        JOB_OBJECT_LIMIT_JOB_MEMORY) &&
                    (checkedCpu.ControlFlags &
                        (JOB_OBJECT_CPU_RATE_CONTROL_ENABLE |
                         JOB_OBJECT_CPU_RATE_CONTROL_HARD_CAP)) ==
                        (JOB_OBJECT_CPU_RATE_CONTROL_ENABLE |
                         JOB_OBJECT_CPU_RATE_CONTROL_HARD_CAP);
                if (!evidence.KernelLimitsReadBack)
                    throw new InvalidOperationException("JOB_KERNEL_LIMITS_MISMATCH");

                var startup = new STARTUPINFO();
                startup.cb = (uint)Marshal.SizeOf(typeof(STARTUPINFO));
                var commandLine = new StringBuilder("\"" + java +
                    "\" -Xms16m -Xmx64m -cp \"" + classes + "\" " + FIXED_MAIN +
                    " " + mode);
                if (useReducedToken) {
                    Require(OpenProcessToken(GetCurrentProcess(), TOKEN_QUERY | TOKEN_DUPLICATE,
                        out callerToken), "PARENT_TOKEN_QUERY_FAILED");
                    evidence.ParentEnabledPrivilegeCount = EnabledPrivilegeCount(callerToken);
                    Require(CreateRestrictedToken(callerToken,
                        DISABLE_MAX_PRIVILEGE | LUA_TOKEN, 0, IntPtr.Zero,
                        0, IntPtr.Zero, 0, IntPtr.Zero, out reducedToken),
                        "PRIVILEGE_REDUCED_TOKEN_CREATE_FAILED");
                    evidence.PrivilegeReducedTokenCreated = true;
                    int reducedEnabled = EnabledPrivilegeCount(reducedToken);
                    if (reducedEnabled > 1 ||
                        reducedEnabled > evidence.ParentEnabledPrivilegeCount)
                        throw new InvalidOperationException("REDUCED_TOKEN_PRIVILEGES_NOT_REDUCED");
                    Require(CreateProcessAsUser(reducedToken, java, commandLine,
                        IntPtr.Zero, IntPtr.Zero, false,
                        CREATE_SUSPENDED | CREATE_NO_WINDOW | CREATE_UNICODE_ENVIRONMENT,
                        environmentPtr, null, ref startup, out pi),
                        "PRIVILEGE_REDUCED_CHILD_LAUNCH_FAILED");
                    evidence.PrivilegeReducedTokenUsedForChild = true;
                } else {
                    Require(CreateProcess(java, commandLine, IntPtr.Zero,
                        IntPtr.Zero, false,
                        CREATE_SUSPENDED | CREATE_NO_WINDOW | CREATE_UNICODE_ENVIRONMENT,
                        environmentPtr, null, ref startup, out pi),
                        "SYNTHETIC_CHILD_START_FAILED");
                }
                processCreated = true;
                evidence.Pid = pi.dwProcessId;
                if (useReducedToken) {
                    IntPtr childToken;
                    Require(OpenProcessToken(pi.hProcess, TOKEN_QUERY, out childToken),
                        "REDUCED_CHILD_TOKEN_QUERY_FAILED");
                    try {
                        evidence.ChildEnabledPrivilegeCount = EnabledPrivilegeCount(childToken);
                        if (evidence.ChildEnabledPrivilegeCount > 1 ||
                            evidence.ChildEnabledPrivilegeCount > evidence.ParentEnabledPrivilegeCount)
                            throw new InvalidOperationException(
                                "REDUCED_CHILD_TOKEN_PRIVILEGES_NOT_VERIFIED");
                    } finally { CloseHandle(childToken); }
                }
                Require(AssignProcessToJobObject(job, pi.hProcess),
                    "ASSIGN_SUSPENDED_CHILD_TO_JOB_FAILED");
                evidence.AssignedWhileSuspended = true;
                assigned = true;

                uint resumeResult = ResumeThread(pi.hThread);
                Require(resumeResult != uint.MaxValue, "RESUME_JOB_CHILD_FAILED");
 
                uint wait = WaitForSingleObject(pi.hProcess, (uint)maxWaitMillis);
                if (wait == WAIT_TIMEOUT) {
                    // JOB_OBJECT_LIMIT_KILL_ON_JOB_CLOSE applies to all members.
                    CloseHandle(job);
                    job = IntPtr.Zero;
                    uint death = WaitForSingleObject(pi.hProcess, 3000);
                    evidence.ChildExited = death == WAIT_OBJECT_0;
                    if (!evidence.ChildExited)
                        throw new InvalidOperationException("JOB_TERMINATION_NOT_CONFIRMED");
                    evidence.Verdict = "TIMED_OUT_KILLED";
                    return evidence;
                }
                if (wait == WAIT_FAILED)
                    throw new Win32Exception(Marshal.GetLastWin32Error(),
                        "JOB_CHILD_WAIT_FAILED");
                if (wait != WAIT_OBJECT_0)
                    throw new InvalidOperationException("UNEXPECTED_JOB_WAIT_RESULT");
                evidence.ChildExited = true;
                uint code;
                Require(GetExitCodeProcess(pi.hProcess, out code),
                    "CHILD_EXIT_STATUS_FAILED");
                evidence.ExitCode = code;
                evidence.Verdict = code == 0 ? "EXITED_SUCCESS" : "CHILD_REJECTED";
                return evidence;
            } finally {
                // If ASSIGN fails, the still-suspended child must not escape
                // and run uncontained. Never resume a child before assignment.
                if (job != IntPtr.Zero) CloseHandle(job);
                if (processCreated) {
                    // If the OS refused Job assignment, the child is still
                    // suspended; terminate it explicitly rather than orphaning
                    // an uncontained process.
                    if (!assigned && pi.hProcess != IntPtr.Zero) {
                        TerminateProcess(pi.hProcess, 26);
                        WaitForSingleObject(pi.hProcess, 3000);
                    }
                    if (pi.hThread != IntPtr.Zero) CloseHandle(pi.hThread);
                    if (pi.hProcess != IntPtr.Zero) CloseHandle(pi.hProcess);
                }
                if (reducedToken != IntPtr.Zero) CloseHandle(reducedToken);
                if (callerToken != IntPtr.Zero) CloseHandle(callerToken);
                Marshal.FreeHGlobal(environmentPtr);
            }
        }
    }
}
