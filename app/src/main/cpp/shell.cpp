//
// Created by Umakanth on 19/11/20.
//

#include <jni.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/types.h>
#include <sys/stat.h>

extern "C" JNIEXPORT JNICALL
jint Java_dev_ukanth_ufirewall_RootShellService_nativeGetUid(
		JNIEnv *env, jobject instance) {
	return getuid();
}

extern "C"
JNIEXPORT jstring JNICALL
Java_dev_ukanth_ufirewall_RootShellService_nativeReadFile(JNIEnv *env, jobject thiz, jstring name) {
	const char *path = env->GetStringUTFChars(name, nullptr);
	int fd = open(path, O_RDONLY);
	env->ReleaseStringUTFChars(name, path);
	char buf[4096];
	buf[read(fd, buf, sizeof(buf) - 1)] = 0;
	return env->NewStringUTF(buf);
}