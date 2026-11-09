// Bring-up harness for the loader. Loads a Linux libwidevinecdm.so, runs its initialisers, calls
// InitializeCdmModule_4, then asks for an instance. This is the tool that answers "how far does the
// CDM get today"; ktcdm_native is what the app uses once this passes.
//
//   probe.exe <libwidevinecdm.so>
#include "winelf.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>


// Entry points exported by the CDM, typed with the calling convention the binary actually uses.
typedef void (__attribute__((sysv_abi)) *linux_init_module)(void);
typedef void* (__attribute__((sysv_abi)) *linux_create_instance)(int version, const char* key_system,
                                                                 unsigned key_system_size,
                                                                 void* get_host, void* user_data);

int main(int argc, char** argv) {
    setvbuf(stdout, NULL, _IONBF, 0);
    setvbuf(stderr, NULL, _IONBF, 0);
    if (argc < 2) {
        fprintf(stderr, "usage: %s <libwidevinecdm.so> [--no-create]\n", argv[0]);
        return 2;
    }
    const int create = !(argc > 2 && strcmp(argv[2], "--no-create") == 0);

    const char* trace = getenv("KT_TRACE");
    if (trace && strcmp(trace, "0") == 0) winelf_set_trace_enabled(0);
    const char* verbose = getenv("KT_LOG");
    if (verbose && strcmp(verbose, "0") != 0) shim_set_trace_verbose(1);

    winelf_install_exception_handler();
    // The TCB has to exist before any relocated code runs: initialisers already read fs:[0x28].
    winelf_tls_install_for_current_thread();
    fprintf(stderr, "[probe] shim has %d symbols, TCB %.16s\n", shim_count(), "installed");

    WinElfImage image;
    char error[512];
    if (winelf_load(argv[1], &image, error, sizeof(error)) != 0) {
        fprintf(stderr, "[probe] load failed: %s\n", error);
        return 3;
    }
    // The TLS image is known only after parsing the file; rebuild main's TCB so the module's
    // variables start from their initialised values.
    winelf_tls_install_for_current_thread();
    fprintf(stderr, "[probe] mapped %.1f MiB at %p, %llu relative relocs, %llu symbol relocs, "
                    "%llu unresolved\n",
            (double)image.span / (1024.0 * 1024.0), (void*)image.base,
            (unsigned long long)image.relative_relocs, (unsigned long long)image.symbol_relocs,
            (unsigned long long)image.unresolved);
    fprintf(stderr, "[probe] TLS va=0x%llx filesz=%llu memsz=%llu align=%llu\n",
            (unsigned long long)image.tls_vaddr, (unsigned long long)image.tls_filesz,
            (unsigned long long)image.tls_memsz, (unsigned long long)image.tls_align);
    fprintf(stderr, "[probe] DT_INIT=0x%llx DT_INIT_ARRAY=0x%llx size=%llu (%llu entries)\n",
            (unsigned long long)image.init_fn, (unsigned long long)image.init_array,
            (unsigned long long)image.init_arraysz,
            (unsigned long long)(image.init_arraysz / 8));

    linux_init_module initialize_module = (linux_init_module)winelf_symbol(&image, "InitializeCdmModule_4");
    linux_create_instance create_instance =
        (linux_create_instance)winelf_symbol(&image, "CreateCdmInstance");
    fprintf(stderr, "[probe] InitializeCdmModule_4=%p CreateCdmInstance=%p\n",
            (void*)initialize_module, (void*)create_instance);
    if (!initialize_module || !create_instance) {
        fprintf(stderr, "[probe] the library does not export the CDM entry points\n");
        return 4;
    }

    winelf_run_initializers(&image);
    fprintf(stderr, "[probe] initialisers done, calling InitializeCdmModule_4\n");
    initialize_module();
    fprintf(stderr, "[probe] InitializeCdmModule_4 returned\n");

    if (!create) return 0;

    fprintf(stderr, "[probe] calling CreateCdmInstance\n");
    const char* key_system = "com.widevine.alpha";
    void* instance = create_instance(11, key_system, (unsigned)strlen(key_system), NULL, NULL);
    fprintf(stderr, "[probe] CreateCdmInstance returned %p\n", instance);
    return instance ? 0 : 5;
}
