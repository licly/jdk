/*
 * Copyright (c) 2024 Alibaba Group Holding Limited. All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
#include "jni.h"
#if defined(__linux__)
#include <linux/fcntl.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include <bits/types.h>
#include <dlfcn.h>
#ifndef STATX_BASIC_STATS
#define STATX_BASIC_STATS 0x000007ffU
#endif
#ifndef STATX_BTIME
#define STATX_BTIME 0x00000800U
#endif
#ifndef _GNU_SOURCE
#define _GNU_SOURCE
#endif
#ifndef RTLD_DEFAULT
#define RTLD_DEFAULT RTLD_LOCAL
#endif

/*
 * Timestamp structure for the timestamps in struct statx.
 */
struct my_statx_timestamp {
        __int64_t   tv_sec;
        __uint32_t  tv_nsec;
        __int32_t   __reserved;
};

/*
 * struct statx used by statx system call on >= glibc 2.28
 * systems
 */
struct my_statx
{
  __uint32_t stx_mask;
  __uint32_t stx_blksize;
  __uint64_t stx_attributes;
  __uint32_t stx_nlink;
  __uint32_t stx_uid;
  __uint32_t stx_gid;
  __uint16_t stx_mode;
  __uint16_t __statx_pad1[1];
  __uint64_t stx_ino;
  __uint64_t stx_size;
  __uint64_t stx_blocks;
  __uint64_t stx_attributes_mask;
  struct my_statx_timestamp stx_atime;
  struct my_statx_timestamp stx_btime;
  struct my_statx_timestamp stx_ctime;
  struct my_statx_timestamp stx_mtime;
  __uint32_t stx_rdev_major;
  __uint32_t stx_rdev_minor;
  __uint32_t stx_dev_major;
  __uint32_t stx_dev_minor;
  __uint64_t __statx_pad2[14];
};

typedef int statx_func(int dirfd, const char *restrict pathname, int flags,
                       unsigned int mask, struct my_statx *restrict statxbuf);

static statx_func* my_statx_func = NULL;
#endif  //#defined(__linux__)

// static native boolean linuxIsCreationTimeSupported()
JNIEXPORT jboolean JNICALL
Java_CreationTimeHelper_linuxIsCreationTimeSupported(JNIEnv *env, jclass cls, jstring file) {
#if defined(__linux__)
    struct my_statx stx;
    int ret, atflag = AT_SYMLINK_NOFOLLOW;
    memset(&stx, 0xbf, sizeof(stx));
    unsigned int mask = STATX_BASIC_STATS | STATX_BTIME;

    my_statx_func = (statx_func*) dlsym(RTLD_DEFAULT, "statx");
    if (my_statx_func == NULL) {
        return JNI_FALSE;
    }

    if (file == NULL) {
        printf("input file error!\n");
        return JNI_FALSE;
    }
    const char *utfChars = (*env)->GetStringUTFChars(env, file, NULL);
    if (utfChars == NULL) {
        printf("jstring convert to char array error!\n");
        return JNI_FALSE;
    }

    ret = my_statx_func(AT_FDCWD, utfChars, atflag, mask, &stx);

    if (file != NULL && utfChars != NULL) {
        (*env)->ReleaseStringUTFChars(env, file, utfChars);
    }

    #ifdef DEBUG
    printf("birth time = %ld\n", stx.stx_btime.tv_sec);
    #endif
    if (ret != 0) {
        return JNI_FALSE;
    }
    if (stx.stx_mask & STATX_BTIME)
        return JNI_TRUE;
    return JNI_FALSE;
#else
    return JNI_FALSE;
#endif
}
