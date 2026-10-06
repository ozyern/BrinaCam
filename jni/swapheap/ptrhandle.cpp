#include "ptrhandle.h"

#if defined(__LP64__)
#include <pthread.h>
#include <vector>

// Handle 0 is NULL; handle n refers to slots[n - 1].
static std::vector<void*>	slots;
static std::vector<jint>	freeHandles;
static pthread_mutex_t		lock = PTHREAD_MUTEX_INITIALIZER;

extern "C" jint ptrh_put(void* ptr)
{
	if (ptr == NULL)
		return 0;

	pthread_mutex_lock(&lock);
	jint handle;
	if (!freeHandles.empty())
	{
		handle = freeHandles.back();
		freeHandles.pop_back();
		slots[handle - 1] = ptr;
	} else
	{
		slots.push_back(ptr);
		handle = (jint)slots.size();
	}
	pthread_mutex_unlock(&lock);
	return handle;
}

extern "C" void* ptrh_get(jint handle)
{
	if (handle <= 0)
		return NULL;

	pthread_mutex_lock(&lock);
	void* ptr = (size_t)handle <= slots.size() ? slots[handle - 1] : NULL;
	pthread_mutex_unlock(&lock);
	return ptr;
}

extern "C" void ptrh_release(jint handle)
{
	if (handle <= 0)
		return;

	pthread_mutex_lock(&lock);
	if ((size_t)handle <= slots.size() && slots[handle - 1] != NULL)
	{
		slots[handle - 1] = NULL;
		freeHandles.push_back(handle);
	}
	pthread_mutex_unlock(&lock);
}
#endif
