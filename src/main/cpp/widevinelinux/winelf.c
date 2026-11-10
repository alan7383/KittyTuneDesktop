// The loader behind winelf.h: map an ELF64 x86-64 shared object, relocate it, run its initialisers,
// and keep the Linux thread pointer (`fs`) alive under a scheduler that resets it.
//
// Built with MinGW-w64 for Windows, but it runs Linux code: every call it makes into the image goes
// through a pointer, every pointer the image holds was relocated to our base.
#include "winelf.h"

#include <windows.h>
#include <immintrin.h>
#include <inttypes.h>
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

// ---------------------------------------------------------------------------------------------
// ELF64 layout, only the fields used here
// ---------------------------------------------------------------------------------------------

typedef struct {
    uint8_t e_ident[16];
    uint16_t e_type, e_machine;
    uint32_t e_version;
    uint64_t e_entry, e_phoff, e_shoff;
    uint32_t e_flags;
    uint16_t e_ehsize, e_phentsize, e_phnum, e_shentsize, e_shnum, e_shstrndx;
} Elf64_Ehdr;

typedef struct {
    uint32_t p_type, p_flags;
    uint64_t p_offset, p_vaddr, p_paddr, p_filesz, p_memsz, p_align;
} Elf64_Phdr;

typedef struct { int64_t d_tag; uint64_t d_val; } Elf64_Dyn;
typedef struct { uint64_t r_offset, r_info; int64_t r_addend; } Elf64_Rela;
typedef struct {
    uint32_t st_name;
    uint8_t st_info, st_other;
    uint16_t st_shndx;
    uint64_t st_value, st_size;
} Elf64_Sym;

enum {
    PT_LOAD = 1, PT_DYNAMIC = 2, PT_TLS = 7,
    DT_NULL = 0, DT_PLTRELSZ = 2, DT_HASH = 4, DT_STRTAB = 5, DT_SYMTAB = 6, DT_RELA = 7,
    DT_RELASZ = 8,
    DT_STRSZ = 10, DT_INIT = 12, DT_JMPREL = 23, DT_INIT_ARRAY = 25, DT_INIT_ARRAYSZ = 27,
    R_X86_64_NONE = 0, R_X86_64_64 = 1, R_X86_64_GLOB_DAT = 6, R_X86_64_JUMP_SLOT = 7,
    R_X86_64_RELATIVE = 8, R_X86_64_DTPMOD64 = 16, R_X86_64_DTPOFF64 = 17, R_X86_64_TPOFF64 = 18
};

#define ELF64_R_SYM(info)  ((uint32_t)((info) >> 32))
#define ELF64_R_TYPE(info) ((uint32_t)((info) & 0xffffffffu))

// ---------------------------------------------------------------------------------------------
// Provided by the glibc shim next to this file
// ---------------------------------------------------------------------------------------------


// ---------------------------------------------------------------------------------------------
// State
// ---------------------------------------------------------------------------------------------

static WinElfImage g_image;
static int g_loaded;
static int g_trace_enabled = 1;

// A stub that logs the name of a symbol the shim does not implement, returns 0 and lets the caller
// continue. It is deliberately non-fatal: during bring-up, knowing which symbol was touched and
// which one broke later is worth more than stopping at the first unknown.
//
//   mov r11, <name>           49 BB imm64
//   mov [rip+disp32], r11     4C 89 1D disp32
//   mov rax, <handler>        48 B8 imm64
//   jmp rax                   FF E0
#define STUB_SIZE 32

static const char* volatile g_stub_name;
static uint8_t* g_stub_pool;
static int g_stub_next;
static uint64_t g_unresolved_count;

static void unresolved_symbol_called(void);

static uint64_t make_unresolved_stub(const char* name) {
    uint8_t* s = g_stub_pool + (size_t)g_stub_next * STUB_SIZE;
    g_stub_next++;
    const char* copy = _strdup(name ? name : "(anonymous)");
    // mov r11, imm64
    s[0] = 0x49; s[1] = 0xBB;
    *(uint64_t*)(s + 2) = (uint64_t)copy;
    // mov [rip+disp32], r11
    s[10] = 0x4C; s[11] = 0x89; s[12] = 0x1D;
    *(int32_t*)(s + 13) = (int32_t)((uint8_t*)&g_stub_name - (s + 17));
    // mov rax, imm64 ; jmp rax
    s[17] = 0x48; s[18] = 0xB8;
    *(uint64_t*)(s + 19) = (uint64_t)unresolved_symbol_called;
    s[27] = 0xFF; s[28] = 0xE0;
    for (int i = 29; i < STUB_SIZE; i++) s[i] = 0xCC;
    return (uint64_t)s;
}

static void unresolved_symbol_called(void) {
    const char* name = g_stub_name;
    fprintf(stderr, "[winelf] the CDM called an undefined symbol: %s\n", name ? name : "(?)");
    fflush(stderr);
}

// ---------------------------------------------------------------------------------------------
// File helpers
// ---------------------------------------------------------------------------------------------

static uint8_t* read_whole_file(const char* path, size_t* out_size) {
    FILE* f = fopen(path, "rb");
    if (!f) return NULL;
    if (fseek(f, 0, SEEK_END) != 0) { fclose(f); return NULL; }
    long size = ftell(f);
    if (size <= 0) { fclose(f); return NULL; }
    if (fseek(f, 0, SEEK_SET) != 0) { fclose(f); return NULL; }
    uint8_t* buffer = (uint8_t*)malloc((size_t)size);
    if (!buffer) { fclose(f); return NULL; }
    size_t got = fread(buffer, 1, (size_t)size, f);
    fclose(f);
    if (got != (size_t)size) { free(buffer); return NULL; }
    *out_size = (size_t)size;
    return buffer;
}

static DWORD protection_from_flags(uint32_t flags) {
    int read = (flags & 4) != 0, write = (flags & 2) != 0, execute = (flags & 1) != 0;
    if (execute && write) return PAGE_EXECUTE_READWRITE;
    if (execute) return PAGE_EXECUTE_READ;
    if (write) return PAGE_READWRITE;
    return read ? PAGE_READONLY : PAGE_NOACCESS;
}

// ---------------------------------------------------------------------------------------------
// Mapping and relocation
// ---------------------------------------------------------------------------------------------

int winelf_load(const char* path, WinElfImage* out, char* err, size_t err_len) {
    if (err && err_len) err[0] = 0;
    size_t file_size = 0;
    uint8_t* file = read_whole_file(path, &file_size);
    if (!file) {
        if (err) snprintf(err, err_len, "cannot read %s", path ? path : "(null)");
        return 1;
    }
    if (file_size < sizeof(Elf64_Ehdr) || memcmp(file, "\x7f" "ELF", 4) != 0 || file[4] != 2 ||
        file[5] != 1) {
        if (err) snprintf(err, err_len, "not a little-endian ELF64 file");
        free(file);
        return 1;
    }
    const Elf64_Ehdr* eh = (const Elf64_Ehdr*)file;
    if (eh->e_machine != 0x3e) {
        if (err) snprintf(err, err_len, "not x86-64 (e_machine=%u)", eh->e_machine);
        free(file);
        return 1;
    }
    const Elf64_Phdr* phdrs = (const Elf64_Phdr*)(file + eh->e_phoff);

    uint64_t span = 0;
    for (int i = 0; i < eh->e_phnum; i++) {
        if (phdrs[i].p_type != PT_LOAD) continue;
        uint64_t end = phdrs[i].p_vaddr + phdrs[i].p_memsz;
        if (end > span) span = end;
    }
    span = (span + 0xFFF) & ~(uint64_t)0xFFF;
    if (span == 0) {
        if (err) snprintf(err, err_len, "no loadable segment");
        free(file);
        return 1;
    }

    uint8_t* base = (uint8_t*)VirtualAlloc(NULL, (SIZE_T)span, MEM_RESERVE, PAGE_NOACCESS);
    if (!base) {
        if (err) snprintf(err, err_len, "cannot reserve %llu bytes: %lu",
                          (unsigned long long)span, GetLastError());
        free(file);
        return 1;
    }
    for (int i = 0; i < eh->e_phnum; i++) {
        if (phdrs[i].p_type != PT_LOAD) continue;
        uint64_t start = phdrs[i].p_vaddr & ~(uint64_t)0xFFF;
        uint64_t end = (phdrs[i].p_vaddr + phdrs[i].p_memsz + 0xFFF) & ~(uint64_t)0xFFF;
        if (!VirtualAlloc(base + start, (SIZE_T)(end - start), MEM_COMMIT, PAGE_READWRITE)) {
            if (err) snprintf(err, err_len, "cannot commit +0x%llx: %lu",
                              (unsigned long long)start, GetLastError());
            VirtualFree(base, 0, MEM_RELEASE);
            free(file);
            return 1;
        }
        if (phdrs[i].p_filesz)
            memcpy(base + phdrs[i].p_vaddr, file + phdrs[i].p_offset, (size_t)phdrs[i].p_filesz);
    }

    memset(out, 0, sizeof(*out));
    out->base = base;
    out->span = (size_t)span;

    const Elf64_Dyn* dyn = NULL;
    for (int i = 0; i < eh->e_phnum; i++) {
        if (phdrs[i].p_type == PT_DYNAMIC) dyn = (const Elf64_Dyn*)(file + phdrs[i].p_offset);
        if (phdrs[i].p_type == PT_TLS) {
            out->tls_vaddr = phdrs[i].p_vaddr;
            out->tls_filesz = phdrs[i].p_filesz;
            out->tls_memsz = phdrs[i].p_memsz;
            out->tls_align = phdrs[i].p_align;
        }
    }
    if (!dyn) {
        if (err) snprintf(err, err_len, "no PT_DYNAMIC");
        VirtualFree(base, 0, MEM_RELEASE);
        free(file);
        return 1;
    }

    uint64_t strtab_va = 0, symtab_va = 0, rela_va = 0, jmprel_va = 0, hash_va = 0;
    for (const Elf64_Dyn* d = dyn; d->d_tag != DT_NULL; d++) {
        switch (d->d_tag) {
            case DT_STRTAB: strtab_va = d->d_val; break;
            case DT_STRSZ: out->strsz = d->d_val; break;
            case DT_SYMTAB: symtab_va = d->d_val; break;
            case DT_RELA: rela_va = d->d_val; break;
            case DT_RELASZ: out->relasz = d->d_val; break;
            case DT_JMPREL: jmprel_va = d->d_val; break;
            case DT_PLTRELSZ: out->pltrelsz = d->d_val; break;
            case DT_INIT: out->init_fn = d->d_val; break;
            case DT_INIT_ARRAY: out->init_array = d->d_val; break;
            case DT_INIT_ARRAYSZ: out->init_arraysz = d->d_val; break;
            case DT_HASH: hash_va = d->d_val; break;
            default: break;
        }
    }
    out->strtab = (const char*)(base + strtab_va);
    out->symtab = (const void*)(base + symtab_va);
    out->rela = (const void*)(base + rela_va);
    out->jmprel = (const void*)(base + jmprel_va);
    if (hash_va) {
        const uint32_t* hash = (const uint32_t*)(base + hash_va);
        out->symcount = hash[1];  // nchain
    }
    if (out->symcount == 0) {
        if (err) snprintf(err, err_len, "no DT_HASH; cannot size the symbol table");
        VirtualFree(base, 0, MEM_RELEASE);
        free(file);
        return 1;
    }

    // R_X86_64_RELATIVE first: everything else may point into the image.
    uint64_t rela_count = out->relasz / sizeof(Elf64_Rela);
    for (uint64_t i = 0; i < rela_count; i++) {
        const Elf64_Rela* r = (const Elf64_Rela*)out->rela + i;
        if (ELF64_R_TYPE(r->r_info) != R_X86_64_RELATIVE) continue;
        *(uint64_t*)(base + r->r_offset) = (uint64_t)base + (uint64_t)r->r_addend;
        out->relative_relocs++;
    }

    // Then symbol relocations: every import is satisfied by the shim or by a logging stub.
    uint64_t jmp_count = out->pltrelsz / sizeof(Elf64_Rela);
    // Worst case one stub per import.
    SIZE_T pool_size = (SIZE_T)((rela_count + jmp_count + 8) * STUB_SIZE);
    if (!g_stub_pool) {
        g_stub_pool = (uint8_t*)VirtualAlloc(NULL, pool_size, MEM_RESERVE | MEM_COMMIT,
                                             PAGE_EXECUTE_READWRITE);
        if (!g_stub_pool) {
            if (err) snprintf(err, err_len, "cannot allocate the stub pool");
            VirtualFree(base, 0, MEM_RELEASE);
            free(file);
            return 1;
        }
    }

    const Elf64_Sym* symbols = (const Elf64_Sym*)out->symtab;
    for (uint64_t i = 0; i < rela_count; i++) {
        const Elf64_Rela* r = (const Elf64_Rela*)out->rela + i;
        uint32_t type = ELF64_R_TYPE(r->r_info);
        uint32_t sym = ELF64_R_SYM(r->r_info);
        uint64_t* slot = (uint64_t*)(base + r->r_offset);
        const char* name = sym ? out->strtab + symbols[sym].st_name : NULL;
        switch (type) {
            case R_X86_64_GLOB_DAT: {
                void* target = name ? shim_lookup(name) : NULL;
                // A data import: the CDM loads *slot. 0 is a value; a code stub would be a lie.
                *slot = (uint64_t)target;
                if (!target && name) fprintf(stderr, "[winelf] unresolved data: %s -> 0\n", name);
                out->symbol_relocs++;
                break;
            }
            case R_X86_64_64: {
                void* target = name ? shim_lookup(name) : NULL;
                *slot = (uint64_t)target + (uint64_t)r->r_addend;
                if (!target && name) fprintf(stderr, "[winelf] unresolved: %s -> 0\n", name);
                out->symbol_relocs++;
                break;
            }
            case R_X86_64_JUMP_SLOT: {
                void* target = name ? shim_lookup(name) : NULL;
                if (target) {
                    *slot = (uint64_t)target;
                } else {
                    static const char kMissing[] = "(unnamed)";
                    *slot = make_unresolved_stub(name ? name : kMissing);
                    out->unresolved++;
                    g_unresolved_count++;
                    fprintf(stderr, "[winelf] unresolved function: %s -> logging stub\n", name);
                }
                out->symbol_relocs++;
                break;
            }
            case R_X86_64_DTPMOD64:
                *slot = 1;  // one module, ours
                break;
            case R_X86_64_TPOFF64:
                *slot = symbols[sym].st_value + (uint64_t)r->r_addend;
                break;
            default:
                break;
        }
    }
    (void)jmp_count;

    // PLT relocations live in their own table (DT_JMPREL), not in RELA.
    const Elf64_Rela* jmprel = (const Elf64_Rela*)out->jmprel;
    for (uint64_t i = 0; jmprel && i < jmp_count; i++) {
        uint32_t type = ELF64_R_TYPE(jmprel[i].r_info);
        uint32_t sym = ELF64_R_SYM(jmprel[i].r_info);
        if (type != R_X86_64_JUMP_SLOT && type != R_X86_64_64 && type != R_X86_64_GLOB_DAT)
            continue;
        uint64_t* slot = (uint64_t*)(base + jmprel[i].r_offset);
        const char* name = sym ? out->strtab + symbols[sym].st_name : NULL;
        void* target = name ? shim_lookup(name) : NULL;
        if (target) {
            *slot = (uint64_t)target;
        } else {
            static const char kMissing[] = "(unnamed)";
            *slot = make_unresolved_stub(name ? name : kMissing);
            out->unresolved++;
            g_unresolved_count++;
            fprintf(stderr, "[winelf] unresolved function: %s -> logging stub\n", name);
        }
        out->symbol_relocs++;
    }

    // Finally, protect each segment as the file asked.
    for (int i = 0; i < eh->e_phnum; i++) {
        if (phdrs[i].p_type != PT_LOAD) continue;
        uint64_t start = phdrs[i].p_vaddr & ~(uint64_t)0xFFF;
        uint64_t end = (phdrs[i].p_vaddr + phdrs[i].p_memsz + 0xFFF) & ~(uint64_t)0xFFF;
        DWORD old = 0;
        VirtualProtect(base + start, (SIZE_T)(end - start), protection_from_flags(phdrs[i].p_flags),
                       &old);
    }

    free(file);
    g_image = *out;
    g_loaded = 1;
    return 0;
}

void* winelf_symbol(const WinElfImage* image, const char* name) {
    if (!image || !image->base || !name) return NULL;
    const Elf64_Sym* symbols = (const Elf64_Sym*)image->symtab;
    for (uint64_t i = 1; i < image->symcount; i++) {
        if (symbols[i].st_shndx == 0) continue;
        const char* candidate = image->strtab + symbols[i].st_name;
        if (candidate && strcmp(candidate, name) == 0)
            return image->base + symbols[i].st_value;
    }
    return NULL;
}

void* winelf_symbol_at(const WinElfImage* image, uint64_t index) {
    if (!image || !image->base || index >= image->symcount) return NULL;
    const Elf64_Sym* symbols = (const Elf64_Sym*)image->symtab;
    if (symbols[index].st_shndx == 0) return NULL;
    return image->base + symbols[index].st_value;
}

// A call into the image must be typed System V, not just cast. The two conventions disagree about
// which registers a callee preserves: rdi and rsi are volatile under System V but must survive a
// Microsoft-ABI call, so an untyped call lets the CDM overwrite the caller's locals.
typedef void (__attribute__((sysv_abi)) *linux_void_function)(void);

// One line per initialiser is noise in a daemon; KT_LOG=1 brings it back when bring-up is the task.
static int loader_verbose(void) {
    static int verbose = -1;
    if (verbose < 0) {
        const char* value = getenv("KT_LOG");
        verbose = value && *value && strcmp(value, "0") != 0;
    }
    return verbose;
}

void winelf_run_initializers(const WinElfImage* image) {
    if (!image || !image->base) return;
    if (image->init_fn) {
        linux_void_function fn = (linux_void_function)(image->base + image->init_fn);
        if (loader_verbose()) fprintf(stderr, "[winelf] DT_INIT %p\n", (void*)fn);
        fn();
        if (loader_verbose()) fprintf(stderr, "[winelf] DT_INIT returned\n");
    }
    if (!image->init_array || !image->init_arraysz) return;
    uint64_t* entries = (uint64_t*)(image->base + image->init_array);
    uint64_t count = image->init_arraysz / 8;

    const char* skip = getenv("KT_SKIP_INIT");
    const uint64_t skip_mask = skip ? _strtoui64(skip, NULL, 16) : 0;
    for (uint64_t i = 0; i < count; i++) {
        uint64_t fn = entries[i];
        if (!fn) continue;
        if (skip_mask & (1ULL << i)) {
            fprintf(stderr, "[winelf] init_array[%llu] skipped\n", (unsigned long long)i);
            continue;
        }
        if (loader_verbose())
            fprintf(stderr, "[winelf] init_array[%llu] %p\n", (unsigned long long)i, (void*)fn);
        ((linux_void_function)fn)();
        if (loader_verbose()) fprintf(stderr, "[winelf] init_array[%llu] returned\n",
                                      (unsigned long long)i);
    }
}

// ---------------------------------------------------------------------------------------------
// Thread-local storage
// ---------------------------------------------------------------------------------------------

// Linux x86-64 initial-exec TLS layout, which is what a `.so` uses:
//
//     [ tls data | TCB ]        fs base points at the TCB (`tp`)
//     ^          ^
//     tp - memsz tp
//
// The module's variables live at negative offsets from `tp`; glibc's `tcbhead_t` holds the self
// pointer at 0, the dtv at 8, and the stack guard at 0x28, which is the canary every function
// prologue checks.
#define ARENA_SIZE 0x10000u
#define TP_OFFSET  0x8000u

typedef struct { DWORD tid; uint8_t* tcb; } TlsSlot;
#define MAX_THREADS 1024
static TlsSlot g_tls_slots[MAX_THREADS];
// Slots in use, and the next one to overwrite when the table is full. `volatile LONG` read without
// the lock on the fault path would be a data race; the fault path takes the shared lock.
static LONG g_tls_used;
static LONG g_tls_victim;
static volatile LONG64 g_tls_count;
static volatile LONG64 g_tls_evictions;

// An SRW lock, not a critical section: it is statically initialisable, so it can be used from a
// vectored exception handler without allocating or calling into anything that might be busy.
static SRWLOCK g_tls_lock = SRWLOCK_INIT;

static uint8_t* g_tls_image_data;   // pointer into the image
static uint64_t g_tls_image_filesz;
static uint64_t g_tls_image_memsz;
static uint64_t g_tls_image_align = 32;
static uint64_t g_canary;

static void make_canary(void) {
    LARGE_INTEGER counter;
    QueryPerformanceCounter(&counter);
    uint64_t value = (uint64_t)counter.QuadPart ^ ((uint64_t)GetCurrentProcessId() << 32) ^
                     (uint64_t)GetTickCount64() ^ 0x9E3779B97F4A7C15ULL;
    value |= 0x100;  // a canary of 0 would hide a missing read
    g_canary = value;
}

static uint8_t* build_tcb(void) {
    uint8_t* arena = (uint8_t*)VirtualAlloc(NULL, ARENA_SIZE, MEM_RESERVE | MEM_COMMIT,
                                            PAGE_READWRITE);
    if (!arena) return NULL;
    uint8_t* tp = arena + TP_OFFSET;
    const uint64_t memsz = g_tls_image_memsz ? g_tls_image_memsz : 0;
    if (memsz && memsz <= TP_OFFSET) {
        uint64_t start = (uint64_t)TP_OFFSET - memsz;
        uint64_t align = g_tls_image_align ? g_tls_image_align : 1;
        start &= ~(align - 1);
        if (g_tls_image_data && g_tls_image_filesz) {
            uint64_t copy = g_tls_image_filesz < memsz ? g_tls_image_filesz : memsz;
            memcpy(arena + start, g_tls_image_data, (size_t)copy);
        }
    }
    *(uint64_t*)(tp + 0x00) = (uint64_t)tp;      // tcb
    *(uint64_t*)(tp + 0x08) = 0;                 // dtv: unused, nothing calls __tls_get_addr
    *(uint64_t*)(tp + 0x10) = (uint64_t)tp;      // self
    *(uint32_t*)(tp + 0x18) = 1;                 // multiple_threads
    *(uint64_t*)(tp + 0x20) = 0;                 // sysinfo
    *(uint64_t*)(tp + 0x28) = g_canary;          // stack_guard — the canary at fs:[0x28]
    *(uint64_t*)(tp + 0x30) = g_canary ^ 0xA5A5A5A5A5A5A5A5ULL;  // pointer_guard
    return tp;
}

static uint8_t* tls_slot_find(DWORD tid) {
    uint8_t* found = NULL;
    AcquireSRWLockShared(&g_tls_lock);
    const LONG count = g_tls_used;
    for (LONG i = 0; i < count; i++) {
        if (g_tls_slots[i].tid == tid) { found = g_tls_slots[i].tcb; break; }
    }
    ReleaseSRWLockShared(&g_tls_lock);
    return found;
}

static int tls_slot_add(DWORD tid, uint8_t* tcb) {
    AcquireSRWLockExclusive(&g_tls_lock);
    for (LONG i = 0; i < g_tls_used; i++) {
        if (g_tls_slots[i].tid == tid) {
            g_tls_slots[i].tcb = tcb;
            ReleaseSRWLockExclusive(&g_tls_lock);
            return 1;
        }
    }
    if (g_tls_used < MAX_THREADS) {
        g_tls_slots[g_tls_used].tcb = tcb;
        g_tls_slots[g_tls_used].tid = tid;
        g_tls_used++;
        ReleaseSRWLockExclusive(&g_tls_lock);
        InterlockedIncrement64(&g_tls_count);
        return 1;
    }
    // Full. The CDM creates a thread per operation and none of them ever tells us it ended, so the
    // table fills on a long session; a thread that is not in it runs with `fs` = 0 and dies on the
    // first stack canary read (`mov rax, fs:[0x28]`, fault at 0x28 — measured). Overwriting the
    // oldest entry keeps new threads working; the thread it displaces re-registers itself through
    // the fault handler below if it ever runs CDM code again.
    const LONG victim = g_tls_victim++ % MAX_THREADS;
    g_tls_slots[victim].tcb = tcb;
    g_tls_slots[victim].tid = tid;
    ReleaseSRWLockExclusive(&g_tls_lock);
    InterlockedIncrement64(&g_tls_count);
    if (InterlockedIncrement64(&g_tls_evictions) % 64 == 1) {
        fprintf(stderr, "[winelf] thread table full, reusing slot %ld\n", (long)victim);
        fflush(stderr);
    }
    return 1;
}

uint8_t* winelf_tcb_for_current_thread(void) {
    return tls_slot_find(GetCurrentThreadId());
}

void winelf_tls_install_for_current_thread(void) {
    if (!g_canary) make_canary();
    winelf_commit_stack();
    DWORD tid = GetCurrentThreadId();
    uint8_t* tp = tls_slot_find(tid);
    if (!tp) {
        tp = build_tcb();
        if (!tp) {
            fprintf(stderr, "[winelf] cannot allocate a TCB for thread %lu\n", (unsigned long)tid);
            return;
        }
        tls_slot_add(tid, tp);
    }
    _writefsbase_u64((uint64_t)tp);
}

uint64_t winelf_tls_memsz(void) { return g_tls_image_memsz; }

// The CDM's code — like any Linux binary — subtracts large frames from `rsp` and then writes far
// below it, which Linux grows the stack for and Windows does not: a store below the guard page is
// not a stack-growth request, it is a fault. Committing the whole reserved stack up front turns
// those stores into ordinary writes. One 16 KiB frame in Widevine's licence path measured this.
// The CDM's code — like any Linux binary — subtracts large frames from `rsp` and then writes far
// below it, which Linux grows the stack for and Windows does not: a store below the guard page is
// not a stack-growth request, it is a fault. Walking down the stack once, a page at a time, commits
// those pages up front through the mechanism Windows does support. One 16 KiB frame in Widevine's
// licence path measured this.
static void touch_stack_pages(size_t remaining) {
    volatile unsigned char page[4096];
    if (remaining == 0) return;
    page[0] = 0;
    page[sizeof(page) - 1] = 0;
    touch_stack_pages(remaining - 4096);
    (void)page[0];
}

void winelf_commit_stack(void) {
    // 256 KiB covers several of the CDM's 16 KiB frames and stays well inside the smallest stack we
    // hand a thread (4 MiB for CDM threads, 32 MiB for the main thread).
    touch_stack_pages(256u * 1024u);
}

void* winelf_base(void) { return g_loaded ? g_image.base : NULL; }

void winelf_set_trace_enabled(int enabled) {
    g_trace_enabled = enabled;
    shim_set_trace_enabled(enabled);
}

void winelf_dump_trace(void) { shim_dump_trace(); }

void* winelf_lookup_export(const char* name) {
    return g_loaded ? winelf_symbol(&g_image, name) : NULL;
}
int winelf_trace_enabled(void) { return g_trace_enabled; }

// ---------------------------------------------------------------------------------------------
// Exception handling
// ---------------------------------------------------------------------------------------------

static volatile LONG64 g_fs_restores;
static volatile LONG64 g_fs_passthrough;

static int readable_range(const void* address, size_t size) {
    if (!address || (uintptr_t)address < 0x10000) return 0;
    MEMORY_BASIC_INFORMATION region;
    if (!VirtualQuery(address, &region, sizeof(region))) return 0;
    if (region.State != MEM_COMMIT || (region.Protect & (PAGE_NOACCESS | PAGE_GUARD))) return 0;
    const uintptr_t start = (uintptr_t)address;
    const uintptr_t end = (uintptr_t)region.BaseAddress + region.RegionSize;
    return start + size <= end;
}

// Windows resets `fs` on every reschedule, so the canary read at fs:[0x28] faults on the first
// instruction after the thread resumes. Restoring the base and continuing the same instruction
// costs a few microseconds per quantum: measured at ~1 fault per 15 ms slice.
static LONG CALLBACK fs_exception_handler(PEXCEPTION_POINTERS info) {
    EXCEPTION_RECORD* record = info->ExceptionRecord;

    const uint64_t fault = record->NumberParameters >= 2 ? (uint64_t)record->ExceptionInformation[1] : 0;
    const uint8_t* rip = (const uint8_t*)info->ContextRecord->Rip;
    uint8_t* tp = tls_slot_find(GetCurrentThreadId());

    // A thread we have never seen — a pool thread the CDM was called back on, or one whose slot was
    // reused — runs with `fs` = 0, so its very first stack canary read faults. Register it here, on
    // the spot, and retry the instruction: an unknown thread is a setup that has not happened yet,
    // not a reason to take the process down.
    if (!tp) {
        if (!(readable_range(rip, 1) && rip[0] == 0x64)) {
            InterlockedIncrement64(&g_fs_passthrough);
            return EXCEPTION_CONTINUE_SEARCH;
        }
        winelf_tls_install_for_current_thread();
        tp = tls_slot_find(GetCurrentThreadId());
        if (!tp) {
            InterlockedIncrement64(&g_fs_passthrough);
            return EXCEPTION_CONTINUE_SEARCH;
        }
        _writefsbase_u64((uint64_t)tp);
        InterlockedIncrement64(&g_fs_restores);
        return EXCEPTION_CONTINUE_EXECUTION;
    }

    const uint64_t base = _readfsbase_u64();

    // A reset base points every `fs:` access at linear 0 + offset: small for the canary, huge and
    // negative for the module's own TLS variables. Both are repaired the same way.
    // Only an `fs:`-prefixed instruction can be a Linux TLS access. Keying on the fault address
    // instead (small or negative-looking) misreads a genuine fault as a TLS one and retries the
    // same instruction for ever — measured: a 16 KiB frame store into the uncommitted stack, which
    // faults every time, so the process dies without ever reaching the diagnostic handler.
    const int fs_prefixed = readable_range(rip, 1) && rip[0] == 0x64;

    if (fs_prefixed && base != (uint64_t)tp) {
        _writefsbase_u64((uint64_t)tp);
        const LONG64 count = InterlockedIncrement64(&g_fs_restores);
        // A healthy run takes one fault per scheduler quantum. Thousands per second, or a fault
        // whose instruction is not an `fs:` access, means the handler is looping and the CDM is not
        // making progress — worth saying out loud rather than appearing to hang.
        if (count % 20000 == 0) {
            char where[256];
            winelf_describe_address(rip, where, sizeof(where));
            fprintf(stderr, "[winelf] fs restore #%lld at %s (fault 0x%llx, base was 0x%llx)\n",
                    (long long)count, where, (unsigned long long)fault,
                    (unsigned long long)base);
            fflush(stderr);
        }
        return EXCEPTION_CONTINUE_EXECUTION;
    }
        (void)fs_prefixed;
    InterlockedIncrement64(&g_fs_passthrough);
    return EXCEPTION_CONTINUE_SEARCH;
}

void winelf_describe_address(const void* address, char* out, size_t out_len) {
    if (!out || out_len == 0) return;
    out[0] = 0;
    if (!g_loaded || !address) {
        snprintf(out, out_len, "%p", address);
        return;
    }
    const uint8_t* base = g_image.base;
    if ((const uint8_t*)address < base || (const uint8_t*)address >= base + g_image.span) {
        // Outside the CDM: name the owning module, if any, so a crash in ucrt or bcrypt is obvious.
        HMODULE module = NULL;
        if (GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS |
                                   GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
                               (LPCSTR)address, &module) &&
            module) {
            char path[MAX_PATH] = {0};
            if (GetModuleFileNameA(module, path, MAX_PATH)) {
                const char* name = strrchr(path, '\\');
                name = name ? name + 1 : path;
                snprintf(out, out_len, "%s+" "0x%llx", name,
                         (unsigned long long)((const uint8_t*)address - (const uint8_t*)module));
                return;
            }
        }
        snprintf(out, out_len, "%p (outside the CDM)", address);
        return;
    }
    uint64_t offset = (uint64_t)((const uint8_t*)address - base);
    // Nearest preceding export, if any.
    const Elf64_Sym* symbols = (const Elf64_Sym*)g_image.symtab;
    const char* best = NULL;
    uint64_t best_value = 0;
    for (uint64_t i = 1; i < g_image.symcount; i++) {
        if (symbols[i].st_shndx == 0 || !symbols[i].st_value) continue;
        if (symbols[i].st_value <= offset && symbols[i].st_value >= best_value) {
            best = g_image.strtab + symbols[i].st_name;
            best_value = symbols[i].st_value;
        }
    }
    if (best && best[0])
        snprintf(out, out_len, "cdm+0x%llx (%s+0x%llx)", (unsigned long long)offset, best,
                 (unsigned long long)(offset - best_value));
    else
        snprintf(out, out_len, "cdm+0x%llx", (unsigned long long)offset);
}

static LONG CALLBACK diagnostic_exception_handler(PEXCEPTION_POINTERS info) {
    EXCEPTION_RECORD* record = info->ExceptionRecord;
    CONTEXT* context = info->ContextRecord;
    fflush(stderr);
    char where[256];
    winelf_describe_address((const void*)context->Rip, where, sizeof(where));
    const uint64_t fault = record->NumberParameters >= 2 ? (uint64_t)record->ExceptionInformation[1] : 0;
    fprintf(stderr, "[winelf] exception 0x%08lx at %s, fault address 0x%llx\n",
            (unsigned long)record->ExceptionCode, where, (unsigned long long)fault);
    fprintf(stderr,
            "[winelf] rax=%016llx rbx=%016llx rcx=%016llx rdx=%016llx\n"
            "[winelf] rsi=%016llx rdi=%016llx rbp=%016llx rsp=%016llx\n"
            "[winelf] r8 =%016llx r9 =%016llx r10=%016llx r11=%016llx\n",
            (unsigned long long)context->Rax, (unsigned long long)context->Rbx,
            (unsigned long long)context->Rcx, (unsigned long long)context->Rdx,
            (unsigned long long)context->Rsi, (unsigned long long)context->Rdi,
            (unsigned long long)context->Rbp, (unsigned long long)context->Rsp,
            (unsigned long long)context->R8, (unsigned long long)context->R9,
            (unsigned long long)context->R10, (unsigned long long)context->R11);
    if (g_loaded && context->Rip >= (uint64_t)g_image.base &&
        context->Rip < (uint64_t)g_image.base + g_image.span && readable_range((void*)context->Rip, 16)) {
        const uint8_t* rip = (const uint8_t*)context->Rip;
        fprintf(stderr, "[winelf] code:");
        for (int i = 0; i < 16; i++) fprintf(stderr, " %02x", rip[i]);
        fprintf(stderr, "\n");
    }
    void* frames[24];
    USHORT count = CaptureStackBackTrace(0, 24, frames, NULL);
    fprintf(stderr, "[winelf] backtrace (%u frames):\n", (unsigned)count);
    for (USHORT i = 0; i < count; i++) {
        winelf_describe_address(frames[i], where, sizeof(where));
        fprintf(stderr, "[winelf]   %02u %s\n", (unsigned)i, where);
    }
    // A raw stack scan: every word that points into the image is a plausible return address, which
    // is how the CDM's own call chain shows up when frame pointers are not available.
    fprintf(stderr, "[winelf] stack words inside the CDM:\n");
    const uint64_t* stack = (const uint64_t*)(uintptr_t)context->Rsp;
    for (int i = 0; i < 96; i++) {
        if (!readable_range(&stack[i], 8)) break;
        uint64_t value = stack[i];
        if (g_loaded && value >= (uint64_t)g_image.base &&
            value < (uint64_t)g_image.base + g_image.span) {
            winelf_describe_address((const void*)(uintptr_t)value, where, sizeof(where));
            fprintf(stderr, "[winelf]   [rsp+0x%02x] %s\n", i * 8, where);
        }
    }
    uint64_t rbp = context->Rbp;
    for (int i = 0; i < 32 && rbp; i++) {
        MEMORY_BASIC_INFORMATION region;
        if (!VirtualQuery((void*)(uintptr_t)rbp, &region, sizeof(region))) break;
        if (region.State != MEM_COMMIT || (region.Protect & (PAGE_NOACCESS | PAGE_GUARD))) break;
        const uint64_t* frame = (const uint64_t*)(uintptr_t)rbp;
        const uint64_t return_address = frame[1];
        const uint64_t next = frame[0];
        winelf_describe_address((const void*)(uintptr_t)return_address, where, sizeof(where));
        fprintf(stderr, "[winelf]   %02d %s\n", i, where);
        if (next <= rbp) break;
        rbp = next;
    }
    winelf_dump_trace();
    fprintf(stderr, "[winelf] fs restores=%lld passthrough=%lld\n",
            (long long)g_fs_restores, (long long)g_fs_passthrough);
    fflush(stderr);
    return EXCEPTION_CONTINUE_SEARCH;
}

static LONG WINAPI unhandled_exception_filter(EXCEPTION_POINTERS* info) {
    fprintf(stderr, "[winelf] UNHANDLED exception in thread %lu\n",
            (unsigned long)GetCurrentThreadId());
    return diagnostic_exception_handler(info) == EXCEPTION_CONTINUE_SEARCH
               ? EXCEPTION_EXECUTE_HANDLER
               : EXCEPTION_CONTINUE_SEARCH;
}

void winelf_install_exception_handler(void) {
    AddVectoredExceptionHandler(0, diagnostic_exception_handler);
    AddVectoredExceptionHandler(1, fs_exception_handler);
    SetUnhandledExceptionFilter(unhandled_exception_filter);
}
