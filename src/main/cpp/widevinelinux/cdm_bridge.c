// Drives a Linux libwidevinecdm.so loaded by winelf, on Windows, through the public CDM API.
//
// The bridge mirrors `widevine/WidevineBridge.cpp` call for call, and exposes the same C ABI, so
// `cdmsidecar/CdmSidecar.cpp` can be compiled against either one without changes. What differs is
// the convention: every call into the CDM is System V, and every callback the CDM makes is System V
// too. C++ virtual dispatch cannot be told which convention to use, so the two object types the CDM
// touches — the host and the buffer — are plain structs whose first field is a hand-built vtable,
// and each slot is a standalone function taking the object pointer as its first argument.
//
// Object layout follows the Itanium ABI the Linux binary was built with:
//   vtable[0] offset-to-top, vtable[1] typeinfo, then the virtual functions in declaration order,
//   and the destructor's two slots last (a destructor declared last in the class lands last anyway).
#include "winelf.h"

#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <intrin.h>

#include <atomic>
#include <chrono>
#include <condition_variable>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <random>
#include <string>
#include <thread>
#include <vector>

#include "content_decryption_module.h"

#define SYSV __attribute__((sysv_abi))

namespace {

using cdm::InputBuffer_2;
using cdm::SubsampleEntry;

// ---------------------------------------------------------------------------------------------
// Buffers handed to the CDM when it asks the host for memory
// ---------------------------------------------------------------------------------------------

struct HostBuffer;

typedef void (SYSV *buffer_destroy_fn)(HostBuffer*);
typedef uint32_t (SYSV *buffer_capacity_fn)(const HostBuffer*);
typedef uint8_t* (SYSV *buffer_data_fn)(HostBuffer*);
typedef void (SYSV *buffer_set_size_fn)(HostBuffer*, uint32_t);
typedef uint32_t (SYSV *buffer_size_fn)(const HostBuffer*);

struct HostBuffer {
    void** vtable;
    uint8_t* memory;
    uint32_t capacity;
    uint32_t size;
    uint32_t owns_memory;
};

SYSV void buffer_destroy(HostBuffer* buffer) {
    if (!buffer) return;
    if (buffer->owns_memory) free(buffer->memory);
    free(buffer);
}
SYSV uint32_t buffer_capacity(const HostBuffer* buffer) { return buffer ? buffer->capacity : 0; }
SYSV uint8_t* buffer_data(HostBuffer* buffer) { return buffer ? buffer->memory : nullptr; }
SYSV void buffer_set_size(HostBuffer* buffer, uint32_t size) {
    if (buffer && size <= buffer->capacity) buffer->size = size;
}
SYSV uint32_t buffer_size(const HostBuffer* buffer) { return buffer ? buffer->size : 0; }
SYSV void buffer_noop(HostBuffer*) {}

void* g_buffer_vtable[] = {
    (void*)buffer_destroy,                                // Destroy
    (void*)buffer_capacity,                               // Capacity
    (void*)buffer_data,                                   // Data
    (void*)buffer_set_size,                               // SetSize
    (void*)buffer_size,                                   // Size
    (void*)buffer_noop,                                   // deleting destructor
    (void*)buffer_noop,                                   // complete destructor
};

HostBuffer* make_buffer(uint32_t capacity) {
    HostBuffer* buffer = (HostBuffer*)calloc(1, sizeof(HostBuffer));
    if (!buffer) return nullptr;
    buffer->memory = (uint8_t*)malloc(capacity ? capacity : 1);
    if (!buffer->memory) { free(buffer); return nullptr; }
    buffer->capacity = capacity;
    buffer->size = capacity;
    buffer->owns_memory = 1;
    buffer->vtable = g_buffer_vtable;
    return buffer;
}

// ---------------------------------------------------------------------------------------------
// The object the CDM fills with decrypted samples. The CDM calls SetDecryptedBuffer and
// SetTimestamp on it, then returns from Decrypt().
// ---------------------------------------------------------------------------------------------

struct HostDecryptedBlock {
    void** vtable;
    HostBuffer* buffer;
    int64_t timestamp;
};

SYSV void block_set_buffer(HostDecryptedBlock* block, HostBuffer* buffer) {
    if (block) block->buffer = buffer;
}
SYSV HostBuffer* block_get_buffer(HostDecryptedBlock* block) { return block ? block->buffer : nullptr; }
SYSV void block_set_timestamp(HostDecryptedBlock* block, int64_t timestamp) {
    if (block) block->timestamp = timestamp;
}
SYSV int64_t block_get_timestamp(HostDecryptedBlock* block) { return block ? block->timestamp : 0; }
SYSV void block_noop(HostDecryptedBlock*) {}

void* g_block_vtable[] = {
    (void*)block_set_buffer,      // SetDecryptedBuffer
    (void*)block_get_buffer,      // DecryptedBuffer
    (void*)block_set_timestamp,   // SetTimestamp
    (void*)block_get_timestamp,   // Timestamp
    (void*)block_noop,            // deleting destructor
    (void*)block_noop,            // complete destructor
};


// Set KITTYTUNE_WIDEVINE_DEBUG=1 to see every host callback the CDM makes, in order.
static bool host_debug() {
    static int enabled = -1;
    if (enabled < 0) {
        const char* value = getenv("KITTYTUNE_WIDEVINE_DEBUG");
        enabled = value && *value && strcmp(value, "0") != 0;
    }
    return enabled != 0;
}


static void host_callsite(const char* name, void* self, void* caller) {
    if (!host_debug()) return;
    char where[256];
    winelf_describe_address(caller, where, sizeof(where));
    void** vtable = self ? *(void***)self : nullptr;
    fprintf(stderr, "[host] %-28s self=%p vtable=%p caller=%s\n", name, self, (void*)vtable, where);
}

// ---------------------------------------------------------------------------------------------
// The bridge state
// ---------------------------------------------------------------------------------------------

struct Bridge;

struct HostObject {
    void** vtable;
    Bridge* bridge;
};

struct Bridge {
    void* cdm = nullptr;          // ContentDecryptionModule_11*
    void** cdm_vtable = nullptr;
    HostObject* host = nullptr;

    std::mutex mutex;
    std::condition_variable condition;

    bool initialized = false;
    bool initialized_ok = false;
    bool license_requested = false;
    bool keys_changed = false;
    bool keys_usable = false;
    bool rejected = false;
    bool promise_settled = false;
    bool promise_ok = false;
    std::string session_id;
    std::string error;
    std::vector<uint8_t> license_request;
    std::vector<uint8_t> key_id;
    std::vector<uint8_t> pending_license;

    void notify() { condition.notify_all(); }
};

typedef void (SYSV *cdm_initialize_fn)(void*, int, int, int);
typedef void (SYSV *cdm_generate_request_fn)(void*, uint32_t, int, int, const uint8_t*, uint32_t);
typedef void (SYSV *cdm_update_session_fn)(void*, uint32_t, const char*, uint32_t, const uint8_t*,
                                           uint32_t);
typedef void (SYSV *cdm_close_session_fn)(void*, uint32_t, const char*, uint32_t);
typedef void (SYSV *cdm_set_certificate_fn)(void*, uint32_t, const uint8_t*, uint32_t);
typedef int (SYSV *cdm_decrypt_fn)(void*, const InputBuffer_2*, void*);
typedef void (SYSV *cdm_on_storage_id_fn)(void*, uint32_t, const uint8_t*, uint32_t);
typedef void (SYSV *cdm_timer_expired_fn)(void*, void*);
typedef void (SYSV *cdm_destroy_fn)(void*);

// The pointer the CDM stores in its object is the address point of its virtual table — the first
// virtual function. Slot 0 is Initialize, not offset-to-top.
cdm_initialize_fn cdm_initialize(Bridge* bridge) {
    return (cdm_initialize_fn)bridge->cdm_vtable[0];
}
cdm_set_certificate_fn cdm_set_certificate(Bridge* bridge) {
    return (cdm_set_certificate_fn)bridge->cdm_vtable[2];
}
cdm_generate_request_fn cdm_generate_request(Bridge* bridge) {
    return (cdm_generate_request_fn)bridge->cdm_vtable[3];
}
cdm_update_session_fn cdm_update_session(Bridge* bridge) {
    return (cdm_update_session_fn)bridge->cdm_vtable[5];
}
cdm_close_session_fn cdm_close_session(Bridge* bridge) {
    return (cdm_close_session_fn)bridge->cdm_vtable[6];
}
cdm_timer_expired_fn cdm_timer_expired(Bridge* bridge) {
    return (cdm_timer_expired_fn)bridge->cdm_vtable[8];
}
cdm_decrypt_fn cdm_decrypt(Bridge* bridge) { return (cdm_decrypt_fn)bridge->cdm_vtable[9]; }
cdm_on_storage_id_fn cdm_on_storage_id(Bridge* bridge) {
    return (cdm_on_storage_id_fn)bridge->cdm_vtable[18];
}
cdm_destroy_fn cdm_destroy(Bridge* bridge) { return (cdm_destroy_fn)bridge->cdm_vtable[19]; }

// ---------------------------------------------------------------------------------------------
// Host callbacks (Host_11). Slots are in declaration order starting at index 2.
// ---------------------------------------------------------------------------------------------

SYSV HostBuffer* host_allocate(HostObject* self, uint32_t capacity);
SYSV void host_set_timer(HostObject* self, int64_t delay_ms, void* context);
SYSV double host_wall_time(HostObject* self);
SYSV void host_on_initialized(HostObject* self, int success);
SYSV void host_on_resolve_key_status(HostObject* self, uint32_t promise_id, int status);
SYSV void host_on_resolve_new_session(HostObject* self, uint32_t promise_id, const char* id,
                                      uint32_t id_size);
SYSV void host_on_resolve_promise(HostObject* self, uint32_t promise_id);
SYSV void host_on_reject_promise(HostObject* self, uint32_t promise_id, int exception,
                                 uint32_t system_code, const char* message, uint32_t message_size);
SYSV void host_on_session_message(HostObject* self, const char* id, uint32_t id_size, int type,
                                  const char* message, uint32_t message_size);
SYSV void host_on_session_keys_change(HostObject* self, const char* id, uint32_t id_size,
                                      int has_additional_usable_key,
                                      const cdm::KeyInformation* keys_info,
                                      uint32_t keys_info_count);
SYSV void host_on_expiration_change(HostObject* self, const char* id, uint32_t id_size,
                                    double expiry);
SYSV void host_on_session_closed(HostObject* self, const char* id, uint32_t id_size);
SYSV void host_send_platform_challenge(HostObject* self, const char* service_id,
                                       uint32_t service_id_size, const char* challenge,
                                       uint32_t challenge_size);
SYSV void host_enable_output_protection(HostObject* self, uint32_t mask);
SYSV void host_query_output_protection(HostObject* self);
SYSV void host_on_deferred_initialization(HostObject* self, int stream_type, int status);
SYSV void* host_create_file_io(HostObject* self, void* client);
SYSV void host_request_storage_id(HostObject* self, uint32_t version);
SYSV void host_report_metrics(HostObject* self, int metric, uint64_t value);
SYSV void host_noop(HostObject*) {}

void* g_host_vtable[] = {
    (void*)host_allocate,                           // 0  Allocate
    (void*)host_set_timer,                          // 1  SetTimer
    (void*)host_wall_time,                          // 2  GetCurrentWallTime
    (void*)host_on_initialized,                     // 3  OnInitialized
    (void*)host_on_resolve_key_status,              // 4  OnResolveKeyStatusPromise
    (void*)host_on_resolve_new_session,             // 5  OnResolveNewSessionPromise
    (void*)host_on_resolve_promise,                 // 6  OnResolvePromise
    (void*)host_on_reject_promise,                  // 7  OnRejectPromise
    (void*)host_on_session_message,                 // 8  OnSessionMessage
    (void*)host_on_session_keys_change,             // 9  OnSessionKeysChange
    (void*)host_on_expiration_change,               // 10 OnExpirationChange
    (void*)host_on_session_closed,                  // 11 OnSessionClosed
    (void*)host_send_platform_challenge,            // 12 SendPlatformChallenge
    (void*)host_enable_output_protection,           // 13 EnableOutputProtection
    (void*)host_query_output_protection,            // 14 QueryOutputProtectionStatus
    (void*)host_on_deferred_initialization,         // 15 OnDeferredInitializationDone
    (void*)host_create_file_io,                     // 16 CreateFileIO
    (void*)host_request_storage_id,                 // 17 RequestStorageId
    (void*)host_report_metrics,                     // 18 ReportMetrics
    (void*)host_noop,                               // 19 complete destructor
    (void*)host_noop,                               // 20 deleting destructor
};


// Bring-up aid: a second host vtable whose slots announce themselves. Selecting it with
// KT_SLOT_PROBE=1 shows exactly which slot the CDM called and with which raw arguments.
static void slot_report(int index, uint64_t a, uint64_t b, uint64_t c, uint64_t d,
                        void* caller) {
    char where[256];
    winelf_describe_address(caller, where, sizeof(where));
    fprintf(stderr, "[slot] %d args=%llx %llx %llx %llx from %s\n", index,
            (unsigned long long)a, (unsigned long long)b, (unsigned long long)c,
            (unsigned long long)d, where);
}

SYSV uint64_t slot_0(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(0, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_1(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(1, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_2(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(2, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_3(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(3, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_4(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(4, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_5(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(5, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_6(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(6, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_7(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(7, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_8(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(8, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_9(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(9, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_10(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(10, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_11(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(11, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_12(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(12, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_13(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(13, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_14(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(14, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_15(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(15, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_16(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(16, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_17(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(17, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_18(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(18, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_19(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(19, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_20(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(20, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_21(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(21, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_22(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(22, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_23(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(23, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_24(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(24, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_25(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(25, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_26(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(26, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_27(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(27, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_28(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(28, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_29(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(29, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_30(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(30, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_31(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(31, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_32(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(32, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_33(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(33, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_34(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(34, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_35(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(35, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_36(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(36, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_37(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(37, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_38(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(38, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_39(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(39, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_40(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(40, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_41(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(41, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_42(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(42, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_43(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(43, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_44(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(44, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_45(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(45, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_46(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(46, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}
SYSV uint64_t slot_47(HostObject* self, uint64_t a, uint64_t b, uint64_t c, uint64_t d) {
    slot_report(47, a, b, c, d, __builtin_return_address(0));
    (void)self; return 0;
}

void* g_host_vtable_probe[] = {
    (void*)slot_0,
    (void*)slot_1,
    (void*)slot_2,
    (void*)slot_3,
    (void*)slot_4,
    (void*)slot_5,
    (void*)slot_6,
    (void*)slot_7,
    (void*)slot_8,
    (void*)slot_9,
    (void*)slot_10,
    (void*)slot_11,
    (void*)slot_12,
    (void*)slot_13,
    (void*)slot_14,
    (void*)slot_15,
    (void*)slot_16,
    (void*)slot_17,
    (void*)slot_18,
    (void*)slot_19,
    (void*)slot_20,
    (void*)slot_21,
    (void*)slot_22,
    (void*)slot_23,
    (void*)slot_24,
    (void*)slot_25,
    (void*)slot_26,
    (void*)slot_27,
    (void*)slot_28,
    (void*)slot_29,
    (void*)slot_30,
    (void*)slot_31,
    (void*)slot_32,
    (void*)slot_33,
    (void*)slot_34,
    (void*)slot_35,
    (void*)slot_36,
    (void*)slot_37,
    (void*)slot_38,
    (void*)slot_39,
    (void*)slot_40,
    (void*)slot_41,
    (void*)slot_42,
    (void*)slot_43,
    (void*)slot_44,
    (void*)slot_45,
    (void*)slot_46,
    (void*)slot_47,
};

SYSV HostBuffer* host_allocate(HostObject* self, uint32_t capacity) {
    host_callsite("Allocate", self, __builtin_return_address(0));
    if (host_debug()) fprintf(stderr, "[host] Allocate(%u)\n", capacity);
    (void)self;
    return make_buffer(capacity);
}

SYSV void host_set_timer(HostObject* self, int64_t delay_ms, void* context) {
    host_callsite("SetTimer", self, __builtin_return_address(0));
    if (host_debug()) fprintf(stderr, "[host] SetTimer(%lld, %p)\n", (long long)delay_ms, context);
    Bridge* bridge = self->bridge;
    if (!bridge || !bridge->cdm) return;
    // The CDM calls this while holding its own lock, so TimerExpired has to come from elsewhere.
    std::thread([bridge, delay_ms, context] {
        std::this_thread::sleep_for(std::chrono::milliseconds(delay_ms > 0 ? delay_ms : 0));
        cdm_timer_expired(bridge)(bridge->cdm, context);
    }).detach();
}

SYSV double host_wall_time(HostObject* self) {
    (void)self;
    using namespace std::chrono;
    return duration<double>(system_clock::now().time_since_epoch()).count();
}

SYSV void host_on_initialized(HostObject* self, int success) {
    host_callsite("OnInitialized", self, __builtin_return_address(0));
    if (host_debug()) fprintf(stderr, "[host] OnInitialized(%d)\n", success);
    Bridge* bridge = self->bridge;
    std::lock_guard<std::mutex> lock(bridge->mutex);
    bridge->initialized = true;
    bridge->initialized_ok = success != 0;
    bridge->notify();
}

SYSV void host_on_resolve_key_status(HostObject* self, uint32_t promise_id, int status) {
    (void)self;
    (void)promise_id;
    (void)status;
}

SYSV void host_on_resolve_new_session(HostObject* self, uint32_t promise_id, const char* id,
                                      uint32_t id_size) {
    host_callsite("OnResolveNewSessionPromise", self, __builtin_return_address(0));
    if (host_debug()) fprintf(stderr, "[host] OnResolveNewSessionPromise(%u, id=%p, size=%u)\n", promise_id, (const void*)id, id_size);
    Bridge* bridge = self->bridge;
    std::lock_guard<std::mutex> lock(bridge->mutex);
    bridge->session_id.assign(id ? id : "", id ? id_size : 0);
    bridge->notify();
}

SYSV void host_on_resolve_promise(HostObject* self, uint32_t promise_id) {
    if (host_debug()) fprintf(stderr, "[host] OnResolvePromise(%u)\n", promise_id);
    (void)promise_id;
    Bridge* bridge = self->bridge;
    std::lock_guard<std::mutex> lock(bridge->mutex);
    bridge->promise_settled = true;
    bridge->promise_ok = true;
    bridge->notify();
}

SYSV void host_on_reject_promise(HostObject* self, uint32_t promise_id, int exception,
                                 uint32_t system_code, const char* message, uint32_t message_size) {
    host_callsite("OnRejectPromise", self, __builtin_return_address(0));
    if (host_debug()) fprintf(stderr, "[host] OnRejectPromise(%u, exception=%d, code=%u, msg=%.*s)\n", promise_id, exception, system_code, (int)message_size, message ? message : "");
    (void)promise_id;
    (void)exception;
    (void)system_code;
    Bridge* bridge = self->bridge;
    std::lock_guard<std::mutex> lock(bridge->mutex);
    bridge->promise_settled = true;
    bridge->promise_ok = false;
    bridge->rejected = true;
    bridge->error.assign(message ? message : "", message ? message_size : 0);
    if (bridge->error.empty()) bridge->error = "the CDM rejected the request";
    bridge->notify();
}

SYSV void host_on_session_message(HostObject* self, const char* id, uint32_t id_size, int type,
                                  const char* message, uint32_t message_size) {
    host_callsite("OnSessionMessage", self, __builtin_return_address(0));
    if (host_debug()) fprintf(stderr, "[host] OnSessionMessage(type=%d, size=%u)\n", type, message_size);
    (void)id;
    (void)id_size;
    Bridge* bridge = self->bridge;
    if (getenv("KITTYTUNE_WIDEVINE_DEBUG"))
        fprintf(stderr, "[ktwv-linux] session message type=%d size=%u\n", type, message_size);
    std::lock_guard<std::mutex> lock(bridge->mutex);
    // kLicenseRequest is 0 in this header (measured: the CDM sends type 0 with the 1759-byte
    // request; treating 1 as the request type silently waited for ever).
    if (type == cdm::MessageType::kLicenseRequest && message && message_size) {
        bridge->license_request.assign((const uint8_t*)message,
                                       (const uint8_t*)message + message_size);
        bridge->license_requested = true;
    }
    bridge->notify();
}

SYSV void host_on_session_keys_change(HostObject* self, const char* id, uint32_t id_size,
                                      int has_additional_usable_key,
                                      const cdm::KeyInformation* keys_info,
                                      uint32_t keys_info_count) {
    if (host_debug()) fprintf(stderr, "[host] OnSessionKeysChange(usable=%d, count=%u)\n", has_additional_usable_key, keys_info_count);
    (void)id;
    (void)id_size;
    Bridge* bridge = self->bridge;
    std::lock_guard<std::mutex> lock(bridge->mutex);
    bridge->keys_changed = true;
    bridge->keys_usable = has_additional_usable_key != 0;
    if (keys_info_count > 0 && keys_info && keys_info[0].key_id_size > 0)
        bridge->key_id.assign(keys_info[0].key_id, keys_info[0].key_id + keys_info[0].key_id_size);
    bridge->notify();
}

SYSV void host_on_expiration_change(HostObject* self, const char* id, uint32_t id_size,
                                    double expiry) {
    (void)self;
    (void)id;
    (void)id_size;
    (void)expiry;
}

SYSV void host_on_session_closed(HostObject* self, const char* id, uint32_t id_size) {
    (void)self;
    (void)id;
    (void)id_size;
}

SYSV void host_send_platform_challenge(HostObject* self, const char* service_id,
                                       uint32_t service_id_size, const char* challenge,
                                       uint32_t challenge_size) {
    (void)self;
    (void)service_id;
    (void)service_id_size;
    (void)challenge;
    (void)challenge_size;
}

SYSV void host_enable_output_protection(HostObject* self, uint32_t mask) {
    (void)self;
    (void)mask;
}

SYSV void host_query_output_protection(HostObject* self) { (void)self; }

SYSV void host_on_deferred_initialization(HostObject* self, int stream_type, int status) {
    (void)self;
    (void)stream_type;
    (void)status;
}

SYSV void* host_create_file_io(HostObject* self, void* client) {
    if (host_debug()) fprintf(stderr, "[host] CreateFileIO(%p)\n", client);
    (void)self;
    (void)client;
    return nullptr;  // persistent state is declared unsupported in Initialize()
}

SYSV void host_request_storage_id(HostObject* self, uint32_t version) {
    if (host_debug()) fprintf(stderr, "[host] RequestStorageId(%u)\n", version);
    Bridge* bridge = self->bridge;
    if (!bridge || !bridge->cdm) return;
    // Gecko answers with an empty id when it has no per-origin salt; matching that keeps the CDM
    // from presenting a persistent identity it cannot actually keep.
    const char* mode = getenv("KITTYTUNE_WIDEVINE_STORAGE_ID");
    static const uint8_t kId[32] = {0x4b, 0x54, 0x2d, 0x6c, 0x69, 0x6e, 0x75, 0x78};
    if (mode && std::string(mode) == "empty") {
        cdm_on_storage_id(bridge)(bridge->cdm, version, nullptr, 0);
        return;
    }
    cdm_on_storage_id(bridge)(bridge->cdm, version, kId, (uint32_t)sizeof(kId));
}

SYSV void host_report_metrics(HostObject* self, int metric, uint64_t value) {
    (void)self;
    (void)metric;
    (void)value;
}

}  // namespace

// ---------------------------------------------------------------------------------------------
// The CDM entry points, called with the Linux convention
// ---------------------------------------------------------------------------------------------

namespace {

typedef void (SYSV *linux_init_module)(void);
typedef void* (SYSV *linux_create_instance)(int version, const char* key_system,
                                            uint32_t key_system_size, void* get_cdm_host,
                                            void* user_data);

SYSV void* get_cdm_host(int host_interface_version, void* user_data) {
    fprintf(stderr, "[host] GetCdmHost(version=%d)\n", host_interface_version);
    Bridge* bridge = (Bridge*)user_data;
    if (!bridge) return nullptr;
    if (host_interface_version != 10 && host_interface_version != 11) {
        fprintf(stderr, "[ktwv-linux] CDM asked for host interface %d, we speak 11\n",
                host_interface_version);
        return nullptr;
    }
    if (!bridge->host) {
        bridge->host = (HostObject*)calloc(1, sizeof(HostObject));
        if (!bridge->host) return nullptr;
        const char* probe_mode = getenv("KT_SLOT_PROBE");
        bridge->host->vtable = (probe_mode && *probe_mode && strcmp(probe_mode, "0") != 0)
                                   ? g_host_vtable_probe
                                   : g_host_vtable;
        bridge->host->bridge = bridge;
    }
    return bridge->host;
}

std::string storage_directory() {
    const char* configured = getenv("KITTYTUNE_WIDEVINE_STORAGE");
    if (configured && *configured) return configured;
    const char* base = getenv("TEMP");
    if (!base || !*base) base = "C:\\Windows\\Temp";
    return std::string(base) + "\\kittytune_cdm";
}

void set_error(char* out, int out_length, const char* message) {
    if (!out || out_length <= 0) return;
    snprintf(out, (size_t)out_length, "%s", message ? message : "unknown error");
}

}  // namespace

extern "C" {

typedef void* KtwvCdm;

KtwvCdm ktwv_create(const char* cdm_path, char* error, int error_length) {
    if (!cdm_path) {
        set_error(error, error_length, "no CDM path");
        return nullptr;
    }
    fprintf(stderr, "[ktwv-linux] loading %s\n", cdm_path);

    winelf_install_exception_handler();
    winelf_tls_install_for_current_thread();

    static WinElfImage image;
    static bool loaded = false;
    if (!loaded) {
        char load_error[512] = {0};
        if (winelf_load(cdm_path, &image, load_error, sizeof(load_error)) != 0) {
            set_error(error, error_length, load_error);
            return nullptr;
        }
        loaded = true;
        // The module's TLS template is known now; rebuild this thread's block from it.
        winelf_tls_install_for_current_thread();
        fprintf(stderr,
                "[ktwv-linux] mapped %.1f MiB at %p (%llu relative relocs, %llu symbol relocs, "
                "%llu unresolved)\n",
                (double)image.span / (1024.0 * 1024.0), (void*)image.base,
                (unsigned long long)image.relative_relocs, (unsigned long long)image.symbol_relocs,
                (unsigned long long)image.unresolved);
        winelf_run_initializers(&image);
    }

    linux_init_module initialize_module =
        (linux_init_module)winelf_symbol(&image, "InitializeCdmModule_4");
    linux_create_instance create_instance =
        (linux_create_instance)winelf_symbol(&image, "CreateCdmInstance");
    if (!initialize_module || !create_instance) {
        set_error(error, error_length, "this library does not export the CDM API");
        return nullptr;
    }
    static bool module_initialized = false;
    if (!module_initialized) {
        initialize_module();
        module_initialized = true;
    }

    Bridge* bridge = new Bridge();
    const char* key_system = "com.widevine.alpha";
    void* instance = create_instance(11, key_system, (uint32_t)strlen(key_system),
                                     (void*)get_cdm_host, bridge);
    if (!instance) {
        delete bridge;
        set_error(error, error_length, "CreateCdmInstance refused interface 11");
        return nullptr;
    }
    bridge->cdm = instance;
    bridge->cdm_vtable = *(void***)instance;

    cdm_initialize(bridge)(bridge->cdm, 0, 0, 0);
    {
        std::unique_lock<std::mutex> lock(bridge->mutex);
        if (!bridge->condition.wait_for(lock, std::chrono::seconds(30),
                                        [bridge] { return bridge->initialized; })) {
            set_error(error, error_length, "the CDM never reported that it was initialised");
            return nullptr;
        }
        if (!bridge->initialized_ok) {
            set_error(error, error_length, "the CDM reported an initialisation failure");
            return nullptr;
        }
    }
    fprintf(stderr, "[ktwv-linux] CDM initialised\n");
    return bridge;
}

void ktwv_destroy(KtwvCdm handle) {
    Bridge* bridge = (Bridge*)handle;
    if (!bridge) return;
    if (bridge->cdm && bridge->cdm_vtable) cdm_destroy(bridge)(bridge->cdm);
    free(bridge->host);
    delete bridge;
}

void ktwv_reset_session(KtwvCdm handle) {
    Bridge* bridge = (Bridge*)handle;
    if (!bridge) return;
    std::string session_id;
    {
        std::lock_guard<std::mutex> lock(bridge->mutex);
        session_id.swap(bridge->session_id);
        bridge->license_requested = false;
        bridge->keys_changed = false;
        bridge->keys_usable = false;
        bridge->rejected = false;
        bridge->error.clear();
        bridge->license_request.clear();
        bridge->key_id.clear();
    }
    if (!session_id.empty() && bridge->cdm)
        cdm_close_session(bridge)(bridge->cdm, 3, session_id.c_str(),
                                  (uint32_t)session_id.size());
}

int ktwv_set_server_certificate(KtwvCdm handle, const uint8_t* certificate, int certificate_length,
                                char* error, int error_length) {
    Bridge* bridge = (Bridge*)handle;
    if (!bridge || !certificate || certificate_length <= 0) {
        set_error(error, error_length, "bad arguments");
        return 0;
    }
    {
        std::lock_guard<std::mutex> lock(bridge->mutex);
        bridge->promise_settled = false;
        bridge->promise_ok = false;
    }
    cdm_set_certificate(bridge)(bridge->cdm, 1000, certificate, (uint32_t)certificate_length);
    std::unique_lock<std::mutex> lock(bridge->mutex);
    if (!bridge->condition.wait_for(lock, std::chrono::seconds(10),
                                    [bridge] { return bridge->promise_settled; })) {
        set_error(error, error_length, "the CDM did not answer the server certificate");
        return 0;
    }
    if (!bridge->promise_ok) {
        set_error(error, error_length, "the CDM rejected the server certificate");
        return 0;
    }
    return 1;
}

int ktwv_license_request(KtwvCdm handle, const uint8_t* pssh, int pssh_length, uint8_t** out,
                         int* out_length, char* error, int error_length) {
    Bridge* bridge = (Bridge*)handle;
    if (!bridge || !pssh || pssh_length <= 0 || !out || !out_length) {
        set_error(error, error_length, "bad arguments");
        return 0;
    }
    // This header's enums are the old ones: kTemporary is 0 here and 1 in current Chromium. The CDM
    // we load is 4.10, so it speaks the old numbering (measured: kTemporary=1 is rejected with
    // kExceptionTypeError, system code 45).
    if (host_debug()) fprintf(stderr, "[ktwv-linux] CreateSessionAndGenerateRequest: %d bytes of PSSH\n", pssh_length);
    cdm_generate_request(bridge)(bridge->cdm, 1, 0 /* kTemporary */, 0 /* kCenc */, pssh,
                                 (uint32_t)pssh_length);
    if (host_debug()) fprintf(stderr, "[ktwv-linux] CreateSessionAndGenerateRequest returned\n");
    std::unique_lock<std::mutex> lock(bridge->mutex);
    if (!bridge->condition.wait_for(lock, std::chrono::seconds(30), [bridge] {
            return bridge->license_requested || bridge->rejected;
        })) {
        set_error(error, error_length, "the CDM produced no license request");
        return 0;
    }
    if (bridge->rejected) {
        set_error(error, error_length, bridge->error.c_str());
        return 0;
    }
    *out = (uint8_t*)malloc(bridge->license_request.size());
    if (!*out) {
        set_error(error, error_length, "out of memory");
        return 0;
    }
    memcpy(*out, bridge->license_request.data(), bridge->license_request.size());
    *out_length = (int)bridge->license_request.size();
    return 1;
}

int ktwv_provide_license(KtwvCdm handle, const uint8_t* license, int license_length, char* error,
                         int error_length) {
    Bridge* bridge = (Bridge*)handle;
    if (!bridge || !license || license_length <= 0) {
        set_error(error, error_length, "bad arguments");
        return 0;
    }
    cdm_update_session(bridge)(bridge->cdm, 2, bridge->session_id.c_str(),
                               (uint32_t)bridge->session_id.size(), license,
                               (uint32_t)license_length);
    std::unique_lock<std::mutex> lock(bridge->mutex);
    if (!bridge->condition.wait_for(lock, std::chrono::seconds(30), [bridge] {
            return bridge->keys_changed || bridge->rejected;
        })) {
        set_error(error, error_length, "the CDM accepted the license but reported no keys");
        return 0;
    }
    if (bridge->rejected) {
        set_error(error, error_length, bridge->error.c_str());
        return 0;
    }
    if (!bridge->keys_usable) {
        set_error(error, error_length, "the license granted keys that are not usable");
        return 0;
    }
    return 1;
}

int ktwv_update(KtwvCdm handle, const uint8_t* message, int message_length, uint8_t** out,
                int* out_length, char* error, int error_length) {
    Bridge* bridge = (Bridge*)handle;
    if (!bridge || !message || message_length <= 0 || !out || !out_length) {
        set_error(error, error_length, "bad arguments");
        return 0;
    }
    *out = nullptr;
    *out_length = 0;
    {
        std::lock_guard<std::mutex> lock(bridge->mutex);
        bridge->license_requested = false;
        bridge->keys_changed = false;
        bridge->rejected = false;
        bridge->license_request.clear();
    }
    cdm_update_session(bridge)(bridge->cdm, 2, bridge->session_id.c_str(),
                               (uint32_t)bridge->session_id.size(), message,
                               (uint32_t)message_length);
    std::unique_lock<std::mutex> lock(bridge->mutex);
    if (!bridge->condition.wait_for(lock, std::chrono::seconds(15), [bridge] {
            return bridge->license_requested || bridge->keys_changed || bridge->rejected;
        })) {
        set_error(error, error_length, "the CDM did not answer");
        return 0;
    }
    if (bridge->rejected) {
        set_error(error, error_length, bridge->error.c_str());
        return 0;
    }
    if (bridge->license_requested && !bridge->license_request.empty()) {
        *out = (uint8_t*)malloc(bridge->license_request.size());
        if (!*out) {
            set_error(error, error_length, "out of memory");
            return 0;
        }
        memcpy(*out, bridge->license_request.data(), bridge->license_request.size());
        *out_length = (int)bridge->license_request.size();
    }
    return 1;
}

int ktwv_decrypt(KtwvCdm handle, const uint8_t* data, int data_length, const uint8_t* iv,
                 int iv_length, const uint32_t* subsamples, int subsample_count,
                 const uint8_t* key_id, int key_id_length, uint8_t** out, int* out_length,
                 char* error, int error_length) {
    Bridge* bridge = (Bridge*)handle;
    if (!bridge || !data || data_length <= 0) {
        set_error(error, error_length, "bad arguments");
        return 0;
    }
    std::vector<SubsampleEntry> entries((size_t)subsample_count);
    for (int i = 0; i < subsample_count; i++) {
        entries[i].clear_bytes = subsamples[i * 2];
        entries[i].cipher_bytes = subsamples[i * 2 + 1];
    }

    InputBuffer_2 input = {};
    input.data = data;
    input.data_size = (uint32_t)data_length;
    input.encryption_scheme = cdm::EncryptionScheme::kCenc;
    input.key_id = key_id;
    input.key_id_size = key_id ? (uint32_t)key_id_length : 0;
    input.iv = iv;
    input.iv_size = (uint32_t)iv_length;
    input.subsamples = entries.empty() ? nullptr : entries.data();
    input.num_subsamples = (uint32_t)entries.size();

    HostDecryptedBlock block = {};
    block.vtable = g_block_vtable;
    int status = cdm_decrypt(bridge)(bridge->cdm, &input, &block);
    if (status != 0 /* kSuccess */) {
        char message[64];
        snprintf(message, sizeof(message), "Decrypt returned status %d", status);
        set_error(error, error_length, message);
        return 0;
    }
    HostBuffer* plain = block.buffer;
    if (!plain) {
        set_error(error, error_length, "Decrypt produced no buffer");
        return 0;
    }
    const uint32_t size = plain->size;
    *out = (uint8_t*)malloc(size ? size : 1);
    if (!*out) {
        set_error(error, error_length, "out of memory");
        return 0;
    }
    if (size) memcpy(*out, plain->memory, size);
    *out_length = (int)size;
    buffer_destroy(plain);
    return 1;
}

void ktwv_free(void* pointer) { free(pointer); }

}  // extern "C"
