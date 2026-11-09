// printf-family bridges from the System V convention the CDM uses to the Microsoft one ucrt speaks.
//
// A variadic function cannot simply be declared `sysv_abi` here: MinGW's GCC builds a `va_list`
// for the Windows convention, and reading a System V argument list with it dereferences whatever
// the register-save area happened to contain (measured: an access violation inside the shim's own
// va_arg). So the entry points take their arguments as fixed parameters — the first six then arrive
// in the same registers either convention uses — and rebuild the contiguous argument block ucrt
// expects. The `__isoc99_v*` variants receive a real System V `va_list` and are translated by
// walking the format string.
#include "winelf.h"

#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <ctype.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define SYSV __attribute__((sysv_abi))

// The System V AMD64 argument list, as passed by the CDM: offsets into a register save area, then
// a pointer into the caller's stack for everything that did not fit in registers.
typedef struct {
    uint32_t gp_offset;   // next general-purpose register, 0..48
    uint32_t fp_offset;   // next vector register, 48..176
    void* overflow_arg_area;
    void* register_save_area;
} SysvVaList;

// Microsoft's x64 va_list is a plain pointer that advances eight bytes per argument; the caller's
// register arguments are simply contiguous with its stack arguments. An argument block of eight
// slots is therefore equivalent to one.
typedef struct { uint64_t slot[16]; } ArgumentBlock;

static const uint64_t* take_register(SysvVaList* list) {
    if (list->gp_offset < 48) {
        const uint64_t* value = (const uint64_t*)((const char*)list->register_save_area +
                                                  list->gp_offset);
        list->gp_offset += 8;
        return value;
    }
    const uint64_t* value = (const uint64_t*)list->overflow_arg_area;
    list->overflow_arg_area = (char*)list->overflow_arg_area + 8;
    return value;
}

static void take_vector(SysvVaList* list, uint64_t* out) {
    if (list->fp_offset < 176) {
        const char* value = (const char*)list->register_save_area + list->fp_offset;
        list->fp_offset += 16;
        memcpy(out, value, 8);
        return;
    }
    memcpy(out, list->overflow_arg_area, 8);
    list->overflow_arg_area = (char*)list->overflow_arg_area + 8;
}

// Walks a format string and copies the arguments it consumes, in order, into the block ucrt will
// read. Conversions from the vector registers are duplicated into their slot the way the Microsoft
// convention requires, so a `%f` lands as the eight bytes of the double.
static void build_argument_block(ArgumentBlock* block, const char* format, SysvVaList* list) {
    memset(block, 0, sizeof(*block));
    unsigned used = 0;
    const char* at = format;
    while (*at && used < 16) {
        if (*at++ != '%') continue;
        if (*at == '%') { at++; continue; }
        while (*at && strchr("-+ #0'", *at)) at++;
        if (*at == '*') {
            if (used < 16) block->slot[used++] = *take_register(list);
            at++;
        } else {
            while (*at && isdigit((unsigned char)*at)) at++;
        }
        if (*at == '.') {
            at++;
            if (*at == '*') {
                if (used < 16) block->slot[used++] = *take_register(list);
                at++;
            } else {
                while (*at && isdigit((unsigned char)*at)) at++;
            }
        }
        while (*at && strchr("hljztL", *at)) at++;
        const char conversion = *at ? *at++ : 0;
        if (!conversion) break;
        if (strchr("fFeEgGaA", conversion)) {
            uint64_t bits = 0;
            take_vector(list, &bits);
            if (used < 16) block->slot[used++] = bits;
        } else {
            if (used < 16) block->slot[used++] = *take_register(list);
        }
    }
}

extern "C" {

// Bring-up switch: KT_NOLOG=1 drops the CDM's own log lines, which isolates a crash inside the
// formatting bridge from a crash in the CDM.
static int log_suppressed(void) {
    static int suppressed = -1;
    if (suppressed < 0) {
        const char* value = getenv("KT_NOLOG");
        suppressed = value && *value && strcmp(value, "0") != 0;
    }
    return suppressed != 0;
}

// fprintf / printf / snprintf / sprintf / sscanf, called by the CDM with System V arguments.
int sysv_fprintf(void* stream, const char* format, uint64_t a1, uint64_t a2, uint64_t a3,
                 uint64_t a4, uint64_t a5, uint64_t a6) __attribute__((sysv_abi));
int sysv_fprintf(void* stream, const char* format, uint64_t a1, uint64_t a2, uint64_t a3,
                 uint64_t a4, uint64_t a5, uint64_t a6) {
    if (log_suppressed()) return 0;
    ArgumentBlock block;
    block.slot[0] = a1; block.slot[1] = a2; block.slot[2] = a3;
    block.slot[3] = a4; block.slot[4] = a5; block.slot[5] = a6;
    return vfprintf(stream ? (FILE*)stream : stderr, format, (va_list)&block);
}

int sysv_printf(const char* format, uint64_t a1, uint64_t a2, uint64_t a3, uint64_t a4,
                uint64_t a5, uint64_t a6) __attribute__((sysv_abi));
int sysv_printf(const char* format, uint64_t a1, uint64_t a2, uint64_t a3, uint64_t a4,
                uint64_t a5, uint64_t a6) {
    ArgumentBlock block;
    block.slot[0] = a1; block.slot[1] = a2; block.slot[2] = a3;
    block.slot[3] = a4; block.slot[4] = a5; block.slot[5] = a6;
    return vfprintf(stdout, format, (va_list)&block);
}

int sysv_snprintf(char* buffer, size_t size, const char* format, uint64_t a1, uint64_t a2,
                  uint64_t a3, uint64_t a4, uint64_t a5, uint64_t a6) __attribute__((sysv_abi));
int sysv_snprintf(char* buffer, size_t size, const char* format, uint64_t a1, uint64_t a2,
                  uint64_t a3, uint64_t a4, uint64_t a5, uint64_t a6) {
    ArgumentBlock block;
    block.slot[0] = a1; block.slot[1] = a2; block.slot[2] = a3;
    block.slot[3] = a4; block.slot[4] = a5; block.slot[5] = a6;
    return vsnprintf(buffer, size, format, (va_list)&block);
}

int sysv_sprintf(char* buffer, const char* format, uint64_t a1, uint64_t a2, uint64_t a3,
                 uint64_t a4, uint64_t a5, uint64_t a6) __attribute__((sysv_abi));
int sysv_sprintf(char* buffer, const char* format, uint64_t a1, uint64_t a2, uint64_t a3,
                 uint64_t a4, uint64_t a5, uint64_t a6) {
    ArgumentBlock block;
    block.slot[0] = a1; block.slot[1] = a2; block.slot[2] = a3;
    block.slot[3] = a4; block.slot[4] = a5; block.slot[5] = a6;
    return vsnprintf(buffer, (size_t)-1, format, (va_list)&block);
}

int sysv_sscanf(const char* input, const char* format, uint64_t a1, uint64_t a2, uint64_t a3,
                uint64_t a4, uint64_t a5, uint64_t a6) __attribute__((sysv_abi));
int sysv_sscanf(const char* input, const char* format, uint64_t a1, uint64_t a2, uint64_t a3,
                uint64_t a4, uint64_t a5, uint64_t a6) {
    ArgumentBlock block;
    block.slot[0] = a1; block.slot[1] = a2; block.slot[2] = a3;
    block.slot[3] = a4; block.slot[4] = a5; block.slot[5] = a6;
    return vsscanf(input, format, (va_list)&block);
}

// The fortified wrappers ucrt's names do not cover.
int sysv_snprintf_chk(char* buffer, size_t size, int flag, size_t dest_size, const char* format,
                      uint64_t a1, uint64_t a2, uint64_t a3, uint64_t a4, uint64_t a5,
                      uint64_t a6) __attribute__((sysv_abi));
int sysv_snprintf_chk(char* buffer, size_t size, int flag, size_t dest_size, const char* format,
                      uint64_t a1, uint64_t a2, uint64_t a3, uint64_t a4, uint64_t a5,
                      uint64_t a6) {
    (void)flag;
    (void)dest_size;
    ArgumentBlock block;
    block.slot[0] = a1; block.slot[1] = a2; block.slot[2] = a3;
    block.slot[3] = a4; block.slot[4] = a5; block.slot[5] = a6;
    return vsnprintf(buffer, size, format, (va_list)&block);
}

int sysv_sprintf_chk(char* buffer, int flag, size_t dest_size, const char* format, uint64_t a1,
                     uint64_t a2, uint64_t a3, uint64_t a4, uint64_t a5, uint64_t a6)
    __attribute__((sysv_abi));
int sysv_sprintf_chk(char* buffer, int flag, size_t dest_size, const char* format, uint64_t a1,
                     uint64_t a2, uint64_t a3, uint64_t a4, uint64_t a5, uint64_t a6) {
    (void)flag;
    (void)dest_size;
    ArgumentBlock block;
    block.slot[0] = a1; block.slot[1] = a2; block.slot[2] = a3;
    block.slot[3] = a4; block.slot[4] = a5; block.slot[5] = a6;
    return vsnprintf(buffer, (size_t)-1, format, (va_list)&block);
}

// The v* entry points: the caller hands over a System V argument list, which has to be translated.
int sysv_vfprintf(void* stream, const char* format, SysvVaList* list) __attribute__((sysv_abi));
int sysv_vfprintf(void* stream, const char* format, SysvVaList* list) {
    ArgumentBlock block;
    SysvVaList copy = *list;
    build_argument_block(&block, format, &copy);
    return vfprintf(stream ? (FILE*)stream : stderr, format, (va_list)&block);
}

int sysv_vsnprintf(char* buffer, size_t size, const char* format, SysvVaList* list)
    __attribute__((sysv_abi));
int sysv_vsnprintf(char* buffer, size_t size, const char* format, SysvVaList* list) {
    ArgumentBlock block;
    SysvVaList copy = *list;
    build_argument_block(&block, format, &copy);
    return vsnprintf(buffer, size, format, (va_list)&block);
}

int sysv_vsnprintf_chk(char* buffer, size_t size, int flag, size_t dest_size, const char* format,
                       SysvVaList* list) __attribute__((sysv_abi));
int sysv_vsnprintf_chk(char* buffer, size_t size, int flag, size_t dest_size, const char* format,
                       SysvVaList* list) {
    (void)flag;
    (void)dest_size;
    ArgumentBlock block;
    SysvVaList copy = *list;
    build_argument_block(&block, format, &copy);
    return vsnprintf(buffer, size, format, (va_list)&block);
}

int sysv_vsscanf(const char* input, const char* format, SysvVaList* list)
    __attribute__((sysv_abi));
int sysv_vsscanf(const char* input, const char* format, SysvVaList* list) {
    ArgumentBlock block;
    SysvVaList copy = *list;
    build_argument_block(&block, format, &copy);
    return vsscanf(input, format, (va_list)&block);
}

int sysv_vasprintf(char** out, const char* format, SysvVaList* list) __attribute__((sysv_abi));
int sysv_vasprintf(char** out, const char* format, SysvVaList* list) {
    if (!out) return -1;
    ArgumentBlock block;
    SysvVaList copy = *list;
    build_argument_block(&block, format, &copy);
    // Format once to size, once to fill: vsnprintf consumes the block, so rebuild it.
    ArgumentBlock probe;
    SysvVaList probe_list = *list;
    build_argument_block(&probe, format, &probe_list);
    int length = vsnprintf(NULL, 0, format, (va_list)&probe);
    if (length < 0) { *out = NULL; return -1; }
    char* buffer = (char*)malloc((size_t)length + 1);
    if (!buffer) { *out = NULL; return -1; }
    vsnprintf(buffer, (size_t)length + 1, format, (va_list)&block);
    *out = buffer;
    return length;
}

}  // extern "C"
