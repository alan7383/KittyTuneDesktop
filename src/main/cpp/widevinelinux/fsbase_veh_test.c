// Spike: can a Vectored Exception Handler restore %fs base after Windows resets it
// on preemption, so that code that reads fs:[0x28] (like the stack canary in every
// glibc function prologue) survives?
//
// If yes, loading libwidevinecdm.so directly on Windows becomes possible (next step).
// If no, the "no WSL, native, like Linux" path is closed for good.
//
// Build with WinLibs g++:
//   g++ -O2 -mfsgsbase -std=c++17 fsbase_veh_test.c -o fsbase_veh_test.exe
//
// Runtime: no args. Prints progress and exits with code 0 on success, non-zero on
// crash/mismatch.

#include <windows.h>
#include <immintrin.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <inttypes.h>

// A tiny "TLS block" we point fs at. In real Linux glibc this is a struct with
// the stack canary at offset 0x28 and a bunch of other slots. Here we just put a
// known sentinel at 0x28 so we can tell whether a read via fs:[0x28] saw our
// block or garbage.
#define TLS_CANARY_OFFSET 0x28
#define TLS_CANARY_VALUE  0xC0FFEEBABEF00D42ULL

static __thread uint8_t tls_block[0x200];

// Points fs at our tls block for this thread and writes the sentinel.
static void install_tls(void) {
    memset(tls_block, 0, sizeof(tls_block));
    *(uint64_t*)(tls_block + TLS_CANARY_OFFSET) = TLS_CANARY_VALUE;
    _writefsbase_u64((uint64_t)tls_block);
}

// Counters observed from the VEH side.
static volatile LONG64 g_veh_fires = 0;
static volatile LONG64 g_veh_restores = 0;
static volatile LONG64 g_veh_unhandled = 0;

// Match a `mov reg, qword ptr fs:[imm8]` prefix: 64 48 8B ?? 25 ?? ?? ?? ??
// The decoder is deliberately narrow — we only recognise the exact shape glibc's
// canary read uses. Anything else falls through to EXCEPTION_CONTINUE_SEARCH.
static int looks_like_fs_read(const uint8_t* p) {
    // 64 = FS override, 48 = REX.W, 8B = MOV r64, r/m64
    if (p[0] == 0x64 && p[1] == 0x48 && p[2] == 0x8B) return 1;
    // Also catch 64 48 33 ?? ?? ?? — XOR rN, fs:[off] used by canary check epilogues.
    if (p[0] == 0x64 && p[1] == 0x48 && p[2] == 0x33) return 1;
    return 0;
}

static LONG CALLBACK fs_restore_veh(PEXCEPTION_POINTERS ep) {
    InterlockedIncrement64(&g_veh_fires);
    if (ep->ExceptionRecord->ExceptionCode != EXCEPTION_ACCESS_VIOLATION) {
        InterlockedIncrement64(&g_veh_unhandled);
        return EXCEPTION_CONTINUE_SEARCH;
    }
    // The fault address must be small (near the canary offset) — a real pointer
    // deref that happens to AV should not get patched.
    uint64_t fault_addr = (uint64_t)ep->ExceptionRecord->ExceptionInformation[1];
    if (fault_addr > 0x2000) {
        InterlockedIncrement64(&g_veh_unhandled);
        return EXCEPTION_CONTINUE_SEARCH;
    }
    // Make sure RIP really sits on a fs: access we recognise.
    const uint8_t* rip = (const uint8_t*)ep->ContextRecord->Rip;
    if (!looks_like_fs_read(rip)) {
        InterlockedIncrement64(&g_veh_unhandled);
        return EXCEPTION_CONTINUE_SEARCH;
    }
    // Restore fs base to our tls block and let the instruction retry.
    _writefsbase_u64((uint64_t)tls_block);
    InterlockedIncrement64(&g_veh_restores);
    return EXCEPTION_CONTINUE_EXECUTION;
}

// Read fs:[0x28] once. Cannot be inlined because we rely on the exact three-byte
// prefix the VEH matches.
static uint64_t __attribute__((noinline)) read_canary(void) {
    uint64_t v;
    __asm__ volatile ("mov %%fs:0x28, %0" : "=r"(v));
    return v;
}

// The hot loop: pretend we are glibc checking the canary on every call. Interleave
// the read with Sleep(0)/SwitchToThread so the scheduler has every chance to
// preempt us between the two.
static int stress(const char* tag, uint64_t iterations) {
    printf("[%s] stress start, N=%" PRIu64 "\n", tag, iterations);
    uint64_t mismatches = 0;
    const uint64_t progress_step = iterations / 10;
    for (uint64_t i = 0; i < iterations; i++) {
        uint64_t v = read_canary();
        if (v != TLS_CANARY_VALUE) mismatches++;
        if ((i & 0xFF) == 0) Sleep(1);
        else if ((i & 0x3F) == 0) SwitchToThread();
        else if ((i & 0x7)  == 0) Sleep(0);
        if (progress_step && (i % progress_step) == 0) {
            printf("[%s] i=%" PRIu64 " VEH fires=%" PRId64 " restores=%" PRId64 " mismatches=%" PRIu64 "\n",
                   tag, i, (int64_t)g_veh_fires, (int64_t)g_veh_restores, mismatches);
        }
    }
    printf("[%s] stress done: %" PRIu64 " reads, %" PRIu64 " mismatches\n", tag, iterations, mismatches);
    return mismatches == 0 ? 0 : 1;
}

static DWORD WINAPI worker(LPVOID arg) {
    (void)arg;
    install_tls();
    return stress("worker", 200'000) == 0 ? 0 : 1;
}

int main(void) {
    setvbuf(stdout, NULL, _IONBF, 0);
    setvbuf(stderr, NULL, _IONBF, 0);
    printf("spike up\n");

    // VEH first — if anything in main() faults before we install it, we crash.
    AddVectoredExceptionHandler(1, fs_restore_veh);
    printf("VEH installed\n");

    install_tls();
    printf("fs base set to %p\n", (void*)tls_block);

    // Sanity: one read without stress. Should just work: fs base is fresh.
    uint64_t sanity = read_canary();
    printf("sanity canary = 0x%" PRIx64 " (want 0x%" PRIx64 ")\n", sanity, TLS_CANARY_VALUE);
    if (sanity != TLS_CANARY_VALUE) {
        fprintf(stderr, "FAIL: wrfsbase did not take effect at all\n");
        return 2;
    }

    // Main thread stress.
    int main_rc = stress("main", 200'000);

    // Second thread — its TLS is a different location, so reading the canary
    // across threads also exercises per-thread install_tls().
    HANDLE t = CreateThread(NULL, 0, worker, NULL, 0, NULL);
    WaitForSingleObject(t, INFINITE);
    DWORD worker_rc = 1;
    GetExitCodeThread(t, &worker_rc);
    CloseHandle(t);

    printf("VEH fires=%" PRId64 " restores=%" PRId64 " unhandled=%" PRId64 "\n",
           (int64_t)g_veh_fires, (int64_t)g_veh_restores, (int64_t)g_veh_unhandled);

    if (main_rc == 0 && worker_rc == 0 && g_veh_restores > 0) {
        printf("PASS: VEH+wrfsbase survived preemption\n");
        return 0;
    }
    if (g_veh_restores == 0) {
        fprintf(stderr, "WARN: no VEH restores fired — did Windows even reset fs? "
                        "Either the scheduler left fs alone this run, or the VEH "
                        "never saw the AV.\n");
    }
    fprintf(stderr, "FAIL: main_rc=%d worker_rc=%lu\n", main_rc, worker_rc);
    return 1;
}
