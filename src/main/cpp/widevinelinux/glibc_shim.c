// The libc surface libwidevinecdm.so expects, implemented on top of Win32 and ucrt.
//
// ABI, first and last: the CDM is System V AMD64 code. Its arguments arrive in rdi/rsi/rdx/rcx/r8/r9
// and its return values in rax/xmm0, while everything this file compiles against (ucrt, the Windows
// API, this compiler's own convention) uses the Microsoft convention (rcx/rdx/r8/r9, rax/xmm0).
// Every function the CDM can call is therefore compiled with `__attribute__((sysv_abi))`; internal
// helpers stay in the native convention. Getting this wrong is not a subtle bug: the CDM's mutex
// pointer arrives in rdi, a Windows-ABI callee reads rcx, and the mutex lock silently operates on
// whatever register value happened to be there.
//
// Coverage matters as much as correctness: a symbol that resolves to NULL is worse than one that
// returns "not supported", because the CDM will call through it. Every name the binary imports is
// answered here; the loader additionally gives anything else a logging stub.
#include "winelf.h"

#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <bcrypt.h>
#include <immintrin.h>
#include <ctype.h>
#include <errno.h>
#include <inttypes.h>
#include <math.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <wchar.h>

#define SYSV __attribute__((sysv_abi))

// ---------------------------------------------------------------------------------------------
// Call tracing: a small per-thread ring of the last shim calls, printed when the CDM faults. It
// costs a store per call and answers the only question that matters when it breaks: which import
// did it just touch, and with what arguments.
// ---------------------------------------------------------------------------------------------

typedef struct {
    const char* function;
    uint64_t argument0;
    uint64_t argument1;
    void* caller;
} ShimTraceEntry;

#define TRACE_DEPTH 128

static __thread ShimTraceEntry g_trace[TRACE_DEPTH];
static __thread unsigned g_trace_position;
static int g_trace_enabled = 1;
static int g_trace_verbose;

static __attribute__((noinline)) void shim_trace(const char* function, void* caller,
                                                 uint64_t argument0, uint64_t argument1) {
    if (!g_trace_enabled) return;
    static int verbose_checked;
    if (!verbose_checked) {
        verbose_checked = 1;
        const char* value = getenv("KT_LOG");
        if (value && *value && strcmp(value, "0") != 0) g_trace_verbose = 1;
    }
    ShimTraceEntry* entry = &g_trace[g_trace_position++ % TRACE_DEPTH];
    entry->function = function;
    entry->argument0 = argument0;
    entry->argument1 = argument1;
    entry->caller = caller;

    if (g_trace_verbose) {
        char where[256];
        winelf_describe_address(caller, where, sizeof(where));
        fprintf(stderr, "[call] %-30s (0x%llx, 0x%llx) from %s\n", function,
                (unsigned long long)argument0, (unsigned long long)argument1, where);
    }
}

#define T0(name) shim_trace(name, __builtin_return_address(0), 0, 0)
#define T1(name, a) shim_trace(name, __builtin_return_address(0), (uint64_t)(a), 0)
#define T2(name, a, b) shim_trace(name, __builtin_return_address(0), (uint64_t)(a), (uint64_t)(b))

extern "C" void shim_dump_trace(void) {
    unsigned start = 0;
    unsigned count = g_trace_position < TRACE_DEPTH ? g_trace_position : TRACE_DEPTH;
    if (g_trace_position > TRACE_DEPTH) start = g_trace_position % TRACE_DEPTH;
    fprintf(stderr, "[shim] last %u calls on this thread (oldest first):\n", count);
    for (unsigned i = 0; i < count; i++) {
        const ShimTraceEntry* entry = &g_trace[(start + i) % TRACE_DEPTH];
        if (!entry->function) continue;
        char where[256];
        winelf_describe_address(entry->caller, where, sizeof(where));
        fprintf(stderr, "[shim]   %-32s (0x%llx, 0x%llx) from %s\n", entry->function,
                (unsigned long long)entry->argument0, (unsigned long long)entry->argument1, where);
    }
    fflush(stderr);
}

extern "C" void shim_set_trace_enabled(int enabled) { g_trace_enabled = enabled; }
extern "C" void shim_set_trace_verbose(int verbose) { g_trace_verbose = verbose; }

// ---------------------------------------------------------------------------------------------
// errno
// ---------------------------------------------------------------------------------------------

static __thread int g_errno;

SYSV int* __errno_location(void) { return &g_errno; }

int* shim_errno(void) { return &g_errno; }

// Reported by the CDM as "the top of the stack"; it only compares it against a pointer.
void* shim_stack_end = (void*)0x7FFFFFFF0000ULL;
static char* g_environ_table[] = { NULL };
char** shim_environ = g_environ_table;

// ---------------------------------------------------------------------------------------------
// Memory
// ---------------------------------------------------------------------------------------------

SYSV void* shim_malloc(size_t size) {
    T1("malloc", size);
    return HeapAlloc(GetProcessHeap(), 0, size ? size : 1);
}

SYSV void shim_free(void* pointer) {
    T1("free", pointer);
    if (pointer) HeapFree(GetProcessHeap(), 0, pointer);
}

SYSV void* shim_calloc(size_t count, size_t size) {
    T2("calloc", count, size);
    return HeapAlloc(GetProcessHeap(), HEAP_ZERO_MEMORY, (count && size) ? count * size : 1);
}

SYSV void* shim_realloc(void* pointer, size_t size) {
    T2("realloc", pointer, size);
    if (!pointer) return HeapAlloc(GetProcessHeap(), 0, size ? size : 1);
    if (!size) { HeapFree(GetProcessHeap(), 0, pointer); return NULL; }
    return HeapReAlloc(GetProcessHeap(), 0, pointer, size);
}

SYSV int shim_posix_memalign(void** out, size_t alignment, size_t size) {
    T2("posix_memalign", alignment, size);
    if (!out) return EINVAL;
    if (alignment < sizeof(void*) || (alignment & (alignment - 1)) != 0) return EINVAL;
    void* block = _aligned_malloc(size, alignment);
    if (!block) return ENOMEM;
    *out = block;
    return 0;
}

SYSV void* shim_aligned_alloc(size_t alignment, size_t size) {
    T2("aligned_alloc", alignment, size);
    return _aligned_malloc(size, alignment ? alignment : 16);
}

SYSV void* shim_memalign(size_t alignment, size_t size) { return shim_aligned_alloc(alignment, size); }

SYSV size_t shim_malloc_usable_size(void* pointer) {
    return pointer ? HeapSize(GetProcessHeap(), 0, pointer) : 0;
}

// ---------------------------------------------------------------------------------------------
// mem*/str* â€” thin System V trampolines around the C runtime
// ---------------------------------------------------------------------------------------------

SYSV void* sysv_memcpy(void* d, const void* s, size_t n) { return memcpy(d, s, n); }
SYSV void* sysv_memmove(void* d, const void* s, size_t n) { return memmove(d, s, n); }
SYSV void* sysv_memset(void* d, int c, size_t n) { return memset(d, c, n); }
SYSV int sysv_memcmp(const void* a, const void* b, size_t n) { return memcmp(a, b, n); }
SYSV void* sysv_memchr(const void* s, int c, size_t n) { return memchr(s, c, n); }
SYSV size_t sysv_strlen(const char* s) { return s ? strlen(s) : 0; }
SYSV int sysv_strcmp(const char* a, const char* b) {
    if (!a) return b ? -1 : 0;
    if (!b) return 1;
    return strcmp(a, b);
}
SYSV char* sysv_strchr(const char* s, int c) { return s ? (char*)strchr(s, c) : NULL; }
SYSV char* sysv_strrchr(const char* s, int c) { return s ? (char*)strrchr(s, c) : NULL; }
SYSV char* sysv_strncpy(char* d, const char* s, size_t n) { return strncpy(d, s, n); }
SYSV char* sysv_strncat(char* d, const char* s, size_t n) { return strncat(d, s, n); }
SYSV char* sysv_strcpy(char* d, const char* s) { return strcpy(d, s); }
SYSV char* sysv_strcat(char* d, const char* s) { return strcat(d, s); }
SYSV int sysv_strncmp(const char* a, const char* b, size_t n) { return strncmp(a, b, n); }
SYSV char* sysv_strerror(int e) { return (char*)strerror(e); }
SYSV char* sysv_strdup(const char* s) { return s ? _strdup(s) : NULL; }
SYSV char* sysv_strndup(const char* s, size_t n) { return s ? _strdup(s) : NULL; }
SYSV char* sysv_strstr(const char* haystack, const char* needle) {
    if (!haystack) return NULL;
    return (char*)strstr(haystack, needle);
}
SYSV int sysv_rand(void) { return rand(); }
SYSV void sysv_srand(unsigned seed) { srand(seed); }

// libm. A double-only signature is register-compatible between the two conventions (xmm0 on both
// sides), but `ldexp` and `frexp` carry an integer or a pointer as the second argument, so every
// entry point is declared System V explicitly rather than relied upon to match.
SYSV double sysv_ceil(double value) { return ceil(value); }
SYSV float sysv_ceilf(float value) { return ceilf(value); }
SYSV double sysv_floor(double value) { return floor(value); }
SYSV double sysv_exp(double value) { return exp(value); }
SYSV double sysv_log2(double value) { return log2(value); }
SYSV double sysv_rint(double value) { return rint(value); }
SYSV double sysv_round(double value) { return round(value); }
SYSV double sysv_ldexp(double value, int exponent) { return ldexp(value, exponent); }
SYSV double sysv_frexp(double value, int* exponent) { return frexp(value, exponent); }
SYSV double sysv_pow(double base, double exponent) { return pow(base, exponent); }
SYSV double sysv_log(double value) { return log(value); }
SYSV double sysv_sqrt(double value) { return sqrt(value); }
SYSV double sysv_fmod(double x, double y) { return fmod(x, y); }

// glibc's `strerror_r` is the GNU one: it returns a char*. Declaring the XSI variant here would
// hand the CDM an integer where it expects a pointer.
SYSV char* sysv_strerror_r(int code, char* buffer, size_t size) {
    if (!buffer || !size) return (char*)strerror(code);
    strerror_s(buffer, size, code);
    return buffer;
}

SYSV long sysv_strtol(const char* s, char** end, int base) { return strtol(s, end, base); }
SYSV unsigned long sysv_strtoul(const char* s, char** end, int base) { return strtoul(s, end, base); }
SYSV long long sysv_strtoll(const char* s, char** end, int base) { return strtoll(s, end, base); }
SYSV unsigned long long sysv_strtoull(const char* s, char** end, int base) { return strtoull(s, end, base); }
SYSV double sysv_strtod(const char* s, char** end) { return strtod(s, end); }
SYSV float sysv_strtof(const char* s, char** end) { return strtof(s, end); }
// _l variants: the locale argument does not exist on Windows; the C locale is what we present.
SYSV long sysv_strtol_l(const char* s, char** end, void* locale) { (void)locale; return strtol(s, end, 10); }
SYSV unsigned long sysv_strtoul_l(const char* s, char** end, void* locale) { (void)locale; return strtoul(s, end, 10); }
SYSV long long sysv_strtoll_l(const char* s, char** end, void* locale) { (void)locale; return strtoll(s, end, 10); }
SYSV unsigned long long sysv_strtoull_l(const char* s, char** end, void* locale) { (void)locale; return strtoull(s, end, 10); }
SYSV double sysv_strtod_l(const char* s, char** end, void* locale) { (void)locale; return strtod(s, end); }
SYSV float sysv_strtof_l(const char* s, char** end, void* locale) { (void)locale; return strtof(s, end); }

SYSV size_t sysv_strftime(char* s, size_t max, const char* format, const struct tm* value) {
    return strftime(s, max, format, value);
}
SYSV size_t sysv_strftime_l(char* s, size_t max, const char* format, const struct tm* value,
                            void* locale) {
    (void)locale;
    return strftime(s, max, format, value);
}

SYSV size_t sysv_wcslen(const wchar_t* s) { return s ? wcslen(s) : 0; }
SYSV wchar_t* sysv_wmemchr(const wchar_t* s, wchar_t c, size_t n) {
    return (wchar_t*)wmemchr(s, c, n);
}

// ---------------------------------------------------------------------------------------------
// Wide characters and multibyte conversion, C locale (one byte, one character)
// ---------------------------------------------------------------------------------------------

SYSV int sysv_mbtowc(wchar_t* out, const char* s, size_t n) {
    if (!s) return 0;
    if (n == 0) return -1;
    if (out) *out = (wchar_t)(unsigned char)s[0];
    return s[0] ? 1 : 0;
}

SYSV size_t sysv_mbrtowc(wchar_t* out, const char* s, size_t n, void* state) {
    (void)state;
    if (!s) return 0;
    if (n == 0) return (size_t)-2;
    if (out) *out = (wchar_t)(unsigned char)s[0];
    return s[0] ? 1 : 0;
}

SYSV size_t sysv_wcrtomb(char* out, wchar_t wc, void* state) {
    (void)state;
    if (!out) return 1;
    if (wc > 0xFF) return (size_t)-1;
    out[0] = (char)(unsigned char)wc;
    return 1;
}

SYSV size_t sysv_mbsrtowcs(wchar_t* out, const char** src, size_t n, void* state) {
    (void)state;
    if (!src || !*src) return 0;
    const char* in = *src;
    size_t written = 0;
    while (n == 0 || written < n) {
        if (out) out[written] = (wchar_t)(unsigned char)in[written];
        if (!in[written]) { *src = NULL; return written; }
        written++;
    }
    *src = in + written;
    return written;
}

SYSV size_t sysv_mbsnrtowcs(wchar_t* out, const char** src, size_t max_bytes, size_t n, void* state) {
    (void)state;
    if (!src || !*src) return 0;
    const char* in = *src;
    size_t written = 0;
    while (written < max_bytes && (n == 0 || written < n)) {
        if (out) out[written] = (wchar_t)(unsigned char)in[written];
        if (!in[written]) { *src = NULL; return written; }
        written++;
    }
    if (written < max_bytes) *src = in + written;
    else *src = in + written;
    return written;
}

SYSV size_t sysv_wcsnrtombs(char* out, const wchar_t** src, size_t count, size_t max, void* state) {
    (void)state;
    if (!src || !*src) return 0;
    const wchar_t* in = *src;
    size_t written = 0;
    while (written < count && (max == 0 || written < max)) {
        if (out) out[written] = (char)(unsigned char)in[written];
        if (!in[written]) { *src = NULL; return written; }
        written++;
    }
    *src = in + written;
    return written;
}

SYSV size_t sysv_mbrlen(const char* s, size_t n, void* state) {
    (void)state;
    if (!s) return 0;
    if (n == 0) return (size_t)-2;
    return s[0] ? 1 : 0;
}

// glibc's ctype entry points return a pointer to the table pointer, and the table must be indexable
// from -128 (that is how the CDM uses it for signed char values).
static int32_t g_toupper_table[384];
static int32_t g_tolower_table[384];
static int32_t* g_toupper_pointer = g_toupper_table + 128;
static int32_t* g_tolower_pointer = g_tolower_table + 128;
static int g_ctype_ready;

static void build_ctype(void) {
    if (g_ctype_ready) return;
    for (int i = -128; i < 256; i++) {
        unsigned char value = (unsigned char)i;
        g_toupper_table[i + 128] = (i < 0 || value == 0) ? i : toupper(value);
        g_tolower_table[i + 128] = (i < 0 || value == 0) ? i : tolower(value);
    }
    g_ctype_ready = 1;
}

SYSV const int32_t** sysv_ctype_toupper_loc(void) {
    build_ctype();
    return (const int32_t**)&g_toupper_pointer;
}
SYSV const int32_t** sysv_ctype_tolower_loc(void) {
    build_ctype();
    return (const int32_t**)&g_tolower_pointer;
}
SYSV int sysv_ctype_get_mb_cur_max(void) { return 1; }  // C locale

SYSV int sysv_toupper(int c) { return toupper((unsigned char)c); }
SYSV int sysv_tolower(int c) { return tolower((unsigned char)c); }

// ---------------------------------------------------------------------------------------------
// stdio
// ---------------------------------------------------------------------------------------------

// printf family: see shim_printf.c — System V varargs cannot be expressed by this compiler.
extern "C" int sysv_fprintf();
extern "C" int sysv_printf();
extern "C" int sysv_snprintf();
extern "C" int sysv_sprintf();
extern "C" int sysv_sscanf();
extern "C" int sysv_vfprintf();
extern "C" int sysv_vsnprintf();
extern "C" int sysv_vsscanf();
extern "C" int sysv_vasprintf();
extern "C" int sysv_vsnprintf_chk();
extern "C" int sysv_snprintf_chk();
extern "C" int sysv_sprintf_chk();

SYSV size_t sysv_fwrite(const void* data, size_t size, size_t count, FILE* stream) {
    T2("fwrite", size, count);
    return fwrite(data, size, count, stream ? stream : stderr);
}
SYSV size_t sysv_fread(void* data, size_t size, size_t count, FILE* stream) {
    return fread(data, size, count, stream);
}
SYSV int sysv_fflush(FILE* stream) { return fflush(stream); }
SYSV int sysv_fclose(FILE* stream) { return stream ? fclose(stream) : 0; }
SYSV char* sysv_fgets(char* buffer, int size, FILE* stream) { return fgets(buffer, size, stream); }
SYSV int sysv_fputc(int c, FILE* stream) { return fputc(c, stream ? stream : stderr); }
SYSV int sysv_fgetc(FILE* stream) { return fgetc(stream); }
SYSV int sysv_getc(FILE* stream) { return getc(stream); }
SYSV int sysv_ungetc(int c, FILE* stream) { return ungetc(c, stream); }
SYSV int sysv_putc(int c, FILE* stream) { return putc(c, stream); }
SYSV int sysv_puts(const char* s) { return puts(s); }
SYSV int sysv_fileno(FILE* stream) { return _fileno(stream); }
SYSV void sysv_perror(const char* s) { perror(s); }
SYSV int sysv_fputwc(wchar_t c, FILE* stream) { return fputc((int)c, stream); }
SYSV int sysv_getwc(FILE* stream) { return fgetc(stream); }
SYSV int sysv_ungetwc(wchar_t c, FILE* stream) { return ungetc((int)c, stream); }
SYSV FILE* sysv_fopen(const char* path, const char* mode) { return fopen(path, mode); }
SYSV FILE* sysv_fopen64(const char* path, const char* mode) { return fopen(path, mode); }
SYSV int sysv_fseek(FILE* stream, long offset, int origin) { return fseek(stream, offset, origin); }
SYSV long sysv_ftell(FILE* stream) { return ftell(stream); }
SYSV void sysv_rewind(FILE* stream) { rewind(stream); }
SYSV int sysv_setvbuf(FILE* stream, char* buffer, int mode, size_t size) { return setvbuf(stream, buffer, mode, size); }
SYSV int sysv_feof(FILE* stream) { return feof(stream); }
SYSV int sysv_ferror(FILE* stream) { return ferror(stream); }
SYSV int sysv_remove(const char* path) { return remove(path); }

// _FORTIFY_SOURCE wrappers: same semantics, bounds known to be large enough to be ignored.
SYSV void* sysv_memcpy_chk(void* d, const void* s, size_t n, size_t dest_size) {
    (void)dest_size;
    return memcpy(d, s, n);
}
SYSV void* sysv_memmove_chk(void* d, const void* s, size_t n, size_t dest_size) {
    (void)dest_size;
    return memmove(d, s, n);
}
SYSV void* sysv_memset_chk(void* d, int c, size_t n, size_t dest_size) {
    (void)dest_size;
    return memset(d, c, n);
}
SYSV char* sysv_strcpy_chk(char* d, const char* s, size_t dest_size) {
    (void)dest_size;
    return strcpy(d, s);
}
SYSV char* sysv_strncat_chk(char* d, const char* s, size_t n, size_t dest_size) {
    (void)dest_size;
    return strncat(d, s, n);
}
SYSV char* sysv_strcat_chk(char* d, const char* s, size_t dest_size) {
    (void)dest_size;
    return strcat(d, s);
}

SYSV void sysv_stack_chk_fail(void) {
    fprintf(stderr,
            "[shim] __stack_chk_fail: the CDM's canary check failed (fs base=%016llx)\n",
            (unsigned long long)_readfsbase_u64());
    fflush(stderr);
    ExitProcess(177);
}

// ---------------------------------------------------------------------------------------------
// mmap family, VirtualAlloc-backed
// ---------------------------------------------------------------------------------------------

#define PROT_READ 1
#define PROT_WRITE 2
#define PROT_EXEC 4
#define MAP_SHARED 1
#define MAP_PRIVATE 2
#define MAP_FIXED 0x10
#define MAP_ANONYMOUS 0x20

static DWORD protection_from_posix(int protection) {
    const int read = protection & PROT_READ, write = protection & PROT_WRITE,
              execute = protection & PROT_EXEC;
    if (execute && write) return PAGE_EXECUTE_READWRITE;
    if (execute && read) return PAGE_EXECUTE_READ;
    if (execute) return PAGE_EXECUTE;
    if (write) return PAGE_READWRITE;
    if (read) return PAGE_READONLY;
    return PAGE_NOACCESS;
}

// Address-pool reservations, so a later `munmap` of one slice can never release the pool itself.
//
// The CDM's allocator reserves a huge PROT_NONE range and then hands out slices of it. Freeing a
// slice is `munmap(slice, page)`, and Windows' MEM_RELEASE frees the *whole* VirtualAlloc allocation
// â€” the 32 GiB reservation with everything still committed inside it. That is a silent, delayed
// crash: the next write anywhere in the pool hits unmapped memory (measured: 0xC0000005 at the pool
// base, an address 1.2 GiB below the image the allocator had just copied into).
#define MAX_RESERVATIONS 128
typedef struct { uint8_t* base; size_t size; } Reservation;
static Reservation g_reservations[MAX_RESERVATIONS];
static int g_reservation_count;
static SRWLOCK g_reservation_lock = SRWLOCK_INIT;

static void remember_reservation(void* base, size_t size) {
    if (!base || !size) return;
    AcquireSRWLockExclusive(&g_reservation_lock);
    for (int i = 0; i < g_reservation_count; i++) {
        if (g_reservations[i].base == (uint8_t*)base) {
            g_reservations[i].size = size;
            ReleaseSRWLockExclusive(&g_reservation_lock);
            return;
        }
    }
    if (g_reservation_count < MAX_RESERVATIONS) {
        g_reservations[g_reservation_count].base = (uint8_t*)base;
        g_reservations[g_reservation_count].size = size;
        g_reservation_count++;
    }
    ReleaseSRWLockExclusive(&g_reservation_lock);
}

/** True when [address] lies inside a pool we reserved, filling [base] and [size]. */
static int inside_reservation(const void* address, uint8_t** base, size_t* size) {
    int found = 0;
    AcquireSRWLockShared(&g_reservation_lock);
    for (int i = 0; i < g_reservation_count; i++) {
        uint8_t* start = g_reservations[i].base;
        if ((uint8_t*)address >= start && (uint8_t*)address < start + g_reservations[i].size) {
            if (base) *base = start;
            if (size) *size = g_reservations[i].size;
            found = 1;
            break;
        }
    }
    ReleaseSRWLockShared(&g_reservation_lock);
    return found;
}

SYSV void* sysv_mmap(void* address, size_t length, int protection, int flags, int fd, int64_t offset) {
    T2("mmap", length, (uint64_t)address);
    if (g_trace_verbose)
        fprintf(stderr, "[shim] mmap(address=%p, length=0x%llx, prot=%d, flags=0x%x)\n", address,
                (unsigned long long)length, protection, flags);
    (void)fd;
    (void)offset;
    if (!(flags & MAP_ANONYMOUS)) { g_errno = EINVAL; return (void*)-1; }
    if (!length) { g_errno = EINVAL; return (void*)-1; }
    length = (length + 0xFFF) & ~(size_t)0xFFF;

    // PROT_NONE means "reserve address space, commit nothing": that is exactly the address-pool
    // pattern the CDM's allocator uses (measured: a 32 GiB PROT_NONE reservation, then mprotect to
    // commit slices of it).
    // The address is a hint for a normal mapping and a requirement for MAP_FIXED. Honouring the
    // hint matters: the CDM's allocator maps inside the address pool it reserved, and a block that
    // lands elsewhere is later "freed" against that pool and fails its own bounds check
    // (measured: ERROR_INVALID_ADDRESS here, then a CHECK trap two calls later).
    void* hint = address;
    if (hint) {
        MEMORY_BASIC_INFORMATION region;
        if (VirtualQuery(hint, &region, sizeof(region)) &&
            (region.State == MEM_RESERVE || region.State == MEM_COMMIT) &&
            (uint8_t*)region.BaseAddress <= (uint8_t*)hint) {
            // The range is already ours: mapping again over it means committing it with the new
            // protection, exactly like mmap over a reserved slice.
            if (protection == 0) {
                VirtualFree(hint, length, MEM_DECOMMIT);
                DWORD previous = 0;
                VirtualProtect(hint, length, PAGE_NOACCESS, &previous);
                return hint;
            }
            void* committed = VirtualAlloc(hint, length, MEM_COMMIT,
                                           protection_from_posix(protection));
            if (committed) return committed;
            DWORD previous = 0;
            if (VirtualProtect(hint, length, protection_from_posix(protection), &previous))
                return hint;
            fprintf(stderr, "[shim] mmap over existing hint=%p length=0x%llx failed error=%lu\n",
                    hint, (unsigned long long)length, (unsigned long)GetLastError());
            fflush(stderr);
            g_errno = ENOMEM;
            return (void*)-1;
        }
    }
    if (protection == 0) {
        void* block = VirtualAlloc(hint, length, MEM_RESERVE, PAGE_NOACCESS);
        if (!block) {
            fprintf(stderr, "[shim] mmap reserve FAILED hint=%p length=0x%llx error=%lu\n", hint,
                    (unsigned long long)length, (unsigned long)GetLastError());
            fflush(stderr);
            g_errno = ENOMEM;
            return (void*)-1;
        }
        remember_reservation(block, length);
        return block;
    }
    void* block = VirtualAlloc(hint, length, MEM_RESERVE | MEM_COMMIT,
                               protection_from_posix(protection));
    if (!block) {
        fprintf(stderr, "[shim] mmap FAILED hint=%p length=0x%llx prot=%d error=%lu\n", hint,
                (unsigned long long)length, protection, (unsigned long)GetLastError());
        fflush(stderr);
        g_errno = ENOMEM;
        return (void*)-1;
    }
    return block;
}

SYSV int sysv_munmap(void* address, size_t length) {
    T2("munmap", address, length);
    if (!address) return 0;
    length = (length + 0xFFF) & ~(size_t)0xFFF;
    // A no-op, on purpose.
    //
    // Every attempt to honour it has broken the CDM's allocator: Windows' MEM_RELEASE frees the
    // whole VirtualAlloc allocation rather than the range asked for (measured: 0xC0000005 at the pool
    // base, twice), and decommitting the slice it names still leaves the CDM writing to pages it
    // believes are committed (measured: the same fault at 0x22900000000, and 0xC0000374 heap
    // corruption). The daemon is short-lived and hands back only a track's worth of buffers, so
    // keeping the memory reserved is bounded and the OS reclaims it at exit. `mmap` at an address
    // that is still reserved re-commits it through the "already ours" path above, so nothing that
    // asks for memory again can notice.
    (void)address;
    (void)length;
    return 0;
}

SYSV int sysv_mprotect(void* address, size_t length, int protection) {
    T2("mprotect", address, length);
    length = (length + 0xFFF) & ~(size_t)0xFFF;
    const DWORD wanted = protection_from_posix(protection);
    // Committing an already-reserved slice and changing the protection of an already-committed one
    // are the same call here.
    if (VirtualAlloc(address, length, MEM_COMMIT, wanted)) return 0;
    DWORD previous = 0;
    if (VirtualProtect(address, length, wanted, &previous)) return 0;
    MEMORY_BASIC_INFORMATION region;
    DWORD error = GetLastError();
    if (VirtualQuery(address, &region, sizeof(region))) {
        fprintf(stderr,
                "[shim] mprotect(%p, %llu, %d) failed: error=%lu state=0x%lx protect=0x%lx "
                "region base=%p size=%llu\n",
                address, (unsigned long long)length, protection, (unsigned long)error,
                (unsigned long)region.State, (unsigned long)region.Protect, region.BaseAddress,
                (unsigned long long)region.RegionSize);
    } else {
        fprintf(stderr, "[shim] mprotect(%p, %llu, %d) failed: error=%lu (not mapped)\n", address,
                (unsigned long long)length, protection, (unsigned long)error);
    }
    fflush(stderr);
    g_errno = EINVAL;
    return -1;
}

SYSV int sysv_madvise(void* address, size_t length, int advice) {
    (void)address;
    (void)length;
    (void)advice;
    return 0;
}

SYSV void* sysv_mremap(void* old_address, size_t old_size, size_t new_size, int flags, ...) {
    (void)flags;
    if (new_size <= old_size) return old_address;
    void* block = VirtualAlloc(NULL, new_size, MEM_RESERVE | MEM_COMMIT, PAGE_READWRITE);
    if (!block) { g_errno = ENOMEM; return (void*)-1; }
    memcpy(block, old_address, old_size);
    VirtualFree(old_address, 0, MEM_RELEASE);
    return block;
}

// ---------------------------------------------------------------------------------------------
// Randomness and time
// ---------------------------------------------------------------------------------------------

SYSV long sysv_getrandom(void* buffer, size_t length, unsigned int flags) {
    (void)flags;
    T2("getrandom", length, buffer);
    if (BCryptGenRandom(NULL, (PUCHAR)buffer, (ULONG)length, BCRYPT_USE_SYSTEM_PREFERRED_RNG) != 0) {
        g_errno = EIO;
        fprintf(stderr, "[shim] getrandom: BCryptGenRandom failed\n");
        return -1;
    }
    return (long)length;
}

struct shim_timespec { int64_t seconds; long nanoseconds; };
struct shim_timeval { int64_t seconds; int64_t microseconds; };

static void wall_clock(struct shim_timespec* out) {
    FILETIME file_time;
    GetSystemTimePreciseAsFileTime(&file_time);
    uint64_t ticks = ((uint64_t)file_time.dwHighDateTime << 32) | file_time.dwLowDateTime;
    ticks -= 116444736000000000ULL;  // 1601 -> 1970
    out->seconds = (int64_t)(ticks / 10000000ULL);
    out->nanoseconds = (long)((ticks % 10000000ULL) * 100);
}

static void monotonic_clock(struct shim_timespec* out) {
    LARGE_INTEGER counter, frequency;
    QueryPerformanceCounter(&counter);
    QueryPerformanceFrequency(&frequency);
    out->seconds = counter.QuadPart / frequency.QuadPart;
    out->nanoseconds = (long)(((counter.QuadPart % frequency.QuadPart) * 1000000000LL) /
                              frequency.QuadPart);
}

SYSV int sysv_clock_gettime(int clock_id, struct shim_timespec* out) {
    if (!out) return -1;
    // CLOCK_REALTIME 0, CLOCK_MONOTONIC 1, CLOCK_PROCESS_CPUTIME_ID 2, CLOCK_MONOTONIC_RAW 4.
    if (clock_id == 0 || clock_id == 5 || clock_id == 6 || clock_id == 7) wall_clock(out);
    else monotonic_clock(out);
    return 0;
}

SYSV int sysv_gettimeofday(struct shim_timeval* out, void* timezone) {
    (void)timezone;
    if (!out) return -1;
    struct shim_timespec now;
    wall_clock(&now);
    out->seconds = now.seconds;
    out->microseconds = now.nanoseconds / 1000;
    return 0;
}

SYSV int sysv_nanosleep(const struct shim_timespec* requested, struct shim_timespec* remaining) {
    (void)remaining;
    if (!requested) { g_errno = EINVAL; return -1; }
    uint64_t milliseconds = (uint64_t)requested->seconds * 1000 +
                            (uint64_t)(requested->nanoseconds / 1000000);
    Sleep((DWORD)milliseconds);
    return 0;
}

SYSV int sysv_usleep(unsigned long microseconds) {
    Sleep((DWORD)((microseconds + 999) / 1000));
    return 0;
}

SYSV int sysv_clock_nanosleep(int clock_id, int flags, const struct shim_timespec* requested,
                              struct shim_timespec* remaining) {
    (void)clock_id;
    (void)flags;
    return sysv_nanosleep(requested, remaining);
}

SYSV int64_t sysv_time(int64_t* out) {
    struct shim_timespec now;
    wall_clock(&now);
    if (out) *out = now.seconds;
    return now.seconds;
}

SYSV int64_t sysv_mktime(struct tm* value) { return (int64_t)mktime(value); }
SYSV int64_t sysv_timegm(struct tm* value) { return (int64_t)_mkgmtime(value); }

SYSV struct tm* sysv_gmtime_r(const int64_t* seconds, struct tm* out) {
    time_t value = (time_t)*seconds;
    return gmtime_s(out, &value) == 0 ? out : NULL;
}
SYSV struct tm* sysv_localtime_r(const int64_t* seconds, struct tm* out) {
    time_t value = (time_t)*seconds;
    return localtime_s(out, &value) == 0 ? out : NULL;
}

// ---------------------------------------------------------------------------------------------
// Mutexes, condition variables, read-write locks
//
// Linux sizes: pthread_mutex_t 40 bytes, pthread_cond_t 48, pthread_rwlock_t 56. The CDM allocates
// them; we only ever need the first 16, and a zero-filled one is PTHREAD_*_INITIALIZER, which is
// exactly the "not initialised yet" marker this implementation keys on.
// ---------------------------------------------------------------------------------------------

typedef struct { SRWLOCK lock; volatile LONG ready; } shim_mutex;
typedef struct { CONDITION_VARIABLE variable; volatile LONG ready; } shim_cond;
typedef struct { SRWLOCK lock; volatile LONG ready; volatile LONG writer; } shim_rwlock;

static int plausible_pointer(const void* pointer) { return (uintptr_t)pointer >= 0x1000; }

static void mutex_ensure(shim_mutex* mutex) {
    if (InterlockedCompareExchange(&mutex->ready, 1, 0) == 0) InitializeSRWLock(&mutex->lock);
}

SYSV int sysv_pthread_mutex_init(shim_mutex* mutex, const void* attributes) {
    (void)attributes;
    T1("pthread_mutex_init", mutex);
    if (!plausible_pointer(mutex)) return 0;
    memset(mutex, 0, sizeof(*mutex));
    InitializeSRWLock(&mutex->lock);
    mutex->ready = 1;
    return 0;
}

SYSV int sysv_pthread_mutex_destroy(shim_mutex* mutex) { (void)mutex; return 0; }

SYSV int sysv_pthread_mutex_lock(shim_mutex* mutex) {
    T1("pthread_mutex_lock", mutex);
    if (!plausible_pointer(mutex)) return 0;
    mutex_ensure(mutex);
    AcquireSRWLockExclusive(&mutex->lock);
    return 0;
}

SYSV int sysv_pthread_mutex_trylock(shim_mutex* mutex) {
    T1("pthread_mutex_trylock", mutex);
    if (!plausible_pointer(mutex)) return 0;
    mutex_ensure(mutex);
    return TryAcquireSRWLockExclusive(&mutex->lock) ? 0 : EBUSY;
}

SYSV int sysv_pthread_mutex_unlock(shim_mutex* mutex) {
    T1("pthread_mutex_unlock", mutex);
    if (!plausible_pointer(mutex)) return 0;
    mutex_ensure(mutex);
    ReleaseSRWLockExclusive(&mutex->lock);
    return 0;
}

static void cond_ensure(shim_cond* variable) {
    if (InterlockedCompareExchange(&variable->ready, 1, 0) == 0)
        InitializeConditionVariable(&variable->variable);
}

SYSV int sysv_pthread_cond_init(shim_cond* variable, const void* attributes) {
    (void)attributes;
    T1("pthread_cond_init", variable);
    if (!plausible_pointer(variable)) return 0;
    memset(variable, 0, sizeof(*variable));
    InitializeConditionVariable(&variable->variable);
    variable->ready = 1;
    return 0;
}

SYSV int sysv_pthread_cond_destroy(shim_cond* variable) { (void)variable; return 0; }

SYSV int sysv_pthread_cond_signal(shim_cond* variable) {
    T1("pthread_cond_signal", variable);
    if (!plausible_pointer(variable)) return 0;
    cond_ensure(variable);
    WakeConditionVariable(&variable->variable);
    return 0;
}

SYSV int sysv_pthread_cond_broadcast(shim_cond* variable) {
    T1("pthread_cond_broadcast", variable);
    if (!plausible_pointer(variable)) return 0;
    cond_ensure(variable);
    WakeAllConditionVariable(&variable->variable);
    return 0;
}

SYSV int sysv_pthread_cond_wait(shim_cond* variable, shim_mutex* mutex) {
    T2("pthread_cond_wait", variable, mutex);
    if (!plausible_pointer(variable) || !plausible_pointer(mutex)) return 0;
    cond_ensure(variable);
    mutex_ensure(mutex);
    SleepConditionVariableSRW(&variable->variable, &mutex->lock, INFINITE, 0);
    return 0;
}

SYSV int sysv_pthread_cond_timedwait(shim_cond* variable, shim_mutex* mutex,
                                     const struct shim_timespec* deadline) {
    T2("pthread_cond_timedwait", variable, mutex);
    if (!plausible_pointer(variable) || !plausible_pointer(mutex) || !deadline) return EINVAL;
    struct shim_timespec now;
    wall_clock(&now);
    int64_t milliseconds = (deadline->seconds - now.seconds) * 1000 +
                           (deadline->nanoseconds - now.nanoseconds) / 1000000;
    if (milliseconds < 0) milliseconds = 0;
    cond_ensure(variable);
    mutex_ensure(mutex);
    BOOL signalled = SleepConditionVariableSRW(&variable->variable, &mutex->lock,
                                               (DWORD)milliseconds, 0);
    return signalled ? 0 : ETIMEDOUT;
}

static void rwlock_ensure(shim_rwlock* lock) {
    if (InterlockedCompareExchange(&lock->ready, 1, 0) == 0) InitializeSRWLock(&lock->lock);
}

SYSV int sysv_pthread_rwlock_init(shim_rwlock* lock, const void* attributes) {
    (void)attributes;
    T1("pthread_rwlock_init", lock);
    if (!plausible_pointer(lock)) return 0;
    memset(lock, 0, sizeof(*lock));
    InitializeSRWLock(&lock->lock);
    lock->ready = 1;
    return 0;
}
SYSV int sysv_pthread_rwlock_destroy(shim_rwlock* lock) { (void)lock; return 0; }
SYSV int sysv_pthread_rwlock_rdlock(shim_rwlock* lock) {
    T1("pthread_rwlock_rdlock", lock);
    if (!plausible_pointer(lock)) return 0;
    rwlock_ensure(lock);
    AcquireSRWLockShared(&lock->lock);
    return 0;
}
SYSV int sysv_pthread_rwlock_wrlock(shim_rwlock* lock) {
    T1("pthread_rwlock_wrlock", lock);
    if (!plausible_pointer(lock)) return 0;
    rwlock_ensure(lock);
    InterlockedExchange(&lock->writer, 1);
    AcquireSRWLockExclusive(&lock->lock);
    return 0;
}
SYSV int sysv_pthread_rwlock_unlock(shim_rwlock* lock) {
    T1("pthread_rwlock_unlock", lock);
    if (!plausible_pointer(lock)) return 0;
    rwlock_ensure(lock);
    if (InterlockedExchange(&lock->writer, 0)) ReleaseSRWLockExclusive(&lock->lock);
    else ReleaseSRWLockShared(&lock->lock);
    return 0;
}

// ---------------------------------------------------------------------------------------------
// Threads
// ---------------------------------------------------------------------------------------------

typedef SYSV void* (*linux_thread_start)(void*);

typedef struct {
    linux_thread_start start;
    void* argument;
    void* result;
    volatile LONG detached;
} shim_thread;

static DWORD WINAPI thread_entry(LPVOID parameter) {
    shim_thread* thread = (shim_thread*)parameter;
    if (g_trace_verbose)
        fprintf(stderr, "[shim] CDM thread %lu started\n", (unsigned long)GetCurrentThreadId());
    // Every thread that runs CDM code needs its own Linux TCB, and `fs` pointing at it, before the
    // first prologue reads the canary.
    winelf_tls_install_for_current_thread();
    thread->result = thread->start(thread->argument);
    if (g_trace_verbose)
        fprintf(stderr, "[shim] CDM thread %lu finished\n", (unsigned long)GetCurrentThreadId());
    if (!thread->detached) {
        // Keep the descriptor alive for pthread_join; the OS handle already holds the result.
        return 0;
    }
    HeapFree(GetProcessHeap(), 0, thread);
    return 0;
}

SYSV int sysv_pthread_create(uint64_t* thread_out, const void* attributes,
                             linux_thread_start start, void* argument) {
    T2("pthread_create", start, argument);
    if (g_trace_verbose)
        fprintf(stderr, "[shim] pthread_create start=%p arg=%p\n", (void*)start, argument);
    (void)attributes;
    if (!thread_out || !start) return EINVAL;
    shim_thread* thread = (shim_thread*)HeapAlloc(GetProcessHeap(), HEAP_ZERO_MEMORY,
                                                  sizeof(*thread));
    if (!thread) return EAGAIN;
    thread->start = start;
    thread->argument = argument;
    DWORD id = 0;
    // 4 MiB of reserve: the CDM's slices use >16 KiB frames, and Windows does not grow a stack for
    // a write below its guard page the way Linux does.
    HANDLE handle = CreateThread(NULL, 4u * 1024u * 1024u, thread_entry, thread, 0, &id);
    if (!handle) {
        HeapFree(GetProcessHeap(), 0, thread);
        return EAGAIN;
    }
    *thread_out = (uint64_t)handle;
    return 0;
}

SYSV int sysv_pthread_join(uint64_t thread, void** result) {
    T1("pthread_join", thread);
    HANDLE handle = (HANDLE)(uintptr_t)thread;
    if (!handle) return ESRCH;
    WaitForSingleObject(handle, INFINITE);
    if (result) *result = NULL;
    CloseHandle(handle);
    return 0;
}

SYSV int sysv_pthread_detach(uint64_t thread) { CloseHandle((HANDLE)(uintptr_t)thread); return 0; }
SYSV uint64_t sysv_pthread_self(void) { return (uint64_t)GetCurrentThreadId(); }

SYSV int sysv_pthread_attr_init(void* attributes) { if (attributes) memset(attributes, 0, 56); return 0; }
SYSV int sysv_pthread_attr_destroy(void* attributes) { (void)attributes; return 0; }
SYSV int sysv_pthread_attr_setdetachstate(void* attributes, int state) { (void)attributes; (void)state; return 0; }
SYSV int sysv_pthread_attr_setstacksize(void* attributes, size_t size) { (void)attributes; (void)size; return 0; }
SYSV int sysv_pthread_attr_getstacksize(const void* attributes, size_t* size) { (void)attributes; if (size) *size = 1u << 20; return 0; }
SYSV int sysv_pthread_mutexattr_init(void* attributes) { (void)attributes; return 0; }
SYSV int sysv_pthread_mutexattr_destroy(void* attributes) { (void)attributes; return 0; }
SYSV int sysv_pthread_mutexattr_settype(void* attributes, int type) { (void)attributes; (void)type; return 0; }
SYSV int sysv_pthread_condattr_init(void* attributes) { (void)attributes; return 0; }
SYSV int sysv_pthread_condattr_destroy(void* attributes) { (void)attributes; return 0; }
SYSV int sysv_pthread_condattr_setclock(void* attributes, int clock_id) { (void)attributes; (void)clock_id; return 0; }
SYSV int sysv_pthread_setname_np(uint64_t thread, const char* name) {
    (void)thread;
    (void)name;
    return 0;
}
SYSV int sysv_pthread_getname_np(uint64_t thread, char* name, size_t size) {
    (void)thread;
    if (name && size) name[0] = 0;
    return 0;
}
SYSV int sysv_pthread_getschedparam(uint64_t thread, int* policy, void* parameters) {
    (void)thread;
    (void)parameters;
    if (policy) *policy = 0;
    return 0;
}
SYSV int sysv_pthread_setschedparam(uint64_t thread, int policy, const void* parameters) {
    (void)thread;
    (void)policy;
    (void)parameters;
    return 0;
}
SYSV int sysv_pthread_getaffinity_np(uint64_t thread, size_t size, void* set) {
    (void)thread;
    if (set && size) memset(set, 0xFF, size);
    return 0;
}
SYSV int sysv_pthread_sigmask(int how, const void* set, void* old) {
    (void)how;
    (void)set;
    (void)old;
    return 0;
}

typedef SYSV void (*linux_once_function)(void);
SYSV int sysv_pthread_once(volatile LONG* once, linux_once_function initializer) {
    T1("pthread_once", once);
    if (!once || !initializer) return EINVAL;
    if (*once == 2) return 0;
    while (InterlockedCompareExchange(once, 1, 0) == 1) SwitchToThread();
    if (*once != 2) {
        initializer();
        *once = 2;
    }
    return 0;
}

SYSV int sysv_pthread_key_create(DWORD* key, void* destructor) {
    (void)destructor;
    if (!key) return EINVAL;
    *key = TlsAlloc();
    return (*key == TLS_OUT_OF_INDEXES) ? EAGAIN : 0;
}
SYSV int sysv_pthread_key_delete(DWORD key) { return TlsFree(key) ? 0 : EINVAL; }
SYSV void* sysv_pthread_getspecific(DWORD key) { return TlsGetValue(key); }
SYSV int sysv_pthread_setspecific(DWORD key, const void* value) {
    return TlsSetValue(key, (LPVOID)value) ? 0 : EINVAL;
}

// Semaphores. Linux sem_t is 32 bytes; a handle and a flag are enough for the CDM's use.
typedef struct { HANDLE handle; volatile LONG ready; } shim_semaphore;

SYSV int sysv_sem_init(shim_semaphore* semaphore, int shared, unsigned value) {
    (void)shared;
    if (!semaphore) return -1;
    semaphore->handle = CreateSemaphoreA(NULL, (LONG)value, LONG_MAX, NULL);
    semaphore->ready = 1;
    return semaphore->handle ? 0 : -1;
}
SYSV int sysv_sem_destroy(shim_semaphore* semaphore) {
    if (semaphore && semaphore->handle) CloseHandle(semaphore->handle);
    if (semaphore) semaphore->handle = NULL;
    return 0;
}
SYSV int sysv_sem_post(shim_semaphore* semaphore) {
    return (semaphore && ReleaseSemaphore(semaphore->handle, 1, NULL)) ? 0 : -1;
}
SYSV int sysv_sem_wait(shim_semaphore* semaphore) {
    return (semaphore && WaitForSingleObject(semaphore->handle, INFINITE) == WAIT_OBJECT_0) ? 0 : -1;
}
SYSV int sysv_sem_trywait(shim_semaphore* semaphore) {
    if (semaphore && WaitForSingleObject(semaphore->handle, 0) == WAIT_OBJECT_0) return 0;
    g_errno = EAGAIN;
    return -1;
}

// ---------------------------------------------------------------------------------------------
// Process, scheduling, limits
// ---------------------------------------------------------------------------------------------

SYSV int sysv_getpid(void) { return (int)GetCurrentProcessId(); }
SYSV int sysv_gettid(void) { return (int)GetCurrentThreadId(); }
SYSV unsigned sysv_geteuid(void) { return 1000; }
SYSV unsigned sysv_getuid(void) { return 1000; }
SYSV unsigned sysv_getegid(void) { return 1000; }
SYSV unsigned sysv_getgid(void) { return 1000; }
SYSV int sysv_sched_yield(void) { SwitchToThread(); return 0; }
SYSV int sysv_sched_getaffinity(int pid, size_t size, void* set) {
    (void)pid;
    if (set && size) memset(set, 0xFF, size);
    return (int)size;
}
SYSV int sysv_sched_setaffinity(int pid, size_t size, const void* set) {
    (void)pid;
    (void)size;
    (void)set;
    return 0;
}
SYSV int sysv_sched_setscheduler(int pid, int policy, const void* parameters) {
    (void)pid;
    (void)policy;
    (void)parameters;
    return 0;
}
SYSV int sysv_setpriority(int which, int who, int priority) {
    (void)which;
    (void)who;
    (void)priority;
    return 0;
}
SYSV int sysv_prctl(int option, ...) { return 0; }

// Some of the CDM's Linux dependencies reach the kernel through `syscall()` instead of a wrapper.
// Only the numbers it actually uses on this path are answered; everything else fails cleanly.
enum {
    LINUX_SYS_read = 0, LINUX_SYS_write = 1, LINUX_SYS_mmap = 9, LINUX_SYS_mprotect = 10,
    LINUX_SYS_munmap = 11, LINUX_SYS_getpid = 39, LINUX_SYS_getuid = 102, LINUX_SYS_geteuid = 107,
    LINUX_SYS_prctl = 157, LINUX_SYS_gettid = 186, LINUX_SYS_futex = 202,
    LINUX_SYS_sched_getaffinity = 204, LINUX_SYS_clock_gettime = 228, LINUX_SYS_getrandom = 318,
    LINUX_SYS_getentropy = 318
};

// The CDM's locks reach the kernel through `syscall(SYS_futex, …)` directly. Answering ENOSYS is not
// harmless: it turns a real wait into "someone woke you", and the code that owns the lock then runs
// on a structure nobody finished initialising (measured: a 0xaa poison pointer used as an object two
// calls later). Windows' address wait is the same primitive.
//
// It is resolved at run time: the import library for it is not in every MinGW toolchain, while the
// functions themselves ship with Windows 8 and later.
typedef BOOL(WINAPI* WaitOnAddressFn)(volatile void*, void*, SIZE_T, DWORD);
typedef void(WINAPI* WakeByAddressFn)(void*);

static WaitOnAddressFn g_wait_on_address;
static WakeByAddressFn g_wake_single;
static WakeByAddressFn g_wake_all;
static int g_futex_loaded;

static void load_futex_functions(void) {
    if (g_futex_loaded) return;
    g_futex_loaded = 1;
    HMODULE kernel = GetModuleHandleA("kernel32.dll");
    HMODULE synch = LoadLibraryA("api-ms-win-core-synch-l1-2-0.dll");
    HMODULE modules[2] = {kernel, synch};
    for (int i = 0; i < 2; i++) {
        if (!modules[i]) continue;
        if (!g_wait_on_address)
            g_wait_on_address = (WaitOnAddressFn)GetProcAddress(modules[i], "WaitOnAddress");
        if (!g_wake_single)
            g_wake_single = (WakeByAddressFn)GetProcAddress(modules[i], "WakeByAddressSingle");
        if (!g_wake_all)
            g_wake_all = (WakeByAddressFn)GetProcAddress(modules[i], "WakeByAddressAll");
    }
    if (synch) FreeLibrary(synch);
}

static long futex_call(uint64_t address, int operation, uint32_t value, uint64_t timeout_address) {
    load_futex_functions();
    const int command = operation & 0x7f;
    if (command == 0) {  // FUTEX_WAIT
        DWORD milliseconds = INFINITE;
        if (timeout_address) {
            const struct shim_timespec* timeout = (const struct shim_timespec*)(uintptr_t)timeout_address;
            uint64_t ms = (uint64_t)timeout->seconds * 1000 + (uint64_t)(timeout->nanoseconds / 1000000);
            if (ms > 0x7fffffff) ms = 0x7fffffff;
            milliseconds = (DWORD)ms;
        }
        if (!g_wait_on_address) {  // no primitive: report "woken", the caller re-checks its state
            Sleep(0);
            return 0;
        }
        if (g_wait_on_address((volatile void*)(uintptr_t)address, &value, 4, milliseconds)) return 0;
        if (GetLastError() == ERROR_TIMEOUT) { g_errno = ETIMEDOUT; return -1; }
        g_errno = EAGAIN;  // the value changed before we slept
        return -1;
    }
    if (command == 1) {  // FUTEX_WAKE
        const long woke = (long)(value ? value : 1);
        if (g_wake_all && g_wake_single) {
            if (value <= 1) g_wake_single((void*)(uintptr_t)address);
            else g_wake_all((void*)(uintptr_t)address);
        }
        return woke;
    }
    g_errno = ENOSYS;
    return -1;
}

SYSV long sysv_futex(uint32_t* address, int operation, uint32_t value,
                     const struct shim_timespec* timeout, uint32_t* address2, uint32_t value3) {
    T2("futex", address, operation);
    (void)address2;
    (void)value3;
    return futex_call((uint64_t)(uintptr_t)address, operation, value,
                      (uint64_t)(uintptr_t)timeout);
}

// Fixed parameters rather than `...`: a System V variadic definition cannot be read by this
// compiler (see shim_printf.c). Arguments seven and beyond arrive on the stack in both conventions,
// so declaring them by position is exact.
SYSV long sysv_syscall(long number, uint64_t a1, uint64_t a2, uint64_t a3, uint64_t a4, uint64_t a5,
                       uint64_t a6) {
    long result = -1;
    switch (number) {
        case LINUX_SYS_getrandom: {
            void* buffer = (void*)a1;
            size_t length = (size_t)a2;
            unsigned flags = (unsigned)a3;
            T2("syscall(getrandom)", length, buffer);
            result = sysv_getrandom(buffer, length, flags);
            break;
        }
        case LINUX_SYS_gettid:
            result = (long)GetCurrentThreadId();
            break;
        case LINUX_SYS_getpid:
            result = (long)GetCurrentProcessId();
            break;
        case LINUX_SYS_getuid:
        case LINUX_SYS_geteuid:
            result = 1000;
            break;
        case LINUX_SYS_clock_gettime: {
            struct shim_timespec* out = (struct shim_timespec*)a2;
            result = sysv_clock_gettime((int)a1, out);
            break;
        }
        case LINUX_SYS_sched_getaffinity: {
            if ((void*)a3) memset((void*)a3, 0xFF, (size_t)a2);
            result = (long)a2;
            break;
        }
        case LINUX_SYS_futex:
            T2("syscall(futex)", a1, a2);
            result = futex_call(a1, (int)a2, (uint32_t)a3, a4);
            break;
        default:
            T1("syscall(unhandled)", number);
            g_errno = ENOSYS;
            break;
    }
    (void)a5;
    (void)a6;
    return result;
}
SYSV int sysv_getrlimit64(int resource, void* limit) {
    (void)resource;
    if (limit) {
        uint64_t* values = (uint64_t*)limit;
        values[0] = values[1] = (uint64_t)-1;
    }
    return 0;
}
SYSV int sysv_setrlimit64(int resource, const void* limit) { (void)resource; (void)limit; return 0; }
SYSV int sysv_sigfillset(void* set) {
    if (set) memset(set, 0xFF, 128);  // sizeof(sigset_t) on Linux x86-64
    return 0;
}
SYSV void* sysv_memset_sigset(void* set, int value, size_t size) { return memset(set, value, size); }
SYSV int sysv_sigaction(int signal, const void* action, void* old) {
    (void)signal;
    (void)action;
    (void)old;
    return 0;
}
SYSV int sysv_sigprocmask(int how, const void* set, void* old) {
    (void)how;
    (void)set;
    (void)old;
    return 0;
}
SYSV int sysv_sigemptyset(void* set) { if (set) memset(set, 0, 128); return 0; }
SYSV int sysv_sigaddset(void* set, int signal) {
    (void)set;
    (void)signal;
    return 0;
}
SYSV unsigned long sysv_getauxval(unsigned long type) { (void)type; return 0; }
SYSV int sysv_getpagesize(void) { return 4096; }
SYSV long sysv_sysconf(int name) {
    switch (name) {
        case 30: return 4096;                        // _SC_PAGESIZE
        case 84: case 85: return 8;                  // _SC_NPROCESSORS_ONLN, _SC_NPROCESSORS_CONF
        case 85 + 1: return 1 << 20;                 // _SC_PHYS_PAGES, approximate
        default: return -1;
    }
}
SYSV int sysv_uname(void* buffer) {
    if (!buffer) return -1;
    memset(buffer, 0, 6 * 65);
    memcpy((char*)buffer + 0 * 65, "Linux", 5);
    memcpy((char*)buffer + 1 * 65, "kittytune", 9);
    memcpy((char*)buffer + 2 * 65, "6.8.0", 5);
    memcpy((char*)buffer + 3 * 65, "#1 SMP", 6);
    memcpy((char*)buffer + 4 * 65, "x86_64", 6);
    return 0;
}
SYSV int sysv___sched_cpucount(size_t size, const void* set) {
    int count = 0;
    const uint8_t* bytes = (const uint8_t*)set;
    if (!bytes) return 0;
    for (size_t i = 0; i < size; i++) {
        uint8_t value = bytes[i];
        while (value) { count += value & 1; value >>= 1; }
    }
    return count;
}
SYSV char* sysv_getenv(const char* name) {
    T1("getenv", name);
    return name ? getenv(name) : NULL;
}
SYSV int sysv_setenv(const char* name, const char* value, int overwrite) {
    (void)overwrite;
    if (!name) return -1;
    return _putenv_s(name, value ? value : "");
}
SYSV void sysv_exit(int code) { ExitProcess((UINT)code); }
SYSV void sysv__exit(int code) { ExitProcess((UINT)code); }
SYSV void sysv_abort(void) {
    fprintf(stderr, "[shim] the CDM called abort()\n");
    shim_dump_trace();
    fflush(stderr);
    ExitProcess(134);
}
SYSV int sysv_atexit(void (*function)(void)) { (void)function; return 0; }

// ---------------------------------------------------------------------------------------------
// C++ runtime bits: the CDM statically links libstdc++, but imports these from libc
// ---------------------------------------------------------------------------------------------

SYSV int sysv_cxa_atexit(void (*function)(void*), void* argument, void* dso) {
    (void)function;
    (void)argument;
    (void)dso;
    return 0;
}
SYSV void sysv_cxa_finalize(void* dso) { (void)dso; }
SYSV void sysv_gmon_start(void) {}
SYSV void sysv_itm_register(void* pointer) { (void)pointer; }
SYSV void sysv_itm_deregister(void* pointer) { (void)pointer; }
SYSV void sysv_gcov_dump(void) {}
SYSV void sysv_gcov_flush(void) {}
SYSV int sysv_register_atfork(void* prepare, void* parent, void* child, void* dso) {
    (void)prepare;
    (void)parent;
    (void)child;
    (void)dso;
    return 0;
}
SYSV void sysv_unwind_raise_exception(void* exception) {
    (void)exception;
    fprintf(stderr, "[shim] the CDM threw a C++ exception (_Unwind_RaiseException)\n");
    fflush(stderr);
    ExitProcess(199);
}
SYSV void sysv_unwind_resume(void* exception) {
    (void)exception;
    fprintf(stderr, "[shim] the CDM resumed an exception (_Unwind_Resume)\n");
    fflush(stderr);
    ExitProcess(200);
}
SYSV void sysv_unwind_set_gr(void* context, int index, uint64_t value) {
    (void)context;
    (void)index;
    (void)value;
}
SYSV uint64_t sysv_unwind_get_ip(void* context) { (void)context; return 0; }
SYSV void sysv_unwind_set_ip(void* context, uint64_t ip) { (void)context; (void)ip; }
SYSV uint64_t sysv_unwind_get_language_specific_data(void* context) { (void)context; return 0; }
SYSV uint64_t sysv_unwind_get_region_start(void* context) { (void)context; return 0; }
SYSV void sysv_unwind_delete_exception(void* exception) { (void)exception; }

// ---------------------------------------------------------------------------------------------
// Locales, message catalogues, dlsym, files
// ---------------------------------------------------------------------------------------------

static int g_dummy_locale;

SYSV void* sysv_newlocale(int mask, const char* name, void* base) {
    (void)mask;
    (void)name;
    (void)base;
    return &g_dummy_locale;  // non-NULL: the C locale the rest of the shim implements
}
SYSV void* sysv_duplocale(void* locale) { (void)locale; return &g_dummy_locale; }
SYSV int sysv_freelocale(void* locale) { (void)locale; return 0; }
SYSV void* sysv_uselocale(void* locale) {
    (void)locale;
    return NULL;  // NULL is LC_GLOBAL_LOCALE: "no change", which is what we want
}
SYSV void* sysv_localeconv(void) { return NULL; }

SYSV void* sysv_catopen(const char* name, int type) {
    (void)name;
    (void)type;
    return (void*)-1;
}
SYSV char* sysv_catgets(void* catalogue, int set, int message, const char* fallback) {
    (void)catalogue;
    (void)set;
    (void)message;
    return (char*)fallback;
}
SYSV int sysv_catclose(void* catalogue) { (void)catalogue; return 0; }

SYSV void* sysv_dlsym(void* library, const char* name) {
    (void)library;
    T1("dlsym", name);
    return name ? winelf_lookup_export(name) : NULL;
}
SYSV void* sysv_dlopen(const char* path, int mode) { (void)path; (void)mode; return NULL; }
SYSV int sysv_dlclose(void* library) { (void)library; return 0; }
SYSV char* sysv_dlerror(void) { return (char*)""; }

// ---------------------------------------------------------------------------------------------
// File descriptors.
//
// The CDM opens exactly one device on this path — `/dev/urandom`, which it checks with an assert
// that traps the process when the open fails — and writes its own log lines to 1 and 2. So the
// descriptor table is a small set of synthetic entries: randomness, /dev/null, and the standard
// streams. Anything else is reported as missing, which is what a Linux host without the file would
// say too.
// ---------------------------------------------------------------------------------------------

enum { FD_FREE = 0, FD_URANDOM = 1, FD_NULL = 2, FD_STDIN = 3, FD_STDOUT = 4, FD_STDERR = 5 };

#define FD_TABLE_SIZE 64
static volatile LONG g_fd_kind[FD_TABLE_SIZE];

static int fd_allocate(int kind) {
    for (int fd = 3; fd < FD_TABLE_SIZE; fd++) {
        if (InterlockedCompareExchange(&g_fd_kind[fd], kind, FD_FREE) == FD_FREE) return fd;
    }
    return -1;
}

static int fd_kind(int fd) {
    if (fd == 0) return FD_STDIN;
    if (fd == 1) return FD_STDOUT;
    if (fd == 2) return FD_STDERR;
    if (fd < 0 || fd >= FD_TABLE_SIZE) return FD_FREE;
    return g_fd_kind[fd];
}

static unsigned random_bytes(void* buffer, size_t length) {
    if (BCryptGenRandom(NULL, (PUCHAR)buffer, (ULONG)length, BCRYPT_USE_SYSTEM_PREFERRED_RNG) != 0)
        return 0;
    return (unsigned)length;
}

SYSV int sysv_fxstat64(int version, int fd, void* buffer) {
    (void)version;
    (void)fd;
    (void)buffer;
    g_errno = ENOENT;
    return -1;
}
SYSV int sysv_xstat64(int version, const char* path, void* buffer) {
    (void)version;
    (void)path;
    (void)buffer;
    g_errno = ENOENT;
    return -1;
}
SYSV int sysv_fcntl(int fd, int command, ...) {
    (void)fd;
    (void)command;
    return 0;
}

SYSV int sysv_open64(const char* path, int flags, ...) {
    T1("open64", path);
    if (!path) { g_errno = EFAULT; return -1; }
    if (strcmp(path, "/dev/urandom") == 0 || strcmp(path, "/dev/random") == 0) {
        int fd = fd_allocate(FD_URANDOM);
        if (fd < 0) { g_errno = EMFILE; return -1; }
        return fd;
    }
    if (strcmp(path, "/dev/null") == 0) {
        int fd = fd_allocate(FD_NULL);
        if (fd < 0) { g_errno = EMFILE; return -1; }
        return fd;
    }
    g_errno = ENOENT;
    return -1;
}
SYSV int sysv_open(const char* path, int flags, ...) { return sysv_open64(path, flags); }
SYSV int sysv_creat64(const char* path, int mode) {
    (void)path;
    (void)mode;
    g_errno = EACCES;
    return -1;
}
SYSV int sysv_close(int fd) {
    if (fd >= 3 && fd < FD_TABLE_SIZE) InterlockedExchange(&g_fd_kind[fd], FD_FREE);
    return 0;
}
SYSV long sysv_read(int fd, void* buffer, size_t count) {
    switch (fd_kind(fd)) {
        case FD_URANDOM:
            return (long)random_bytes(buffer, count);
        case FD_NULL:
            return 0;
        case FD_STDIN: {
            size_t got = fread(buffer, 1, count, stdin);
            return (long)got;
        }
        default:
            g_errno = EBADF;
            return -1;
    }
}
SYSV long sysv_write(int fd, const void* buffer, size_t count) {
    switch (fd_kind(fd)) {
        case FD_STDOUT:
            fwrite(buffer, 1, count, stdout);
            fflush(stdout);
            return (long)count;
        case FD_STDERR:
            fwrite(buffer, 1, count, stderr);
            fflush(stderr);
            return (long)count;
        case FD_NULL:
            return (long)count;
        default:
            g_errno = EBADF;
            return -1;
    }
}
SYSV long sysv_pread64(int fd, void* buffer, size_t count, int64_t offset) {
    (void)offset;
    return sysv_read(fd, buffer, count);
}
SYSV long sysv_pwrite64(int fd, const void* buffer, size_t count, int64_t offset) {
    (void)offset;
    return sysv_write(fd, buffer, count);
}
SYSV int64_t sysv_lseek(int fd, int64_t offset, int origin) {
    (void)offset;
    (void)origin;
    return fd_kind(fd) == FD_FREE ? -1 : 0;
}
SYSV int sysv_access(const char* path, int mode) {
    (void)path;
    (void)mode;
    g_errno = ENOENT;
    return -1;
}

// ---------------------------------------------------------------------------------------------
// BoringSSL allocator hooks
// ---------------------------------------------------------------------------------------------

SYSV void* sysv_openssl_memory_alloc(size_t size) { return malloc(size); }
SYSV void sysv_openssl_memory_free(void* pointer) { free(pointer); }
SYSV size_t sysv_openssl_memory_get_size(void* pointer) {
    return pointer ? HeapSize(GetProcessHeap(), 0, pointer) : 0;
}

// ---------------------------------------------------------------------------------------------
// qsort with a System V comparator â€” the CDM's comparison function is Linux code and cannot be
// called with the Windows convention, so the sort has to live here as well.
// ---------------------------------------------------------------------------------------------

typedef SYSV int (*linux_comparator)(const void*, const void*);

static void swap_bytes(uint8_t* a, uint8_t* b, size_t size) {
    while (size--) {
        uint8_t temporary = *a;
        *a++ = *b;
        *b++ = temporary;
    }
}

static void sift_down(uint8_t* base, size_t start, size_t count, size_t size,
                      linux_comparator compare) {
    size_t root = start;
    for (;;) {
        size_t child = root * 2 + 1;
        if (child >= count) break;
        if (child + 1 < count &&
            compare(base + child * size, base + (child + 1) * size) < 0)
            child++;
        if (compare(base + root * size, base + child * size) >= 0) break;
        swap_bytes(base + root * size, base + child * size, size);
        root = child;
    }
}

SYSV void sysv_qsort(void* base, size_t count, size_t size, linux_comparator compare) {
    T2("qsort", count, size);
    if (!base || !compare || size == 0 || count < 2) return;
    uint8_t* bytes = (uint8_t*)base;
    for (size_t start = (count - 2) / 2 + 1; start-- > 0;) sift_down(bytes, start, count, size, compare);
    for (size_t end = count - 1; end > 0; end--) {
        swap_bytes(bytes, bytes + end * size, size);
        sift_down(bytes, 0, end, size, compare);
    }
}

// ---------------------------------------------------------------------------------------------
// Symbols defined in sysv_support.S
// ---------------------------------------------------------------------------------------------

extern "C" int sysv_setjmp(void* buffer);
extern "C" void sysv_longjmp(void* buffer, int value);
extern "C" uint64_t shim_udivti3(void);
extern "C" long double sysv_strtold_l(const char*, char**, void*);

// ---------------------------------------------------------------------------------------------
// TLS for the general-dynamic model
// ---------------------------------------------------------------------------------------------

typedef struct { uint64_t module; uint64_t offset; } shim_tls_index;

SYSV void* sysv_tls_get_addr(shim_tls_index* index) {
    if (!index) return NULL;
    uint64_t base = _readfsbase_u64();
    if (!base) base = (uint64_t)winelf_tcb_for_current_thread();
    if (!base) return NULL;
    uint64_t memsz = winelf_tls_memsz();
    return (void*)(base - memsz + index->offset);
}

// ---------------------------------------------------------------------------------------------
// The table: every symbol the CDM imports.
// ---------------------------------------------------------------------------------------------

typedef struct { const char* name; void* address; } ShimEntry;

#define S(name, function) { name, (void*)(function) }

static const ShimEntry g_shim[] = {
    // errno and libc globals
    S("__errno_location", __errno_location),
    S("__libc_stack_end", &shim_stack_end),
    S("__environ", &shim_environ),

    // memory
    S("malloc", shim_malloc),
    S("free", shim_free),
    S("calloc", shim_calloc),
    S("realloc", shim_realloc),
    S("posix_memalign", shim_posix_memalign),
    S("aligned_alloc", shim_aligned_alloc),
    S("memalign", shim_memalign),
    S("malloc_usable_size", shim_malloc_usable_size),

    // mem*/str*
    S("memcpy", sysv_memcpy), S("memmove", sysv_memmove), S("memset", sysv_memset),
    S("memcmp", sysv_memcmp), S("memchr", sysv_memchr), S("bcmp", sysv_memcmp),
    S("strlen", sysv_strlen), S("strcmp", sysv_strcmp), S("strncmp", sysv_strncmp),
    S("strchr", sysv_strchr), S("strrchr", sysv_strrchr), S("strcpy", sysv_strcpy),
    S("strncpy", sysv_strncpy), S("strcat", sysv_strcat), S("strncat", sysv_strncat),
    S("strstr", sysv_strstr), S("strdup", sysv_strdup), S("strndup", sysv_strndup),
    S("strerror", sysv_strerror), S("strerror_r", sysv_strerror_r),
    S("strtol", sysv_strtol), S("strtoul", sysv_strtoul),
    S("strtoll", sysv_strtoll), S("strtoull", sysv_strtoull),
    S("strtod", sysv_strtod), S("strtof", sysv_strtof),
    S("strtol_l", sysv_strtol_l), S("strtoul_l", sysv_strtoul_l),
    S("strtoll_l", sysv_strtoll_l), S("strtoull_l", sysv_strtoull_l),
    S("strtod_l", sysv_strtod_l), S("strtof_l", sysv_strtof_l),
    S("strtold_l", sysv_strtold_l),
    S("strftime", sysv_strftime), S("strftime_l", sysv_strftime_l),
    S("wcslen", sysv_wcslen), S("wmemchr", sysv_wmemchr), S("wcsnrtombs", sysv_wcsnrtombs),

    // ctype
    S("__ctype_toupper_loc", sysv_ctype_toupper_loc),
    S("__ctype_tolower_loc", sysv_ctype_tolower_loc),
    S("__ctype_get_mb_cur_max", sysv_ctype_get_mb_cur_max),
    S("__mbrlen", sysv_mbrlen), S("mbrlen", sysv_mbrlen),
    S("mbtowc", sysv_mbtowc), S("mbrtowc", sysv_mbrtowc),
    S("mbsrtowcs", sysv_mbsrtowcs), S("mbsnrtowcs", sysv_mbsnrtowcs),
    S("wcrtomb", sysv_wcrtomb),
    S("toupper", sysv_toupper), S("tolower", sysv_tolower),

    // fortified variants
    S("__memcpy_chk", sysv_memcpy_chk), S("__memmove_chk", sysv_memmove_chk),
    S("__memset_chk", sysv_memset_chk), S("__strcpy_chk", sysv_strcpy_chk),
    S("__strcat_chk", sysv_strcat_chk), S("__strncat_chk", sysv_strncat_chk),
    S("__vsnprintf_chk", sysv_vsnprintf_chk), S("__snprintf_chk", sysv_snprintf_chk),
    S("__sprintf_chk", sysv_sprintf_chk), S("__stack_chk_fail", sysv_stack_chk_fail),

    // mmap family
    S("mmap", sysv_mmap), S("mmap64", sysv_mmap), S("munmap", sysv_munmap),
    S("mprotect", sysv_mprotect), S("madvise", sysv_madvise), S("mremap", sysv_mremap),

    // randomness and time
    S("getrandom", sysv_getrandom), S("rand", sysv_rand), S("srand", sysv_srand),
    S("clock_gettime", sysv_clock_gettime), S("gettimeofday", sysv_gettimeofday),
    S("nanosleep", sysv_nanosleep), S("clock_nanosleep", sysv_clock_nanosleep),
    S("usleep", sysv_usleep), S("time", sysv_time),
    S("mktime", sysv_mktime), S("timegm", sysv_timegm),
    S("gmtime_r", sysv_gmtime_r), S("localtime_r", sysv_localtime_r),

    // pthreads
    S("pthread_mutex_init", sysv_pthread_mutex_init),
    S("pthread_mutex_destroy", sysv_pthread_mutex_destroy),
    S("pthread_mutex_lock", sysv_pthread_mutex_lock),
    S("pthread_mutex_trylock", sysv_pthread_mutex_trylock),
    S("pthread_mutex_unlock", sysv_pthread_mutex_unlock),
    S("pthread_mutexattr_init", sysv_pthread_mutexattr_init),
    S("pthread_mutexattr_destroy", sysv_pthread_mutexattr_destroy),
    S("pthread_mutexattr_settype", sysv_pthread_mutexattr_settype),
    S("pthread_cond_init", sysv_pthread_cond_init),
    S("pthread_cond_destroy", sysv_pthread_cond_destroy),
    S("pthread_cond_signal", sysv_pthread_cond_signal),
    S("pthread_cond_broadcast", sysv_pthread_cond_broadcast),
    S("pthread_cond_wait", sysv_pthread_cond_wait),
    S("pthread_cond_timedwait", sysv_pthread_cond_timedwait),
    S("pthread_condattr_init", sysv_pthread_condattr_init),
    S("pthread_condattr_destroy", sysv_pthread_condattr_destroy),
    S("pthread_condattr_setclock", sysv_pthread_condattr_setclock),
    S("pthread_rwlock_init", sysv_pthread_rwlock_init),
    S("pthread_rwlock_destroy", sysv_pthread_rwlock_destroy),
    S("pthread_rwlock_rdlock", sysv_pthread_rwlock_rdlock),
    S("pthread_rwlock_wrlock", sysv_pthread_rwlock_wrlock),
    S("pthread_rwlock_unlock", sysv_pthread_rwlock_unlock),
    S("pthread_create", sysv_pthread_create), S("pthread_join", sysv_pthread_join),
    S("pthread_detach", sysv_pthread_detach), S("pthread_self", sysv_pthread_self),
    S("pthread_attr_init", sysv_pthread_attr_init),
    S("pthread_attr_destroy", sysv_pthread_attr_destroy),
    S("pthread_attr_setdetachstate", sysv_pthread_attr_setdetachstate),
    S("pthread_attr_setstacksize", sysv_pthread_attr_setstacksize),
    S("pthread_attr_getstacksize", sysv_pthread_attr_getstacksize),
    S("pthread_setname_np", sysv_pthread_setname_np),
    S("pthread_getname_np", sysv_pthread_getname_np),
    S("pthread_getschedparam", sysv_pthread_getschedparam),
    S("pthread_setschedparam", sysv_pthread_setschedparam),
    S("pthread_getaffinity_np", sysv_pthread_getaffinity_np),
    S("pthread_sigmask", sysv_pthread_sigmask),
    S("pthread_once", sysv_pthread_once),
    S("pthread_key_create", sysv_pthread_key_create),
    S("pthread_key_delete", sysv_pthread_key_delete),
    S("pthread_getspecific", sysv_pthread_getspecific),
    S("pthread_setspecific", sysv_pthread_setspecific),
    S("sem_init", sysv_sem_init), S("sem_destroy", sysv_sem_destroy),
    S("sem_post", sysv_sem_post), S("sem_wait", sysv_sem_wait),
    S("sem_trywait", sysv_sem_trywait),

    // process / scheduling
    S("getpid", sysv_getpid), S("gettid", sysv_gettid),
    S("geteuid", sysv_geteuid), S("getuid", sysv_getuid),
    S("getegid", sysv_getegid), S("getgid", sysv_getgid),
    S("sched_yield", sysv_sched_yield), S("sched_getaffinity", sysv_sched_getaffinity),
    S("sched_setaffinity", sysv_sched_setaffinity),
    S("sched_setscheduler", sysv_sched_setscheduler), S("setpriority", sysv_setpriority),
    S("prctl", sysv_prctl), S("syscall", sysv_syscall),
    S("getrlimit64", sysv_getrlimit64), S("setrlimit64", sysv_setrlimit64),
    S("sigfillset", sysv_sigfillset), S("sigemptyset", sysv_sigemptyset),
    S("sigaddset", sysv_sigaddset), S("sigaction", sysv_sigaction),
    S("sigprocmask", sysv_sigprocmask),
    S("getauxval", sysv_getauxval), S("getpagesize", sysv_getpagesize),
    S("sysconf", sysv_sysconf), S("uname", sysv_uname),
    S("__sched_cpucount", sysv___sched_cpucount),
    S("getenv", sysv_getenv), S("setenv", sysv_setenv),
    S("abort", sysv_abort), S("_exit", sysv__exit), S("exit", sysv_exit),
    S("atexit", sysv_atexit),

    // C++ runtime
    S("__cxa_atexit", sysv_cxa_atexit), S("__cxa_finalize", sysv_cxa_finalize),
    S("__gmon_start__", sysv_gmon_start),
    S("_ITM_registerTMCloneTable", sysv_itm_register),
    S("_ITM_deregisterTMCloneTable", sysv_itm_deregister),
    S("__gcov_dump", sysv_gcov_dump), S("__gcov_flush", sysv_gcov_flush),
    S("__register_atfork", sysv_register_atfork),
    S("_Unwind_RaiseException", sysv_unwind_raise_exception),
    S("_Unwind_Resume", sysv_unwind_resume),
    S("_Unwind_SetGR", sysv_unwind_set_gr),
    S("_Unwind_GetIP", sysv_unwind_get_ip),
    S("_Unwind_SetIP", sysv_unwind_set_ip),
    S("_Unwind_GetLanguageSpecificData", sysv_unwind_get_language_specific_data),
    S("_Unwind_GetRegionStart", sysv_unwind_get_region_start),
    S("_Unwind_DeleteException", sysv_unwind_delete_exception),
    S("_setjmp", sysv_setjmp), S("__sigsetjmp", sysv_setjmp), S("setjmp", sysv_setjmp),
    S("longjmp", sysv_longjmp), S("siglongjmp", sysv_longjmp),
    S("__udivti3", shim_udivti3),

    // locales and catalogues
    S("newlocale", sysv_newlocale), S("duplocale", sysv_duplocale),
    S("freelocale", sysv_freelocale), S("uselocale", sysv_uselocale),
    S("localeconv", sysv_localeconv),
    S("catopen", sysv_catopen), S("catgets", sysv_catgets), S("catclose", sysv_catclose),

    // dynamic loading
    S("dlsym", sysv_dlsym), S("dlopen", sysv_dlopen), S("dlclose", sysv_dlclose),
    S("dlerror", sysv_dlerror),

    // files and descriptors
    S("__fxstat64", sysv_fxstat64), S("__xstat64", sysv_xstat64),
    S("fcntl", sysv_fcntl), S("open", sysv_open), S("open64", sysv_open64),
    S("creat64", sysv_creat64), S("close", sysv_close),
    S("read", sysv_read), S("write", sysv_write),
    S("pread64", sysv_pread64), S("pwrite64", sysv_pwrite64), S("lseek", sysv_lseek),
    S("access", sysv_access),

    // stdio
    S("fprintf", sysv_fprintf), S("printf", sysv_printf), S("vfprintf", sysv_vfprintf),
    S("snprintf", sysv_snprintf), S("vsnprintf", sysv_vsnprintf),
    S("sprintf", sysv_sprintf), S("vasprintf", sysv_vasprintf),
    S("__isoc99_sscanf", sysv_sscanf), S("__isoc99_vsscanf", sysv_vsscanf),
    S("sscanf", sysv_sscanf), S("vsscanf", sysv_vsscanf),
    S("fwrite", sysv_fwrite), S("fread", sysv_fread), S("fflush", sysv_fflush),
    S("fclose", sysv_fclose), S("fopen", sysv_fopen), S("fopen64", sysv_fopen64),
    S("fgets", sysv_fgets), S("fputc", sysv_fputc), S("fgetc", sysv_fgetc),
    S("getc", sysv_getc), S("ungetc", sysv_ungetc), S("putc", sysv_putc),
    S("puts", sysv_puts), S("perror", sysv_perror), S("fileno", sysv_fileno),
    S("fputwc", sysv_fputwc), S("getwc", sysv_getwc), S("ungetwc", sysv_ungetwc),
    S("fseek", sysv_fseek), S("ftell", sysv_ftell), S("rewind", sysv_rewind),
    S("setvbuf", sysv_setvbuf), S("feof", sysv_feof), S("ferror", sysv_ferror),
    S("remove", sysv_remove),

    // BoringSSL
    S("OPENSSL_memory_alloc", sysv_openssl_memory_alloc),
    S("OPENSSL_memory_free", sysv_openssl_memory_free),
    S("OPENSSL_memory_get_size", sysv_openssl_memory_get_size),

    // misc
    S("qsort", sysv_qsort),

    // libm
    S("ceil", sysv_ceil), S("ceilf", sysv_ceilf), S("floor", sysv_floor),
    S("exp", sysv_exp), S("log2", sysv_log2), S("rint", sysv_rint),
    S("round", sysv_round), S("ldexp", sysv_ldexp), S("frexp", sysv_frexp),
    S("pow", sysv_pow), S("log", sysv_log), S("sqrt", sysv_sqrt), S("fmod", sysv_fmod),

    // TLS
    S("__tls_get_addr", sysv_tls_get_addr),
};

#define SHIM_ENTRY_COUNT ((int)(sizeof(g_shim) / sizeof(g_shim[0])))

extern "C" void* shim_lookup(const char* name) {
    if (!name) return NULL;
    // stdio streams are macros in ucrt: resolve them at run time, as data.
    if (strcmp(name, "stderr") == 0) return (void*)stderr;
    if (strcmp(name, "stdout") == 0) return (void*)stdout;
    if (strcmp(name, "stdin") == 0) return (void*)stdin;
    for (int i = 0; i < SHIM_ENTRY_COUNT; i++)
        if (strcmp(g_shim[i].name, name) == 0) return g_shim[i].address;
    return NULL;
}

extern "C" int shim_count(void) { return SHIM_ENTRY_COUNT; }
