# Assembly the CDM needs and C cannot express on Windows.
#
#   shim_udivti3   — 128-bit unsigned division, called by libstdc++ helpers. The System V convention
#                 passes the dividend in rdi:rsi, the divisor in rdx:rcx and returns the quotient in
#                 rax:rdx; that 128-bit register pairing has no Windows equivalent in C.
#   setjmp/longjmp — glibc's jmp_buf layout, saved with the System V registers. ucrt's own setjmp
#                 writes a larger Microsoft-shaped buffer than the CDM reserved.
#   strtold_l   — returns a long double the way Linux does, on the x87 stack. Nothing in the CDM's
#                 hot path uses it; returning zero keeps it callable instead of crashing.

    .text

# ---------------------------------------------------------------------------------------------
# uint64_t[2] shim_udivti3(uint64_t[2] dividend, uint64_t[2] divisor)   (Linux ABI)
# ---------------------------------------------------------------------------------------------
    .globl  shim_udivti3
    .p2align 4
shim_udivti3:
    push    %rbx
    push    %r12
    push    %r13
    # a (lo:hi) = rdi:rsi, b (lo:hi) = rdx:rcx
    mov     %rdx, %r12              # b.lo
    mov     %rcx, %r13              # b.hi
    xor     %r8d, %r8d              # q.lo
    xor     %r9d, %r9d              # q.hi
    xor     %r10d, %r10d            # rem.lo
    xor     %r11d, %r11d            # rem.hi
    mov     $128, %ebx
1:
    mov     %rsi, %rax
    shr     $63, %rax               # bit leaving the top of a
    shl     $1, %rdi                # a <<= 1
    rcl     $1, %rsi
    shl     $1, %r10                # rem <<= 1 | bit
    rcl     $1, %r11
    or      %rax, %r10
    # rem >= b ?
    cmp     %r13, %r11
    ja      2f
    jb      3f
    cmp     %r12, %r10
    jb      3f
2:  sub     %r12, %r10
    sbb     %r13, %r11
    shl     $1, %r8                 # q <<= 1 | 1
    rcl     $1, %r9
    or      $1, %r8
    jmp     4f
3:  shl     $1, %r8                 # q <<= 1
    rcl     $1, %r9
4:  dec     %ebx
    jnz     1b
    mov     %r8, %rax               # quotient lo:hi = rax:rdx
    mov     %r9, %rdx
    pop     %r13
    pop     %r12
    pop     %rbx
    ret

# ---------------------------------------------------------------------------------------------
# int setjmp(jmp_buf) / void longjmp(jmp_buf, int)   (glibc x86-64 buffer layout)
# ---------------------------------------------------------------------------------------------
    .globl  sysv_setjmp
    .p2align 4
sysv_setjmp:
    mov     %rbx, 0(%rdi)
    mov     %rbp, 8(%rdi)
    mov     %r12, 16(%rdi)
    mov     %r13, 24(%rdi)
    mov     %r14, 32(%rdi)
    mov     %r15, 40(%rdi)
    lea     8(%rsp), %rax           # stack pointer as it will be after `ret`
    mov     %rax, 48(%rdi)
    mov     (%rsp), %rax            # return address
    mov     %rax, 56(%rdi)
    xor     %eax, %eax
    ret

    .globl  sysv_longjmp
    .p2align 4
sysv_longjmp:
    mov     0(%rdi), %rbx
    mov     8(%rdi), %rbp
    mov     16(%rdi), %r12
    mov     24(%rdi), %r13
    mov     32(%rdi), %r14
    mov     40(%rdi), %r15
    mov     48(%rdi), %rsp
    mov     %esi, %eax
    test    %eax, %eax
    jne     5f
    mov     $1, %eax
5:
    jmp     *56(%rdi)

# ---------------------------------------------------------------------------------------------
# long double strtold_l(const char*, char**, locale_t) — returns 0.0 on the x87 stack
# ---------------------------------------------------------------------------------------------
    .globl  sysv_strtold_l
    .p2align 4
sysv_strtold_l:
    fldz
    ret
