#define _GNU_SOURCE
#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <dlfcn.h>

#define VK_SUCCESS 0
#define VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO 2
#define VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO 3
#define VK_STRUCTURE_TYPE_DEVICE_QUEUE_GLOBAL_PRIORITY_CREATE_INFO_KHR 1000174000
#define VK_QUEUE_GLOBAL_PRIORITY_LOW_KHR 128

typedef void* VkInstance;
typedef void* VkPhysicalDevice;
typedef void* VkDevice;
typedef int32_t VkResult;
typedef void (*PFN_vkVoidFunction)(void);

typedef struct {
    int32_t sType;
    void* pNext;
    uint32_t globalPriority;
} VkDeviceQueueGlobalPriorityCreateInfoKHR;

typedef struct {
    int32_t sType;
    void* pNext;
    uint32_t flags;
    uint32_t queueFamilyIndex;
    uint32_t queueCount;
    const float* pQueuePriorities;
} VkDeviceQueueCreateInfo;

typedef struct {
    int32_t sType;
    const void* pNext;
    uint32_t flags;
    uint32_t queueCreateInfoCount;
    const VkDeviceQueueCreateInfo* pQueueCreateInfos;
    uint32_t enabledLayerCount;
    const char* const* ppEnabledLayerNames;
    uint32_t enabledExtensionCount;
    const char* const* ppEnabledExtensionNames;
    const void* pEnabledFeatures;
} VkDeviceCreateInfo;

typedef VkResult (*PFN_vkCreateDevice)(VkPhysicalDevice, const VkDeviceCreateInfo*, const void*, VkDevice*);
typedef PFN_vkVoidFunction (*PFN_vkGetInstanceProcAddr)(VkInstance, const char*);

static PFN_vkGetInstanceProcAddr real_gipa = NULL;
static PFN_vkCreateDevice real_create_dev_from_gipa = NULL;
static void* vulkan_lib_handle = NULL;

static void init_vulkan_handle() {
    if (vulkan_lib_handle && real_gipa) return;

    // Try RTLD_NEXT first
    real_gipa = (PFN_vkGetInstanceProcAddr)dlsym(RTLD_NEXT, "vkGetInstanceProcAddr");
    if (!real_gipa) {
        // Open library explicitly
        const char* paths[] = {
            "libvulkan.so.1",
            "/usr/lib/libvulkan.so.1",
            "/usr/lib64/libvulkan.so.1",
            "/usr/lib/x86_64-linux-gnu/libvulkan.so.1",
            NULL
        };
        for (int i = 0; paths[i]; i++) {
            vulkan_lib_handle = dlopen(paths[i], RTLD_NOW | RTLD_LOCAL);
            if (vulkan_lib_handle) {
                real_gipa = (PFN_vkGetInstanceProcAddr)dlsym(vulkan_lib_handle, "vkGetInstanceProcAddr");
                if (real_gipa) break;
            }
        }
    }
}

__attribute__((visibility("default")))
VkResult vkCreateDevice(VkPhysicalDevice physicalDevice, const VkDeviceCreateInfo* pCreateInfo, const void* pAllocator, VkDevice* pDevice) {
    if (!real_create_dev_from_gipa) {
        init_vulkan_handle();
        real_create_dev_from_gipa = (PFN_vkCreateDevice)dlsym(RTLD_NEXT, "vkCreateDevice");
        if (!real_create_dev_from_gipa && vulkan_lib_handle) {
            real_create_dev_from_gipa = (PFN_vkCreateDevice)dlsym(vulkan_lib_handle, "vkCreateDevice");
        }
    }

    if (!real_create_dev_from_gipa) {
        fprintf(stderr, "[KittyTune GPU Pacer] ERROR: Cannot find underlying vkCreateDevice symbol!\n");
        return -1;
    }

    if (!pCreateInfo) {
        return real_create_dev_from_gipa(physicalDevice, pCreateInfo, pAllocator, pDevice);
    }

    uint32_t qCount = pCreateInfo->queueCreateInfoCount;
    VkDeviceQueueCreateInfo* newQInfos = (VkDeviceQueueCreateInfo*)malloc(sizeof(VkDeviceQueueCreateInfo) * qCount);
    VkDeviceQueueGlobalPriorityCreateInfoKHR* prioInfos = (VkDeviceQueueGlobalPriorityCreateInfoKHR*)malloc(sizeof(VkDeviceQueueGlobalPriorityCreateInfoKHR) * qCount);

    for (uint32_t i = 0; i < qCount; i++) {
        newQInfos[i] = pCreateInfo->pQueueCreateInfos[i];
        prioInfos[i].sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_GLOBAL_PRIORITY_CREATE_INFO_KHR;
        prioInfos[i].pNext = (void*)newQInfos[i].pNext;
        prioInfos[i].globalPriority = VK_QUEUE_GLOBAL_PRIORITY_LOW_KHR;
        newQInfos[i].pNext = &prioInfos[i];
    }

    uint32_t extCount = pCreateInfo->enabledExtensionCount;
    int hasExt = 0;
    for (uint32_t i = 0; i < extCount; i++) {
        if (pCreateInfo->ppEnabledExtensionNames[i] &&
            (strcmp(pCreateInfo->ppEnabledExtensionNames[i], "VK_KHR_global_priority") == 0 ||
             strcmp(pCreateInfo->ppEnabledExtensionNames[i], "VK_EXT_global_priority") == 0)) {
            hasExt = 1;
            break;
        }
    }

    const char** newExts = (const char**)malloc(sizeof(char*) * (extCount + 2));
    for (uint32_t i = 0; i < extCount; i++) {
        newExts[i] = pCreateInfo->ppEnabledExtensionNames[i];
    }
    if (!hasExt) {
        newExts[extCount] = "VK_KHR_global_priority";
        extCount++;
    }

    VkDeviceCreateInfo modifiedCreateInfo = *pCreateInfo;
    modifiedCreateInfo.pQueueCreateInfos = newQInfos;
    modifiedCreateInfo.enabledExtensionCount = extCount;
    modifiedCreateInfo.ppEnabledExtensionNames = newExts;

    VkResult res = real_create_dev_from_gipa(physicalDevice, &modifiedCreateInfo, pAllocator, pDevice);
    if (res == VK_SUCCESS) {
        fprintf(stderr, "[KittyTune GPU Pacer] SUCCESS: Device created with VK_QUEUE_GLOBAL_PRIORITY_LOW_KHR (exit 0)!\n");
    } else {
        fprintf(stderr, "[KittyTune GPU Pacer] Global priority creation returned %d; retrying with standard priority...\n", res);
        res = real_create_dev_from_gipa(physicalDevice, pCreateInfo, pAllocator, pDevice);
        fprintf(stderr, "[KittyTune GPU Pacer] Standard priority fallback result: %d\n", res);
    }

    free(newQInfos);
    free(prioInfos);
    free(newExts);
    return res;
}

__attribute__((visibility("default")))
PFN_vkVoidFunction vkGetInstanceProcAddr(VkInstance instance, const char* pName) {
    init_vulkan_handle();

    if (pName && strcmp(pName, "vkCreateDevice") == 0) {
        if (instance && real_gipa) {
            PFN_vkCreateDevice f = (PFN_vkCreateDevice)real_gipa(instance, "vkCreateDevice");
            if (f) {
                real_create_dev_from_gipa = f;
            }
        }
        return (PFN_vkVoidFunction)vkCreateDevice;
    }

    if (real_gipa) {
        return real_gipa(instance, pName);
    }
    return NULL;
}
