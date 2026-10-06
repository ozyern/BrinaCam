/*
 * Native buffers are handed to Java as jint. That only works while pointers
 * are 32 bits wide, so 64-bit builds map pointers to small integer handles
 * through a table shared by all libraries (implemented in swapheap).
 */
#ifndef PTRHANDLE_H
#define PTRHANDLE_H

#include <jni.h>
#include <stdlib.h>

#if defined(__LP64__)
extern "C" {
jint ptrh_put(void* ptr);
void* ptrh_get(jint handle);
void ptrh_release(jint handle);
}
#define PTR2J(p)	ptrh_put((void*)(p))
#define J2PTR(h)	ptrh_get((jint)(h))
#define JFREE(h)	do { jint _h = (jint)(h); free(ptrh_get(_h)); ptrh_release(_h); } while (0)
#else
#define PTR2J(p)	((jint)(p))
#define J2PTR(h)	((void*)(h))
#define JFREE(h)	free((void*)(h))
#endif

#endif
