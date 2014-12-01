#ifndef __MINTOMIC_MINTOMIC_H__
#define __MINTOMIC_MINTOMIC_H__

#include "core.h"

#ifdef __cplusplus
extern "C" {
#endif


//--------------------------------------------------------------
//  Platform-specific fences and atomic RMW operations
//--------------------------------------------------------------
#if MINT_COMPILER_MSVC
    #include "private/mintomic_msvc.h"
#elif MINT_COMPILER_GCC && (MINT_CPU_X86 || MINT_CPU_X64)
    #include "private/mintomic_gcc_x86-64.h"
#elif MINT_COMPILER_GCC && MINT_CPU_ARM
    #include "private/mintomic_gcc_arm.h"
#else
    #error Unsupported platform!
#endif

//--------------------------------------------------------------
//  Pointer-sized atomic RMW operation wrappers
//--------------------------------------------------------------
#if MINT_PTR_SIZE == 4
    MINT_C_INLINE void *mint_load_ptr_relaxed(mint_atomicPtr_t *object)
    {
        return (void *) mint_load_32_relaxed((mint_atomic32_t *) object);
    }
    MINT_C_INLINE void mint_store_ptr_relaxed(mint_atomicPtr_t *object, void *desired)
    {
        mint_store_32_relaxed((mint_atomic32_t *) object, (size_t) desired);
    }
    MINT_C_INLINE void *mint_compare_exchange_strong_ptr_relaxed(mint_atomicPtr_t *object, void *expected, void *desired)
    {
        return (void *) mint_compare_exchange_strong_32_relaxed((mint_atomic32_t *) object, (size_t) expected, (size_t) desired);
    }
    MINT_C_INLINE void *mint_fetch_add_ptr_relaxed(mint_atomicPtr_t *object, ptrdiff_t operand)
    {
        return (void *) mint_fetch_add_32_relaxed((mint_atomic32_t *) object, operand);
    }
    MINT_C_INLINE void *mint_fetch_and_ptr_relaxed(mint_atomicPtr_t *object, size_t operand)
    {
        return (void *) mint_fetch_and_32_relaxed((mint_atomic32_t *) object, operand);
    }
    MINT_C_INLINE void *mint_fetch_or_ptr_relaxed(mint_atomicPtr_t *object, size_t operand)
    {
        return (void *) mint_fetch_or_32_relaxed((mint_atomic32_t *) object, operand);
    }
#elif MINT_PTR_SIZE == 8
    MINT_C_INLINE void *mint_load_ptr_relaxed(mint_atomicPtr_t *object)
    {
        return (void *) mint_load_64_relaxed((mint_atomic64_t *) object);
    }
    MINT_C_INLINE void mint_store_ptr_relaxed(mint_atomicPtr_t *object, void *desired)
    {
        mint_store_64_relaxed((mint_atomic64_t *) object, (size_t) desired);
    }
    MINT_C_INLINE void *mint_compare_exchange_strong_ptr_relaxed(mint_atomicPtr_t *object, void *expected, void *desired)
    {
        return (void *) mint_compare_exchange_strong_64_relaxed((mint_atomic64_t *) object, (size_t) expected, (size_t) desired);
    }
    MINT_C_INLINE void *mint_fetch_add_ptr_relaxed(mint_atomicPtr_t *object, ptrdiff_t operand)
    {
        return (void *) mint_fetch_add_64_relaxed((mint_atomic64_t *) object, operand);
    }
    MINT_C_INLINE void *mint_fetch_and_ptr_relaxed(mint_atomicPtr_t *object, size_t operand)
    {
        return (void *) mint_fetch_and_64_relaxed((mint_atomic64_t *) object, operand);
    }
    MINT_C_INLINE void *mint_fetch_or_ptr_relaxed(mint_atomicPtr_t *object, size_t operand)
    {
        return (void *) mint_fetch_or_64_relaxed((mint_atomic64_t *) object, operand);
    }
#else
    #error MINT_PTR_SIZE not set!
#endif

// With the current API, which separates the operations from the memory fences
// chosen, it is difficult to use the operations in a semantically meaningful
// way, such that is it possible to do an efficient sequentially consistent
// compare and set.

// This code happens to work as both ARM and X86's CAS operations have sufficient
// barriers to prevent hardware re-ordering.  The mint_signal_fence_seq_cst is
// a conveience operation to apply a compiler barrier either side of the CAS.
// In both compiler variants (msvc/gcc) this happens to work as mint_signal_fence_seq_cst
// is implemented as the necessary compiler barrier.

// This code should be reviewed in the face of any changes to the asm (e.g. new
// processor types) or additional compilers being added to the mix.

MINT_C_INLINE uint32_t mint_compare_exchange_strong_32_seq_cst(mint_atomic32_t *object, uint32_t expected, uint32_t desired)
{
    mint_signal_fence_seq_cst();
    uint32_t r = mint_compare_exchange_strong_32_relaxed(object, expected, desired);
    mint_signal_fence_seq_cst();
    return r;
}

MINT_C_INLINE uint32_t mint_fetch_add_32_seq_cst(mint_atomic32_t *object, int32_t operand)
{
    mint_signal_fence_seq_cst();
    uint32_t r = mint_fetch_add_32_relaxed(object, operand);
    mint_signal_fence_seq_cst();
    return r;
}

MINT_C_INLINE uint32_t mint_fetch_and_32_seq_cst(mint_atomic32_t *object, uint32_t operand)
{
    mint_signal_fence_seq_cst();
    uint32_t r = mint_fetch_and_32_relaxed(object, operand);
    mint_signal_fence_seq_cst();
    return r;
}

MINT_C_INLINE uint32_t mint_fetch_or_32_seq_cst(mint_atomic32_t *object, uint32_t operand)
{
    mint_signal_fence_seq_cst();
    uint32_t r = mint_fetch_or_32_relaxed(object, operand);
    mint_signal_fence_seq_cst();
    return r;
}

MINT_C_INLINE uint64_t mint_compare_exchange_strong_64_seq_cst(mint_atomic64_t *object, uint64_t expected, uint64_t desired)
{
    mint_signal_fence_seq_cst();
    uint64_t r = mint_compare_exchange_strong_64_relaxed(object, expected, desired);
    mint_signal_fence_seq_cst();
    return r;
}

MINT_C_INLINE uint64_t mint_fetch_add_64_seq_cst(mint_atomic64_t *object, int64_t operand)
{
    mint_signal_fence_seq_cst();
    uint64_t r = mint_fetch_add_64_relaxed(object, operand);
    mint_signal_fence_seq_cst();
    return r;
}

MINT_C_INLINE uint64_t mint_fetch_and_64_seq_cst(mint_atomic64_t *object, uint64_t operand)
{
    mint_signal_fence_seq_cst();
    uint64_t r = mint_fetch_and_64_relaxed(object, operand);
    mint_signal_fence_seq_cst();
    return r;
}

MINT_C_INLINE uint64_t mint_fetch_or_64_seq_cst(mint_atomic64_t *object, uint64_t operand)
{
    mint_signal_fence_seq_cst();
    uint64_t r = mint_fetch_or_64_relaxed(object, operand);
    mint_signal_fence_seq_cst();
    return r;
}

#ifdef __cplusplus
} // extern "C"
#endif

#endif // __MINTOMIC_MINTOMIC_H__
