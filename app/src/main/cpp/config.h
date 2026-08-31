#ifndef INDIRGITSIN_LAME_CONFIG_H
#define INDIRGITSIN_LAME_CONFIG_H
#include <stdint.h>
#define STDC_HEADERS 1
#define HAVE_STDINT_H 1
#define HAVE_INTTYPES_H 1
#define HAVE_ERRNO_H 1
#define HAVE_FCNTL_H 1
#define HAVE_LIMITS_H 1
#define HAVE_STRCHR 1
#define HAVE_MEMCPY 1
#define HAVE_STRERROR 1
#define HAVE_STRTOL 1
#define HAVE_LONG_DOUBLE 1
#define HAVE_LONG_DOUBLE_WIDER 1
#define SIZEOF_SHORT 2
#define SIZEOF_INT 4
#define SIZEOF_FLOAT 4
#define SIZEOF_DOUBLE 8
#define SIZEOF_LONG __SIZEOF_LONG__
#define SIZEOF_LONG_LONG 8
#define SIZEOF_LONG_DOUBLE __SIZEOF_LONG_DOUBLE__
#define PACKAGE "lame"
#define VERSION "3.100"
#define LAME_LIBRARY_BUILD 1
#define PROTOTYPES 1
#define USE_FAST_LOG 1
typedef float ieee754_float32_t;
typedef double ieee754_float64_t;
typedef long double ieee854_float80_t;
#endif
