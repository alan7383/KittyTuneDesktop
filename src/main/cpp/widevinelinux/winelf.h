// winelf — a minimal ELF64 loader for x86-64 shared objects, on Windows, in user mode.
//
// Why this exists: the Widevine module SoundCloud's licence server accepts is the Linux build
// (`libwidevinecdm.so`). Every Windows build of the same CDM presents a client identity the server
// refuses. Running the Linux binary is the only way to get clear samples on Windows without a
// Linux host, so this loader maps the `.so` into the current process and runs it.
//
// Two Windows facts shape the design:
//
//  * The Linux binary addresses its thread-local storage through the `fs` segment and reads a stack
//    canary at `fs:[0x28]`. Windows resets the `fs` base of a thread at every reschedule (it keeps
//    the TEB in `gs`), so the loader installs a vectored exception handler that puts the base back
//    and resumes the faulting instruction. Measured: ~1 fault per quantum, a few microseconds each.
//
//  * The Linux binary is built for the System V AMD64 ABI. Every function we hand it, and every
//    function pointer it hands us, must be `__attribute__((sysv_abi))`. The glibc shim and the CDM
//    host next to this loader are written that way.
#pragma once

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct WinElfImage {
    uint8_t* base;           // chosen load address
    size_t   span;           // reserved span, 4 KiB rounded
    const char* strtab;
    uint64_t strsz;
    const void* symtab;
    uint64_t symcount;
    const void* rela;
    uint64_t relasz;
    const void* jmprel;
    uint64_t pltrelsz;
    uint64_t init_fn;        // DT_INIT
    uint64_t init_array;     // DT_INIT_ARRAY
    uint64_t init_arraysz;
    uint64_t tls_vaddr;
    uint64_t tls_filesz;
    uint64_t tls_memsz;
    uint64_t tls_align;
    // Counters, for the probe's summary line.
    uint64_t relative_relocs;
    uint64_t symbol_relocs;
    uint64_t unresolved;
} WinElfImage;

// Maps |path|, applies relative relocations, resolves every imported symbol through the shim and
// applies symbol relocations. Returns 0 on success and fills |err| otherwise.
int winelf_load(const char* path, WinElfImage* image, char* err, size_t err_len);

// Address of an exported symbol, or NULL.
void* winelf_symbol(const WinElfImage* image, const char* name);

// Address of a symbol exported by the image last loaded — used to answer the CDM's own dlsym.
void* winelf_lookup_export(const char* name);

// Address of any symbol by index (used to describe code addresses in traces).
void* winelf_symbol_at(const WinElfImage* image, uint64_t index);

// Runs DT_INIT then DT_INIT_ARRAY, in order. Must be called after winelf_load.
void winelf_run_initializers(const WinElfImage* image);

// Installs the exception handler that repairs the `fs` base, plus a diagnostic handler that prints
// the last shim calls and the CDM code address before the process dies.
void winelf_install_exception_handler(void);

// Creates (once) and installs this thread's Linux TCB, then points `fs` at it. Every thread that
// runs CDM code must call this first.
void winelf_tls_install_for_current_thread(void);

// The current thread's TCB, or NULL when this thread has none.
uint8_t* winelf_tcb_for_current_thread(void);

// Size of the module's TLS data block.
uint64_t winelf_tls_memsz(void);

// Commits the whole reserved stack of the calling thread, so Linux-style large-frame writes land in
// memory instead of faulting below the guard page.
void winelf_commit_stack(void);

// Load base, so traces can print CDM addresses as `cdm+0x...`.
void* winelf_base(void);

// Enables/disables the shim call trace (KT_TRACE=0 turns it off).
void winelf_set_trace_enabled(int enabled);

// Prints the tail of the shim call event log for the current thread.
void winelf_dump_trace(void);

// Writes a one-line description of |address|: `cdm+0x1234` when it is inside the image, and the
// exported name when it is.
void winelf_describe_address(const void* address, char* out, size_t out_len);

// The libc shim next to this loader. Declared here so both translation units agree on C linkage.
void* shim_lookup(const char* name);
int shim_count(void);
void shim_dump_trace(void);
void shim_set_trace_enabled(int enabled);
void shim_set_trace_verbose(int verbose);

#ifdef __cplusplus
}
#endif
